package csx55.sta.streaming.factory;

import csx55.sta.streaming.config.StreamConfig;
import org.apache.spark.sql.SparkSession;

public class IcebergSessionBuilder {
  private final StreamConfig config;
  private final String appName;

  public IcebergSessionBuilder(String appName, StreamConfig config) {
    this.appName = appName;
    this.config = config;
  }

  public SparkSession build() {
    String catalogName = config.getIcebergCatalogName();
    String catalogType = config.getIcebergCatalogType();
    String warehousePath = config.getIcebergWarehousePath();

    SparkSession.Builder builder =
        SparkSession.builder()
            .appName(appName)
            .config(
                "spark.sql.extensions",
                "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
            .config(
                String.format("spark.sql.catalog.%s", catalogName),
                "org.apache.iceberg.spark.SparkCatalog")
            .config(String.format("spark.sql.catalog.%s.type", catalogName), catalogType)
            .config(String.format("spark.sql.catalog.%s.warehouse", catalogName), warehousePath);

    if ("jdbc".equalsIgnoreCase(catalogType)) {
      String jdbcUri = config.getJdbcUri();
      builder.config(String.format("spark.sql.catalog.%s.uri", catalogName), jdbcUri);
      builder.config(
          String.format("spark.sql.catalog.%s.jdbc.user", catalogName), config.getJdbcUser());
      builder.config(
          String.format("spark.sql.catalog.%s.jdbc.password", catalogName),
          config.getJdbcPassword());
    }

    SparkSession spark =
        builder
            .config("spark.hadoop.fs.s3a.endpoint", config.getS3Endpoint())
            .config("spark.hadoop.fs.s3a.access.key", config.getS3AccessKey())
            .config("spark.hadoop.fs.s3a.secret.key", config.getS3SecretKey())
            .config(
                "spark.hadoop.fs.s3a.path.style.access",
                String.valueOf(config.getS3PathStyleAccess()))
            .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
            .config(
                "spark.hadoop.fs.s3a.connection.ssl.enabled",
                String.valueOf(config.getS3SslEnabled()))
            .getOrCreate();

    return spark;
  }

  public static SparkSession createSession(String appName, StreamConfig config) {
    return new IcebergSessionBuilder(appName, config).build();
  }
}
