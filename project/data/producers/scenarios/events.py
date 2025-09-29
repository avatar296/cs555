#!/usr/bin/env python3
"""
Event scenarios for NYC data generation.

Defines special events, recurring patterns, and event generation logic.
"""

import random
from datetime import datetime, timedelta
from typing import Dict, Tuple

# Major NYC events with their impact
SPECIAL_EVENTS = [
    {
        "date": "2023-01-01",
        "name": "New Year's Day",
        "zones_affected": [161, 230, 234],  # Times Square, Manhattan zones
        "trip_multiplier": 2.5,
        "duration_hours": 6,
        "weather_sensitive": False
    },
    {
        "date": "2023-07-04",
        "name": "Independence Day",
        "zones_affected": [13, 87, 88, 231],  # Battery Park, waterfront zones
        "trip_multiplier": 1.8,
        "duration_hours": 8,
        "weather_sensitive": True
    },
    {
        "date": "2023-11-05",
        "name": "NYC Marathon",
        "zones_affected": list(range(100, 165)),  # Route through multiple boroughs
        "trip_multiplier": 1.6,
        "duration_hours": 10,
        "weather_sensitive": True
    },
    {
        "date": "2023-11-23",
        "name": "Thanksgiving Day Parade",
        "zones_affected": [161, 162, 163, 164, 230],  # Midtown Manhattan
        "trip_multiplier": 1.4,
        "duration_hours": 5,
        "weather_sensitive": True
    },
    {
        "date": "2023-12-31",
        "name": "New Year's Eve",
        "zones_affected": [161, 230, 234, 90, 100],  # Times Square area
        "trip_multiplier": 3.0,
        "duration_hours": 8,
        "weather_sensitive": False
    }
]

# Recurring event patterns
RECURRING_PATTERNS = {
    "yankees_game": {
        "venue": "Yankee Stadium",
        "lat": 40.8296,
        "lon": -73.9262,
        "zones": [136],  # Yankee Stadium zone
        "season": {"start_month": 4, "end_month": 10},
        "days": [1, 2, 3, 5, 6, 0],  # Mon, Tue, Wed, Fri, Sat, Sun
        "time": 19,  # 7 PM
        "attendance": 35000,
        "trip_surge": 1.3
    },
    "madison_square_garden": {
        "venue": "Madison Square Garden",
        "lat": 40.7505,
        "lon": -73.9934,
        "zones": [161, 186, 234],  # Penn Station area
        "season": {"start_month": 1, "end_month": 12},
        "days": [1, 3, 5, 6],  # Tue, Thu, Sat, Sun
        "time": 19,  # 7 PM
        "attendance": 18000,
        "trip_surge": 1.4
    },
    "broadway_shows": {
        "venue": "Theater District",
        "lat": 40.7590,
        "lon": -73.9845,
        "zones": [161, 163, 164, 230],
        "season": {"start_month": 1, "end_month": 12},
        "days": [1, 2, 3, 4, 5, 6],  # Tue-Sun
        "time": 20,  # 8 PM
        "attendance": 15000,  # Total across all theaters
        "trip_surge": 1.2
    }
}


def should_generate_event(date: datetime, venue: str) -> Tuple[bool, Dict]:
    """
    Determine if an event should occur at a venue on a given date.

    Args:
        date: Date to check
        venue: Venue name

    Returns:
        Tuple of (should_generate, event_pattern)
    """
    weekday = date.weekday()
    month = date.month

    for pattern_name, pattern in RECURRING_PATTERNS.items():
        if pattern["venue"] != venue:
            continue

        # Check if in season
        if pattern["season"]["start_month"] <= pattern["season"]["end_month"]:
            if not (pattern["season"]["start_month"] <= month <= pattern["season"]["end_month"]):
                continue
        else:  # Season crosses year boundary
            if not (month >= pattern["season"]["start_month"] or month <= pattern["season"]["end_month"]):
                continue

        # Check day of week
        if weekday in pattern["days"]:
            # Add some randomness (80% chance of event occurring)
            if random.random() < 0.8:
                return True, pattern

    return False, {}


def get_special_event_for_date(date: datetime) -> Dict:
    """
    Check if there's a special event on the given date.

    Args:
        date: Date to check

    Returns:
        Special event dictionary or empty dict
    """
    date_str = date.strftime("%Y-%m-%d")

    for event in SPECIAL_EVENTS:
        if event["date"] == date_str:
            return event

    return {}


def check_event_impact(timestamp: datetime) -> float:
    """
    Check if timestamp is affected by special events and return multiplier.

    Args:
        timestamp: Timestamp to check

    Returns:
        Trip multiplier based on events
    """
    date_str = timestamp.strftime("%Y-%m-%d")

    for event in SPECIAL_EVENTS:
        if event["date"] == date_str:
            event_start = datetime.strptime(event["date"], "%Y-%m-%d").replace(hour=8)
            event_end = event_start + timedelta(hours=event["duration_hours"])
            if event_start <= timestamp <= event_end:
                return event["trip_multiplier"]

    return 1.0