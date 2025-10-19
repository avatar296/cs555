-- ============================================================================
-- Silver Layer: Weather Transformation
-- ============================================================================
-- Purpose: Transform and enrich weather data from external sources
-- Pattern: SQL for transformation, Deequ for validation
--
-- TEACHING NOTE - LOAN ORIGINATION PARALLEL:
-- This SQL handles transformation of external enrichment data.
-- For loan originations, this is like transforming:
--   - Credit bureau data (Experian, Equifax, TransUnion)
--   - Employment verification from third parties
--   - Bank statement data from aggregators
--
-- Weather is ENRICHMENT data (adds context to trips).
-- Similarly, credit bureau data enriches loan applications.
--
-- VALIDATION: Handled by Deequ (not SQL)
--   - Temperature range checks  → Deequ checks
--   - Precipitation validation  → Deequ checks
--   - Wind speed validation     → Deequ checks
--   - Required field checks     → Deequ checks
--
-- BEST PRACTICE: External data transformation separated from validation
-- ============================================================================

-- ============================================================================
-- DEDUPLICATION CTE
-- Handles duplicate records from external API calls
-- For loans: Prevents duplicate credit bureau pulls
--
-- NOTE: Incremental processing is handled by Spark Structured Streaming
-- checkpoint, not by SQL WHERE clause. Each micro-batch contains only
-- new data from Bronze layer.
-- ============================================================================
WITH deduplicated_weather AS (
  SELECT
    *,
    ROW_NUMBER() OVER (
      PARTITION BY timestamp, locationId
      ORDER BY bronze_ingestion_time DESC  -- Keep most recent
    ) AS row_num
  FROM lakehouse_bronze_weather  -- Temp view created from streaming source
)
SELECT
  -- ========================================================================
  -- Core Fields (pass-through from Bronze)
  -- NOTE: Standardized to snake_case for consistency
  -- ========================================================================
  timestamp,
  locationId AS location_id,
  temperature,
  precipitation,
  windSpeed AS wind_speed,
  condition,
  bronze_ingestion_time,
  bronze_offset,
  bronze_partition,

  -- ========================================================================
  -- Derived Business Features (FEATURE ENGINEERING)
  -- TEACHABLE: Transform raw weather data into business insights
  -- For loans: Transform credit bureau data into risk indicators
  -- ========================================================================

  -- Severe weather flag (affects trip demand and safety)
  -- Loan parallel: high_credit_risk_flag (DTI > 43%, credit < 620)
  CASE
    WHEN condition IN ('SNOW', 'STORM') THEN true
    WHEN precipitation > ${SEVERE_PRECIPITATION_THRESHOLD} THEN true
    WHEN windSpeed > ${SEVERE_WIND_SPEED_THRESHOLD} THEN true
    ELSE false
  END AS is_severe_weather,

  -- Weather impact level (1-3 scale for trip demand modeling)
  -- Loan parallel: credit_risk_tier (1=prime, 2=near-prime, 3=subprime)
  CASE
    WHEN condition IN ('STORM') OR windSpeed > ${HIGH_IMPACT_WIND_THRESHOLD} THEN 3  -- High impact
    WHEN condition IN ('SNOW', 'RAIN') OR precipitation > ${MEDIUM_IMPACT_PRECIP_THRESHOLD} THEN 2  -- Medium impact
    WHEN condition IN ('FOG') THEN 1  -- Low impact
    ELSE 0  -- No impact
  END AS weather_impact_level,

  -- Temperature category (for seasonal analysis)
  -- Loan parallel: income_bracket (for loan product targeting)
  CASE
    WHEN temperature < ${TEMP_FREEZING_MAX} THEN 'freezing'
    WHEN temperature < ${TEMP_COLD_MAX} THEN 'cold'
    WHEN temperature < ${TEMP_MILD_MAX} THEN 'mild'
    WHEN temperature < ${TEMP_WARM_MAX} THEN 'warm'
    ELSE 'hot'
  END AS temperature_category,

  -- Precipitation category (for demand forecasting)
  -- Loan parallel: debt_level (none, low, moderate, high)
  CASE
    WHEN precipitation = 0 THEN 'none'
    WHEN precipitation < ${PRECIP_LIGHT_MAX} THEN 'light'
    WHEN precipitation < ${PRECIP_MODERATE_MAX} THEN 'moderate'
    ELSE 'heavy'
  END AS precipitation_category,

  -- Wind category (safety considerations)
  -- Loan parallel: employment_stability (stable, moderate, unstable)
  CASE
    WHEN windSpeed < ${WIND_CALM_MAX} THEN 'calm'
    WHEN windSpeed < ${WIND_BREEZY_MAX} THEN 'breezy'
    WHEN windSpeed < ${WIND_WINDY_MAX} THEN 'windy'
    ELSE 'dangerous'
  END AS wind_category,

  -- Is weather condition likely to impact trip patterns?
  -- Loan parallel: requires_manual_review (complex credit scenarios)
  CASE
    WHEN condition IN ('STORM', 'SNOW', 'FOG') THEN true
    WHEN precipitation > ${MEDIUM_IMPACT_PRECIP_THRESHOLD} THEN true
    WHEN temperature < ${EXTREME_COLD_THRESHOLD} OR temperature > ${EXTREME_HOT_THRESHOLD} THEN true
    ELSE false
  END AS impacts_travel,

  -- ========================================================================
  -- QUALITY VALIDATION
  -- Validation now handled by Deequ in WeatherCleanedJob.scala
  -- Deequ enforces:
  --   - Completeness checks (timestamp, location_id, condition)
  --   - Range validation (temperature: -20 to 120, precipitation: 0-10, wind_speed: 0-100)
  -- Invalid batches are rejected before reaching Silver layer
  -- ========================================================================

  -- ========================================================================
  -- Deduplication Flag
  -- ========================================================================
  CASE WHEN row_num > 1 THEN true ELSE false END AS was_duplicate,

  -- ========================================================================
  -- Metadata (AUDIT TRAIL)
  -- TEACHABLE: Track when external data was processed
  -- For loans: Track when credit bureau data was pulled and validated
  -- ========================================================================
  current_timestamp() AS silver_processed_at,
  'weather_cleaned_v1_deequ' AS silver_transformation_version

