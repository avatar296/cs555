#!/usr/bin/env python3
"""
Silver Layer Events Consumer

Reads from bronze.events Iceberg table and applies basic cleaning.
Writes to silver.events Iceberg table.
"""

from typing import List
from pyspark.sql import DataFrame
from pyspark.sql import functions as F

from ..core.silver_consumer import SilverConsumer


class SilverEventsConsumer(SilverConsumer):
    """
    Ultra-simple silver layer for NYC events data.

    Focus: Basic data cleaning only
    - Parse timestamps (start and end)
    - Normalize geographic coordinates (NYC bounds)
    - Normalize attendance (outlier capping)
    - Deduplicate records
    - Add partitioning

    NO quality scoring, NO enrichment, NO business logic.
    """

    def __init__(
        self,
        source_table: str = "bronze.events",
        target_table: str = "silver.events",
        checkpoint_location: str = "/tmp/checkpoint/silver/events",
        trigger_interval: str = "300 seconds",
        watermark_duration: str = "1 hour",
        **kwargs
    ):
        """
        Initialize silver events consumer.

        Args:
            source_table: Bronze table to read from
            target_table: Silver table to write to
            checkpoint_location: Checkpoint directory
            trigger_interval: Processing interval (5 minutes for events)
            watermark_duration: How long to wait for late data (1 hour)
            **kwargs: Additional arguments for BaseIcebergConsumer
        """
        super().__init__(
            name="silver-events-consumer",
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
        # 1. Parse start and end timestamps to proper format
        df = df.withColumn(
            "event_start_timestamp",
            F.to_timestamp(F.col("start_time"))
        ).withColumn(
            "event_end_timestamp",
            F.to_timestamp(F.col("end_time"))
        )

        # 2. Normalize venue latitude (NYC bounds: 40.5 to 41.0)
        df = df.withColumn(
            "venue_lat_normalized",
            F.when(F.col("venue_lat") > 41.0, 41.0)
            .when(F.col("venue_lat") < 40.5, 40.5)
            .otherwise(F.col("venue_lat"))
        )

        # 3. Normalize venue longitude (NYC bounds: -74.3 to -73.7)
        df = df.withColumn(
            "venue_lon_normalized",
            F.when(F.col("venue_lon") > -73.7, -73.7)
            .when(F.col("venue_lon") < -74.3, -74.3)
            .otherwise(F.col("venue_lon"))
        )

        # 4. Normalize expected attendance (valid range: 0 to 100,000)
        df = df.withColumn(
            "expected_attendance_normalized",
            F.when(F.col("expected_attendance") > 100000, 100000)
            .when(F.col("expected_attendance") < 0, 0)
            .otherwise(F.col("expected_attendance"))
        )

        # 5. Add partitioning columns based on event start time
        df = self.add_event_time_partitions(df, "event_start_timestamp")

        # 6. Deduplicate by event_id (unique identifier)
        df = self.deduplicate(
            df,
            keys=["event_id"],
            watermark_duration=self.watermark_duration
        )

        return df

    def get_partitioning_columns(self) -> List[str]:
        """
        Define partitioning strategy for silver events table.

        Returns:
            List of partitioning columns
        """
        # Partition by event start time for better query performance
        return ["event_year", "event_month", "event_day"]


def main():
    """Main entry point for silver events consumer."""
    import argparse

    parser = argparse.ArgumentParser(description="Run Silver Events Consumer")
    parser.add_argument("--trigger-interval", type=str, default="300 seconds",
                       help="Processing trigger interval")
    parser.add_argument("--watermark", type=str, default="1 hour",
                       help="Watermark duration for late data")

    args = parser.parse_args()

    consumer = SilverEventsConsumer(
        trigger_interval=args.trigger_interval,
        watermark_duration=args.watermark
    )

    print("Running Silver Events Consumer (simple cleaning only)")
    consumer.run()


if __name__ == "__main__":
    main()
