-- ============================================================================
-- Silver Layer: trips_cleaned Table
-- ============================================================================
-- Description: Cleaned and enriched NYC taxi trip data with derived business features
-- Source: lakehouse.bronze.trips
-- Transformation: lakehouse/silver/src/main/resources/sql/trips_cleaned.sql
--
-- TEACHING NOTE - LOAN ORIGINATION PARALLEL:
-- This is analogous to an "applications_validated" table that would contain:
-- - Core application fields (applicant_id, loan_amount, property_value)
-- - Derived metrics (DTI ratio, LTV ratio, credit_utilization)
-- - Categorical features (loan_size_category, risk_tier, product_type)
-- - Quality flags (was_duplicate, requires_manual_review)
-- ============================================================================

CREATE TABLE IF NOT EXISTS lakehouse.silver.trips_cleaned (
  -- ==========================================================================
  -- Core Fields (pass-through from Bronze)
  -- ==========================================================================
  timestamp TIMESTAMP NOT NULL COMMENT 'Trip timestamp',
  pickup_location_id BIGINT NOT NULL COMMENT 'TLC Taxi Zone where the trip started',
  dropoff_location_id BIGINT NOT NULL COMMENT 'TLC Taxi Zone where the trip ended',
  trip_distance DOUBLE NOT NULL COMMENT 'Trip distance in miles',
  fare_amount DOUBLE NOT NULL COMMENT 'Fare amount in USD',
  passenger_count INT NOT NULL COMMENT 'Number of passengers in the vehicle',
  bronze_ingestion_time TIMESTAMP NOT NULL COMMENT 'Timestamp when record was ingested into Bronze layer',
  bronze_offset BIGINT NOT NULL COMMENT 'Kafka offset for record traceability',
  bronze_partition INT NOT NULL COMMENT 'Kafka partition for record traceability',

  -- ==========================================================================
  -- Derived Business Features (Feature Engineering)
  -- ==========================================================================
  is_rush_hour BOOLEAN NOT NULL COMMENT 'Whether trip occurred during rush hour (7-9am, 4-7pm)',
  is_weekend BOOLEAN NOT NULL COMMENT 'Whether trip occurred on Saturday or Sunday',
  hour_of_day INT NOT NULL COMMENT 'Hour of day (0-23) for time-series analysis',
  day_of_week STRING NOT NULL COMMENT 'Day name (Monday, Tuesday, etc.) for reporting',
  fare_per_mile DOUBLE COMMENT 'Fare amount divided by trip distance (nullable for zero-distance trips)',
  distance_category STRING NOT NULL COMMENT 'Distance category: short (<1mi), medium (1-5mi), long (5-20mi), very_long (>20mi)',
  fare_category STRING NOT NULL COMMENT 'Fare category: economy (<$10), standard ($10-30), premium ($30-100), luxury (>$100)',

  -- ==========================================================================
  -- Quality Tracking (Data Quality Audit)
  -- ==========================================================================
  was_duplicate BOOLEAN NOT NULL COMMENT 'Whether this record was identified as a duplicate during deduplication',

  -- ==========================================================================
  -- Metadata (Audit Trail)
  -- ==========================================================================
  silver_processed_at TIMESTAMP NOT NULL COMMENT 'Timestamp when record was processed into Silver layer',
  silver_transformation_version STRING NOT NULL COMMENT 'Version of transformation logic applied (e.g., trips_cleaned_v1_deequ)'
)
USING iceberg
COMMENT 'Silver layer: Cleaned and enriched NYC taxi trips with derived features for analytics and demand modeling';
