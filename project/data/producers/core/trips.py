#!/usr/bin/env python3
"""
NYC TLC Taxi Trip Data Producer (Clean Architecture)

Streams NYC Taxi & Limousine Commission trip data from Parquet files
to Apache Kafka using Avro serialization.

This version uses clean architecture with configuration injection.
"""

from typing import Generator, Tuple, Any, Dict, Optional
import sys

from .base import BaseProducer
from ..config.trips import TripsConfig
from ..common.schemas import SCHEMAS
from ..data_sources.factories import get_trip_data_source
from ..utils import (
    add_lateness,
    generate_salted_key,
    generate_hour_of_week_key,
    normalize_timestamp,
)


class TripsProducer(BaseProducer):
    """Clean trips producer with configuration injection."""

    def __init__(self, config: TripsConfig = None):
        """
        Initialize trips producer.

        Args:
            config: TripsConfig instance (defaults to environment-based config)
        """
        self.config = config or TripsConfig.from_env()

        super().__init__(
            name="trips",
            schema_file=SCHEMAS["trips"],
            topic=self.config.topic,
            bootstrap_servers=self.config.kafka_bootstrap,
            schema_registry_url=self.config.schema_registry_url,
            rate=self.config.rate,
            batch_size=self.config.batch_size,
        )

        # Get appropriate data source
        self.data_source = get_trip_data_source(self.config)

    def get_producer_config(self):
        """Get trips-specific Kafka producer configuration."""
        return {
            "compression.type": self.config.compression_type,
            "enable.idempotence": self.config.enable_idempotence,
            "max.in.flight.requests.per.connection": self.config.max_in_flight_requests,
        }

    def fetch_data(self) -> Generator:
        """Fetch trip data from configured source."""
        print(f"Data mode: {self.config.synthetic_mode}", file=sys.stderr)

        # Check if source is available
        if (
            not self.data_source.is_available()
            and self.config.synthetic_mode != "synthetic"
        ):
            print("Primary data source unavailable, using fallback", file=sys.stderr)

        # Return generator from data source
        return self.data_source.fetch()

    def process_record(
        self, record: Any
    ) -> Tuple[bytes, bytes, Optional[Dict[str, str]]]:
        """
        Process a trip record into Kafka key, value, and headers.

        Args:
            record: Trip data tuple (timestamp, pickup_zone, dropoff_zone, distance)

        Returns:
            Tuple of (key_bytes, value_bytes, headers)
        """
        ts, pu, do, dist = record

        # Normalize and potentially add lateness to timestamp
        ts_dt = normalize_timestamp(ts)
        original_ts = ts_dt
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
        if self.config.key_mode == "how_pu":
            base_key = generate_hour_of_week_key(ts_dt, int(pu or 0))
        else:
            base_key = str(int(pu or 0))

        # Apply salting if configured
        key = generate_salted_key(base_key, self.config.salt_keys)

        # Create payload (without metadata - will be added by base class)
        payload = {
            "ts": ts_out.isoformat(sep=" "),
            "pu": int(pu or 0),
            "do": int(do or 0),
            "dist": float(dist or 0.0),
            "lateness_ms": lateness_ms,
        }

        # Producer-specific headers
        additional_headers = {"synthetic-mode": self.config.synthetic_mode}

        # Use common processing from base class
        return self.process_record_common(record, key, payload, additional_headers)

    def get_summary_info(self) -> str:
        """Get additional summary information."""
        # Check if data source has metrics
        if hasattr(self.data_source, "synthetic_pct"):
            return f"(synthetic: {self.data_source.synthetic_pct:.1f}%)"

        # Use base class to determine data source
        data_source, is_synthetic = self.get_data_source_info()
        if is_synthetic:
            return "(100% synthetic)"
        elif data_source == "MIXED":
            return "(mixed mode)"

        return ""


def main():
    """Main entry point."""
    # Create producer with environment configuration
    config = TripsConfig.from_env()
    producer = TripsProducer(config)
    producer.run()


if __name__ == "__main__":
    main()
