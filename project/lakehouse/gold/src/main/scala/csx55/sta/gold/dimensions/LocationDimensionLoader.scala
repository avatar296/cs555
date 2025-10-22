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
    val config = new StreamConfig()
    val spark = IcebergSessionBuilder.createSession("LocationDimensionLoader", config)

    try {
      loadLocationDimension(spark)
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

    val tripsDF = spark.read
      .format("iceberg")
      .table(SOURCE_TABLE)

    val pickupLocations = tripsDF.select($"pickup_location_id".as("location_id")).distinct()
    val dropoffLocations = tripsDF.select($"dropoff_location_id".as("location_id")).distinct()

    val allLocations = pickupLocations.union(dropoffLocations).distinct()

    val locationDimDF = allLocations
      .withColumn("zone_name", concat(lit("Zone_"), $"location_id"))
      .withColumn("borough", lit("Unknown"))
      .withColumn("service_zone", lit("Unknown"))
      .orderBy($"location_id")

    locationDimDF.write
      .format("iceberg")
      .mode(SaveMode.Overwrite)
      .saveAsTable(TARGET_TABLE)
  }
}
