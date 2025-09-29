#!/usr/bin/env python3
"""
Time-based patterns for NYC scenario generation.

Defines rush hours, demand patterns, and temporal variations.
"""

# Rush hour patterns by day type
RUSH_HOUR_PATTERNS = {
    "weekday": {
        "morning_peak": {"start": 7, "end": 9, "multiplier": 2.5},
        "evening_peak": {"start": 17, "end": 19, "multiplier": 2.8},
        "lunch_bump": {"start": 12, "end": 13, "multiplier": 1.3},
        "late_night": {"start": 22, "end": 2, "multiplier": 0.8}
    },
    "weekend": {
        "morning_peak": {"start": 10, "end": 12, "multiplier": 1.4},
        "evening_peak": {"start": 18, "end": 22, "multiplier": 1.8},
        "lunch_bump": {"start": 13, "end": 15, "multiplier": 1.5},
        "late_night": {"start": 23, "end": 3, "multiplier": 1.2}
    }
}

# Day of week adjustment factors
DAY_OF_WEEK_FACTORS = {
    0: 0.9,   # Monday
    1: 1.0,   # Tuesday
    2: 1.0,   # Wednesday
    3: 1.0,   # Thursday
    4: 1.2,   # Friday
    5: 1.1,   # Saturday
    6: 0.8    # Sunday
}

# Seasonal adjustment factors
SEASONAL_FACTORS = {
    "winter": {  # Dec-Feb
        "months": [12, 1, 2],
        "trip_factor": 0.85,
        "event_factor": 0.9
    },
    "spring": {  # Mar-May
        "months": [3, 4, 5],
        "trip_factor": 1.0,
        "event_factor": 1.1
    },
    "summer": {  # Jun-Aug
        "months": [6, 7, 8],
        "trip_factor": 0.95,
        "event_factor": 1.2
    },
    "fall": {  # Sep-Nov
        "months": [9, 10, 11],
        "trip_factor": 1.05,
        "event_factor": 1.0
    }
}


def get_time_multiplier(hour: int, weekday: int) -> float:
    """
    Get trip demand multiplier based on time and day.

    Args:
        hour: Hour of day (0-23)
        weekday: Day of week (0=Monday, 6=Sunday)

    Returns:
        Multiplier value
    """
    is_weekend = weekday >= 5
    pattern = RUSH_HOUR_PATTERNS["weekend" if is_weekend else "weekday"]

    # Find applicable multiplier
    multiplier = 0.5  # Base overnight rate

    for period_name, period in pattern.items():
        start = period["start"]
        end = period["end"]

        # Handle periods that cross midnight
        if start <= end:
            if start <= hour < end:
                multiplier = period["multiplier"]
                break
        else:  # Crosses midnight
            if hour >= start or hour < end:
                multiplier = period["multiplier"]
                break

    # Apply day-of-week adjustment
    multiplier *= DAY_OF_WEEK_FACTORS.get(weekday, 1.0)

    return multiplier


def get_seasonal_factor(month: int, factor_type: str = "trip_factor") -> float:
    """
    Get seasonal adjustment factor.

    Args:
        month: Month (1-12)
        factor_type: Type of factor ("trip_factor" or "event_factor")

    Returns:
        Seasonal adjustment factor
    """
    for season, data in SEASONAL_FACTORS.items():
        if month in data["months"]:
            return data.get(factor_type, 1.0)
    return 1.0