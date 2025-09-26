#!/usr/bin/env python3
"""
Core constants and settings for producers.
"""

# Time-based patterns
RUSH_HOUR_MORNING = (7, 9)
RUSH_HOUR_EVENING = (17, 19)
PEAK_EVENT_HOURS = [14, 16, 19, 20]

# Data processing settings
DEFAULT_CHUNK_SIZE = 10000
DEFAULT_METRIC_INTERVAL = 10.0  # seconds

# Event categories
EVENT_TYPES = ['concert', 'sports', 'theater', 'conference', 'festival']

# Weather condition mappings
WEATHER_CONDITIONS = {
    "clear": {"code": "", "impact": 1.0},
    "cloudy": {"code": "", "impact": 1.0},
    "rain": {"code": "RA", "impact": 0.85},
    "heavy_rain": {"code": "RA+", "impact": 0.7},
    "snow": {"code": "SN", "impact": 0.8},
    "heavy_snow": {"code": "SN+", "impact": 0.6},
    "fog": {"code": "FG", "impact": 0.9}
}

# Default fallback values
DEFAULT_TEMPERATURE_F = 70.0
DEFAULT_WIND_SPEED_MPH = 5.0
DEFAULT_VISIBILITY_MILES = 10.0
DEFAULT_PRECIPITATION_INCHES = 0.0
DEFAULT_EVENT_ATTENDANCE = 1000
DEFAULT_EVENT_DURATION_HOURS = 3

# Radius for event zone impact (in miles)
EVENT_IMPACT_RADIUS = 0.5

# Error messages
ERROR_SCHEMA_NOT_FOUND = "✗ Schema file not found: {path}"
ERROR_SCHEMA_REGISTRY = "✗ Failed to connect to Schema Registry: {error}"
ERROR_NO_DATA = "No {type} data available"
ERROR_FETCH_FAILED = "⚠ Could not fetch {source}: {error}"

# Success messages
SUCCESS_CONNECTED = "✓ Connected to {service}"
SUCCESS_LOADED = "✓ Loaded {count} {type}"
SUCCESS_USING_SYNTHETIC = "✓ Using synthetic data"

# Status messages
STATUS_STARTING = "Starting {name} Producer"
STATUS_FETCHING = "Fetching {source} data..."
STATUS_PROCESSING = "Processing {count} records..."