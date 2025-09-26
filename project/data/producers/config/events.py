#!/usr/bin/env python3
"""
Configuration for events producer.
"""

from dataclasses import dataclass
from .base import BaseConfig


@dataclass
class EventsConfig(BaseConfig):
    """Configuration for events producer."""

    # Data configuration
    year: str = "2023"

    # Override defaults for events (slower rate)
    rate: float = 10.0
    batch_size: int = 50

    @classmethod
    def from_env(cls):
        """Create events config from environment variables."""
        # Define events-specific environment mappings with different defaults
        events_mappings = {
            'rate': ('RATE', float, 10.0),  # Events have slower default rate
            'batch_size': ('BATCH', int, 50),  # Smaller batch for events
            'year': ('YEAR', str, cls.year)
        }

        return super().from_env(name="events", env_mappings=events_mappings)