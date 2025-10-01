#!/usr/bin/env python3
"""
Gold Layer: Zone Demand Context Hourly (Maximum Complexity)

Demonstrates advanced multi-stream join pattern.
Triple stream join: trips × weather × events with temporal + spatial alignment.

Purpose: Trip demand with full environmental context
Complexity: Maximum - graduate-level streaming architecture
"""

from typing import List
from pyspark.sql import DataFrame
from pyspark.sql import functions as F
from math import radians, sin, cos, sqrt, atan2

from ..core.gold_consumer import GoldConsumer


class GoldZoneDemandContextHourly(GoldConsumer):
    """
    Maximum complexity gold layer: 3-way stream join with context enrichment.

    Demonstrates:
    - Triple stream join (trips × weather × events)
    - Temporal alignment (coordinate multiple time windows)
    - Spatial joins (distance-based event proximity)
    - Complex business logic (demand lift calculations)
    - Coordinated watermarking across 3 streams
    """

    def __init__(
        self,
        trips_table: str = "silver.trips",
        weather_table: str = "silver.weather",
        events_table: str = "silver.events",
        target_table: str = "gold.zone_demand_context_hourly",
        checkpoint_location: str = "/tmp/checkpoint/gold/zone_demand_context_hourly",
        trigger_interval: str = "5 seconds",
        aggregation_window: str = "15 minutes",
        trips_watermark: str = "5 minutes",
        weather_watermark: str = "5 minutes",
        events_watermark: str = "1 hour",
        event_proximity_miles: float = 1.0,
        **kwargs
    ):
        """
        Initialize zone demand context consumer.

        Args:
            trips_table: Silver trips table
            weather_table: Silver weather table
            events_table: Silver events table
            target_table: Gold table to write to
            checkpoint_location: Checkpoint directory
            trigger_interval: Processing interval
            aggregation_window: Window size for aggregation
            trips_watermark: Watermark for trips stream
            weather_watermark: Watermark for weather stream
            events_watermark: Watermark for events stream (longer due to advance scheduling)
            event_proximity_miles: Distance threshold for "nearby" events
            **kwargs: Additional arguments for GoldConsumer
        """
        super().__init__(
            name="gold-zone-demand-context-hourly",
            source_table=trips_table,  # Primary source
            target_table=target_table,
            checkpoint_location=checkpoint_location,
            trigger_interval=trigger_interval,
            aggregation_window=aggregation_window,
            watermark=trips_watermark,
            **kwargs
        )
        self.trips_table = trips_table
        self.weather_table = weather_table
        self.events_table = events_table
        self.trips_watermark = trips_watermark
        self.weather_watermark = weather_watermark
        self.events_watermark = events_watermark
        self.event_proximity_miles = event_proximity_miles

    def read_input(self) -> DataFrame:
        """
        Override to read multiple source tables.

        Returns:
            Combined DataFrame from all three streams
        """
        # Read trips stream
        trips_df = self.spark.readStream.format("iceberg").load(self.trips_table)
        trips_df = trips_df.withWatermark("event_timestamp", self.trips_watermark)

        # Read weather stream
        weather_df = self.spark.readStream.format("iceberg").load(self.weather_table)
        weather_df = weather_df.withWatermark("event_timestamp", self.weather_watermark)

        # Read events stream
        events_df = self.spark.readStream.format("iceberg").load(self.events_table)
        events_df = events_df.withWatermark("event_start_timestamp", self.events_watermark)

        # Load zones dimension for spatial joins
        zones_df = self._load_zones_dimension()

        # Perform the triple join
        return self._join_streams(trips_df, weather_df, events_df, zones_df)

    def _join_streams(
        self,
        trips_df: DataFrame,
        weather_df: DataFrame,
        events_df: DataFrame,
        zones_df: DataFrame
    ) -> DataFrame:
        """
        Perform triple stream join with temporal + spatial alignment.

        Strategy:
        1. Join trips with weather (by zone + time window)
        2. Enrich trips with zone centroids
        3. Cross join with events and filter by spatial proximity + time overlap
        4. Aggregate to zone + window level

        Args:
            trips_df: Trips stream
            weather_df: Weather stream
            events_df: Events stream
            zones_df: Zones dimension

        Returns:
            Combined DataFrame ready for aggregation
        """
        # Step 1: Enrich trips with zone centroids for spatial calculations
        if zones_df is not None:
            trips_enriched = trips_df.join(
                F.broadcast(zones_df.select(
                    F.col("zone_id").alias("pu"),
                    F.col("centroid_lat").alias("pickup_lat"),
                    F.col("centroid_lon").alias("pickup_lon")
                )),
                on="pu",
                how="left"
            )
        else:
            trips_enriched = trips_df

        # Step 2: Prepare weather data with proper time alignment
        weather_prepared = weather_df.select(
            F.col("zone_id"),
            F.col("event_timestamp").alias("weather_timestamp"),
            F.col("temperature_normalized").alias("temperature"),
            F.col("precipitation_normalized").alias("precipitation"),
            F.col("wind_speed_normalized").alias("wind_speed"),
            F.col("visibility_normalized").alias("visibility"),
            F.col("conditions").alias("weather_condition")
        )

        # Step 3: Join trips with weather (temporal + zone match)
        # Use interval join: trip time within ±30 minutes of weather observation
        trips_with_weather = trips_enriched.join(
            weather_prepared,
            (trips_enriched.pu == weather_prepared.zone_id) &
            (trips_enriched.event_timestamp >= weather_prepared.weather_timestamp - F.expr("INTERVAL 30 MINUTES")) &
            (trips_enriched.event_timestamp <= weather_prepared.weather_timestamp + F.expr("INTERVAL 30 MINUTES")),
            how="left"
        ).drop(weather_prepared.zone_id)

        # Step 4: Prepare events with spatial + temporal context
        events_prepared = events_df.select(
            F.col("event_id"),
            F.col("event_start_timestamp"),
            F.col("event_end_timestamp"),
            F.col("venue_lat_normalized").alias("event_lat"),
            F.col("venue_lon_normalized").alias("event_lon"),
            F.col("event_type"),
            F.col("expected_attendance_normalized").alias("event_attendance")
        )

        # Step 5: Join with events (spatial proximity + time overlap)
        # This is the complex part: cross join with filter
        combined = trips_with_weather.alias("trips").join(
            events_prepared.alias("events"),
            # Temporal condition: trip during event window (start - 4 hours to end + 2 hours)
            (F.col("trips.event_timestamp") >= F.col("events.event_start_timestamp") - F.expr("INTERVAL 4 HOURS")) &
            (F.col("trips.event_timestamp") <= F.col("events.event_end_timestamp") + F.expr("INTERVAL 2 HOURS")),
            how="left"
        )

        # Calculate distance and filter by proximity
        combined = self._add_spatial_distance(combined)

        # Flag nearby events
        combined = combined.withColumn(
            "has_nearby_event",
            (F.col("distance_to_event_miles").isNotNull()) &
            (F.col("distance_to_event_miles") <= self.event_proximity_miles)
        )

        return combined

    def _add_spatial_distance(self, df: DataFrame) -> DataFrame:
        """
        Calculate haversine distance between pickup location and event venue.

        Args:
            df: DataFrame with pickup_lat, pickup_lon, event_lat, event_lon

        Returns:
            DataFrame with distance_to_event_miles column
        """
        # Haversine formula for distance calculation
        # Using SQL expressions for compatibility
        return df.withColumn(
            "distance_to_event_miles",
            F.when(
                F.col("event_lat").isNotNull() & F.col("pickup_lat").isNotNull(),
                # Simplified distance calculation (approximate for small distances)
                F.sqrt(
                    F.pow((F.col("event_lat") - F.col("pickup_lat")) * 69.0, 2) +
                    F.pow((F.col("event_lon") - F.col("pickup_lon")) * 54.6, 2)
                )
            ).otherwise(F.lit(None))
        )

    def aggregate(self, df: DataFrame) -> DataFrame:
        """
        Aggregate enriched trip data to zone + window level.

        Args:
            df: Combined DataFrame from all streams

        Returns:
            Aggregated DataFrame with context metrics
        """
        # Create time windows
        aggregated = (
            df.groupBy(
                F.window("event_timestamp", self.aggregation_window),
                F.col("pu").alias("zone_id")
            )
            .agg(
                # Trip volume
                F.count("*").alias("trip_count"),
                F.avg("dist_normalized").alias("avg_distance"),

                # Weather context
                F.avg("temperature").alias("avg_temperature"),
                F.avg("precipitation").alias("avg_precipitation"),
                F.avg("wind_speed").alias("avg_wind_speed"),
                F.avg("visibility").alias("avg_visibility"),
                F.mode("weather_condition").alias("dominant_weather_condition"),

                # Event context
                F.sum(F.when(F.col("has_nearby_event"), 1).otherwise(0)).alias("trips_with_nearby_events"),
                F.countDistinct(F.when(F.col("has_nearby_event"), F.col("event_id"))).alias("unique_events_count"),
                F.max(F.when(F.col("has_nearby_event"), F.col("event_attendance"))).alias("max_event_attendance"),
                F.first(F.when(F.col("has_nearby_event"), F.col("event_type")), ignorenulls=True).alias("primary_event_type"),

                # Distance to events
                F.min("distance_to_event_miles").alias("min_distance_to_event_miles")
            )
        )

        # Calculate derived metrics
        aggregated = self._calculate_demand_lift(aggregated)

        # Enrich with zone metadata
        zones_df = self._load_zones_dimension()
        if zones_df is not None:
            aggregated = aggregated.join(
                F.broadcast(zones_df),
                on="zone_id",
                how="left"
            )

        # Add window partitioning columns
        aggregated = self.add_window_partitions(aggregated)

        # Add metadata
        aggregated = self.add_metadata(aggregated)

        return aggregated

    def _calculate_demand_lift(self, df: DataFrame) -> DataFrame:
        """
        Calculate demand lift indicators.

        Args:
            df: Aggregated DataFrame

        Returns:
            DataFrame with demand lift metrics
        """
        return (
            df
            .withColumn(
                "event_impact_pct",
                (F.col("trips_with_nearby_events") / F.col("trip_count") * 100.0)
            )
            .withColumn(
                "has_active_event",
                F.col("unique_events_count") > 0
            )
            .withColumn(
                "weather_severity_score",
                # Simple severity: higher precipitation + lower visibility + higher wind
                (F.col("avg_precipitation") * 10.0) +
                ((10.0 - F.coalesce(F.col("avg_visibility"), F.lit(10.0))) * 2.0) +
                (F.col("avg_wind_speed") / 10.0)
            )
            .withColumn(
                "weather_category",
                F.when(F.col("avg_precipitation") > 0.1, "rainy")
                .when(F.col("avg_temperature") < 32, "freezing")
                .when(F.col("avg_temperature") > 90, "hot")
                .when(F.col("avg_wind_speed") > 25, "windy")
                .otherwise("normal")
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
    """Main entry point for zone demand context consumer."""
    import argparse

    parser = argparse.ArgumentParser(
        description="Run Gold Zone Demand Context Hourly Consumer (Maximum Complexity)"
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
        "--trips-watermark",
        type=str,
        default="5 minutes",
        help="Watermark for trips stream"
    )
    parser.add_argument(
        "--weather-watermark",
        type=str,
        default="5 minutes",
        help="Watermark for weather stream"
    )
    parser.add_argument(
        "--events-watermark",
        type=str,
        default="1 hour",
        help="Watermark for events stream"
    )
    parser.add_argument(
        "--event-proximity",
        type=float,
        default=1.0,
        help="Event proximity threshold in miles"
    )

    args = parser.parse_args()

    consumer = GoldZoneDemandContextHourly(
        trigger_interval=args.trigger_interval,
        aggregation_window=args.window,
        trips_watermark=args.trips_watermark,
        weather_watermark=args.weather_watermark,
        events_watermark=args.events_watermark,
        event_proximity_miles=args.event_proximity
    )

    print("=" * 70)
    print("Gold Layer: Zone Demand Context Hourly (MAXIMUM COMPLEXITY)")
    print("=" * 70)
    print(f"Sources: silver.trips + silver.weather + silver.events")
    print(f"Target: gold.zone_demand_context_hourly")
    print(f"Window: {args.window}")
    print(f"Trips Watermark: {args.trips_watermark}")
    print(f"Weather Watermark: {args.weather_watermark}")
    print(f"Events Watermark: {args.events_watermark}")
    print(f"Event Proximity: {args.event_proximity} miles")
    print("Pattern: Triple stream join + temporal + spatial alignment")
    print("=" * 70)
    print("\nThis demonstrates graduate-level streaming architecture:")
    print("  ✓ Stream-stream-stream joins")
    print("  ✓ Coordinated watermarking (3 sources)")
    print("  ✓ Temporal alignment (interval joins)")
    print("  ✓ Spatial filtering (distance calculations)")
    print("  ✓ Complex business logic (demand lift)")
    print("=" * 70)

    consumer.run()


if __name__ == "__main__":
    main()
