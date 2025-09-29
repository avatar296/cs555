#!/usr/bin/env python3
"""
Configuration for Bronze Layer Trips Consumer

Extends base configuration with trips-specific defaults.
Bronze layer preserves raw data - no quality filtering.
"""

from dataclasses import dataclass

from .base import BaseConsumerConfig


@dataclass
class TripsConsumerConfig(BaseConsumerConfig):
    """Configuration for bronze trips consumer."""

    # Override base defaults for trips
    source_topic: str = "trips.yellow"
    target_table: str = "bronze.trips"
    consumer_name: str = "bronze-trips"
    trigger_interval: str = "30 seconds"
    max_offsets_per_trigger: int = 10000