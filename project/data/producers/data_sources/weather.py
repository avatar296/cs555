#!/usr/bin/env python3
"""
Weather data sources for Kafka producers.

Provides NOAA and synthetic weather data sources.
"""

import sys
import random
from datetime import timedelta
from typing import Generator, Dict

from .base import (
    DataSource,
    ConfigurableDataSource,
    DatabaseSourceMixin,
    SyntheticSourceMixin,
    ContinuousDataMixin,
    QueryBuilder
)

from ..utils import generate_date_list
from ..common import (
    NOAA_BASE_URL,
    NYC_WEATHER_STATIONS,
    fahrenheit_to_celsius,
    mph_to_mps
)


class WeatherDataSource(ConfigurableDataSource, DataSource):
    """Base class for weather data sources."""

    def __init__(self, config):
        """Initialize with weather configuration."""
        super().__init__(config)
        # Add stations to config if not present
        if not hasattr(config, 'stations'):
            config.stations = NYC_WEATHER_STATIONS


class NOAAWeatherSource(WeatherDataSource, DatabaseSourceMixin, ContinuousDataMixin):
    """Real NOAA weather data source."""

    def is_available(self) -> bool:
        """Check if NOAA data is accessible."""
        # Try one station
        station_id = list(NYC_WEATHER_STATIONS.keys())[0]
        url = NOAA_BASE_URL.format(
            year=self.config.year,
            station=station_id
        )

        query = QueryBuilder.build_csv_query(
            url=url,
            columns=["COUNT(*)"],
            options={'limit': '1'}
        )

        result = self.execute_duckdb_query(
            query,
            fetch_one=True,
            error_message="NOAA availability check failed"
        )
        return result is not None

    def fetch(self) -> Generator:
        """Fetch NOAA weather data."""
        all_data = self._fetch_all_stations()

        if not all_data:
            return

        # Generate data with continuous mode support
        yield from self._generate_observations(all_data)

    def _fetch_all_stations(self) -> Dict:
        """Fetch data from all weather stations."""
        all_data = {}

        for station_id, station_name in NYC_WEATHER_STATIONS.items():
            url = NOAA_BASE_URL.format(
                year=self.config.year,
                station=station_id
            )

            query = QueryBuilder.build_csv_query(
                url=url,
                columns=["DATE", "STATION", "TMP", "WND", "MW1", "AA1"],
                where_conditions=["DATE IS NOT NULL"],
                options={
                    'header': 'true',
                    'delim': "','",
                    'quote': "'\"'",
                    'escape': "'\"'",
                    'ignore_errors': 'true'
                }
            )

            result = self.execute_duckdb_query(
                query,
                fetch_all=True,
                error_message=f"Could not fetch {station_name}"
            )

            if result:
                all_data[station_id] = result
                print(f"✓ Loaded {len(result)} observations from {station_name}",
                      file=sys.stderr)
            else:
                all_data[station_id] = []

        return all_data

    def _generate_observations(self, all_data: Dict) -> Generator:
        """Generate observations with continuous mode support."""
        indices = {sid: 0 for sid in all_data.keys()}
        cycles = 0

        while True:
            for station_id, observations in all_data.items():
                if not observations:
                    continue

                idx = indices[station_id]

                # Check if we need to cycle
                if idx >= len(observations):
                    if not self.should_continue(self.config):
                        return  # Stop if not in continuous mode

                    idx = 0
                    indices[station_id] = 0
                    if station_id == list(all_data.keys())[0]:  # Only print once
                        cycles += 1
                        if cycles == 1:
                            print("Weather data exhausted, continuing with replay...",
                                  file=sys.stderr)

                obs = observations[idx]
                indices[station_id] += 1

                # Adjust timestamp if replaying
                if cycles > 0 and isinstance(obs, dict) and 'DATE' in obs:
                    obs = {
                        **obs,
                        'DATE': self.adjust_timestamp_for_cycle(
                            obs['DATE'],
                            cycles
                        )
                    }

                yield (station_id, obs)


class SyntheticWeatherSource(WeatherDataSource, SyntheticSourceMixin, ContinuousDataMixin):
    """Synthetic weather data generator."""

    def fetch(self) -> Generator:
        """Generate synthetic weather data."""
        dates = generate_date_list(
            self.config.year,
            self.get_config_value('months', '')
        )

        while True:
            for date in dates:
                yield from self._generate_day_weather(date)

            # Check if should continue
            if not self.should_continue(self.config):
                break

            # Advance dates for next cycle
            dates = [date + timedelta(days=365) for date in dates]

    def _generate_day_weather(self, date) -> Generator:
        """Generate weather observations for a single day."""
        for hour in range(24):
            obs_time = date.replace(hour=hour)
            obs = self._create_observation(obs_time)

            # Yield for each station
            for station_id in NYC_WEATHER_STATIONS.keys():
                yield (station_id, obs)

    def _create_observation(self, obs_time) -> Dict:
        """Create a single weather observation."""
        weather = self.scenarios.generate_weather_conditions(obs_time)

        # Convert to NOAA-like format
        temp_c = fahrenheit_to_celsius(weather["temperature"])
        wind_dir = random.randint(0, 359)
        wind_speed_mps = mph_to_mps(weather["wind_speed"])

        # Get weather code using mixin method
        mw1 = self.generate_weather_code(weather["conditions"])

        return {
            'DATE': obs_time.isoformat(),
            'TMP': f"{temp_c:.1f}",
            'WND': f"{wind_dir:03d},1,N,{wind_speed_mps:.1f}",
            'MW1': mw1,
            'AA1': f"{weather['precipitation']:.2f}" if weather['precipitation'] > 0 else '0.0'
        }