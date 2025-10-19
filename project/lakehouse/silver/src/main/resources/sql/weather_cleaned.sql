-- ============================================================================
-- Silver Layer: Weather Cleaning
-- ============================================================================
-- Purpose: Data Quality and Validation for Weather Data
-- Pattern: SQL-First Transformation (90% SQL, 10% Java wrapper)
--
-- TEACHING NOTE - LOAN ORIGINATION PARALLEL:
-- This pattern validates weather data from external sources.
-- For loan originations, this is like validating:
--   - Credit bureau data (Experian, Equifax, TransUnion)
--   - Employment verification from third parties
--   - Bank statement data from aggregators
--
-- Weather is ENRICHMENT data (adds context to trips).
-- Similarly, credit bureau data enriches loan applications.
--
-- BEST PRACTICE: External data needs validation too!
-- ============================================================================

SELECT
  -- ========================================================================
  -- Original Fields (pass-through from Bronze)
  -- ========================================================================
  timestamp,
  locationId,
  temperature,
  precipitation,
  windSpeed,
  condition,
  bronze_ingestion_time,
  bronze_offset,
  bronze_partition,

  -- ========================================================================
  -- Data Quality Flag (TEACHABLE PATTERN)
  -- Validates weather data ranges and required fields
  -- For loans: Validate credit bureau data completeness and ranges
  -- ========================================================================
  CASE
    -- Rule 1: Temperature range validation (NYC: -20°F to 120°F)
    WHEN temperature < -20.0 THEN 'invalid_temperature_too_cold'
    WHEN temperature > 120.0 THEN 'invalid_temperature_too_hot'

    -- Rule 2: Precipitation validation (0 to 10 inches/hour max)
    WHEN precipitation < 0 THEN 'invalid_precipitation_negative'
    WHEN precipitation > 10.0 THEN 'invalid_precipitation_excessive'

    -- Rule 3: Wind speed validation (0 to 100 mph, hurricane threshold)
    WHEN windSpeed < 0 THEN 'invalid_windspeed_negative'
    WHEN windSpeed > 100.0 THEN 'invalid_windspeed_hurricane_level'

    -- Rule 4: Required field - location
    WHEN locationId IS NULL THEN 'missing_required_location'

    -- Rule 5: Weather condition must be valid
    WHEN condition IS NULL THEN 'missing_weather_condition'

    -- Rule 6: All validations passed
    ELSE 'valid'
  END AS data_quality_flag,

  -- ========================================================================
  -- Quality Score (0.0 to 1.0)
  -- TEACHABLE: Numeric score for analytics
  -- For loans: Credit bureau data completeness score
  -- ========================================================================
  CASE
    -- Perfect record: All validations pass
    WHEN temperature BETWEEN -20.0 AND 120.0
      AND precipitation BETWEEN 0.0 AND 10.0
      AND windSpeed BETWEEN 0.0 AND 100.0
      AND locationId IS NOT NULL
      AND condition IS NOT NULL
    THEN 1.0

    -- Minor issues: Extreme but plausible values
    WHEN (temperature < 0 OR temperature > 100)
      AND precipitation BETWEEN 0.0 AND 10.0
      AND windSpeed BETWEEN 0.0 AND 100.0
      AND locationId IS NOT NULL
      AND condition IS NOT NULL
    THEN 0.75

    -- Major issues: Invalid data
    ELSE 0.0
  END AS quality_score,

  -- ========================================================================
  -- Derived Business Fields (FEATURE ENGINEERING)
  -- TEACHABLE: Business logic for analytics
  -- For loans: Derived credit risk indicators
  -- ========================================================================

  -- Is this severe weather? (affects trip demand)
  CASE
    WHEN condition IN ('SNOW', 'STORM') THEN true
    WHEN precipitation > 1.0 THEN true
    WHEN windSpeed > 30.0 THEN true
    ELSE false
  END AS is_severe_weather,

  -- Weather impact level (1-3 scale)
  CASE
    WHEN condition IN ('STORM') OR windSpeed > 50.0 THEN 3  -- High impact
    WHEN condition IN ('SNOW', 'RAIN') OR precipitation > 0.5 THEN 2  -- Medium impact
    WHEN condition IN ('FOG') THEN 1  -- Low impact
    ELSE 0  -- No impact
  END AS weather_impact_level,

  -- Temperature category (for analysis)
  CASE
    WHEN temperature < 32.0 THEN 'freezing'
    WHEN temperature < 50.0 THEN 'cold'
    WHEN temperature < 70.0 THEN 'mild'
    WHEN temperature < 85.0 THEN 'warm'
    ELSE 'hot'
  END AS temperature_category,

  -- Precipitation category
  CASE
    WHEN precipitation = 0 THEN 'none'
    WHEN precipitation < 0.1 THEN 'light'
    WHEN precipitation < 0.5 THEN 'moderate'
    ELSE 'heavy'
  END AS precipitation_category,

  -- ========================================================================
  -- Metadata (AUDIT TRAIL)
  -- ========================================================================
  current_timestamp() AS silver_processed_at,
  'weather_cleaned_v1' AS silver_transformation_version

-- ========================================================================
-- Source: Bronze Layer (Raw Weather Data)
-- ========================================================================
FROM lakehouse.bronze.weather

-- ========================================================================
-- INCREMENTAL PROCESSING (CRITICAL BEST PRACTICE)
-- Only process NEW records from Bronze
--
-- TEACHABLE: Prevents reprocessing credit bureau pulls every time
-- For loans: Only process new credit report data since last run
-- ========================================================================
WHERE bronze_ingestion_time > (
  SELECT COALESCE(MAX(silver_processed_at), TIMESTAMP('1970-01-01 00:00:00'))
  FROM lakehouse.silver.weather_cleaned
)

-- ========================================================================
-- TEACHING NOTES:
--
-- 1. EXTERNAL DATA VALIDATION:
--    - Weather comes from external API (like credit bureau)
--    - Must validate: ranges, completeness, consistency
--    - Can't assume external data is always correct!
--
-- 2. LOAN ORIGINATION MAPPING:
--    Weather Data              → Credit Bureau Data
--    ─────────────────────────────────────────────────
--    temperature validation    → credit score range (300-850)
--    precipitation validation  → debt-to-income ratio validation
--    windSpeed validation      → monthly income validation
--    condition enum            → employment status enum
--    is_severe_weather         → high_credit_risk_flag
--    weather_impact_level      → credit_risk_tier (1-3)
--
-- 3. ENRICHMENT PATTERN:
--    - Weather enriches trips (adds context)
--    - Credit bureau enriches loan applications
--    - Both are external, both need validation
--    - Both affect downstream decisions
-- ============================================================================
