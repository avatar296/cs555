#!/usr/bin/env python3
"""
Gold Layer: Zone Metrics Hourly (Simple Pattern)

Demonstrates standard gold layer aggregation pattern.
Single source (silver.trips) aggregated by zone + time window.

Purpose: Baseline trip demand metrics by zone
Complexity: Simple - typical gold layer processing
"""

from typing import List
from pyspark.sql import DataFrame
from pyspark.sql import functions as F

from ..core.gold_consumer import GoldConsumer


class GoldZoneMetricsHourly(GoldConsumer):
    """
    Simple gold layer pattern: aggregate trips by zone and time window.

    Demonstrates:
    - Standard windowed aggregation
    - Broadcast dimension join
    - Basic KPI calculations
    """

    def __init__(
        self,
        source_table: str = "silver.trips",
        target_table: str = "gold.zone_metrics_hourly",
        checkpoint_location: str = "/tmp/checkpoint/gold/zone_metrics_hourly",
        trigger_interval: str = "5 seconds",
        aggregation_window: str = "15 minutes",
        watermark: str = "5 minutes",
        **kwargs
    ):
        """
        Initialize zone metrics consumer.

        Args:
            source_table: Silver table to read from
            target_table: Gold table to write to
            checkpoint_location: Checkpoint directory
            trigger_interval: Processing interval
            aggregation_window: Window size for aggregation
            watermark: How long to wait for late data
            **kwargs: Additional arguments for GoldConsumer
        """
        super().__init__(
            name="gold-zone-metrics-hourly",
            source_table=source_table,
            target_table=target_table,
            checkpoint_location=checkpoint_location,
            trigger_interval=trigger_interval,
            aggregation_window=aggregation_window,
            watermark=watermark,
            **kwargs
        )

    def aggregate(self, df: DataFrame) -> DataFrame:
        """
        Aggregate trips by zone and time window.

        Simple pattern: GROUP BY window, zone_id

        Args:
            df: Input DataFrame from silver.trips

        Returns:
            Aggregated DataFrame with zone metrics
        """
        # Create time windows and apply watermark
        df = df.withWatermark("event_timestamp", self.watermark)

        # Aggregate pickups by zone
        pickup_agg = (
            df.groupBy(
                F.window("event_timestamp", self.aggregation_window),
                F.col("pu").alias("zone_id")
            )
            .agg(
                F.count("*").alias("pickup_count"),
                F.avg("dist_normalized").alias("avg_pickup_distance"),
                F.sum("dist_normalized").alias("total_pickup_distance"),
                F.min("dist_normalized").alias("min_pickup_distance"),
                F.max("dist_normalized").alias("max_pickup_distance")
            )
        )

        # Aggregate dropoffs by zone
        dropoff_agg = (
            df.groupBy(
                F.window("event_timestamp", self.aggregation_window),
                F.col("do").alias("zone_id")
            )
            .agg(
                F.count("*").alias("dropoff_count"),
                F.avg("dist_normalized").alias("avg_dropoff_distance")
            )
        )

        # Combine pickups and dropoffs
        combined = pickup_agg.join(
            dropoff_agg,
            on=["window", "zone_id"],
            how="outer"
        )

        # Fill nulls and calculate totals
        result = (
            combined
            .withColumn("pickup_count", F.coalesce("pickup_count", F.lit(0)))
            .withColumn("dropoff_count", F.coalesce("dropoff_count", F.lit(0)))
            .withColumn("total_trips", F.col("pickup_count") + F.col("dropoff_count"))
            .withColumn("avg_pickup_distance", F.coalesce("avg_pickup_distance", F.lit(0.0)))
            .withColumn("avg_dropoff_distance", F.coalesce("avg_dropoff_distance", F.lit(0.0)))
            .withColumn("total_pickup_distance", F.coalesce("total_pickup_distance", F.lit(0.0)))
            .withColumn("min_pickup_distance", F.coalesce("min_pickup_distance", F.lit(0.0)))
            .withColumn("max_pickup_distance", F.coalesce("max_pickup_distance", F.lit(0.0)))
        )

        # Load zones dimension for enrichment (batch read, broadcast join)
        zones_df = self._load_zones_dimension()
        if zones_df is not None:
            result = result.join(
                F.broadcast(zones_df),
                on="zone_id",
                how="left"
            )

        # Add window partitioning columns
        result = self.add_window_partitions(result)

        # Add metadata
        result = self.add_metadata(result)

        return result

    def _load_zones_dimension(self) -> DataFrame:
        """
        Load zones dimension table for enrichment.

        Returns:
            Zones DataFrame or None if table doesn't exist
        """
        try:
            zones = self.spark.read.format("iceberg").load("silver.zones")
            return zones.select(
                "zone_id",
                "zone_name",
                "borough",
                "service_zone",
                "centroid_lat",
                "centroid_lon"
            )
        except Exception as e:
            self.logger.warning(f"Could not load zones dimension: {e}")
            return None

    def get_partitioning_columns(self) -> List[str]:
        """
        Define partitioning strategy.

        Returns:
            List of partitioning columns
        """
        return ["window_year", "window_month", "window_day"]


def main():
    """Main entry point for zone metrics consumer."""
    import argparse

    parser = argparse.ArgumentParser(
        description="Run Gold Zone Metrics Hourly Consumer (Simple Pattern)"
    )
    parser.add_argument(
        "--trigger-interval",
        type=str,
        default="5 seconds",
        help="Processing trigger interval"
    )
    parser.add_argument(
        "--window",
        type=str,
        default="15 minutes",
        help="Aggregation window size"
    )
    parser.add_argument(
        "--watermark",
        type=str,
        default="5 minutes",
        help="Watermark duration for late data"
    )

    args = parser.parse_args()

    consumer = GoldZoneMetricsHourly(
        trigger_interval=args.trigger_interval,
        aggregation_window=args.window,
        watermark=args.watermark
    )

    print("=" * 70)
    print("Gold Layer: Zone Metrics Hourly (Simple Pattern)")
    print("=" * 70)
    print(f"Source: silver.trips")
    print(f"Target: gold.zone_metrics_hourly")
    print(f"Window: {args.window}")
    print(f"Watermark: {args.watermark}")
    print("Pattern: Standard windowed aggregation")
    print("=" * 70)

    consumer.run()


if __name__ == "__main__":
    main()