-- ========================================================================
-- Source: Deduplicated Bronze Layer (from CTE above)
-- ========================================================================
FROM deduplicated_weather

-- ========================================================================
-- DEDUPLICATION FILTER
-- ========================================================================
WHERE row_num = 1

-- ========================================================================
-- TEACHING NOTES:
--
-- 1. EXTERNAL DATA TRANSFORMATION:
--    - Weather comes from external API (like credit bureau)
--    - SQL transforms raw values into business categories
--    - Deequ validates ranges and completeness (separate concern)
--
-- 2. ENRICHMENT PATTERN:
--    - Weather enriches trips (adds context for demand modeling)
--    - Credit bureau enriches loan applications (adds context for underwriting)
--    - Both are external, both need transformation AND validation
--
-- 3. LOAN ORIGINATION MAPPING:
--    Weather Feature              → Credit Bureau Feature
--    ─────────────────────────────────────────────────────────
--    is_severe_weather            → high_credit_risk_flag
--    weather_impact_level         → credit_risk_tier (1-3)
--    temperature_category         → income_bracket
--    precipitation_category       → debt_level_category
--    impacts_travel               → requires_manual_review
--
-- 4. VALIDATION (Moved to Deequ):
--    - Range checks: temperature (-20 to 120), precipitation (0-10), windSpeed (0-100)
--    - Completeness: locationId, condition must not be null
--    - Consistency: precipitation and windSpeed must be non-negative
-- ============================================================================
