#!/usr/bin/env python3
"""
Configuration for weather producer.
"""

from dataclasses import dataclass, field
from .base import BaseConfig


@dataclass
class WeatherConfig(BaseConfig):
    """Configuration for weather producer."""

    # Station configuration (filled by constants)
    stations: dict = field(default_factory=dict)

    # Note: years, months, synthetic_mode, synthetic_ratio inherited from BaseConfig

    @classmethod
    def from_env(cls):
        """Create weather config from environment variables."""
        # Weather uses all base mappings, no need for custom overrides
        # since we've standardized on 'years' in BaseConfig
        return super().from_env(name="weather")