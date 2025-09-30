#!/usr/bin/env python3
"""
Silver Layer Consumer Base Class

Implements silver-specific behavior for the medallion architecture.
Silver layer focuses on data cleansing, validation, and normalization.
Reads from bronze Iceberg tables and writes to silver Iceberg tables.
"""

from abc import abstractmethod
from typing import List
from pyspark.sql import DataFrame
from pyspark.sql import functions as F

from .base_iceberg_consumer import BaseIcebergConsumer


class SilverConsumer(BaseIcebergConsumer):
    """
    Silver layer consumer base class.

    Silver layer responsibilities:
    - Data quality validation
    - Cleansing and normalization
    - Deduplication
    - Schema conformance
    - Partition by event time (not ingestion time)

    Reads from bronze Iceberg tables and writes to silver Iceberg tables.
    """

    def __init__(self, *args, quality_threshold: float = 0.8, **kwargs):
        """
        Initialize silver consumer.

        Args:
            quality_threshold: Minimum quality score to accept (0.0-1.0)
            *args, **kwargs: Passed to parent BaseIcebergConsumer
        """
        # Set default catalogs for silver layer
        kwargs.setdefault('source_catalog', 'bronze')
        kwargs.setdefault('target_catalog', 'silver')

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
            .withColumn("source_table", F.lit(self.source_table))
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

        # Add data quality tier
        df = df.withColumn(
            "quality_tier",
            F.when(F.col("quality_score") >= 0.9, "high")
            .when(F.col("quality_score") >= 0.7, "medium")
            .when(F.col("quality_score") >= 0.5, "low")
            .otherwise("poor")
        )

        # Log quality metrics (in production, write to metrics system)
        return df

    def deduplicate(self, df: DataFrame, keys: List[str], watermark_duration: str = "30 minutes") -> DataFrame:
        """
        Remove duplicate records using watermarking (production-ready for streaming).

        Args:
            df: Input DataFrame
            keys: Columns to use for deduplication
            watermark_duration: Watermark duration for handling late data

        Returns:
            Deduplicated DataFrame with watermark
        """
        # Determine timestamp column for watermarking
        timestamp_col = None
        if "event_timestamp" in df.columns:
            timestamp_col = "event_timestamp"
        elif "kafka_timestamp" in df.columns:
            timestamp_col = "kafka_timestamp"
        else:
            timestamp_col = "silver_processing_time"

        # Apply watermark and deduplicate
        # This is the standard approach for production streaming
        df_with_watermark = df.withWatermark(timestamp_col, watermark_duration)

        # Use dropDuplicates which is supported in streaming
        # Will maintain state for duplicates within the watermark window
        return df_with_watermark.dropDuplicates(keys)

    def deduplicate_with_state(self, df: DataFrame, unique_key: str, watermark_duration: str = "30 minutes") -> DataFrame:
        """
        Alternative: Stateful deduplication for stronger guarantees.

        Uses dropDuplicatesWithinWatermark for maintaining dedup state across batches.
        Requires Spark 3.5+

        Args:
            df: Input DataFrame
            unique_key: Unique identifier column
            watermark_duration: Watermark duration

        Returns:
            Deduplicated DataFrame with persistent state
        """
        timestamp_col = "event_timestamp" if "event_timestamp" in df.columns else "silver_processing_time"

        try:
            # This maintains dedup state across microbatches
            # Better for scenarios with delayed duplicates
            return (
                df
                .withWatermark(timestamp_col, watermark_duration)
                .dropDuplicatesWithinWatermark([unique_key])
            )
        except AttributeError:
            # Fallback for older Spark versions
            print(f"[{self.name}] dropDuplicatesWithinWatermark not available, using standard dropDuplicates")
            return self.deduplicate(df, [unique_key], watermark_duration)

    def normalize_timestamps(self, df: DataFrame, timestamp_cols: List[str]) -> DataFrame:
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
        df = df.filter(F.col("is_valid"))

        # Additional transformations can be added by subclasses
        return df

    def get_output_mode(self) -> str:
        """
        Silver uses append mode.

        Returns:
            "append" - silver is append-only after cleansing
        """
        return "append"

    def get_partitioning_columns(self) -> List[str]:
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

    def enrich_with_derived_fields(self, df: DataFrame) -> DataFrame:
        """
        Add common derived fields for analytics.

        Args:
            df: Input DataFrame

        Returns:
            DataFrame with derived fields
        """
        # Add day of week and hour if timestamp exists
        if "event_timestamp" in df.columns:
            df = (
                df
                .withColumn("day_of_week", F.dayofweek("event_timestamp"))
                .withColumn("is_weekend",
                    F.when(F.col("day_of_week").isin([1, 7]), True).otherwise(False))
                .withColumn("hour_of_day", F.hour("event_timestamp"))
            )

        return df

    def add_data_lineage(self, df: DataFrame) -> DataFrame:
        """
        Add data lineage tracking columns.

        Args:
            df: Input DataFrame

        Returns:
            DataFrame with lineage columns
        """
        return (
            df
            .withColumn("lineage_source", F.lit(self.source_table))
            .withColumn("lineage_target", F.lit(self.target_table))
            .withColumn("lineage_timestamp", F.current_timestamp())
            .withColumn("lineage_consumer", F.lit(self.name))
        )

    def track_quality_metrics(self, df: DataFrame):
        """
        Track and log quality metrics for monitoring.

        Args:
            df: DataFrame with quality scores
        """
        # Calculate quality statistics
        quality_stats = df.select(
            F.avg("quality_score").alias("avg_quality"),
            F.min("quality_score").alias("min_quality"),
            F.max("quality_score").alias("max_quality"),
            F.count(F.when(F.col("is_valid"), 1)).alias("valid_count"),
            F.count(F.when(~F.col("is_valid"), 1)).alias("invalid_count"),
            F.count("*").alias("total_count")
        ).collect()[0]

        print(f"\n[{self.name}] Quality Metrics:")
        print(f"  Average Quality: {quality_stats['avg_quality']:.3f}")
        print(f"  Valid Records: {quality_stats['valid_count']} / {quality_stats['total_count']}")
        print(f"  Invalid Records: {quality_stats['invalid_count']} / {quality_stats['total_count']}")
        print(f"  Quality Range: [{quality_stats['min_quality']:.3f}, {quality_stats['max_quality']:.3f}]")