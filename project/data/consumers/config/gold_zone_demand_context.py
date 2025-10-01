#!/usr/bin/env python3
"""
Configuration for Gold Layer Zone Demand Context Consumer

Maximum complexity: Triple stream join with temporal + spatial alignment.
"""

from dataclasses import dataclass


@dataclass
class GoldZoneDemandContextConfig:
    """Configuration for gold zone demand context hourly consumer."""

    # Source tables (multiple streams)
    trips_table: str = "silver.trips"
    weather_table: str = "silver.weather"
    events_table: str = "silver.events"
    target_table: str = "gold.zone_demand_context_hourly"
    consumer_name: str = "gold-zone-demand-context-hourly"

    # Checkpoint and trigger
    checkpoint_location: str = "/tmp/checkpoint/gold/zone_demand_context_hourly"
    trigger_interval: str = "5 seconds"

    # Windowing and watermarks (different for each stream)
    aggregation_window: str = "15 minutes"  # Fast feedback for POC
    trips_watermark: str = "5 minutes"  # Minimal wait for trips
    weather_watermark: str = "5 minutes"  # Minimal wait for weather
    events_watermark: str = "1 hour"  # Longer wait for events (scheduled in advance)

    # Spatial parameters
    event_proximity_miles: float = 1.0  # Events within 1 mile considered "nearby"

    # Iceberg warehouse
    warehouse_path: str = "s3a://lakehouse/iceberg"
    catalog_name: str = "local"
