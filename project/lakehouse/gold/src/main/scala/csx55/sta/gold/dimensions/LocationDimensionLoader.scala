package csx55.sta.gold.dimensions

import csx55.sta.streaming.config.StreamConfig
import csx55.sta.streaming.factory.IcebergSessionBuilder
import org.apache.spark.sql.{SaveMode, SparkSession}
import org.apache.spark.sql.functions._
import org.slf4j.LoggerFactory

object LocationDimensionLoader {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private val SOURCE_TABLE = "lakehouse.silver.trips_cleaned"
  private val TARGET_TABLE = "lakehouse.gold.location_dim"

  def main(args: Array[String]): Unit = {
    logger.info("========================================")
    logger.info("Gold Layer: Location Dimension Loader")
    logger.info("========================================")

    val config = new StreamConfig()
    val spark = IcebergSessionBuilder.createSession("LocationDimensionLoader", config)

    try {
      loadLocationDimension(spark)
      logger.info("Location dimension loaded successfully")
    } catch {
      case e: Exception =>
        logger.error("Failed to load location dimension", e)
        System.exit(1)
    } finally {
      spark.stop()
    }
  }

  private def loadLocationDimension(spark: SparkSession): Unit = {
    import spark.implicits._

    logger.info(s"Extracting distinct locations from $SOURCE_TABLE")

    // Read trips to extract distinct location IDs
    val tripsDF = spark.read
      .format("iceberg")
      .table(SOURCE_TABLE)

    // Get distinct pickup and dropoff locations
    val pickupLocations = tripsDF.select($"pickup_location_id".as("location_id")).distinct()
    val dropoffLocations = tripsDF.select($"dropoff_location_id".as("location_id")).distinct()

    val allLocations = pickupLocations.union(dropoffLocations).distinct()

    logger.info(s"Found ${allLocations.count()} distinct locations")

    // Create location dimension with placeholder values
    // Note: In a production system, you would join with TLC Taxi Zone lookup data
    val locationDimDF = allLocations
      .withColumn("zone_name", concat(lit("Zone_"), $"location_id"))
      .withColumn("borough", lit("Unknown"))
      .withColumn("service_zone", lit("Unknown"))
      .orderBy($"location_id")

    logger.info(s"Writing ${locationDimDF.count()} records to $TARGET_TABLE")

    // Write to Iceberg table (overwrite mode for idempotency)
    locationDimDF.write
      .format("iceberg")
      .mode(SaveMode.Overwrite)
      .saveAsTable(TARGET_TABLE)

    logger.info(s"Location dimension loaded: ${locationDimDF.count()} records")

    // Show sample of loaded data
    logger.info("Sample locations:")
    locationDimDF.show(10, truncate = false)
  }
}
