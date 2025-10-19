-- ============================================================================
-- Silver Layer: Events Cleaning
-- ============================================================================
-- Purpose: Data Quality and Validation for Special Events Data
-- Pattern: SQL-First Transformation (90% SQL, 10% Java wrapper)
--
-- TEACHING NOTE - LOAN ORIGINATION PARALLEL:
-- This pattern validates special events that affect taxi demand.
-- For loan originations, this is like validating:
--   - Co-borrower information
--   - Special loan programs (FHA, VA, first-time buyer)
--   - Guarantor information
--   - Collateral details
--
-- Events are CONTEXTUAL data (special circumstances).
-- Similarly, loan programs/co-borrowers are special circumstances in lending.
--
-- BEST PRACTICE: Special cases need their own validation rules!
-- ============================================================================

SELECT
  -- ========================================================================
  -- Original Fields (pass-through from Bronze)
  -- ========================================================================
  timestamp,
  locationId,
  eventType,
  attendanceEstimate,
  startTime,
  endTime,
  venueName,
  bronze_ingestion_time,
  bronze_offset,
  bronze_partition,

  -- ========================================================================
  -- Data Quality Flag (TEACHABLE PATTERN)
  -- Validates event data ranges and logical consistency
  -- For loans: Validate co-borrower data, special program eligibility
  -- ========================================================================
  CASE
    -- Rule 1: Attendance validation (1 to 100,000 - Madison Square Garden capacity)
    WHEN attendanceEstimate < 1 THEN 'invalid_attendance_too_low'
    WHEN attendanceEstimate > 100000 THEN 'invalid_attendance_exceeds_capacity'

    -- Rule 2: Time validation - event must have valid start/end times
    WHEN startTime IS NULL THEN 'missing_start_time'
    WHEN endTime IS NULL THEN 'missing_end_time'

    -- Rule 3: Logical validation - start time must be before end time
    WHEN startTime >= endTime THEN 'invalid_time_range_start_after_end'

    -- Rule 4: Event duration validation (max 24 hours is reasonable)
    WHEN (endTime - startTime) > 86400000 THEN 'invalid_duration_exceeds_24_hours'  -- 24h in millis

    -- Rule 5: Required field - location
    WHEN locationId IS NULL THEN 'missing_required_location'

    -- Rule 6: Event type must be valid
    WHEN eventType IS NULL THEN 'missing_event_type'

    -- Rule 7: All validations passed
    ELSE 'valid'
  END AS data_quality_flag,

  -- ========================================================================
  -- Quality Score (0.0 to 1.0)
  -- TEACHABLE: Numeric score for analytics
  -- For loans: Co-borrower data completeness score
  -- ========================================================================
  CASE
    -- Perfect record: All validations pass
    WHEN attendanceEstimate BETWEEN 1 AND 100000
      AND startTime IS NOT NULL
      AND endTime IS NOT NULL
      AND startTime < endTime
      AND (endTime - startTime) <= 86400000
      AND locationId IS NOT NULL
      AND eventType IS NOT NULL
    THEN 1.0

    -- Acceptable: Missing optional venue name
    WHEN attendanceEstimate BETWEEN 1 AND 100000
      AND startTime IS NOT NULL
      AND endTime IS NOT NULL
      AND startTime < endTime
      AND (endTime - startTime) <= 86400000
      AND locationId IS NOT NULL
      AND eventType IS NOT NULL
      AND venueName IS NULL
    THEN 0.85

    -- Major issues: Invalid required fields
    ELSE 0.0
  END AS quality_score,

  -- ========================================================================
  -- Derived Business Fields (FEATURE ENGINEERING)
  -- TEACHABLE: Business logic for analytics
  -- For loans: Derived risk indicators from co-borrower/program data
  -- ========================================================================

  -- Is this a major event? (significant demand impact)
  CASE
    WHEN attendanceEstimate > 20000 THEN true
    WHEN eventType IN ('SPORTS', 'CONCERT') AND attendanceEstimate > 10000 THEN true
    ELSE false
  END AS is_major_event,

  -- Event size category (for analysis)
  CASE
    WHEN attendanceEstimate < 1000 THEN 'small'
    WHEN attendanceEstimate < 10000 THEN 'medium'
    WHEN attendanceEstimate < 50000 THEN 'large'
    ELSE 'mega'
  END AS event_size_category,

  -- Event duration in hours (derived metric)
  CAST((endTime - startTime) / 3600000.0 AS DOUBLE) AS event_duration_hours,

  -- Is event happening during typical commute hours?
  CASE
    WHEN HOUR(FROM_UNIXTIME(startTime / 1000)) BETWEEN 7 AND 9 THEN true
    WHEN HOUR(FROM_UNIXTIME(startTime / 1000)) BETWEEN 16 AND 19 THEN true
    WHEN HOUR(FROM_UNIXTIME(endTime / 1000)) BETWEEN 7 AND 9 THEN true
    WHEN HOUR(FROM_UNIXTIME(endTime / 1000)) BETWEEN 16 AND 19 THEN true
    ELSE false
  END AS overlaps_rush_hour,

  -- Is event on weekend?
  CASE
    WHEN DAYOFWEEK(FROM_UNIXTIME(startTime / 1000)) IN (1, 7) THEN true  -- Sunday=1, Saturday=7
    ELSE false
  END AS is_weekend_event,

  -- Event type category (high-demand vs low-demand)
  CASE
    WHEN eventType IN ('SPORTS', 'CONCERT') THEN 'high_demand'
    WHEN eventType IN ('CONFERENCE', 'FESTIVAL') THEN 'medium_demand'
    ELSE 'low_demand'
  END AS demand_category,

  -- ========================================================================
  -- Metadata (AUDIT TRAIL)
  -- ========================================================================
  current_timestamp() AS silver_processed_at,
  'events_cleaned_v1' AS silver_transformation_version

