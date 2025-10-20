-- Silver Layer: trips_cleaned Table

CREATE TABLE IF NOT EXISTS lakehouse.silver.trips_cleaned (
  -- Core fields
  timestamp TIMESTAMP NOT NULL COMMENT 'Trip timestamp',
  pickup_location_id BIGINT NOT NULL COMMENT 'TLC Taxi Zone where trip started',
  dropoff_location_id BIGINT NOT NULL COMMENT 'TLC Taxi Zone where trip ended',
  trip_distance DOUBLE NOT NULL COMMENT 'Trip distance in miles',
  fare_amount DOUBLE NOT NULL COMMENT 'Fare amount in USD',
  passenger_count INT NOT NULL COMMENT 'Number of passengers',
  bronze_ingestion_time TIMESTAMP NOT NULL COMMENT 'Bronze layer ingestion timestamp',
  bronze_offset BIGINT NOT NULL COMMENT 'Kafka offset for traceability',
  bronze_partition INT NOT NULL COMMENT 'Kafka partition for traceability',

  -- Derived features
  is_rush_hour BOOLEAN NOT NULL COMMENT 'Trip during rush hour (7-9am, 4-7pm)',
  is_weekend BOOLEAN NOT NULL COMMENT 'Trip on Saturday or Sunday',
  hour_of_day INT NOT NULL COMMENT 'Hour of day (0-23)',
  day_of_week STRING NOT NULL COMMENT 'Day name',
  fare_per_mile DOUBLE COMMENT 'Fare amount divided by trip distance',
  distance_category STRING NOT NULL COMMENT 'Distance category: short, medium, long, very_long',
  fare_category STRING NOT NULL COMMENT 'Fare category: economy, standard, premium, luxury',

  -- Quality tracking
  was_duplicate BOOLEAN NOT NULL COMMENT 'Record identified as duplicate during deduplication'
)
USING iceberg
COMMENT 'Silver layer: Cleaned and enriched NYC taxi trips';
