#!/usr/bin/env python3
"""
Base Consumer Class for Bronze Layer Kafka Consumers

Provides common functionality for all bronze layer consumers following medallion architecture.
Handles Spark session setup, Kafka streaming, Avro deserialization, and Iceberg writing.
"""

import os
import sys
import uuid
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Dict, Any, Optional

from pyspark.sql import SparkSession, DataFrame
from pyspark.sql import functions as F
from pyspark.sql.avro.functions import from_avro
import requests


class BaseBronzeConsumer(ABC):
    """Base class for all bronze layer Kafka consumers."""

    def __init__(
        self,
        name: str,
        source_topic: str,
        schema_subject: str,
        target_table: str,
        kafka_bootstrap: str = None,
        schema_registry_url: str = None,
        checkpoint_location: str = None,
        trigger_interval: str = None,
        max_offsets_per_trigger: int = None,
        starting_offsets: str = None
    ):
        """
        Initialize bronze consumer.

        Args:
            name: Consumer name for logging (e.g., "bronze-trips")
            source_topic: Kafka topic to consume from
            schema_subject: Schema Registry subject name
            target_table: Target Iceberg table name
            kafka_bootstrap: Kafka bootstrap servers
            schema_registry_url: Schema Registry URL
            checkpoint_location: Checkpoint directory for fault tolerance
            trigger_interval: Processing trigger interval (e.g., "30 seconds")
            max_offsets_per_trigger: Max messages per trigger
            starting_offsets: Starting offset position ("earliest", "latest")
        """
        self.name = name
        self.source_topic = source_topic
        self.schema_subject = schema_subject
        self.target_table = target_table

        # Configuration with defaults
        self.kafka_bootstrap = kafka_bootstrap or os.getenv("KAFKA_BOOTSTRAP", "kafka:9092")
        self.schema_registry_url = schema_registry_url or os.getenv("SCHEMA_REGISTRY_URL", "http://schema-registry:8081")
        self.checkpoint_location = checkpoint_location or os.getenv("CHECKPOINT_LOCATION", f"/tmp/checkpoint/{name}")
        self.trigger_interval = trigger_interval or os.getenv("TRIGGER_INTERVAL", "30 seconds")
        self.max_offsets_per_trigger = max_offsets_per_trigger or int(os.getenv("MAX_OFFSETS_PER_TRIGGER", "10000"))
        self.starting_offsets = starting_offsets or os.getenv("STARTING_OFFSETS", "latest")

        # Consumer metadata
        self.consumer_id = str(uuid.uuid4())
        self.consumer_version = "1.0.0"

        # Spark session (initialized in setup)
        self.spark = None
        self.avro_schema = None

        # Iceberg configuration
        self.iceberg_catalog = os.getenv("ICEBERG_CATALOG", "bronze")
        self.warehouse_path = os.getenv("WAREHOUSE_PATH", "s3a://lakehouse/iceberg")
        self.s3_endpoint = os.getenv("S3_ENDPOINT", "http://minio:9000")
        self.s3_access_key = os.getenv("S3_ACCESS_KEY", "admin")
        self.s3_secret_key = os.getenv("S3_SECRET_KEY", "admin123")

    def create_spark_session(self) -> SparkSession:
        """Create Spark session with required configurations."""
        spark = (
            SparkSession.builder
            .appName(f"{self.name}-consumer")
            .config("spark.sql.adaptive.enabled", "false")  # Disabled for streaming
            .config("spark.sql.streaming.stateStore.stateSchemaCheck", "false")
            .config("spark.sql.session.timeZone", "UTC")
            # Required JARs
            .config("spark.jars.packages", ",".join([
                "org.apache.spark:spark-avro_2.12:3.5.0",
                "org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.4.2",
                "org.apache.hadoop:hadoop-aws:3.3.4",
                "com.amazonaws:aws-java-sdk-bundle:1.12.262"
            ]))
            # Iceberg catalog configuration
            .config(f"spark.sql.catalog.{self.iceberg_catalog}", "org.apache.iceberg.spark.SparkCatalog")
            .config(f"spark.sql.catalog.{self.iceberg_catalog}.type", "hadoop")
            .config(f"spark.sql.catalog.{self.iceberg_catalog}.warehouse", self.warehouse_path)
            # MinIO/S3 configuration
            .config("spark.hadoop.fs.s3a.endpoint", self.s3_endpoint)
            .config("spark.hadoop.fs.s3a.path.style.access", "true")
            .config("spark.hadoop.fs.s3a.access.key", self.s3_access_key)
            .config("spark.hadoop.fs.s3a.secret.key", self.s3_secret_key)
            .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
            .config("spark.hadoop.fs.s3a.connection.ssl.enabled", "false")
            .getOrCreate()
        )
        spark.sparkContext.setLogLevel("WARN")
        return spark

    def fetch_avro_schema(self) -> str:
        """Fetch Avro schema from Schema Registry."""
        try:
            resp = requests.get(
                f"{self.schema_registry_url}/subjects/{self.schema_subject}/versions/latest",
                timeout=10
            )
            if resp.status_code == 200:
                info = resp.json()
                schema = info["schema"]
                print(f"✓ [{self.name}] Fetched Avro schema (id={info['id']}, version={info['version']})", file=sys.stderr)
                return schema
            else:
                print(f"✗ [{self.name}] Schema not found for subject {self.schema_subject}", file=sys.stderr)
                sys.exit(1)
        except Exception as e:
            print(f"✗ [{self.name}] Failed to connect to Schema Registry: {e}", file=sys.stderr)
            sys.exit(1)

    def read_kafka_stream(self) -> DataFrame:
        """Create Kafka streaming DataFrame."""
        return (
            self.spark.readStream
            .format("kafka")
            .option("kafka.bootstrap.servers", self.kafka_bootstrap)
            .option("subscribe", self.source_topic)
            .option("startingOffsets", self.starting_offsets)
            .option("failOnDataLoss", "false")
            .option("maxOffsetsPerTrigger", self.max_offsets_per_trigger)
            .load()
        )

    def deserialize_avro(self, kafka_df: DataFrame) -> DataFrame:
        """
        Deserialize Avro data from Kafka messages.
        Handles Confluent wire format (5-byte header).
        """
        # Extract Kafka metadata and deserialize Avro
        parsed_df = (
            kafka_df.select(
                # Kafka metadata columns
                F.col("key").cast("string").alias("kafka_key"),
                F.col("partition").alias("kafka_partition"),
                F.col("offset").alias("kafka_offset"),
                F.col("timestamp").alias("kafka_timestamp"),
                F.col("timestampType").alias("kafka_timestamp_type"),
                F.col("topic").alias("kafka_topic"),

                # Deserialize Avro (skip 5-byte Confluent header)
                from_avro(
                    F.expr("substring(value, 6, length(value)-5)"),
                    self.avro_schema
                ).alias("data")
            )
            .select(
                # Flatten the data structure
                "kafka_key",
                "kafka_partition",
                "kafka_offset",
                "kafka_timestamp",
                "kafka_timestamp_type",
                "kafka_topic",
                "data.*"  # Expand all fields from the Avro record
            )
        )

        return parsed_df

    def add_bronze_metadata(self, df: DataFrame) -> DataFrame:
        """
        Add bronze layer metadata columns.

        Args:
            df: DataFrame with deserialized data

        Returns:
            DataFrame with additional bronze layer metadata
        """
        return (
            df
            # Add consumer metadata
            .withColumn("consumer_id", F.lit(self.consumer_id))
            .withColumn("consumer_version", F.lit(self.consumer_version))
            .withColumn("bronze_processing_time", F.current_timestamp())

            # Add partitioning columns based on ingestion time (kafka_timestamp)
            .withColumn("ingestion_year", F.year("kafka_timestamp"))
            .withColumn("ingestion_month", F.month("kafka_timestamp"))
            .withColumn("ingestion_day", F.dayofmonth("kafka_timestamp"))
            .withColumn("ingestion_hour", F.hour("kafka_timestamp"))

            # Add a composite partition key for efficient querying
            .withColumn(
                "ingestion_date",
                F.date_format("kafka_timestamp", "yyyy-MM-dd")
            )
        )

    def ensure_database_exists(self):
        """Create Iceberg database if it doesn't exist."""
        db_name = self.target_table.split(".")[0] if "." in self.target_table else "bronze"
        self.spark.sql(f"CREATE DATABASE IF NOT EXISTS {self.iceberg_catalog}.{db_name}")
        print(f"✓ [{self.name}] Ensured database {self.iceberg_catalog}.{db_name} exists", file=sys.stderr)

    def ensure_table_exists(self, schema_df: DataFrame):
        """Create Iceberg table if it doesn't exist."""
        full_table_name = f"{self.iceberg_catalog}.{self.target_table}"

        try:
            self.spark.table(full_table_name)
            print(f"✓ [{self.name}] Table {full_table_name} already exists", file=sys.stderr)
        except Exception:
            print(f"✓ [{self.name}] Creating Iceberg table {full_table_name}...", file=sys.stderr)

            # Create empty DataFrame with schema
            empty_df = self.spark.createDataFrame([], schema_df.schema)

            # Create partitioned Iceberg table
            (
                empty_df.writeTo(full_table_name)
                .using("iceberg")
                .partitionedBy("ingestion_year", "ingestion_month", "ingestion_day", "ingestion_hour")
                .tableProperty("write.target-file-size-bytes", "134217728")  # 128MB files
                .tableProperty("write.distribution-mode", "hash")
                .tableProperty("write.metadata.compression-codec", "gzip")
                .createOrReplace()
            )
            print(f"✓ [{self.name}] Created table {full_table_name} with ingestion time partitioning", file=sys.stderr)

    def create_streaming_query(self, df: DataFrame):
        """
        Create the streaming query to write to Iceberg.

        Args:
            df: Processed DataFrame ready to write

        Returns:
            StreamingQuery object
        """
        full_table_name = f"{self.iceberg_catalog}.{self.target_table}"

        return (
            df.writeStream
            .outputMode("append")  # Bronze layer is append-only
            .format("iceberg")
            .option("path", f"{self.warehouse_path}/{self.target_table.replace('.', '/')}")
            .option("checkpointLocation", self.checkpoint_location)
            .trigger(processingTime=self.trigger_interval)
            .toTable(full_table_name)
        )

    def setup(self):
        """Setup consumer components."""
        print(f"Starting {self.name} Bronze Consumer", file=sys.stderr)
        print(f"Source Topic: {self.source_topic}", file=sys.stderr)
        print(f"Target Table: {self.iceberg_catalog}.{self.target_table}", file=sys.stderr)
        print(f"Checkpoint: {self.checkpoint_location}", file=sys.stderr)
        print(f"Trigger: {self.trigger_interval}", file=sys.stderr)

        # Initialize Spark
        self.spark = self.create_spark_session()

        # Fetch schema
        self.avro_schema = self.fetch_avro_schema()

        # Ensure database exists
        self.ensure_database_exists()

    def run(self):
        """Main consumer execution loop."""
        self.setup()

        # Read from Kafka
        kafka_df = self.read_kafka_stream()

        # Deserialize Avro
        parsed_df = self.deserialize_avro(kafka_df)

        # Add bronze metadata
        bronze_df = self.add_bronze_metadata(parsed_df)

        # Apply any consumer-specific transformations
        processed_df = self.process_batch(bronze_df)

        # Ensure table exists with correct schema
        self.ensure_table_exists(processed_df)

        # Create and start streaming query
        query = self.create_streaming_query(processed_df)

        # Print status
        print("\n" + "=" * 72, file=sys.stderr)
        print(f"[{self.name}] Streaming started", file=sys.stderr)
        print(f"Reading from: {self.source_topic}", file=sys.stderr)
        print(f"Writing to: {self.iceberg_catalog}.{self.target_table}", file=sys.stderr)
        print(f"Partitioned by: ingestion_year/month/day/hour", file=sys.stderr)
        print("Press Ctrl+C to stop", file=sys.stderr)
        print("=" * 72 + "\n", file=sys.stderr)

        try:
            query.awaitTermination()
        except KeyboardInterrupt:
            print(f"\n[{self.name}] Shutting down...", file=sys.stderr)
            query.stop()
            self.spark.stop()
            print(f"[{self.name}] Stopped cleanly", file=sys.stderr)

    @abstractmethod
    def process_batch(self, df: DataFrame) -> DataFrame:
        """
        Process a batch of records. Override for consumer-specific logic.

        Args:
            df: DataFrame with bronze metadata added

        Returns:
            Processed DataFrame ready for writing
        """
        # Default implementation - no additional processing
        return df

    @abstractmethod
    def get_consumer_config(self) -> Dict[str, Any]:
        """
        Get consumer-specific configuration.
        Override to provide custom configuration.

        Returns:
            Dictionary of configuration parameters
        """
        pass