package csx55.sta.streaming.utils;

import static org.apache.spark.sql.functions.*;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import za.co.absa.abris.config.AbrisConfig;
import za.co.absa.abris.config.FromAvroConfig;

public class AvroUtils {

  public static Dataset<Row> deserializeAvro(
      Dataset<Row> kafkaStream, String topic, String schemaRegistryUrl) {

    FromAvroConfig abrisConfig =
        AbrisConfig.fromConfluentAvro()
            .downloadReaderSchemaByLatestVersion()
            .andTopicNameStrategy(topic, false)
            .usingSchemaRegistry(schemaRegistryUrl);

    return kafkaStream
        .selectExpr(
            "CAST(key AS STRING) as kafka_key",
            "value",
            "topic",
            "partition",
            "offset",
            "timestamp as kafka_timestamp")
        .withColumn(
            "avro_value", za.co.absa.abris.avro.functions.from_avro(col("value"), abrisConfig))
        .select(
            col("kafka_key"),
            col("topic"),
            col("partition"),
            col("offset"),
            col("kafka_timestamp"),
            col("avro_value.*"));
  }

  public static Dataset<Row> addBronzeMetadata(Dataset<Row> stream) {
    return stream
        .withColumn("ingestion_timestamp", current_timestamp())
        .withColumn("ingestion_date", current_date());
  }

  public static Dataset<Row> prepareBronzeData(
      Dataset<Row> kafkaStream, String topic, String schemaRegistryUrl) {

    Dataset<Row> deserialized = deserializeAvro(kafkaStream, topic, schemaRegistryUrl);
    return addBronzeMetadata(deserialized);
  }
}
