package csx55.sta.streaming.utils;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.*;

/**
 * Utilities for working with Avro data in Spark
 */
public class AvroUtils {

    /**
     * Deserialize Avro-encoded Kafka messages using Schema Registry
     *
     * @param kafkaStream     The raw Kafka stream with value column
     * @param topic           The Kafka topic name
     * @param schemaRegistryUrl The Schema Registry URL
     * @return Dataset with deserialized Avro data and Kafka metadata
     */
    public static Dataset<Row> deserializeAvro(
            Dataset<Row> kafkaStream,
            String topic,
            String schemaRegistryUrl) {

        return kafkaStream
                .selectExpr(
                        "CAST(key AS STRING) as kafka_key",
                        "value",
                        "topic",
                        "partition",
                        "offset",
                        "timestamp as kafka_timestamp"
                )
                .withColumn("avro_value",
                        expr(String.format(
                                "from_avro(value, '%s', map('schema.registry.url', '%s'))",
                                topic + "-value",
                                schemaRegistryUrl
                        )))
                .select(
                        col("kafka_key"),
                        col("topic"),
                        col("partition"),
                        col("offset"),
                        col("kafka_timestamp"),
                        col("avro_value.*")
                );
    }

    /**
     * Add bronze layer metadata columns
     *
     * @param stream The input stream
     * @return Stream with ingestion metadata added
     */
    public static Dataset<Row> addBronzeMetadata(Dataset<Row> stream) {
        return stream
                .withColumn("ingestion_timestamp", current_timestamp())
                .withColumn("ingestion_date", current_date());
    }

    /**
     * Deserialize Avro and add bronze metadata in one step
     */
    public static Dataset<Row> prepareBronzeData(
            Dataset<Row> kafkaStream,
            String topic,
            String schemaRegistryUrl) {

        Dataset<Row> deserialized = deserializeAvro(kafkaStream, topic, schemaRegistryUrl);
        return addBronzeMetadata(deserialized);
    }
}
