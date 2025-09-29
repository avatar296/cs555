#!/usr/bin/env python3
"""
Configuration for Bronze Layer Events Consumer

Extends base configuration with events-specific defaults.
Bronze layer preserves raw data - no quality filtering.
"""

from dataclasses import dataclass

from .base import BaseConsumerConfig


@dataclass
class EventsConsumerConfig(BaseConsumerConfig):
    """Configuration for bronze events consumer."""

    # Override base defaults for events
    source_topic: str = "events.nyc"
    target_table: str = "bronze.events"
    consumer_name: str = "bronze-events"
    trigger_interval: str = "60 seconds"
    max_offsets_per_trigger: int = 5000