package csx55.sta.silver.jobs;

import csx55.sta.streaming.config.StreamConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Silver Layer Job: Trips Cleaning
 *
 * <p><b>Purpose:</b> Data quality and validation for taxi trips.</p>
 *
 * <p><b>SQL-FIRST PATTERN DEMONSTRATION:</b></p>
 * <p>This class is intentionally minimal (only 20 lines of actual code).
 * <b>All business logic lives in SQL</b>: {@code sql/trips_cleaned.sql}</p>
 *
 * <p><b>Teaching Benefits:</b></p>
 * <ul>
 *   <li>SQL developers modify {@code trips_cleaned.sql} (familiar)</li>
 *   <li>Java developers copy this template for new jobs</li>
 *   <li>Clear separation: SQL = business rules, Java = execution engine</li>
 * </ul>
 *
 * <p><b>LOAN ORIGINATION PARALLEL:</b></p>
 * <pre>
 * To create ApplicationsValidatedJob for loan originations:
 * 1. Copy this file → ApplicationsValidatedJob.java
 * 2. Copy sql/trips_cleaned.sql → sql/applications_validated.sql
 * 3. Replace trip validation rules with credit rules
 * 4. Update getSqlFilePath() to point to new SQL file
 * 5. Done! Same pattern, different domain.
 * </pre>
 *
 * <p><b>What This Job Does:</b></p>
 * <ul>
 *   <li>Reads from {@code lakehouse.bronze.trips} (raw taxi data)</li>
 *   <li>Applies SQL validation rules (distance, fare, location checks)</li>
 *   <li>Adds data quality flags and scores</li>
 *   <li>Writes to {@code lakehouse.silver.trips_cleaned} (validated data)</li>
 * </ul>
 *
 * @see AbstractSilverJob
 */
public class TripsCleanedJob extends AbstractSilverJob {

    private static final Logger logger = LoggerFactory.getLogger(TripsCleanedJob.class);

    /**
     * Constructor.
     * Initializes with configuration for trips cleaning job.
     *
     * @param config Global streaming configuration
     */
    public TripsCleanedJob(StreamConfig config) {
        super(config, config.getSilverTripsCleanedConfig());
    }

    /**
     * Specifies which SQL file contains the business logic.
     *
     * <p><b>Teaching Note:</b> This is the ONLY method you need to implement
     * for a new Silver job. Everything else is handled by AbstractSilverJob.</p>
     *
     * @return Path to SQL file (relative to resources directory)
     */
    @Override
    protected String getSqlFilePath() {
        return "sql/trips_cleaned.sql";
    }

    /**
     * Main entry point for the job.
     *
     * <p><b>Teaching Note:</b> Standard pattern for all Spark Structured Streaming jobs.
     * For loan originations, this would be identical.</p>
     *
     * @param args Command-line arguments (not used)
     */
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("Silver Layer: Trips Cleaned Job");
        logger.info("========================================");

        try {
            // Load configuration
            StreamConfig config = new StreamConfig();

            // Create and run job
            TripsCleanedJob job = new TripsCleanedJob(config);
            job.run();

        } catch (Exception e) {
            logger.error("Failed to run TripsCleanedJob", e);
            System.exit(1);
        }
    }
}
