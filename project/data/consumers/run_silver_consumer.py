#!/usr/bin/env python3
"""
Silver Layer Consumer Runner

Runs silver layer consumers for the medallion architecture.
Reads from bronze Iceberg tables and applies data quality and cleansing.
"""

import argparse
import sys
import os

# Add parent directory to path for imports
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from consumers.silver.trips import SilverTripsConsumer, SilverTripsAggregateConsumer


def parse_arguments():
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(
        description="Run Silver Layer Consumer",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
    python run_silver_consumer.py --source trips
    python run_silver_consumer.py --source trips --quality-threshold 0.8
    python run_silver_consumer.py --source trips --trigger-interval "2 minutes"
        """
    )

    parser.add_argument(
        "--source",
        type=str,
        default="trips",
        choices=["trips", "weather", "events"],
        help="Data source to consume (default: trips)"
    )

    parser.add_argument(
        "--bronze-table",
        type=str,
        help="Bronze table to read from (default: bronze.<source>)"
    )

    parser.add_argument(
        "--silver-table",
        type=str,
        help="Silver table to write to (default: silver.<source>)"
    )

    parser.add_argument(
        "--quality-threshold",
        type=float,
        default=0.7,
        help="Minimum quality score to accept (0.0-1.0, default: 0.7)"
    )

    parser.add_argument(
        "--trigger-interval",
        type=str,
        default="60 seconds",
        help="Trigger interval for streaming (default: 60 seconds)"
    )

    parser.add_argument(
        "--checkpoint-location",
        type=str,
        help="Checkpoint location (default: /tmp/checkpoint/silver/<source>)"
    )

    parser.add_argument(
        "--starting-offsets",
        type=str,
        default="latest",
        choices=["earliest", "latest"],
        help="Starting offsets for reading (default: latest)"
    )

    parser.add_argument(
        "--aggregate",
        action="store_true",
        help="Run aggregate version that pre-aggregates by hour"
    )

    return parser.parse_args()


def run_trips_consumer(args):
    """
    Run the silver trips consumer.

    Args:
        args: Parsed command-line arguments
    """
    # Determine source and target tables
    source_table = args.bronze_table or "bronze.trips"
    target_table = args.silver_table or "silver.trips"

    if args.aggregate:
        target_table = "silver.trips_hourly_agg"
        ConsumerClass = SilverTripsAggregateConsumer
    else:
        ConsumerClass = SilverTripsConsumer

    # Create and run consumer
    print(f"Starting Silver Trips Consumer...")
    print(f"  Reading from: {source_table}")
    print(f"  Writing to: {target_table}")
    print(f"  Quality threshold: {args.quality_threshold}")
    print(f"  Trigger interval: {args.trigger_interval}")
    print(f"  Aggregate mode: {args.aggregate}")

    # Create consumer
    consumer = ConsumerClass(
        source_table=source_table,
        target_table=target_table,
        checkpoint_location=args.checkpoint_location or f"/tmp/checkpoint/silver/{target_table.replace('.', '_')}",
        trigger_interval=args.trigger_interval,
        quality_threshold=args.quality_threshold
    )

    # Run streaming consumer
    consumer.run()


def run_weather_consumer(args):
    """Run the silver weather consumer (placeholder)."""
    print("Weather consumer not yet implemented")
    print("To implement: Create consumers/silver/weather.py similar to trips.py")
    sys.exit(1)


def run_events_consumer(args):
    """Run the silver events consumer (placeholder)."""
    print("Events consumer not yet implemented")
    print("To implement: Create consumers/silver/events.py similar to trips.py")
    sys.exit(1)


def main():
    """Main entry point."""
    args = parse_arguments()

    # Route to appropriate consumer
    if args.source == "trips":
        run_trips_consumer(args)
    elif args.source == "weather":
        run_weather_consumer(args)
    elif args.source == "events":
        run_events_consumer(args)
    else:
        print(f"Unknown source: {args.source}")
        sys.exit(1)


if __name__ == "__main__":
    main()