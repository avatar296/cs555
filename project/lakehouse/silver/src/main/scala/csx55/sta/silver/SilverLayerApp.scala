package csx55.sta.silver

import csx55.sta.silver.jobs.{EventsCleanedJob, TripsCleanedJob, WeatherCleanedJob}
import csx55.sta.streaming.config.StreamConfig
import org.slf4j.LoggerFactory

/**
 * Main entry point for Silver Layer data quality jobs.
 *
 * <p>The Silver layer performs:
 * <ul>
 *   <li>Data cleaning and validation with Deequ</li>
 *   <li>SQL-based transformations</li>
 *   <li>Deduplication</li>
 *   <li>Feature engineering</li>
 *   <li>Quality metrics tracking</li>
 * </ul>
 *
 * <p>Usage: spark-submit silver.jar <job-name>
 *
 * <p>Available jobs:
 * <ul>
 *   <li>trips-cleaned: Validate taxi trip data (distance, fare, passengers)</li>
 *   <li>weather-cleaned: Validate weather data (temperature, precipitation, wind)</li>
 *   <li>events-cleaned: Validate special events data (attendance, times)</li>
 * </ul>
 */
object SilverLayerApp {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def main(args: Array[String]): Unit = {
    if (args.length == 0) {
      printUsage()
      System.exit(1)
    }

    val jobName = args(0)
    val config = new StreamConfig()

    logger.info("========================================")
    logger.info("Silver Layer Application")
    logger.info("Job: {}", jobName)
    logger.info("========================================")

    jobName.toLowerCase match {
      case "trips-cleaned" =>
        logger.info("Starting Trips Cleaned Job (Deequ validation)")
        new TripsCleanedJob(config).run()

      case "weather-cleaned" =>
        logger.info("Starting Weather Cleaned Job (Deequ validation)")
        new WeatherCleanedJob(config).run()

      case "events-cleaned" =>
        logger.info("Starting Events Cleaned Job (Deequ validation)")
        new EventsCleanedJob(config).run()

      case _ =>
        logger.error("Unknown job: {}", jobName)
        printUsage()
        System.exit(1)
    }
  }

  private def printUsage(): Unit = {
    println()
    println("=" * 60)
    println("Silver Layer Application - Data Quality with Deequ")
    println("=" * 60)
    println()
    println("Usage: spark-submit silver.jar <job-name>")
    println()
    println("Available jobs:")
    println()
    println("  trips-cleaned")
    println("    Clean and validate taxi trip data")
    println("    - Validates: trip_distance (0.1-200), fare_amount (2.50-1000)")
    println("    - Validates: passenger_count (1-6)")
    println("    - Source: lakehouse.bronze.trips")
    println("    - Target: lakehouse.silver.trips_cleaned")
    println()
    println("  weather-cleaned")
    println("    Clean and validate external weather data")
    println("    - Validates: temperature (-20 to 120°F)")
    println("    - Validates: precipitation (0-10\"), wind_speed (0-100 mph)")
    println("    - Source: lakehouse.bronze.weather")
    println("    - Target: lakehouse.silver.weather_cleaned")
    println()
    println("  events-cleaned")
    println("    Clean and validate special events data")
    println("    - Validates: attendance_estimate (1-100,000)")
    println("    - Validates: event time consistency (start < end)")
    println("    - Source: lakehouse.bronze.events")
    println("    - Target: lakehouse.silver.events_cleaned")
    println()
    println("=" * 60)
  }
}
