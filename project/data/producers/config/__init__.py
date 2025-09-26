#!/usr/bin/env python3
"""
Configuration package for Kafka producers.
"""

from .base import BaseConfig
from .trips import TripsConfig
from .weather import WeatherConfig
from .events import EventsConfig

__all__ = [
    'BaseConfig',
    'TripsConfig',
    'WeatherConfig',
    'EventsConfig'
]

# Factory function for backward compatibility
def get_producer_config(producer_name: str):
    """
    Factory function to get appropriate config for a producer.

    Args:
        producer_name: Name of producer (trips, weather, events)

    Returns:
        Appropriate config instance
    """
    configs = {
        "trips": TripsConfig,
        "weather": WeatherConfig,
        "events": EventsConfig
    }

    config_class = configs.get(producer_name)
    if not config_class:
        raise ValueError(f"Unknown producer: {producer_name}")

    return config_class.from_env()