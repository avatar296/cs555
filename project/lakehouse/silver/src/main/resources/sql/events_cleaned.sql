-- Silver Layer: Events Transformation
-- Note: Deduplication is handled by Spark streaming dropDuplicates, not in SQL

SELECT
  -- Core fields
  TIMESTAMP_MILLIS(timestamp) AS timestamp,
  locationId AS location_id,
  eventType AS event_type,
  attendanceEstimate AS attendance_estimate,
  startTime AS start_time,
  endTime AS end_time,
  venueName AS venue_name,
  ingestion_timestamp,
  offset AS bronze_offset,
  partition AS bronze_partition,

  -- Derived features
  CASE
    WHEN attendanceEstimate > ${MAJOR_EVENT_ATTENDANCE_THRESHOLD} THEN true
    WHEN eventType IN ('SPORTS', 'CONCERT') AND attendanceEstimate > ${MAJOR_SPORTS_CONCERT_THRESHOLD} THEN true
    ELSE false
  END AS is_major_event,

  CASE
    WHEN attendanceEstimate < ${EVENT_SMALL_MAX} THEN 'small'
    WHEN attendanceEstimate < ${EVENT_MEDIUM_MAX} THEN 'medium'
    WHEN attendanceEstimate < ${EVENT_LARGE_MAX} THEN 'large'
    ELSE 'mega'
  END AS event_size_category,

  CAST((endTime - startTime) / 3600000.0 AS DOUBLE) AS event_duration_hours,

  CASE
    WHEN HOUR(FROM_UNIXTIME(startTime / 1000)) BETWEEN ${MORNING_RUSH_START} AND ${MORNING_RUSH_END} THEN true
    WHEN HOUR(FROM_UNIXTIME(startTime / 1000)) BETWEEN ${EVENING_RUSH_START} AND ${EVENING_RUSH_END} THEN true
    WHEN HOUR(FROM_UNIXTIME(endTime / 1000)) BETWEEN ${MORNING_RUSH_START} AND ${MORNING_RUSH_END} THEN true
    WHEN HOUR(FROM_UNIXTIME(endTime / 1000)) BETWEEN ${EVENING_RUSH_START} AND ${EVENING_RUSH_END} THEN true
    ELSE false
  END AS overlaps_rush_hour,

  CASE
    WHEN DAYOFWEEK(FROM_UNIXTIME(startTime / 1000)) IN (1, 7) THEN true
    ELSE false
  END AS is_weekend_event,

  CASE
    WHEN eventType IN ('SPORTS', 'CONCERT') THEN 'high_demand'
    WHEN eventType IN ('CONFERENCE', 'FESTIVAL') THEN 'medium_demand'
    ELSE 'low_demand'
  END AS demand_category,

  HOUR(FROM_UNIXTIME(startTime / 1000)) AS event_start_hour,
  HOUR(FROM_UNIXTIME(endTime / 1000)) AS event_end_hour,

  CASE
    WHEN venueName IS NOT NULL AND TRIM(venueName) != '' THEN true
    ELSE false
  END AS has_venue_details,

  CASE
    WHEN (endTime - startTime) / 3600000.0 < ${DURATION_BRIEF_MAX} THEN 'brief'
    WHEN (endTime - startTime) / 3600000.0 < ${DURATION_MODERATE_MAX} THEN 'moderate'
    WHEN (endTime - startTime) / 3600000.0 < ${DURATION_EXTENDED_MAX} THEN 'extended'
    ELSE 'multi_day'
  END AS duration_category

FROM lakehouse_bronze_events
WHERE startTime < endTime
