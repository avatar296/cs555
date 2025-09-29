"""
Data Consumers for Medallion Architecture
"""

from .bronze import (
    BronzeTripsConsumer,
    BronzeWeatherConsumer,
    BronzeEventsConsumer
)

__all__ = [
    'BronzeTripsConsumer',
    'BronzeWeatherConsumer',
    'BronzeEventsConsumer'
]