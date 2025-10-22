package csx55.sta.streaming.base;

import csx55.sta.streaming.config.StreamConfig;
import csx55.sta.streaming.factory.IcebergSessionBuilder;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseStreamingJob {
  protected final Logger logger = LoggerFactory.getLogger(getClass());
  protected final StreamConfig config;
  protected SparkSession spark;

  public BaseStreamingJob(StreamConfig config) {
    this.config = config;
  }

  public void run() throws Exception {
    try {
      this.spark = createSparkSession();

      initialize();

      Dataset<Row> stream = readStream();

      Dataset<Row> transformed = transform(stream);

      StreamingQuery query = writeStream(transformed);

      query.awaitTermination();

    } catch (Exception e) {
      logger.error("Streaming job failed: {}", getJobName(), e);
      throw e;
    } finally {
      cleanup();
    }
  }

  protected SparkSession createSparkSession() {
    return IcebergSessionBuilder.createSession(getJobName(), config);
  }

  protected abstract String getJobName();

  protected void initialize() {
    String namespace = getNamespace();
    if (namespace != null) {
      spark.sql(String.format("CREATE NAMESPACE IF NOT EXISTS %s", namespace));
    }
  }

  protected abstract Dataset<Row> readStream();

  protected abstract Dataset<Row> transform(Dataset<Row> input);

  protected abstract StreamingQuery writeStream(Dataset<Row> output) throws Exception;

  protected String getNamespace() {
    return null;
  }

  protected void cleanup() {
    if (spark != null) {
      spark.stop();
    }
  }
}
