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
    years: str = "2023"
    months: str = ""

    # Synthetic data settings
    synthetic_mode: str = "fallback"  # none, fallback, synthetic, mixed
    synthetic_ratio: float = 0.2

    # Key configuration
    salt_keys: int = 0
    key_mode: str = "pu"  # pu or how_pu

    # Lateness injection
    p_late: float = 0.0
    late_min: int = 300
    late_max: int = 1200

    # Filters
    min_dist: float = 0.2
    max_dist: float = 40.0

    # Processing
    chunk_rows: int = 10000

    # Advanced Kafka settings
    enable_idempotence: bool = True
    max_in_flight_requests: int = 1

    @classmethod
    def from_env(cls):
        """Create trips config from environment variables."""
        # Define trips-specific environment mappings
        trips_mappings = {
            # Override compression default for trips
            'compression_type': ('COMPRESSION', str, 'zstd'),

            # Trips-specific fields
            'dataset': ('DATASET', str, cls.dataset),
            'years': ('YEARS', str, cls.years),
            'months': ('MONTHS', str, cls.months),
            'synthetic_mode': ('SYNTHETIC_MODE', str, cls.synthetic_mode),
            'synthetic_ratio': ('SYNTHETIC_RATIO', float, cls.synthetic_ratio),
            'salt_keys': ('SALT_KEYS', int, cls.salt_keys),
            'key_mode': ('KEY_MODE', str, cls.key_mode),
            'p_late': ('P_LATE', float, cls.p_late),
            'late_min': ('LATE_MIN', int, cls.late_min),
            'late_max': ('LATE_MAX', int, cls.late_max),
            'min_dist': ('MIN_DIST', float, cls.min_dist),
            'max_dist': ('MAX_DIST', float, cls.max_dist),
            'chunk_rows': ('CHUNK_ROWS', int, cls.chunk_rows),
            'enable_idempotence': ('IDEMPOTENCE', lambda x: x.lower() == "true", cls.enable_idempotence),
            'max_in_flight_requests': ('MAX_INFLIGHT', int, cls.max_in_flight_requests)
        }

        return super().from_env(name="trips", env_mappings=trips_mappings)