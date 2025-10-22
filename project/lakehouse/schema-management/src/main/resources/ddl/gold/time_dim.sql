CREATE TABLE IF NOT EXISTS lakehouse.gold.time_dim (
  hour_timestamp TIMESTAMP NOT NULL,
  hour_of_day INT NOT NULL,
  day_of_week STRING NOT NULL,
  day_of_week_num INT NOT NULL,
  day_of_month INT NOT NULL,
  month INT NOT NULL,
  month_name STRING NOT NULL,
  quarter INT NOT NULL,
  year INT NOT NULL,
  is_weekend BOOLEAN NOT NULL,
  is_rush_hour BOOLEAN NOT NULL,
  is_morning_rush BOOLEAN NOT NULL,
  is_evening_rush BOOLEAN NOT NULL,
  date_key STRING NOT NULL
)
USING iceberg
TBLPROPERTIES (
  'write.format.default' = 'parquet',
  'write.metadata.compression-codec' = 'gzip',
  'write.parquet.compression-codec' = 'zstd'
);
