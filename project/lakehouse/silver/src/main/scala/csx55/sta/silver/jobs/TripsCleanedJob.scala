package csx55.sta.silver.jobs

import com.amazon.deequ.checks.{Check, CheckLevel}
import csx55.sta.streaming.config.StreamConfig
import org.slf4j.LoggerFactory

/**
 * Silver Layer Job: Trips Cleaning with Deequ Validation (Scala)
 *
 * <p><b>Purpose:</b> Transform and validate taxi trips data.</p>
 *
 * <p><b>DEEQU VALIDATION PATTERN:</b></p>
 * <p>This Scala implementation demonstrates proper Deequ usage:
 * <ul>
 *   <li><b>SQL</b>: Transformation logic (sql/trips_cleaned.sql)</li>
 *   <li><b>Deequ</b>: Data quality validation (this file)</li>
 * </ul>
 *
 * <p><b>Why Scala?</b> Deequ is a Scala library - using Scala gives us:
 * <ul>
 *   <li>Native lambda syntax: <code>_ >= 0.1</code> vs Java's verbose Function1 wrappers</li>
 *   <li>Clean fluent API: <code>.hasMin("col", _ >= 0.1).hasMax("col", _ <= 200)</code></li>
 *   <li>Better maintainability for data quality rules</li>
 * </ul>
 */
class TripsCleanedJob(config: StreamConfig)
    extends AbstractSilverJob(config, config.getSilverTripsCleanedConfig()) {

  // Note: logger is inherited from BaseStreamingJob (via AbstractSilverJob)

  override protected def getSqlFilePath(): String = {
    "sql/trips_cleaned.sql"
  }

  /**
   * Defines Deequ validation checks for trip data.
   *
   * <p><b>Validation Rules:</b>
   * <ul>
   *   <li><b>Completeness:</b> Critical fields must not be NULL</li>
   *   <li><b>Range validation:</b> Values within business-defined boundaries</li>
   *   <li><b>Business rules:</b> Automated quality enforcement</li>
   * </ul>
   *
   * <p><b>Loan Origination Parallel:</b>
   * <ul>
   *   <li>trip_distance (0.1-200) → loan_amount (1K-10M)</li>
   *   <li>fare_amount (2.50-1000) → interest_rate (0-20%)</li>
   *   <li>passenger_count (1-6) → DTI (0-1.0)</li>
   * </ul>
   */
  override protected def getDeequChecks(): Check = {
    Check(CheckLevel.Error, "Trips Data Quality Checks")
      // ====================================================================
      // COMPLETENESS CHECKS
      // Critical fields must always be present
      // ====================================================================
      .isComplete("timestamp")
      .isComplete("pickup_location_id")
      .isComplete("dropoff_location_id")

      // ====================================================================
      // RANGE VALIDATION: Trip Distance
      // Business rule: Valid taxi trips are 0.1 to 200 miles
      // - Minimum 0.1: Prevents zero/negative distances
      // - Maximum 200: Catches data errors (NYC to Boston ~215mi)
      //
      // Loan parallel: loan_amount ($1K - $10M)
      // ====================================================================
      .hasMin("trip_distance", _ >= 0.1)
      .hasMax("trip_distance", _ <= 200.0)

      // ====================================================================
      // RANGE VALIDATION: Fare Amount
      // Business rule: Valid fares are $2.50 to $1000
      // - Minimum $2.50: NYC taxi minimum fare
      // - Maximum $1000: Prevents extreme outliers
      //
      // Loan parallel: interest_rate (0% - 20%)
      // ====================================================================
      .hasMin("fare_amount", _ >= 2.50)
      .hasMax("fare_amount", _ <= 1000.0)

      // ====================================================================
      // RANGE VALIDATION: Passenger Count
      // Business rule: 1-6 passengers (standard taxi capacity)
      // - Minimum 1: At least one passenger
      // - Maximum 6: Standard taxi max capacity
      //
      // Loan parallel: DTI ratio (0.0 - 1.0)
      // ====================================================================
      .hasMin("passenger_count", _ >= 1.0)
      .hasMax("passenger_count", _ <= 6.0)
  }
}

/**
 * Main entry point for running TripsCleanedJob
 */
object TripsCleanedJob {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def main(args: Array[String]): Unit = {
    logger.info("========================================")
    logger.info("Silver Layer: Trips Cleaned Job (Deequ)")
    logger.info("========================================")

    try {
      val config = new StreamConfig()
      val job = new TripsCleanedJob(config)
      job.run()

    } catch {
      case e: Exception =>
        logger.error("Failed to run TripsCleanedJob", e)
        System.exit(1)
    }
  }
}
