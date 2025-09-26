#!/usr/bin/env python3
"""
Configuration for weather producer.
"""

from dataclasses import dataclass, field
from .base import BaseConfig


@dataclass
class WeatherConfig(BaseConfig):
    """Configuration for weather producer."""

    # Data configuration
    year: str = "2023"
    months: str = ""

    # Station configuration (filled by constants)
    stations: dict = field(default_factory=dict)

    @classmethod
    def from_env(cls):
        """Create weather config from environment variables."""
        # Define weather-specific environment mappings
        weather_mappings = {
            'year': ('YEAR', str, cls.year),
            'months': ('MONTHS', str, cls.months)
        }

        return super().from_env(name="weather", env_mappings=weather_mappings)