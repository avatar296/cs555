-- Monitoring: silver_quality_metrics Table

CREATE TABLE IF NOT EXISTS lakehouse.monitoring.silver_quality_metrics (
  timestamp TIMESTAMP NOT NULL COMMENT 'When validation ran',
  table_name STRING NOT NULL COMMENT 'Which Silver table was validated',
  job_name STRING NOT NULL COMMENT 'Which Spark streaming job produced this batch',
  batch_id BIGINT NOT NULL COMMENT 'Unique batch identifier from Spark Structured Streaming',
  record_count BIGINT NOT NULL COMMENT 'Number of records in this batch',
  status STRING NOT NULL COMMENT 'Overall validation status: Success, Error, or Warning',
  all_checks_passed BOOLEAN NOT NULL COMMENT 'True if all Deequ constraints passed',
  passed_checks BIGINT NOT NULL COMMENT 'Number of Deequ constraint checks that passed',
  failed_checks BIGINT NOT NULL COMMENT 'Number of Deequ constraint checks that failed',
  constraint_failures STRING COMMENT 'JSON array of constraint violation details',
  batch_quarantined BOOLEAN NOT NULL COMMENT 'True if batch was sent to quarantine tables'
)
USING iceberg
COMMENT 'Deequ quality validation metrics for Silver layer';
