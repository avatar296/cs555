package csx55.sta.silver.monitoring

import csx55.sta.silver.validation.ValidationResult
import org.apache.spark.sql.{SaveMode, SparkSession}
import org.slf4j.LoggerFactory

import java.sql.Timestamp

class QualityMetricsRecorder(
  spark: SparkSession,
  monitoringTable: String
) {

  private val logger = LoggerFactory.getLogger(getClass)

  def record(
    batchId: Long,
    tableName: String,
    jobName: String,
    recordCount: Long,
    result: ValidationResult,
    quarantined: Boolean
  ): Unit = {
    try {
      val now = new Timestamp(System.currentTimeMillis())

      import scala.jdk.CollectionConverters._
      val metricsRow = spark.createDataFrame(
        Seq(
          org.apache.spark.sql.Row(
            now,
            tableName,
            jobName,
            batchId,
            recordCount,
            result.status,
            result.passed,
            result.passedChecks,
            result.failedChecks,
            result.failures.orNull,
            quarantined
          )
        ).asJava,
        getMonitoringTableSchema()
      )

      metricsRow.write
        .format("iceberg")
        .mode(SaveMode.Append)
        .save(monitoringTable)

    } catch {
      case e: Exception =>
        logger.warn("Failed to store quality metrics for batch {}: {}", batchId, e.getMessage)
    }
  }

  private def getMonitoringTableSchema() = {
    try {
      spark.table(monitoringTable).schema
    } catch {
      case e: Exception =>
        throw new RuntimeException(
          s"Monitoring table $monitoringTable does not exist. Run MonitoringSetup first.",
          e
        )
    }
  }

  def monitoringTableExists(): Boolean = {
    try {
      spark.table(monitoringTable)
      true
    } catch {
      case e: Exception =>
        // Expected: monitoring table may not exist yet
        logger.debug(s"Monitoring table $monitoringTable does not exist: ${e.getMessage}")
        false
    }
  }

  def getTotalBatchesValidated(tableName: String): Long = {
    if (monitoringTableExists()) {
      spark.sql(
        s"""
           |SELECT COUNT(*) as batch_count
           |FROM $monitoringTable
           |WHERE table_name = '$tableName'
           |""".stripMargin
      ).first().getLong(0)
    } else {
      0L
    }
  }

  def getPassRate(tableName: String): Double = {
    if (monitoringTableExists()) {
      val result = spark.sql(
        s"""
           |SELECT
           |  COUNT(*) as total,
           |  SUM(CASE WHEN all_checks_passed THEN 1 ELSE 0 END) as passed
           |FROM $monitoringTable
           |WHERE table_name = '$tableName'
           |""".stripMargin
      ).first()

      val total = result.getLong(0)
      val passed = result.getLong(1)

      if (total > 0) {
        (passed.toDouble / total.toDouble) * 100.0
      } else {
        0.0
      }
    } else {
      0.0
    }
  }
}

object QualityMetricsRecorder {

  def apply(spark: SparkSession, monitoringTable: String): QualityMetricsRecorder = {
    new QualityMetricsRecorder(spark, monitoringTable)
  }
}
