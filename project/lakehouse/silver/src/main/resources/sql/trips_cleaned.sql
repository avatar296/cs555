-- ============================================================================
-- Silver Layer: Trips Transformation
-- ============================================================================
-- Purpose: Clean and enrich trip data
-- Pattern: SQL for transformation, Deequ for validation
--
-- TEACHING NOTE - LOAN ORIGINATION PARALLEL:
-- This SQL handles data transformation and feature engineering:
--   - Pass-through core fields      → loan_amount, applicant_id, etc.
--   - Derived features              → DTI ratio, credit utilization, etc.
--   - Time-based features           → application_hour, is_business_day
--
-- VALIDATION: Handled by Deequ (not SQL)
--   - Range checks (distance, fare) → Deequ checks
--   - Completeness checks           → Deequ checks
--   - Uniqueness checks             → Deequ checks
--
-- BEST PRACTICE: Separation of concerns
--   - SQL  = Data transformation (WHAT data looks like)
--   - Deequ = Data validation (WHETHER data is valid)
-- ============================================================================

-- ============================================================================
-- DEDUPLICATION CTE
-- Handles duplicate records from Kafka (retries, network issues, etc.)
-- For loans: Prevents duplicate application submissions
--
-- NOTE: Incremental processing is handled by Spark Structured Streaming
-- checkpoint, not by SQL WHERE clause. Each micro-batch contains only
-- new data from Bronze layer.
-- ============================================================================
WITH
  deduplicated_trips
  AS
  (
    SELECT
      *,
      ROW_NUMBER() OVER (
      PARTITION BY timestamp, pickupLocationId, dropoffLocationId
      ORDER BY bronze_ingestion_time DESC  -- Keep most recent
    ) AS row_num
    FROM lakehouse_bronze_trips  -- Temp view created from streaming source
  )
SELECT
  -- ========================================================================
  -- Core Fields (pass-through from Bronze)
  -- NOTE: Standardized to snake_case for consistency
  -- ========================================================================
  timestamp,
  pickupLocationId AS pickup_location_id,
  dropoffLocationId AS dropoff_location_id,
  tripDistance AS trip_distance,
  fareAmount AS fare_amount,
  passengerCount AS passenger_count,
  bronze_ingestion_time,
  bronze_offset,
  bronze_partition,

  -- ========================================================================
  -- Derived Business Features (FEATURE ENGINEERING)
  -- TEACHABLE: Business logic for analytics
  -- For loans: application_hour, is_business_day, tenure_months, etc.
  -- ========================================================================

  -- Is this a rush hour trip? (affects demand patterns)
  -- Loan parallel: is_business_hours (application timing affects conversion)
  CASE
    WHEN HOUR(timestamp) BETWEEN ${MORNING_RUSH_START} AND ${MORNING_RUSH_END} THEN true   -- Morning rush
    WHEN HOUR(timestamp) BETWEEN ${EVENING_RUSH_START} AND ${EVENING_RUSH_END} THEN true -- Evening rush
    ELSE false
  END AS is_rush_hour,

  -- Is this a weekend trip? (affects pricing patterns)
  -- Loan parallel: is_weekend (weekend applications less likely to convert)
  CASE
    WHEN DAYOFWEEK(timestamp) IN (1, 7) THEN true -- Sunday=1, Saturday=7
    ELSE false
  END AS is_weekend,

  -- Hour of day (0-23) for time-series analysis
  -- Loan parallel: application_hour (behavioral patterns)
  HOUR(timestamp) AS hour_of_day,

  -- Day of week name for reporting
  -- Loan parallel: application_day_name (reporting grouping)
  DAYNAME(timestamp) AS day_of_week,

  -- Trip efficiency: fare per mile (derived metric)
  -- Loan parallel: debt_to_income_ratio (key underwriting metric)
  CASE
    WHEN tripDistance > 0 THEN fareAmount / tripDistance
    ELSE NULL
  END AS fare_per_mile,

  -- Distance category for segmentation
  -- Loan parallel: loan_size_category (small, medium, large, jumbo)
  CASE
    WHEN tripDistance < ${DISTANCE_SHORT_MAX} THEN 'short'
    WHEN tripDistance < ${DISTANCE_MEDIUM_MAX} THEN 'medium'
    WHEN tripDistance < ${DISTANCE_LONG_MAX} THEN 'long'
    ELSE 'very_long'
  END AS distance_category,

  -- Fare category for segmentation
  -- Loan parallel: risk_category (prime, near-prime, subprime)
  CASE
    WHEN fareAmount < ${FARE_ECONOMY_MAX} THEN 'economy'
    WHEN fareAmount < ${FARE_STANDARD_MAX} THEN 'standard'
    WHEN fareAmount < ${FARE_PREMIUM_MAX} THEN 'premium'
    ELSE 'luxury'
  END AS fare_category,

  -- ========================================================================
  -- QUALITY VALIDATION
  -- Validation now handled by Deequ in TripsCleanedJob.scala
  -- Deequ enforces:
  --   - Completeness checks (timestamp, pickup_location_id, dropoff_location_id)
  --   - Range validation (trip_distance: 0.1-200, fare_amount: 2.50-1000, passenger_count: 1-6)
  -- Invalid batches are rejected before reaching Silver layer
  -- ========================================================================

  -- ========================================================================
  -- Deduplication Flag (AUDIT TRAIL)
  -- Tracks if record was a duplicate (for monitoring)
  -- For loans: Track duplicate application submissions
  -- ========================================================================
  CASE WHEN row_num > 1 THEN true ELSE false END AS was_duplicate,

  -- ========================================================================
  -- Metadata (AUDIT TRAIL)
  -- TEACHABLE: Track data lineage and processing timestamps
  -- For loans: application_received_at, validated_at, decision_at
  -- ========================================================================
  current_timestamp
() AS silver_processed_at,

  'trips_cleaned_v1_deequ' AS silver_transformation_version

-- ========================================================================
-- Source: Deduplicated Bronze Layer (from CTE above)
-- ========================================================================
FROM deduplicated_trips

-- ========================================================================
-- DEDUPLICATION FILTER
-- Only keep the first occurrence of each unique trip
-- For loans: Only keep first submission of each application
-- ========================================================================
WHERE row_num = 1