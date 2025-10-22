package csx55.sta.silver.batch

import csx55.sta.silver.monitoring.QualityMetricsRecorder
import csx55.sta.silver.quarantine.QuarantineHandler
import csx55.sta.silver.validation.DataQualityValidator
import org.apache.spark.sql.{Dataset, Row, SaveMode}
import org.slf4j.LoggerFactory

class BatchProcessor(
  validator: DataQualityValidator,
  quarantineHandler: QuarantineHandler,
  metricsRecorder: QualityMetricsRecorder,
  targetTable: String,
  jobName: String
) {

  private val logger = LoggerFactory.getLogger(getClass)

  def process(batch: Dataset[Row], batchId: Long): Unit = {
    if (batch.isEmpty) {
      return
    }

    val recordCount = batch.count()

    try {
      val result = validator.validate(batch)

      if (result.passed) {
        handleSuccessfulBatch(batch, batchId, result)
      } else {
        handleFailedBatch(batch, batchId, result)
        logger.error("Batch {}: {} records failed validation and quarantined", batchId, recordCount)
      }

    } catch {
      case e: Exception =>
        logger.error("Failed to process batch {}", batchId, e)
        throw new RuntimeException(s"Batch processing failed for batch $batchId", e)
    }
  }

  private def handleSuccessfulBatch(
    batch: Dataset[Row],
    batchId: Long,
    result: csx55.sta.silver.validation.ValidationResult
  ): Unit = {
    batch.write
      .format("iceberg")
      .mode(SaveMode.Append)
      .saveAsTable(targetTable)

    metricsRecorder.record(
      batchId = batchId,
      tableName = targetTable,
      jobName = jobName,
      recordCount = batch.count(),
      result = result,
      quarantined = false
    )
  }

  private def handleFailedBatch(
    batch: Dataset[Row],
    batchId: Long,
    result: csx55.sta.silver.validation.ValidationResult
  ): Unit = {
    logger.error("Failure details: {}", result.getFailuresOrElse("Unknown"))

    quarantineHandler.quarantineBatch(
      batch = batch,
      batchId = batchId,
      targetTable = targetTable,
      failureReason = result.getFailuresOrElse("Validation failed"),
      validationStatus = result.status
    )

    metricsRecorder.record(
      batchId = batchId,
      tableName = targetTable,
      jobName = jobName,
      recordCount = batch.count(),
      result = result,
      quarantined = true
    )
  }
}

object BatchProcessor {

  def apply(
    validator: DataQualityValidator,
    quarantineHandler: QuarantineHandler,
    metricsRecorder: QualityMetricsRecorder,
    targetTable: String,
    jobName: String
  ): BatchProcessor = {
    new BatchProcessor(validator, quarantineHandler, metricsRecorder, targetTable, jobName)
  }
}
