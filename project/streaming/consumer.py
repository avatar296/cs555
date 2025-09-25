#!/usr/bin/env python3
"""
Spark Structured Streaming consumer for TLC trips
- Source: Kafka topic with JSON messages: {"ts": "...", "pu": int, "do": int, "dist": float}
- Windowed aggregations (event-time) with watermark
- Sink: Delta Lake (exactly-once) at the specified output path
- Optional: OD flows (PU->DO) aggregation to a second output

spark-submit \
  --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.1,io.delta:delta-spark_2.12:3.2.0 \
  --conf spark.sql.extensions=io.delta.sql.DeltaSparkSessionExtension \
  --conf spark.sql.catalog.spark_catalog=org.apache.spark.sql.delta.catalog.DeltaCatalog \
  [plus your s3a/minio confs] \
  streaming/spark_job.py \
    --kafka.bootstrap localhost:9092 \
    --topic trips.yellow \
    --checkpoint s3a://cityscale/chk/pu_hour/ \
    --output s3a://cityscale/delta/pu_hour/
"""

import argparse
from pyspark.sql import SparkSession, functions as F, types as T


def parse_args():
    p = argparse.ArgumentParser(
        description="TLC Kafka -> Delta (windowed aggregations)"
    )
    p.add_argument(
        "--kafka.bootstrap",
        dest="bootstrap",
        required=True,
        help="Kafka bootstrap servers (host:port)",
    )
    p.add_argument("--topic", required=True, help="Kafka topic to subscribe")
    p.add_argument(
        "--startingOffsets",
        default="latest",
        choices=["earliest", "latest"],
        help="Kafka starting offsets",
    )
    p.add_argument(
        "--checkpoint",
        required=True,
        help="Checkpoint location (e.g., s3a://bucket/chk/pu_hour/)",
    )
    p.add_argument(
        "--output", required=True, help="Delta output path for PU hourly counts"
    )
    p.add_argument(
        "--od-output",
        default=None,
        help="Optional Delta output path for OD hourly counts",
    )
    p.add_argument(
        "--watermark",
        default="30 minutes",
        help="Event-time watermark (e.g., '30 minutes')",
    )
    p.add_argument(
        "--window", default="1 hour", help="Tumbling window duration (e.g., '1 hour')"
    )
    p.add_argument(
        "--trigger",
        default="30 seconds",
        help="Processing trigger (e.g., '30 seconds')",
    )
    p.add_argument(
        "--output-mode",
        default="update",
        choices=["append", "update", "complete"],
        help="Streaming output mode",
    )
    p.add_argument(
        "--rocksdb",
        action="store_true",
        help="Enable RocksDB state store provider (requires Spark 3.4+)",
    )
    return p.parse_args()


def build_spark(args):
    builder = (
        SparkSession.builder.appName("cityscale-stream-consumer")
        .config("spark.sql.shuffle.partitions", "200")
        .config("spark.sql.adaptive.skewJoin.enabled", "true")
    )
    if args.rocksdb:
        builder = builder.config(
            "spark.sql.streaming.stateStore.providerClass",
            "org.apache.spark.sql.execution.streaming.state.RocksDBStateStoreProvider",
        )
    # Delta configs are expected via --packages + --conf from spark-submit
    return builder.getOrCreate()


def main():
    args = parse_args()
    spark = build_spark(args)
    spark.sparkContext.setLogLevel("WARN")

    # ----- Kafka source -----
    raw = (
        spark.readStream.format("kafka")
        .option("kafka.bootstrap.servers", args.bootstrap)
        .option("subscribe", args.topic)
        .option("startingOffsets", args.startingOffsets)
        .load()
    )

    # ----- JSON parsing -----
    schema = T.StructType(
        [
            T.StructField("ts", T.StringType(), True),  # event time (string)
            T.StructField("pu", T.IntegerType(), True),  # pickup zone id
            T.StructField("do", T.IntegerType(), True),  # dropoff zone id
            T.StructField("dist", T.DoubleType(), True),  # trip distance
        ]
    )

    json_df = spark.read.json(
        raw.selectExpr("CAST(value AS STRING)").rdd.map(lambda r: r[0]), schema=schema
    )
    # Join back to have a proper streaming DataFrame (avoids extra scan)
    df = raw.select(
        F.from_json(F.col("value").cast("string"), schema).alias("j")
    ).select(
        F.to_timestamp("j.ts").alias("event_time"),
        F.col("j.pu").alias("PULocationID"),
        F.col("j.do").alias("DOLocationID"),
        F.col("j.dist").alias("trip_distance"),
    )

    # Basic hygiene filters (matches producer bounds)
    df = df.where(
        (F.col("trip_distance").isNotNull())
        & (F.col("trip_distance") >= F.lit(0.2))
        & (F.col("trip_distance") <= F.lit(40.0))
        & F.col("event_time").isNotNull()
    )

    # ----- PU hourly aggregation -----
    pu_agg = (
        df.withWatermark("event_time", args.watermark)
        .groupBy(F.window("event_time", args.window).alias("w"), F.col("PULocationID"))
        .agg(F.count(F.lit(1)).alias("count"))
        .select(
            F.col("PULocationID"),
            F.col("w.start").alias("hour"),
            F.col("count").cast("long").alias("count"),
        )
    )

    pu_query = (
        pu_agg.writeStream.format("delta")
        .outputMode(args.output_mode)
        .option("checkpointLocation", args.checkpoint.rstrip("/") + "/pu_hour/")
        .trigger(processingTime=args.trigger)
        .start(args.output)
    )

    # ----- Optional: OD hourly aggregation -----
    if args.od_output:
        od_agg = (
            df.withWatermark("event_time", args.watermark)
            .groupBy(
                F.window("event_time", args.window).alias("w"),
                F.col("PULocationID"),
                F.col("DOLocationID"),
            )
            .agg(F.count(F.lit(1)).alias("count"))
            .select(
                F.col("PULocationID"),
                F.col("DOLocationID"),
                F.col("w.start").alias("hour"),
                F.col("count").cast("long").alias("count"),
            )
        )

        od_query = (
            od_agg.writeStream.format("delta")
            .outputMode(args.output_mode)
            .option("checkpointLocation", args.checkpoint.rstrip("/") + "/od_hour/")
            .trigger(processingTime=args.trigger)
            .start(args.od_output)
        )
        # Await both
        pu_query.awaitTermination()
        od_query.awaitTermination()
    else:
        pu_query.awaitTermination()


if __name__ == "__main__":
    main()
