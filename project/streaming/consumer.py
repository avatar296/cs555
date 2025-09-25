#!/usr/bin/env python3
"""
Spark Streaming Consumer - Kafka -> Iceberg (MinIO)
- Reads Avro-encoded messages from Kafka (Confluent wire format)
- Performs windowed aggregations
- Writes to Iceberg table (partitioned by date)
"""

from pyspark.sql import SparkSession
from pyspark.sql import functions as F
from pyspark.sql.avro.functions import from_avro
import requests
import sys
import os


# -----------------------------
# Config (override via env vars)
# -----------------------------
KAFKA_SERVERS = os.getenv("KAFKA_SERVERS", "kafka:9092")
KAFKA_TOPIC = os.getenv("KAFKA_TOPIC", "trips.yellow")
SCHEMA_REGISTRY_URL = os.getenv("SCHEMA_REGISTRY_URL", "http://schema-registry:8081")
SCHEMA_SUBJECT = os.getenv("SCHEMA_SUBJECT", "trips.yellow-value")

# Iceberg catalog configuration
ICEBERG_CATALOG = os.getenv("ICEBERG_CATALOG", "local")
ICEBERG_DB = os.getenv("ICEBERG_DB", "db")
ICEBERG_TABLE = os.getenv("ICEBERG_TABLE", "trips_aggregated")
TABLE_NAME = f"{ICEBERG_CATALOG}.{ICEBERG_DB}.{ICEBERG_TABLE}"

WAREHOUSE = os.getenv("WAREHOUSE", "s3a://lakehouse/iceberg")

# MinIO / S3A
S3_ENDPOINT = os.getenv("S3_ENDPOINT", "http://minio:9000")
S3_ACCESS_KEY = os.getenv("S3_ACCESS_KEY", "admin")
S3_SECRET_KEY = os.getenv("S3_SECRET_KEY", "admin123")
S3_SSL = os.getenv("S3_SSL", "false").lower()  # "false" for http

# Checkpoints
CHECKPOINT_ICEBERG = os.getenv(
    "CHECKPOINT_ICEBERG", "/opt/spark/work-dir/checkpoint/trips_iceberg"
)

# Trigger cadence
TRIGGER_CONSOLE = os.getenv("TRIGGER_CONSOLE", "10 seconds")
TRIGGER_ICEBERG = os.getenv("TRIGGER_ICEBERG", "30 seconds")

# Stream source sizing
MAX_OFFSETS_PER_TRIGGER = int(os.getenv("MAX_OFFSETS_PER_TRIGGER", "10000"))


def create_spark_session():
    """Create Spark session with Kafka, Avro, Iceberg, and S3/MinIO configured."""
    spark = (
        SparkSession.builder.appName("KafkaStreamingConsumer")
        # Keep logs sane; adaptive is disabled for streaming anyway
        .config("spark.sql.adaptive.enabled", "false")
        # Use default state store provider (HDFSBackedStateStoreProvider)
        # Removed RocksDB due to compatibility issues with complete output mode
        .config("spark.sql.streaming.stateStore.stateSchemaCheck", "false")
        # Session timezone for consistent date/hour derivation
        .config("spark.sql.session.timeZone", "UTC")
        # === Required jars ===
        .config(
            "spark.jars.packages",
            ",".join(
                [
                    "org.apache.spark:spark-avro_2.12:3.5.0",
                    "org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.4.2",
                    "org.apache.hadoop:hadoop-aws:3.3.4",
                    "com.amazonaws:aws-java-sdk-bundle:1.12.262",
                ]
            ),
        )
        # === Iceberg catalog ===
        .config(
            f"spark.sql.catalog.{ICEBERG_CATALOG}",
            "org.apache.iceberg.spark.SparkCatalog",
        )
        .config(f"spark.sql.catalog.{ICEBERG_CATALOG}.type", "hadoop")
        .config(f"spark.sql.catalog.{ICEBERG_CATALOG}.warehouse", WAREHOUSE)
        # === MinIO/S3A ===
        .config("spark.hadoop.fs.s3a.endpoint", S3_ENDPOINT)
        .config("spark.hadoop.fs.s3a.path.style.access", "true")
        .config("spark.hadoop.fs.s3a.access.key", S3_ACCESS_KEY)
        .config("spark.hadoop.fs.s3a.secret.key", S3_SECRET_KEY)
        .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
        .config("spark.hadoop.fs.s3a.connection.ssl.enabled", S3_SSL)
        .getOrCreate()
    )
    spark.sparkContext.setLogLevel("WARN")
    return spark


