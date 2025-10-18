package csx55.sta.streaming.factory;

import csx55.sta.streaming.config.StreamConfig;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating Spark sessions configured for Iceberg and S3/MinIO
 */
public class IcebergSessionBuilder {
    private static final Logger logger = LoggerFactory.getLogger(IcebergSessionBuilder.class);

    private final StreamConfig config;
    private final String appName;

    public IcebergSessionBuilder(String appName, StreamConfig config) {
        this.appName = appName;
        this.config = config;
    }

    /**
     * Build a Spark session with Iceberg and S3 configuration
     */
    public SparkSession build() {
        logger.info("Creating Spark session: {}", appName);

        String catalogName = config.getIcebergCatalogName();
        String catalogType = config.getIcebergCatalogType();
        String warehousePath = config.getIcebergWarehousePath();

        SparkSession.Builder builder = SparkSession.builder()
                .appName(appName)
                // Iceberg Catalog Extensions
                .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
                // Iceberg Catalog Configuration
                .config(String.format("spark.sql.catalog.%s", catalogName), "org.apache.iceberg.spark.SparkCatalog")
                .config(String.format("spark.sql.catalog.%s.type", catalogName), catalogType)
                .config(String.format("spark.sql.catalog.%s.warehouse", catalogName), warehousePath);

        // Add JDBC configuration if catalog type is "jdbc"
        if ("jdbc".equalsIgnoreCase(catalogType)) {
            String jdbcUri = config.getJdbcUri();
            builder.config(String.format("spark.sql.catalog.%s.uri", catalogName), jdbcUri);
            builder.config(String.format("spark.sql.catalog.%s.jdbc.user", catalogName), config.getJdbcUser());
            builder.config(String.format("spark.sql.catalog.%s.jdbc.password", catalogName), config.getJdbcPassword());
            logger.info("Configured JDBC Catalog URI: {}", jdbcUri);
        }

        SparkSession spark = builder
                // S3/MinIO Configuration
                .config("spark.hadoop.fs.s3a.endpoint", config.getS3Endpoint())
                .config("spark.hadoop.fs.s3a.access.key", config.getS3AccessKey())
                .config("spark.hadoop.fs.s3a.secret.key", config.getS3SecretKey())
                .config("spark.hadoop.fs.s3a.path.style.access", String.valueOf(config.getS3PathStyleAccess()))
                .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
                .config("spark.hadoop.fs.s3a.connection.ssl.enabled", String.valueOf(config.getS3SslEnabled()))
                .getOrCreate();

        logger.info("Spark session created successfully");
        logger.info("Iceberg catalog: {} (type: {}, warehouse: {})", catalogName, catalogType, warehousePath);
        logger.info("S3 endpoint: {}", config.getS3Endpoint());

        return spark;
    }

    /**
     * Convenience method to create and return a session
     */
    public static SparkSession createSession(String appName, StreamConfig config) {
        return new IcebergSessionBuilder(appName, config).build();
    }
}