-- ========================================================================
-- Source: Bronze Layer (Raw Events Data)
-- ========================================================================
FROM lakehouse.bronze.events

-- ========================================================================
-- INCREMENTAL PROCESSING (CRITICAL BEST PRACTICE)
-- Only process NEW records from Bronze
--
-- TEACHABLE: Prevents reprocessing co-borrower data every time
-- For loans: Only process new co-borrower records since last run
-- ========================================================================
WHERE bronze_ingestion_time > (
  SELECT COALESCE(MAX(silver_processed_at), TIMESTAMP('1970-01-01 00:00:00'))
  FROM lakehouse.silver.events_cleaned
)

-- ========================================================================
-- TEACHING NOTES:
--
-- 1. LOGICAL VALIDATION:
--    - Not just range checks (like weather/trips)
--    - Must validate RELATIONSHIPS between fields
--    - startTime < endTime (logical consistency)
--    - duration < 24 hours (business rule)
--
-- 2. LOAN ORIGINATION MAPPING:
--    Events Data                → Loan Special Circumstances
--    ───────────────────────────────────────────────────────────
--    attendanceEstimate         → number of co-borrowers (1-4)
--    startTime/endTime          → loan term start/end dates
--    time range validation      → term validity (6mo - 30yr)
--    eventType enum             → loan program type (FHA, VA, Conventional)
--    is_major_event             → has_guarantor (major risk reduction)
--    event_size_category        → co-borrower_tier (income contribution)
--    venueName (optional)       → guarantor_name (optional)
--
-- 3. SPECIAL CIRCUMSTANCES PATTERN:
--    - Events are optional enrichment (not all trips have nearby events)
--    - Co-borrowers/programs are optional (not all loans have them)
--    - Both add complexity but can improve outcomes
--    - Both need validation even though they're "extras"
--
-- 4. COMPLETENESS VS. VALIDITY:
--    - venueName can be NULL (acceptable with lower quality score)
--    - Similarly, guarantor name might be optional
--    - Teaches: Not all fields are required, but all present fields must be valid
-- ============================================================================
