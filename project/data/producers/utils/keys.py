#!/usr/bin/env python3
"""
Key generation utilities for Kafka producers.
"""

import random
from datetime import datetime


def generate_salted_key(base_key: str, salt_buckets: int) -> str:
    """
    Generate a salted key for better partition distribution.

    Args:
        base_key: Base key value
        salt_buckets: Number of salt buckets (0 for no salting)

    Returns:
        Salted key string
    """
    if salt_buckets <= 0:
        return base_key

    bucket = random.randint(0, salt_buckets - 1)
    return f"{base_key}:{bucket}"


def generate_hour_of_week_key(timestamp: datetime, zone_id: int) -> str:
    """
    Generate a key based on hour of week and zone.

    Args:
        timestamp: Event timestamp
        zone_id: Zone identifier

    Returns:
        Key string in format "HOW:ZONE"
    """
    hour_of_week = (timestamp.weekday() * 24) + timestamp.hour
    return f"{hour_of_week}:{zone_id}"