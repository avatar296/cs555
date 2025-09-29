#!/usr/bin/env python3
"""
Base configuration class for all producers.
"""

import os
from dataclasses import dataclass


@dataclass
class BaseConfig:
    """Base configuration shared by all producers."""

    # Kafka configuration
    topic: str
    kafka_bootstrap: str = "localhost:29092"
    schema_registry_url: str = "http://localhost:8082"

    # Producer behavior
    rate: float = 1000.0  # messages per second
    batch_size: int = 1000
    continuous_mode: bool = True  # Continue generating data when exhausted
    burst_mode: bool = False  # If True, disable realistic timing delays

    # Synthetic data configuration (standardized across all producers)
    synthetic_mode: str = "fallback"  # none, fallback, synthetic, mixed
    synthetic_ratio: float = 0.2  # For mixed mode (0.0-1.0)

    # Date range configuration (standardized)
    years: str = "2023"  # Comma-separated years or single year
    months: str = ""  # Comma-separated months (1-12) or empty for all

    # Processing configuration
    chunk_size: int = 10000  # Records to process at once

    # Key generation configuration
    salt_keys: int = 0  # Number of salt buckets for partition distribution (0 = no salting)
    key_mode: str = "default"  # Key generation mode (default, how_zone, etc.)

    # Lateness injection for testing streaming behavior
    p_late: float = 0.0  # Probability of late arrival (0-1)
    late_min: int = 300  # Minimum lateness in seconds
    late_max: int = 1200  # Maximum lateness in seconds

    # Kafka delivery configuration
    compression_type: str = "snappy"
    acks: str = "all"
    enable_idempotence: bool = True  # Enable exactly-once semantics
    max_in_flight_requests: int = 5  # Max unacknowledged requests

    @classmethod
    def from_env(cls, name: str = None, env_mappings: dict = None, **kwargs):
        """
        Create config from environment variables with support for custom mappings.

        Args:
            name: Producer name for default topic
            env_mappings: Dict mapping field names to (env_var, converter, default) tuples
            **kwargs: Additional fixed values to pass to constructor

        Returns:
            Config instance
        """
        if name is None:
            # Extract name from class name (e.g., TripsConfig -> trips)
            name = cls.__name__.replace('Config', '').lower()

        # Default environment variable mappings
        default_mappings = {
            'topic': ('TOPIC', str, f"{name}.default"),
            'kafka_bootstrap': ('KAFKA_BOOTSTRAP', str, "localhost:29092"),
            'schema_registry_url': ('SCHEMA_REGISTRY_URL', str, "http://localhost:8082"),
            'rate': ('RATE', float, cls.rate if hasattr(cls, 'rate') else 1000.0),
            'batch_size': ('BATCH', int, cls.batch_size if hasattr(cls, 'batch_size') else 1000),
            'continuous_mode': ('CONTINUOUS', lambda x: x.lower() == "true", True),
            'burst_mode': ('BURST_MODE', lambda x: x.lower() == "true", False),
            'synthetic_mode': ('SYNTHETIC_MODE', str, "fallback"),
            'synthetic_ratio': ('SYNTHETIC_RATIO', float, 0.2),
            'years': ('YEARS', str, "2023"),
            'months': ('MONTHS', str, ""),
            'chunk_size': ('CHUNK_SIZE', int, 10000),
            'salt_keys': ('SALT_KEYS', int, 0),
            'key_mode': ('KEY_MODE', str, "default"),
            'p_late': ('P_LATE', float, 0.0),
            'late_min': ('LATE_MIN', int, 300),
            'late_max': ('LATE_MAX', int, 1200),
            'compression_type': ('COMPRESSION', str, cls.compression_type if hasattr(cls, 'compression_type') else "snappy"),
            'acks': ('ACKS', str, "all"),
            'enable_idempotence': ('IDEMPOTENCE', lambda x: x.lower() == "true", True),
            'max_in_flight_requests': ('MAX_INFLIGHT', int, 5)
        }

        # Merge with custom mappings if provided
        if env_mappings:
            default_mappings.update(env_mappings)

        # Build config dict from environment
        config = {}
        for field_name, mapping in default_mappings.items():
            if len(mapping) == 3:
                env_var, converter, default = mapping
                env_value = os.getenv(env_var)
                if env_value is not None:
                    config[field_name] = converter(env_value)
                else:
                    config[field_name] = default

        # Merge with any fixed kwargs
        config.update(kwargs)

        return cls(**config)