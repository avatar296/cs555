package csx55.sta.silver.validation

import com.amazon.deequ.{VerificationResult, VerificationSuite}
import com.amazon.deequ.checks.{Check, CheckResult, CheckStatus}
import org.apache.spark.sql.{Dataset, Row}
import org.slf4j.LoggerFactory

class DeequValidator(check: Check) extends DataQualityValidator {

  private val logger = LoggerFactory.getLogger(getClass)

  override def validate(data: Dataset[Row]): ValidationResult = {
    val verificationResult = new VerificationSuite()
      .onData(data)
      .addCheck(check)
      .run()

    convertToValidationResult(verificationResult)
  }

  private def convertToValidationResult(deequResult: VerificationResult): ValidationResult = {
    val passed = deequResult.status == CheckStatus.Success

    if (passed) {
      ValidationResult.success(passedChecks = countPassedChecks(deequResult))
    } else {
      val failureDetails = extractFailureReason(deequResult)
      ValidationResult.failure(
        passedChecks = countPassedChecks(deequResult),
        failedChecks = countFailedChecks(deequResult),
        failureDetails = failureDetails
      )
    }
  }

  private def extractFailureReason(result: VerificationResult): String = {
    import scala.jdk.CollectionConverters._

    try {
      val checkResultsMap = result.checkResults.asInstanceOf[java.util.Map[Check, CheckResult]]

      val failures = checkResultsMap.asScala
        .filter { case (_, checkResult) => checkResult.status != CheckStatus.Success }
        .flatMap { case (_, checkResult) =>
          checkResult.constraintResults.map { constraintResult =>
            val message = constraintResult.message.getOrElse("No message")
            val metric = constraintResult.metric
              .map(m => s" (value: ${m.value.toString})")
              .getOrElse("")
            s"${constraintResult.constraint.toString}$metric: $message"
          }
        }
        .mkString("; ")

      if (failures.nonEmpty) {
        failures
      } else {
        "Validation failed but no specific constraint details available"
      }

    } catch {
      case e: Exception =>
        logger.warn("Failed to extract failure details: {}", e.getMessage)
        s"Validation failed with status: ${result.status}"
    }
  }

  private def countPassedChecks(result: VerificationResult): Long = {
    import scala.jdk.CollectionConverters._

    try {
      val checkResultsMap = result.checkResults.asInstanceOf[java.util.Map[Check, CheckResult]]
      checkResultsMap.asScala.values.count(_.status == CheckStatus.Success).toLong
    } catch {
      case _: Exception => 0L
    }
  }

  private def countFailedChecks(result: VerificationResult): Long = {
    import scala.jdk.CollectionConverters._

    try {
      val checkResultsMap = result.checkResults.asInstanceOf[java.util.Map[Check, CheckResult]]
      checkResultsMap.asScala.values.count(_.status != CheckStatus.Success).toLong
    } catch {
      case _: Exception => 0L
    }
  }
}

object DeequValidator {

  def apply(check: Check): DeequValidator = new DeequValidator(check)
}
