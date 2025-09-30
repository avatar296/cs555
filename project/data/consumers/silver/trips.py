#!/usr/bin/env python3
"""
Silver Layer Trips Consumer

Reads from bronze.trips Iceberg table and applies cleansing, validation, and normalization.
Writes to silver.trips Iceberg table.
"""

from typing import List
from pyspark.sql import DataFrame
from pyspark.sql import functions as F

from ..core.silver_consumer import SilverConsumer
from ..core.quality_mixins import TripQualityMixin, DataCompletenessMixin


class SilverTripsConsumer(SilverConsumer, TripQualityMixin, DataCompletenessMixin):
    """
    Silver layer consumer for NYC TLC trips data.

    Production-ready implementation with:
    - Watermark-based deduplication
    - Quality scoring and filtering
    - Data enrichment and normalization
    - Late data handling
    - Metrics tracking
    """

    def __init__(
        self,
        source_table: str = "bronze.trips",
        target_table: str = "silver.trips",
        checkpoint_location: str = "/tmp/checkpoint/silver/trips",
        trigger_interval: str = "5 seconds",  # Real-time: 5 second micro-batches
        quality_threshold: float = 0.7,
        watermark_duration: str = "10 minutes",  # Real-time: shorter watermark
        enable_metrics: bool = True,
        **kwargs
    ):
        """
        Initialize silver trips consumer.

        Args:
            source_table: Bronze table to read from
            target_table: Silver table to write to
            checkpoint_location: Checkpoint directory
            trigger_interval: Processing interval
            quality_threshold: Minimum quality score to accept
            watermark_duration: How long to wait for late data
            enable_metrics: Whether to track and log metrics
            **kwargs: Additional arguments for BaseIcebergConsumer
        """
        super().__init__(
            name="silver-trips-consumer",
            source_table=source_table,
            target_table=target_table,
            checkpoint_location=checkpoint_location,
            trigger_interval=trigger_interval,
            quality_threshold=quality_threshold,
            **kwargs
        )
        self.watermark_duration = watermark_duration
        self.enable_metrics = enable_metrics
        self.metrics = {
            "records_processed": 0,
            "records_filtered": 0,
            "duplicates_removed": 0
        }

    def calculate_quality_score(self, df: DataFrame) -> DataFrame:
        """
        Calculate quality score for trip records.

        Uses TripQualityMixin methods.

        Args:
            df: Input DataFrame

        Returns:
            DataFrame with quality_score column
        """
        # Apply trip-specific quality checks from mixin
        df = self.calculate_trip_quality_score(df)

        # Check data completeness
        required_columns = ["ts", "pu", "do", "dist"]
        df = self.check_required_columns(df, required_columns)

        # Combine scores
        df = df.withColumn(
            "quality_score",
            (F.col("trip_quality_score") * 0.8 + F.col("completeness_score") * 0.2)
        )

        return df

    def transform(self, df: DataFrame) -> DataFrame:
        """
        Apply silver layer transformations.

        Cleanses and normalizes trip data.

        Args:
            df: Input DataFrame

        Returns:
            Transformed DataFrame
        """
        # Call parent transform (filters by quality score)
        df = super().transform(df)

        # Normalize distance (remove outliers)
        df = df.withColumn(
            "dist_normalized",
            F.when(F.col("dist") > 100, 100.0)  # Cap at 100 miles
            .when(F.col("dist") < 0, 0.0)  # No negative distances
            .otherwise(F.col("dist"))
        )

        # Add derived fields
        df = (
            df
            # Trip type based on distance
            .withColumn(
                "trip_type",
                F.when(F.col("dist_normalized") < 1, "short")
                .when(F.col("dist_normalized") < 5, "medium")
                .when(F.col("dist_normalized") < 20, "long")
                .otherwise("very_long")
            )
            # Same zone indicator
            .withColumn(
                "same_zone",
                F.when(F.col("pu") == F.col("do"), True).otherwise(False)
            )
            # Parse event timestamp
            .withColumn(
                "event_timestamp",
                F.to_timestamp(F.col("ts"), "yyyy-MM-dd HH:mm:ss")
            )
        )

        # Add time-based features
        df = (
            df
            .withColumn("hour_of_day", F.hour("event_timestamp"))
            .withColumn("day_of_week", F.dayofweek("event_timestamp"))
            .withColumn("is_weekend",
                F.when(F.col("day_of_week").isin([1, 7]), True).otherwise(False))
            .withColumn("is_rush_hour",
                F.when(
                    (F.col("hour_of_day").between(7, 9)) |
                    (F.col("hour_of_day").between(17, 19)),
                    True
                ).otherwise(False))
            .withColumn("time_period",
                F.when(F.col("hour_of_day").between(6, 12), "morning")
                .when(F.col("hour_of_day").between(12, 17), "afternoon")
                .when(F.col("hour_of_day").between(17, 22), "evening")
                .otherwise("night"))
        )

        # Add zone categories (simplified - in production would join with zone data)
        df = df.withColumn(
            "pickup_zone_type",
            F.when(F.col("pu").isin([132, 138, 1]), "airport")  # JFK, LGA, EWR
            .when(F.col("pu") < 100, "manhattan")
            .otherwise("outer_borough")
        )

        # Add event time partitions for silver layer
        df = self.add_event_time_partitions(df, "event_timestamp")

        # Production-ready deduplication with watermarking
        # Uses configurable watermark duration for late data handling
        df = self.deduplicate(df, keys=["pu", "do"], watermark_duration=self.watermark_duration)

        # Add data lineage
        df = self.add_data_lineage(df)

        # Remove temporary columns
        columns_to_drop = ["trip_quality_score", "completeness_score", "valid_distance",
                          "valid_locations", "valid_timestamp", "row_num"]
        for col in columns_to_drop:
            if col in df.columns:
                df = df.drop(col)

        return df

    def get_partitioning_columns(self) -> List[str]:
        """
        Define partitioning strategy for silver trips table.

        Returns:
            List of partitioning columns
        """
        # Partition by event time for better query performance
        return ["event_year", "event_month", "event_day"]

    def process_batch_metrics(self, df: DataFrame, epoch_id: int):
        """
        Track metrics for each batch (production monitoring).

        Args:
            df: Batch DataFrame
            epoch_id: Batch identifier
        """
        if self.enable_metrics:
            batch_metrics = {
                "epoch": epoch_id,
                "total_records": df.count(),
                "valid_records": df.filter(F.col("is_valid")).count(),
                "invalid_records": df.filter(~F.col("is_valid")).count(),
                "avg_quality_score": df.agg(F.avg("quality_score")).collect()[0][0],
                "quality_distribution": df.groupBy("quality_tier").count().collect()
            }

            print(f"\n[{self.name}] Batch Metrics (Epoch {epoch_id}):")
            print(f"  Total Records: {batch_metrics['total_records']}")
            print(f"  Valid/Invalid: {batch_metrics['valid_records']}/{batch_metrics['invalid_records']}")
            print(f"  Avg Quality Score: {batch_metrics['avg_quality_score']:.3f}")
            print(f"  Quality Distribution: {batch_metrics['quality_distribution']}")

            # In production, send to metrics system (Prometheus, CloudWatch, etc.)
            return batch_metrics


