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

    # Compression and delivery
    compression_type: str = "snappy"
    acks: str = "all"

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
            'compression_type': ('COMPRESSION', str, cls.compression_type if hasattr(cls, 'compression_type') else "snappy"),
            'acks': ('ACKS', str, "all")
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