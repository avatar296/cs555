#!/usr/bin/env python3
"""
Zone Dimension Loader for Silver Layer

Loads NYC TLC zone reference data from CSV and enriches with computed fields.
Creates silver.zones table for joining with trips, weather, and events.

This is a one-time batch load (not streaming), as zone data is static.
"""

import sys
from pathlib import Path
from pyspark.sql import SparkSession, DataFrame
from pyspark.sql import functions as F
from pyspark.sql.types import StructType, StructField, IntegerType, StringType, DoubleType

# Add parent directory to path to import producers utilities
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "producers"))
from utils.zones import ZoneMapper


class ZonesLoader:
    """
    Loads and enriches NYC TLC zone dimension data.

    Reads from CSV, computes centroids and weather stations,
    writes to silver.zones Iceberg table.
    """

    def __init__(
        self,
        csv_path: str = None,
        target_table: str = "silver.zones",
        warehouse_path: str = "s3://lakehouse/iceberg"
    ):
        """
        Initialize zones loader.

        Args:
            csv_path: Path to taxi_zone_lookup.csv (auto-detected if None)
            target_table: Target Iceberg table
            warehouse_path: Iceberg warehouse location
        """
        # Auto-detect CSV path
        if csv_path is None:
            producers_path = Path(__file__).parent.parent.parent / "producers"
            csv_path = str(producers_path / "data" / "zones" / "taxi_zone_lookup.csv")

        self.csv_path = csv_path
        self.target_table = target_table
        self.warehouse_path = warehouse_path

        # Initialize zone mapper for centroid calculations
        self.zone_mapper = ZoneMapper()

        # Initialize Spark with Iceberg support
        self.spark = self._create_spark_session()

    def _create_spark_session(self) -> SparkSession:
        """Create Spark session with Iceberg catalog configured."""
        return (
            SparkSession.builder
            .appName("zones-loader")
            .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
            .config("spark.sql.catalog.local", "org.apache.iceberg.spark.SparkCatalog")
            .config("spark.sql.catalog.local.type", "hadoop")
            .config("spark.sql.catalog.local.warehouse", self.warehouse_path)
            .config("spark.sql.defaultCatalog", "local")
            .getOrCreate()
        )

    def load_zones(self) -> DataFrame:
        """
        Load zone data from CSV and enrich with computed fields.

        Returns:
            DataFrame with enriched zone data
        """
        print(f"Loading zones from {self.csv_path}...")

        # Read CSV
        df = self.spark.read.csv(
            self.csv_path,
            header=True,
            inferSchema=True
        )

        # Rename LocationID to zone_id for consistency
        df = df.withColumnRenamed("LocationID", "zone_id") \
               .withColumnRenamed("Zone", "zone_name") \
               .withColumnRenamed("Borough", "borough") \
               .withColumnRenamed("service_zone", "service_zone")

        # Compute centroids for each zone
        @F.udf(returnType=DoubleType())
        def get_centroid_lat(zone_id):
            lat, lon = self.zone_mapper.get_zone_centroid(zone_id)
            return float(lat)

        @F.udf(returnType=DoubleType())
        def get_centroid_lon(zone_id):
            lat, lon = self.zone_mapper.get_zone_centroid(zone_id)
            return float(lon)

        @F.udf(returnType=StringType())
        def get_weather_station(zone_id):
            return self.zone_mapper.get_weather_station_for_zone(zone_id)

        # Add computed fields
        df = (
            df
            .withColumn("centroid_lat", get_centroid_lat(F.col("zone_id")))
            .withColumn("centroid_lon", get_centroid_lon(F.col("zone_id")))
            .withColumn("weather_station_id", get_weather_station(F.col("zone_id")))
        )

        # Add metadata
        df = df.withColumn("loaded_at", F.current_timestamp())

        print(f"Loaded {df.count()} zones")

        return df

    def write_to_iceberg(self, df: DataFrame, mode: str = "overwrite"):
        """
        Write zone data to Iceberg table.

        Args:
            df: Zone DataFrame
            mode: Write mode (overwrite or append)
        """
        print(f"Writing zones to {self.target_table}...")

        # Create database if not exists
        catalog, db_table = self.target_table.split(".")
        self.spark.sql(f"CREATE DATABASE IF NOT EXISTS {db_table.split('.')[0]}")

        # Write to Iceberg
        df.writeTo(self.target_table) \
          .using("iceberg") \
          .tableProperty("write.format.default", "parquet") \
          .tableProperty("write.parquet.compression-codec", "snappy") \
          .partitionedBy("borough") \
          .createOrReplace()

        print(f"✓ Zones loaded to {self.target_table}")

        # Show sample
        self.spark.sql(f"SELECT * FROM {self.target_table} LIMIT 5").show(truncate=False)

    def run(self):
        """Run the full load process."""
        try:
            # Load and enrich zones
            zones_df = self.load_zones()

            # Show schema
            print("\nZone dimension schema:")
            zones_df.printSchema()

            # Write to Iceberg
            self.write_to_iceberg(zones_df)

            # Show stats by borough
            print("\nZones by borough:")
            self.spark.sql(f"""
                SELECT borough, COUNT(*) as zone_count
                FROM {self.target_table}
                GROUP BY borough
                ORDER BY zone_count DESC
            """).show()

            print(f"\n✓ Zone dimension loaded successfully")
            print(f"  Table: {self.target_table}")
            print(f"  Total zones: {zones_df.count()}")

        except Exception as e:
            print(f"❌ Error loading zones: {e}")
            raise
        finally:
            self.spark.stop()


def main():
    """Main entry point for zones loader."""
    import argparse

    parser = argparse.ArgumentParser(description="Load NYC TLC zone dimension")
    parser.add_argument("--csv-path", type=str, default=None,
                       help="Path to taxi_zone_lookup.csv (auto-detected if not provided)")
    parser.add_argument("--target-table", type=str, default="silver.zones",
                       help="Target Iceberg table")
    parser.add_argument("--warehouse", type=str, default="s3://lakehouse/iceberg",
                       help="Iceberg warehouse path")

    args = parser.parse_args()

    print("=" * 60)
    print("NYC TLC Zone Dimension Loader")
    print("=" * 60)

    loader = ZonesLoader(
        csv_path=args.csv_path,
        target_table=args.target_table,
        warehouse_path=args.warehouse
    )

    loader.run()


if __name__ == "__main__":
    main()
