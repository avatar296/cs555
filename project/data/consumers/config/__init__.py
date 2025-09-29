"""
Bronze Consumer Configuration Package

Provides configuration classes for all bronze layer consumers.
"""

from .base import BaseConsumerConfig
from .trips import TripsConsumerConfig
from .weather import WeatherConsumerConfig
from .events import EventsConsumerConfig


__all__ = [
    'BaseConsumerConfig',
    'TripsConsumerConfig',
    'WeatherConsumerConfig',
    'EventsConsumerConfig',
    'get_consumer_config'
]


def get_consumer_config(consumer_name: str, **kwargs):
    """
    Factory function to get consumer configuration by name.

    Args:
        consumer_name: Name of the consumer ('trips', 'weather', 'events')
        **kwargs: Configuration field overrides

    Returns:
        Consumer configuration instance

    Raises:
        ValueError: If consumer name is unknown
    """
    configs = {
        'trips': TripsConsumerConfig,
        'bronze-trips': TripsConsumerConfig,
        'weather': WeatherConsumerConfig,
        'bronze-weather': WeatherConsumerConfig,
        'events': EventsConsumerConfig,
        'bronze-events': EventsConsumerConfig,
    }

    config_class = configs.get(consumer_name.lower())
    if not config_class:
        raise ValueError(
            f"Unknown consumer: {consumer_name}. "
            f"Available: {', '.join(configs.keys())}"
        )

    # Create config with any provided overrides
    return config_class(**kwargs)