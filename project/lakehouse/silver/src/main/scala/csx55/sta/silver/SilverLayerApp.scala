package csx55.sta.silver

import csx55.sta.silver.jobs.{EventsCleanedJob, TripsCleanedJob, WeatherCleanedJob}
import csx55.sta.streaming.config.StreamConfig
import org.slf4j.LoggerFactory

object SilverLayerApp {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def main(args: Array[String]): Unit = {
    if (args.length == 0) {
      logger.error("No job name provided.")
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
        logger.info("Starting Trips Cleaned Job")
        new TripsCleanedJob(config).run()

      case "weather-cleaned" =>
        logger.info("Starting Weather Cleaned Job")
        new WeatherCleanedJob(config).run()

      case "events-cleaned" =>
        logger.info("Starting Events Cleaned Job")
        new EventsCleanedJob(config).run()

      case _ =>
        logger.error("Unknown job: {}.", jobName)
        System.exit(1)
    }
  }
}
