-- Silver Layer: weather_cleaned Table

CREATE TABLE IF NOT EXISTS lakehouse.silver.weather_cleaned (
  -- Core fields
  timestamp TIMESTAMP NOT NULL COMMENT 'Weather observation timestamp',
  location_id BIGINT NOT NULL COMMENT 'Location identifier (TLC Taxi Zone ID)',
  temperature DOUBLE NOT NULL COMMENT 'Temperature in Fahrenheit',
  precipitation DOUBLE NOT NULL COMMENT 'Precipitation amount in inches',
  wind_speed DOUBLE NOT NULL COMMENT 'Wind speed in miles per hour',
  condition STRING NOT NULL COMMENT 'Weather condition: CLEAR, RAIN, SNOW, FOG, STORM',
  bronze_ingestion_time TIMESTAMP NOT NULL COMMENT 'Bronze layer ingestion timestamp',
  bronze_offset BIGINT NOT NULL COMMENT 'Kafka offset for traceability',
  bronze_partition INT NOT NULL COMMENT 'Kafka partition for traceability',

  -- Derived features
  is_severe_weather BOOLEAN NOT NULL COMMENT 'Severe weather (SNOW/STORM, precip >1in, or wind >30mph)',
  weather_impact_level INT NOT NULL COMMENT 'Weather impact: 0=none, 1=low, 2=medium, 3=high',
  temperature_category STRING NOT NULL COMMENT 'Temperature category: freezing, cold, mild, warm, hot',
  precipitation_category STRING NOT NULL COMMENT 'Precipitation category: none, light, moderate, heavy',
  wind_category STRING NOT NULL COMMENT 'Wind category: calm, breezy, windy, dangerous',
  impacts_travel BOOLEAN NOT NULL COMMENT 'Whether conditions impact travel patterns',

  -- Quality tracking
  was_duplicate BOOLEAN NOT NULL COMMENT 'Record identified as duplicate during deduplication'
)
USING iceberg
COMMENT 'Silver layer: Cleaned and enriched weather data';
