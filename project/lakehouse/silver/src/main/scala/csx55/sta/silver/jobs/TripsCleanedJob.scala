package csx55.sta.silver.jobs

import com.amazon.deequ.checks.{Check, CheckLevel}
import csx55.sta.silver.config.BusinessRules
import csx55.sta.streaming.config.StreamConfig
import org.slf4j.LoggerFactory

class TripsCleanedJob(config: StreamConfig)
    extends AbstractSilverJob(config, config.getSilverTripsCleanedConfig()) {

  override protected def getSqlFilePath(): String = {
    "sql/trips_cleaned.sql"
  }

  override protected def getDeequChecks(): Check = {
    Check(CheckLevel.Error, "Trips Data Quality Checks")
      .isComplete("timestamp")
      .isComplete("pickup_location_id")
      .isComplete("dropoff_location_id")
      .hasMin("trip_distance", _ >= 0.1)
      .hasMax("trip_distance", _ <= 200.0)
      .hasMin("fare_amount", _ >= 2.50)
      .hasMax("fare_amount", _ <= 1000.0)
      .hasMin("passenger_count", _ >= 1.0)
      .hasMax("passenger_count", _ <= 6.0)
  }
  override protected def getBusinessRuleReplacements(): Map[String, Any] = {
    Map(
      "MORNING_RUSH_START" -> BusinessRules.MORNING_RUSH_START,
      "MORNING_RUSH_END" -> BusinessRules.MORNING_RUSH_END,
      "EVENING_RUSH_START" -> BusinessRules.EVENING_RUSH_START,
      "EVENING_RUSH_END" -> BusinessRules.EVENING_RUSH_END,
      "DISTANCE_SHORT_MAX" -> BusinessRules.DISTANCE_SHORT_MAX,
      "DISTANCE_MEDIUM_MAX" -> BusinessRules.DISTANCE_MEDIUM_MAX,
      "DISTANCE_LONG_MAX" -> BusinessRules.DISTANCE_LONG_MAX,
      "FARE_ECONOMY_MAX" -> BusinessRules.FARE_ECONOMY_MAX,
      "FARE_STANDARD_MAX" -> BusinessRules.FARE_STANDARD_MAX,
      "FARE_PREMIUM_MAX" -> BusinessRules.FARE_PREMIUM_MAX
    )
  }
}

object TripsCleanedJob {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def main(args: Array[String]): Unit = {
    logger.info("========================================")
    logger.info("Silver Layer: Trips Cleaned Job")
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
