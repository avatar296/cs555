#!/usr/bin/env python3
"""
Weather Producer (Clean Architecture) - NOAA Data → Kafka

Pulls historical weather data from NOAA stations and maps to NYC taxi zones.
This version uses clean architecture with configuration injection.
"""

import sys
import random
from typing import Generator, Tuple, Any, Dict, Optional
from datetime import datetime

from .base import BaseProducer
from ..utils.zones import ZoneMapper

from ..config.weather import WeatherConfig
from ..common import SCHEMAS, celsius_to_fahrenheit
from ..data_sources.factories import get_weather_data_source
from ..utils import (
    add_lateness,
    generate_salted_key,
    normalize_timestamp
)


class WeatherProducer(BaseProducer):
    """Clean weather producer with configuration injection."""

    def __init__(self, config: WeatherConfig = None):
        """
        Initialize weather producer.

        Args:
            config: WeatherConfig instance (defaults to environment-based config)
        """
        self.config = config or WeatherConfig.from_env()

        super().__init__(
            name="weather",
            schema_file=SCHEMAS["weather"],
            topic=self.config.topic,
            bootstrap_servers=self.config.kafka_bootstrap,
            schema_registry_url=self.config.schema_registry_url,
            rate=self.config.rate,
            batch_size=self.config.batch_size
        )

        # Zone mapper for station assignments
        self.mapper = ZoneMapper()

        # Get appropriate data source
        self.data_source = get_weather_data_source(self.config)

    def get_producer_config(self):
        """Get weather-specific Kafka producer configuration."""
        return {
            "compression.type": self.config.compression_type,
            "batch.num.messages": 1000
        }

    def fetch_data(self) -> Generator:
        """Fetch weather data from configured source, respecting synthetic_mode."""
        print(f"Data mode: {self.config.synthetic_mode}", file=sys.stderr)

        # Check if source is available
        if (
            not self.data_source.is_available()
            and self.config.synthetic_mode != "synthetic"
        ):
            print("Primary data source unavailable, using fallback", file=sys.stderr)

        # Get raw weather observations
        for station_id, observation in self.data_source.fetch():
            # Parse the observation
            base_weather = self.parse_observation(observation)

            # Generate weather for all zones associated with this station
            for zone_id in range(1, 264):
                if self.mapper.get_weather_station_for_zone(zone_id) == station_id:
                    weather_event = self.generate_zone_weather(
                        base_weather,
                        zone_id,
                        station_id
                    )
                    yield weather_event

    def parse_observation(self, obs) -> dict:
        """
        Parse weather observation from various formats.

        Args:
            obs: Weather observation (dict or tuple)

        Returns:
            Parsed weather dict
        """
        if isinstance(obs, dict):
            # Parse temperature
            temp_str = obs.get('TMP', '')
            if temp_str and ',' in temp_str:
                temp_c = float(temp_str.split(',')[0])
                if temp_c != 9999:
                    temp_f = celsius_to_fahrenheit(temp_c)
                else:
                    temp_f = 70.0
            else:
                temp_f = 70.0

            # Parse wind
            wind_str = obs.get('WND', '')
            if wind_str and ',' in wind_str:
                wind_parts = wind_str.split(',')
                if len(wind_parts) > 3:
                    wind_speed = float(wind_parts[3]) * 2.237  # m/s to mph
                else:
                    wind_speed = 5.0
            else:
                wind_speed = 5.0

            # Parse conditions
            mw1 = str(obs.get('MW1', ''))
            if 'RA' in mw1:
                conditions = "heavy_rain" if '+' in mw1 else "rain"
            elif 'SN' in mw1:
                conditions = "heavy_snow" if '+' in mw1 else "snow"
            elif 'FG' in mw1:
                conditions = "fog"
            else:
                conditions = "clear"

            # Parse precipitation
            precip_str = obs.get('AA1', '0')
            try:
                precipitation = float(precip_str)
            except (ValueError, TypeError):
                precipitation = 0.1 if conditions in ['rain', 'snow'] else 0.0

            return {
                'temperature': temp_f,
                'wind_speed': wind_speed,
                'conditions': conditions,
                'visibility': 10.0 if conditions != "fog" else 5.0,
                'precipitation': precipitation,
                'observation_time': obs.get('DATE', datetime.now().isoformat())
            }
        else:
            # Simplified parsing for tuple data
            return {
                'temperature': 70.0 + hash(str(obs)) % 20,
                'precipitation': 0.0 if hash(str(obs)) % 5 > 0 else 0.1,
                'wind_speed': 5.0 + hash(str(obs)) % 15,
                'visibility': 10.0,
                'conditions': ['clear', 'cloudy', 'rain'][hash(str(obs)) % 3],
                'observation_time': datetime.now().isoformat()
            }

    def generate_zone_weather(self, base_weather: dict, zone_id: int, station_id: str) -> dict:
        """
        Generate weather for a specific zone based on station observation.

        Args:
            base_weather: Base weather from station
            zone_id: Taxi zone ID
            station_id: Weather station ID

        Returns:
            Weather event for the zone
        """
        # Add small variations for different zones
        distance_factor = abs(hash(f"{zone_id}{station_id}")) % 10 / 10.0

        return {
            'zone_id': zone_id,
            'temperature': base_weather['temperature'] + random.uniform(-2, 2) * distance_factor,
            'precipitation': base_weather['precipitation'],
            'wind_speed': base_weather['wind_speed'] + random.uniform(-2, 2) * distance_factor,
            'visibility': base_weather['visibility'],
            'conditions': base_weather['conditions'],
            'observation_time': base_weather['observation_time'],
            'station_id': station_id
        }

    def process_record(self, record: Any) -> Tuple[bytes, bytes, Optional[Dict[str, str]]]:
        """
        Process a weather record into Kafka key, value, and headers.

        Args:
            record: Weather event dict

        Returns:
            Tuple of (key_bytes, value_bytes, headers)
        """
        # Extract timestamp for potential lateness injection
        ts_str = record.get("observation_time", datetime.now().isoformat())
        ts_dt = normalize_timestamp(ts_str)
        original_ts = ts_dt

        # Add lateness if configured
        ts_out = add_lateness(
            ts_dt, self.config.p_late, self.config.late_min, self.config.late_max
        )

        # Calculate lateness in milliseconds
        lateness_ms = (
            int((ts_out - original_ts).total_seconds() * 1000)
            if ts_out != original_ts
            else None
        )

        # Generate key based on configuration
        if self.config.key_mode == "station":
            base_key = record.get('station_id', 'unknown')
        elif self.config.key_mode == "zone":
            base_key = str(record['zone_id'])
        else:
            # Default: zone ID
            base_key = str(record['zone_id'])

        # Apply salting if configured
        key = generate_salted_key(base_key, self.config.salt_keys)

        # Create payload with updated timestamp and lateness
        payload = record.copy()
        if ts_out != original_ts:
            payload["observation_time"] = ts_out.isoformat()
        if lateness_ms is not None:
            payload["lateness_ms"] = lateness_ms

        # Producer-specific headers
        additional_headers = {
            'station-id': record.get('station_id', 'unknown'),
            'zone-id': str(record['zone_id'])
        }

        # Use common processing from base class
        return self.process_record_common(record, key, payload, additional_headers)

    def get_summary_info(self) -> str:
        """Get additional summary information."""
        data_source, is_synthetic = self.get_data_source_info()
        if is_synthetic:
            return f"[synthetic: {self.config.synthetic_mode}]"
        elif data_source == "NOAA":
            return "[NOAA weather]"
        return ""


def main():
    """Main entry point."""
    # Create producer with environment configuration
    config = WeatherConfig.from_env()
    producer = WeatherProducer(config)
    producer.run()


if __name__ == "__main__":
    main()