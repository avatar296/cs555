package csx55.sta.streaming.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Configuration management for streaming jobs
 * Loads configuration from application.conf with environment variable overrides
 */
public class StreamConfig {
    private final Config config;

    public StreamConfig() {
        this.config = ConfigFactory.load();
    }

    public StreamConfig(Config config) {
        this.config = config;
    }

    // Kafka Configuration
    public String getKafkaBootstrapServers() {
        return config.getString("kafka.bootstrap.servers");
    }

    public String getSchemaRegistryUrl() {
        return config.getString("schema-registry.url");
    }

    // Iceberg Configuration
    public String getIcebergCatalogName() {
        return config.getString("iceberg.catalog.name");
    }

    public String getIcebergCatalogType() {
        return config.getString("iceberg.catalog.type");
    }

    public String getIcebergWarehousePath() {
        return config.getString("iceberg.warehouse.path");
    }

    public String getJdbcUri() {
        return config.getString("iceberg.jdbc.uri");
    }

    public String getJdbcUser() {
        return config.getString("iceberg.jdbc.user");
    }

    public String getJdbcPassword() {
        return config.getString("iceberg.jdbc.password");
    }

    // S3/MinIO Configuration
    public String getS3Endpoint() {
        return config.getString("s3.endpoint");
    }

    public String getS3AccessKey() {
        return config.getString("s3.access.key");
    }

    public String getS3SecretKey() {
        return config.getString("s3.secret.key");
    }

    public boolean getS3PathStyleAccess() {
        return config.getBoolean("s3.path.style.access");
    }

    public boolean getS3SslEnabled() {
        return config.getBoolean("s3.ssl.enabled");
    }

    // Checkpoint Configuration
    public String getCheckpointBasePath() {
        return config.getString("checkpoint.base.path");
    }

    // Streaming Configuration
    public String getTriggerInterval() {
        return config.getString("streaming.trigger.interval");
    }

    public String getStartingOffsets() {
        return config.getString("streaming.starting.offsets");
    }

    public boolean getFailOnDataLoss() {
        return config.getBoolean("streaming.fail.on.data.loss");
    }

    // Bronze Layer Configuration
    public BronzeStreamConfig getBronzeTripsConfig() {
        return new BronzeStreamConfig(
            config.getString("bronze.trips.topic"),
            config.getString("bronze.trips.table"),
            config.getString("bronze.trips.checkpoint")
        );
    }

    public BronzeStreamConfig getBronzeWeatherConfig() {
        return new BronzeStreamConfig(
            config.getString("bronze.weather.topic"),
            config.getString("bronze.weather.table"),
            config.getString("bronze.weather.checkpoint")
        );
    }

    public BronzeStreamConfig getBronzeEventsConfig() {
        return new BronzeStreamConfig(
            config.getString("bronze.events.topic"),
            config.getString("bronze.events.table"),
            config.getString("bronze.events.checkpoint")
        );
    }

    // Silver Layer Configuration
    public SilverStreamConfig getSilverTripsCleanedConfig() {
        return new SilverStreamConfig(
            config.getString("silver.trips_cleaned.source"),
            config.getString("silver.trips_cleaned.table"),
            config.getString("silver.trips_cleaned.checkpoint")
        );
    }

    public SilverStreamConfig getSilverWeatherCleanedConfig() {
        return new SilverStreamConfig(
            config.getString("silver.weather_cleaned.source"),
            config.getString("silver.weather_cleaned.table"),
            config.getString("silver.weather_cleaned.checkpoint")
        );
    }

    public SilverStreamConfig getSilverEventsCleanedConfig() {
        return new SilverStreamConfig(
            config.getString("silver.events_cleaned.source"),
            config.getString("silver.events_cleaned.table"),
            config.getString("silver.events_cleaned.checkpoint")
        );
    }

    /**
     * Bronze layer stream configuration
     */
    public static class BronzeStreamConfig {
        public final String topic;
        public final String table;
        public final String checkpointPath;

        public BronzeStreamConfig(String topic, String table, String checkpointPath) {
            this.topic = topic;
            this.table = table;
            this.checkpointPath = checkpointPath;
        }

        @Override
        public String toString() {
            return String.format("BronzeStreamConfig{topic='%s', table='%s', checkpoint='%s'}",
                    topic, table, checkpointPath);
        }
    }

    /**
     * Silver layer stream configuration
     */
    public static class SilverStreamConfig {
        public final String sourceTable;
        public final String targetTable;
        public final String checkpointPath;

        public SilverStreamConfig(String sourceTable, String targetTable, String checkpointPath) {
            this.sourceTable = sourceTable;
            this.targetTable = targetTable;
            this.checkpointPath = checkpointPath;
        }

        @Override
        public String toString() {
            return String.format("SilverStreamConfig{source='%s', target='%s', checkpoint='%s'}",
                    sourceTable, targetTable, checkpointPath);
        }
    }
}
