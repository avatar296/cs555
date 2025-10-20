package csx55.sta.streaming.base;

import csx55.sta.streaming.config.StreamConfig;
import csx55.sta.streaming.factory.IcebergSessionBuilder;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Base class for all streaming jobs using Template Method pattern. */
public abstract class BaseStreamingJob {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final StreamConfig config;
    protected SparkSession spark;

    public BaseStreamingJob(StreamConfig config) {
        this.config = config;
    }

    /** Main entry point - executes the streaming job lifecycle. */
    public void run() throws Exception {
        try {
            logger.info("Starting streaming job: {}", getJobName());

            // Initialize Spark session
            this.spark = createSparkSession();

            // Initialize job-specific setup (create namespaces, tables, etc.)
            initialize();

            // Read streaming data
            Dataset<Row> stream = readStream();

            // Transform data
            Dataset<Row> transformed = transform(stream);

            // Write stream
            StreamingQuery query = writeStream(transformed);

            logger.info("Streaming query started: {}", query.name());

            // Wait for termination
            query.awaitTermination();

        } catch (Exception e) {
            logger.error("Streaming job failed: {}", getJobName(), e);
            throw e;
        } finally {
            cleanup();
        }
    }

    /** Create Spark session - can be overridden for custom configuration. */
    protected SparkSession createSparkSession() {
        return IcebergSessionBuilder.createSession(getJobName(), config);
    }

    /** Get the job name for logging and Spark app name. */
    protected abstract String getJobName();

    /** Initialize resources (namespaces, tables, etc.). */
    protected void initialize() {
        String namespace = getNamespace();
        if (namespace != null) {
            logger.debug("Creating namespace if not exists: {}", namespace);
            spark.sql(String.format("CREATE NAMESPACE IF NOT EXISTS %s", namespace));
        }
    }

    /** Read streaming data from source. */
    protected abstract Dataset<Row> readStream();

    /** Transform the data. */
    protected abstract Dataset<Row> transform(Dataset<Row> input);

    /** Write the transformed stream to destination. */
    protected abstract StreamingQuery writeStream(Dataset<Row> output) throws Exception;

    /** Get the namespace for this job's tables (e.g., "lakehouse.bronze"). */
    protected String getNamespace() {
        return null; // Override in subclasses if needed
    }

    /** Cleanup resources. */
    protected void cleanup() {
        if (spark != null) {
            logger.debug("Stopping Spark session");
            spark.stop();
        }
    }
}
