-- Monitoring: pipeline_latency_metrics Table

CREATE TABLE IF NOT EXISTS lakehouse.monitoring.pipeline_latency_metrics (
  window_start TIMESTAMP COMMENT 'Start of 5-minute aggregation window',
  window_end TIMESTAMP COMMENT 'End of 5-minute aggregation window',
  events_processed BIGINT NOT NULL COMMENT 'Total number of events processed in this window',
  events_per_second DOUBLE COMMENT 'Average throughput (events/second) in this window',
  latency_p50_ms BIGINT NOT NULL COMMENT '50th percentile (median) end-to-end latency in milliseconds',
  latency_p95_ms BIGINT NOT NULL COMMENT '95th percentile end-to-end latency in milliseconds',
  latency_p99_ms BIGINT NOT NULL COMMENT '99th percentile end-to-end latency in milliseconds',
  min_latency_ms BIGINT NOT NULL COMMENT 'Minimum end-to-end latency in milliseconds',
  max_latency_ms BIGINT NOT NULL COMMENT 'Maximum end-to-end latency in milliseconds',
  avg_latency_ms DOUBLE NOT NULL COMMENT 'Average end-to-end latency in milliseconds'
)
USING iceberg
PARTITIONED BY (days(window_start))
TBLPROPERTIES (
  'write.format.default' = 'parquet',
  'write.metadata.compression-codec' = 'gzip',
  'write.distribution-mode' = 'hash'
)
COMMENT 'End-to-end pipeline latency and throughput metrics from producer to Gold layer';
