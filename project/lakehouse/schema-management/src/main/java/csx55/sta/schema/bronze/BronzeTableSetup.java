package csx55.sta.schema.bronze;

import csx55.sta.streaming.config.StreamConfig;
import csx55.sta.streaming.factory.IcebergSessionBuilder;
import csx55.sta.streaming.utils.AvroUtils;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BronzeTableSetup {

    private static final Logger logger = LoggerFactory.getLogger(BronzeTableSetup.class);

    private final StreamConfig config;
    private final SparkSession spark;

    public BronzeTableSetup(StreamConfig config) {
        this.config = config;
        this.spark = IcebergSessionBuilder.createSession("BronzeTableSetup", config);
    }

    public void run() {
        logger.info("Starting Bronze Layer table setup");

        ensureNamespaceExists();

        createTableForStream(config.getBronzeTripsConfig());
        createTableForStream(config.getBronzeWeatherConfig());
        createTableForStream(config.getBronzeEventsConfig());

        logger.info("Bronze tables setup complete");
        spark.stop();
    }

    private void ensureNamespaceExists() {
        try {
            spark.sql("CREATE NAMESPACE IF NOT EXISTS lakehouse.bronze");
            logger.debug("Ensured namespace exists: lakehouse.bronze");
        } catch (Exception e) {
            logger.warn("Could not create namespace: {}", e.getMessage());
        }
    }

    private void createTableForStream(StreamConfig.BronzeStreamConfig streamConfig) {
        try {
            spark.table(streamConfig.table);
            logger.info("Table already exists: {}", streamConfig.table);
            return;
        } catch (Exception e) {
            logger.debug("Table does not exist, creating: {}", streamConfig.table);
        }

        Dataset<Row> sampleBatch = spark
                .read()
                .format("kafka")
                .option("kafka.bootstrap.servers", config.getKafkaBootstrapServers())
                .option("subscribe", streamConfig.topic)
                .option("startingOffsets", "earliest")
                .option("endingOffsets", "latest")
                .load()
                .limit(10);

        if (sampleBatch.isEmpty()) {
            logger.warn("No data available in topic {}. Table will be created when data becomes available.",
                    streamConfig.topic);
            return;
        }

        Dataset<Row> transformedSample = AvroUtils.prepareBronzeData(
                sampleBatch,
                streamConfig.topic,
                config.getSchemaRegistryUrl());

        logger.debug("Schema: {}", transformedSample.schema().treeString());

        transformedSample.write()
                .format("iceberg")
                .mode("append")
                .saveAsTable(streamConfig.table);

        logger.info("Table created: {}", streamConfig.table);
    }
}
