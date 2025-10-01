#!/usr/bin/env python3
"""
Configuration for Gold Layer OD Flows Consumer

Medium complexity: Aggregation with dual dimension joins.
"""

from dataclasses import dataclass


@dataclass
class GoldODFlowsConfig:
    """Configuration for gold OD flows enriched hourly consumer."""

    # Source and target
    source_table: str = "silver.trips"
    target_table: str = "gold.od_flows_enriched_hourly"
    consumer_name: str = "gold-od-flows-enriched-hourly"

    # Checkpoint and trigger
    checkpoint_location: str = "/tmp/checkpoint/gold/od_flows_enriched_hourly"
    trigger_interval: str = "5 seconds"

    # Windowing and watermark
    aggregation_window: str = "15 minutes"  # Fast feedback for POC
    watermark: str = "5 minutes"  # Minimal wait for late data

    # Iceberg warehouse
    warehouse_path: str = "s3a://lakehouse/iceberg"
    catalog_name: str = "local"
