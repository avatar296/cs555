CREATE TABLE IF NOT EXISTS lakehouse.silver.weather_cleaned (
  timestamp TIMESTAMP NOT NULL,
  location_id BIGINT NOT NULL,
  temperature DOUBLE NOT NULL,
  precipitation DOUBLE NOT NULL,
  wind_speed DOUBLE NOT NULL,
  condition STRING NOT NULL,
  ingestion_timestamp TIMESTAMP NOT NULL,
  bronze_offset BIGINT NOT NULL,
  bronze_partition INT NOT NULL,
  is_severe_weather BOOLEAN NOT NULL,
  weather_impact_level INT NOT NULL,
  temperature_category STRING NOT NULL,
  precipitation_category STRING NOT NULL,
  wind_category STRING NOT NULL,
  impacts_travel BOOLEAN NOT NULL
)
USING iceberg
PARTITIONED BY (days(timestamp))
TBLPROPERTIES (
  'write.format.default' = 'parquet',
  'write.metadata.compression-codec' = 'gzip',
  'write.parquet.compression-codec' = 'zstd',
  'write.distribution-mode' = 'hash'
);
