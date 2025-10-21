package csx55.sta.gold.jobs

import csx55.sta.streaming.config.StreamConfig
import org.slf4j.LoggerFactory

class TripMetricsLiveJob(config: StreamConfig)
    extends AbstractGoldJob(config, config.getGoldTripMetricsLiveConfig()) {

  override protected def getSqlFilePath(): String = {
    "sql/trip_metrics_live.sql"
  }
}

object TripMetricsLiveJob {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def main(args: Array[String]): Unit = {
    logger.info("========================================")
    logger.info("Gold Layer: Trip Metrics Live Job")
    logger.info("========================================")

    try {
      val config = new StreamConfig()
      val job = new TripMetricsLiveJob(config)
      job.run()

    } catch {
      case e: Exception =>
        logger.error("Failed to run TripMetricsLiveJob", e)
        System.exit(1)
    }
  }
}
