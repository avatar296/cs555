#!/usr/bin/env python3
"""
Spark Streaming Consumer - Kafka to Console
Continuously processes trip data from Kafka with windowed aggregations
Ready to be enhanced with Iceberg table writes
"""

from pyspark.sql import SparkSession
from pyspark.sql import functions as F
from pyspark.sql.types import (
    StructType,
    StructField,
    StringType,
    IntegerType,
    DoubleType,
)


def create_spark_session():
    """Create Spark session with Kafka, Iceberg, and RocksDB support"""
    spark = (
        SparkSession.builder.appName("KafkaStreamingConsumer")
        .config("spark.sql.adaptive.enabled", "true")
        .config("spark.sql.adaptive.coalescePartitions.enabled", "true")
        # RocksDB state store for better memory management and checkpointing
        .config("spark.sql.streaming.stateStore.providerClass",
                "org.apache.spark.sql.execution.streaming.state.RocksDBStateStoreProvider")
        .config("spark.sql.streaming.stateStore.rocksdb.changelogCheckpointing.enabled", "true")
        # State store configurations
        .config("spark.sql.streaming.stateStore.stateSchemaCheck", "false")
        .config("spark.sql.streaming.stateStore.rocksdb.compactOnCommit", "true")
        .getOrCreate()
    )

    spark.sparkContext.setLogLevel("WARN")
    return spark


def main():
    print("Starting Kafka Streaming Consumer...")
    print("Press Ctrl+C to stop")

    # Create Spark session
    spark = create_spark_session()

    # Define schema for incoming JSON messages
    schema = StructType(
        [
            StructField("ts", StringType(), True),
            StructField("pu", IntegerType(), True),
            StructField("do", IntegerType(), True),
            StructField("dist", DoubleType(), True),
        ]
    )

    # Read from Kafka
    kafka_df = (
        spark.readStream.format("kafka")
        .option("kafka.bootstrap.servers", "kafka:9092")
        .option("subscribe", "trips.yellow")
        .option("startingOffsets", "latest")
        .option("maxOffsetsPerTrigger", 10000)
        .load()
    )

    # Parse JSON from Kafka value
    parsed_df = kafka_df.select(
        F.col("key").cast("string").alias("key"),
        F.from_json(F.col("value").cast("string"), schema).alias("data"),
        F.col("timestamp"),
    ).select(
        F.col("key"),
        F.col("data.ts").alias("event_time"),
        F.col("data.pu").alias("pickup_location"),
        F.col("data.do").alias("dropoff_location"),
        F.col("data.dist").alias("distance"),
        F.col("timestamp").alias("kafka_timestamp"),
    )

    # Add transformations and timestamp parsing
    processed_df = (
        parsed_df.withColumn("processing_time", F.current_timestamp())
        .withColumn(
            "distance_category",
            F.when(F.col("distance") < 2, "short")
            .when(F.col("distance") < 10, "medium")
            .otherwise("long"),
        )
        .withColumn("event_timestamp", F.to_timestamp(F.col("event_time")))
    )

    # Create windowed aggregations
    windowed_stats = (
        processed_df.withWatermark("event_timestamp", "1 minute")
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
            F.col("distance_category"),
            F.col("trip_count"),
            F.round(F.col("avg_distance"), 2).alias("avg_distance"),
            F.round(F.col("min_distance"), 2).alias("min_distance"),
            F.round(F.col("max_distance"), 2).alias("max_distance"),
        )
    )

    # Output to console with checkpointing
    console_query = (
        windowed_stats.writeStream.outputMode("update")
        .format("console")
        .option("truncate", False)
        .option("checkpointLocation", "/opt/spark/work-dir/checkpoint/trips")
        .trigger(processingTime="10 seconds")
        .start()
    )

    print("\n" + "=" * 60)
    print("Streaming consumer started successfully!")
    print("Reading from: trips.yellow")
    print("Processing window: 30 seconds, sliding every 10 seconds")
    print("Spark UI: http://localhost:8081")
    print("=" * 60 + "\n")

    try:
        # Run continuously until interrupted
        console_query.awaitTermination()
    except KeyboardInterrupt:
        print("\n\nShutting down streaming consumer...")
        console_query.stop()
        spark.stop()
        print("Consumer stopped successfully")


if __name__ == "__main__":
    main()
