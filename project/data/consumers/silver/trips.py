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


class SilverTripsConsumer(SilverConsumer):
    """
    Ultra-simple silver layer for NYC TLC trips.

    Focus: Basic data cleaning only
    - Parse timestamps
    - Normalize distances (outlier capping)
    - Deduplicate records
    - Add partitioning

    NO quality scoring, NO enrichment, NO business logic.
    """

    def __init__(
        self,
        source_table: str = "bronze.trips",
        target_table: str = "silver.trips",
        checkpoint_location: str = "/tmp/checkpoint/silver/trips",
        trigger_interval: str = "5 seconds",
        watermark_duration: str = "10 minutes",
        **kwargs
    ):
        """
        Initialize silver trips consumer.

        Args:
            source_table: Bronze table to read from
            target_table: Silver table to write to
            checkpoint_location: Checkpoint directory
            trigger_interval: Processing interval
            watermark_duration: How long to wait for late data
            **kwargs: Additional arguments for BaseIcebergConsumer
        """
        super().__init__(
            name="silver-trips-consumer",
            source_table=source_table,
            target_table=target_table,
            checkpoint_location=checkpoint_location,
            trigger_interval=trigger_interval,
            **kwargs
        )
        self.watermark_duration = watermark_duration

    def transform(self, df: DataFrame) -> DataFrame:
        """
        Simple cleaning transformations only.

        Args:
            df: Input DataFrame

        Returns:
            Cleaned DataFrame
        """
        # 1. Parse timestamp to proper format
        df = df.withColumn(
            "event_timestamp",
            F.to_timestamp(F.col("ts"), "yyyy-MM-dd HH:mm:ss")
        )

        # 2. Normalize distance (cap outliers, remove negatives)
        df = df.withColumn(
            "dist_normalized",
            F.when(F.col("dist") > 100, 100.0)  # Cap at 100 miles
            .when(F.col("dist") < 0, 0.0)       # No negative distances
            .otherwise(F.col("dist"))
        )

        # 3. Add partitioning columns for efficient queries
        df = self.add_event_time_partitions(df, "event_timestamp")

        # 4. Deduplicate with watermarks
        df = self.deduplicate(
            df,
            keys=["pu", "do", "ts"],
            watermark_duration=self.watermark_duration
        )

        return df

    def get_partitioning_columns(self) -> List[str]:
        """
        Define partitioning strategy for silver trips table.

        Returns:
            List of partitioning columns
        """
        # Partition by event time for better query performance
        return ["event_year", "event_month", "event_day"]


def main():
    """Main entry point for silver trips consumer."""
    import argparse

    parser = argparse.ArgumentParser(description="Run Silver Trips Consumer")
    parser.add_argument("--trigger-interval", type=str, default="5 seconds",
                       help="Processing trigger interval")
    parser.add_argument("--watermark", type=str, default="10 minutes",
                       help="Watermark duration for late data")

    args = parser.parse_args()

    consumer = SilverTripsConsumer(
        trigger_interval=args.trigger_interval,
        watermark_duration=args.watermark
    )

    print("Running Silver Trips Consumer (simple cleaning only)")
    consumer.run()


if __name__ == "__main__":
    main()