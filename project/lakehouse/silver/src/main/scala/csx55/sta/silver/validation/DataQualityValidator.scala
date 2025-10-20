package csx55.sta.silver.validation

import org.apache.spark.sql.{Dataset, Row}

trait DataQualityValidator {

  def validate(data: Dataset[Row]): ValidationResult
}

case class ValidationResult(
  passed: Boolean,
  status: String,
  passedChecks: Long,
  failedChecks: Long,
  failures: Option[String]
) {

  def getFailuresOrElse(default: String): String = failures.getOrElse(default)
}

object ValidationResult {

  def success(passedChecks: Long): ValidationResult = {
    ValidationResult(
      passed = true,
      status = "Success",
      passedChecks = passedChecks,
      failedChecks = 0,
      failures = None
    )
  }

  def failure(passedChecks: Long, failedChecks: Long, failureDetails: String): ValidationResult = {
    ValidationResult(
      passed = false,
      status = "Error",
      passedChecks = passedChecks,
      failedChecks = failedChecks,
      failures = Some(failureDetails)
    )
  }
}
