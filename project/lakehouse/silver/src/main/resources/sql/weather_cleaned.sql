SELECT
  TIMESTAMP_MILLIS(timestamp) AS timestamp,
  locationId AS location_id,
  temperature,
  precipitation,
  windSpeed AS wind_speed,
  condition,
  ingestion_timestamp,
  offset AS bronze_offset,
  partition AS bronze_partition,

  CASE
    WHEN condition IN ('SNOW', 'STORM') THEN true
    WHEN precipitation > ${SEVERE_PRECIPITATION_THRESHOLD} THEN true
    WHEN windSpeed > ${SEVERE_WIND_SPEED_THRESHOLD} THEN true
    ELSE false
  END AS is_severe_weather,

  CASE
    WHEN condition IN ('STORM') OR windSpeed > ${HIGH_IMPACT_WIND_THRESHOLD} THEN 3
    WHEN condition IN ('SNOW', 'RAIN') OR precipitation > ${MEDIUM_IMPACT_PRECIP_THRESHOLD} THEN 2
    WHEN condition IN ('FOG') THEN 1
    ELSE 0
  END AS weather_impact_level,

  CASE
    WHEN temperature < ${TEMP_FREEZING_MAX} THEN 'freezing'
    WHEN temperature < ${TEMP_COLD_MAX} THEN 'cold'
    WHEN temperature < ${TEMP_MILD_MAX} THEN 'mild'
    WHEN temperature < ${TEMP_WARM_MAX} THEN 'warm'
    ELSE 'hot'
  END AS temperature_category,

  CASE
    WHEN precipitation = 0 THEN 'none'
    WHEN precipitation < ${PRECIP_LIGHT_MAX} THEN 'light'
    WHEN precipitation < ${PRECIP_MODERATE_MAX} THEN 'moderate'
    ELSE 'heavy'
  END AS precipitation_category,

  CASE
    WHEN windSpeed < ${WIND_CALM_MAX} THEN 'calm'
    WHEN windSpeed < ${WIND_BREEZY_MAX} THEN 'breezy'
    WHEN windSpeed < ${WIND_WINDY_MAX} THEN 'windy'
    ELSE 'dangerous'
  END AS wind_category,

  CASE
    WHEN condition IN ('STORM', 'SNOW', 'FOG') THEN true
    WHEN precipitation > ${MEDIUM_IMPACT_PRECIP_THRESHOLD} THEN true
    WHEN temperature < ${EXTREME_COLD_THRESHOLD} OR temperature > ${EXTREME_HOT_THRESHOLD} THEN true
    ELSE false
  END AS impacts_travel

FROM lakehouse_bronze_weather
