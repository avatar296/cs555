#!/usr/bin/env python3
"""
Abstract Base Stream Consumer for Medallion Architecture

Provides the foundation for all streaming consumers (bronze, silver, gold)
following SOLID principles and enabling DRY code reuse.
"""

import uuid
from abc import ABC, abstractmethod
from typing import Dict, Any, Optional

from pyspark.sql import SparkSession, DataFrame
from pyspark.sql import functions as F
from pyspark.sql.avro.functions import from_avro
import requests


class BaseStreamConsumer(ABC):
    """
    Abstract base class for all medallion layer streaming consumers.

    Implements Template Method pattern for processing pipeline.
    """

    def __init__(
        self,
        name: str,
        source_topic: str,
        target_table: str,
        kafka_bootstrap: str,
        schema_registry_url: str,
        checkpoint_location: str,
        trigger_interval: str = "30 seconds",
        max_offsets_per_trigger: int = 10000,
        starting_offsets: str = "latest",
        schema_subject: str = None
    ):
        """
        Initialize base stream consumer.

        Args:
            name: Consumer name for logging
            source_topic: Kafka topic to consume from
            target_table: Target table name
            kafka_bootstrap: Kafka bootstrap servers
            schema_registry_url: Schema Registry URL
            checkpoint_location: Checkpoint directory
            trigger_interval: Processing trigger interval
            max_offsets_per_trigger: Max messages per trigger
            starting_offsets: Starting offset position
            schema_subject: Schema Registry subject (auto-generated if None)
        """
        self.name = name
        self.source_topic = source_topic
        self.target_table = target_table
        self.kafka_bootstrap = kafka_bootstrap
        self.schema_registry_url = schema_registry_url
        self.checkpoint_location = checkpoint_location
        self.trigger_interval = trigger_interval
        self.max_offsets_per_trigger = max_offsets_per_trigger
        self.starting_offsets = starting_offsets
        self.schema_subject = schema_subject or f"{source_topic}-value"

        # Consumer metadata
        self.consumer_id = str(uuid.uuid4())
        self.consumer_version = "2.0.0"

        # Spark session (initialized in setup)
        self.spark = None
        self.avro_schema = None

    # ==================== Abstract Methods ====================
    # These MUST be implemented by layer-specific classes

    @abstractmethod
    def add_metadata(self, df: DataFrame) -> DataFrame:
        """
        Add layer-specific metadata columns.

        Args:
            df: Input DataFrame

        Returns:
            DataFrame with metadata columns added
        """
        pass

    @abstractmethod
    def validate(self, df: DataFrame) -> DataFrame:
        """
        Perform layer-specific validation.

        Bronze: No validation (pass through)
        Silver: Add quality flags
        Gold: Business rule validation

        Args:
            df: Input DataFrame

        Returns:
            DataFrame with validation columns/filters applied
        """
        pass

    @abstractmethod
    def transform(self, df: DataFrame) -> DataFrame:
        """
        Perform layer-specific transformations.

        Bronze: No transformation (pass through)
        Silver: Cleansing, normalization, deduplication
        Gold: Aggregations, joins, KPI calculations

        Args:
            df: Input DataFrame

        Returns:
            Transformed DataFrame
        """
        pass

    @abstractmethod
    def get_output_mode(self) -> str:
        """
        Get the output mode for this layer.

        Returns:
            "append" for bronze/silver, "complete" for gold aggregations
        """
        pass

    @abstractmethod
    def get_partitioning_columns(self) -> list:
        """
        Get partitioning columns for the output table.

        Returns:
            List of column names to partition by
        """
        pass

    # ==================== Template Method ====================
    # Defines the processing pipeline structure

    def process_batch(self, df: DataFrame) -> DataFrame:
        """
        Template method defining the processing pipeline.

        This method orchestrates the processing steps in order:
        1. Add metadata
        2. Validate
        3. Transform

        Subclasses should NOT override this method.

        Args:
            df: Raw DataFrame from Kafka

        Returns:
            Processed DataFrame ready for output
        """
        # Step 1: Add layer-specific metadata
        df = self.add_metadata(df)

        # Step 2: Validate data (may add flags or filter)
        df = self.validate(df)

        # Step 3: Apply transformations
        df = self.transform(df)

        return df

    # ==================== Common Functionality ====================
    # Shared by all layers

    def create_spark_session(self) -> SparkSession:
        """Create Spark session with required configurations."""
        spark = (
            SparkSession.builder
            .appName(f"{self.name}-consumer")
            .config("spark.sql.adaptive.enabled", "false")
            .config("spark.sql.streaming.stateStore.stateSchemaCheck", "false")
            .config("spark.sql.session.timeZone", "UTC")
            # Required JARs
            .config("spark.jars.packages", ",".join([
                "org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0",
                "org.apache.spark:spark-avro_2.12:3.5.0",
                "org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.4.2",
                "org.apache.hadoop:hadoop-aws:3.3.4",
                "com.amazonaws:aws-java-sdk-bundle:1.12.262"
            ]))
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
                print(f"✓ [{self.name}] Fetched schema (id={info['id']}, v={info['version']})")
                return schema
            else:
                raise ValueError(f"Schema not found for {self.schema_subject}")
        except Exception as e:
            raise RuntimeError(f"Failed to fetch schema: {e}")

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
        parsed_df = (
            kafka_df.select(
                # Kafka metadata
                F.col("key").cast("string").alias("kafka_key"),
                F.col("partition").alias("kafka_partition"),
                F.col("offset").alias("kafka_offset"),
                F.col("timestamp").alias("kafka_timestamp"),
                F.col("topic").alias("kafka_topic"),

                # Deserialize Avro (skip 5-byte header)
                from_avro(
                    F.expr("substring(value, 6, length(value)-5)"),
                    self.avro_schema
                ).alias("data")
            )
            .select(
                # Flatten
                "kafka_key",
                "kafka_partition",
                "kafka_offset",
                "kafka_timestamp",
                "kafka_topic",
                "data.*"
            )
        )
        return parsed_df

    def setup(self):
        """Setup consumer components."""
        print(f"Starting {self.name} Consumer")
        print(f"Source: {self.source_topic} → Target: {self.target_table}")

        # Initialize Spark
        self.spark = self.create_spark_session()

        # Fetch schema
        self.avro_schema = self.fetch_avro_schema()

    def run(self):
        """Main consumer execution loop."""
        self.setup()

        # Read from Kafka
        kafka_df = self.read_kafka_stream()

        # Deserialize Avro
        parsed_df = self.deserialize_avro(kafka_df)

        # Process through template method
        processed_df = self.process_batch(parsed_df)

        # Write to output (implementation depends on layer)
        query = self.write_output(processed_df)

        print(f"\n[{self.name}] Streaming started")
        print(f"Output mode: {self.get_output_mode()}")
        print(f"Partitioning: {self.get_partitioning_columns()}")
        print("Press Ctrl+C to stop\n")

        try:
            query.awaitTermination()
        except KeyboardInterrupt:
            print(f"\n[{self.name}] Shutting down...")
            query.stop()
            self.spark.stop()
            print(f"[{self.name}] Stopped cleanly")

    @abstractmethod
    def write_output(self, df: DataFrame):
        """
        Write output to target system.
        Must be implemented by concrete classes.

        Args:
            df: Processed DataFrame to write

        Returns:
            StreamingQuery object
        """
        pass