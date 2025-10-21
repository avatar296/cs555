-- Gold Layer: Pipeline Latency Metrics
-- Streaming aggregation with 5-minute tumbling windows
-- Measures end-to-end latency from producer to Gold layer

SELECT
  -- Time window dimensions (explicitly non-nullable by selecting from named_struct)
  CAST(window.start AS TIMESTAMP) AS window_start,
  CAST(window.end AS TIMESTAMP) AS window_end,

  -- Throughput metrics (COUNT is non-nullable, but division produces nullable)
  COUNT(*) AS events_processed,
  CAST(CAST(COUNT(*) AS DOUBLE) / 300.0 AS DOUBLE) AS events_per_second,  -- Explicit DOUBLE cast

  -- Latency percentiles (in milliseconds) - COALESCE to handle nulls
  COALESCE(CAST(approx_percentile(latency_ms, 0.50) AS BIGINT), 0) AS latency_p50_ms,
  COALESCE(CAST(approx_percentile(latency_ms, 0.95) AS BIGINT), 0) AS latency_p95_ms,
  COALESCE(CAST(approx_percentile(latency_ms, 0.99) AS BIGINT), 0) AS latency_p99_ms,

  -- Min/max/avg latency - COALESCE to handle nulls
  COALESCE(MIN(latency_ms), 0) AS min_latency_ms,
  COALESCE(MAX(latency_ms), 0) AS max_latency_ms,
  COALESCE(AVG(latency_ms), 0.0) AS avg_latency_ms

FROM (
  SELECT
    timestamp,
    -- Calculate latency: current processing time minus event timestamp
    (unix_timestamp(current_timestamp()) - unix_timestamp(timestamp)) * 1000 AS latency_ms
  FROM lakehouse_silver_trips_cleaned
)

-- Group by 5-minute tumbling windows on the event timestamp
GROUP BY window(timestamp, '5 minutes')
