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

    /**
     * Initialize the job
     * NOTE: Tables are expected to be pre-created by BronzeTableSetup
     */
    @Override
    protected void initialize() {
        super.initialize();
        verifyTableExists();
    }

    /**
     * Verify that the table exists before starting streaming
     */
    private void verifyTableExists() {
        try {
            spark.table(streamConfig.table);
            logger.info("Verified table exists: {}", streamConfig.table);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Table " + streamConfig.table + " does not exist. " +
                    "Run BronzeTableSetup first to create tables.", e);
        }
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
