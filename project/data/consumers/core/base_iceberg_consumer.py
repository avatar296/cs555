#!/usr/bin/env python3
"""
Base Iceberg Consumer for Medallion Architecture

Abstract base class for consumers that read from Iceberg tables and write to Iceberg tables.
Used for Silver and Gold layers which process data from upstream Iceberg tables.
"""

import uuid
from abc import ABC, abstractmethod
from typing import Dict, Any, Optional, List

from pyspark.sql import SparkSession, DataFrame
from pyspark.sql import functions as F


class BaseIcebergConsumer(ABC):
    """
    Abstract base class for Iceberg-to-Iceberg streaming consumers.

    Designed for Silver and Gold layers that read from upstream Iceberg tables
    rather than directly from Kafka.
    """

    def __init__(
        self,
        name: str,
        source_table: str,
        target_table: str,
        checkpoint_location: str,
        trigger_interval: str = "5 seconds",  # Real-time: default to 5 second micro-batches
        starting_timestamp: int = 0,
        source_catalog: str = "bronze",
        target_catalog: str = "silver",
        warehouse_path: str = "s3a://lakehouse/iceberg"
    ):
        """
        Initialize base Iceberg consumer.

        Args:
            name: Consumer name for logging
            source_table: Source Iceberg table (e.g., "bronze.trips")
            target_table: Target Iceberg table (e.g., "silver.trips")
            checkpoint_location: Checkpoint directory for streaming
            trigger_interval: Processing trigger interval
            starting_timestamp: Starting timestamp for reading (0 = read all)
            source_catalog: Source catalog name
            target_catalog: Target catalog name
            warehouse_path: Base warehouse path for Iceberg tables
        """
        self.name = name
        self.source_table = source_table
        self.target_table = target_table
        self.checkpoint_location = checkpoint_location
        self.trigger_interval = trigger_interval
        self.starting_timestamp = starting_timestamp
        self.source_catalog = source_catalog
        self.target_catalog = target_catalog
        self.warehouse_path = warehouse_path

        # Consumer metadata
        self.consumer_id = str(uuid.uuid4())
        self.consumer_version = "1.0.0"

        # Spark session (initialized in setup)
        self.spark = None

    def setup(self):
        """Initialize Spark session with Iceberg configurations."""
        if self.spark is None:
            self.spark = (
                SparkSession.builder
                .appName(self.name)
                .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
                .config(f"spark.sql.catalog.{self.source_catalog}", "org.apache.iceberg.spark.SparkCatalog")
                .config(f"spark.sql.catalog.{self.source_catalog}.type", "hadoop")
                .config(f"spark.sql.catalog.{self.source_catalog}.warehouse", self.warehouse_path)
                .config(f"spark.sql.catalog.{self.target_catalog}", "org.apache.iceberg.spark.SparkCatalog")
                .config(f"spark.sql.catalog.{self.target_catalog}.type", "hadoop")
                .config(f"spark.sql.catalog.{self.target_catalog}.warehouse", self.warehouse_path)
                .config("spark.sql.adaptive.enabled", "false")
                .config("spark.sql.streaming.stateStore.stateSchemaCheck", "false")
                .getOrCreate()
            )

            print(f"[{self.name}] Spark session initialized")
            print(f"[{self.name}] Source catalog: {self.source_catalog}")
            print(f"[{self.name}] Target catalog: {self.target_catalog}")

    def read_source_table(self) -> DataFrame:
        """
        Read from source Iceberg table as a stream.

        Returns:
            Streaming DataFrame from source table
        """
        source_path = f"{self.warehouse_path}/{self.source_table.replace('.', '/')}"

        print(f"[{self.name}] Reading from: {source_path}")

        return (
            self.spark.readStream
            .format("iceberg")
            .option("stream-from-timestamp", self.starting_timestamp)
            .load(source_path)
        )

    def write_target_table(self, df: DataFrame):
        """
        Write to target Iceberg table.

        Args:
            df: Processed DataFrame to write

        Returns:
            StreamingQuery object
        """
        target_path = f"{self.warehouse_path}/{self.target_table.replace('.', '/')}"

        # Ensure target database exists
        target_db = self.target_table.split('.')[0] if '.' in self.target_table else self.target_catalog
        self.spark.sql(f"CREATE DATABASE IF NOT EXISTS {self.target_catalog}.{target_db}")

        print(f"[{self.name}] Writing to: {target_path}")

        return (
            df.writeStream
            .outputMode(self.get_output_mode())
            .format("iceberg")
            .option("path", target_path)
            .option("checkpointLocation", self.checkpoint_location)
            .option("fanout-enabled", "false")
            .trigger(processingTime=self.trigger_interval)
            .partitionBy(*self.get_partitioning_columns())
            .toTable(f"{self.target_catalog}.{self.target_table}")
        )

    # ==================== Abstract Methods ====================

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

        Args:
            df: Input DataFrame

        Returns:
            DataFrame with validation applied
        """
        pass

    @abstractmethod
    def transform(self, df: DataFrame) -> DataFrame:
        """
        Apply layer-specific transformations.

        Args:
            df: Input DataFrame

        Returns:
            Transformed DataFrame
        """
        pass

    @abstractmethod
    def get_output_mode(self) -> str:
        """
        Get the output mode for writing.

        Returns:
            Output mode string ("append", "update", "complete")
        """
        pass

    @abstractmethod
    def get_partitioning_columns(self) -> List[str]:
        """
        Get columns to use for partitioning the output.

        Returns:
            List of column names for partitioning
        """
        pass

    # ==================== Template Method ====================

    def process(self, df: DataFrame) -> DataFrame:
        """
        Template method for processing pipeline.

        Args:
            df: Input DataFrame from source table

        Returns:
            Processed DataFrame ready for output
        """
        # 1. Add layer metadata
        df_with_metadata = self.add_metadata(df)

        # 2. Validate data
        df_validated = self.validate(df_with_metadata)

        # 3. Apply transformations
        df_transformed = self.transform(df_validated)

        return df_transformed

    def run(self):
        """Main execution loop for Iceberg-to-Iceberg streaming."""
        # Initialize Spark if needed
        if self.spark is None:
            self.setup()

        print(f"\n[{self.name}] Starting Iceberg streaming consumer")
        print(f"Source table: {self.source_table}")
        print(f"Target table: {self.target_table}")
        print(f"Trigger interval: {self.trigger_interval}")
        print(f"Checkpoint: {self.checkpoint_location}")
        print("=" * 60)

        # Read from source Iceberg table
        source_df = self.read_source_table()

        # Process through pipeline
        processed_df = self.process(source_df)

        # Write to target Iceberg table
        query = self.write_target_table(processed_df)

        print(f"\n[{self.name}] Streaming started successfully")
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

    # ==================== Utility Methods ====================

    def get_source_table_stats(self) -> Dict[str, Any]:
        """
        Get statistics about the source table.

        Returns:
            Dictionary with table statistics
        """
        if self.spark is None:
            self.setup()

        source_path = f"{self.warehouse_path}/{self.source_table.replace('.', '/')}"

        try:
            df = self.spark.read.format("iceberg").load(source_path)
            count = df.count()

            # Get snapshot info
            snapshots_df = self.spark.sql(f"""
                SELECT * FROM {self.source_catalog}.{self.source_table}.snapshots
                ORDER BY committed_at DESC
                LIMIT 5
            """)

            return {
                "table": self.source_table,
                "record_count": count,
                "recent_snapshots": snapshots_df.collect()
            }
        except Exception as e:
            return {"error": str(e)}

    def validate_table_exists(self, table_name: str, catalog: str) -> bool:
        """
        Check if a table exists in the specified catalog.

        Args:
            table_name: Table name to check
            catalog: Catalog name

        Returns:
            True if table exists, False otherwise
        """
        try:
            self.spark.table(f"{catalog}.{table_name}")
            return True
        except Exception:
            return False

    def get_checkpoint_status(self) -> Dict[str, Any]:
        """
        Get information about checkpoint status.

        Returns:
            Dictionary with checkpoint information
        """
        # This would check the checkpoint directory for status
        # Simplified for now
        return {
            "location": self.checkpoint_location,
            "exists": True  # Would check filesystem
        }