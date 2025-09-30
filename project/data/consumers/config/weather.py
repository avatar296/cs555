#!/usr/bin/env python3
"""
Configuration for Bronze Layer Weather Consumer

Extends base configuration with weather-specific defaults.
Bronze layer preserves raw data - no quality filtering.
"""

from dataclasses import dataclass

from .base import BaseConsumerConfig


@dataclass
class WeatherConsumerConfig(BaseConsumerConfig):
    """Configuration for bronze weather consumer."""

    # Override base defaults for weather
    source_topic: str = "weather.updates"
    target_table: str = "bronze.weather"
    consumer_name: str = "bronze-weather"
    checkpoint_location: str = "/tmp/checkpoint/bronze/bronze-weather"
    trigger_interval: str = "60 seconds"
    max_offsets_per_trigger: int = 5000