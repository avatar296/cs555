#!/usr/bin/env python3
"""
Schema file mappings and configurations.
"""

# Schema file names
SCHEMAS = {
    "trips": "trip_event.avsc",
    "weather": "weather_event.avsc",
    "events": "event_calendar.avsc"
}

# DuckDB configuration
DUCKDB_SETUP = "INSTALL httpfs; LOAD httpfs;"