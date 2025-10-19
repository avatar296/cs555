package csx55.sta.schema.monitoring;

import csx55.sta.streaming.config.StreamConfig;
import csx55.sta.streaming.factory.IcebergSessionBuilder;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitoring Infrastructure Setup
 *
 * <p>Creates Iceberg tables for storing Deequ quality metrics.</p>
 *
 * <p><b>TEACHING NOTE - MONITORING PATTERN:</b></p>
 * <pre>
 * This creates infrastructure for data quality monitoring.
 * For loan originations:
 *   - Compliance audit trail (regulators require this!)
 *   - Quality metrics over time (trending/alerting)
 *   - SLA tracking (99% of loans must be validated within 2 minutes)
 * </pre>
 *
 * <p><b>What Gets Created:</b></p>
 * <ul>
 *   <li>{@code lakehouse.monitoring.silver_quality_metrics} - Deequ validation results</li>
 * </ul>
 */
public class MonitoringSetup {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringSetup.class);

    private final StreamConfig config;
    private final SparkSession spark;

    public MonitoringSetup(StreamConfig config) {
        this.config = config;
        this.spark = IcebergSessionBuilder.createSession("MonitoringSetup", config);
    }

    /**
     * Create all monitoring tables.
     */
    public void run() {
        logger.info("========================================");
        logger.info("Monitoring Infrastructure Setup");
        logger.info("========================================");

        // Create monitoring namespace
        ensureMonitoringNamespaceExists();

        // Create quarantine namespace
        ensureQuarantineNamespaceExists();

        // Create quality metrics table
        createSilverQualityMetricsTable();

        // Create quarantine tables
        createQuarantineTables();

        logger.info("========================================");
        logger.info("Monitoring tables setup complete!");
        logger.info("========================================");

        spark.stop();
    }

    /**
     * Ensure the monitoring namespace exists.
     */
    private void ensureMonitoringNamespaceExists() {
        try {
            spark.sql("CREATE NAMESPACE IF NOT EXISTS lakehouse.monitoring");
            logger.info("✓ Ensured namespace exists: lakehouse.monitoring");
        } catch (Exception e) {
            logger.warn("Could not create namespace (may already exist): {}", e.getMessage());
        }
    }

    /**
     * Ensure the quarantine namespace exists.
     */
    private void ensureQuarantineNamespaceExists() {
        try {
            spark.sql("CREATE NAMESPACE IF NOT EXISTS lakehouse.quarantine");
            logger.info("✓ Ensured namespace exists: lakehouse.quarantine");
        } catch (Exception e) {
            logger.warn("Could not create namespace (may already exist): {}", e.getMessage());
        }
    }

    /**
     * Create the Silver quality metrics table.
     *
     * <p><b>TEACHING NOTE:</b> This table stores Deequ validation results over time.</p>
     *
     * <p><b>Schema:</b></p>
     * <ul>
     *   <li>timestamp - When validation ran</li>
     *   <li>table_name - Which Silver table was validated</li>
     *   <li>job_name - Which job produced this batch</li>
     *   <li>batch_id - Unique batch identifier</li>
     *   <li>record_count - Number of records in batch</li>
     *   <li>status - Validation status (Success, Error, Warning)</li>
     *   <li>all_checks_passed - Boolean success indicator</li>
     *   <li>passed_checks - Number of checks that passed</li>
     *   <li>failed_checks - Number of checks that failed</li>
     *   <li>constraint_failures - Details of constraint violations (nullable)</li>
     *   <li>batch_quarantined - Whether batch was sent to quarantine</li>
     * </ul>
     *
     * <p><b>For Loan Originations:</b> This becomes your compliance audit trail!</p>
     */
    private void createSilverQualityMetricsTable() {
        String tableName = "lakehouse.monitoring.silver_quality_metrics";

        logger.info("");
        logger.info("Processing: {}", tableName);

        // Check if table already exists
        try {
            spark.table(tableName);
            logger.info("  ✓ Table already exists: {}", tableName);
            return;
        } catch (Exception e) {
            logger.info("  Table does not exist, creating...");
        }

        // Define schema for quality metrics
        StructType schema = new StructType(new StructField[]{
                new StructField("timestamp", DataTypes.TimestampType, false, Metadata.empty()),
                new StructField("table_name", DataTypes.StringType, false, Metadata.empty()),
                new StructField("job_name", DataTypes.StringType, false, Metadata.empty()),
                new StructField("batch_id", DataTypes.LongType, false, Metadata.empty()),
                new StructField("record_count", DataTypes.LongType, false, Metadata.empty()),
                new StructField("status", DataTypes.StringType, false, Metadata.empty()),
                new StructField("all_checks_passed", DataTypes.BooleanType, false, Metadata.empty()),
                new StructField("passed_checks", DataTypes.LongType, false, Metadata.empty()),
                new StructField("failed_checks", DataTypes.LongType, false, Metadata.empty()),
                new StructField("constraint_failures", DataTypes.StringType, true, Metadata.empty()),
                new StructField("batch_quarantined", DataTypes.BooleanType, false, Metadata.empty())
        });

        logger.info("  Creating table with schema:");
        schema.printTreeString();

        try {
            // Create empty DataFrame with schema
            spark.createDataFrame(java.util.Collections.emptyList(), schema)
                    .write()
                    .format("iceberg")
                    .mode("append")
                    .saveAsTable(tableName);

            logger.info("  ✓ Table created successfully: {}", tableName);

            // Log usage example
            logger.info("");
            logger.info("  Usage Example (Query quality trends):");
            logger.info("  SELECT");
            logger.info("    DATE(timestamp) as date,");
            logger.info("    table_name,");
            logger.info("    AVG(CAST(all_checks_passed AS INT)) as pass_rate,");
            logger.info("    SUM(record_count) as total_records");
            logger.info("  FROM lakehouse.monitoring.silver_quality_metrics");
            logger.info("  WHERE timestamp >= CURRENT_DATE - INTERVAL 7 DAYS");
            logger.info("  GROUP BY DATE(timestamp), table_name");
            logger.info("  ORDER BY date DESC, table_name");
            logger.info("");

        } catch (Exception e) {
            logger.error("  ✗ Failed to create table: {}", tableName, e);
            throw new RuntimeException("Failed to create monitoring table: " + tableName, e);
        }
    }

    /**
     * Create quarantine tables for Silver layer data quality failures.
     *
     * <p><b>TEACHING NOTE:</b> Quarantine tables store batches that fail Deequ validation.</p>
     *
     * <p><b>Strategy:</b> Tables are created on-demand during first write.</p>
     * <ul>
     *   <li>Schema matches Silver tables (from SQL transformations)</li>
     *   <li>Additional metadata columns added during write:
     *       <ul>
     *           <li>quarantine_timestamp - When batch was quarantined</li>
     *           <li>batch_id - Streaming batch identifier</li>
     *           <li>failure_reason - Deequ constraint violation details</li>
     *           <li>validation_status - Deequ validation status string</li>
     *       </ul>
     *   </li>
     * </ul>
     *
     * <p><b>For Loan Originations:</b> This is your rejected applications table!</p>
     * <ul>
     *   <li>Stores loans that fail credit/income/DTI validation</li>
     *   <li>Manual review queue for underwriters</li>
     *   <li>Reprocessing after data correction</li>
     * </ul>
     */
    private void createQuarantineTables() {
        logger.info("");
        logger.info("Quarantine Tables Setup");
        logger.info("=======================");
        logger.info("");
        logger.info("Quarantine tables will be created on-demand when first batch is rejected:");
        logger.info("  - lakehouse.quarantine.trips_quarantined");
        logger.info("  - lakehouse.quarantine.weather_quarantined");
        logger.info("  - lakehouse.quarantine.events_quarantined");
        logger.info("");
        logger.info("Schema: Silver table columns + metadata:");
        logger.info("  + quarantine_timestamp (timestamp of rejection)");
        logger.info("  + batch_id (streaming batch identifier)");
        logger.info("  + failure_reason (Deequ constraint violations)");
        logger.info("  + validation_status (Deequ validation status)");
        logger.info("");
        logger.info("✓ Quarantine namespace prepared");
    }

    /**
     * Main entry point (for standalone execution).
     */
    public static void main(String[] args) {
        logger.info("Running Monitoring Setup as standalone job");

        try {
            StreamConfig config = new StreamConfig();
            MonitoringSetup setup = new MonitoringSetup(config);
            setup.run();

        } catch (Exception e) {
            logger.error("Monitoring setup failed", e);
            System.exit(1);
        }
    }
}
