#!/usr/bin/env python3
"""
Gold Layer: OD Flows Enriched Hourly (Medium Complexity)

Demonstrates dimension enrichment pattern.
Origin-Destination flows with dual zone metadata joins.

Purpose: Inter-zone trip flows with geographic context
Complexity: Medium - aggregation + dual dimension joins
"""

from typing import List
from pyspark.sql import DataFrame
from pyspark.sql import functions as F

from ..core.gold_consumer import GoldConsumer


class GoldODFlowsEnrichedHourly(GoldConsumer):
    """
    Medium complexity gold layer: OD flows with dimension enrichment.

    Demonstrates:
    - Origin-Destination aggregation
    - Dual dimension joins (pickup + dropoff zones)
    - Inter-borough flow analysis
    - Geographic metadata enrichment
    """

    def __init__(
        self,
        source_table: str = "silver.trips",
        target_table: str = "gold.od_flows_enriched_hourly",
        checkpoint_location: str = "/tmp/checkpoint/gold/od_flows_enriched_hourly",
        trigger_interval: str = "5 seconds",
        aggregation_window: str = "15 minutes",
        watermark: str = "5 minutes",
        **kwargs
    ):
        """
        Initialize OD flows consumer.

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
            name="gold-od-flows-enriched-hourly",
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
        Aggregate OD flows and enrich with zone metadata.

        Pattern: GROUP BY window, pickup_zone, dropoff_zone
                 + JOIN with zones dimension (twice)

        Args:
            df: Input DataFrame from silver.trips

        Returns:
            Aggregated DataFrame with enriched OD flows
        """
        # Create time windows and apply watermark
        df = df.withWatermark("event_timestamp", self.watermark)

        # Aggregate by OD pair
        od_flows = (
            df.groupBy(
                F.window("event_timestamp", self.aggregation_window),
                F.col("pu").alias("pickup_zone_id"),
                F.col("do").alias("dropoff_zone_id")
            )
            .agg(
                F.count("*").alias("trip_count"),
                F.avg("dist_normalized").alias("avg_distance"),
                F.sum("dist_normalized").alias("total_distance"),
                F.min("dist_normalized").alias("min_distance"),
                F.max("dist_normalized").alias("max_distance"),
                F.stddev("dist_normalized").alias("stddev_distance")
            )
        )

        # Load zones dimension
        zones_df = self._load_zones_dimension()

        if zones_df is not None:
            # Prepare pickup zone metadata (with prefix)
            pickup_zones = zones_df.select(
                F.col("zone_id").alias("pickup_zone_id"),
                F.col("zone_name").alias("pickup_zone_name"),
                F.col("borough").alias("pickup_borough"),
                F.col("service_zone").alias("pickup_service_zone"),
                F.col("centroid_lat").alias("pickup_lat"),
                F.col("centroid_lon").alias("pickup_lon")
            )

            # Prepare dropoff zone metadata (with prefix)
            dropoff_zones = zones_df.select(
                F.col("zone_id").alias("dropoff_zone_id"),
                F.col("zone_name").alias("dropoff_zone_name"),
                F.col("borough").alias("dropoff_borough"),
                F.col("service_zone").alias("dropoff_service_zone"),
                F.col("centroid_lat").alias("dropoff_lat"),
                F.col("centroid_lon").alias("dropoff_lon")
            )

            # Join with pickup zone metadata
            od_flows = od_flows.join(
                F.broadcast(pickup_zones),
                on="pickup_zone_id",
                how="left"
            )

            # Join with dropoff zone metadata
            od_flows = od_flows.join(
                F.broadcast(dropoff_zones),
                on="dropoff_zone_id",
                how="left"
            )

            # Calculate flow characteristics
            od_flows = self._add_flow_characteristics(od_flows)

        # Add window partitioning columns
        od_flows = self.add_window_partitions(od_flows)

        # Add metadata
        od_flows = self.add_metadata(od_flows)

        return od_flows

    def _add_flow_characteristics(self, df: DataFrame) -> DataFrame:
        """
        Add flow characteristic flags.

        Args:
            df: DataFrame with pickup/dropoff borough columns

        Returns:
            DataFrame with flow characteristics added
        """
        return (
            df
            # Inter-borough vs intra-borough flow
            .withColumn(
                "is_inter_borough",
                F.when(
                    F.col("pickup_borough").isNotNull() &
                    F.col("dropoff_borough").isNotNull(),
                    F.col("pickup_borough") != F.col("dropoff_borough")
                ).otherwise(F.lit(None))
            )
            # Same zone loop
            .withColumn(
                "is_same_zone",
                F.col("pickup_zone_id") == F.col("dropoff_zone_id")
            )
            # Distance category
            .withColumn(
                "distance_category",
                F.when(F.col("avg_distance") < 1.0, "short")
                .when(F.col("avg_distance") < 5.0, "medium")
                .when(F.col("avg_distance") < 15.0, "long")
                .otherwise("very_long")
            )
        )

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
    """Main entry point for OD flows consumer."""
    import argparse

    parser = argparse.ArgumentParser(
        description="Run Gold OD Flows Enriched Hourly Consumer (Medium Complexity)"
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

    consumer = GoldODFlowsEnrichedHourly(
        trigger_interval=args.trigger_interval,
        aggregation_window=args.window,
        watermark=args.watermark
    )

    print("=" * 70)
    print("Gold Layer: OD Flows Enriched Hourly (Medium Complexity)")
    print("=" * 70)
    print(f"Source: silver.trips")
    print(f"Target: gold.od_flows_enriched_hourly")
    print(f"Window: {args.window}")
    print(f"Watermark: {args.watermark}")
    print("Pattern: Aggregation + dual dimension joins")
    print("=" * 70)

    consumer.run()


if __name__ == "__main__":
    main()
