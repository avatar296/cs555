package csx55.sta.silver.jobs;

import csx55.sta.streaming.base.BaseStreamingJob;
import csx55.sta.streaming.config.StreamConfig;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.Trigger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Abstract base class for Silver Layer streaming jobs.
 *
 * <p><b>SQL-FIRST PATTERN (Teaching POC)</b></p>
 *
 * <p>Philosophy: Business logic belongs in SQL, not Java code.
 * This class is a <b>minimal wrapper</b> that:
 * <ol>
 *   <li>Loads SQL from resources (business logic lives in .sql files)</li>
 *   <li>Executes SQL as a streaming query</li>
 *   <li>Writes results to Silver Iceberg tables</li>
 * </ol>
 *
 * <p><b>LOAN ORIGINATION PARALLEL:</b></p>
 * <pre>
 * Taxi POC:                   Loan Origination:
 * ---------                   -----------------
 * SQL: trips_cleaned.sql  →   SQL: applications_validated.sql
 * Java: TripsCleanedJob   →   Java: ApplicationsValidatedJob
 * Logic: Data quality     →   Logic: Credit rules, compliance checks
 * </pre>
 *
 * <p><b>Teaching Benefits:</b></p>
 * <ul>
 *   <li>SQL developers modify .sql files (familiar territory)</li>
 *   <li>Java code is a template (copy-paste for new jobs)</li>
 *   <li>Clear separation: SQL = WHAT to do, Java = HOW to run it</li>
 * </ul>
 *
 * @see csx55.sta.streaming.base.BaseStreamingJob
 */
public abstract class AbstractSilverJob extends BaseStreamingJob {

    protected final StreamConfig.SilverStreamConfig streamConfig;

    public AbstractSilverJob(StreamConfig config, StreamConfig.SilverStreamConfig streamConfig) {
        super(config);
        this.streamConfig = streamConfig;
    }

    /**
     * Returns the SQL file path (relative to resources directory).
     *
     * <p>Example: "sql/trips_cleaned.sql"</p>
     *
     * <p><b>Teaching Note:</b> Subclasses only need to specify which SQL file to run.
     * All execution logic is handled by this base class.</p>
     */
    protected abstract String getSqlFilePath();

    /**
     * Implements BaseStreamingJob abstract method.
     *
     * <p><b>Teaching Note:</b> We override run() for SQL-first pattern,
     * but must implement this for compilation. Returns the class name.</p>
     */
    @Override
    protected String getJobName() {
        return getClass().getSimpleName();
    }

    /**
     * Implements BaseStreamingJob abstract method (not used in SQL-first pattern).
     *
     * <p><b>Teaching Note:</b> SQL-first pattern loads SQL and executes it directly
     * in run() method. This template method is not used but required by base class.</p>
     */
    @Override
    protected Dataset<Row> readStream() {
        throw new UnsupportedOperationException(
            "SQL-first pattern doesn't use readStream(). " +
            "Data source is specified in SQL file.");
    }

    /**
     * Implements BaseStreamingJob abstract method (not used in SQL-first pattern).
     *
     * <p><b>Teaching Note:</b> SQL-first pattern applies transformations via SQL file.
     * This template method is not used but required by base class.</p>
     */
    @Override
    protected Dataset<Row> transform(Dataset<Row> input) {
        throw new UnsupportedOperationException(
            "SQL-first pattern doesn't use transform(). " +
            "Business logic is in SQL file: " + getSqlFilePath());
    }

    @Override
    protected String getNamespace() {
        return "lakehouse.silver";
    }

    /**
     * Initialize the job.
     * Verifies that Bronze source table exists before starting Silver processing.
     */
    @Override
    protected void initialize() {
        super.initialize();
        verifySourceTableExists();
    }

