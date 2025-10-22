CREATE TABLE IF NOT EXISTS lakehouse.monitoring.pipeline_latency_metrics (
  window_start TIMESTAMP,
  window_end TIMESTAMP,
  events_processed BIGINT NOT NULL,
  events_per_second DOUBLE,
  latency_p50_ms BIGINT NOT NULL,
  latency_p95_ms BIGINT NOT NULL,
  latency_p99_ms BIGINT NOT NULL,
  min_latency_ms BIGINT NOT NULL,
  max_latency_ms BIGINT NOT NULL,
  avg_latency_ms DOUBLE NOT NULL
)
USING iceberg
PARTITIONED BY (days(window_start))
TBLPROPERTIES (
  'write.format.default' = 'parquet',
  'write.metadata.compression-codec' = 'gzip',
  'write.distribution-mode' = 'hash'
);
