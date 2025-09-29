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
        # Use first year from years list and strip any whitespace
        years = self.config.years
        year = years[0] if isinstance(years, list) else years
        year = str(year).strip()  # Remove any trailing/leading whitespace
        url = NOAA_BASE_URL.format(
            year=year,
            station=station_id
        )

        # Just check if we can read the CSV, don't use limit in options
        query = QueryBuilder.build_csv_query(
            url=url,
            columns=["COUNT(*)"]
        )
        # Add LIMIT directly to the query
        query += "LIMIT 1"

        result = self.execute_duckdb_query(
            query,
            fetch_one=True,
            error_message="NOAA availability check failed"
        )
        return result is not None

    def fetch(self) -> Generator:
        """Fetch NOAA weather data."""
        print(f"[DEBUG] NOAAWeatherSource.fetch() called", file=sys.stderr)
        all_data = self._fetch_all_stations()

        if not all_data:
            print(f"[DEBUG] No data returned from _fetch_all_stations", file=sys.stderr)
            return

        print(f"[DEBUG] Got data, calling _generate_observations", file=sys.stderr)
        # Generate data with continuous mode support
        yield from self._generate_observations(all_data)

    def _fetch_all_stations(self) -> Dict:
        """Fetch data from all weather stations."""
        all_data = {}

        # Use first year from years list and strip any whitespace
        years = self.config.years
        year = years[0] if isinstance(years, list) else years
        year = str(year).strip()  # Remove any trailing/leading whitespace

        for station_id, station_name in NYC_WEATHER_STATIONS.items():
            url = NOAA_BASE_URL.format(
                year=year,
                station=station_id
            )

            # Build where conditions with month filtering
            where_conditions = ["DATE IS NOT NULL"]

            # Add month filtering if specified
            if self.config.months:
                months = self.config.months
                if isinstance(months, str):
                    # Convert "06,07,08" or "06,07,08    " to list
                    months = [m.strip() for m in months.strip().split(',') if m.strip()]

                if months:
                    # Create month filter condition
                    month_conditions = []
                    for month in months:
                        month_num = str(month).zfill(2)  # Ensure 2 digits
                        # NOAA DATE format is YYYYMMDDHHMM
                        # Check if month matches in position 5-6
                        # Cast DATE to VARCHAR since it's read as TIMESTAMP
                        month_conditions.append(f"SUBSTR(CAST(DATE AS VARCHAR), 5, 2) = '{month_num}'")

                    if month_conditions:
                        where_conditions.append(f"({' OR '.join(month_conditions)})")

            query = QueryBuilder.build_csv_query(
                url=url,
                columns=["DATE", "STATION", "TMP", "WND", "MW1", "AA1"],
                where_conditions=where_conditions,
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
        print(f"[DEBUG] _generate_observations: all_data keys: {list(all_data.keys())}", file=sys.stderr)
        for sid, obs in all_data.items():
            print(f"[DEBUG] Station {sid}: {len(obs) if obs else 0} observations", file=sys.stderr)

        # Check if all stations have no data
        if all(not obs for obs in all_data.values()):
            print(f"[WARNING] All stations have no observations, no data to generate", file=sys.stderr)
            return  # Exit early if no data at all

        indices = {sid: 0 for sid in all_data.keys()}
        cycles = 0

        while True:
            yielded_in_cycle = 0
            stations_with_data = 0
            for station_id, observations in all_data.items():
                if not observations:
                    print(f"[DEBUG] Station {station_id} has no observations, skipping", file=sys.stderr)
                    continue

                stations_with_data += 1  # Count stations that have data
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

                yielded_in_cycle += 1
                if yielded_in_cycle <= 2:
                    print(f"[DEBUG] Yielding observation {yielded_in_cycle} for station {station_id}", file=sys.stderr)
                yield (station_id, obs)

            # If no stations have data, break out of the loop
            if stations_with_data == 0:
                print(f"[WARNING] No stations have any data left, exiting generator", file=sys.stderr)
                return


class SyntheticWeatherSource(WeatherDataSource, SyntheticSourceMixin, ContinuousDataMixin):
    """Synthetic weather data generator."""

    def is_available(self) -> bool:
        """Synthetic data is always available."""
        return True

    def fetch(self) -> Generator:
        """Generate synthetic weather data."""
        dates = generate_date_list(
            self.config.years,
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
        import time
        for hour in range(24):
            obs_time = date.replace(hour=hour)
            obs = self._create_observation(obs_time)

            # Yield for each station with realistic timing
            for i, station_id in enumerate(NYC_WEATHER_STATIONS.keys()):
                yield (station_id, obs)

                # Small delay between stations reporting (simulates network delays)
                if not self.config.burst_mode and i < len(NYC_WEATHER_STATIONS) - 1:
                    time.sleep(0.05)  # 50ms between station reports

            # Delay between hours (simulates hourly reporting)
            if not self.config.burst_mode and hour < 23:
                time.sleep(0.5)  # 500ms between hours (compressed time)

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