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
    spark.readStream
      .format("iceberg")
      .table(streamConfig.sourceTable)
  }

  override protected def transform(input: Dataset[Row]): Dataset[Row] = {
    val sqlTemplate = loadSqlFromResources(getSqlFilePath())

    val watermarkedInput = input.withWatermark("timestamp", "5 minutes")

    val tempViewName = getTempViewName()
    watermarkedInput.createOrReplaceTempView(tempViewName)

    spark.sql(sqlTemplate)
  }

  override protected def writeStream(output: Dataset[Row]): StreamingQuery = {
    output.writeStream
      .format("iceberg")
      .outputMode("append")
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
