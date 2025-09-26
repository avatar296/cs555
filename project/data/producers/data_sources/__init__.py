#!/usr/bin/env python3
"""
Data Sources Package

Provides modular data source implementations for Kafka producers.
This package refactors the original monolithic data_sources.py file
following DRY principles.
"""

# Import base classes and mixins
from .base import (
    DataSource,
    ConfigurableDataSource,
    DatabaseSourceMixin,
    SyntheticSourceMixin,
    ContinuousDataMixin,
    DataFetcher,
    QueryBuilder
)

# Import trip data sources
from .trips import (
    TripDataSource,
    NYCTLCDataSource,
    SyntheticTripSource,
    MixedTripSource
)

# Import weather data sources
from .weather import (
    WeatherDataSource,
    NOAAWeatherSource,
    SyntheticWeatherSource
)

# Import event data sources
from .events import (
    EventDataSource,
    NYCOpenDataEventSource,
    SyntheticEventSource
)

# Import factory functions
from .factories import (
    get_data_source_with_fallback,
    get_trip_data_source,
    get_weather_data_source,
    get_event_data_source,
    get_data_source,
    DATA_SOURCE_REGISTRY
)

__all__ = [
    # Base classes
    'DataSource',
    'ConfigurableDataSource',
    'DatabaseSourceMixin',
    'SyntheticSourceMixin',
    'ContinuousDataMixin',
    'DataFetcher',
    'QueryBuilder',

    # Trip sources
    'TripDataSource',
    'NYCTLCDataSource',
    'SyntheticTripSource',
    'MixedTripSource',

    # Weather sources
    'WeatherDataSource',
    'NOAAWeatherSource',
    'SyntheticWeatherSource',

    # Event sources
    'EventDataSource',
    'NYCOpenDataEventSource',
    'SyntheticEventSource',

    # Factory functions
    'get_data_source_with_fallback',
    'get_trip_data_source',
    'get_weather_data_source',
    'get_event_data_source',
    'get_data_source',
    'DATA_SOURCE_REGISTRY'
]

__version__ = '2.0.0'  # Version after DRY refactoring