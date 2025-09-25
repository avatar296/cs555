Title

CityScale: Exactly-Once Streaming Analytics for NYC Taxi Demand (Delta Lake + Spark on 10 nodes)

One-liner

Replay historical TLC trips as a synthetic live stream → ingest via Kafka → aggregate with Spark Structured Streaming (event-time, watermarks) → Delta Lake tables with exactly-once semantics → serve fast queries (batch + streaming) for spatial analytics at zone granularity.

Why this is perfect for CS X55

Spatial: uses TLC Taxi Zones (polygon joins; optional H3 tiling).

Distributed: runs on ≥10 machines (Spark cluster + Kafka brokers + ZooKeeper/Redpanda + object store/DFS).

Data/compute-intensive: multi-month streams; stateful windows; compaction/Z-order experiments.

Systems concepts (what you can demo/measure): backpressure, skew, checkpoint/restart, exactly-once IO, partitioning, file layout, small-file compaction, autoscaling.

What you’ll build (high level)

Ingest layer

A “replayer” that reads Parquet months and publishes to Kafka at a controllable event rate (and configurable lateness to test watermarks).

Topics: trips.yellow (key=PULocationID or (how, PULocationID) salted).

Streaming compute

Spark Structured Streaming job with event-time windows (e.g., 1-hour tumbling) and watermarking.

Stateful aggregations: pickups_by_zone_hour, OD_flows_hourly (optional).

Exactly-once sink to Delta Lake/Iceberg on object storage (S3/MinIO/HDFS).

Batch+Ad-hoc layer

Spark SQL notebooks to join with Taxi Zones polygons → maps/choropleths and OD tables.

Layout experiments: partitioning by year,month,PULocationID vs. year,month,H3_7; OPTIMIZE/Z-order vs. naive.

Ops & resilience

Checkpointing + RocksDB state store (or default) and a failure script that kills an executor/broker mid-run.

Dashboards/metrics (Spark UI, Kafka lag) and scripted backpressure tests (increase publish rate 1×→10×).

How you’ll run on ≥10 machines

Option A (most straightforward): 1 × Kafka (or 3× for HA), 1 × ZooKeeper/Redpanda, 1× Spark driver, 6–12× Spark executors (t2.medium/standard student VMs are fine), 1 × object store (MinIO) or HDFS name/data nodes.

Option B: Kubernetes with Helm charts (Strimzi for Kafka, Delta on S3/MinIO, Spark on K8s). Count nodes, not pods, to meet the “10 machines” requirement.

Slide deck for TP-D1 (exactly 5 slides)

Title
CityScale: Exactly-Once Streaming Analytics for NYC Taxi Demand — Your Names

Problem characterization

Need near-real-time, fault-tolerant analytics over a high-volume spatial stream (NYC Taxi).

Challenges: skew (airport zones), out-of-order/late events, exactly-once writes, table layout at scale.

Why it matters

Real-world ops: dispatching, surge detection, incident response, performance SLAs.

Teaches core DS themes: backpressure, checkpointing, replay, compaction, partition pruning.

Proposed solution & libs

Kafka (source) → Spark Structured Streaming (event-time windows + watermark) → Delta Lake (exactly-once) on S3/MinIO.

Batch layer for maps/OD; optional H3 tiling. Cluster of ≥10 nodes.

Evaluation

Correctness under failure (exactly-once, idempotent counts)

Throughput/latency vs. cluster size & ingest rate

Shuffle/file layout metrics (pruning %, files/partition, compaction gains)

Skew mitigation (runtime bytes spilled, stages time)

Milestones mapped to course deadlines

TP-D0 (Team): pick 2–3 roles (Streaming lead, Data/Geo lead, Infra/Benchmark lead).

TP-D1 (Pitch): use the 5-slide outline above.

By mid-Oct: data replayer + Kafka topic + first streaming job writing to Delta/Iceberg.

Early Nov: failure testing (kill/restart), watermark correctness, autoscaling experiment.

Mid-Nov: layout experiments, skew mitigation, charts/maps.

TP-D2 (Code & Demo): scripts to spin up cluster, run replayer, run streaming job, run benchmarks + notebooks.

TP-D3/D4: report + presentation.

Concrete metrics you will report

Correctness: equality of window counts before/after failure; zero duplicates (idempotent table).

Streaming performance:

ingest rate (msg/s), processed rows/s, end-to-end latency (event-time → committed).

effect of watermark and allowed lateness on output completeness.

Skew & partitioning: shuffle bytes, task time distribution; compare salted vs. naive keys.

Lakehouse layout: #files/partition, mean file size, query time for “Fri/Sat 22:00–02:00 pickups in JFK/LGA zones” before/after OPTIMIZE/Z-order.

Minimal code scaffolds (you can drop in today)
1) Replayer → Kafka
# pip install confluent-kafka duckdb pyarrow
import duckdb, json, time, os
from confluent_kafka import Producer

RATE = float(os.getenv("RATE", "500"))  # msgs/sec
base = "https://d37ci6vzurychx.cloudfront.net/trip-data/"
months = ["2024-06","2024-07","2024-08"]
urls = [f"{base}yellow_tripdata_{m}.parquet" for m in months]

con = duckdb.connect()
rows = con.execute(f"""
SELECT tpepPickupDatetime AS ts, PULocationID, DOLocationID, trip_distance
FROM read_parquet({urls})
WHERE trip_distance BETWEEN 0.2 AND 40
ORDER BY ts
""").fetchall()

