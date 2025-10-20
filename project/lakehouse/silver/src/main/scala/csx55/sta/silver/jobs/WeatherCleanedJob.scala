package csx55.sta.silver.jobs

import com.amazon.deequ.checks.{Check, CheckLevel}
import csx55.sta.silver.config.BusinessRules
import csx55.sta.streaming.config.StreamConfig
import org.slf4j.LoggerFactory

class WeatherCleanedJob(config: StreamConfig)
    extends AbstractSilverJob(config, config.getSilverWeatherCleanedConfig()) {

  override protected def getSqlFilePath(): String = {
    "sql/weather_cleaned.sql"
  }

  override protected def getDeequChecks(): Check = {
    Check(CheckLevel.Error, "Weather Data Quality Checks")
      .isComplete("timestamp")
      .isComplete("location_id")
      .isComplete("condition")
      .hasMin("temperature", _ >= -20.0)
      .hasMax("temperature", _ <= 120.0)
      .hasMin("precipitation", _ >= 0.0)
      .hasMax("precipitation", _ <= 10.0)
      .hasMin("wind_speed", _ >= 0.0)
      .hasMax("wind_speed", _ >= 100.0)
  }

  override protected def getBusinessRuleReplacements(): Map[String, Any] = {
    Map(
      "SEVERE_PRECIPITATION_THRESHOLD" -> BusinessRules.SEVERE_PRECIPITATION_THRESHOLD,
      "SEVERE_WIND_SPEED_THRESHOLD" -> BusinessRules.SEVERE_WIND_SPEED_THRESHOLD,
      "TEMP_FREEZING_MAX" -> BusinessRules.TEMP_FREEZING_MAX,
      "TEMP_COLD_MAX" -> BusinessRules.TEMP_COLD_MAX,
      "TEMP_MILD_MAX" -> BusinessRules.TEMP_MILD_MAX,
      "TEMP_WARM_MAX" -> BusinessRules.TEMP_WARM_MAX,
      "PRECIP_LIGHT_MAX" -> BusinessRules.PRECIP_LIGHT_MAX,
      "PRECIP_MODERATE_MAX" -> BusinessRules.PRECIP_MODERATE_MAX,
      "WIND_CALM_MAX" -> BusinessRules.WIND_CALM_MAX,
      "WIND_BREEZY_MAX" -> BusinessRules.WIND_BREEZY_MAX,
      "WIND_WINDY_MAX" -> BusinessRules.WIND_WINDY_MAX,
      "HIGH_IMPACT_WIND_THRESHOLD" -> BusinessRules.HIGH_IMPACT_WIND_THRESHOLD,
      "MEDIUM_IMPACT_PRECIP_THRESHOLD" -> BusinessRules.MEDIUM_IMPACT_PRECIP_THRESHOLD,
      "EXTREME_COLD_THRESHOLD" -> BusinessRules.EXTREME_COLD_THRESHOLD,
      "EXTREME_HOT_THRESHOLD" -> BusinessRules.EXTREME_HOT_THRESHOLD
    )
  }

  override protected def getDeduplicationColumns(): Array[String] = {
    Array("timestamp", "location_id")
  }
}

object WeatherCleanedJob {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def main(args: Array[String]): Unit = {
    logger.info("========================================")
    logger.info("Silver Layer: Weather Cleaned Job")
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
