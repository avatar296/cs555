package csx55.sta.silver.jobs;

import csx55.sta.streaming.config.StreamConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Silver Layer Job: Weather Cleaning
 *
 * <p><b>Purpose:</b> Data quality and validation for weather data (external enrichment source).</p>
 *
 * <p><b>SQL-FIRST PATTERN DEMONSTRATION:</b></p>
 * <p>This class is intentionally minimal (only 20 lines of actual code).
 * <b>All business logic lives in SQL</b>: {@code sql/weather_cleaned.sql}</p>
 *
 * <p><b>LOAN ORIGINATION PARALLEL:</b></p>
 * <pre>
 * Weather Data (External)       → Credit Bureau Data (External)
 * ─────────────────────────────────────────────────────────────
 * Temperature validation         → Credit score validation (300-850)
 * Precipitation validation       → Debt-to-income ratio validation
 * Wind speed validation          → Monthly income validation
 * Weather condition enum         → Employment status enum
 * Severe weather flag            → High credit risk flag
 *
 * Teaching Point: External data sources need validation too!
 * Just because it comes from an API doesn't mean it's correct.
 * </pre>
 *
 * <p><b>What This Job Does:</b></p>
 * <ul>
 *   <li>Reads from {@code lakehouse.bronze.weather} (raw weather data from API)</li>
 *   <li>Applies SQL validation rules (temperature, precipitation, wind speed)</li>
 *   <li>Adds data quality flags and derived weather impact metrics</li>
 *   <li>Writes to {@code lakehouse.silver.weather_cleaned} (validated weather)</li>
 * </ul>
 *
 * @see AbstractSilverJob
 */
public class WeatherCleanedJob extends AbstractSilverJob {

    private static final Logger logger = LoggerFactory.getLogger(WeatherCleanedJob.class);

    /**
     * Constructor.
     * Initializes with configuration for weather cleaning job.
     *
     * @param config Global streaming configuration
     */
    public WeatherCleanedJob(StreamConfig config) {
        super(config, config.getSilverWeatherCleanedConfig());
    }

    /**
     * Specifies which SQL file contains the business logic.
     *
     * <p><b>Teaching Note:</b> Change this one line to point to a different SQL file,
     * and you have a completely different transformation job. That's the power of
     * the SQL-first pattern!</p>
     *
     * @return Path to SQL file (relative to resources directory)
     */
    @Override
    protected String getSqlFilePath() {
        return "sql/weather_cleaned.sql";
    }

    /**
     * Main entry point for the job.
     *
     * @param args Command-line arguments (not used)
     */
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("Silver Layer: Weather Cleaned Job");
        logger.info("========================================");

        try {
            // Load configuration
            StreamConfig config = new StreamConfig();

            // Create and run job
            WeatherCleanedJob job = new WeatherCleanedJob(config);
            job.run();

        } catch (Exception e) {
            logger.error("Failed to run WeatherCleanedJob", e);
            System.exit(1);
        }
    }
}
