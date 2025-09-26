#!/usr/bin/env python3
"""
NYC location data including venues, weather stations, and taxi zones.
"""

# NYC Weather Stations
NYC_WEATHER_STATIONS = {
    "72505394728": "Central Park",
    "72503014732": "LaGuardia",
    "74486094789": "JFK"
}

# Major NYC Venues
NYC_VENUES = [
    {"name": "Madison Square Garden", "lat": 40.7505, "lon": -73.9934, "capacity": 20000},
    {"name": "Barclays Center", "lat": 40.6826, "lon": -73.9754, "capacity": 19000},
    {"name": "Yankee Stadium", "lat": 40.8296, "lon": -73.9262, "capacity": 54000},
    {"name": "Citi Field", "lat": 40.7571, "lon": -73.8458, "capacity": 41000},
    {"name": "MetLife Stadium", "lat": 40.8128, "lon": -74.0742, "capacity": 82500},
    {"name": "Lincoln Center", "lat": 40.7725, "lon": -73.9835, "capacity": 5000},
    {"name": "Radio City Music Hall", "lat": 40.7597, "lon": -73.9799, "capacity": 6000},
    {"name": "Apollo Theater", "lat": 40.8098, "lon": -73.9500, "capacity": 1500},
    {"name": "Brooklyn Academy of Music", "lat": 40.6863, "lon": -73.9788, "capacity": 2100},
    {"name": "Central Park SummerStage", "lat": 40.7812, "lon": -73.9665, "capacity": 5000},
    {"name": "Prospect Park Bandshell", "lat": 40.6627, "lon": -73.9712, "capacity": 7500},
    {"name": "Queens Theater", "lat": 40.7436, "lon": -73.8448, "capacity": 500},
]

# Popular taxi zone pairs for synthetic data
POPULAR_ZONE_PAIRS = [
    (161, 132, 18.5),  # Midtown → JFK
    (132, 161, 18.5),  # JFK → Midtown
    (161, 138, 9.2),   # Midtown → LGA
    (138, 161, 9.2),   # LGA → Midtown
    (161, 234, 2.1),   # Manhattan internal
    (234, 79, 3.5),    # Manhattan internal
    (33, 161, 5.8),    # Brooklyn → Manhattan
    (161, 33, 5.8),    # Manhattan → Brooklyn
]

# NYC Zone Information
TOTAL_NYC_ZONES = 263
DEFAULT_ZONE_LAT = 40.7580  # Times Square as default
DEFAULT_ZONE_LON = -73.9855