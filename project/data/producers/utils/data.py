#!/usr/bin/env python3
"""
Data processing and validation utilities.
"""

import random
from typing import Generator, List, Any


def validate_distance(distance: float, min_dist: float = 0.2, max_dist: float = 40.0) -> bool:
    """
    Validate trip distance is within reasonable bounds.

    Args:
        distance: Trip distance in miles
        min_dist: Minimum valid distance
        max_dist: Maximum valid distance

    Returns:
        True if valid, False otherwise
    """
    return min_dist <= distance <= max_dist


def validate_coordinates(lat: float, lon: float) -> bool:
    """
    Validate NYC area coordinates.

    Args:
        lat: Latitude
        lon: Longitude

    Returns:
        True if coordinates are in NYC area
    """
    # Rough bounding box for NYC
    return (40.4 <= lat <= 41.0) and (-74.3 <= lon <= -73.7)


def batch_generator(generator: Generator, batch_size: int) -> Generator:
    """
    Convert a generator into batches.

    Args:
        generator: Input generator
        batch_size: Size of each batch

    Yields:
        Lists of items up to batch_size
    """
    batch = []
    for item in generator:
        batch.append(item)
        if len(batch) >= batch_size:
            yield batch
            batch = []

    if batch:
        yield batch


def calculate_sleep_time(
    num_messages: int,
    elapsed_seconds: float,
    target_rate: float
) -> float:
    """
    Calculate sleep time needed to maintain target rate.

    Args:
        num_messages: Number of messages sent
        elapsed_seconds: Time elapsed
        target_rate: Target messages per second

    Returns:
        Sleep time in seconds (0 if no sleep needed)
    """
    if target_rate <= 0:
        return 0.0

    target_time = num_messages / target_rate
    sleep_time = target_time - elapsed_seconds

    return max(0.0, sleep_time)


def random_choice_weighted(choices: List[tuple], weights: List[float]):
    """
    Choose random item with weights.

    Args:
        choices: List of choices
        weights: List of weights (same length as choices)

    Returns:
        Randomly selected choice
    """
    return random.choices(choices, weights=weights, k=1)[0]


def generate_gaussian_value(mean: float, stddev: float, min_val: float = None, max_val: float = None) -> float:
    """
    Generate a value from gaussian distribution with optional bounds.

    Args:
        mean: Mean of distribution
        stddev: Standard deviation
        min_val: Optional minimum value
        max_val: Optional maximum value

    Returns:
        Random value
    """
    value = random.gauss(mean, stddev)

    if min_val is not None:
        value = max(value, min_val)
    if max_val is not None:
        value = min(value, max_val)

    return value