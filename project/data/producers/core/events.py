#!/usr/bin/env python3
"""
Events Producer (Clean Architecture) - NYC Open Data → Kafka

Pulls NYC event data and maps to affected taxi zones.
This version uses clean architecture with configuration injection.
"""

import sys
import random
from typing import Generator, Tuple, Any, Dict, Optional
from datetime import datetime, timedelta

from .base import BaseProducer
from ..utils.zones import find_affected_zones

from ..config.events import EventsConfig
from ..common import SCHEMAS, EVENT_IMPACT_RADIUS
from ..data_sources.factories import get_event_data_source
from ..utils import (
    add_lateness,
    generate_salted_key,
    normalize_timestamp
)


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
            batch_size=self.config.batch_size,
        )

        # Get appropriate data source
        self.data_source = get_event_data_source(self.config)


    def get_producer_config(self):
        """Get events-specific Kafka producer configuration."""
        return {
            "compression.type": self.config.compression_type,
            "batch.num.messages": 1000,
            "enable.idempotence": self.config.enable_idempotence,
            "max.in.flight.requests.per.connection": self.config.max_in_flight_requests,
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
            event["venue_lat"], event["venue_lon"], radius=EVENT_IMPACT_RADIUS
        )
        return {**event, "affected_zones": zones}

    def generate_synthetic_event(self, event_id: int, timestamp: datetime) -> dict:
        """
        Generate a single synthetic event.

        Args:
            event_id: Unique identifier for the event
            timestamp: Base timestamp for the event

        Returns:
            Complete event dictionary
        """
        from ..common import NYC_VENUES
        venue = random.choice(NYC_VENUES)
        hour = random.choice([14, 16, 19, 20])  # Popular event times
        event_type = random.choice(["concert", "sports", "theater", "conference"])

        # Adjust attendance based on event type
        if event_type == "sports":
            attendance_factor = random.uniform(0.7, 0.95)
        elif event_type == "concert":
            attendance_factor = random.uniform(0.6, 0.9)
        else:
            attendance_factor = random.uniform(0.4, 0.8)

        return {
            "event_id": f"synthetic_{event_id}",
            "event_type": event_type,
            "venue_name": venue["name"],
            "venue_lat": venue["lat"],
            "venue_lon": venue["lon"],
            "expected_attendance": int(venue["capacity"] * attendance_factor),
            "start_time": timestamp.replace(hour=hour, minute=0).isoformat(),
            "end_time": (
                timestamp.replace(hour=hour, minute=0)
                + timedelta(hours=random.randint(2, 4))
            ).isoformat(),
        }

    def fetch_data(self) -> Generator:
        """Fetch event data from configured source, respecting synthetic_mode."""
        print(f"Data mode: {self.config.synthetic_mode}", file=sys.stderr)

        # Check if source is available
        if (
            not self.data_source.is_available()
            and self.config.synthetic_mode != "synthetic"
        ):
            print("Primary data source unavailable, using fallback", file=sys.stderr)

        # Return generator from data source
        for event in self.data_source.fetch():
            yield self.add_affected_zones(event)

    def process_record(
        self, record: Any
    ) -> Tuple[bytes, bytes, Optional[Dict[str, str]]]:
        """
        Process an event record into Kafka key, value, and headers.

        Args:
            record: Event dict with affected zones

        Returns:
            Tuple of (key_bytes, value_bytes, headers)
        """
        # Extract timestamp for potential lateness injection
        ts_str = record.get("start_time", datetime.now().isoformat())
        ts_dt = normalize_timestamp(ts_str)
        original_ts = ts_dt

        # Add lateness if configured
        ts_out = add_lateness(
            ts_dt, self.config.p_late, self.config.late_min, self.config.late_max
        )

        # Calculate lateness in milliseconds
        lateness_ms = (
            int((ts_out - original_ts).total_seconds() * 1000)
            if ts_out != original_ts
            else None
        )

        # Generate key based on configuration
        if self.config.key_mode == "zone":
            # Use first affected zone as key base
            zones = record.get("affected_zones", [])
            base_key = str(zones[0]) if zones else "0"
        else:
            # Default: use event ID
            base_key = record["event_id"]

        # Apply salting if configured
        key = generate_salted_key(base_key, self.config.salt_keys)

        # Create payload with updated timestamp and lateness
        payload = record.copy()
        if ts_out != original_ts:
            payload["start_time"] = ts_out.isoformat()
        if lateness_ms is not None:
            payload["lateness_ms"] = lateness_ms


        # Producer-specific headers
        additional_headers = {
            "event-type": record.get("event_type", "unknown"),
            "venue": record.get("venue_name", "unknown"),
            "affected-zones-count": str(len(record.get("affected_zones", []))),
        }

        # Use common processing from base class
        return self.process_record_common(record, key, payload, additional_headers)

    def get_summary_info(self) -> str:
        """Get additional summary information."""
        data_source, is_synthetic = self.get_data_source_info()
        if is_synthetic:
            return f"[synthetic: {self.config.synthetic_mode}]"
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