    /**
     * Verify that the Bronze source table exists.
     *
     * <p><b>Teaching Note:</b> Always validate dependencies before processing.
     * For loans: Verify raw application data exists before validation.</p>
     */
    private void verifySourceTableExists() {
        try {
            spark.table(streamConfig.sourceTable);
            logger.info("✓ Verified source table exists: {}", streamConfig.sourceTable);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Source table " + streamConfig.sourceTable + " does not exist. " +
                    "Ensure Bronze layer is running first.", e);
        }
    }

    /**
     * Execute the Silver transformation.
     *
     * <p><b>SQL-FIRST PATTERN:</b></p>
     * <ol>
     *   <li>Load SQL from .sql file (business logic)</li>
     *   <li>Register Bronze table as temp view (enables SQL querying)</li>
     *   <li>Execute SQL transformation</li>
     *   <li>Write results to Silver Iceberg table</li>
     * </ol>
     *
     * <p><b>Teaching Note:</b> This method is the same for ALL Silver jobs.
     * Different jobs = different SQL files, same Java wrapper.</p>
     */
    @Override
    public void run() throws Exception {
        logger.info("========================================");
        logger.info("Starting Silver Job: {}", getClass().getSimpleName());
        logger.info("Source: {}", streamConfig.sourceTable);
        logger.info("Target: {}", streamConfig.targetTable);
        logger.info("SQL File: {}", getSqlFilePath());
        logger.info("========================================");

        // Step 1: Load SQL transformation logic from file
        String sql = loadSqlFromResources(getSqlFilePath());
        logger.info("Loaded SQL transformation ({} characters)", sql.length());

        // Step 2: Read from Bronze Iceberg table as a stream
        // Teaching Note: readStream() enables incremental processing
        Dataset<Row> bronzeStream = spark.readStream()
                .format("iceberg")
                .table(streamConfig.sourceTable);

        // Step 3: Register as temp view so SQL can query it
        bronzeStream.createOrReplaceTempView("lakehouse_bronze_trips");
        logger.info("Registered Bronze stream as temp view");

        // Step 4: Execute SQL transformation
        // Teaching Note: THIS is where your business logic runs (the SQL file)
        Dataset<Row> silverStream = spark.sql(sql);
        logger.info("Applied SQL transformation");

        // Step 5: Write to Silver Iceberg table
        logger.info("Writing to Silver table: {}", streamConfig.targetTable);
        StreamingQuery query = writeStream(silverStream);

        // Wait for termination
        query.awaitTermination();
    }

    /**
     * Write the transformed stream to Silver Iceberg table.
     *
     * <p><b>Configuration:</b></p>
     * <ul>
     *   <li>Format: Iceberg (ACID transactions, schema evolution)</li>
     *   <li>Mode: Append (add new records, don't replace)</li>
     *   <li>Trigger: Every 5 minutes (micro-batch)</li>
     *   <li>Checkpoint: Enables exactly-once processing</li>
     * </ul>
     *
     * <p><b>Teaching Note:</b> Checkpointing ensures that if the job crashes,
     * it resumes from where it left off (no duplicate processing).</p>
     */
    protected StreamingQuery writeStream(Dataset<Row> output) throws Exception {
        logger.info("Configuring streaming write:");
        logger.info("  - Table: {}", streamConfig.targetTable);
        logger.info("  - Checkpoint: {}", streamConfig.checkpointPath);
        logger.info("  - Trigger: {}", config.getTriggerInterval());

        return output
                .writeStream()
                .format("iceberg")
                .outputMode("append")
                .trigger(Trigger.ProcessingTime(config.getTriggerInterval()))
                .option("checkpointLocation", streamConfig.checkpointPath)
                .option("fanout-enabled", "true")
                .toTable(streamConfig.targetTable);
    }

    /**
     * Load SQL file from resources.
     *
     * <p><b>Teaching Note:</b> SQL files live in src/main/resources/sql/
     * This allows SQL developers to modify business logic without touching Java.</p>
     *
     * @param resourcePath Path to SQL file (e.g., "sql/trips_cleaned.sql")
     * @return SQL content as a String
     * @throws RuntimeException if file not found or cannot be read
     */
    protected String loadSqlFromResources(String resourcePath) {
        try {
            logger.debug("Loading SQL from: {}", resourcePath);

            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);

            if (inputStream == null) {
                throw new RuntimeException("SQL file not found in resources: " + resourcePath);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String sql = reader.lines().collect(Collectors.joining("\n"));

                if (sql.trim().isEmpty()) {
                    throw new RuntimeException("SQL file is empty: " + resourcePath);
                }

                return sql;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load SQL file: " + resourcePath, e);
        }
    }
}
