#!/usr/bin/env python3
"""
Query Iceberg tables using DuckDB - Modern, serverless approach
No Hive Metastore needed!
"""

import duckdb
import pandas as pd
from datetime import datetime

def setup_duckdb_iceberg():
    """Initialize DuckDB with Iceberg and S3 support"""

    # Create connection
    con = duckdb.connect()

    # Install and load required extensions
    con.execute("INSTALL iceberg;")
    con.execute("INSTALL httpfs;")  # For S3 support
    con.execute("LOAD iceberg;")
    con.execute("LOAD httpfs;")

    # Configure S3/MinIO credentials
    con.execute("""
        SET s3_region='us-east-1';
        SET s3_endpoint='localhost:9000';
        SET s3_access_key_id='admin';
        SET s3_secret_access_key='admin123';
        SET s3_use_ssl=false;
        SET s3_url_style='path';
    """)

    return con

def list_iceberg_snapshots(con, table_path):
    """List all snapshots of an Iceberg table"""
    try:
        # Test if table exists by trying to scan it
        con.execute(f"""
            SELECT * FROM iceberg_scan('{table_path}', allow_moved_paths=true)
            LIMIT 0
        """)
        print(f"✓ Table found at: {table_path}")

        # Get metadata
        metadata = con.execute(f"""
            SELECT * FROM iceberg_metadata('{table_path}')
        """).fetchall()

        if metadata:
            print("\nTable Metadata:")
            for row in metadata:
                print(f"  {row}")

    except Exception as e:
        print(f"✗ Could not access table: {e}")
        return False
    return True

def query_iceberg_table(con, table_path):
    """Query the Iceberg table and show results"""

    print(f"\n📊 Querying Iceberg table: {table_path}")
    print("=" * 60)

    try:
        # First check if table exists
        con.execute(f"""
            SELECT * FROM iceberg_scan('{table_path}', allow_moved_paths=true)
            LIMIT 1
        """)

        # Get row count
        count_result = con.execute(f"""
            SELECT COUNT(*) as total_rows
            FROM iceberg_scan('{table_path}', allow_moved_paths=true)
        """).fetchone()

        print(f"Total rows: {count_result[0]}")

        # Query aggregated data
        print("\n📈 Aggregated Trip Statistics:")
        df = con.execute(f"""
            SELECT
                date,
                hour,
                distance_category,
                SUM(trip_count) as total_trips,
                ROUND(AVG(avg_distance), 2) as avg_distance,
                ROUND(MIN(min_distance), 2) as min_distance,
                ROUND(MAX(max_distance), 2) as max_distance
            FROM iceberg_scan('{table_path}', allow_moved_paths=true)
            GROUP BY date, hour, distance_category
            ORDER BY date DESC, hour DESC, distance_category
            LIMIT 20
        """).fetchdf()

        print(df.to_string())

        # Summary by distance category
        print("\n📊 Summary by Distance Category:")
        summary = con.execute(f"""
            SELECT
                distance_category,
                COUNT(*) as window_count,
                SUM(trip_count) as total_trips,
                ROUND(AVG(avg_distance), 2) as overall_avg_distance
            FROM iceberg_scan('{table_path}', allow_moved_paths=true)
            GROUP BY distance_category
            ORDER BY distance_category
        """).fetchdf()

        print(summary.to_string())

        # Recent windows
        print("\n🕐 Most Recent Time Windows:")
        recent = con.execute(f"""
            SELECT
                window_start,
                window_end,
                distance_category,
                trip_count
            FROM iceberg_scan('{table_path}', allow_moved_paths=true)
            ORDER BY window_end DESC
            LIMIT 10
        """).fetchdf()

        print(recent.to_string())

    except Exception as e:
        print(f"✗ Error querying table: {e}")
        print("\nTrying to list available columns...")
        try:
            # Try to get schema
            schema = con.execute(f"""
                SELECT * FROM iceberg_scan('{table_path}', allow_moved_paths=true)
                LIMIT 0
            """).description
            print("Available columns:")
            for col in schema:
                print(f"  - {col[0]}")
        except:
            pass

def explore_s3_bucket(con):
    """Explore the S3 bucket structure"""
    print("\n🗂️  Exploring MinIO Bucket Structure:")
    print("=" * 60)

    try:
        # List files in the iceberg directory
        files = con.execute("""
            SELECT * FROM glob('s3://lakehouse/iceberg/**/*.parquet')
            LIMIT 10
        """).fetchall()

        if files:
            print("Found Parquet files:")
            for f in files:
                print(f"  - {f[0]}")
        else:
            print("No Parquet files found in s3://lakehouse/iceberg/")

        # Try to find metadata files
        metadata_files = con.execute("""
            SELECT * FROM glob('s3://lakehouse/iceberg/**/metadata/*.json')
            LIMIT 5
        """).fetchall()

        if metadata_files:
            print("\nFound Iceberg metadata files:")
            for f in metadata_files:
                print(f"  - {f[0]}")

    except Exception as e:
        print(f"Could not explore bucket: {e}")

def main():
    """Main function to query Iceberg tables with DuckDB"""

    print("🦆 DuckDB Iceberg Query Tool")
    print("=" * 60)
    print(f"Timestamp: {datetime.now()}")

    # Initialize DuckDB with Iceberg support
    con = setup_duckdb_iceberg()
    print("✓ DuckDB initialized with Iceberg and S3 support")

    # Define the Iceberg table path
    # This should match where Spark writes the data
    table_path = "s3://lakehouse/iceberg/db/trips_aggregated"

    # Explore bucket structure
    explore_s3_bucket(con)

    # Query the Iceberg table
    if list_iceberg_snapshots(con, table_path):
        query_iceberg_table(con, table_path)
    else:
        print(f"\n⚠️  Table not found at {table_path}")
        print("Make sure the Spark streaming consumer has written data first.")
        print("Run: make spark-consume")

    # Close connection
    con.close()
    print("\n✓ Done")

if __name__ == "__main__":
    main()