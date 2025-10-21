package csx55.sta.gold.jobs

import csx55.sta.streaming.base.BaseStreamingJob
import csx55.sta.streaming.config.StreamConfig
import csx55.sta.streaming.config.StreamConfig.GoldStreamConfig
import org.apache.spark.sql.{Dataset, Row}
import org.apache.spark.sql.streaming.{StreamingQuery, Trigger}

import scala.io.Source

abstract class AbstractGoldJob(
  config: StreamConfig,
  protected val streamConfig: GoldStreamConfig
) extends BaseStreamingJob(config) {

  protected def getSqlFilePath(): String

  override protected def getJobName(): String = {
    getClass.getSimpleName
  }

  override protected def readStream(): Dataset[Row] = {
    logger.info("Reading stream from: {}", streamConfig.sourceTable)

    spark.readStream
      .format("iceberg")
      .table(streamConfig.sourceTable)
  }

  override protected def transform(input: Dataset[Row]): Dataset[Row] = {
    val sqlTemplate = loadSqlFromResources(getSqlFilePath())
    logger.debug("Loaded SQL transformation from: {}", getSqlFilePath())

    // Add watermark for windowed aggregations in append mode
    // Windows are considered complete 10 minutes after the latest event timestamp
    val watermarkedInput = input.withWatermark("timestamp", "10 minutes")
    logger.debug("Added watermark: 10 minutes on timestamp column")

    // Create temp view from watermarked stream
    val tempViewName = getTempViewName()
    watermarkedInput.createOrReplaceTempView(tempViewName)
    logger.debug("Created temp view: {}", tempViewName)

    // Execute aggregation SQL and return
    spark.sql(sqlTemplate)
  }

  override protected def writeStream(output: Dataset[Row]): StreamingQuery = {
    logger.info("Writing stream to: {}", streamConfig.targetTable)

    output.writeStream
      .format("iceberg")
      .outputMode("append")  // Append mode - each closed window produces a new row
      .option("checkpointLocation", streamConfig.checkpointPath)
      .trigger(Trigger.ProcessingTime("30 seconds"))
      .toTable(streamConfig.targetTable)
  }

  override protected def getNamespace(): String = {
    "lakehouse.gold"
  }

  override protected def initialize(): Unit = {
    super.initialize()
    verifySourceTableExists()
    verifyTargetTableExists()
  }

  private def verifySourceTableExists(): Unit = {
    try {
      spark.table(streamConfig.sourceTable)
      logger.debug("Verified source table: {}", streamConfig.sourceTable)
    } catch {
      case e: Exception =>
        throw new RuntimeException(
          s"Source table ${streamConfig.sourceTable} does not exist. Ensure Silver layer is running first.",
          e
        )
    }
  }

  private def verifyTargetTableExists(): Unit = {
    try {
      spark.table(streamConfig.targetTable)
      logger.debug("Verified target table: {}", streamConfig.targetTable)
    } catch {
      case e: Exception =>
        throw new RuntimeException(
          s"Target table ${streamConfig.targetTable} does not exist. Run gold-table-setup first.",
          e
        )
    }
  }

  private def getTempViewName(): String = {
    streamConfig.sourceTable.replace(".", "_")
  }

  protected def loadSqlFromResources(resourcePath: String): String = {
    try {
      logger.debug("Loading SQL from: {}", resourcePath)

      val inputStream = getClass.getClassLoader.getResourceAsStream(resourcePath)

      if (inputStream == null) {
        throw new RuntimeException(s"SQL file not found in resources: $resourcePath")
      }

      val sql = Source.fromInputStream(inputStream, "UTF-8").mkString

      inputStream.close()

      if (sql.trim.isEmpty) {
        throw new RuntimeException(s"SQL file is empty: $resourcePath")
      }

      sql

    } catch {
      case e: Exception =>
        throw new RuntimeException(s"Failed to load SQL file: $resourcePath", e)
    }
  }
}
