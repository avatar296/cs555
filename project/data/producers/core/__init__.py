#!/usr/bin/env python3
"""
Core producer implementations.
"""

from .base import BaseProducer
from .trips import TripsProducer
from .weather import WeatherProducer
from .events import EventsProducer

__all__ = [
    'BaseProducer',
    'TripsProducer',
    'WeatherProducer',
    'EventsProducer'
]