-- ============================================================================
-- Silver Layer: Trips Cleaning
-- ============================================================================
-- Purpose: Data Quality and Validation
-- Pattern: SQL-First Transformation (90% SQL, 10% Java wrapper)
--
-- TEACHING NOTE - LOAN ORIGINATION PARALLEL:
-- This pattern validates taxi trips, but the same logic applies to loans:
--   - trip_distance validation    → loan_amount range validation
--   - fare_amount validation       → income verification
--   - location validation          → address validation
--   - passenger_count validation   → number of co-borrowers
--   - data_quality_flag            → application completeness
--   - quality_score                → underwriting score
--
-- BEST PRACTICE: Business rules in SQL = visible, auditable, testable
-- ============================================================================

SELECT
  -- ========================================================================
  -- Original Fields (pass-through from Bronze)
  -- ========================================================================
  timestamp,
  pickupLocationId,
  dropoffLocationId,
  tripDistance,
  fareAmount,
  passengerCount,
  bronze_ingestion_time,
  bronze_offset,
  bronze_partition,

  -- ========================================================================
  -- Data Quality Flag (TEACHABLE PATTERN)
  -- Each CASE branch represents a business rule
  -- For loans: Replace with credit rules (credit_score < 600, debt_to_income > 43%, etc.)
  -- ========================================================================
  CASE
    -- Rule 1: Minimum distance threshold
    WHEN tripDistance < 0.1 THEN 'invalid_distance_too_short'

    -- Rule 2: Maximum reasonable distance (NYC to Boston = ~200mi)
    WHEN tripDistance > 200.0 THEN 'invalid_distance_too_long'

    -- Rule 3: NYC yellow cab minimum fare is $2.50
    WHEN fareAmount < 2.50 THEN 'invalid_fare_below_minimum'

    -- Rule 4: Suspicious high fare (fraud detection)
    WHEN fareAmount > 1000.0 THEN 'suspicious_fare_too_high'

    -- Rule 5: Required field - pickup location
    WHEN pickupLocationId IS NULL THEN 'missing_required_pickup_location'

    -- Rule 6: Required field - dropoff location
    WHEN dropoffLocationId IS NULL THEN 'missing_required_dropoff_location'

    -- Rule 7: Passenger count validation (NYC taxis fit 1-6 passengers)
    WHEN passengerCount IS NULL THEN 'missing_passenger_count'
    WHEN passengerCount < 1 THEN 'invalid_passenger_count_zero'
    WHEN passengerCount > 6 THEN 'invalid_passenger_count_exceeds_capacity'

    -- Rule 8: All validations passed
    ELSE 'valid'
  END AS data_quality_flag,

  -- ========================================================================
  -- Quality Score (0.0 to 1.0)
  -- TEACHABLE: Numeric score for analytics/ML
  -- For loans: Becomes application completeness score or pre-screening score
  -- ========================================================================
  CASE
    -- Perfect record: All validations pass
    WHEN tripDistance BETWEEN 0.1 AND 200.0
      AND fareAmount BETWEEN 2.50 AND 1000.0
      AND pickupLocationId IS NOT NULL
      AND dropoffLocationId IS NOT NULL
      AND passengerCount BETWEEN 1 AND 6
    THEN 1.0

    -- Minor issues: Missing optional field (e.g., passengerCount could be inferred)
    WHEN passengerCount IS NULL
      AND tripDistance BETWEEN 0.1 AND 200.0
      AND fareAmount BETWEEN 2.50 AND 1000.0
      AND pickupLocationId IS NOT NULL
      AND dropoffLocationId IS NOT NULL
    THEN 0.75

    -- Major issues: Invalid required fields
    ELSE 0.0
  END AS quality_score,

  -- ========================================================================
  -- Derived Business Fields (FEATURE ENGINEERING)
  -- TEACHABLE: Business logic for analytics
  -- For loans: Time-based features (application_hour, is_business_day)
  -- ========================================================================

  -- Is this a rush hour trip? (affects demand patterns)
  CASE
    WHEN HOUR(timestamp) BETWEEN 7 AND 9 THEN true   -- Morning rush
    WHEN HOUR(timestamp) BETWEEN 16 AND 19 THEN true -- Evening rush
    ELSE false
  END AS is_rush_hour,

  -- Is this a weekend trip? (affects pricing patterns)
  CASE
    WHEN DAYOFWEEK(timestamp) IN (1, 7) THEN true -- Sunday=1, Saturday=7
    ELSE false
  END AS is_weekend,

  -- Hour of day (0-23) for time-series analysis
  HOUR(timestamp) AS hour_of_day,

  -- Day of week name for reporting
  DAYNAME(timestamp) AS day_of_week,

  -- Trip efficiency: fare per mile (outlier detection)
  CASE
    WHEN tripDistance > 0 THEN fareAmount / tripDistance
    ELSE NULL
  END AS fare_per_mile,

  -- ========================================================================
  -- Metadata (AUDIT TRAIL)
  -- TEACHABLE: Track data lineage
  -- For loans: Track application_received_at, processed_at, approved_at
  -- ========================================================================
  current_timestamp() AS silver_processed_at,

  'trips_cleaned_v1' AS silver_transformation_version

-- ========================================================================
-- Source: Bronze Layer (Raw Data)
-- ========================================================================
FROM lakehouse.bronze.trips

-- ========================================================================
-- INCREMENTAL PROCESSING (CRITICAL BEST PRACTICE)
-- Only process NEW records from Bronze that haven't been processed yet
--
-- TEACHABLE: This prevents reprocessing all historical data every 5 minutes
-- For loans: Process only new applications since last run
--
-- How it works:
--   1. Find the latest silver_processed_at timestamp in Silver
--   2. Only read Bronze records newer than that timestamp
--   3. If Silver is empty (first run), read everything (1970-01-01)
-- ========================================================================
WHERE bronze_ingestion_time > (
  SELECT COALESCE(MAX(silver_processed_at), TIMESTAMP('1970-01-01 00:00:00'))
  FROM lakehouse.silver.trips_cleaned
)

-- ========================================================================
-- TEACHING NOTES:
--
-- 1. IDEMPOTENCY: This query can be run multiple times safely
--    - Same input always produces same output
--    - Rerunning doesn't create duplicates (incremental WHERE clause)
--
-- 2. DATA QUALITY: Rules are explicit and visible
--    - Easy to audit: "Why was this trip rejected?"
--    - Easy to modify: Change threshold in SQL, not buried in code
--
-- 3. PERFORMANCE: Incremental processing
--    - Only reads new data (partition pruning on bronze_ingestion_time)
--    - Spark can parallelize across partitions
--
-- 4. LOAN ORIGINATION MAPPING:
--    - Replace trip validation rules with credit rules
--    - Add loan-specific features (DTI ratio, credit utilization)
--    - Same pattern: SQL for business logic, Java wrapper for execution
-- ============================================================================
