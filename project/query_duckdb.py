#!/usr/bin/env python3
"""
Query Bronze Layer Iceberg Tables using DuckDB

This script queries the raw data from the bronze layer of the medallion architecture.
Bronze layer preserves data exactly as received with ingestion metadata.
"""

import duckdb
import pandas as pd
from datetime import datetime
import sys

def setup_duckdb_iceberg():
    """Initialize DuckDB with S3 support for reading Parquet files"""

    # Create connection
    con = duckdb.connect()

    # Install and load httpfs extension for S3 support
    con.execute("INSTALL httpfs;")
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

def check_table_exists(con, table_path):
    """Check if parquet files exist for a table"""
    try:
        # Try to find any parquet files for this table
        result = con.execute(f"""
            SELECT COUNT(*) FROM glob('{table_path}/data/**/*.parquet')
        """).fetchone()
        return result[0] > 0
    except Exception:
        return False

def query_bronze_trips(con):
    """Query the bronze trips table"""
    table_path = "s3://lakehouse/iceberg/bronze/trips"

    print("\n" + "="*60)
    print("📊 BRONZE LAYER: TRIPS DATA")
    print("="*60)

    if not check_table_exists(con, table_path):
        print(f"❌ Table not found: {table_path}")
        print("   Run: make bronze-trips")
        return

    try:
        # Read all parquet files for this table
        parquet_path = f"{table_path}/data/**/*.parquet"

        # Get total row count
        count = con.execute(f"""
            SELECT COUNT(*) as total_rows
            FROM read_parquet('{parquet_path}')
        """).fetchone()[0]

        print(f"✅ Total records: {count:,}")

        if count == 0:
            print("   No data yet. Wait for producers to send data.")
            return

        # Recent trip records
        print("\n🚕 Recent Trip Records:")
        df = con.execute(f"""
            SELECT
                ts,
                pu as pickup_zone,
                "do" as dropoff_zone,
                dist as distance,
                lateness_ms,
                consumer_id,
                bronze_processing_time,
                ingestion_date,
                ingestion_hour
            FROM read_parquet('{parquet_path}')
            ORDER BY bronze_processing_time DESC
            LIMIT 10
        """).fetchdf()

        print(df.to_string(index=False))

        # Data quality summary
        print("\n📈 Data Quality Summary:")
        quality = con.execute(f"""
            SELECT
                COUNT(*) as total_records,
                COUNT(DISTINCT ingestion_date) as days_of_data,
                COUNT(CASE WHEN lateness_ms > 0 THEN 1 END) as late_records,
                ROUND(AVG(dist), 2) as avg_distance,
                MIN(ts) as earliest_event,
                MAX(ts) as latest_event
            FROM read_parquet('{parquet_path}')
        """).fetchdf()

        print(quality.to_string(index=False))

        # Records by ingestion hour
        print("\n⏰ Records by Ingestion Hour (last 5 hours):")
        hourly = con.execute(f"""
            SELECT
                ingestion_date,
                ingestion_hour,
                COUNT(*) as record_count,
                COUNT(DISTINCT pu) as unique_pickup_zones,
                ROUND(AVG(dist), 2) as avg_distance
            FROM read_parquet('{parquet_path}')
            GROUP BY ingestion_date, ingestion_hour
            ORDER BY ingestion_date DESC, ingestion_hour DESC
            LIMIT 5
        """).fetchdf()

        print(hourly.to_string(index=False))

    except Exception as e:
        print(f"❌ Error querying trips: {e}")

def query_bronze_weather(con):
    """Query the bronze weather table"""
    table_path = "s3://lakehouse/iceberg/bronze/weather"

    print("\n" + "="*60)
    print("🌤️  BRONZE LAYER: WEATHER DATA")
    print("="*60)

    if not check_table_exists(con, table_path):
        print(f"❌ Table not found: {table_path}")
        print("   Run: make bronze-weather")
        return

    try:
        # Read all parquet files for this table
        parquet_path = f"{table_path}/data/**/*.parquet"

        # Get total row count
        count = con.execute(f"""
            SELECT COUNT(*) as total_rows
            FROM read_parquet('{parquet_path}')
        """).fetchone()[0]

        print(f"✅ Total records: {count:,}")

        if count == 0:
            print("   No data yet. Wait for producers to send data.")
            return

        # Recent weather observations
        print("\n🌡️  Recent Weather Observations:")
        df = con.execute(f"""
            SELECT
                zone_id,
                temperature,
                precipitation,
                wind_speed,
                visibility,
                conditions,
                station_id,
                observation_time,
                bronze_processing_time
            FROM read_parquet('{parquet_path}')
            ORDER BY bronze_processing_time DESC
            LIMIT 10
        """).fetchdf()

        print(df.to_string(index=False))

        # Weather statistics by zone
        print("\n📊 Weather Statistics (sample zones):")
        stats = con.execute(f"""
            SELECT
                zone_id,
                COUNT(*) as observations,
                ROUND(AVG(temperature), 1) as avg_temp,
                ROUND(MIN(temperature), 1) as min_temp,
                ROUND(MAX(temperature), 1) as max_temp,
                ROUND(AVG(wind_speed), 1) as avg_wind
            FROM read_parquet('{parquet_path}')
            WHERE zone_id <= 10
            GROUP BY zone_id
            ORDER BY zone_id
            LIMIT 10
        """).fetchdf()

        print(stats.to_string(index=False))

        # Weather conditions distribution
        print("\n☁️  Weather Conditions Distribution:")
        conditions = con.execute(f"""
            SELECT
                conditions,
                COUNT(*) as count,
                ROUND(AVG(temperature), 1) as avg_temp,
                ROUND(AVG(precipitation), 2) as avg_precip
            FROM read_parquet('{parquet_path}')
            GROUP BY conditions
            ORDER BY count DESC
            LIMIT 5
        """).fetchdf()

        print(conditions.to_string(index=False))

    except Exception as e:
        print(f"❌ Error querying weather: {e}")