def fetch_latest_schema(schema_registry_url: str, subject: str) -> str:
    """Fetch latest Avro schema string from Confluent Schema Registry."""
    try:
        resp = requests.get(
            f"{schema_registry_url}/subjects/{subject}/versions/latest", timeout=10
        )
        if resp.status_code == 200:
            info = resp.json()
            schema = info["schema"]
            print(f"✓ Avro schema fetched (id={info['id']}, version={info['version']})")
            return schema
        else:
            print(
                f"✗ Schema not found for subject {subject} (status: {resp.status_code})"
            )
            sys.exit(1)
    except Exception as e:
        print(f"✗ Failed to connect to Schema Registry at {schema_registry_url}: {e}")
        sys.exit(1)


def ensure_db_exists(spark: SparkSession, catalog: str, db: str):
    """Create Iceberg namespace if it doesn't exist."""
    spark.sql(f"CREATE NAMESPACE IF NOT EXISTS {catalog}.{db}")


def ensure_table_exists(spark: SparkSession, full_table_name: str, schema_df):
    """Create Iceberg table if it doesn't exist, partitioned by date."""
    try:
        spark.table(full_table_name)
        print(f"Table {full_table_name} already exists")
    except Exception:
        print(f"Creating Iceberg table {full_table_name} ...")
        (
            spark.createDataFrame([], schema_df.schema)
            .writeTo(full_table_name)
            .using("iceberg")
            .partitionedBy("date")  # Partition by date to avoid small file explosion
            .tableProperty("write.target-file-size-bytes", "134217728")  # 128MB target
            .tableProperty("write.distribution-mode", "hash")
            .createOrReplace()
        )
        print(f"✓ Created {full_table_name} (partitioned by date)")


