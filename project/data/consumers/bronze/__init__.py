"""
Bronze Layer Consumers for Medallion Architecture
"""

from .trips import BronzeTripsConsumer
from .weather import BronzeWeatherConsumer
from .events import BronzeEventsConsumer

__all__ = [
    'BronzeTripsConsumer',
    'BronzeWeatherConsumer',
    'BronzeEventsConsumer'
]