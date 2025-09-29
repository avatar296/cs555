#!/usr/bin/env python3
"""
Trip demand patterns for NYC scenario generation.

Provides trip demand generation and zone characteristics.
"""

from datetime import datetime
from typing import Dict

from .constants import ZONE_CATEGORIES, ZONE_BASE_RATES
from .patterns import get_time_multiplier, get_seasonal_factor
from .events import check_event_impact


def generate_trip_demand(timestamp: datetime, base_rate: float = 100) -> float:
    """
    Calculate trip demand multiplier for a given timestamp.

    Considers time of day, day of week, special events, and seasonal factors.

    Args:
        timestamp: Timestamp to calculate demand for
        base_rate: Base trip rate

    Returns:
        Adjusted trip demand
    """
    hour = timestamp.hour
    weekday = timestamp.weekday()
    month = timestamp.month

    # Get time-based multiplier
    multiplier = get_time_multiplier(hour, weekday)

    # Apply seasonal adjustment
    multiplier *= get_seasonal_factor(month, "trip_factor")

    # Check for special events
    multiplier *= check_event_impact(timestamp)

    return base_rate * multiplier


def get_zone_characteristics(zone_id: int) -> Dict:
    """
    Get characteristics of a zone for trip generation.

    Args:
        zone_id: NYC TLC zone ID

    Returns:
        Dictionary with zone characteristics
    """
    characteristics = {
        "is_airport": False,
        "is_business": False,
        "is_tourist": False,
        "is_nightlife": False,
        "base_trip_rate": ZONE_BASE_RATES["default"]
    }

    for category, zones in ZONE_CATEGORIES.items():
        if zone_id in zones:
            if category == "airports":
                characteristics["is_airport"] = True
                characteristics["base_trip_rate"] = ZONE_BASE_RATES["airports"]
            elif category == "manhattan_business":
                characteristics["is_business"] = True
                characteristics["base_trip_rate"] = ZONE_BASE_RATES["manhattan_business"]
            elif category == "tourist_areas":
                characteristics["is_tourist"] = True
                characteristics["base_trip_rate"] = ZONE_BASE_RATES["tourist_areas"]
            elif category == "nightlife":
                characteristics["is_nightlife"] = True
                characteristics["base_trip_rate"] = ZONE_BASE_RATES["nightlife"]

    return characteristics


def calculate_trip_distance(pickup_zone: int, dropoff_zone: int, base_distance: float = None) -> float:
    """
    Calculate expected trip distance between zones.

    Args:
        pickup_zone: Pickup zone ID
        dropoff_zone: Dropoff zone ID
        base_distance: Optional base distance to adjust

    Returns:
        Expected trip distance in miles
    """
    # Airport trips are typically longer
    pickup_chars = get_zone_characteristics(pickup_zone)
    dropoff_chars = get_zone_characteristics(dropoff_zone)

    if pickup_chars["is_airport"] or dropoff_chars["is_airport"]:
        return base_distance or 15.0

    # Inter-borough trips are longer
    if abs(pickup_zone - dropoff_zone) > 50:
        return base_distance or 8.0

    # Local trips
    return base_distance or 3.0


def get_zone_demand_factor(zone_id: int, hour: int) -> float:
    """
    Get demand factor for a specific zone at a specific hour.

    Args:
        zone_id: Zone ID
        hour: Hour of day (0-23)

    Returns:
        Zone-specific demand factor
    """
    chars = get_zone_characteristics(zone_id)

    # Business districts have morning/evening peaks
    if chars["is_business"]:
        if 7 <= hour <= 9:
            return 1.5
        elif 17 <= hour <= 19:
            return 1.8
        elif 10 <= hour <= 16:
            return 1.2
        else:
            return 0.6

    # Nightlife zones peak late
    if chars["is_nightlife"]:
        if 20 <= hour or hour <= 2:
            return 1.8
        elif 14 <= hour <= 19:
            return 1.2
        else:
            return 0.5

    # Tourist areas peak midday
    if chars["is_tourist"]:
        if 10 <= hour <= 18:
            return 1.4
        else:
            return 0.7

    # Airports have consistent demand
    if chars["is_airport"]:
        if 5 <= hour <= 22:
            return 1.2
        else:
            return 0.8

    return 1.0