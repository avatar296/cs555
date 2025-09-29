#!/usr/bin/env python3
"""
Bronze Consumer Entry Point

Main script to run bronze layer consumers for the medallion architecture.
Supports running individual consumers or all consumers in parallel.
"""

import sys
import os
import argparse
import signal
import time
from typing import List, Optional
from concurrent.futures import ProcessPoolExecutor, as_completed
import logging

# Add parent directory to path for imports
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from consumers.bronze import (
    BronzeTripsConsumer,
    BronzeWeatherConsumer,
    BronzeEventsConsumer
)
from consumers.config import get_consumer_config


# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger(__name__)


# Consumer registry
CONSUMERS = {
    'trips': BronzeTripsConsumer,
    'weather': BronzeWeatherConsumer,
    'events': BronzeEventsConsumer
}


def run_consumer(consumer_name: str, config_overrides: dict = None) -> int:
    """
    Run a single bronze consumer.

    Args:
        consumer_name: Name of the consumer to run
        config_overrides: Optional configuration dictionary overrides

    Returns:
        Exit code (0 for success, 1 for failure)
    """
    if consumer_name not in CONSUMERS:
        logger.error(f"Unknown consumer: {consumer_name}")
        logger.info(f"Available consumers: {', '.join(CONSUMERS.keys())}")
        return 1

    try:
        logger.info(f"Starting {consumer_name} bronze consumer...")
        # Get configuration using the config factory
        config = get_consumer_config(consumer_name, **(config_overrides or {}))
        consumer_class = CONSUMERS[consumer_name]
        consumer = consumer_class(config)
        consumer.run()
        return 0
    except KeyboardInterrupt:
        logger.info(f"{consumer_name} consumer interrupted by user")
        return 0
    except Exception as e:
        logger.error(f"Error running {consumer_name} consumer: {e}", exc_info=True)
        return 1


def run_parallel_consumers(consumer_names: List[str], config_overrides: dict = None) -> int:
    """
    Run multiple consumers in parallel processes.

    Args:
        consumer_names: List of consumer names to run
        config_overrides: Optional configuration dictionary overrides

    Returns:
        Exit code (0 if all succeed, 1 if any fail)
    """
    logger.info(f"Starting {len(consumer_names)} consumers in parallel...")

    # Handle interrupt signal
    def signal_handler(signum, frame):
        logger.info("Received interrupt signal, stopping all consumers...")
        sys.exit(0)

    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    success_count = 0
    failure_count = 0

    with ProcessPoolExecutor(max_workers=len(consumer_names)) as executor:
        # Submit all consumer tasks
        futures = {
            executor.submit(run_consumer, name, config_overrides): name
            for name in consumer_names
        }

        # Wait for completion
        for future in as_completed(futures):
            consumer_name = futures[future]
            try:
                result = future.result()
                if result == 0:
                    success_count += 1
                    logger.info(f"{consumer_name} consumer completed successfully")
                else:
                    failure_count += 1
                    logger.error(f"{consumer_name} consumer failed")
            except Exception as e:
                failure_count += 1
                logger.error(f"{consumer_name} consumer crashed: {e}")

    logger.info(f"Parallel execution complete: {success_count} succeeded, {failure_count} failed")
    return 0 if failure_count == 0 else 1


def parse_config(config_str: str) -> dict:
    """
    Parse configuration string into dictionary.

    Args:
        config_str: Configuration in format "key1=value1,key2=value2"

    Returns:
        Configuration dictionary
    """
    if not config_str:
        return {}

    config = {}
    for item in config_str.split(','):
        if '=' in item:
            key, value = item.split('=', 1)
            # Try to parse as number
            try:
                if '.' in value:
                    config[key.strip()] = float(value)
                else:
                    config[key.strip()] = int(value)
            except ValueError:
                # Keep as string
                config[key.strip()] = value.strip()

    return config


def main():
    """Main entry point for bronze consumer."""
    parser = argparse.ArgumentParser(
        description='Run bronze layer consumers for medallion architecture',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Run trips consumer
  python run_bronze_consumer.py --source trips

  # Run all consumers in parallel
  python run_bronze_consumer.py --all

  # Run specific consumers in parallel
  python run_bronze_consumer.py --source trips weather

  # Run with custom configuration
  python run_bronze_consumer.py --source trips --config "trigger_interval=10 seconds,max_offsets_per_trigger=5000"

  # Run with environment-based configuration
  TRIPS_TOPIC=trips.yellow TRIPS_TRIGGER_INTERVAL="30 seconds" python run_bronze_consumer.py --source trips
        """
    )

    parser.add_argument(
        '--source',
        nargs='+',
        choices=list(CONSUMERS.keys()),
        help='Data source(s) to consume'
    )

    parser.add_argument(
        '--all',
        action='store_true',
        help='Run all available consumers in parallel'
    )

    parser.add_argument(
        '--config',
        type=str,
        help='Configuration as key=value pairs (e.g., "trigger_interval=30 seconds,max_offsets=5000")'
    )

    parser.add_argument(
        '--list',
        action='store_true',
        help='List available consumers and exit'
    )

    parser.add_argument(
        '--parallel',
        action='store_true',
        help='Run specified consumers in parallel (default for multiple sources)'
    )

    parser.add_argument(
        '--kafka-bootstrap',
        type=str,
        default=os.getenv('KAFKA_BOOTSTRAP', 'kafka:9092'),
        help='Kafka bootstrap servers'
    )

    parser.add_argument(
        '--schema-registry',
        type=str,
        default=os.getenv('SCHEMA_REGISTRY_URL', 'http://schema-registry:8081'),
        help='Schema Registry URL'
    )

    parser.add_argument(
        '--starting-offsets',
        choices=['earliest', 'latest'],
        default='latest',
        help='Starting offset position for consumption'
    )

    parser.add_argument(
        '--verbose',
        action='store_true',
        help='Enable verbose logging'
    )

    args = parser.parse_args()

    # Set logging level
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)

    # List consumers and exit
    if args.list:
        print("Available bronze consumers:")
        for name in CONSUMERS:
            print(f"  - {name}")
        return 0

    # Determine which consumers to run
    if args.all:
        consumer_names = list(CONSUMERS.keys())
        run_parallel = True
    elif args.source:
        consumer_names = args.source
        run_parallel = args.parallel or len(consumer_names) > 1
    else:
        parser.error("Please specify --source or --all")

    # Parse configuration
    config = parse_config(args.config) if args.config else {}

    # Add command-line arguments to config
    config['kafka_bootstrap'] = args.kafka_bootstrap
    config['schema_registry_url'] = args.schema_registry
    config['starting_offsets'] = args.starting_offsets

    # Log configuration
    logger.info(f"Configuration: {config}")

    # Run consumers
    if run_parallel and len(consumer_names) > 1:
        return run_parallel_consumers(consumer_names, config)
    else:
        # Run single consumer
        return run_consumer(consumer_names[0], config)


if __name__ == '__main__':
    sys.exit(main())