package csx55.sta.gold

import csx55.sta.gold.dimensions.{LocationDimensionLoader, TimeDimensionLoader}
import csx55.sta.gold.jobs.{PipelineLatencyJob, TripMetricsLiveJob}
import csx55.sta.streaming.config.StreamConfig
import org.slf4j.LoggerFactory

object GoldLayerApp {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      printUsage()
      System.exit(1)
    }

    val jobType = args(0)

    try {
      jobType match {
        case "trip-metrics-live" =>
          runTripMetricsLive()

        case "pipeline-latency" =>
          runPipelineLatency()

        case "load-time-dim" =>
          runTimeDimLoader()

        case "load-location-dim" =>
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

  private def runPipelineLatency(): Unit = {
    val config = new StreamConfig()
    val job = new PipelineLatencyJob(config)
    job.run()
  }

  private def runTimeDimLoader(): Unit = {
    TimeDimensionLoader.main(Array.empty)
  }

  private def runLocationDimLoader(): Unit = {
    LocationDimensionLoader.main(Array.empty)
  }

  private def printUsage(): Unit = {
    println("Usage: GoldLayerApp <job-type>")
    println()
    println("Available job types:")
    println("  trip-metrics-live    - Streaming aggregation job (continuously updates trip metrics)")
    println("  pipeline-latency     - Streaming metrics job (tracks end-to-end latency and throughput)")
    println("  load-time-dim        - Load time dimension table (one-time batch job)")
    println("  load-location-dim    - Load location dimension table (one-time batch job)")
    println()
    println("Examples:")
    println("  GoldLayerApp trip-metrics-live")
    println("  GoldLayerApp pipeline-latency")
    println("  GoldLayerApp load-time-dim")
  }
}
