#!/usr/bin/env python3
"""
Iceberg Writer Utilities for Bronze Layer

Provides utility functions for writing to Iceberg tables, managing partitions,
and handling table maintenance operations.
"""

from typing import Dict, List, Optional
from pyspark.sql import SparkSession, DataFrame
from pyspark.sql import functions as F


class IcebergWriter:
    """Utilities for writing to Iceberg tables."""

    def __init__(self, spark: SparkSession, catalog_name: str):
        """
        Initialize Iceberg writer.

        Args:
            spark: Active Spark session
            catalog_name: Iceberg catalog name
        """
        self.spark = spark
        self.catalog_name = catalog_name

    def table_exists(self, table_name: str) -> bool:
        """
        Check if an Iceberg table exists.

        Args:
            table_name: Full table name (database.table)

        Returns:
            True if table exists, False otherwise
        """
        try:
            self.spark.table(f"{self.catalog_name}.{table_name}")
            return True
        except Exception:
            return False

    def get_table_metadata(self, table_name: str) -> Dict:
        """
        Get metadata for an Iceberg table.

        Args:
            table_name: Full table name (database.table)

        Returns:
            Dictionary with table metadata
        """
        full_name = f"{self.catalog_name}.{table_name}"

        # Get partition info
        partitions_df = self.spark.sql(f"""
            SELECT * FROM {full_name}.partitions
            LIMIT 10
        """)

        # Get file count and sizes
        files_df = self.spark.sql(f"""
            SELECT
                COUNT(*) as file_count,
                SUM(file_size_in_bytes) as total_size_bytes,
                AVG(file_size_in_bytes) as avg_file_size_bytes
            FROM {full_name}.files
        """)

        # Get record count
        record_count = self.spark.sql(f"SELECT COUNT(*) FROM {full_name}").collect()[0][0]

        return {
            "table": table_name,
            "record_count": record_count,
            "partitions": partitions_df.collect(),
            "file_stats": files_df.collect()[0].asDict() if files_df.count() > 0 else {}
        }

    def optimize_table(self, table_name: str, options: Dict = None):
        """
        Optimize an Iceberg table by compacting small files.

        Args:
            table_name: Full table name (database.table)
            options: Optional optimization parameters
        """
        full_name = f"{self.catalog_name}.{table_name}"

        # Default options
        opts = {
            "target-file-size-bytes": "134217728",  # 128MB
            "min-file-size-bytes": "33554432",      # 32MB
            "max-concurrent-file-group-rewrites": "5"
        }

        if options:
            opts.update(options)

        # Rewrite data files for compaction
        print(f"Optimizing table {full_name}...")

        rewrite_query = f"CALL {self.catalog_name}.system.rewrite_data_files("
        rewrite_query += f"table => '{full_name}'"

        for key, value in opts.items():
            rewrite_query += f", '{key}' => '{value}'"

        rewrite_query += ")"

        result = self.spark.sql(rewrite_query).collect()
        print(f"Optimization complete: {result}")

    def expire_snapshots(self, table_name: str, older_than_days: int = 7):
        """
        Expire old snapshots to reduce metadata size.

        Args:
            table_name: Full table name (database.table)
            older_than_days: Expire snapshots older than this many days
        """
        full_name = f"{self.catalog_name}.{table_name}"

        expire_query = f"""
        CALL {self.catalog_name}.system.expire_snapshots(
            table => '{full_name}',
            older_than => TIMESTAMP '{older_than_days} days ago',
            retain_last => 3
        )
        """

        result = self.spark.sql(expire_query).collect()
        print(f"Expired snapshots for {full_name}: {result}")

    def remove_orphan_files(self, table_name: str, older_than_days: int = 3):
        """
        Remove orphan files not referenced by any snapshot.

        Args:
            table_name: Full table name (database.table)
            older_than_days: Remove files older than this many days
        """
        full_name = f"{self.catalog_name}.{table_name}"

        remove_query = f"""
        CALL {self.catalog_name}.system.remove_orphan_files(
            table => '{full_name}',
            older_than => TIMESTAMP '{older_than_days} days ago'
        )
        """

        result = self.spark.sql(remove_query).collect()
        print(f"Removed orphan files for {full_name}: {result}")

    def validate_bronze_schema(self, df: DataFrame) -> bool:
        """
        Validate that a DataFrame has required bronze layer columns.

        Args:
            df: DataFrame to validate

        Returns:
            True if schema is valid, False otherwise
        """
        required_columns = [
            "kafka_timestamp",
            "kafka_partition",
            "kafka_offset",
            "kafka_topic",
            "consumer_id",
            "bronze_processing_time",
            "ingestion_year",
            "ingestion_month",
            "ingestion_day",
            "ingestion_hour"
        ]

        df_columns = set(df.columns)
        missing = set(required_columns) - df_columns

        if missing:
            print(f"Missing required bronze columns: {missing}")
            return False

        return True

    def create_dead_letter_table(self, table_name: str):
        """
        Create a dead letter queue table for failed records.

        Args:
            table_name: Name for the DLQ table
        """
        full_name = f"{self.catalog_name}.{table_name}"

        # Create schema for DLQ
        dlq_schema = """
            kafka_timestamp TIMESTAMP,
            kafka_topic STRING,
            kafka_partition INT,
            kafka_offset BIGINT,
            error_message STRING,
            error_timestamp TIMESTAMP,
            raw_value BINARY,
            consumer_id STRING
        """

        create_query = f"""
        CREATE TABLE IF NOT EXISTS {full_name} (
            {dlq_schema}
        )
        USING iceberg
        PARTITIONED BY (days(error_timestamp))
        """

        self.spark.sql(create_query)
        print(f"Created DLQ table: {full_name}")

    def write_to_dlq(self, error_records: DataFrame, dlq_table: str):
        """
        Write error records to dead letter queue.

        Args:
            error_records: DataFrame with error records
            dlq_table: DLQ table name
        """
        full_name = f"{self.catalog_name}.{dlq_table}"

        (
            error_records
            .withColumn("error_timestamp", F.current_timestamp())
            .writeTo(full_name)
            .append()
        )

    def get_partition_metrics(self, table_name: str) -> DataFrame:
        """
        Get metrics for each partition in the table.

        Args:
            table_name: Full table name (database.table)

        Returns:
            DataFrame with partition metrics
        """
        full_name = f"{self.catalog_name}.{table_name}"

        return self.spark.sql(f"""
            SELECT
                ingestion_year,
                ingestion_month,
                ingestion_day,
                ingestion_hour,
                COUNT(*) as record_count,
                MIN(kafka_timestamp) as earliest_kafka_timestamp,
                MAX(kafka_timestamp) as latest_kafka_timestamp,
                COUNT(DISTINCT kafka_partition) as distinct_partitions,
                COUNT(DISTINCT consumer_id) as distinct_consumers
            FROM {full_name}
            GROUP BY ingestion_year, ingestion_month, ingestion_day, ingestion_hour
            ORDER BY ingestion_year DESC, ingestion_month DESC, ingestion_day DESC, ingestion_hour DESC
        """)

    def get_consumer_lag(self, table_name: str) -> DataFrame:
        """
        Calculate consumer lag based on kafka timestamps.

        Args:
            table_name: Full table name (database.table)

        Returns:
            DataFrame with lag metrics
        """
        full_name = f"{self.catalog_name}.{table_name}"

        return self.spark.sql(f"""
            SELECT
                kafka_topic,
                kafka_partition,
                MAX(kafka_offset) as latest_offset,
                MAX(kafka_timestamp) as latest_timestamp,
                CURRENT_TIMESTAMP() - MAX(kafka_timestamp) as lag_seconds,
                COUNT(*) as messages_processed,
                COUNT(DISTINCT consumer_id) as consumer_count
            FROM {full_name}
            WHERE kafka_timestamp >= CURRENT_TIMESTAMP() - INTERVAL 1 HOUR
            GROUP BY kafka_topic, kafka_partition
            ORDER BY kafka_partition
        """)


def compact_bronze_tables(spark: SparkSession, catalog: str, tables: List[str]):
    """
    Batch compact multiple bronze tables.

    Args:
        spark: Spark session
        catalog: Iceberg catalog name
        tables: List of table names to compact
    """
    writer = IcebergWriter(spark, catalog)

    for table in tables:
        try:
            print(f"Compacting {table}...")
            writer.optimize_table(table)
            writer.expire_snapshots(table, older_than_days=7)
        except Exception as e:
            print(f"Failed to compact {table}: {e}")


def monitor_bronze_health(spark: SparkSession, catalog: str, tables: List[str]) -> Dict:
    """
    Monitor health metrics for bronze tables.

    Args:
        spark: Spark session
        catalog: Iceberg catalog name
        tables: List of table names to monitor

    Returns:
        Dictionary with health metrics
    """
    writer = IcebergWriter(spark, catalog)
    health = {}

    for table in tables:
        try:
            health[table] = {
                "metadata": writer.get_table_metadata(table),
                "lag": writer.get_consumer_lag(table).collect(),
                "partitions": writer.get_partition_metrics(table).limit(10).collect()
            }
        except Exception as e:
            health[table] = {"error": str(e)}

    return health