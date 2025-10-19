package csx55.sta.silver.jobs;

import csx55.sta.streaming.config.StreamConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Silver Layer Job: Events Cleaning
 *
 * <p><b>Purpose:</b> Data quality and validation for special events data (contextual data).</p>
 *
 * <p><b>SQL-FIRST PATTERN DEMONSTRATION:</b></p>
 * <p>This class is intentionally minimal (only 20 lines of actual code).
 * <b>All business logic lives in SQL</b>: {@code sql/events_cleaned.sql}</p>
 *
 * <p><b>LOAN ORIGINATION PARALLEL:</b></p>
 * <pre>
 * Special Events Data            → Loan Special Circumstances
 * ─────────────────────────────────────────────────────────────────
 * Attendance validation          → Number of co-borrowers validation (1-4)
 * Start/end time validation      → Loan term date range validation
 * Event type enum                → Loan program type (FHA, VA, Conventional)
 * Event duration check           → Loan term validation (6mo - 30yr)
 * Major event flag               → Has guarantor flag (risk reduction)
 * Venue name (optional)          → Guarantor name (optional)
 *
 * Teaching Point: Special circumstances need their own validation rules.
 * Not all data follows the same pattern - some is optional but still needs validation.
 * </pre>
 *
 * <p><b>What This Job Does:</b></p>
 * <ul>
 *   <li>Reads from {@code lakehouse.bronze.events} (raw special events data)</li>
 *   <li>Applies SQL validation rules (attendance, time ranges, event types)</li>
 *   <li>Validates logical consistency (start before end, reasonable duration)</li>
 *   <li>Adds data quality flags and derived event impact metrics</li>
 *   <li>Writes to {@code lakehouse.silver.events_cleaned} (validated events)</li>
 * </ul>
 *
 * @see AbstractSilverJob
 */
public class EventsCleanedJob extends AbstractSilverJob {

    private static final Logger logger = LoggerFactory.getLogger(EventsCleanedJob.class);

    /**
     * Constructor.
     * Initializes with configuration for events cleaning job.
     *
     * @param config Global streaming configuration
     */
    public EventsCleanedJob(StreamConfig config) {
        super(config, config.getSilverEventsCleanedConfig());
    }

    /**
     * Specifies which SQL file contains the business logic.
     *
     * <p><b>Teaching Note:</b> This is the ONLY line that differs between
     * WeatherCleanedJob, EventsCleanedJob, and TripsCleanedJob.
     * Everything else is identical - that's template reusability!</p>
     *
     * @return Path to SQL file (relative to resources directory)
     */
    @Override
    protected String getSqlFilePath() {
        return "sql/events_cleaned.sql";
    }

    /**
     * Main entry point for the job.
     *
     * @param args Command-line arguments (not used)
     */
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("Silver Layer: Events Cleaned Job");
        logger.info("========================================");

        try {
            // Load configuration
            StreamConfig config = new StreamConfig();

            // Create and run job
            EventsCleanedJob job = new EventsCleanedJob(config);
            job.run();

        } catch (Exception e) {
            logger.error("Failed to run EventsCleanedJob", e);
            System.exit(1);
        }
    }
}
