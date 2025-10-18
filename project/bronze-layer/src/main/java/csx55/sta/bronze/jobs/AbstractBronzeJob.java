package csx55.sta.bronze.jobs;

import csx55.sta.streaming.base.BaseStreamingJob;
import csx55.sta.streaming.config.StreamConfig;
import csx55.sta.streaming.utils.AvroUtils;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.Trigger;

/**
 * Abstract base class for Bronze Layer streaming jobs
 * Implements common logic for reading from Kafka and writing to Iceberg
 */
public abstract class AbstractBronzeJob extends BaseStreamingJob {

    protected final StreamConfig.BronzeStreamConfig streamConfig;

    public AbstractBronzeJob(StreamConfig config, StreamConfig.BronzeStreamConfig streamConfig) {
        super(config);
        this.streamConfig = streamConfig;
    }

    @Override
    protected String getNamespace() {
        return "lakehouse.bronze";
    }

    @Override
    protected void initialize() {
        super.initialize();
        ensureNamespaceExists();
        ensureTableExists();
    }

    /**
     * Create the bronze namespace if it doesn't exist
     */
    private void ensureNamespaceExists() {
        try {
            spark.sql("CREATE NAMESPACE IF NOT EXISTS lakehouse.bronze");
            logger.info("Ensured namespace exists: lakehouse.bronze");
        } catch (Exception e) {
            logger.warn("Could not create namespace (may already exist): {}", e.getMessage());
        }
    }

    /**
     * Create the Iceberg table if it doesn't exist
     * Uses a micro-batch read to infer schema from Kafka + Schema Registry
     */
    private void ensureTableExists() {
        try {
            // Check if table already exists
            spark.table(streamConfig.table);
            logger.info("Table already exists: {}", streamConfig.table);
        } catch (Exception e) {
            logger.info("Table does not exist, creating: {}", streamConfig.table);
            createTableFromKafkaSample();
        }
    }

    /**
     * Read one micro-batch from Kafka to infer schema and create the table
     */
    private void createTableFromKafkaSample() {
        logger.info("Reading sample batch from Kafka topic: {}", streamConfig.topic);

        // Read a small batch from Kafka (NOT streaming - use batch read)
        Dataset<Row> sampleBatch = spark
                .read()  // Batch read, not readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", config.getKafkaBootstrapServers())
                .option("subscribe", streamConfig.topic)
                .option("startingOffsets", "earliest")
                .option("endingOffsets", "latest")  // Read up to latest available
                .load()
                .limit(10);  // Get a small sample

        // Check if we got any data
        if (sampleBatch.isEmpty()) {
            logger.warn("No data available in topic {} to infer schema. Table creation will be deferred.",
                        streamConfig.topic);
            return;  // Skip table creation if no data yet
        }

        // Transform using same logic as streaming
        Dataset<Row> transformedSample = transform(sampleBatch);

        logger.info("Creating table with inferred schema: {}", streamConfig.table);
        logger.info("Schema: \n{}", transformedSample.schema().treeString());

        // Write sample data to create the table (batch write)
        transformedSample.write()
                .format("iceberg")
                .mode("append")
                .saveAsTable(streamConfig.table);

        logger.info("Table created successfully: {}", streamConfig.table);
    }

    @Override
    protected Dataset<Row> readStream() {
        logger.info("Reading from Kafka topic: {}", streamConfig.topic);

        return spark
                .readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", config.getKafkaBootstrapServers())
                .option("subscribe", streamConfig.topic)
                .option("startingOffsets", config.getStartingOffsets())
                .option("failOnDataLoss", String.valueOf(config.getFailOnDataLoss()))
                .load();
    }

    @Override
    protected Dataset<Row> transform(Dataset<Row> input) {
        logger.info("Deserializing Avro from Schema Registry");

        // Deserialize Avro and add bronze metadata
        return AvroUtils.prepareBronzeData(
                input,
                streamConfig.topic,
                config.getSchemaRegistryUrl()
        );
    }

    @Override
    protected StreamingQuery writeStream(Dataset<Row> output) throws Exception {
        logger.info("Writing to Iceberg table: {}", streamConfig.table);
        logger.info("Checkpoint location: {}", streamConfig.checkpointPath);

        return output
                .writeStream()
                .format("iceberg")
                .outputMode("append")
                .trigger(Trigger.ProcessingTime(config.getTriggerInterval()))
                .option("checkpointLocation", streamConfig.checkpointPath)
                .option("fanout-enabled", "true")  // Enable table creation
                .toTable(streamConfig.table);
    }
}
