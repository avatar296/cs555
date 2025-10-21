-- Gold Layer: location_dim Table
-- Dimension table for NYC Taxi Zone locations

CREATE TABLE IF NOT EXISTS lakehouse.gold.location_dim (
  location_id BIGINT NOT NULL COMMENT 'Location ID (TLC Taxi Zone ID) - primary key',
  zone_name STRING COMMENT 'Zone name (e.g., JFK Airport, Times Square)',
  borough STRING COMMENT 'Borough (Manhattan, Brooklyn, Queens, Bronx, Staten Island, EWR)',
  service_zone STRING COMMENT 'Service zone type (Yellow Zone, Boro Zone, Airports, EWR)'
)
USING iceberg
TBLPROPERTIES (
  'write.format.default' = 'parquet',
  'write.metadata.compression-codec' = 'gzip',
  'write.parquet.compression-codec' = 'zstd'
)
COMMENT 'Location dimension for NYC Taxi Zones';
