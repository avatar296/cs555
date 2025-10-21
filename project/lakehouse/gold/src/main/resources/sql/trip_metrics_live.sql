-- Gold Layer: Trip Metrics Live Aggregation
-- Streaming aggregation with 5-minute tumbling windows
-- Output mode: update (continuously updates existing aggregates)

SELECT
  -- Time window dimensions
  window.start AS window_start,
  window.end AS window_end,

  -- Location dimensions
  pickup_location_id,
  dropoff_location_id,

  -- Core aggregated metrics
  COUNT(*) AS trip_count,
  COALESCE(SUM(fare_amount), 0.0) AS total_revenue,
  COALESCE(SUM(trip_distance), 0.0) AS total_distance,
  COALESCE(SUM(passenger_count), 0) AS total_passengers,

  -- Average metrics
  COALESCE(AVG(fare_amount), 0.0) AS avg_fare,
  COALESCE(AVG(trip_distance), 0.0) AS avg_distance,
  COALESCE(AVG(passenger_count), 0.0) AS avg_passengers,
  AVG(fare_per_mile) AS avg_fare_per_mile,

  -- Conditional aggregations: Rush hour breakdown
  COALESCE(SUM(CASE WHEN is_rush_hour = true THEN 1 ELSE 0 END), 0) AS rush_hour_trip_count,
  COALESCE(SUM(CASE WHEN is_weekend = true THEN 1 ELSE 0 END), 0) AS weekend_trip_count,

  -- Fare category breakdown
  COALESCE(SUM(CASE WHEN fare_category = 'economy' THEN 1 ELSE 0 END), 0) AS economy_fare_count,
  COALESCE(SUM(CASE WHEN fare_category = 'standard' THEN 1 ELSE 0 END), 0) AS standard_fare_count,
  COALESCE(SUM(CASE WHEN fare_category = 'premium' THEN 1 ELSE 0 END), 0) AS premium_fare_count,
  COALESCE(SUM(CASE WHEN fare_category = 'luxury' THEN 1 ELSE 0 END), 0) AS luxury_fare_count,

  -- Distance category breakdown
  COALESCE(SUM(CASE WHEN distance_category = 'short' THEN 1 ELSE 0 END), 0) AS short_trip_count,
  COALESCE(SUM(CASE WHEN distance_category = 'medium' THEN 1 ELSE 0 END), 0) AS medium_trip_count,
  COALESCE(SUM(CASE WHEN distance_category = 'long' THEN 1 ELSE 0 END), 0) AS long_trip_count,
  COALESCE(SUM(CASE WHEN distance_category = 'very_long' THEN 1 ELSE 0 END), 0) AS very_long_trip_count,

  -- Metadata: when was this aggregate last updated
  CURRENT_TIMESTAMP() AS last_updated

FROM lakehouse_silver_trips_cleaned

-- Group by 5-minute tumbling windows and location pairs
GROUP BY
  window(timestamp, '5 minutes'),
  pickup_location_id,
  dropoff_location_id
