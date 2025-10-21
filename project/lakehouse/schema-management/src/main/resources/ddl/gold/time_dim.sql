-- Gold Layer: time_dim Table
-- Dimension table providing time-based attributes for analytical queries

CREATE TABLE IF NOT EXISTS lakehouse.gold.time_dim (
  hour_timestamp TIMESTAMP NOT NULL COMMENT 'Hour timestamp (primary key)',
  hour_of_day INT NOT NULL COMMENT 'Hour of day (0-23)',
  day_of_week STRING NOT NULL COMMENT 'Day name (Monday, Tuesday, etc.)',
  day_of_week_num INT NOT NULL COMMENT 'Day of week number (1=Monday, 7=Sunday)',
  day_of_month INT NOT NULL COMMENT 'Day of month (1-31)',
  month INT NOT NULL COMMENT 'Month number (1-12)',
  month_name STRING NOT NULL COMMENT 'Month name (January, February, etc.)',
  quarter INT NOT NULL COMMENT 'Quarter (1-4)',
  year INT NOT NULL COMMENT 'Year',
  is_weekend BOOLEAN NOT NULL COMMENT 'True if Saturday or Sunday',
  is_rush_hour BOOLEAN NOT NULL COMMENT 'True if 7-9am or 4-7pm',
  is_morning_rush BOOLEAN NOT NULL COMMENT 'True if 7-9am',
  is_evening_rush BOOLEAN NOT NULL COMMENT 'True if 4-7pm',
  date_key STRING NOT NULL COMMENT 'Date in YYYY-MM-DD format'
)
USING iceberg
TBLPROPERTIES (
  'write.format.default' = 'parquet',
  'write.metadata.compression-codec' = 'gzip',
  'write.parquet.compression-codec' = 'zstd'
)
COMMENT 'Time dimension for hourly granularity analytics';
