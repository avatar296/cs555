-- Silver Layer: events_cleaned Table

CREATE TABLE IF NOT EXISTS lakehouse.silver.events_cleaned (
  -- Core fields
  timestamp TIMESTAMP NOT NULL COMMENT 'Event announcement/update timestamp',
  location_id BIGINT NOT NULL COMMENT 'Location identifier (TLC Taxi Zone ID)',
  event_type STRING NOT NULL COMMENT 'Event type: SPORTS, CONCERT, CONFERENCE, FESTIVAL, PARADE, OTHER',
  attendance_estimate INT NOT NULL COMMENT 'Estimated attendance',
  start_time BIGINT NOT NULL COMMENT 'Event start time (epoch milliseconds)',
  end_time BIGINT NOT NULL COMMENT 'Event end time (epoch milliseconds)',
  venue_name STRING COMMENT 'Venue name',
  ingestion_timestamp TIMESTAMP NOT NULL COMMENT 'Bronze layer ingestion timestamp',
  bronze_offset BIGINT NOT NULL COMMENT 'Kafka offset for traceability',
  bronze_partition INT NOT NULL COMMENT 'Kafka partition for traceability',

  -- Derived features
  is_major_event BOOLEAN NOT NULL COMMENT 'Major event (>20K attendance or >10K for SPORTS/CONCERT)',
  event_size_category STRING NOT NULL COMMENT 'Event size: small, medium, large, mega',
  event_duration_hours DOUBLE NOT NULL COMMENT 'Event duration in hours',
  overlaps_rush_hour BOOLEAN NOT NULL COMMENT 'Event overlaps rush hours (7-9am, 4-7pm)',
  is_weekend_event BOOLEAN NOT NULL COMMENT 'Event on Saturday or Sunday',
  demand_category STRING NOT NULL COMMENT 'Expected demand: high_demand, medium_demand, low_demand',
  event_start_hour INT NOT NULL COMMENT 'Hour when event starts (0-23)',
  event_end_hour INT NOT NULL COMMENT 'Hour when event ends (0-23)',
  has_venue_details BOOLEAN NOT NULL COMMENT 'Whether venue name is available',
  duration_category STRING NOT NULL COMMENT 'Duration: brief, moderate, extended, multi_day'
)
USING iceberg
PARTITIONED BY (months(timestamp))
TBLPROPERTIES (
  'write.format.default' = 'parquet',
  'write.metadata.compression-codec' = 'gzip',
  'write.parquet.compression-codec' = 'zstd',
  'write.distribution-mode' = 'hash'
)
COMMENT 'Silver layer: Cleaned and enriched special events data';
