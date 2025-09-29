#!/usr/bin/env python3
"""
Event data sources for Kafka producers.

Provides NYC Open Data and synthetic event data sources.
"""

import sys
import random
from datetime import datetime, timedelta
from typing import Generator, Dict

from .base import (
    DataSource,
    ConfigurableDataSource,
    DatabaseSourceMixin,
    SyntheticSourceMixin,
    ContinuousDataMixin
)

from ..utils import generate_date_list
from ..common import (
    NYC_EVENTS_URL,
    NYC_VENUES,
    DEFAULT_ZONE_LAT,
    DEFAULT_ZONE_LON
)


class EventDataSource(ConfigurableDataSource, DataSource):
    """Base class for event data sources."""
    pass


class NYCOpenDataEventSource(EventDataSource, DatabaseSourceMixin, ContinuousDataMixin):
    """Real NYC Open Data event source."""

    def is_available(self) -> bool:
        """Check if NYC Open Data is accessible."""
        query = f"""
        SELECT COUNT(*) FROM read_json_auto(
            '{NYC_EVENTS_URL}?$limit=1',
            maximum_object_size=100000000
        )
        """

        result = self.execute_duckdb_query(
            query,
            fetch_one=True,
            error_message="NYC Open Data availability check failed"
        )
        return result is not None

    def fetch(self) -> Generator:
        """Fetch NYC event data."""
        # Use first year from years list
        years = self.config.years
        year = years[0] if isinstance(years, list) else years
        year = str(year).strip()

        query = f"""
        SELECT * FROM read_json_auto(
            '{NYC_EVENTS_URL}?$limit=1000&$where=date_extract_y(start_date_time)={year}',
            maximum_object_size=100000000
        )
        """

        result = self.execute_duckdb_query(
            query,
            fetch_all=True,
            error_message="Could not fetch NYC events"
        )

        if not result:
            print(f"No NYC Open Data events found for year {year}",
                  file=sys.stderr)
            print("NYC Open Data only has events for 2024-2026",
                  file=sys.stderr)
            return

        for row in result:
            if isinstance(row, dict):
                yield self._parse_event(row)

    def _parse_event(self, row: Dict) -> Dict:
        """Parse NYC Open Data event."""
        lat, lon = self._get_event_location(row)

        return {
            'event_id': str(row.get('event_id', hash(str(row)))),
            'event_type': row.get('event_type', 'special_event'),
            'venue_name': row.get('event_location', 'Unknown Venue'),
            'venue_lat': lat,
            'venue_lon': lon,
            'expected_attendance': int(row.get('estimated_attendance', 1000)),
            'start_time': row.get(
                'start_date_time',
                datetime.now().isoformat()
            ),
            'end_time': row.get(
                'end_date_time',
                (datetime.now() + timedelta(hours=3)).isoformat()
            )
        }

    def _get_event_location(self, row: Dict) -> tuple:
        """Get event location with fallback to random venue."""
        lat = float(row.get('latitude', 0)) or DEFAULT_ZONE_LAT
        lon = float(row.get('longitude', 0)) or DEFAULT_ZONE_LON

        # Use random venue if no coordinates
        if lat == 0 or lon == 0:
            venue = random.choice(NYC_VENUES)
            lat = venue['lat']
            lon = venue['lon']

        return lat, lon


class SyntheticEventSource(EventDataSource, SyntheticSourceMixin, ContinuousDataMixin):
    """Synthetic event data generator."""

    def __init__(self, config):
        """Initialize synthetic event source."""
        super().__init__(config)
        self.event_counter = 0

    def is_available(self) -> bool:
        """Synthetic data is always available."""
        return True

    def fetch(self) -> Generator:
        """Generate synthetic event data."""
        import time
        dates = generate_date_list(
            self.config.years,
            self.get_config_value('months', '')
        )

        while True:
            for date in dates:
                # Collect all events for the day
                daily_events = []

                # Generate special events
                for event in self._generate_special_events(date):
                    daily_events.append(event)

                # Generate regular venue events
                for event in self._generate_venue_events(date):
                    daily_events.append(event)

                # Sort events by start time for realistic ordering
                daily_events.sort(key=lambda x: x.get('start_time', ''))

                # Yield events with realistic timing
                for i, event in enumerate(daily_events):
                    yield event

                    # Add delays to simulate event announcements throughout the day
                    if not self.config.burst_mode and i < len(daily_events) - 1:
                        # Events are typically announced throughout the day
                        # Use 100-200ms between event announcements
                        time.sleep(0.15)

                # Add delay between days
                if not self.config.burst_mode and date != dates[-1]:
                    time.sleep(0.5)  # 500ms between days

            # Check if should continue
            if not self.should_continue(self.config):
                break

            # Advance dates for next cycle
            dates = [date + timedelta(days=365) for date in dates]

    def _generate_special_events(self, date) -> Generator:
        """Generate special events for specific dates."""
        for special_event in self.scenarios.SPECIAL_EVENTS:
            if self._is_special_event_date(date, special_event):
                yield self._create_special_event(date, special_event)
                self.event_counter += 1

    def _is_special_event_date(self, date, special_event) -> bool:
        """Check if date matches special event."""
        # Use the year from the date we're checking
        special_date = special_event["date"].replace(
            "2023",
            str(date.year)
        )
        return date.strftime("%Y-%m-%d") == special_date

    def _create_special_event(self, date, special_event) -> Dict:
        """Create a special event record."""
        venue = NYC_VENUES[0]  # Use MSG as default

        return {
            'event_id': f"special_{self.event_counter}",
            'event_type': 'special_event',
            'venue_name': special_event['name'],
            'venue_lat': venue['lat'],
            'venue_lon': venue['lon'],
            'expected_attendance': int(
                venue['capacity'] * special_event['trip_multiplier']
            ),
            'start_time': date.replace(hour=10).isoformat(),
            'end_time': (
                date.replace(hour=10) +
                timedelta(hours=special_event['duration_hours'])
            ).isoformat()
        }

    def _generate_venue_events(self, date) -> Generator:
        """Generate regular events at venues."""
        for venue in NYC_VENUES:
            should_have, pattern = self.scenarios.should_generate_event(
                date,
                venue["name"]
            )

            if should_have:
                yield self._create_venue_event(date, venue, pattern)
                self.event_counter += 1

    def _create_venue_event(self, date, venue, pattern) -> Dict:
        """Create a regular venue event."""
        start_hour = pattern.get("time", 19)
        duration = random.randint(2, 4)
        event_type = "sports" if "Stadium" in venue["name"] else "concert"

        return {
            'event_id': f"regular_{self.event_counter}",
            'event_type': event_type,
            'venue_name': venue['name'],
            'venue_lat': venue['lat'],
            'venue_lon': venue['lon'],
            'expected_attendance': int(
                venue['capacity'] * random.uniform(0.5, 0.9)
            ),
            'start_time': date.replace(hour=start_hour).isoformat(),
            'end_time': (
                date.replace(hour=start_hour) +
                timedelta(hours=duration)
            ).isoformat()
        }