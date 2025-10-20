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

        // Create table with partition spec using SQL DDL
        String partitionSpec = getPartitionSpec(streamConfig.topic);
        logger.info("Creating table {} with partition spec: {}", streamConfig.table, partitionSpec);

        String createTableDDL = generateCreateTableSQL(streamConfig.table, transformedSample, partitionSpec);
        logger.debug("Executing DDL: {}", createTableDDL);
        spark.sql(createTableDDL);

        // Write sample data to verify table creation
        transformedSample.write()
                .format("iceberg")
                .mode("append")
                .saveAsTable(streamConfig.table);

        logger.info("Table created: {} (partitioned by {})", streamConfig.table, partitionSpec);
    }

    private String getPartitionSpec(String topic) {
        // Events have lower volume, use daily partitioning
        if (topic.contains("events")) {
            return "days(ingestion_timestamp)";
        }
        // Trips and weather have high volume, use hourly partitioning
        return "hours(ingestion_timestamp)";
    }

    private String generateCreateTableSQL(String tableName, Dataset<Row> sample, String partitionSpec) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");

        // Add columns from schema
        org.apache.spark.sql.types.StructField[] fields = sample.schema().fields();
        for (int i = 0; i < fields.length; i++) {
            org.apache.spark.sql.types.StructField field = fields[i];
            ddl.append("  ").append(field.name()).append(" ").append(sqlTypeFor(field.dataType()));

            if (!field.nullable()) {
                ddl.append(" NOT NULL");
            }

            if (i < fields.length - 1) {
                ddl.append(",\n");
            } else {
                ddl.append("\n");
            }
        }

        ddl.append(")\n");
        ddl.append("USING iceberg\n");
        ddl.append("PARTITIONED BY (").append(partitionSpec).append(")\n");
        ddl.append("TBLPROPERTIES (\n");
        ddl.append("  'write.format.default' = 'parquet',\n");
        ddl.append("  'write.metadata.compression-codec' = 'gzip',\n");
        ddl.append("  'write.distribution-mode' = 'hash'\n");
        ddl.append(")");

        return ddl.toString();
    }

    private String sqlTypeFor(org.apache.spark.sql.types.DataType dataType) {
        if (dataType instanceof org.apache.spark.sql.types.StringType) {
            return "STRING";
        } else if (dataType instanceof org.apache.spark.sql.types.LongType) {
            return "BIGINT";
        } else if (dataType instanceof org.apache.spark.sql.types.IntegerType) {
            return "INT";
        } else if (dataType instanceof org.apache.spark.sql.types.DoubleType) {
            return "DOUBLE";
        } else if (dataType instanceof org.apache.spark.sql.types.FloatType) {
            return "FLOAT";
        } else if (dataType instanceof org.apache.spark.sql.types.BooleanType) {
            return "BOOLEAN";
        } else if (dataType instanceof org.apache.spark.sql.types.TimestampType) {
            return "TIMESTAMP";
        } else if (dataType instanceof org.apache.spark.sql.types.DateType) {
            return "DATE";
        } else if (dataType instanceof org.apache.spark.sql.types.BinaryType) {
            return "BINARY";
        } else {
            return dataType.sql();
        }
    }
}
