#!/usr/bin/env python3
"""
Synthetic Data Scenarios for NYC Data Simulation

Provides shared patterns, events, and correlations for generating
realistic synthetic data across trips, weather, and events producers.
"""

from datetime import datetime, timedelta
from typing import Dict, List, Tuple
import random
import math


class NYCScenarios:
    """Defines NYC-specific patterns and scenarios for synthetic data."""

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

    # Zone categories for different trip patterns
    ZONE_CATEGORIES = {
        "airports": [132, 138, 1],  # JFK, LGA, EWR
        "manhattan_business": list(range(140, 165)),  # Midtown/Downtown
        "manhattan_residential": list(range(165, 200)),  # Upper East/West
        "brooklyn_hip": [33, 61, 76, 91],  # Williamsburg, DUMBO, Park Slope
        "queens_residential": list(range(190, 220)),
        "tourist_areas": [161, 163, 164, 230, 234],  # Times Square, Central Park
        "nightlife": [79, 148, 158, 230, 234]  # East Village, Chelsea, etc.
    }

    @staticmethod
    def get_seasonal_weather_params(date: datetime) -> Dict:
        """Get seasonal weather parameters for a given date."""
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

    @staticmethod
    def generate_trip_demand(timestamp: datetime, base_rate: float = 100) -> float:
        """
        Calculate trip demand multiplier for a given timestamp.
        Considers time of day, day of week, and special events.
        """
        hour = timestamp.hour
        weekday = timestamp.weekday()
        is_weekend = weekday >= 5

        # Get base pattern
        pattern = NYCScenarios.RUSH_HOUR_PATTERNS["weekend" if is_weekend else "weekday"]

        # Find applicable multiplier
        multiplier = 0.5  # Base overnight rate
        for period_name, period in pattern.items():
            # Handle periods that cross midnight
            start = period["start"]
            end = period["end"]

            if start <= end:
                if start <= hour < end:
                    multiplier = period["multiplier"]
                    break
            else:  # Crosses midnight
                if hour >= start or hour < end:
                    multiplier = period["multiplier"]
                    break

        # Apply day-of-week adjustments
        if weekday == 4:  # Friday
            multiplier *= 1.2
        elif weekday == 0:  # Monday
            multiplier *= 0.9
        elif weekday == 6:  # Sunday
            multiplier *= 0.8

        # Check for special events
        date_str = timestamp.strftime("%Y-%m-%d")
        for event in NYCScenarios.SPECIAL_EVENTS:
            if event["date"] == date_str:
                event_start = datetime.strptime(event["date"], "%Y-%m-%d").replace(hour=8)
                event_end = event_start + timedelta(hours=event["duration_hours"])
                if event_start <= timestamp <= event_end:
                    multiplier *= event["trip_multiplier"]
                    break

        return base_rate * multiplier

    @staticmethod
    def generate_weather_conditions(date: datetime, station_id: str = None) -> Dict:
        """Generate realistic weather conditions for a given date and station."""
        params = NYCScenarios.get_seasonal_weather_params(date)

        # Generate base temperature with daily cycle
        hour = date.hour
        daily_temp_variation = 10 * math.sin((hour - 6) * math.pi / 12) if 6 <= hour <= 18 else -5
        base_temp = params["temp_mean"] + daily_temp_variation
        temperature = max(params["temp_range"][0],
                         min(params["temp_range"][1],
                             base_temp + random.gauss(0, 5)))

        # Determine precipitation
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

    @staticmethod
    def get_zone_characteristics(zone_id: int) -> Dict:
        """Get characteristics of a zone for trip generation."""
        characteristics = {
            "is_airport": False,
            "is_business": False,
            "is_tourist": False,
            "is_nightlife": False,
            "base_trip_rate": 100
        }

        for category, zones in NYCScenarios.ZONE_CATEGORIES.items():
            if zone_id in zones:
                if category == "airports":
                    characteristics["is_airport"] = True
                    characteristics["base_trip_rate"] = 150
                elif category == "manhattan_business":
                    characteristics["is_business"] = True
                    characteristics["base_trip_rate"] = 200
                elif category == "tourist_areas":
                    characteristics["is_tourist"] = True
                    characteristics["base_trip_rate"] = 180
                elif category == "nightlife":
                    characteristics["is_nightlife"] = True
                    characteristics["base_trip_rate"] = 120

        return characteristics

    @staticmethod
    def should_generate_event(date: datetime, venue: str) -> Tuple[bool, Dict]:
        """Determine if an event should occur at a venue on a given date."""
        weekday = date.weekday()
        month = date.month

        for pattern_name, pattern in NYCScenarios.RECURRING_PATTERNS.items():
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


# Singleton instance for shared random seed
_scenarios = NYCScenarios()

def get_scenarios() -> NYCScenarios:
    """Get the shared scenarios instance."""
    return _scenarios