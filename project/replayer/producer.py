#!/usr/bin/env python3
"""
TLC → Kafka producer (JSON)
- Reads TLC Parquet over HTTPS with DuckDB
- Publishes to Kafka with rate control & batching
- Optional lateness injection & key salting to reduce partition skew

Deps: pip install confluent-kafka duckdb pyarrow
"""

import os
import sys
import time
import signal
import random
import duckdb
from datetime import datetime, timedelta
from confluent_kafka import Producer
from confluent_kafka.serialization import SerializationContext, MessageField
from confluent_kafka.schema_registry import SchemaRegistryClient
from confluent_kafka.schema_registry.avro import AvroSerializer

# -----------------------
# Config (env or defaults)
# -----------------------
BOOTSTRAP = os.getenv("KAFKA_BOOTSTRAP", "localhost:9092")
TOPIC = os.getenv("TOPIC", "trips.yellow")
SCHEMA_REGISTRY_URL = os.getenv("SCHEMA_REGISTRY_URL", "http://localhost:8082")

# Replay scope
DATASET = os.getenv("DATASET", "yellow")  # yellow | green | fhv | fhvhv
YEARS = os.getenv("YEARS", "2024")  # e.g. "2024" or "2023,2024"
MONTHS = os.getenv("MONTHS", "")  # optional explicit months "06,07,08"
URL_BASE = "https://d37ci6vzurychx.cloudfront.net/trip-data/"

# Producer behavior
RATE = float(os.getenv("RATE", "2000"))  # messages per second target
BATCH = int(os.getenv("BATCH", "2000"))  # rows sent per flush
COMPRESSION = os.getenv("COMPRESSION", "zstd")  # zstd|lz4|snappy|gzip
IDEMP = os.getenv("IDEMPOTENCE", "true").lower() == "true"
MAX_INFLT = int(os.getenv("MAX_INFLIGHT", "1"))  # keep 1 if idempotent

# Keys / skew
SALT_KEYS = int(os.getenv("SALT_KEYS", "0"))  # 0 = no salt; else N buckets
KEY_MODE = os.getenv("KEY_MODE", "pu")  # pu | how_pu (hourOfWeek+pu)

# Lateness injection
P_LATE = float(os.getenv("P_LATE", "0.0"))  # fraction 0..1 of records to delay
LATE_MIN = int(os.getenv("LATE_MIN", "300"))  # seconds (min)
LATE_MAX = int(os.getenv("LATE_MAX", "1200"))  # seconds (max)

# Filters
MIN_DIST = float(os.getenv("MIN_DIST", "0.2"))
MAX_DIST = float(os.getenv("MAX_DIST", "40"))

# DuckDB streaming chunk (fetchmany size)
CHUNK_ROWS = int(os.getenv("CHUNK_ROWS", str(BATCH * 10)))


def build_urls(dataset: str, years: str, months: str):
    yrs = [y.strip() for y in years.split(",") if y.strip()]
    if months:
        mlist = [m.strip().zfill(2) for m in months.split(",") if m.strip()]
        return [
            f"{URL_BASE}{dataset}_tripdata_{y}-{m}.parquet" for y in yrs for m in mlist
        ]
    # default: all 12 months for each year
    return [
        f"{URL_BASE}{dataset}_tripdata_{y}-{m:02d}.parquet"
        for y in yrs
        for m in range(1, 13)
    ]


# Load Avro schema from file
def load_schema():
    """Load the Avro schema from file (required)."""
    import pathlib
    schema_path = pathlib.Path(__file__).parent.parent / "schemas" / "trip_event.avsc"

    if schema_path.exists():
        with open(schema_path, 'r') as f:
            return f.read()
    else:
        print(f"✗ Schema file not found: {schema_path}", file=sys.stderr)
        print("  Please ensure schemas/trip_event.avsc exists", file=sys.stderr)
        sys.exit(1)

def make_producer():
    conf = {
        "bootstrap.servers": BOOTSTRAP,
        "acks": "all",
        "compression.type": COMPRESSION,
        "linger.ms": 10,
        "batch.num.messages": max(BATCH, 1000),
        "enable.idempotence": IDEMP,
        "max.in.flight.requests.per.connection": MAX_INFLT,
    }
    return Producer(conf)


def delivery_cb(err, msg, stats):
    if err:
        stats["failed"] += 1
    else:
        stats["delivered"] += 1


def add_lateness(ts: datetime) -> datetime:
    if P_LATE <= 0:
        return ts
    if random.random() < P_LATE:
        return ts + timedelta(seconds=random.randint(LATE_MIN, LATE_MAX))
    return ts


def salt_key(base: str) -> str:
    if SALT_KEYS and SALT_KEYS > 0:
        bucket = random.randint(0, SALT_KEYS - 1)
        return f"{base}:{bucket}"
    return base


