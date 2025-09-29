#!/usr/bin/env python3
"""
Bronze Layer Consumer Base Class

Implements bronze-specific behavior for the medallion architecture.
Bronze layer focuses on raw data preservation with no transformations.
"""

from pyspark.sql import DataFrame
from pyspark.sql import functions as F

from .base_stream_consumer import BaseStreamConsumer


class BronzeConsumer(BaseStreamConsumer):
    """
    Bronze layer consumer base class.

    Bronze layer responsibilities:
    - Preserve raw data exactly as received
    - Add ingestion metadata for lineage
    - Partition by ingestion time (not event time)
    - No validation or transformation
    """

    def add_metadata(self, df: DataFrame) -> DataFrame:
        """
        Add bronze layer metadata.

        Adds:
        - Consumer tracking (ID, version)
        - Ingestion timestamp
        - Partitioning columns based on ingestion time

        Args:
            df: Input DataFrame with Kafka metadata

        Returns:
            DataFrame with bronze metadata added
        """
        return (
            df
            # Consumer metadata
            .withColumn("consumer_id", F.lit(self.consumer_id))
            .withColumn("consumer_version", F.lit(self.consumer_version))
            .withColumn("bronze_ingestion_time", F.current_timestamp())

            # Partitioning columns (based on ingestion time)
            .withColumn("ingestion_year", F.year("kafka_timestamp"))
            .withColumn("ingestion_month", F.month("kafka_timestamp"))
            .withColumn("ingestion_day", F.dayofmonth("kafka_timestamp"))
            .withColumn("ingestion_hour", F.hour("kafka_timestamp"))

            # Composite date for easier querying
            .withColumn(
                "ingestion_date",
                F.date_format("kafka_timestamp", "yyyy-MM-dd")
            )
        )

    def validate(self, df: DataFrame) -> DataFrame:
        """
        Bronze layer validation - none.

        Bronze preserves everything, no validation.

        Args:
            df: Input DataFrame

        Returns:
            Unchanged DataFrame
        """
        return df

    def transform(self, df: DataFrame) -> DataFrame:
        """
        Bronze layer transformation - none.

        Bronze preserves raw data, no transformation.

        Args:
            df: Input DataFrame

        Returns:
            Unchanged DataFrame
        """
        return df

    def get_output_mode(self) -> str:
        """
        Bronze uses append mode.

        Returns:
            "append" - bronze is append-only
        """
        return "append"

    def get_partitioning_columns(self) -> list:
        """
        Bronze partitions by ingestion time.

        Returns:
            List of partitioning columns
        """
        return ["ingestion_year", "ingestion_month", "ingestion_day", "ingestion_hour"]

    def write_output(self, df: DataFrame):
        """
        Write to Iceberg table with bronze-specific settings.

        Args:
            df: Processed DataFrame to write

        Returns:
            StreamingQuery object
        """
        # Configure Iceberg catalog
        catalog_name = self.target_table.split('.')[0] if '.' in self.target_table else "bronze"

        # Ensure database exists
        self.spark.sql(f"CREATE DATABASE IF NOT EXISTS {catalog_name}")

        # Configure Iceberg write
        return (
            df.writeStream
            .outputMode(self.get_output_mode())
            .format("iceberg")
            .option("path", f"s3a://lakehouse/iceberg/{self.target_table.replace('.', '/')}")
            .option("checkpointLocation", self.checkpoint_location)
            .option("fanout-enabled", "true")  # Optimize for high-volume writes
            .trigger(processingTime=self.trigger_interval)
            .partitionBy(*self.get_partitioning_columns())
            .toTable(self.target_table)
        )