#!/usr/bin/env python3
"""
Weather scenarios for NYC data generation.

Provides weather patterns, impacts, and generation functions.
"""

import random
import math
from datetime import datetime
from typing import Dict

# Weather impact on trip patterns
WEATHER_IMPACTS = {
    "clear": {"trip_count": 1.0, "trip_distance": 1.0, "trip_duration": 1.0},
    "cloudy": {"trip_count": 0.98, "trip_distance": 1.0, "trip_duration": 1.0},
    "rain": {"trip_count": 1.1, "trip_distance": 0.85, "trip_duration": 1.2},
    "heavy_rain": {"trip_count": 0.9, "trip_distance": 0.7, "trip_duration": 1.4},
    "snow": {"trip_count": 0.6, "trip_distance": 0.7, "trip_duration": 1.8},
    "heavy_snow": {"trip_count": 0.3, "trip_distance": 0.5, "trip_duration": 2.5},
    "fog": {"trip_count": 0.85, "trip_distance": 0.9, "trip_duration": 1.3},
    "extreme_heat": {"trip_count": 1.15, "trip_distance": 0.8, "trip_duration": 1.1},
    "extreme_cold": {"trip_count": 0.9, "trip_distance": 0.85, "trip_duration": 1.1}
}


def get_seasonal_weather_params(date: datetime) -> Dict:
    """
    Get seasonal weather parameters for a given date.

    Args:
        date: Date to get parameters for

    Returns:
        Dictionary with seasonal weather parameters
    """
    month = date.month

    # Winter (Dec-Feb)
    if month in [12, 1, 2]:
        return {
            "temp_range": (25, 45),
            "temp_mean": 35,
            "precip_chance": 0.35,
            "snow_chance": 0.25,
            "storm_chance": 0.08
        }
    # Spring (Mar-May)
    elif month in [3, 4, 5]:
        return {
            "temp_range": (40, 70),
            "temp_mean": 55,
            "precip_chance": 0.30,
            "snow_chance": 0.02,
            "storm_chance": 0.10
        }
    # Summer (Jun-Aug)
    elif month in [6, 7, 8]:
        return {
            "temp_range": (65, 90),
            "temp_mean": 78,
            "precip_chance": 0.25,
            "snow_chance": 0.0,
            "storm_chance": 0.15
        }
    # Fall (Sep-Nov)
    else:
        return {
            "temp_range": (45, 75),
            "temp_mean": 60,
            "precip_chance": 0.28,
            "snow_chance": 0.05,
            "storm_chance": 0.12
        }


def generate_weather_conditions(date: datetime, station_id: str = None) -> Dict:
    """
    Generate realistic weather conditions for a given date and station.

    Args:
        date: Date/time to generate weather for
        station_id: Optional weather station ID

    Returns:
        Dictionary with weather conditions
    """
    params = get_seasonal_weather_params(date)

    # Generate base temperature with daily cycle
    hour = date.hour
    daily_temp_variation = 10 * math.sin((hour - 6) * math.pi / 12) if 6 <= hour <= 18 else -5
    base_temp = params["temp_mean"] + daily_temp_variation
    temperature = max(params["temp_range"][0],
                     min(params["temp_range"][1],
                         base_temp + random.gauss(0, 5)))

    # Determine precipitation and conditions
    conditions = "clear"
    precipitation = 0.0

    if random.random() < params["precip_chance"]:
        if temperature < 32 and random.random() < params["snow_chance"]:
            conditions = "snow"
            precipitation = random.uniform(0.1, 2.0)
            if random.random() < params["storm_chance"]:
                conditions = "heavy_snow"
                precipitation = random.uniform(2.0, 8.0)
        else:
            conditions = "rain"
            precipitation = random.uniform(0.05, 0.5)
            if random.random() < params["storm_chance"]:
                conditions = "heavy_rain"
                precipitation = random.uniform(0.5, 2.0)
    elif random.random() < 0.3:
        conditions = "cloudy"

    # Add fog conditions in morning during spring/fall
    if hour in [5, 6, 7] and date.month in [3, 4, 10, 11] and random.random() < 0.1:
        conditions = "fog"

    # Extreme temperatures
    if temperature > 90:
        conditions = "extreme_heat"
    elif temperature < 20:
        conditions = "extreme_cold"

    # Wind speed correlates with storms
    if "heavy" in conditions:
        wind_speed = random.uniform(15, 35)
    elif conditions in ["rain", "snow"]:
        wind_speed = random.uniform(8, 20)
    else:
        wind_speed = random.uniform(3, 12)

    # Visibility
    visibility = 10.0
    if conditions == "fog":
        visibility = random.uniform(0.5, 2.0)
    elif "heavy" in conditions:
        visibility = random.uniform(2.0, 5.0)
    elif conditions in ["rain", "snow"]:
        visibility = random.uniform(5.0, 8.0)

    return {
        "temperature": temperature,
        "conditions": conditions,
        "precipitation": precipitation,
        "wind_speed": wind_speed,
        "visibility": visibility,
        "humidity": random.uniform(40, 90) if conditions != "clear" else random.uniform(30, 60)
    }


def get_weather_impact(conditions: str) -> Dict:
    """
    Get the impact factors for given weather conditions.

    Args:
        conditions: Weather condition string

    Returns:
        Dictionary with impact factors
    """
    return WEATHER_IMPACTS.get(conditions, {
        "trip_count": 1.0,
        "trip_distance": 1.0,
        "trip_duration": 1.0
    })