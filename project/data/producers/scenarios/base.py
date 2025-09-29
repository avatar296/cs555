#!/usr/bin/env python3
"""
Base class for NYC scenarios.

Provides the main NYCScenarios class that ties together all scenario components.
"""

from .constants import *
from .patterns import RUSH_HOUR_PATTERNS
from .weather import (
    WEATHER_IMPACTS,
    get_seasonal_weather_params,
    generate_weather_conditions
)
from .events import (
    SPECIAL_EVENTS,
    RECURRING_PATTERNS,
    should_generate_event
)
from .trips import (
    generate_trip_demand,
    get_zone_characteristics
)


class NYCScenarios:
    """
    Main class for NYC scenario generation.

    Provides access to all scenario components and generation methods.
    """

    # Import constants as class attributes for backward compatibility
    SPECIAL_EVENTS = SPECIAL_EVENTS
    RECURRING_PATTERNS = RECURRING_PATTERNS
    RUSH_HOUR_PATTERNS = RUSH_HOUR_PATTERNS
    WEATHER_IMPACTS = WEATHER_IMPACTS
    ZONE_CATEGORIES = ZONE_CATEGORIES

    @staticmethod
    def get_seasonal_weather_params(date):
        """Get seasonal weather parameters for a given date."""
        return get_seasonal_weather_params(date)

    @staticmethod
    def generate_weather_conditions(date, station_id=None):
        """Generate realistic weather conditions for a given date."""
        return generate_weather_conditions(date, station_id)

    @staticmethod
    def generate_trip_demand(timestamp, base_rate=100):
        """Calculate trip demand for a given timestamp."""
        return generate_trip_demand(timestamp, base_rate)

    @staticmethod
    def get_zone_characteristics(zone_id):
        """Get characteristics of a zone for trip generation."""
        return get_zone_characteristics(zone_id)

    @staticmethod
    def should_generate_event(date, venue):
        """Determine if an event should occur at a venue on a given date."""
        return should_generate_event(date, venue)


# Singleton instance for shared random seed and backward compatibility
_scenarios = NYCScenarios()


def get_scenarios() -> NYCScenarios:
    """
    Get the shared scenarios instance.

    Returns:
        NYCScenarios singleton instance
    """
    return _scenarios