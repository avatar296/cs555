package csx55.sta.silver.jobs

import com.amazon.deequ.checks.{Check, CheckLevel}
import csx55.sta.streaming.config.StreamConfig
import org.slf4j.LoggerFactory

/**
 * Silver Layer Job: Events Cleaning with Deequ Validation (Scala)
 *
 * <p><b>Purpose:</b> Transform and validate special events data.</p>
 *
 * <p><b>TEACHING NOTE - LOAN ORIGINATION PARALLEL:</b></p>
 * <p>Events are CONTEXTUAL data (special circumstances affecting trips).
 * In loan originations, this is like:
 * <ul>
 *   <li>Co-borrower information (additional income sources)</li>
 *   <li>Special loan programs (FHA, VA, first-time homebuyer)</li>
 *   <li>Guarantor information (co-signers, collateral)</li>
 *   <li>Government assistance programs</li>
 * </ul>
 *
 * <p><b>Data Characteristics:</b>
 * <ul>
 *   <li><b>Optional:</b> Not all trips have nearby events (not all loans have co-borrowers)</li>
 *   <li><b>Impact:</b> Events affect demand (co-borrowers affect loan risk)</li>
 *   <li><b>Validation:</b> When present, data must be high quality</li>
 * </ul>
 */
class EventsCleanedJob(config: StreamConfig)
    extends AbstractSilverJob(config, config.getSilverEventsCleanedConfig()) {

  // Note: logger is inherited from BaseStreamingJob (via AbstractSilverJob)

  override protected def getSqlFilePath(): String = {
    "sql/events_cleaned.sql"
  }

  /**
   * Defines Deequ validation checks for events data.
   *
   * <p><b>Validation Rules:</b>
   * <ul>
   *   <li><b>Attendance range:</b> 1-100,000 (reasonable event sizes)</li>
   *   <li><b>Completeness:</b> Event type, timestamps must be present</li>
   *   <li><b>Logical consistency:</b> startTime < endTime (handled in SQL)</li>
   * </ul>
   *
   * <p><b>Note on Complex Constraints:</b>
   * Cross-column logical constraints like "startTime < endTime" are better
   * handled in SQL WHERE clauses. Deequ excels at single-column validation.
   *
   * <p><b>Loan Origination Parallel:</b>
   * <ul>
   *   <li>attendance_estimate (1-100K) → co_borrower_income (0-10M)</li>
   *   <li>event_type completeness → loan_program_type completeness</li>
   *   <li>startTime completeness → program_start_date completeness</li>
   *   <li>endTime completeness → program_end_date completeness</li>
   * </ul>
   */
  override protected def getDeequChecks(): Check = {
    Check(CheckLevel.Error, "Events Data Quality Checks")
      // ====================================================================
      // COMPLETENESS CHECKS
      // Critical fields must always be present
      // ====================================================================
      .isComplete("timestamp")
      .isComplete("location_id")
      .isComplete("event_type")    // SPORTS, CONCERT, CONFERENCE, FESTIVAL
      .isComplete("start_time")    // Event start timestamp
      .isComplete("end_time")      // Event end timestamp

      // ====================================================================
      // RANGE VALIDATION: Attendance Estimate
      // Business rule: 1-100,000 people (reasonable event sizes)
      // - Minimum 1: At least one attendee
      // - Maximum 100,000: Large stadium/venue capacity
      //
      // Loan parallel: co_borrower_income ($0 - $10M)
      // ====================================================================
      .hasMin("attendance_estimate", _ >= 1.0)
      .hasMax("attendance_estimate", _ <= 100000.0)

      // ====================================================================
      // COMPLEX LOGICAL CONSTRAINTS (handled in SQL)
      // ====================================================================
      // The constraint "start_time < end_time" is a cross-column check.
      // Deequ can handle this with custom Analyzers, but SQL is clearer:
      //
      // In events_cleaned.sql, we filter:
      //   WHERE start_time < end_time
      //
      // This approach is:
      // - More readable for SQL developers
      // - Easier to maintain
      // - Standard pattern for cross-column constraints
      //
      // For loans: Similar logic for validating co-borrower relationships,
      // program eligibility rules, date range consistency, etc.
      // ====================================================================
  }
}

/**
 * Main entry point for running EventsCleanedJob
 */
object EventsCleanedJob {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def main(args: Array[String]): Unit = {
    logger.info("========================================")
    logger.info("Silver Layer: Events Cleaned Job (Deequ)")
    logger.info("========================================")

    try {
      val config = new StreamConfig()
      val job = new EventsCleanedJob(config)
      job.run()

    } catch {
      case e: Exception =>
        logger.error("Failed to run EventsCleanedJob", e)
        System.exit(1)
    }
  }
}
