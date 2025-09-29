#!/usr/bin/env python3
"""
Constants for NYC scenario generation.

Defines zones, venues, and other shared constants used across scenarios.
"""

# NYC Venue definitions
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

# Zone category definitions
AIRPORTS = [132, 138, 1]  # JFK, LGA, EWR
MANHATTAN_BUSINESS = list(range(140, 165))  # Midtown/Downtown
MANHATTAN_RESIDENTIAL = list(range(165, 200))  # Upper East/West
BROOKLYN_HIP = [33, 61, 76, 91]  # Williamsburg, DUMBO, Park Slope
QUEENS_RESIDENTIAL = list(range(190, 220))
TOURIST_AREAS = [161, 163, 164, 230, 234]  # Times Square, Central Park
NIGHTLIFE_ZONES = [79, 148, 158, 230, 234]  # East Village, Chelsea, etc.

# Zone categories dictionary for easy access
ZONE_CATEGORIES = {
    "airports": AIRPORTS,
    "manhattan_business": MANHATTAN_BUSINESS,
    "manhattan_residential": MANHATTAN_RESIDENTIAL,
    "brooklyn_hip": BROOKLYN_HIP,
    "queens_residential": QUEENS_RESIDENTIAL,
    "tourist_areas": TOURIST_AREAS,
    "nightlife": NIGHTLIFE_ZONES
}

# Zone characteristics base rates
ZONE_BASE_RATES = {
    "airports": 150,
    "manhattan_business": 200,
    "tourist_areas": 180,
    "nightlife": 120,
    "default": 100
}