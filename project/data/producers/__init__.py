#!/usr/bin/env python3
"""
Kafka Producers Package - Reorganized Structure

This package provides Kafka producers for NYC data streaming with a clean,
modular organization following DRY principles.

Structure:
- core/: Producer implementations
- config/: Configuration management
- common/: Constants and shared resources
- utils/: Utility functions
- data_sources/: Data source abstractions
"""

# Import core producers
from .core import (
    BaseProducer,
    TripsProducer,
    WeatherProducer,
    EventsProducer
)

# Import configurations
from .config import (
    BaseConfig,
    TripsConfig,
    WeatherConfig,
    EventsConfig,
    get_producer_config
)

# Import key utilities
from .utils import (
    get_duckdb_connection,
    generate_date_list,
    generate_salted_key,
    normalize_timestamp
)

# Import data source factories
from .data_sources import (
    get_trip_data_source,
    get_weather_data_source,
    get_event_data_source
)

__all__ = [
    # Core producers
    'BaseProducer',
    'TripsProducer',
    'WeatherProducer',
    'EventsProducer',

    # Configurations
    'BaseConfig',
    'TripsConfig',
    'WeatherConfig',
    'EventsConfig',
    'get_producer_config',

    # Key utilities
    'get_duckdb_connection',
    'generate_date_list',
    'generate_salted_key',
    'normalize_timestamp',

    # Data sources
    'get_trip_data_source',
    'get_weather_data_source',
    'get_event_data_source'
]

__version__ = '3.0.0'  # Major reorganization version