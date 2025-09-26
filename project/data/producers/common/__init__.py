#!/usr/bin/env python3
"""
Common constants and utilities package.
"""

# Import URLs
from .urls import (
    NYC_TLC_BASE_URL,
    NOAA_BASE_URL,
    NYC_EVENTS_URL,
    NYC_PARKS_URL
)

# Import locations
from .locations import (
    NYC_WEATHER_STATIONS,
    NYC_VENUES,
    POPULAR_ZONE_PAIRS,
    TOTAL_NYC_ZONES,
    DEFAULT_ZONE_LAT,
    DEFAULT_ZONE_LON
)

# Import converters
from .converters import (
    celsius_to_fahrenheit,
    fahrenheit_to_celsius,
    mph_to_mps,
    mps_to_mph,
    km_to_miles,
    miles_to_km,
    MPS_TO_MPH,
    MILES_TO_KM
)

# Import schemas
from .schemas import (
    SCHEMAS,
    DUCKDB_SETUP
)

# Import core constants
from .constants import (
    RUSH_HOUR_MORNING,
    RUSH_HOUR_EVENING,
    PEAK_EVENT_HOURS,
    DEFAULT_CHUNK_SIZE,
    DEFAULT_METRIC_INTERVAL,
    EVENT_TYPES,
    WEATHER_CONDITIONS,
    DEFAULT_TEMPERATURE_F,
    DEFAULT_WIND_SPEED_MPH,
    DEFAULT_VISIBILITY_MILES,
    DEFAULT_PRECIPITATION_INCHES,
    DEFAULT_EVENT_ATTENDANCE,
    DEFAULT_EVENT_DURATION_HOURS,
    EVENT_IMPACT_RADIUS,
    ERROR_SCHEMA_NOT_FOUND,
    ERROR_SCHEMA_REGISTRY,
    ERROR_NO_DATA,
    ERROR_FETCH_FAILED,
    SUCCESS_CONNECTED,
    SUCCESS_LOADED,
    SUCCESS_USING_SYNTHETIC,
    STATUS_STARTING,
    STATUS_FETCHING,
    STATUS_PROCESSING
)

__all__ = [
    # URLs
    'NYC_TLC_BASE_URL', 'NOAA_BASE_URL', 'NYC_EVENTS_URL', 'NYC_PARKS_URL',

    # Locations
    'NYC_WEATHER_STATIONS', 'NYC_VENUES', 'POPULAR_ZONE_PAIRS',
    'TOTAL_NYC_ZONES', 'DEFAULT_ZONE_LAT', 'DEFAULT_ZONE_LON',

    # Converters
    'celsius_to_fahrenheit', 'fahrenheit_to_celsius',
    'mph_to_mps', 'mps_to_mph', 'km_to_miles', 'miles_to_km',
    'MPS_TO_MPH', 'MILES_TO_KM',

    # Schemas
    'SCHEMAS', 'DUCKDB_SETUP',

    # Core constants
    'RUSH_HOUR_MORNING', 'RUSH_HOUR_EVENING', 'PEAK_EVENT_HOURS',
    'DEFAULT_CHUNK_SIZE', 'DEFAULT_METRIC_INTERVAL', 'EVENT_TYPES',
    'WEATHER_CONDITIONS', 'DEFAULT_TEMPERATURE_F', 'DEFAULT_WIND_SPEED_MPH',
    'DEFAULT_VISIBILITY_MILES', 'DEFAULT_PRECIPITATION_INCHES',
    'DEFAULT_EVENT_ATTENDANCE', 'DEFAULT_EVENT_DURATION_HOURS',
    'EVENT_IMPACT_RADIUS',

    # Messages
    'ERROR_SCHEMA_NOT_FOUND', 'ERROR_SCHEMA_REGISTRY', 'ERROR_NO_DATA',
    'ERROR_FETCH_FAILED', 'SUCCESS_CONNECTED', 'SUCCESS_LOADED',
    'SUCCESS_USING_SYNTHETIC', 'STATUS_STARTING', 'STATUS_FETCHING',
    'STATUS_PROCESSING'
]