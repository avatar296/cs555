-- Gold Layer: trip_metrics_live Table
-- Fact table with continuously updated trip metrics
-- GRAIN: One row per 5-minute window per pickup-dropoff location pair

CREATE TABLE IF NOT EXISTS lakehouse.gold.trip_metrics_live (
  -- Time dimension
  window_start TIMESTAMP NOT NULL COMMENT 'Start of 5-minute aggregation window',
  window_end TIMESTAMP NOT NULL COMMENT 'End of 5-minute aggregation window',

  -- Location dimensions
  pickup_location_id BIGINT NOT NULL COMMENT 'Pickup location (TLC Taxi Zone ID)',
  dropoff_location_id BIGINT NOT NULL COMMENT 'Dropoff location (TLC Taxi Zone ID)',

  -- Core metrics
  trip_count BIGINT NOT NULL COMMENT 'Total number of trips in window',
  total_revenue DOUBLE NOT NULL COMMENT 'Sum of all fare amounts',
  total_distance DOUBLE NOT NULL COMMENT 'Sum of all trip distances (miles)',
  total_passengers BIGINT NOT NULL COMMENT 'Sum of all passenger counts',

  -- Average metrics
  avg_fare DOUBLE NOT NULL COMMENT 'Average fare amount per trip',
  avg_distance DOUBLE NOT NULL COMMENT 'Average trip distance (miles)',
  avg_passengers DOUBLE NOT NULL COMMENT 'Average passengers per trip',
  avg_fare_per_mile DOUBLE COMMENT 'Average fare per mile (null if distance = 0)',

  -- Categorical breakdowns
  rush_hour_trip_count BIGINT NOT NULL COMMENT 'Trips during rush hour (7-9am, 4-7pm)',
  weekend_trip_count BIGINT NOT NULL COMMENT 'Trips on Saturday or Sunday',

  -- Fare category counts
  economy_fare_count BIGINT NOT NULL COMMENT 'Trips with economy-level fares',
  standard_fare_count BIGINT NOT NULL COMMENT 'Trips with standard-level fares',
  premium_fare_count BIGINT NOT NULL COMMENT 'Trips with premium-level fares',
  luxury_fare_count BIGINT NOT NULL COMMENT 'Trips with luxury-level fares',

  -- Distance category counts
  short_trip_count BIGINT NOT NULL COMMENT 'Short distance trips',
  medium_trip_count BIGINT NOT NULL COMMENT 'Medium distance trips',
  long_trip_count BIGINT NOT NULL COMMENT 'Long distance trips',
  very_long_trip_count BIGINT NOT NULL COMMENT 'Very long distance trips',

  -- Metadata
  last_updated TIMESTAMP NOT NULL COMMENT 'Timestamp when this aggregate was last updated'
)
USING iceberg
PARTITIONED BY (days(window_start))
TBLPROPERTIES (
  'write.format.default' = 'parquet',
  'write.metadata.compression-codec' = 'gzip',
  'write.parquet.compression-codec' = 'zstd',
  'write.distribution-mode' = 'hash'
)
COMMENT 'Gold layer: Continuously updated trip metrics fact table (5-min windows, streaming update mode)';
