#!/usr/bin/env python3
"""
Bronze Layer Trips Consumer

Consumes NYC TLC trip data from Kafka and writes to bronze layer in Iceberg.
Preserves raw data exactly as received with ingestion timestamp-based partitioning.
No filtering or quality checks - pure data archival.
"""

from typing import Dict, Any
from pyspark.sql import DataFrame

from ..core import BaseBronzeConsumer
from ..config.trips import TripsConsumerConfig


class BronzeTripsConsumer(BaseBronzeConsumer):
    """Bronze layer consumer for NYC TLC trips data."""

    def __init__(self, config: TripsConsumerConfig = None):
        """
        Initialize bronze trips consumer.

        Args:
            config: Optional TripsConsumerConfig instance
        """
        # Use provided config or create default
        self.config = config or TripsConsumerConfig()

        super().__init__(
            name=self.config.consumer_name,
            source_topic=self.config.source_topic,
            schema_subject=self.config.schema_subject,
            target_table=self.config.target_table,
            kafka_bootstrap=self.config.kafka_bootstrap,
            schema_registry_url=self.config.schema_registry_url,
            checkpoint_location=self.config.checkpoint_location,
            trigger_interval=self.config.trigger_interval,
            max_offsets_per_trigger=self.config.max_offsets_per_trigger,
            starting_offsets=self.config.starting_offsets
        )

    def get_consumer_config(self) -> Dict[str, Any]:
        """
        Get trips consumer configuration.

        Returns:
            Configuration dictionary
        """
        return {
            "source_topic": self.config.source_topic,
            "target_table": self.config.target_table,
            "trigger_interval": self.config.trigger_interval,
            "max_offsets_per_trigger": self.config.max_offsets_per_trigger
        }

    def process_batch(self, df: DataFrame) -> DataFrame:
        """
        Process a batch of trip records.
        Bronze layer preserves raw data - no transformations or filtering.

        Args:
            df: DataFrame with bronze metadata already added

        Returns:
            DataFrame unchanged (raw data preservation)
        """
        # Bronze layer: No processing, no filtering, no quality checks
        # Just return the data as-is with bronze metadata already added by base class
        return df


def main():
    """Main entry point for bronze trips consumer."""
    consumer = BronzeTripsConsumer()
    consumer.run()


if __name__ == "__main__":
    main()