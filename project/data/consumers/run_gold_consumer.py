#!/usr/bin/env python3
"""
Gold Layer Consumer Runner

Runs gold layer consumers for the medallion architecture.
Reads from silver Iceberg tables and performs business aggregations.

Showcases three levels of complexity:
- Simple: Basic windowed aggregation (zone_metrics_hourly)
- Medium: Aggregation with dimension enrichment (od_flows_enriched_hourly)
- Complex: Multi-stream joins with temporal + spatial alignment (zone_demand_context_hourly)
"""

import argparse
import sys
import os

# Add parent directory to path for imports
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from consumers.gold.zone_metrics_hourly import GoldZoneMetricsHourly
from consumers.gold.od_flows_enriched_hourly import GoldODFlowsEnrichedHourly
from consumers.gold.zone_demand_context_hourly import GoldZoneDemandContextHourly


def parse_arguments():
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(
        description="Run Gold Layer Consumer",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
    # Simple pattern (trips only)
    python run_gold_consumer.py --table zone_metrics_hourly

    # Medium pattern (trips + zones)
    python run_gold_consumer.py --table od_flows_enriched_hourly

    # Complex pattern (trips + weather + events)
    python run_gold_consumer.py --table zone_demand_context_hourly

    # Custom window and watermark
    python run_gold_consumer.py --table zone_metrics_hourly --window "1 hour" --watermark "10 minutes"
        """
    )

    parser.add_argument(
        "--table",
        type=str,
        required=True,
        choices=[
            "zone_metrics_hourly",
            "od_flows_enriched_hourly",
            "zone_demand_context_hourly"
        ],
        help="Gold table to produce"
    )

    parser.add_argument(
        "--window",
        type=str,
        default="15 minutes",
        help="Aggregation window size (default: 15 minutes)"
    )

    parser.add_argument(
        "--watermark",
        type=str,
        default="5 minutes",
        help="Watermark for late data (default: 5 minutes)"
    )

    parser.add_argument(
        "--trigger-interval",
        type=str,
        default="5 seconds",
        help="Trigger interval for streaming (default: 5 seconds)"
    )

    parser.add_argument(
        "--checkpoint-location",
        type=str,
        help="Checkpoint location (default: /tmp/checkpoint/gold/<table>)"
    )

    # Advanced options for zone_demand_context_hourly
    parser.add_argument(
        "--trips-watermark",
        type=str,
        default="5 minutes",
        help="Watermark for trips stream (zone_demand_context_hourly only)"
    )

    parser.add_argument(
        "--weather-watermark",
        type=str,
        default="5 minutes",
        help="Watermark for weather stream (zone_demand_context_hourly only)"
    )

    parser.add_argument(
        "--events-watermark",
        type=str,
        default="1 hour",
        help="Watermark for events stream (zone_demand_context_hourly only)"
    )

    parser.add_argument(
        "--event-proximity",
        type=float,
        default=1.0,
        help="Event proximity threshold in miles (zone_demand_context_hourly only)"
    )

    return parser.parse_args()


def run_zone_metrics_hourly(args):
    """
    Run the zone metrics hourly consumer (Simple pattern).

    Args:
        args: Parsed command-line arguments
    """
    print("=" * 80)
    print("GOLD LAYER: Zone Metrics Hourly (SIMPLE PATTERN)")
    print("=" * 80)
    print(f"Source: silver.trips")
    print(f"Target: gold.zone_metrics_hourly")
    print(f"Window: {args.window}")
    print(f"Watermark: {args.watermark}")
    print(f"Trigger: {args.trigger_interval}")
    print("\nDemonstrates: Standard windowed aggregation")
    print("=" * 80)

    consumer = GoldZoneMetricsHourly(
        checkpoint_location=args.checkpoint_location or "/tmp/checkpoint/gold/zone_metrics_hourly",
        trigger_interval=args.trigger_interval,
        aggregation_window=args.window,
        watermark=args.watermark
    )

    consumer.run()


def run_od_flows_enriched_hourly(args):
    """
    Run the OD flows enriched hourly consumer (Medium pattern).

    Args:
        args: Parsed command-line arguments
    """
    print("=" * 80)
    print("GOLD LAYER: OD Flows Enriched Hourly (MEDIUM COMPLEXITY)")
    print("=" * 80)
    print(f"Source: silver.trips + silver.zones")
    print(f"Target: gold.od_flows_enriched_hourly")
    print(f"Window: {args.window}")
    print(f"Watermark: {args.watermark}")
    print(f"Trigger: {args.trigger_interval}")
    print("\nDemonstrates: Aggregation + dual dimension joins")
    print("=" * 80)

    consumer = GoldODFlowsEnrichedHourly(
        checkpoint_location=args.checkpoint_location or "/tmp/checkpoint/gold/od_flows_enriched_hourly",
        trigger_interval=args.trigger_interval,
        aggregation_window=args.window,
        watermark=args.watermark
    )

    consumer.run()


def run_zone_demand_context_hourly(args):
    """
    Run the zone demand context hourly consumer (Complex pattern).

    Args:
        args: Parsed command-line arguments
    """
    print("=" * 80)
    print("GOLD LAYER: Zone Demand Context Hourly (MAXIMUM COMPLEXITY)")
    print("=" * 80)
    print(f"Sources: silver.trips + silver.weather + silver.events")
    print(f"Target: gold.zone_demand_context_hourly")
    print(f"Window: {args.window}")
    print(f"Trips Watermark: {args.trips_watermark}")
    print(f"Weather Watermark: {args.weather_watermark}")
    print(f"Events Watermark: {args.events_watermark}")
    print(f"Event Proximity: {args.event_proximity} miles")
    print(f"Trigger: {args.trigger_interval}")
    print("\nDemonstrates: Triple stream join + temporal + spatial alignment")
    print("=" * 80)
    print("\nThis showcases graduate-level streaming architecture:")
    print("  ✓ Stream-stream-stream joins (3 sources)")
    print("  ✓ Coordinated watermarking across multiple streams")
    print("  ✓ Temporal alignment (interval joins)")
    print("  ✓ Spatial filtering (distance calculations)")
    print("  ✓ Complex business logic (demand lift, weather impact)")
    print("=" * 80)

    consumer = GoldZoneDemandContextHourly(
        checkpoint_location=args.checkpoint_location or "/tmp/checkpoint/gold/zone_demand_context_hourly",
        trigger_interval=args.trigger_interval,
        aggregation_window=args.window,
        trips_watermark=args.trips_watermark,
        weather_watermark=args.weather_watermark,
        events_watermark=args.events_watermark,
        event_proximity_miles=args.event_proximity
    )

    consumer.run()


def main():
    """Main entry point."""
    args = parse_arguments()

    # Route to appropriate consumer
    if args.table == "zone_metrics_hourly":
        run_zone_metrics_hourly(args)
    elif args.table == "od_flows_enriched_hourly":
        run_od_flows_enriched_hourly(args)
    elif args.table == "zone_demand_context_hourly":
        run_zone_demand_context_hourly(args)
    else:
        print(f"Unknown table: {args.table}")
        sys.exit(1)


if __name__ == "__main__":
    main()
