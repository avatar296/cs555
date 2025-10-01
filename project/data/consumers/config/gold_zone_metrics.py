#!/usr/bin/env python3
"""
Configuration for Gold Layer Zone Metrics Consumer

Simple pattern: Standard windowed aggregation.
"""

from dataclasses import dataclass


@dataclass
class GoldZoneMetricsConfig:
    """Configuration for gold zone metrics hourly consumer."""

    # Source and target
    source_table: str = "silver.trips"
    target_table: str = "gold.zone_metrics_hourly"
    consumer_name: str = "gold-zone-metrics-hourly"

    # Checkpoint and trigger
    checkpoint_location: str = "/tmp/checkpoint/gold/zone_metrics_hourly"
    trigger_interval: str = "5 seconds"

    # Windowing and watermark
    aggregation_window: str = "15 minutes"  # Fast feedback for POC
    watermark: str = "5 minutes"  # Minimal wait for late data

    # Iceberg warehouse
    warehouse_path: str = "s3a://lakehouse/iceberg"
    catalog_name: str = "local"
