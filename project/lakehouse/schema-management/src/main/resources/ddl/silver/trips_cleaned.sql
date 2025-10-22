CREATE TABLE IF NOT EXISTS lakehouse.silver.trips_cleaned (
  timestamp TIMESTAMP NOT NULL,
  pickup_location_id BIGINT NOT NULL,
  dropoff_location_id BIGINT NOT NULL,
  trip_distance DOUBLE NOT NULL,
  fare_amount DOUBLE NOT NULL,
  passenger_count INT NOT NULL,
  ingestion_timestamp TIMESTAMP NOT NULL,
  bronze_offset BIGINT NOT NULL,
  bronze_partition INT NOT NULL,
  is_rush_hour BOOLEAN NOT NULL,
  is_weekend BOOLEAN NOT NULL,
  hour_of_day INT NOT NULL,
  day_of_week STRING NOT NULL,
  fare_per_mile DOUBLE,
  distance_category STRING NOT NULL,
  fare_category STRING NOT NULL
)
USING iceberg
PARTITIONED BY (days(timestamp))
TBLPROPERTIES (
  'write.format.default' = 'parquet',
  'write.metadata.compression-codec' = 'gzip',
  'write.parquet.compression-codec' = 'zstd',
  'write.distribution-mode' = 'hash'
);
