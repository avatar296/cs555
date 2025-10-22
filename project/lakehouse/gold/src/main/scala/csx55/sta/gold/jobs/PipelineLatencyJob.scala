package csx55.sta.gold.jobs

import csx55.sta.streaming.config.StreamConfig
import org.slf4j.LoggerFactory

class PipelineLatencyJob(config: StreamConfig)
    extends AbstractGoldJob(config, config.getGoldPipelineLatencyConfig()) {

  override protected def getSqlFilePath(): String = {
    "sql/pipeline_latency.sql"
  }
}

object PipelineLatencyJob {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def main(args: Array[String]): Unit = {
    try {
      val config = new StreamConfig()
      val job = new PipelineLatencyJob(config)
      job.run()

    } catch {
      case e: Exception =>
        logger.error("Failed to run PipelineLatencyJob", e)
        System.exit(1)
    }
  }
}
