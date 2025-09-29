#!/usr/bin/env python3
"""
Configuration for trips producer.
"""

from dataclasses import dataclass
from .base import BaseConfig


@dataclass
class TripsConfig(BaseConfig):
    """Configuration for trips producer."""

    # Data source configuration
    dataset: str = "yellow"

    # Note: years, months, synthetic_mode, synthetic_ratio inherited from BaseConfig
    # Note: salt_keys, key_mode, lateness, idempotence now inherited from BaseConfig

    # Override key_mode default for trips
    key_mode: str = "pu"  # pu or how_pu for trips specifically

    # Filters
    min_dist: float = 0.2
    max_dist: float = 40.0

    # Override Kafka settings for trips (stricter for high volume)
    max_in_flight_requests: int = 1  # Stricter than base default of 5

    @classmethod
    def from_env(cls):
        """Create trips config from environment variables."""
        # Define trips-specific environment mappings
        trips_mappings = {
            # Override compression default for trips
            'compression_type': ('COMPRESSION', str, 'zstd'),

            # Trips-specific fields only
            'dataset': ('DATASET', str, cls.dataset),
            'key_mode': ('KEY_MODE', str, 'pu'),  # Override default
            'min_dist': ('MIN_DIST', float, cls.min_dist),
            'max_dist': ('MAX_DIST', float, cls.max_dist),
            'max_in_flight_requests': ('MAX_INFLIGHT', int, 1)  # Override base default
        }

        return super().from_env(name="trips", env_mappings=trips_mappings)