p = Producer({"bootstrap.servers":"localhost:9092"})
period = 1.0 / RATE
for (ts, pu, do, dist) in rows:
    msg = {"ts": str(ts), "pu": int(pu), "do": int(do), "dist": float(dist)}
    p.produce("trips.yellow", json.dumps(msg))
    p.poll(0); time.sleep(period)
p.flush()

2) Spark Structured Streaming → Delta (exactly-once)
# spark-submit --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.1,io.delta:delta-spark_2.12:3.2.0
from pyspark.sql import SparkSession, functions as F
from delta import configure_spark_with_delta_pip

spark = configure_spark_with_delta_pip(
  SparkSession.builder
    .appName("cityscale-stream")
    .config("spark.sql.shuffle.partitions","200")
    .config("spark.sql.streaming.stateStore.providerClass",
            "org.apache.spark.sql.execution.streaming.state.RocksDBStateStoreProvider")
    .config("spark.sql.adaptive.skewJoin.enabled","true")
).getOrCreate()

raw = (spark.readStream.format("kafka")
  .option("kafka.bootstrap.servers","localhost:9092")
  .option("subscribe","trips.yellow").load())

json = spark.read.json(raw.selectExpr("CAST(value AS STRING)").toDF("value").rdd.map(lambda r: r.value))
with_ts = json.withColumn("event_time", F.to_timestamp("ts"))

agg = (with_ts
  .withWatermark("event_time","30 minutes")
  .groupBy(F.window("event_time","1 hour"), F.col("pu").alias("PULocationID"))
  .count())

(agg.writeStream
  .format("delta")
  .outputMode("update")
  .option("checkpointLocation","s3a://cityscale/chk/pu_hour/")   # or local/MinIO/hdfs
  .start("s3a://cityscale/delta/pu_hour/"))
spark.streams.awaitAnyTermination()

3) Batch query + geometry join (map-ready)
# pip install geopandas pyogrio shapely duckdb
import duckdb, geopandas as gpd

zones = gpd.read_file("https://s3.amazonaws.com/nyc-tlc/misc/taxi_zones.zip").to_crs(4326)
con = duckdb.connect()
pu_counts = con.execute("""
  SELECT PULocationID AS LocationID, SUM(count) AS trips
  FROM read_parquet('s3a://cityscale/delta/pu_hour/*.parquet')
  GROUP BY 1
""").df()
choropleth = zones.merge(pu_counts, on="LocationID", how="left").fillna({"trips":0})
choropleth.plot(column="trips", legend=True)

Evaluation experiments (easy to run, easy to grade)

Failure & exactly-once

Start stream; inject data for 10 min; kill the driver/executor; restart from checkpoint; show identical window counts.

Repeat with late events to verify watermark behavior.

Throughput/latency scaling

Run at 500, 1k, 2k, 5k msgs/s; record processedRowsPerSecond and latency; scale executors 4 → 8 → 12.

Skew mitigation

Compare naive key (PULocationID) vs. salted key (PULocationID || hash(how)%K) on shuffle time and stage skew.

Layout/compaction

Create two Delta tables: naive (no partitioning) vs. partitioned (year,month,PULocationID) + OPTIMIZE/ZORDER.

Query “Fri/Sat 22:00–02:00 in airport zones” and report wall-clock + data-scanned.

Report mapping (section hints + what to include)

Introduction (400): real-time spatial analytics use cases; taxi demand as a proxy; DS themes (fault tolerance, exactly-once).

Problem characterization (400): streaming semantics, out-of-order, skewed keys, small-file problem, geo joins.

Dominant approaches (300): micro-batch (Spark) vs. record-at-a-time (Flink); lakehouse tables (Delta/Iceberg/Hudi); trade-offs.

Methodology (900): architecture, schemas, keys, watermarks, partitioning, checkpointing, compaction, experiments design.

Benchmarks (400): the four experiments above; metrics/plots.

Insights (400): what actually moved the needle (e.g., salting > broadcast; Z-order benefits; watermark trade-offs).

Future (300): multi-tenant streams, geo-indexing (H3), CDC upserts, autoscaling, streaming joins with weather.

Conclusions (400): concrete assertions (e.g., “With 8 executors we sustain 2k msg/s at <5s latency and survive failures with 0 duplicates.”).

Bibliography (8–10): papers/blogs/docs on exactly-once, watermarks, Delta/Iceberg, Spark/Flink streaming, H3.

Risks & how you’ll mitigate

HTTPS Parquet directly in Spark: stage months to S3/MinIO/HDFS first (simple Python downloader or AWS CLI).

Cluster access: if no cloud credits, use the department’s cluster or spin 10 small VMs (or 10 K8s nodes).

Version pinning: Spark 3.5.x, Delta 3.2.x, Kafka 3.x. Keep a requirements.txt / poetry.lock for Python tools.

Repo skeleton (ready for TP-D2)
cityscale/
  README.md
  docker-compose/ (kafka, minio, spark history)
  infra/
    terraform/ or k8s/helm/
  replayer/
    replay.py
  streaming/
    spark_job.py
    conf/ (spark-defaults, log4j)
  batch/
    queries.sql
    zone_join_map.ipynb
  scripts/
    stage_data.sh
    kill_and_resume.sh
  notebooks/
    benchmarks.ipynb
  reports/
    pitch-slides.pptx


If you want, I’ll tailor this to your exact environment (CSU cluster vs. cloud) and give you:

a docker-compose that runs Kafka + MinIO locally,

a stage_data.sh that downloads 2024-06..08 TLC files,

and a Makefile with make up, make replay, make stream, make bench.