package csx55.sta.gold

import csx55.sta.gold.dimensions.{LocationDimensionLoader, TimeDimensionLoader}
import csx55.sta.gold.jobs.TripMetricsLiveJob
import csx55.sta.streaming.config.StreamConfig
import org.slf4j.LoggerFactory

object GoldLayerApp {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def main(args: Array[String]): Unit = {
    logger.info("========================================")
    logger.info("Gold Layer Application")
    logger.info("========================================")

    if (args.isEmpty) {
      printUsage()
      System.exit(1)
    }

    val jobType = args(0)

    try {
      jobType match {
        case "trip-metrics-live" =>
          logger.info("Starting Trip Metrics Live streaming job")
          runTripMetricsLive()

        case "load-time-dim" =>
          logger.info("Loading time dimension")
          runTimeDimLoader()

        case "load-location-dim" =>
          logger.info("Loading location dimension")
          runLocationDimLoader()

        case _ =>
          logger.error(s"Unknown job type: $jobType")
          printUsage()
          System.exit(1)
      }

    } catch {
      case e: Exception =>
        logger.error(s"Gold layer job failed: $jobType", e)
        System.exit(1)
    }
  }

  private def runTripMetricsLive(): Unit = {
    val config = new StreamConfig()
    val job = new TripMetricsLiveJob(config)
    job.run()
  }

  private def runTimeDimLoader(): Unit = {
    TimeDimensionLoader.main(Array.empty)
  }

  private def runLocationDimLoader(): Unit = {
    LocationDimensionLoader.main(Array.empty)
  }

  private def printUsage(): Unit = {
    logger.info("Usage: GoldLayerApp <job-type>")
    logger.info("")
    logger.info("Available job types:")
    logger.info("  trip-metrics-live    - Streaming aggregation job (continuously updates trip metrics)")
    logger.info("  load-time-dim        - Load time dimension table (one-time batch job)")
    logger.info("  load-location-dim    - Load location dimension table (one-time batch job)")
    logger.info("")
    logger.info("Examples:")
    logger.info("  GoldLayerApp trip-metrics-live")
    logger.info("  GoldLayerApp load-time-dim")
  }
}
