-- ============================================================================
-- Silver Layer: weather_cleaned Table
-- ============================================================================
-- Description: Cleaned and enriched weather data with derived features for travel impact analysis
-- Source: lakehouse.bronze.weather
-- Transformation: lakehouse/silver/src/main/resources/sql/weather_cleaned.sql
--
-- TEACHING NOTE - LOAN ORIGINATION PARALLEL:
-- This is analogous to a "credit_bureau_data" table that would contain:
-- - Core credit data (credit_score, total_debt, payment_history)
-- - Derived risk indicators (high_credit_risk_flag, credit_risk_tier)
-- - Categorical features (income_bracket, debt_level_category)
-- - Impact flags (requires_manual_review, affects_eligibility)
-- ============================================================================

CREATE TABLE IF NOT EXISTS lakehouse.silver.weather_cleaned (
  -- ==========================================================================
  -- Core Fields (pass-through from Bronze)
  -- ==========================================================================
  timestamp TIMESTAMP NOT NULL COMMENT 'Weather observation timestamp',
  location_id BIGINT NOT NULL COMMENT 'Location identifier (TLC Taxi Zone ID)',
  temperature DOUBLE NOT NULL COMMENT 'Temperature in Fahrenheit',
  precipitation DOUBLE NOT NULL COMMENT 'Precipitation amount in inches',
  wind_speed DOUBLE NOT NULL COMMENT 'Wind speed in miles per hour',
  condition STRING NOT NULL COMMENT 'Weather condition: CLEAR, RAIN, SNOW, FOG, STORM',
  bronze_ingestion_time TIMESTAMP NOT NULL COMMENT 'Timestamp when record was ingested into Bronze layer',
  bronze_offset BIGINT NOT NULL COMMENT 'Kafka offset for record traceability',
  bronze_partition INT NOT NULL COMMENT 'Kafka partition for record traceability',

  -- ==========================================================================
  -- Derived Business Features (Feature Engineering)
  -- ==========================================================================
  is_severe_weather BOOLEAN NOT NULL COMMENT 'Whether weather is severe (SNOW/STORM, precip >1in, or wind >30mph)',
  weather_impact_level INT NOT NULL COMMENT 'Weather impact on travel: 0=none, 1=low, 2=medium, 3=high',
  temperature_category STRING NOT NULL COMMENT 'Temperature category: freezing (<32F), cold (32-50F), mild (50-70F), warm (70-85F), hot (>85F)',
  precipitation_category STRING NOT NULL COMMENT 'Precipitation category: none (0), light (<0.1in), moderate (0.1-0.5in), heavy (>0.5in)',
  wind_category STRING NOT NULL COMMENT 'Wind category: calm (<10mph), breezy (10-20mph), windy (20-30mph), dangerous (>30mph)',
  impacts_travel BOOLEAN NOT NULL COMMENT 'Whether weather conditions are likely to impact travel patterns',

  -- ==========================================================================
  -- Quality Tracking (Data Quality Audit)
  -- ==========================================================================
  was_duplicate BOOLEAN NOT NULL COMMENT 'Whether this record was identified as a duplicate during deduplication',

  -- ==========================================================================
  -- Metadata (Audit Trail)
  -- ==========================================================================
  silver_processed_at TIMESTAMP NOT NULL COMMENT 'Timestamp when record was processed into Silver layer',
  silver_transformation_version STRING NOT NULL COMMENT 'Version of transformation logic applied (e.g., weather_cleaned_v1_deequ)'
)
USING iceberg
COMMENT 'Silver layer: Cleaned and enriched weather data with impact categorization for travel demand analysis';