def query_bronze_events(con):
    """Query the bronze events table"""
    table_path = "s3://lakehouse/iceberg/bronze/events"

    print("\n" + "="*60)
    print("🎭 BRONZE LAYER: EVENTS DATA")
    print("="*60)

    if not check_table_exists(con, table_path):
        print(f"❌ Table not found: {table_path}")
        print("   Run: make bronze-events")
        return

    try:
        # Read all parquet files for this table
        parquet_path = f"{table_path}/data/**/*.parquet"

        # Get total row count
        count = con.execute(f"""
            SELECT COUNT(*) as total_rows
            FROM read_parquet('{parquet_path}')
        """).fetchone()[0]

        print(f"✅ Total records: {count:,}")

        if count == 0:
            print("   No data yet. Wait for producers to send data.")
            return

        # Recent events
        print("\n🎪 Recent Events:")
        df = con.execute(f"""
            SELECT
                event_id,
                event_type,
                venue_name,
                expected_attendance,
                start_time,
                end_time,
                bronze_processing_time
            FROM read_parquet('{parquet_path}')
            ORDER BY bronze_processing_time DESC
            LIMIT 10
        """).fetchdf()

        print(df.to_string(index=False))

        # Event type distribution
        print("\n📈 Event Type Distribution:")
        types = con.execute(f"""
            SELECT
                event_type,
                COUNT(*) as event_count,
                SUM(expected_attendance) as total_expected_attendance,
                ROUND(AVG(expected_attendance), 0) as avg_attendance
            FROM read_parquet('{parquet_path}')
            GROUP BY event_type
            ORDER BY event_count DESC
        """).fetchdf()

        print(types.to_string(index=False))

        # Top venues by events
        print("\n🏟️  Top Venues by Event Count:")
        venues = con.execute(f"""
            SELECT
                venue_name,
                COUNT(*) as event_count,
                COUNT(DISTINCT event_type) as event_types,
                SUM(expected_attendance) as total_expected_attendance
            FROM read_parquet('{parquet_path}')
            GROUP BY venue_name
            ORDER BY event_count DESC
            LIMIT 5
        """).fetchdf()

        print(venues.to_string(index=False))

    except Exception as e:
        print(f"❌ Error querying events: {e}")

def explore_bronze_tables(con):
    """Explore available bronze tables in MinIO"""
    print("\n" + "="*60)
    print("🗂️  BRONZE LAYER EXPLORATION")
    print("="*60)

    try:
        # Try to find any parquet files in bronze layer
        files = con.execute("""
            SELECT * FROM glob('s3://lakehouse/iceberg/bronze/**/*.parquet')
            LIMIT 5
        """).fetchall()

        if files:
            print("✅ Found Bronze Layer Parquet files:")
            for f in files:
                print(f"   - {f[0]}")
        else:
            print("⚠️  No Parquet files found in bronze layer")
            print("   Make sure bronze consumers are running")

    except Exception as e:
        print(f"❌ Could not explore bronze layer: {e}")


def main():
    """Main function to query Bronze Layer Iceberg tables"""

    print("\n" + "🦆" * 30)
    print("🦆 DuckDB Bronze Layer Query Tool")
    print("🦆" * 30)
    print(f"\n⏰ Timestamp: {datetime.now()}")

    # Initialize DuckDB
    print("\n🔧 Initializing DuckDB with S3 support...")
    con = setup_duckdb_iceberg()
    print("✅ DuckDB ready")

    # Explore bronze tables
    explore_bronze_tables(con)

    # Query each bronze table
    query_bronze_trips(con)
    query_bronze_weather(con)
    query_bronze_events(con)

    # Summary
    print("\n" + "="*60)
    print("✨ BRONZE LAYER QUERY COMPLETE")
    print("="*60)

    # Close connection
    con.close()

if __name__ == "__main__":
    main()