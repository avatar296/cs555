#!/usr/bin/env python3
"""
NYC Synthetic Data Scenarios Package

Provides shared patterns, events, and correlations for generating
realistic synthetic data across trips, weather, and events producers.
"""

from .base import NYCScenarios, get_scenarios
from .constants import (
    NYC_VENUES,
    ZONE_CATEGORIES,
    AIRPORTS,
    MANHATTAN_BUSINESS,
    MANHATTAN_RESIDENTIAL,
    BROOKLYN_HIP,
    QUEENS_RESIDENTIAL,
    TOURIST_AREAS,
    NIGHTLIFE_ZONES
)
from .patterns import RUSH_HOUR_PATTERNS
from .weather import WEATHER_IMPACTS, get_seasonal_weather_params, generate_weather_conditions
from .events import SPECIAL_EVENTS, RECURRING_PATTERNS, should_generate_event
from .trips import generate_trip_demand, get_zone_characteristics

__all__ = [
    # Main class and getter
    'NYCScenarios',
    'get_scenarios',

    # Constants
    'NYC_VENUES',
    'ZONE_CATEGORIES',
    'AIRPORTS',
    'MANHATTAN_BUSINESS',
    'MANHATTAN_RESIDENTIAL',
    'BROOKLYN_HIP',
    'QUEENS_RESIDENTIAL',
    'TOURIST_AREAS',
    'NIGHTLIFE_ZONES',

    # Patterns
    'RUSH_HOUR_PATTERNS',
    'WEATHER_IMPACTS',
    'SPECIAL_EVENTS',
    'RECURRING_PATTERNS',

    # Functions
    'get_seasonal_weather_params',
    'generate_weather_conditions',
    'generate_trip_demand',
    'get_zone_characteristics',
    'should_generate_event'
]

__version__ = '1.0.0'