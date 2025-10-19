package csx55.sta.schema.silver;

import csx55.sta.streaming.config.StreamConfig;
import csx55.sta.streaming.factory.IcebergSessionBuilder;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Silver Layer Table Setup
 *
 * <p>One-time initialization job that creates Silver Iceberg tables
 * by reading sample data from Bronze and applying SQL transformations.</p>
 *
 * <p><b>SQL-FIRST PATTERN:</b> Uses the same SQL files as streaming jobs
 * to ensure schema consistency.</p>
 *
 * <p><b>TEACHING NOTE - LOAN ORIGINATION PARALLEL:</b></p>
 * <pre>
 * This pattern sets up Silver tables for cleaned/validated data.
 * For loan originations:
 *   - Bronze: Raw loan applications
 *   - SQL: Application validation rules (sql/applications_validated.sql)
 *   - Silver: Validated loan applications
 * </pre>
 */
public class SilverTableSetup {

    private static final Logger logger = LoggerFactory.getLogger(SilverTableSetup.class);

    private final StreamConfig config;
    private final SparkSession spark;

    public SilverTableSetup(StreamConfig config) {
        this.config = config;
        this.spark = IcebergSessionBuilder.createSession("SilverTableSetup", config);
    }

    /**
     * Create all Silver layer tables.
     */
    public void run() {
        logger.info("========================================");
        logger.info("Silver Layer Table Setup");
        logger.info("========================================");

        // Create silver namespace
        ensureNamespaceExists();

        // Create Silver layer tables
        createTripsCleanedTable();
        createWeatherCleanedTable();
        createEventsCleanedTable();

        logger.info("========================================");
        logger.info("Silver tables setup complete!");
        logger.info("========================================");

        spark.stop();
    }

    /**
     * Ensure the Silver namespace exists.
     */
    private void ensureNamespaceExists() {
        try {
            spark.sql("CREATE NAMESPACE IF NOT EXISTS lakehouse.silver");
            logger.info("✓ Ensured namespace exists: lakehouse.silver");
        } catch (Exception e) {
            logger.warn("Could not create namespace (may already exist): {}", e.getMessage());
        }
    }

    /**
     * Create trips_cleaned table by applying SQL transformation to Bronze sample.
     *
     * <p><b>TEACHING NOTE:</b> This demonstrates the SQL-first pattern:
     * <ol>
     *   <li>Read sample from Bronze (source data)</li>
     *   <li>Load SQL transformation (business logic)</li>
     *   <li>Apply SQL to infer Silver schema</li>
     *   <li>Create table with inferred schema</li>
     * </ol>
     * </p>
     */
    private void createTripsCleanedTable() {
        String tableName = "lakehouse.silver.trips_cleaned";
        String sourceTable = "lakehouse.bronze.trips";

        logger.info("");
        logger.info("Processing: {}", tableName);
        logger.info("  Source: {}", sourceTable);

        // Check if table already exists
        try {
            spark.table(tableName);
            logger.info("  ✓ Table already exists: {}", tableName);
            return;
        } catch (Exception e) {
            logger.info("  Table does not exist, creating...");
        }

        // Step 1: Read sample from Bronze
        logger.info("  Reading sample from Bronze: {}", sourceTable);
        Dataset<Row> bronzeSample;
        try {
            bronzeSample = spark
                    .read()
                    .format("iceberg")
                    .table(sourceTable)
                    .limit(100);

            if (bronzeSample.isEmpty()) {
                logger.warn("  ⚠ No data available in {}. Skipping table creation.", sourceTable);
                logger.warn("  Table will be created when Bronze data becomes available.");
                return;
            }

            logger.info("  Read {} sample records from Bronze", bronzeSample.count());
        } catch (Exception e) {
            logger.error("  ✗ Failed to read from Bronze table: {}", sourceTable, e);
            throw new RuntimeException("Bronze table " + sourceTable + " does not exist or is not accessible", e);
        }

        // Step 2: Register Bronze sample as temp view (so SQL can query it)
        bronzeSample.createOrReplaceTempView("lakehouse_bronze_trips");
        logger.info("  Registered Bronze sample as temp view");

        // Step 3: Load SQL transformation
        String sql = loadSqlFromResources("sql/trips_cleaned.sql");
        logger.info("  Loaded SQL transformation ({} characters)", sql.length());

        // Step 4: Apply SQL transformation to infer schema
        logger.info("  Applying SQL transformation...");
        Dataset<Row> silverSample = spark.sql(sql);

        logger.info("  Inferred Silver schema:");
        silverSample.schema().printTreeString();

        // Step 5: Create Silver table with inferred schema
        logger.info("  Creating table: {}", tableName);
        try {
            silverSample.write()
                    .format("iceberg")
                    .mode("append")
                    .saveAsTable(tableName);

            logger.info("  ✓ Table created successfully: {}", tableName);
        } catch (Exception e) {
            logger.error("  ✗ Failed to create table: {}", tableName, e);
            throw new RuntimeException("Failed to create Silver table: " + tableName, e);
        }
    }