def main():
    print("Starting Kafka Streaming Consumer → Iceberg (UTC partitioning by date)")
    print("Press Ctrl+C to stop\n")

    spark = create_spark_session()

    # 1) Avro schema
    avro_schema = fetch_latest_schema(SCHEMA_REGISTRY_URL, SCHEMA_SUBJECT)

    # 2) Kafka source
    kafka_df = (
        spark.readStream.format("kafka")
        .option("kafka.bootstrap.servers", KAFKA_SERVERS)
        .option("subscribe", KAFKA_TOPIC)
        .option("startingOffsets", "latest")
        .option("failOnDataLoss", "false")
        .option("maxOffsetsPerTrigger", MAX_OFFSETS_PER_TRIGGER)
        .load()
    )

    # 3) Avro decoding (skip Confluent header: 5 bytes)
    parsed_df = kafka_df.select(
        F.col("key").cast("string").alias("key"),
        from_avro(F.expr("substring(value, 6, length(value)-5)"), avro_schema).alias(
            "data"
        ),
        F.col("timestamp").alias("kafka_ingest_ts"),
    ).select(
        "key",
        F.col("data.ts").alias("event_time"),  # could be epoch ms/sec or ISO string
        F.col("data.pu").alias("pickup_location"),
        F.col("data.do").alias("dropoff_location"),
        F.col("data.dist").alias("distance"),
        "kafka_ingest_ts",
    )
    print("✓ Using Avro deserialization with Confluent wire format")

    # 4) Robust event_time parsing -> event_timestamp (UTC)
    event_time_col = F.col("event_time")
    event_ts = (
        F.when(
            (event_time_col.cast("double").isNotNull())
            & (event_time_col.cast("double") >= F.lit(1_000_000_000_000)),
            F.to_timestamp(F.from_unixtime((event_time_col.cast("double") / 1000.0))),
        )
        .when(
            (event_time_col.cast("double").isNotNull())
            & (event_time_col.cast("double") >= F.lit(1_000_000_000)),
            F.to_timestamp(F.from_unixtime(event_time_col.cast("double"))),
        )
        .otherwise(
            F.to_timestamp(event_time_col)  # try ISO-8601 / 'yyyy-MM-dd HH:mm:ss'
        )
    )

    processed_df = (
        parsed_df.withColumn("processing_time", F.current_timestamp())
        .withColumn(
            "distance_category",
            F.when(F.col("distance") < 2, "short")
            .when(F.col("distance") < 10, "medium")
            .otherwise("long"),
        )
        .withColumn("event_timestamp", event_ts)  # UTC by session config
        .filter(F.col("event_timestamp").isNotNull())
    )

    # 5) Windowed stats (UTC); watermark set to 30 seconds for faster results
    windowed_stats = (
        processed_df.filter(F.col("event_timestamp").isNotNull())
        .withWatermark("event_timestamp", "30 seconds")
        .groupBy(
            F.window(F.col("event_timestamp"), "30 seconds", "10 seconds"),
            F.col("distance_category"),
        )
        .agg(
            F.count("*").alias("trip_count"),
            F.avg("distance").alias("avg_distance"),
            F.min("distance").alias("min_distance"),
            F.max("distance").alias("max_distance"),
        )
        .select(
            F.col("window.start").alias("window_start"),
            F.col("window.end").alias("window_end"),
            F.date_format(F.col("window.start"), "yyyy-MM-dd").alias(
                "date"
            ),  # partition key (UTC)
            F.hour(F.col("window.start")).alias("hour"),  # hour for analysis, not partitioning
            F.col("distance_category"),
            F.col("trip_count"),
            F.round(F.col("avg_distance"), 2).alias("avg_distance"),
            F.round(F.col("min_distance"), 2).alias("min_distance"),
            F.round(F.col("max_distance"), 2).alias("max_distance"),
        )
    )

    # 6) Ensure namespace/table exist
    ensure_db_exists(spark, ICEBERG_CATALOG, ICEBERG_DB)
    ensure_table_exists(spark, TABLE_NAME, windowed_stats)

    # 7) Console sink (visibility)
    console_query = (
        windowed_stats.writeStream.outputMode("update")
        .format("console")
        .option("truncate", False)
        .trigger(processingTime=TRIGGER_CONSOLE)
        .start()
    )

    # 8) Iceberg sink (complete mode - writes entire aggregation state)
    iceberg_query = (
        windowed_stats.writeStream
        .outputMode("complete")  # Complete mode for aggregations with Iceberg
        .option("checkpointLocation", CHECKPOINT_ICEBERG)
        .trigger(processingTime=TRIGGER_ICEBERG)
        .toTable(TABLE_NAME)
    )

    print("\n" + "=" * 72)
    print(f"Streaming started: reading Kafka topic: {KAFKA_TOPIC}")
    print("Window: 30s, slide: 10s | Watermark: 30s (UTC)")
    print(f"Writing to Iceberg table: {TABLE_NAME} (using complete mode)")
    print(f"Warehouse: {WAREHOUSE}")
    print(f"Checkpoint: {CHECKPOINT_ICEBERG}")
    print("Spark UI: http://localhost:8081")
    print("MinIO Console: http://localhost:9001 (admin/admin123)")
    print("=" * 72 + "\n")

    try:
        console_query.awaitTermination()
        iceberg_query.awaitTermination()
    except KeyboardInterrupt:
        print("\nShutting down ...")
        console_query.stop()
        iceberg_query.stop()
        spark.stop()
        print("Stopped cleanly")


if __name__ == "__main__":
    main()
