CREATE TABLE IF NOT EXISTS lakehouse.monitoring.silver_quality_metrics (
  timestamp TIMESTAMP NOT NULL,
  table_name STRING NOT NULL,
  job_name STRING NOT NULL,
  batch_id BIGINT NOT NULL,
  record_count BIGINT NOT NULL,
  status STRING NOT NULL,
  all_checks_passed BOOLEAN NOT NULL,
  passed_checks BIGINT NOT NULL,
  failed_checks BIGINT NOT NULL,
  constraint_failures STRING,
  batch_quarantined BOOLEAN NOT NULL
)
USING iceberg
PARTITIONED BY (days(timestamp))
TBLPROPERTIES (
  'write.format.default' = 'parquet',
  'write.metadata.compression-codec' = 'gzip',
  'write.distribution-mode' = 'hash'
);
