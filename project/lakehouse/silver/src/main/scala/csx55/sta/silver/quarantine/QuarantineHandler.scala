package csx55.sta.silver.quarantine

import org.apache.spark.sql.{Dataset, Row, SaveMode, SparkSession}
import org.apache.spark.sql.functions._
import org.slf4j.LoggerFactory

class QuarantineHandler(spark: SparkSession) {

  private val logger = LoggerFactory.getLogger(getClass)

  def quarantineBatch(
    batch: Dataset[Row],
    batchId: Long,
    targetTable: String,
    failureReason: String,
    validationStatus: String
  ): Unit = {
    val quarantineTable = getQuarantineTableName(targetTable)

    try {
      val quarantinedBatch = enrichWithQuarantineMetadata(
        batch,
        batchId,
        failureReason,
        validationStatus
      )

      quarantinedBatch.write
        .format("iceberg")
        .mode(SaveMode.Append)
        .option("mergeSchema", "true")
        .saveAsTable(quarantineTable)

    } catch {
      case e: Exception =>
        logger.error("Failed to quarantine batch {}", batchId, e)
        throw new RuntimeException(s"Quarantine operation failed for batch $batchId", e)
    }
  }

  private def enrichWithQuarantineMetadata(
    batch: Dataset[Row],
    batchId: Long,
    failureReason: String,
    validationStatus: String
  ): Dataset[Row] = {
    batch
      .withColumn("quarantine_timestamp", current_timestamp())
      .withColumn("batch_id_quarantine", lit(batchId))
      .withColumn("failure_reason", lit(failureReason))
      .withColumn("validation_status", lit(validationStatus))
  }

  def getQuarantineTableName(silverTable: String): String = {
    silverTable
      .replace(".silver.", ".quarantine.")
      .replace("_cleaned", "_quarantined")
  }

  def quarantineTableExists(silverTable: String): Boolean = {
    val quarantineTable = getQuarantineTableName(silverTable)
    try {
      spark.table(quarantineTable)
      true
    } catch {
      case e: Exception =>
        // Expected: quarantine table may not exist yet
        logger.debug("Quarantine table {} does not exist: {}", quarantineTable, e.getMessage)
        false
    }
  }

  def getQuarantinedRecordCount(silverTable: String): Long = {
    if (quarantineTableExists(silverTable)) {
      val quarantineTable = getQuarantineTableName(silverTable)
      spark.table(quarantineTable).count()
    } else {
      0L
    }
  }
}

object QuarantineHandler {

  def apply(spark: SparkSession): QuarantineHandler = new QuarantineHandler(spark)
}
