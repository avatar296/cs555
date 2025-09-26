#!/usr/bin/env python3
"""
Unified Producer Runner

Single entry point for all Kafka producers to eliminate code duplication.
Usage: python run_producer.py <producer_type>
Where producer_type is one of: trips, weather, events
"""

import sys

from core import TripsProducer, WeatherProducer, EventsProducer
from config import TripsConfig, WeatherConfig, EventsConfig


def get_producer_class_and_config(producer_type: str):
    """
    Get the appropriate producer class and configuration based on type.

    Args:
        producer_type: Type of producer (trips, weather, events)

    Returns:
        Tuple of (ProducerClass, ConfigClass)

    Raises:
        ValueError: If producer_type is invalid
    """
    producers = {
        "trips": (TripsProducer, TripsConfig),
        "weather": (WeatherProducer, WeatherConfig),
        "events": (EventsProducer, EventsConfig)
    }

    if producer_type not in producers:
        raise ValueError(
            f"Unknown producer type: {producer_type}\n"
            f"Valid types are: {', '.join(producers.keys())}"
        )

    return producers[producer_type]


def main():
    """Main entry point for unified producer runner."""
    # Check command line arguments
    if len(sys.argv) < 2:
        print("Usage: python run_producer.py <producer_type>", file=sys.stderr)
        print("Where producer_type is one of: trips, weather, events", file=sys.stderr)
        sys.exit(1)

    producer_type = sys.argv[1].lower()

    try:
        # Get appropriate classes
        ProducerClass, ConfigClass = get_producer_class_and_config(producer_type)

        # Create configuration from environment
        config = ConfigClass.from_env()

        # Create and run producer
        producer = ProducerClass(config)
        producer.run()

    except ValueError as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        print(f"\n[{producer_type}] Interrupted by user", file=sys.stderr)
        sys.exit(0)
    except Exception as e:
        print(f"Error running {producer_type} producer: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()