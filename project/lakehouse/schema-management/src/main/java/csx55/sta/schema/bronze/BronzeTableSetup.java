package csx55.sta.schema.bronze;

import csx55.sta.streaming.config.StreamConfig;
import csx55.sta.streaming.factory.IcebergSessionBuilder;
import csx55.sta.streaming.utils.AvroUtils;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bronze Layer Table Setup
 *
 * One-time initialization job that creates all bronze Iceberg tables
 * by reading sample data from Kafka topics.
 *
 * This separates schema management from streaming execution.
 */
public class BronzeTableSetup {

    private static final Logger logger = LoggerFactory.getLogger(BronzeTableSetup.class);

    private final StreamConfig config;
    private final SparkSession spark;

    public BronzeTableSetup(StreamConfig config) {
        this.config = config;
        this.spark = IcebergSessionBuilder.createSession("BronzeTableSetup", config);
    }

    public void run() {
        logger.info("========================================");
        logger.info("Bronze Layer Table Setup");
        logger.info("========================================");

        // Create bronze namespace
        ensureNamespaceExists();

        // Create tables for each stream
        createTableForStream(config.getBronzeTripsConfig());
        createTableForStream(config.getBronzeWeatherConfig());
        createTableForStream(config.getBronzeEventsConfig());

        logger.info("========================================");
        logger.info("Bronze tables setup complete!");
        logger.info("========================================");

        spark.stop();
    }

    /**
     * Create the bronze namespace if it doesn't exist
     */
    private void ensureNamespaceExists() {
        try {
            spark.sql("CREATE NAMESPACE IF NOT EXISTS lakehouse.bronze");
            logger.info("✓ Ensured namespace exists: lakehouse.bronze");
        } catch (Exception e) {
            logger.warn("Could not create namespace (may already exist): {}", e.getMessage());
        }
    }

    /**
     * Create a table for a specific stream
     */
    private void createTableForStream(StreamConfig.BronzeStreamConfig streamConfig) {
        logger.info("");
        logger.info("Processing: {}", streamConfig.table);
        logger.info("  Topic: {}", streamConfig.topic);

        try {
            // Check if table already exists
            spark.table(streamConfig.table);
            logger.info("  ✓ Table already exists: {}", streamConfig.table);
            return;
        } catch (Exception e) {
            logger.info("  Table does not exist, creating...");
        }

        // Read sample data from Kafka
        logger.info("  Reading sample from Kafka topic: {}", streamConfig.topic);
        Dataset<Row> sampleBatch = spark
                .read()
                .format("kafka")
                .option("kafka.bootstrap.servers", config.getKafkaBootstrapServers())
                .option("subscribe", streamConfig.topic)
                .option("startingOffsets", "earliest")
                .option("endingOffsets", "latest")
                .load()
                .limit(10);

        // Check if we got any data
        if (sampleBatch.isEmpty()) {
            logger.warn("  ⚠ No data available in topic {}. Skipping table creation.", streamConfig.topic);
            logger.warn("  Table will be created when data becomes available.");
            return;
        }

        // Transform using same logic as streaming
        logger.info("  Transforming sample data...");
        Dataset<Row> transformedSample = AvroUtils.prepareBronzeData(
                sampleBatch,
                streamConfig.topic,
                config.getSchemaRegistryUrl()
        );

        logger.info("  Creating table with inferred schema");
        logger.info("  Schema:");
        transformedSample.schema().printTreeString();

        // Write sample data to create the table
        transformedSample.write()
                .format("iceberg")
                .mode("append")
                .saveAsTable(streamConfig.table);

        logger.info("  ✓ Table created successfully: {}", streamConfig.table);
    }
}
