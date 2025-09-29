#!/usr/bin/env python3
"""
Unified Producer Runner

Single entry point for all Kafka producers with standardized configuration.
Usage: python run_producer.py <producer_type> [options]

Where producer_type is one of: trips, weather, events

Options:
    --synthetic-mode MODE   Set synthetic data mode (none/fallback/synthetic/mixed)
    --rate RATE            Set message rate per second
    --help                 Show this help message
"""

import sys
import os
import argparse

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


def parse_arguments():
    """Parse command-line arguments with support for configuration overrides."""
    parser = argparse.ArgumentParser(
        description="Run a Kafka producer with unified configuration",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Environment Variables (apply to all producers):
    SYNTHETIC_MODE      Synthetic data mode (none/fallback/synthetic/mixed)
    SYNTHETIC_RATIO     Ratio for mixed mode (0.0-1.0)
    YEARS              Years to process (e.g., "2023" or "2022,2023")
    MONTHS             Months to process (e.g., "1,2,3" or empty for all)
    RATE               Messages per second
    CONTINUOUS         Continue when data exhausted (true/false)
    KAFKA_BOOTSTRAP    Kafka bootstrap servers
    SCHEMA_REGISTRY_URL Schema registry URL

Examples:
    python run_producer.py trips
    python run_producer.py trips --synthetic-mode synthetic
    python run_producer.py weather --rate 500
    SYNTHETIC_MODE=mixed python run_producer.py events
        """
    )

    parser.add_argument(
        "producer_type",
        choices=["trips", "weather", "events"],
        help="Type of producer to run"
    )

    parser.add_argument(
        "--synthetic-mode",
        choices=["none", "fallback", "synthetic", "mixed"],
        help="Override synthetic data mode"
    )

    parser.add_argument(
        "--rate",
        type=float,
        help="Override message rate per second"
    )

    parser.add_argument(
        "--synthetic-ratio",
        type=float,
        help="Override synthetic ratio for mixed mode (0.0-1.0)"
    )

    parser.add_argument(
        "--years",
        help="Override years to process (e.g., '2023' or '2022,2023')"
    )

    parser.add_argument(
        "--months",
        help="Override months to process (e.g., '1,2,3' or empty for all)"
    )

    return parser.parse_args()


def apply_overrides(args):
    """Apply command-line overrides to environment variables."""
    if args.synthetic_mode:
        os.environ["SYNTHETIC_MODE"] = args.synthetic_mode

    if args.rate is not None:
        os.environ["RATE"] = str(args.rate)

    if args.synthetic_ratio is not None:
        os.environ["SYNTHETIC_RATIO"] = str(args.synthetic_ratio)

    if args.years:
        os.environ["YEARS"] = args.years

    if args.months is not None:
        os.environ["MONTHS"] = args.months


def print_configuration_summary(producer_type: str, config):
    """Print configuration summary at startup."""
    print(f"\n{'='*60}", file=sys.stderr)
    print(f"Starting {producer_type.upper()} Producer", file=sys.stderr)
    print(f"{'='*60}", file=sys.stderr)
    print(f"Configuration:", file=sys.stderr)
    print(f"  Topic: {config.topic}", file=sys.stderr)
    print(f"  Synthetic Mode: {config.synthetic_mode}", file=sys.stderr)

    if hasattr(config, 'synthetic_ratio'):
        print(f"  Synthetic Ratio: {config.synthetic_ratio}", file=sys.stderr)

    print(f"  Years: {config.years}", file=sys.stderr)
    print(f"  Months: {config.months or 'all'}", file=sys.stderr)
    print(f"  Rate: {config.rate} msgs/sec", file=sys.stderr)
    print(f"  Batch Size: {config.batch_size}", file=sys.stderr)
    print(f"  Continuous Mode: {config.continuous_mode}", file=sys.stderr)
    print(f"{'='*60}\n", file=sys.stderr)


def main():
    """Main entry point for unified producer runner."""
    try:
        # Parse arguments
        args = parse_arguments()
        producer_type = args.producer_type

        # Apply command-line overrides to environment
        apply_overrides(args)

        # Get appropriate classes
        ProducerClass, ConfigClass = get_producer_class_and_config(producer_type)

        # Create configuration from environment
        config = ConfigClass.from_env()

        # Print configuration summary
        print_configuration_summary(producer_type, config)

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