class SilverTripsAggregateConsumer(SilverTripsConsumer):
    """
    Alternative silver consumer that pre-aggregates trips by zone and hour.

    Demonstrates how silver can also do light aggregations for common queries.
    """

    def __init__(self, **kwargs):
        """Initialize with different target table."""
        kwargs['target_table'] = kwargs.get('target_table', 'silver.trips_hourly_agg')
        super().__init__(**kwargs)

    def transform(self, df: DataFrame) -> DataFrame:
        """
        Apply silver transformations with pre-aggregation.

        Args:
            df: Input DataFrame

        Returns:
            Aggregated DataFrame
        """
        # First apply base silver transformations
        df = super().transform(df)

        # Pre-aggregate by pickup zone and hour
        aggregated_df = (
            df
            .withColumn("event_hour", F.date_trunc("hour", "event_timestamp"))
            .groupBy("pu", "event_hour", "trip_type", "pickup_zone_type", "time_period", "is_weekend")
            .agg(
                F.count("*").alias("trip_count"),
                F.avg("dist_normalized").alias("avg_distance"),
                F.min("dist_normalized").alias("min_distance"),
                F.max("dist_normalized").alias("max_distance"),
                F.stddev("dist_normalized").alias("stddev_distance"),
                F.sum(F.when(F.col("same_zone"), 1).otherwise(0)).alias("same_zone_trips"),
                F.countDistinct("do").alias("unique_destinations"),
                F.avg("quality_score").alias("avg_quality_score"),
                F.min("quality_score").alias("min_quality_score"),
                F.max("quality_score").alias("max_quality_score")
            )
            .withColumn("aggregation_level", F.lit("hourly"))
            .withColumn("aggregation_timestamp", F.current_timestamp())
        )

        # Re-add partitioning columns based on event_hour
        aggregated_df = (
            aggregated_df
            .withColumn("event_year", F.year("event_hour"))
            .withColumn("event_month", F.month("event_hour"))
            .withColumn("event_day", F.dayofmonth("event_hour"))
        )

        return aggregated_df


def main():
    """Main entry point for silver trips consumer."""
    import argparse

    parser = argparse.ArgumentParser(description="Run Silver Trips Consumer")
    parser.add_argument("--aggregate", action="store_true",
                       help="Run aggregate version instead of raw")
    parser.add_argument("--quality-threshold", type=float, default=0.7,
                       help="Minimum quality threshold")
    parser.add_argument("--trigger-interval", type=str, default="60 seconds",
                       help="Processing trigger interval")

    args = parser.parse_args()

    if args.aggregate:
        consumer = SilverTripsAggregateConsumer(
            quality_threshold=args.quality_threshold,
            trigger_interval=args.trigger_interval
        )
        print("Running Silver Trips Aggregate Consumer")
    else:
        consumer = SilverTripsConsumer(
            quality_threshold=args.quality_threshold,
            trigger_interval=args.trigger_interval
        )
        print("Running Silver Trips Consumer")

    consumer.run()


if __name__ == "__main__":
    main()