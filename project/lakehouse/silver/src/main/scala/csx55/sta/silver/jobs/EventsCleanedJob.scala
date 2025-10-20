package csx55.sta.silver.jobs

import com.amazon.deequ.checks.{Check, CheckLevel}
import csx55.sta.silver.config.BusinessRules
import csx55.sta.streaming.config.StreamConfig
import org.slf4j.LoggerFactory

class EventsCleanedJob(config: StreamConfig)
    extends AbstractSilverJob(config, config.getSilverEventsCleanedConfig()) {

  override protected def getSqlFilePath(): String = {
    "sql/events_cleaned.sql"
  }

  override protected def getDeequChecks(): Check = {
    Check(CheckLevel.Error, "Events Data Quality Checks")
      .isComplete("timestamp")
      .isComplete("location_id")
      .isComplete("event_type")    // SPORTS, CONCERT, CONFERENCE, FESTIVAL
      .isComplete("start_time")    // Event start timestamp
      .isComplete("end_time")      // Event end timestamp
      .hasMin("attendance_estimate", _ >= 1.0)
      .hasMax("attendance_estimate", _ <= 100000.0)
  }

  override protected def getBusinessRuleReplacements(): Map[String, Any] = {
    Map(
      // Major event identification thresholds
      "MAJOR_EVENT_ATTENDANCE_THRESHOLD" -> BusinessRules.MAJOR_EVENT_ATTENDANCE_THRESHOLD,
      "MAJOR_SPORTS_CONCERT_THRESHOLD" -> BusinessRules.MAJOR_SPORTS_CONCERT_THRESHOLD,

      // Event size categorization (small, medium, large, massive)
      "EVENT_SMALL_MAX" -> BusinessRules.EVENT_SMALL_MAX,
      "EVENT_MEDIUM_MAX" -> BusinessRules.EVENT_MEDIUM_MAX,
      "EVENT_LARGE_MAX" -> BusinessRules.EVENT_LARGE_MAX,

      // Event duration categorization (brief, moderate, extended, multi-day)
      "DURATION_BRIEF_MAX" -> BusinessRules.DURATION_BRIEF_MAX,
      "DURATION_MODERATE_MAX" -> BusinessRules.DURATION_MODERATE_MAX,
      "DURATION_EXTENDED_MAX" -> BusinessRules.DURATION_EXTENDED_MAX
    )
  }

  override protected def getDeduplicationColumns(): Array[String] = {
    Array("timestamp", "location_id", "event_type", "start_time")
  }
}

object EventsCleanedJob {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def main(args: Array[String]): Unit = {
    logger.info("========================================")
    logger.info("Silver Layer: Events Cleaned Job")
    logger.info("========================================")

    try {
      val config = new StreamConfig()
      val job = new EventsCleanedJob(config)
      job.run()

    } catch {
      case e: Exception =>
        logger.error("Failed to run EventsCleanedJob", e)
        System.exit(1)
    }
  }
}
