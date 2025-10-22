CREATE TABLE IF NOT EXISTS lakehouse.gold.trip_metrics_live (
  window_start TIMESTAMP,
  window_end TIMESTAMP,
  pickup_location_id BIGINT NOT NULL,
  dropoff_location_id BIGINT NOT NULL,
  trip_count BIGINT NOT NULL,
  total_revenue DOUBLE NOT NULL,
  total_distance DOUBLE NOT NULL,
  total_passengers BIGINT NOT NULL,
  avg_fare DOUBLE NOT NULL,
  avg_distance DOUBLE NOT NULL,
  avg_passengers DOUBLE NOT NULL,
  avg_fare_per_mile DOUBLE,
  rush_hour_trip_count BIGINT NOT NULL,
  weekend_trip_count BIGINT NOT NULL,
  economy_fare_count BIGINT NOT NULL,
  standard_fare_count BIGINT NOT NULL,
  premium_fare_count BIGINT NOT NULL,
  luxury_fare_count BIGINT NOT NULL,
  short_trip_count BIGINT NOT NULL,
  medium_trip_count BIGINT NOT NULL,
  long_trip_count BIGINT NOT NULL,
  very_long_trip_count BIGINT NOT NULL,
  last_updated TIMESTAMP NOT NULL
)
USING iceberg
PARTITIONED BY (days(window_start))
TBLPROPERTIES (
  'write.format.default' = 'parquet',
  'write.metadata.compression-codec' = 'gzip',
  'write.parquet.compression-codec' = 'zstd',
  'write.distribution-mode' = 'hash'
);
