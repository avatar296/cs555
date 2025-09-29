#!/usr/bin/env python3
"""
Silver Layer Trips Consumer

Demonstrates how to extend SilverConsumer with quality checks using mixins.
Reads from bronze layer and applies cleansing, validation, and normalization.
"""

from pyspark.sql import DataFrame
from pyspark.sql import functions as F

from ..core.silver_consumer import SilverConsumer
from ..core.quality_mixins import TripQualityMixin, DataCompletenessMixin
from ..config.trips import TripsConsumerConfig


class SilverTripsConsumer(SilverConsumer, TripQualityMixin, DataCompletenessMixin):
    """
    Silver layer consumer for NYC TLC trips data.

    Applies quality checks and cleansing to bronze trip data.
    """

    def __init__(self, config: TripsConsumerConfig = None):
        """
        Initialize silver trips consumer.

        Args:
            config: Optional configuration (uses silver defaults)
        """
        # Create silver config with appropriate defaults
        if config is None:
            config = TripsConsumerConfig(
                source_topic="bronze.trips",  # Read from bronze table
                target_table="silver.trips",
                consumer_name="silver-trips",
                trigger_interval="60 seconds",  # Less frequent than bronze
                checkpoint_location="/tmp/checkpoint/silver/trips"
            )

        # Initialize with quality threshold
        super().__init__(
            name=config.consumer_name,
            source_topic=config.source_topic,
            target_table=config.target_table,
            kafka_bootstrap=config.kafka_bootstrap,
            schema_registry_url=config.schema_registry_url,
            checkpoint_location=config.checkpoint_location,
            trigger_interval=config.trigger_interval,
            max_offsets_per_trigger=config.max_offsets_per_trigger,
            starting_offsets=config.starting_offsets,
            quality_threshold=0.7  # Accept records with 70%+ quality
        )

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

        # Add event time partitions for silver layer
        df = self.add_event_time_partitions(df, "event_timestamp")

        # Deduplicate within 1 hour window
        df = self.deduplicate(df, keys=["pu", "do", "event_timestamp"], window_duration="1 hour")

        # Remove temporary columns
        df = df.drop("trip_quality_score", "completeness_score")

        return df


class SilverTripsAggregateConsumer(SilverTripsConsumer):
    """
    Alternative silver consumer that pre-aggregates trips by zone and hour.

    Demonstrates how silver can also do light aggregations.
    """

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
            .groupBy("pu", "event_hour", "trip_type")
            .agg(
                F.count("*").alias("trip_count"),
                F.avg("dist_normalized").alias("avg_distance"),
                F.min("dist_normalized").alias("min_distance"),
                F.max("dist_normalized").alias("max_distance"),
                F.sum(F.when(F.col("same_zone"), 1).otherwise(0)).alias("same_zone_trips"),
                F.avg("quality_score").alias("avg_quality_score")
            )
            .withColumn("records_per_trip", F.lit(1))  # For tracking aggregation level
        )

        return aggregated_df


def main():
    """Main entry point for silver trips consumer."""
    # Example of running with custom configuration
    config = TripsConsumerConfig(
        source_topic="bronze.trips",
        target_table="silver.trips",
        consumer_name="silver-trips",
        kafka_bootstrap="kafka:9092",
        starting_offsets="earliest",
        quality_threshold=0.75  # Higher quality requirement
    )

    consumer = SilverTripsConsumer(config)
    consumer.run()


if __name__ == "__main__":
    main()