#!/usr/bin/env python3
"""
Query Pipeline Latency Metrics from MinIO using DuckDB

This script queries pipeline latency metrics stored as Parquet files in MinIO
and displays them in a formatted table.
"""

import argparse
import csv
import sys
from datetime import datetime
from typing import List, Dict, Any

try:
    import duckdb
    from tabulate import tabulate
except ImportError as e:
    print(f"Error: Missing required dependency: {e}")
    print("Please install dependencies: pip install -r requirements.txt")
    sys.exit(1)


class LatencyMetricsQuery:
    """Query and display pipeline latency metrics from MinIO."""

    def __init__(
        self,
        endpoint: str = "localhost:9000",
        access_key: str = "admin",
        secret_key: str = "admin123",
        bucket: str = "lakehouse",
        use_ssl: bool = False
    ):
        """
        Initialize the metrics query client.

        Args:
            endpoint: MinIO S3 endpoint
            access_key: MinIO access key
            secret_key: MinIO secret key
            bucket: S3 bucket name
            use_ssl: Whether to use SSL for S3 connection
        """
        self.endpoint = endpoint
        self.access_key = access_key
        self.secret_key = secret_key
        self.bucket = bucket
        self.use_ssl = use_ssl
        self.conn = None

    def connect(self):
        """Initialize DuckDB connection and configure S3 access."""
        self.conn = duckdb.connect(':memory:')

        # Install and load httpfs extension for S3 access
        self.conn.execute("INSTALL httpfs;")
        self.conn.execute("LOAD httpfs;")

        # Configure S3 connection to MinIO
        self.conn.execute(f"SET s3_endpoint='{self.endpoint}';")
        self.conn.execute(f"SET s3_access_key_id='{self.access_key}';")
        self.conn.execute(f"SET s3_secret_access_key='{self.secret_key}';")
        self.conn.execute(f"SET s3_use_ssl={'true' if self.use_ssl else 'false'};")
        self.conn.execute("SET s3_url_style='path';")

        print(f"Connected to MinIO at {self.endpoint}")

    def get_parquet_path(self) -> str:
        """Get the S3 path to the pipeline latency metrics Parquet files."""
        return f"s3://{self.bucket}/warehouse/monitoring/pipeline_latency_metrics/data/**/*.parquet"

    def query_latest_metrics(self, limit: int = 10) -> List[Dict[str, Any]]:
        """
        Query the latest N windows of latency metrics.

        Args:
            limit: Number of most recent windows to return

        Returns:
            List of dictionaries containing metric data
        """
        parquet_path = self.get_parquet_path()

        query = f"""
        SELECT
            window_start,
            window_end,
            events_processed,
            events_per_second,
            latency_p50_ms,
            latency_p95_ms,
            latency_p99_ms,
            min_latency_ms,
            max_latency_ms,
            avg_latency_ms
        FROM read_parquet('{parquet_path}', hive_partitioning=true)
        ORDER BY window_start DESC
        LIMIT {limit}
        """

        try:
            result = self.conn.execute(query).fetchall()
            columns = [desc[0] for desc in self.conn.description]
            return [dict(zip(columns, row)) for row in result]
        except Exception as e:
            print(f"Error querying metrics: {e}")
            print(f"Attempted path: {parquet_path}")
            return []

    def format_timestamp(self, ts) -> str:
        """Format timestamp for display."""
        if isinstance(ts, datetime):
            return ts.strftime('%Y-%m-%d %H:%M:%S')
        return str(ts)

    def format_number(self, num, decimals: int = 2) -> str:
        """Format number for display."""
        if num is None:
            return "N/A"
        if isinstance(num, float):
            return f"{num:,.{decimals}f}"
        return f"{num:,}"

    def display_metrics(self, metrics: List[Dict[str, Any]]):
        """
        Display metrics in a formatted table.

        Args:
            metrics: List of metric dictionaries
        """
        if not metrics:
            print("\nNo metrics found.")
            return

        # Prepare data for display
        headers = [
            "Window Start",
            "Window End",
            "Events",
            "Events/sec",
            "P50 (ms)",
            "P95 (ms)",
            "P99 (ms)",
            "Min (ms)",
            "Max (ms)",
            "Avg (ms)"
        ]

        rows = []
        for m in metrics:
            rows.append([
                self.format_timestamp(m['window_start']),
                self.format_timestamp(m['window_end']),
                self.format_number(m['events_processed'], 0),
                self.format_number(m['events_per_second'], 2),
                self.format_number(m['latency_p50_ms'], 0),
                self.format_number(m['latency_p95_ms'], 0),
                self.format_number(m['latency_p99_ms'], 0),
                self.format_number(m['min_latency_ms'], 0),
                self.format_number(m['max_latency_ms'], 0),
                self.format_number(m['avg_latency_ms'], 2)
            ])

        # Display table
        print("\n" + "="*120)
        print(f"Pipeline Latency Metrics (Last {len(metrics)} windows)")
        print("="*120)
        print(tabulate(rows, headers=headers, tablefmt='grid'))
        print()

        # Summary statistics
        if metrics:
            avg_throughput = sum(m['events_per_second'] or 0 for m in metrics) / len(metrics)
            avg_p95 = sum(m['latency_p95_ms'] or 0 for m in metrics) / len(metrics)
            avg_p99 = sum(m['latency_p99_ms'] or 0 for m in metrics) / len(metrics)

            print("="*120)
            print("Summary Statistics")
            print("="*120)
            print(f"Average Throughput:    {self.format_number(avg_throughput, 2)} events/sec")
            print(f"Average P95 Latency:   {self.format_number(avg_p95, 2)} ms")
            print(f"Average P99 Latency:   {self.format_number(avg_p99, 2)} ms")
            print()

    def export_to_csv(self, metrics: List[Dict[str, Any]], filename: str):
        """
        Export metrics to a CSV file.

        Args:
            metrics: List of metric dictionaries
            filename: Path to the CSV file to create
        """
        if not metrics:
            print(f"\nNo metrics to export.")
            return

        # Define CSV headers (matching the raw data)
        headers = [
            'window_start',
            'window_end',
            'events_processed',
            'events_per_second',
            'latency_p50_ms',
            'latency_p95_ms',
            'latency_p99_ms',
            'min_latency_ms',
            'max_latency_ms',
            'avg_latency_ms'
        ]

        try:
            with open(filename, 'w', newline='') as csvfile:
                writer = csv.DictWriter(csvfile, fieldnames=headers)
                writer.writeheader()

                # Write each metric row
                for m in metrics:
                    # Convert timestamps to string format for CSV
                    row = {
                        'window_start': self.format_timestamp(m['window_start']),
                        'window_end': self.format_timestamp(m['window_end']),
                        'events_processed': m['events_processed'],
                        'events_per_second': m['events_per_second'],
                        'latency_p50_ms': m['latency_p50_ms'],
                        'latency_p95_ms': m['latency_p95_ms'],
                        'latency_p99_ms': m['latency_p99_ms'],
                        'min_latency_ms': m['min_latency_ms'],
                        'max_latency_ms': m['max_latency_ms'],
                        'avg_latency_ms': m['avg_latency_ms']
                    }
                    writer.writerow(row)

            print(f"\n✓ Exported {len(metrics)} rows to {filename}")
        except Exception as e:
            print(f"\nError exporting to CSV: {e}", file=sys.stderr)

    def close(self):
        """Close the database connection."""
        if self.conn:
            self.conn.close()


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(
        description='Query pipeline latency metrics from MinIO',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Show last 10 windows and save to CSV (default)
  python query_latency_metrics.py

  # Show last 20 windows
  python query_latency_metrics.py -n 20

  # Custom CSV output path
  python query_latency_metrics.py -o /path/to/metrics.csv

  # Use custom MinIO endpoint
  python query_latency_metrics.py --endpoint localhost:9000
        """
    )

    parser.add_argument(
        '-n', '--limit',
        type=int,
        default=10,
        help='Number of most recent windows to display (default: 10)'
    )

    parser.add_argument(
        '--endpoint',
        type=str,
        default='localhost:9000',
        help='MinIO S3 endpoint (default: localhost:9000)'
    )

    parser.add_argument(
        '--access-key',
        type=str,
        default='admin',
        help='MinIO access key (default: admin)'
    )

    parser.add_argument(
        '--secret-key',
        type=str,
        default='admin123',
        help='MinIO secret key (default: admin123)'
    )

    parser.add_argument(
        '--bucket',
        type=str,
        default='lakehouse',
        help='S3 bucket name (default: lakehouse)'
    )

    parser.add_argument(
        '--ssl',
        action='store_true',
        help='Use SSL for S3 connection'
    )

    parser.add_argument(
        '-o', '--output',
        type=str,
        default='latency_metrics.csv',
        help='CSV output file path (default: latency_metrics.csv)'
    )

    args = parser.parse_args()

    # Create query client
    client = LatencyMetricsQuery(
        endpoint=args.endpoint,
        access_key=args.access_key,
        secret_key=args.secret_key,
        bucket=args.bucket,
        use_ssl=args.ssl
    )

    try:
        # Connect and query
        client.connect()
        metrics = client.query_latest_metrics(limit=args.limit)

        # Display to console
        client.display_metrics(metrics)

        # Export to CSV
        client.export_to_csv(metrics, args.output)

    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)
    finally:
        client.close()


if __name__ == '__main__':
    main()
