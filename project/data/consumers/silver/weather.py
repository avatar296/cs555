#!/usr/bin/env python3
"""
Silver Layer Weather Consumer

Reads from bronze.weather Iceberg table and applies basic cleaning.
Writes to silver.weather Iceberg table.
"""

from typing import List
from pyspark.sql import DataFrame
from pyspark.sql import functions as F

from ..core.silver_consumer import SilverConsumer


class SilverWeatherConsumer(SilverConsumer):
    """
    Ultra-simple silver layer for weather data.

    Focus: Basic data cleaning only
    - Parse timestamps
    - Normalize temperature, precipitation, wind, visibility (outlier capping)
    - Deduplicate records
    - Add partitioning

    NO quality scoring, NO enrichment, NO business logic.
    """

    def __init__(
        self,
        source_table: str = "bronze.weather",
        target_table: str = "silver.weather",
        checkpoint_location: str = "/tmp/checkpoint/silver/weather",
        trigger_interval: str = "60 seconds",
        watermark_duration: str = "30 minutes",
        **kwargs
    ):
        """
        Initialize silver weather consumer.

        Args:
            source_table: Bronze table to read from
            target_table: Silver table to write to
            checkpoint_location: Checkpoint directory
            trigger_interval: Processing interval
            watermark_duration: How long to wait for late data
            **kwargs: Additional arguments for BaseIcebergConsumer
        """
        super().__init__(
            name="silver-weather-consumer",
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
            F.to_timestamp(F.col("observation_time"))
        )

        # 2. Normalize temperature (valid range: -50°F to 150°F)
        df = df.withColumn(
            "temperature_normalized",
            F.when(F.col("temperature") > 150, 150.0)
            .when(F.col("temperature") < -50, -50.0)
            .otherwise(F.col("temperature"))
        )

        # 3. Normalize precipitation (valid range: 0 to 10 inches/hour)
        df = df.withColumn(
            "precipitation_normalized",
            F.when(F.col("precipitation") > 10, 10.0)
            .when(F.col("precipitation") < 0, 0.0)
            .otherwise(F.col("precipitation"))
        )

        # 4. Normalize wind speed (valid range: 0 to 200 mph)
        df = df.withColumn(
            "wind_speed_normalized",
            F.when(F.col("wind_speed") > 200, 200.0)
            .when(F.col("wind_speed") < 0, 0.0)
            .otherwise(F.col("wind_speed"))
        )

        # 5. Normalize visibility (valid range: 0 to 10 miles)
        df = df.withColumn(
            "visibility_normalized",
            F.when(F.col("visibility") > 10, 10.0)
            .when(F.col("visibility") < 0, 0.0)
            .otherwise(F.col("visibility"))
        )

        # 6. Add partitioning columns for efficient queries
        df = self.add_event_time_partitions(df, "event_timestamp")

        # 7. Deduplicate with watermarks
        df = self.deduplicate(
            df,
            keys=["zone_id", "observation_time"],
            watermark_duration=self.watermark_duration
        )

        return df

    def get_partitioning_columns(self) -> List[str]:
        """
        Define partitioning strategy for silver weather table.

        Returns:
            List of partitioning columns
        """
        # Partition by event time for better query performance
        return ["event_year", "event_month", "event_day"]


def main():
    """Main entry point for silver weather consumer."""
    import argparse

    parser = argparse.ArgumentParser(description="Run Silver Weather Consumer")
    parser.add_argument("--trigger-interval", type=str, default="60 seconds",
                       help="Processing trigger interval")
    parser.add_argument("--watermark", type=str, default="30 minutes",
                       help="Watermark duration for late data")

    args = parser.parse_args()

    consumer = SilverWeatherConsumer(
        trigger_interval=args.trigger_interval,
        watermark_duration=args.watermark
    )

    print("Running Silver Weather Consumer (simple cleaning only)")
    consumer.run()


if __name__ == "__main__":
    main()
