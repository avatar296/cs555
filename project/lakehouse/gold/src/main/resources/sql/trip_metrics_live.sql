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
  SUM(fare_amount) AS total_revenue,
  SUM(trip_distance) AS total_distance,
  SUM(passenger_count) AS total_passengers,

  -- Average metrics
  AVG(fare_amount) AS avg_fare,
  AVG(trip_distance) AS avg_distance,
  AVG(passenger_count) AS avg_passengers,
  AVG(fare_per_mile) AS avg_fare_per_mile,

  -- Conditional aggregations: Rush hour breakdown
  SUM(CASE WHEN is_rush_hour = true THEN 1 ELSE 0 END) AS rush_hour_trip_count,
  SUM(CASE WHEN is_weekend = true THEN 1 ELSE 0 END) AS weekend_trip_count,

  -- Fare category breakdown
  SUM(CASE WHEN fare_category = 'economy' THEN 1 ELSE 0 END) AS economy_fare_count,
  SUM(CASE WHEN fare_category = 'standard' THEN 1 ELSE 0 END) AS standard_fare_count,
  SUM(CASE WHEN fare_category = 'premium' THEN 1 ELSE 0 END) AS premium_fare_count,
  SUM(CASE WHEN fare_category = 'luxury' THEN 1 ELSE 0 END) AS luxury_fare_count,

  -- Distance category breakdown
  SUM(CASE WHEN distance_category = 'short' THEN 1 ELSE 0 END) AS short_trip_count,
  SUM(CASE WHEN distance_category = 'medium' THEN 1 ELSE 0 END) AS medium_trip_count,
  SUM(CASE WHEN distance_category = 'long' THEN 1 ELSE 0 END) AS long_trip_count,
  SUM(CASE WHEN distance_category = 'very_long' THEN 1 ELSE 0 END) AS very_long_trip_count,

  -- Metadata: when was this aggregate last updated
  CURRENT_TIMESTAMP() AS last_updated

FROM lakehouse_silver_trips_cleaned

-- Group by 5-minute tumbling windows and location pairs
GROUP BY
  window(timestamp, '5 minutes'),
  pickup_location_id,
  dropoff_location_id
