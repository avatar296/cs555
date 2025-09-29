#!/usr/bin/env python3
"""
Configuration for events producer.
"""

from dataclasses import dataclass
from .base import BaseConfig


@dataclass
class EventsConfig(BaseConfig):
    """Configuration for events producer."""

    # Override defaults for events (slower rate)
    rate: float = 10.0
    batch_size: int = 50

    # Note: years, months, synthetic_mode, synthetic_ratio inherited from BaseConfig

    @classmethod
    def from_env(cls):
        """Create events config from environment variables."""
        # Define events-specific environment mappings with different defaults
        events_mappings = {
            'rate': ('RATE', float, 10.0),  # Events have slower default rate
            'batch_size': ('BATCH', int, 50),  # Smaller batch for events
        }

        return super().from_env(name="events", env_mappings=events_mappings)