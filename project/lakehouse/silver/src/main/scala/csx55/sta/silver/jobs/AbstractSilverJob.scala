package csx55.sta.silver.jobs

import com.amazon.deequ.checks.Check
import csx55.sta.silver.batch.BatchProcessor
import csx55.sta.silver.monitoring.QualityMetricsRecorder
import csx55.sta.silver.quarantine.QuarantineHandler
import csx55.sta.silver.utils.SqlTemplateProcessor
import csx55.sta.silver.validation.{DataQualityValidator, DeequValidator}
import csx55.sta.streaming.base.BaseStreamingJob
import csx55.sta.streaming.config.StreamConfig
import org.apache.spark.sql.{Dataset, Row}
import org.apache.spark.sql.streaming.{StreamingQuery, Trigger}

import scala.io.Source

abstract class AbstractSilverJob(
  config: StreamConfig,
  protected val streamConfig: StreamConfig.SilverStreamConfig
) extends BaseStreamingJob(config) {

  private val MONITORING_TABLE = "lakehouse.monitoring.silver_quality_metrics"

  private lazy val validator: DataQualityValidator = createValidator()
  private lazy val quarantineHandler = new QuarantineHandler(spark)
  private lazy val metricsRecorder = new QualityMetricsRecorder(spark, MONITORING_TABLE)
  private lazy val batchProcessor = new BatchProcessor(
    validator,
    quarantineHandler,
    metricsRecorder,
    streamConfig.targetTable,
    getJobName()
  )

  protected def getSqlFilePath(): String

  protected def getDeequChecks(): Check

  protected def getBusinessRuleReplacements(): Map[String, Any]

  protected def createValidator(): DataQualityValidator = {
    new DeequValidator(getDeequChecks())
  }

  override protected def getJobName(): String = {
    getClass.getSimpleName
  }

  override protected def readStream(): Dataset[Row] = {
    throw new UnsupportedOperationException("Deequ pattern uses custom run() method")
  }

  override protected def transform(input: Dataset[Row]): Dataset[Row] = {
    throw new UnsupportedOperationException(s"Transformation in SQL file: ${getSqlFilePath()}")
  }

  override protected def writeStream(output: Dataset[Row]): StreamingQuery = {
    throw new UnsupportedOperationException("Deequ pattern uses foreachBatch for validation")
  }

  override protected def getNamespace(): String = {
    "lakehouse.silver"
  }

  override protected def initialize(): Unit = {
    super.initialize()
    verifySourceTableExists()
    ensureMonitoringTableExists()
  }

  private def verifySourceTableExists(): Unit = {
    try {
      spark.table(streamConfig.sourceTable)
      logger.debug("Verified source table: {}", streamConfig.sourceTable)
    } catch {
      case e: Exception =>
        throw new RuntimeException(
          s"Source table ${streamConfig.sourceTable} does not exist. Ensure Bronze layer is running first.",
          e
        )
    }
  }

  private def ensureMonitoringTableExists(): Unit = {
    try {
      spark.table(MONITORING_TABLE)
      logger.debug("Monitoring table exists: {}", MONITORING_TABLE)
    } catch {
      case _: Exception =>
        logger.warn("Monitoring table not found. It will be created by MonitoringSetup job.")
    }
  }

  override def run(): Unit = {
    logger.info("Starting {}: {} → {}", getClass.getSimpleName, streamConfig.sourceTable, streamConfig.targetTable)

    this.spark = createSparkSession()
    initialize()

    val sqlTemplate = loadSqlFromResources(getSqlFilePath())
    val sql = injectBusinessRules(sqlTemplate)
    logger.debug("Loaded SQL transformation from: {}", getSqlFilePath())

    val bronzeStream = spark.readStream
      .format("iceberg")
      .table(streamConfig.sourceTable)

    val tempViewName = getTempViewName()
    bronzeStream.createOrReplaceTempView(tempViewName)
    logger.debug("Created temp view: {}", tempViewName)

    val silverStream = spark.sql(sql)

    val query = silverStream
      .writeStream
      .foreachBatch { (batchDF: Dataset[Row], batchId: Long) =>
        batchProcessor.process(batchDF, batchId)
      }
      .trigger(Trigger.ProcessingTime(config.getTriggerInterval))
      .option("checkpointLocation", streamConfig.checkpointPath)
      .start()

    query.awaitTermination()
  }

  private def getTempViewName(): String = {
    streamConfig.sourceTable.replace(".", "_")
  }

  private def injectBusinessRules(sqlTemplate: String): String = {
    SqlTemplateProcessor.inject(sqlTemplate, getBusinessRuleReplacements())
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
