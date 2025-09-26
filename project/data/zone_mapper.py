#!/usr/bin/env python3
"""
Zone mapper utility for mapping coordinates to NYC TLC zones.
Downloads and caches zone data for spatial operations.
"""

import os
import json
import math
import duckdb
from typing import List, Tuple, Optional
from pathlib import Path


class ZoneMapper:
    def __init__(self):
        self.data_dir = Path(__file__).parent / "zones"
        self.data_dir.mkdir(exist_ok=True)

        self.zone_lookup_file = self.data_dir / "taxi_zone_lookup.csv"
        self.zone_geojson_file = self.data_dir / "taxi_zones.geojson"

        # Zone centroids for weather mapping (simplified - using representative points)
        # Manhattan zones generally use Central Park station
        # Queens zones use LaGuardia station
        # Brooklyn/SI zones use JFK station
        self.zone_to_station = self._init_zone_stations()

        # Download zone data if not present
        if not self.zone_lookup_file.exists():
            self._download_zone_data()

    def _init_zone_stations(self) -> dict:
        """Map zones to nearest weather station based on borough."""
        # Simplified mapping - in production would use actual distance calculations
        manhattan_zones = list(range(1, 100))  # Approximate Manhattan zones
        queens_zones = list(range(100, 200))   # Approximate Queens zones
        brooklyn_zones = list(range(200, 264)) # Approximate Brooklyn/SI zones

        station_map = {}
        for zone in range(1, 264):
            if zone in manhattan_zones:
                station_map[zone] = "72505394728"  # Central Park
            elif zone in queens_zones:
                station_map[zone] = "72503014732"  # LaGuardia
            else:
                station_map[zone] = "74486094789"  # JFK

        return station_map

    def _download_zone_data(self):
        """Download zone lookup table and boundaries."""
        con = duckdb.connect()

        print("Downloading NYC TLC zone data...")

        # Download zone lookup table
        lookup_query = """
        COPY (
            SELECT * FROM read_csv_auto(
                'https://d37ci6vzurychx.cloudfront.net/misc/taxi_zone_lookup.csv'
            )
        ) TO '{}'
        """.format(str(self.zone_lookup_file))

        try:
            con.execute(lookup_query)
            print(f"✓ Downloaded zone lookup to {self.zone_lookup_file}")
        except Exception as e:
            print(f"Warning: Could not download zone lookup: {e}")
            # Create a minimal lookup file
            self._create_minimal_lookup()

        con.close()

    def _create_minimal_lookup(self):
        """Create a minimal zone lookup for testing."""
        with open(self.zone_lookup_file, 'w') as f:
            f.write("LocationID,Borough,Zone,service_zone\n")
            # Add some sample zones
            f.write("1,EWR,Newark Airport,EWR\n")
            f.write("4,Manhattan,Alphabet City,Yellow Zone\n")
            f.write("132,Queens,JFK Airport,Airports\n")
            f.write("161,Manhattan,Midtown Center,Yellow Zone\n")
            f.write("138,Queens,LaGuardia Airport,Airports\n")

    def get_zone_for_point(self, lat: float, lon: float) -> int:
        """
        Find which zone a lat/lon point belongs to.
        Simplified implementation - returns approximate zone based on coordinates.
        """
        # For demo purposes, use a simple grid mapping
        # In production, would use actual polygon boundaries

        # NYC rough boundaries
        if not (40.4 < lat < 41.0 and -74.3 < lon < -73.7):
            return 1  # Default to zone 1 if outside NYC

        # Simple grid division (not accurate but functional for demo)
        lat_bins = 10
        lon_bins = 26

        lat_idx = int((lat - 40.4) * lat_bins / 0.6)
        lon_idx = int((lon + 74.3) * lon_bins / 0.6)

        zone_id = (lat_idx * lon_bins + lon_idx) % 263 + 1
        return zone_id

    def get_zones_in_radius(self, lat: float, lon: float, radius_miles: float) -> List[int]:
        """
        Find all zones within radius of a point.
        Returns list of zone IDs that fall within the specified radius.
        """
        affected_zones = []

        # Convert radius to approximate degrees (1 degree ≈ 69 miles)
        radius_deg = radius_miles / 69.0

        # Check a grid of points around the center
        # This is simplified - production would use actual zone polygons
        for zone_id in range(1, 264):
            # Generate a pseudo-center for each zone (for demo)
            zone_lat = 40.4 + (zone_id % 20) * 0.03
            zone_lon = -74.3 + (zone_id // 20) * 0.023

            # Calculate distance
            dist = math.sqrt((lat - zone_lat)**2 + (lon - zone_lon)**2)

            if dist <= radius_deg:
                affected_zones.append(zone_id)

        # Return at least the nearest zone
        if not affected_zones:
            affected_zones.append(self.get_zone_for_point(lat, lon))

        return affected_zones[:10]  # Limit to 10 zones max

    def get_zone_centroid(self, zone_id: int) -> Tuple[float, float]:
        """
        Get the centroid (center point) of a zone.
        Returns (lat, lon) tuple.
        """
        # Simplified calculation for demo
        lat = 40.4 + (zone_id % 20) * 0.03
        lon = -74.3 + (zone_id // 20) * 0.023
        return (lat, lon)

    def get_weather_station_for_zone(self, zone_id: int) -> str:
        """Get the nearest weather station ID for a given zone."""
        return self.zone_to_station.get(zone_id, "72505394728")  # Default to Central Park

    def get_all_zones(self) -> List[int]:
        """Return list of all zone IDs."""
        return list(range(1, 264))


# Module-level convenience functions
_mapper = None

def get_mapper() -> ZoneMapper:
    """Get or create singleton mapper instance."""
    global _mapper
    if _mapper is None:
        _mapper = ZoneMapper()
    return _mapper

def map_point_to_zone(lat: float, lon: float) -> int:
    """Convenience function to map a point to a zone."""
    return get_mapper().get_zone_for_point(lat, lon)

def find_affected_zones(lat: float, lon: float, radius: float = 0.3) -> List[int]:
    """Convenience function to find zones within radius (default 0.3 miles)."""
    return get_mapper().get_zones_in_radius(lat, lon, radius)