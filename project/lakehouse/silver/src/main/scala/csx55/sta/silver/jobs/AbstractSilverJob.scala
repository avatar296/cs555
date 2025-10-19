package csx55.sta.silver.jobs

import com.amazon.deequ.{VerificationResult, VerificationSuite}
import com.amazon.deequ.checks.{Check, CheckStatus}
import csx55.sta.silver.config.BusinessRules
import csx55.sta.streaming.base.BaseStreamingJob
import csx55.sta.streaming.config.StreamConfig
import org.apache.spark.sql.{Dataset, Row, SaveMode}
import org.apache.spark.sql.streaming.{StreamingQuery, Trigger}

import java.sql.Timestamp
import scala.io.Source

/**
 * Abstract base class for Silver Layer streaming jobs with Deequ validation.
 *
 * <p><b>DEEQU-BASED PATTERN (Teaching POC)</b></p>
 *
 * <p>Philosophy: SQL for transformation, Deequ for validation + monitoring.
 * This class:
 * <ol>
 * <li>Loads SQL from resources (transformation logic)</li>
 * <li>Executes SQL transformation</li>
 * <li>Validates each micro-batch with Deequ</li>
 * <li>Stores quality metrics for monitoring</li>
 * <li>Writes results to Silver Iceberg tables</li>
 * </ol>
 *
 * @see csx55.sta.streaming.base.BaseStreamingJob
 */
