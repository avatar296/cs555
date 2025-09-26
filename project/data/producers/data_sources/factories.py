#!/usr/bin/env python3
"""
Factory functions for data sources.

Provides factory methods for creating appropriate data sources.
"""

import sys
from typing import Optional

from .base import DataSource
from .trips import (
    TripDataSource,
    NYCTLCDataSource,
    SyntheticTripSource,
    MixedTripSource
)
from .weather import (
    WeatherDataSource,
    NOAAWeatherSource,
    SyntheticWeatherSource
)
from .events import (
    EventDataSource,
    NYCOpenDataEventSource,
    SyntheticEventSource
)


def get_data_source_with_fallback(
    primary_source_class,
    synthetic_source_class,
    config,
    source_name: str,
    test_fetch: bool = False
) -> DataSource:
    """
    Generic factory for data sources with synthetic fallback.

    Args:
        primary_source_class: The primary data source class
        synthetic_source_class: The synthetic fallback class
        config: Configuration object
        source_name: Name for logging
        test_fetch: Whether to test fetching data before confirming availability

    Returns:
        Appropriate DataSource implementation
    """
    primary_source = primary_source_class(config)

    if primary_source.is_available():
        if test_fetch:
            # Test that we can actually fetch data
            try:
                test_gen = primary_source.fetch()
                first_item = next(test_gen, None)
                if first_item:
                    return primary_source
                else:
                    print(f"Falling back to synthetic {source_name} "
                          f"(no data for requested period)",
                          file=sys.stderr)
            except Exception as e:
                print(f"Falling back to synthetic {source_name} "
                      f"(fetch test failed: {e})",
                      file=sys.stderr)
        else:
            return primary_source
    else:
        print(f"Falling back to synthetic {source_name} "
              f"(primary source not available)",
              file=sys.stderr)

    return synthetic_source_class(config)


def get_trip_data_source(config) -> TripDataSource:
    """
    Get appropriate trip data source based on configuration.

    Args:
        config: TripsConfig instance

    Returns:
        Appropriate TripDataSource implementation
    """
    mode = getattr(config, 'synthetic_mode', 'fallback')

    if mode == "synthetic":
        return SyntheticTripSource(config)
    elif mode == "mixed":
        return MixedTripSource(config)
    elif mode == "none":
        return NYCTLCDataSource(config)
    else:  # fallback
        return get_data_source_with_fallback(
            NYCTLCDataSource,
            SyntheticTripSource,
            config,
            "trip data"
        )


def get_weather_data_source(config) -> WeatherDataSource:
    """
    Get appropriate weather data source.

    Args:
        config: WeatherConfig instance

    Returns:
        Appropriate WeatherDataSource implementation
    """
    return get_data_source_with_fallback(
        NOAAWeatherSource,
        SyntheticWeatherSource,
        config,
        "weather data"
    )


def get_event_data_source(config) -> EventDataSource:
    """
    Get appropriate event data source.

    Args:
        config: EventsConfig instance

    Returns:
        Appropriate EventDataSource implementation
    """
    return get_data_source_with_fallback(
        NYCOpenDataEventSource,
        SyntheticEventSource,
        config,
        "events",
        test_fetch=True  # Events need fetch testing due to year limitations
    )


# Registry pattern for dynamic source lookup
DATA_SOURCE_REGISTRY = {
    'trips': {
        'real': NYCTLCDataSource,
        'synthetic': SyntheticTripSource,
        'mixed': MixedTripSource,
        'factory': get_trip_data_source
    },
    'weather': {
        'real': NOAAWeatherSource,
        'synthetic': SyntheticWeatherSource,
        'factory': get_weather_data_source
    },
    'events': {
        'real': NYCOpenDataEventSource,
        'synthetic': SyntheticEventSource,
        'factory': get_event_data_source
    }
}


def get_data_source(source_type: str, config, mode: Optional[str] = None) -> DataSource:
    """
    Get a data source by type and mode.

    Args:
        source_type: Type of data source ('trips', 'weather', 'events')
        config: Configuration object
        mode: Optional mode ('real', 'synthetic', 'mixed', None for auto)

    Returns:
        DataSource instance

    Raises:
        ValueError: If source_type is unknown
    """
    if source_type not in DATA_SOURCE_REGISTRY:
        raise ValueError(f"Unknown source type: {source_type}. "
                        f"Valid types: {list(DATA_SOURCE_REGISTRY.keys())}")

    registry_entry = DATA_SOURCE_REGISTRY[source_type]

    if mode and mode in registry_entry:
        # Use specific mode
        source_class = registry_entry[mode]
        return source_class(config)
    else:
        # Use factory function
        factory = registry_entry['factory']
        return factory(config)