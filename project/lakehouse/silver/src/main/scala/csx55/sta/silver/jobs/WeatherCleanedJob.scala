package csx55.sta.silver.jobs

import com.amazon.deequ.checks.{Check, CheckLevel}
import csx55.sta.streaming.config.StreamConfig
import org.slf4j.LoggerFactory

/**
 * Silver Layer Job: Weather Cleaning with Deequ Validation (Scala)
 *
 * <p><b>Purpose:</b> Transform and validate weather data from external sources.</p>
 *
 * <p><b>TEACHING NOTE - LOAN ORIGINATION PARALLEL:</b></p>
 * <p>Weather data is EXTERNAL data (third-party API, not directly controlled).
 * In loan originations, this is like:
 * <ul>
 *   <li>Credit bureau data (Experian, TransUnion, Equifax)</li>
 *   <li>Property appraisals (third-party appraisers)</li>
 *   <li>Employment verification (external HR systems)</li>
 * </ul>
 *
 * <p><b>Validation Strategy:</b> External data requires strict validation because
 * we don't control the source. Deequ enforces quality standards before Silver storage.
 */
class WeatherCleanedJob(config: StreamConfig)
    extends AbstractSilverJob(config, config.getSilverWeatherCleanedConfig()) {

  // Note: logger is inherited from BaseStreamingJob (via AbstractSilverJob)

  override protected def getSqlFilePath(): String = {
    "sql/weather_cleaned.sql"
  }

  /**
   * Defines Deequ validation checks for weather data.
   *
   * <p><b>Validation Rules:</b>
   * <ul>
   *   <li><b>Temperature range:</b> -20°F to 120°F (extreme weather boundaries)</li>
   *   <li><b>Precipitation range:</b> 0-10 inches (physically plausible)</li>
   *   <li><b>Wind speed range:</b> 0-100 mph (excludes hurricanes/tornadoes)</li>
   *   <li><b>Condition completeness:</b> Weather condition must be present</li>
   * </ul>
   *
   * <p><b>Loan Origination Parallel:</b>
   * <ul>
   *   <li>temperature (-20 to 120) → credit_score (300-850)</li>
   *   <li>precipitation (0-10) → debt_amount (0-1M)</li>
   *   <li>wind_speed (0-100) → income (0-10M)</li>
   *   <li>condition completeness → employment_status completeness</li>
   * </ul>
   */
  override protected def getDeequChecks(): Check = {
    Check(CheckLevel.Error, "Weather Data Quality Checks")
      // ====================================================================
      // COMPLETENESS CHECKS
      // Critical fields must always be present
      // ====================================================================
      .isComplete("timestamp")
      .isComplete("location_id")
      .isComplete("condition")  // Weather condition (CLEAR, RAIN, SNOW, etc.)

      // ====================================================================
      // RANGE VALIDATION: Temperature
      // Business rule: -20°F to 120°F (NYC weather extremes)
      // - Minimum -20°F: Historical NYC low (Feb 1934)
      // - Maximum 120°F: Extreme heat ceiling
      //
      // Loan parallel: credit_score (300-850)
      // ====================================================================
      .hasMin("temperature", _ >= -20.0)
      .hasMax("temperature", _ <= 120.0)

      // ====================================================================
      // RANGE VALIDATION: Precipitation
      // Business rule: 0-10 inches (daily precipitation cap)
      // - Minimum 0: No negative rainfall
      // - Maximum 10: Extreme weather events ceiling
      //
      // Loan parallel: monthly_debt_payment (0-100K)
      // ====================================================================
      .hasMin("precipitation", _ >= 0.0)
      .hasMax("precipitation", _ <= 10.0)

      // ====================================================================
      // RANGE VALIDATION: Wind Speed
      // Business rule: 0-100 mph (excludes extreme storms)
      // - Minimum 0: Calm conditions
      // - Maximum 100: Hurricane threshold
      //
      // Loan parallel: annual_income (0-10M)
      // ====================================================================
      .hasMin("wind_speed", _ >= 0.0)
      .hasMax("wind_speed", _ <= 100.0)
  }
}

/**
 * Main entry point for running WeatherCleanedJob
 */
object WeatherCleanedJob {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def main(args: Array[String]): Unit = {
    logger.info("========================================")
    logger.info("Silver Layer: Weather Cleaned Job (Deequ)")
    logger.info("========================================")

    try {
      val config = new StreamConfig()
      val job = new WeatherCleanedJob(config)
      job.run()

    } catch {
      case e: Exception =>
        logger.error("Failed to run WeatherCleanedJob", e)
        System.exit(1)
    }
  }
}