abstract class AbstractSilverJob(
  config: StreamConfig,
  protected val streamConfig: StreamConfig.SilverStreamConfig
) extends BaseStreamingJob(config) {

  private val MONITORING_TABLE = "lakehouse.monitoring.silver_quality_metrics"

  /**
   * Returns the path to the SQL transformation file in resources.
   * Example: "sql/trips_cleaned.sql"
   */
  protected def getSqlFilePath(): String

  /**
   * Returns Deequ checks for data quality validation.
   * Subclasses define validation rules using Deequ's fluent API.
   */
  protected def getDeequChecks(): Check

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
      logger.info("✓ Verified source table exists: {}", streamConfig.sourceTable)
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
      logger.info("✓ Monitoring table exists: {}", MONITORING_TABLE)
    } catch {
      case _: Exception =>
        logger.warn("Monitoring table not found. It will be created by MonitoringSetup job.")
    }
  }

  override def run(): Unit = {
    logger.info("========================================")
    logger.info("Starting Silver Job (Deequ): {}", getClass.getSimpleName)
    logger.info("Source: {}", streamConfig.sourceTable)
    logger.info("Target: {}", streamConfig.targetTable)
    logger.info("SQL File: {}", getSqlFilePath())
    logger.info("Monitoring: {}", MONITORING_TABLE)
    logger.info("========================================")

    // Initialize Spark
    this.spark = createSparkSession()
    initialize()

    // Load SQL transformation template and inject business rules
    val sqlTemplate = loadSqlFromResources(getSqlFilePath())
    val sql = injectBusinessRules(sqlTemplate)
    logger.info("Loaded SQL transformation ({} characters)", sql.length)

    // Read from Bronze
    val bronzeStream = spark.readStream
      .format("iceberg")
      .table(streamConfig.sourceTable)

    // Create temp view with standardized name (used by SQL)
    val tempViewName = getTempViewName()
    bronzeStream.createOrReplaceTempView(tempViewName)
    logger.info("Created temp view: {}", tempViewName)

    val silverStream = spark.sql(sql)
    logger.info("Applied SQL transformation")

    // Write with Deequ validation
    logger.info("Starting streaming with Deequ validation")
    val query = silverStream
      .writeStream
      .foreachBatch { (batchDF: Dataset[Row], batchId: Long) =>
        processBatchWithDeequ(batchDF, batchId)
      }
      .trigger(Trigger.ProcessingTime(config.getTriggerInterval))
      .option("checkpointLocation", streamConfig.checkpointPath)
      .start()

    query.awaitTermination()
  }

  private def processBatchWithDeequ(batchDF: Dataset[Row], batchId: Long): Unit = {
    if (batchDF.isEmpty) {
      logger.info("Batch {} is empty, skipping", batchId)
      return
    }

    logger.info("========================================")
    logger.info("Processing batch {}: {} records", batchId, batchDF.count())

    try {
      // Run Deequ validation
      val result = new VerificationSuite()
        .onData(batchDF)
        .addCheck(getDeequChecks())
        .run()

      val allChecksPassed = result.status == CheckStatus.Success
      logger.info("Deequ validation: {}", result.status)

      // Conditional write based on validation result
      if (allChecksPassed) {
        // ✅ VALID BATCH → Write to Silver
        batchDF.write
          .format("iceberg")
          .mode(SaveMode.Append)
          .save(streamConfig.targetTable)

        logger.info("✓ Batch {} written to Silver: {} records", batchId, batchDF.count())

        // Store success metrics
        storeQualityMetrics(batchId, result, batchDF.count(), success = true, quarantined = false)

      } else {
        // ❌ INVALID BATCH → Quarantine
        val quarantineTable = getQuarantineTableName()
        val failureReason = getFailureReason(result)

        logger.error("✗ Batch {} FAILED validation - sending to quarantine", batchId)
        logger.error("Failure reason: {}", failureReason)

        // Add quarantine metadata columns
        import org.apache.spark.sql.functions._
        val quarantinedBatch = batchDF
          .withColumn("quarantine_timestamp", current_timestamp())
          .withColumn("batch_id_quarantine", lit(batchId))
          .withColumn("failure_reason", lit(failureReason))
          .withColumn("validation_status", lit(result.status.toString))

        // Write to quarantine table
        quarantinedBatch.write
          .format("iceberg")
          .mode(SaveMode.Append)
          .option("mergeSchema", "true")  // Allow schema evolution for new columns
          .saveAsTable(quarantineTable)

        logger.error("✗ Batch {} QUARANTINED: {} records → {}", batchId.asInstanceOf[Object], batchDF.count().asInstanceOf[Object], quarantineTable)

        // Store failure metrics
        storeQualityMetrics(batchId, result, batchDF.count(), success = false, quarantined = true)
      }

    } catch {
      case e: Exception =>
        logger.error("✗ Failed to process batch {}", batchId, e)
        throw new RuntimeException("Batch processing failed", e)
    }

    logger.info("========================================")
  }

  /**
   * Get the quarantine table name for this job's target table.
   * Converts: lakehouse.silver.trips_cleaned → lakehouse.quarantine.trips_quarantined
   */
  private def getQuarantineTableName(): String = {
    streamConfig.targetTable
      .replace(".silver.", ".quarantine.")
      .replace("_cleaned", "_quarantined")
  }

  /**
   * Extract failure reason from Deequ VerificationResult.
   * Provides detailed constraint violation information.
   */
  private def getFailureReason(result: VerificationResult): String = {
    import scala.jdk.CollectionConverters._
    import com.amazon.deequ.checks.CheckResult

    try {
      val checkResultsMap = result.checkResults.asInstanceOf[java.util.Map[com.amazon.deequ.checks.Check, CheckResult]]

      val failures = checkResultsMap.asScala
        .filter { case (_, checkResult) => checkResult.status != CheckStatus.Success }
        .flatMap { case (check, checkResult) =>
          checkResult.constraintResults.map { constraintResult =>
            val message = constraintResult.message.getOrElse("No message")
            val metric = constraintResult.metric.map(m => s" (value: ${m.value.toString})").getOrElse("")
            s"${constraintResult.constraint.toString}${metric}: ${message}"
          }
        }
        .mkString("; ")

      if (failures.nonEmpty) failures else "Validation failed but no specific constraint details available"
    } catch {
      case e: Exception =>
        logger.warn("Failed to extract failure details: {}", e.getMessage)
        s"Validation failed with status: ${result.status}"
    }
  }

  private def storeQualityMetrics(
    batchId: Long,
    result: VerificationResult,
    recordCount: Long,
    success: Boolean,
    quarantined: Boolean
  ): Unit = {
    try {
      val now = new Timestamp(System.currentTimeMillis())
      val tableName = streamConfig.targetTable
      val status = result.status.toString
      val constraintFailures = if (success) null else getFailureReason(result)

      // Create metrics row - need to convert Scala Seq to Java List for createDataFrame
      import scala.jdk.CollectionConverters._
      val metricsRow = spark.createDataFrame(
        Seq(
          org.apache.spark.sql.Row(
            now,
            tableName,
            getJobName(),
            batchId,
            recordCount,
            status,
            success,
            if (success) 1L else 0L, // Simple pass/fail counts
            if (success) 0L else 1L,
            constraintFailures,  // New: detailed failure reason
            quarantined           // New: quarantine flag
          )
        ).asJava,
        spark.table(MONITORING_TABLE).schema
      )

      metricsRow.write
        .format("iceberg")
        .mode(SaveMode.Append)
        .save(MONITORING_TABLE)

      logger.info("✓ Stored quality metrics for batch {}", batchId)

    } catch {
      case e: Exception =>
        logger.warn("Failed to store quality metrics: {}", e.getMessage)
    }
  }

  /**
   * Get the temp view name for this job's Bronze source table.
   * Converts: lakehouse.bronze.trips → lakehouse_bronze_trips
   */
  private def getTempViewName(): String = {
    streamConfig.sourceTable.replace(".", "_")
  }

  /**
   * Inject business rules into SQL template.
   *
   * Replaces placeholders like ${MORNING_RUSH_START} with actual values
   * from BusinessRules configuration object.
   *
   * <p><b>TEACHING NOTE:</b> This allows SQL to stay clean and readable
   * while business logic remains centralized and testable.</p>
   *
   * <p><b>Loan Origination Parallel:</b> This is like injecting regulatory
   * thresholds (DTI limits, credit score tiers, LTV ratios) into loan
   * decisioning SQL.</p>
   */
  private def injectBusinessRules(sqlTemplate: String): String = {
    sqlTemplate
      // Trips - Rush hour configuration
      .replace("${MORNING_RUSH_START}", BusinessRules.MORNING_RUSH_START.toString)
      .replace("${MORNING_RUSH_END}", BusinessRules.MORNING_RUSH_END.toString)
      .replace("${EVENING_RUSH_START}", BusinessRules.EVENING_RUSH_START.toString)
      .replace("${EVENING_RUSH_END}", BusinessRules.EVENING_RUSH_END.toString)

      // Trips - Distance thresholds
      .replace("${DISTANCE_SHORT_MAX}", BusinessRules.DISTANCE_SHORT_MAX.toString)
      .replace("${DISTANCE_MEDIUM_MAX}", BusinessRules.DISTANCE_MEDIUM_MAX.toString)
      .replace("${DISTANCE_LONG_MAX}", BusinessRules.DISTANCE_LONG_MAX.toString)

      // Trips - Fare thresholds
      .replace("${FARE_ECONOMY_MAX}", BusinessRules.FARE_ECONOMY_MAX.toString)
      .replace("${FARE_STANDARD_MAX}", BusinessRules.FARE_STANDARD_MAX.toString)
      .replace("${FARE_PREMIUM_MAX}", BusinessRules.FARE_PREMIUM_MAX.toString)

      // Weather - Severe weather thresholds
      .replace("${SEVERE_PRECIPITATION_THRESHOLD}", BusinessRules.SEVERE_PRECIPITATION_THRESHOLD.toString)
      .replace("${SEVERE_WIND_SPEED_THRESHOLD}", BusinessRules.SEVERE_WIND_SPEED_THRESHOLD.toString)

      // Weather - Temperature categories
      .replace("${TEMP_FREEZING_MAX}", BusinessRules.TEMP_FREEZING_MAX.toString)
      .replace("${TEMP_COLD_MAX}", BusinessRules.TEMP_COLD_MAX.toString)
      .replace("${TEMP_MILD_MAX}", BusinessRules.TEMP_MILD_MAX.toString)
      .replace("${TEMP_WARM_MAX}", BusinessRules.TEMP_WARM_MAX.toString)

      // Weather - Precipitation categories
      .replace("${PRECIP_LIGHT_MAX}", BusinessRules.PRECIP_LIGHT_MAX.toString)
      .replace("${PRECIP_MODERATE_MAX}", BusinessRules.PRECIP_MODERATE_MAX.toString)

      // Weather - Wind categories
      .replace("${WIND_CALM_MAX}", BusinessRules.WIND_CALM_MAX.toString)
      .replace("${WIND_BREEZY_MAX}", BusinessRules.WIND_BREEZY_MAX.toString)
      .replace("${WIND_WINDY_MAX}", BusinessRules.WIND_WINDY_MAX.toString)

      // Weather - Impact thresholds
      .replace("${HIGH_IMPACT_WIND_THRESHOLD}", BusinessRules.HIGH_IMPACT_WIND_THRESHOLD.toString)
      .replace("${MEDIUM_IMPACT_PRECIP_THRESHOLD}", BusinessRules.MEDIUM_IMPACT_PRECIP_THRESHOLD.toString)

      // Weather - Extreme temperature thresholds
      .replace("${EXTREME_COLD_THRESHOLD}", BusinessRules.EXTREME_COLD_THRESHOLD.toString)
      .replace("${EXTREME_HOT_THRESHOLD}", BusinessRules.EXTREME_HOT_THRESHOLD.toString)

      // Events - Attendance thresholds
      .replace("${MAJOR_EVENT_ATTENDANCE_THRESHOLD}", BusinessRules.MAJOR_EVENT_ATTENDANCE_THRESHOLD.toString)
      .replace("${MAJOR_SPORTS_CONCERT_THRESHOLD}", BusinessRules.MAJOR_SPORTS_CONCERT_THRESHOLD.toString)

      // Events - Event size categories
      .replace("${EVENT_SMALL_MAX}", BusinessRules.EVENT_SMALL_MAX.toString)
      .replace("${EVENT_MEDIUM_MAX}", BusinessRules.EVENT_MEDIUM_MAX.toString)
      .replace("${EVENT_LARGE_MAX}", BusinessRules.EVENT_LARGE_MAX.toString)

      // Events - Duration categories
      .replace("${DURATION_BRIEF_MAX}", BusinessRules.DURATION_BRIEF_MAX.toString)
      .replace("${DURATION_MODERATE_MAX}", BusinessRules.DURATION_MODERATE_MAX.toString)
      .replace("${DURATION_EXTENDED_MAX}", BusinessRules.DURATION_EXTENDED_MAX.toString)
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
