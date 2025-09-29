#!/usr/bin/env python3
"""
Silver Layer Consumer Base Class

Implements silver-specific behavior for the medallion architecture.
Silver layer focuses on data cleansing, validation, and normalization.
"""

from abc import abstractmethod
from pyspark.sql import DataFrame
from pyspark.sql import functions as F
from pyspark.sql.window import Window

from .base_stream_consumer import BaseStreamConsumer


class SilverConsumer(BaseStreamConsumer):
    """
    Silver layer consumer base class.

    Silver layer responsibilities:
    - Data quality validation
    - Cleansing and normalization
    - Deduplication
    - Schema conformance
    - Partition by event time (not ingestion time)
    """

    def __init__(self, *args, quality_threshold: float = 0.8, **kwargs):
        """
        Initialize silver consumer.

        Args:
            quality_threshold: Minimum quality score to accept (0.0-1.0)
            *args, **kwargs: Passed to parent class
        """
        super().__init__(*args, **kwargs)
        self.quality_threshold = quality_threshold

    def add_metadata(self, df: DataFrame) -> DataFrame:
        """
        Add silver layer metadata.

        Adds:
        - Silver processing timestamp
        - Data lineage information
        - Quality score placeholder

        Args:
            df: Input DataFrame

        Returns:
            DataFrame with silver metadata added
        """
        return (
            df
            .withColumn("silver_processing_time", F.current_timestamp())
            .withColumn("silver_consumer_id", F.lit(self.consumer_id))
            .withColumn("silver_consumer_version", F.lit(self.consumer_version))
            # Initialize quality score (will be calculated in validate)
            .withColumn("quality_score", F.lit(0.0))
        )

    @abstractmethod
    def calculate_quality_score(self, df: DataFrame) -> DataFrame:
        """
        Calculate quality score for each record.

        Must be implemented by specific consumers.

        Args:
            df: Input DataFrame

        Returns:
            DataFrame with quality_score column updated
        """
        pass

    def validate(self, df: DataFrame) -> DataFrame:
        """
        Silver layer validation.

        Performs quality checks and filters based on quality threshold.

        Args:
            df: Input DataFrame

        Returns:
            DataFrame with quality validation applied
        """
        # Calculate quality score (implemented by subclasses)
        df = self.calculate_quality_score(df)

        # Add validation flag
        df = df.withColumn(
            "is_valid",
            F.col("quality_score") >= self.quality_threshold
        )

        # Log quality metrics (in production, write to metrics system)
        # Note: This is a simplified version for demonstration
        return df

    def deduplicate(self, df: DataFrame, keys: list, window_duration: str = "1 hour") -> DataFrame:
        """
        Remove duplicate records within a time window.

        Args:
            df: Input DataFrame
            keys: Columns to use for deduplication
            window_duration: Time window for deduplication

        Returns:
            Deduplicated DataFrame
        """
        # Add row number partitioned by keys and ordered by event time
        window_spec = Window.partitionBy(*keys).orderBy(F.desc("event_timestamp"))

        df_with_row_num = df.withColumn("row_num", F.row_number().over(window_spec))

        # Keep only the first occurrence
        return df_with_row_num.filter(F.col("row_num") == 1).drop("row_num")

    def normalize_timestamps(self, df: DataFrame, timestamp_cols: list) -> DataFrame:
        """
        Normalize timestamps to consistent format.

        Args:
            df: Input DataFrame
            timestamp_cols: List of timestamp columns to normalize

        Returns:
            DataFrame with normalized timestamps
        """
        for col_name in timestamp_cols:
            if col_name in df.columns:
                df = df.withColumn(
                    f"{col_name}_normalized",
                    F.to_timestamp(F.col(col_name), "yyyy-MM-dd HH:mm:ss")
                )
        return df

    def transform(self, df: DataFrame) -> DataFrame:
        """
        Silver layer transformation.

        Default implementation:
        - Filters invalid records
        - Can be overridden for specific transformations

        Args:
            df: Input DataFrame

        Returns:
            Transformed DataFrame
        """
        # Filter out invalid records
        df = df.filter(F.col("is_valid") == True)

        # Additional transformations can be added by subclasses
        return df

    def get_output_mode(self) -> str:
        """
        Silver uses append mode.

        Returns:
            "append" - silver is append-only after cleansing
        """
        return "append"

    def get_partitioning_columns(self) -> list:
        """
        Silver partitions by event time (if available).

        Returns:
            List of partitioning columns
        """
        # Partition by event time if available, otherwise by processing time
        return ["event_year", "event_month", "event_day"]

    def add_event_time_partitions(self, df: DataFrame, event_time_col: str) -> DataFrame:
        """
        Add event time partitioning columns.

        Args:
            df: Input DataFrame
            event_time_col: Name of event time column

        Returns:
            DataFrame with partitioning columns added
        """
        if event_time_col in df.columns:
            df = (
                df
                .withColumn("event_year", F.year(event_time_col))
                .withColumn("event_month", F.month(event_time_col))
                .withColumn("event_day", F.dayofmonth(event_time_col))
                .withColumn("event_hour", F.hour(event_time_col))
                .withColumn("event_date", F.to_date(event_time_col))
            )
        else:
            # Fallback to processing time if no event time
            df = (
                df
                .withColumn("event_year", F.year("silver_processing_time"))
                .withColumn("event_month", F.month("silver_processing_time"))
                .withColumn("event_day", F.dayofmonth("silver_processing_time"))
            )
        return df

    def write_output(self, df: DataFrame):
        """
        Write to Iceberg table with silver-specific settings.

        Args:
            df: Processed DataFrame to write

        Returns:
            StreamingQuery object
        """
        # Configure Iceberg catalog
        catalog_name = self.target_table.split('.')[0] if '.' in self.target_table else "silver"

        # Ensure database exists
        self.spark.sql(f"CREATE DATABASE IF NOT EXISTS {catalog_name}")

        # Configure Iceberg write with silver-specific optimizations
        return (
            df.writeStream
            .outputMode(self.get_output_mode())
            .format("iceberg")
            .option("path", f"s3a://lakehouse/iceberg/{self.target_table.replace('.', '/')}")
            .option("checkpointLocation", self.checkpoint_location)
            .option("fanout-enabled", "false")  # Less fanout for cleaner data
            .trigger(processingTime=self.trigger_interval)
            .partitionBy(*self.get_partitioning_columns())
            .toTable(self.target_table)
        )