CREATE TABLE IF NOT EXISTS lakehouse.silver.events_cleaned (
  timestamp TIMESTAMP NOT NULL,
  location_id BIGINT NOT NULL,
  event_type STRING NOT NULL,
  attendance_estimate INT NOT NULL,
  start_time BIGINT NOT NULL,
  end_time BIGINT NOT NULL,
  venue_name STRING,
  ingestion_timestamp TIMESTAMP NOT NULL,
  bronze_offset BIGINT NOT NULL,
  bronze_partition INT NOT NULL,
  is_major_event BOOLEAN NOT NULL,
  event_size_category STRING NOT NULL,
  event_duration_hours DOUBLE NOT NULL,
  overlaps_rush_hour BOOLEAN NOT NULL,
  is_weekend_event BOOLEAN NOT NULL,
  demand_category STRING NOT NULL,
  event_start_hour INT NOT NULL,
  event_end_hour INT NOT NULL,
  has_venue_details BOOLEAN NOT NULL,
  duration_category STRING NOT NULL
)
USING iceberg
PARTITIONED BY (months(timestamp))
TBLPROPERTIES (
  'write.format.default' = 'parquet',
  'write.metadata.compression-codec' = 'gzip',
  'write.parquet.compression-codec' = 'zstd',
  'write.distribution-mode' = 'hash'
);
