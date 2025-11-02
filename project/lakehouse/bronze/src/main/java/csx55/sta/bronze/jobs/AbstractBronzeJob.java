package csx55.sta.bronze.jobs;

import csx55.sta.streaming.base.BaseStreamingJob;
import csx55.sta.streaming.config.StreamConfig;
import csx55.sta.streaming.utils.AvroUtils;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.Trigger;

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
    verifyTableExists();
  }

  private void verifyTableExists() {
    try {
      spark.table(streamConfig.table);
    } catch (Exception e) {
      throw new RuntimeException(
          "Table "
              + streamConfig.table
              + " does not exist. "
              + "Run BronzeTableSetup first to create tables.",
          e);
    }
  }

  @Override
  protected Dataset<Row> readStream() {
    return spark
        .readStream()
        .format("kafka")
        .option("kafka.bootstrap.servers", config.getKafkaBootstrapServers())
        .option("subscribe", streamConfig.topic)
        .option("startingOffsets", config.getStartingOffsets())
        .option("failOnDataLoss", config.getFailOnDataLoss())
        .option("maxOffsetsPerTrigger", "2500")
        .load();
  }

  @Override
  protected Dataset<Row> transform(Dataset<Row> input) {
    return AvroUtils.prepareBronzeData(input, streamConfig.topic, config.getSchemaRegistryUrl());
  }

  @Override
  protected StreamingQuery writeStream(Dataset<Row> output) throws Exception {
    return output
        .writeStream()
        .format("iceberg")
        .outputMode("append")
        .trigger(Trigger.ProcessingTime(config.getTriggerInterval()))
        .option("checkpointLocation", streamConfig.checkpointPath)
        .option("fanout-enabled", "true")
        .toTable(streamConfig.table);
  }
}
