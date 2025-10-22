CREATE TABLE IF NOT EXISTS lakehouse.gold.location_dim (
  location_id BIGINT NOT NULL,
  zone_name STRING,
  borough STRING,
  service_zone STRING
)
USING iceberg
TBLPROPERTIES (
  'write.format.default' = 'parquet',
  'write.metadata.compression-codec' = 'gzip',
  'write.parquet.compression-codec' = 'zstd'
);