def main():
    urls = build_urls(DATASET, YEARS, MONTHS)
    if not urls:
        print("No URLs constructed; check YEARS/MONTHS env.", file=sys.stderr)
        sys.exit(1)

    # DuckDB reader with pruning and ordering by event time
    con = duckdb.connect()
    con.execute("INSTALL httpfs; LOAD httpfs;")

    query = f"""
    SELECT
      tpep_pickup_datetime    AS ts,
      PULocationID            AS pu,
      DOLocationID            AS do,
      trip_distance           AS dist
    FROM read_parquet(?, filename=true)
    WHERE trip_distance BETWEEN {MIN_DIST} AND {MAX_DIST}
      AND tpep_dropoff_datetime > tpep_pickup_datetime
    ORDER BY ts
    """

    result = con.execute(query, [urls])

    prod = make_producer()

    # Setup Schema Registry and Avro serializer (required)
    schema_str = load_schema()

    try:
        schema_registry_conf = {'url': SCHEMA_REGISTRY_URL}
        schema_registry_client = SchemaRegistryClient(schema_registry_conf)

        # Auto-register schema if not exists
        avro_serializer = AvroSerializer(
            schema_registry_client,
            schema_str,
            conf={'auto.register.schemas': True}
        )
        print(f"✓ Connected to Schema Registry at {SCHEMA_REGISTRY_URL}", file=sys.stderr)
        print(f"✓ Using Avro serialization with schema evolution support", file=sys.stderr)
    except Exception as e:
        print(f"✗ Failed to connect to Schema Registry: {e}", file=sys.stderr)
        print(f"  Please ensure Schema Registry is running at {SCHEMA_REGISTRY_URL}", file=sys.stderr)
        print("  Run: make up", file=sys.stderr)
        sys.exit(1)

    # metrics
    stats = {"sent": 0, "delivered": 0, "failed": 0, "start": time.time()}
    stop = {"flag": False}

    def handle_sig(sig, frame):
        stop["flag"] = True
        print("\nStopping… flushing…", file=sys.stderr)

    signal.signal(signal.SIGINT, handle_sig)
    signal.signal(signal.SIGTERM, handle_sig)

    # pacing
    period = 1.0 / RATE if RATE > 0 else 0.0
    last_print = time.time()
    buf = []

    def send_buf():
        for k, v in buf:
            # Encode key and value appropriately
            key_bytes = k.encode("utf-8") if k else None
            val_bytes = v if isinstance(v, bytes) else v.encode("utf-8")
            prod.produce(
                TOPIC, key=key_bytes, value=val_bytes, callback=lambda e, m: delivery_cb(e, m, stats)
            )
        prod.flush()  # enforce backpressure per batch
        buf.clear()

    while not stop["flag"]:
        rows = result.fetchmany(CHUNK_ROWS)
        if not rows:
            break
        for ts, pu, do, dist in rows:
            # event time & key
            ts_dt = ts if isinstance(ts, datetime) else datetime.fromisoformat(str(ts))
            ts_out = add_lateness(ts_dt)

            # hour-of-week if requested
            if KEY_MODE == "how_pu":
                how = (ts_dt.weekday() * 24) + ts_dt.hour
                base_key = f"{how}:{int(pu or 0)}"
            else:
                base_key = str(int(pu or 0))

            key = salt_key(base_key)

            payload = {
                "ts": ts_out.isoformat(sep=" "),
                "pu": int(pu or 0),
                "do": int(do or 0),
                "dist": float(dist or 0.0),
            }

            # Serialize with Avro
            val = avro_serializer(
                payload, SerializationContext(TOPIC, MessageField.VALUE)
            )

            buf.append((key, val))
            stats["sent"] += 1

            if len(buf) >= BATCH:
                t0 = time.time()
                send_buf()
                # simple rate pacing (approximate)
                elapsed = time.time() - t0
                target = BATCH * period
                sleep_for = max(0.0, target - elapsed)
                if sleep_for > 0:
                    time.sleep(sleep_for)

            # periodic metrics
            now = time.time()
            if now - last_print >= 5.0:
                dur = now - stats["start"]
                eps = stats["sent"] / max(dur, 1e-6)
                print(
                    f"[producer] sent={stats['sent']} delivered={stats['delivered']} "
                    f"failed={stats['failed']} rate~{eps:.0f} msg/s",
                    file=sys.stderr,
                )
                last_print = now

        # flush tail of chunk (keeps pacing tighter)
        if buf:
            send_buf()

    # final flush
    if buf:
        send_buf()
    prod.flush()

    dur = time.time() - stats["start"]
    eps = stats["sent"] / max(dur, 1e-6)
    print(
        f"[producer] DONE in {dur:.1f}s  sent={stats['sent']}  delivered={stats['delivered']} "
        f"failed={stats['failed']}  avg_rate={eps:.0f} msg/s",
        file=sys.stderr,
    )


if __name__ == "__main__":
    main()
