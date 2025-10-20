-- Silver Layer: Trips Transformation

WITH
  deduplicated_trips
  AS
  (
    SELECT
      *,
      ROW_NUMBER() OVER (
      PARTITION BY timestamp, pickupLocationId, dropoffLocationId
      ORDER BY bronze_ingestion_time DESC
    ) AS row_num
    FROM lakehouse_bronze_trips
  )
SELECT
  -- Core fields
  timestamp,
  pickupLocationId AS pickup_location_id,
  dropoffLocationId AS dropoff_location_id,
  tripDistance AS trip_distance,
  fareAmount AS fare_amount,
  passengerCount AS passenger_count,
  bronze_ingestion_time,
  bronze_offset,
  bronze_partition,

  -- Derived features
  CASE
    WHEN HOUR(timestamp) BETWEEN ${MORNING_RUSH_START} AND ${MORNING_RUSH_END} THEN true
    WHEN HOUR(timestamp) BETWEEN ${EVENING_RUSH_START} AND ${EVENING_RUSH_END} THEN true
    ELSE false
  END AS is_rush_hour,

  CASE
    WHEN DAYOFWEEK(timestamp) IN (1, 7) THEN true
    ELSE false
  END AS is_weekend,

  HOUR(timestamp) AS hour_of_day,
  DAYNAME(timestamp) AS day_of_week,

  CASE
    WHEN tripDistance > 0 THEN fareAmount / tripDistance
    ELSE NULL
  END AS fare_per_mile,

  CASE
    WHEN tripDistance < ${DISTANCE_SHORT_MAX} THEN 'short'
    WHEN tripDistance < ${DISTANCE_MEDIUM_MAX} THEN 'medium'
    WHEN tripDistance < ${DISTANCE_LONG_MAX} THEN 'long'
    ELSE 'very_long'
  END AS distance_category,

  CASE
    WHEN fareAmount < ${FARE_ECONOMY_MAX} THEN 'economy'
    WHEN fareAmount < ${FARE_STANDARD_MAX} THEN 'standard'
    WHEN fareAmount < ${FARE_PREMIUM_MAX} THEN 'premium'
    ELSE 'luxury'
  END AS fare_category,

  CASE WHEN row_num > 1 THEN true ELSE false END AS was_duplicate

FROM deduplicated_trips
WHERE row_num = 1