    /**
     * Create weather_cleaned table by applying SQL transformation to Bronze sample.
     */
    private void createWeatherCleanedTable() {
        String tableName = "lakehouse.silver.weather_cleaned";
        String sourceTable = "lakehouse.bronze.weather";
        String sqlFile = "sql/weather_cleaned.sql";

        logger.info("");
        logger.info("Processing: {}", tableName);
        logger.info("  Source: {}", sourceTable);

        // Check if table already exists
        try {
            spark.table(tableName);
            logger.info("  ✓ Table already exists: {}", tableName);
            return;
        } catch (Exception e) {
            logger.info("  Table does not exist, creating...");
        }

        // Read sample from Bronze, apply SQL, create table
        logger.info("  Reading sample from Bronze: {}", sourceTable);
        Dataset<Row> bronzeSample = spark.read().format("iceberg").table(sourceTable).limit(100);

        if (bronzeSample.isEmpty()) {
            logger.warn("  ⚠ No data available in {}. Skipping table creation.", sourceTable);
            return;
        }

        bronzeSample.createOrReplaceTempView("lakehouse_bronze_weather");
        String sql = loadSqlFromResources(sqlFile);
        Dataset<Row> silverSample = spark.sql(sql);

        logger.info("  Inferred Silver schema:");
        silverSample.schema().printTreeString();

        silverSample.write().format("iceberg").mode("append").saveAsTable(tableName);
        logger.info("  ✓ Table created successfully: {}", tableName);
    }

    /**
     * Create events_cleaned table by applying SQL transformation to Bronze sample.
     */
    private void createEventsCleanedTable() {
        String tableName = "lakehouse.silver.events_cleaned";
        String sourceTable = "lakehouse.bronze.events";
        String sqlFile = "sql/events_cleaned.sql";

        logger.info("");
        logger.info("Processing: {}", tableName);
        logger.info("  Source: {}", sourceTable);

        // Check if table already exists
        try {
            spark.table(tableName);
            logger.info("  ✓ Table already exists: {}", tableName);
            return;
        } catch (Exception e) {
            logger.info("  Table does not exist, creating...");
        }

        // Read sample from Bronze, apply SQL, create table
        logger.info("  Reading sample from Bronze: {}", sourceTable);
        Dataset<Row> bronzeSample = spark.read().format("iceberg").table(sourceTable).limit(100);

        if (bronzeSample.isEmpty()) {
            logger.warn("  ⚠ No data available in {}. Skipping table creation.", sourceTable);
            return;
        }

        bronzeSample.createOrReplaceTempView("lakehouse_bronze_events");
        String sql = loadSqlFromResources(sqlFile);
        Dataset<Row> silverSample = spark.sql(sql);

        logger.info("  Inferred Silver schema:");
        silverSample.schema().printTreeString();

        silverSample.write().format("iceberg").mode("append").saveAsTable(tableName);
        logger.info("  ✓ Table created successfully: {}", tableName);
    }

    /**
     * Load SQL file from resources.
     *
     * <p><b>TEACHING NOTE:</b> Reuses the same SQL files as streaming jobs.
     * This ensures schema consistency between table setup and streaming execution.</p>
     *
     * @param resourcePath Path to SQL file in resources
     * @return SQL content as a String
     */
    private String loadSqlFromResources(String resourcePath) {
        try {
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
