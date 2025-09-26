#!/usr/bin/env python3
"""
Events Producer (Clean Architecture) - NYC Open Data → Kafka

Pulls NYC event data and maps to affected taxi zones.
This version uses clean architecture with configuration injection.
"""

import sys
import random
from pathlib import Path
from typing import Generator, Tuple, Any, Dict, Optional
from datetime import datetime, timedelta

# Add parent directory to path for zone_mapper import
sys.path.append(str(Path(__file__).parent.parent.parent))

from .base import BaseProducer
from zone_mapper import find_affected_zones  # This is in data directory

from ..config.events import EventsConfig
from .. import common
from ..common import SCHEMAS, EVENT_IMPACT_RADIUS
from ..data_sources.factories import get_event_data_source


class EventsProducer(BaseProducer):
    """Clean events producer with configuration injection."""

    def __init__(self, config: EventsConfig = None):
        """
        Initialize events producer.

        Args:
            config: EventsConfig instance (defaults to environment-based config)
        """
        self.config = config or EventsConfig.from_env()

        super().__init__(
            name="events",
            schema_file=SCHEMAS["events"],
            topic=self.config.topic,
            bootstrap_servers=self.config.kafka_bootstrap,
            schema_registry_url=self.config.schema_registry_url,
            rate=self.config.rate,
            batch_size=self.config.batch_size
        )

        # Get appropriate data source
        self.data_source = get_event_data_source(self.config)

        # Track if we're generating synthetic events
        self.is_generating_synthetic = False
        self.synthetic_event_counter = 0

    def get_producer_config(self):
        """Get events-specific Kafka producer configuration."""
        return {
            "compression.type": self.config.compression_type,
            "batch.num.messages": 1000
        }

    def add_affected_zones(self, event: dict) -> dict:
        """
        Add affected zones to an event based on venue location.

        Args:
            event: Event dict with venue_lat and venue_lon

        Returns:
            Event dict with affected_zones added
        """
        zones = find_affected_zones(
            event['venue_lat'],
            event['venue_lon'],
            radius=EVENT_IMPACT_RADIUS
        )
        return {**event, 'affected_zones': zones}

    def generate_synthetic_event(self, event_id: int, timestamp: datetime) -> dict:
        """
        Generate a single synthetic event.

        Args:
            event_id: Unique identifier for the event
            timestamp: Base timestamp for the event

        Returns:
            Complete event dictionary
        """
        venue = random.choice(constants.NYC_VENUES)
        hour = random.choice([14, 16, 19, 20])  # Popular event times
        event_type = random.choice(['concert', 'sports', 'theater', 'conference'])

        # Adjust attendance based on event type
        if event_type == 'sports':
            attendance_factor = random.uniform(0.7, 0.95)
        elif event_type == 'concert':
            attendance_factor = random.uniform(0.6, 0.9)
        else:
            attendance_factor = random.uniform(0.4, 0.8)

        return {
            'event_id': f"synthetic_{event_id}",
            'event_type': event_type,
            'venue_name': venue['name'],
            'venue_lat': venue['lat'],
            'venue_lon': venue['lon'],
            'expected_attendance': int(venue['capacity'] * attendance_factor),
            'start_time': timestamp.replace(hour=hour, minute=0).isoformat(),
            'end_time': (timestamp.replace(hour=hour, minute=0) + timedelta(hours=random.randint(2, 4))).isoformat()
        }

    def fetch_data(self) -> Generator:
        """Generate event records, continuing with synthetic when exhausted."""
        # First, yield all events from the data source
        last_event_time = None
        event_count = 0

        for event in self.data_source.fetch():
            event_count += 1
            # Track the last event time we've seen
            if 'start_time' in event:
                try:
                    last_event_time = datetime.fromisoformat(event['start_time'])
                except:
                    pass

            yield self.add_affected_zones(event)

        # Log what we processed
        if event_count > 0:
            print(f"Processed {event_count} events from data source", file=sys.stderr)
        else:
            print("No events from data source", file=sys.stderr)

        # If continuous mode, generate synthetic events forever
        if self.config.continuous_mode:
            print("Starting continuous synthetic event generation...", file=sys.stderr)
            self.is_generating_synthetic = True

            # Start from last known time or current time
            current_date = (last_event_time + timedelta(days=1)) if last_event_time else datetime.now()

            while True:
                # Generate 0-4 events per day
                num_events_today = random.randint(0, 4)

                for _ in range(num_events_today):
                    event = self.generate_synthetic_event(
                        self.synthetic_event_counter,
                        current_date
                    )
                    self.synthetic_event_counter += 1

                    yield self.add_affected_zones(event)

                # Move to next day
                current_date += timedelta(days=1)

    def process_record(self, record: Any) -> Tuple[bytes, bytes, Optional[Dict[str, str]]]:
        """
        Process an event record into Kafka key, value, and headers.

        Args:
            record: Event dict with affected zones

        Returns:
            Tuple of (key_bytes, value_bytes, headers)
        """
        # Key is event ID
        key = record['event_id']

        # Payload is the record itself (metadata will be added by base class)
        payload = record.copy()

        # Override data source detection if we're generating synthetic events
        if self.is_generating_synthetic or record['event_id'].startswith('synthetic_'):
            # Temporarily override data_source attribute for proper detection
            original_source = self.data_source if hasattr(self, 'data_source') else None

            # Create a temporary synthetic source indicator
            class SyntheticIndicator:
                __class__.__name__ = 'SyntheticEventSource'

            self.data_source = SyntheticIndicator()
            result = self.process_record_common(record, key, payload, {
                'event-type': record.get('event_type', 'unknown'),
                'venue': record.get('venue_name', 'unknown'),
                'affected-zones-count': str(len(record.get('affected_zones', [])))
            })

            # Restore original source
            if original_source:
                self.data_source = original_source

            return result

        # Producer-specific headers
        additional_headers = {
            'event-type': record.get('event_type', 'unknown'),
            'venue': record.get('venue_name', 'unknown'),
            'affected-zones-count': str(len(record.get('affected_zones', [])))
        }

        # Use common processing from base class
        return self.process_record_common(record, key, payload, additional_headers)

    def get_summary_info(self) -> str:
        """Get additional summary information."""
        if self.is_generating_synthetic:
            return f"(generated {self.synthetic_event_counter} synthetic events)"

        data_source, is_synthetic = self.get_data_source_info()
        if is_synthetic:
            return "[synthetic]"
        elif data_source == "NYC_OPEN_DATA":
            return "[NYC Open Data]"

        return ""


def main():
    """Main entry point."""
    # Create producer with environment configuration
    config = EventsConfig.from_env()
    producer = EventsProducer(config)
    producer.run()


if __name__ == "__main__":
    main()