SELECT
  CAST(window.start AS TIMESTAMP) AS window_start,
  CAST(window.end AS TIMESTAMP) AS window_end,
  COUNT(*) AS events_processed,
  CAST(CAST(COUNT(*) AS DOUBLE) / 120.0 AS DOUBLE) AS events_per_second,

  COALESCE(CAST(approx_percentile(latency_ms, 0.50, 1000) AS BIGINT), 0) AS latency_p50_ms,
  COALESCE(CAST(approx_percentile(latency_ms, 0.95, 1000) AS BIGINT), 0) AS latency_p95_ms,
  COALESCE(CAST(approx_percentile(latency_ms, 0.99, 1000) AS BIGINT), 0) AS latency_p99_ms,
  COALESCE(MIN(latency_ms), 0) AS min_latency_ms,
  COALESCE(MAX(latency_ms), 0) AS max_latency_ms,
  COALESCE(AVG(latency_ms), 0.0) AS avg_latency_ms

FROM
(
  SELECT
  timestamp,
  (unix_timestamp(current_timestamp()) - unix_timestamp(timestamp)) * 1000 AS latency_ms
  FROM lakehouse_silver_trips_cleaned
)

GROUP BY window(timestamp, '2 minutes')
