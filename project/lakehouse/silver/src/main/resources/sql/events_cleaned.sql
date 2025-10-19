-- ============================================================================
-- Silver Layer: Events Transformation
-- ============================================================================
-- Purpose: Transform and enrich special events data
-- Pattern: SQL for transformation, Deequ for validation
--
-- TEACHING NOTE - LOAN ORIGINATION PARALLEL:
-- This SQL handles transformation of special circumstances data.
-- For loan originations, this is like transforming:
--   - Co-borrower information
--   - Special loan programs (FHA, VA, first-time buyer)
--   - Guarantor information
--   - Collateral details
--
-- Events are CONTEXTUAL data (special circumstances affecting trips).
-- Similarly, loan programs/co-borrowers are special circumstances in lending.
--
-- VALIDATION: Handled by Deequ (not SQL)
--   - Attendance range checks      → Deequ checks
--   - Time range validation        → Deequ checks
--   - Logical consistency checks   → Deequ checks
--   - Required field checks        → Deequ checks
--
-- BEST PRACTICE: Transformation separated from validation
-- ============================================================================

-- ============================================================================
-- DEDUPLICATION CTE
-- Handles duplicate event records
-- For loans: Prevents duplicate co-borrower/program records
--
-- NOTE: Incremental processing is handled by Spark Structured Streaming
-- checkpoint, not by SQL WHERE clause. Each micro-batch contains only
-- new data from Bronze layer.
-- ============================================================================
WITH deduplicated_events AS (
  SELECT
    *,
    ROW_NUMBER() OVER (
      PARTITION BY timestamp, locationId, eventType
      ORDER BY bronze_ingestion_time DESC  -- Keep most recent
    ) AS row_num
  FROM lakehouse_bronze_events  -- Temp view created from streaming source
)
SELECT
  -- ========================================================================
  -- Core Fields (pass-through from Bronze)
  -- NOTE: Standardized to snake_case for consistency
  -- ========================================================================
  timestamp,
  locationId AS location_id,
  eventType AS event_type,
  attendanceEstimate AS attendance_estimate,
  startTime AS start_time,
  endTime AS end_time,
  venueName AS venue_name,
  bronze_ingestion_time,
  bronze_offset,
  bronze_partition,

  -- ========================================================================
  -- Derived Business Features (FEATURE ENGINEERING)
  -- TEACHABLE: Transform raw event data into business insights
  -- For loans: Transform co-borrower/program data into risk/eligibility indicators
  -- ========================================================================

  -- Is this a major event? (significant demand impact)
  -- Loan parallel: has_guarantor (major risk reduction)
  CASE
    WHEN attendanceEstimate > ${MAJOR_EVENT_ATTENDANCE_THRESHOLD} THEN true
    WHEN eventType IN ('SPORTS', 'CONCERT') AND attendanceEstimate > ${MAJOR_SPORTS_CONCERT_THRESHOLD} THEN true
    ELSE false
  END AS is_major_event,

  -- Event size category (for demand modeling)
  -- Loan parallel: co_borrower_tier (income contribution level)
  CASE
    WHEN attendanceEstimate < ${EVENT_SMALL_MAX} THEN 'small'
    WHEN attendanceEstimate < ${EVENT_MEDIUM_MAX} THEN 'medium'
    WHEN attendanceEstimate < ${EVENT_LARGE_MAX} THEN 'large'
    ELSE 'mega'
  END AS event_size_category,

  -- Event duration in hours (derived metric)
  -- Loan parallel: loan_term_years
  CAST((endTime - startTime) / 3600000.0 AS DOUBLE) AS event_duration_hours,

  -- Is event happening during typical commute hours?
  -- Loan parallel: application_during_business_hours
  CASE
    WHEN HOUR(FROM_UNIXTIME(startTime / 1000)) BETWEEN ${MORNING_RUSH_START} AND ${MORNING_RUSH_END} THEN true
    WHEN HOUR(FROM_UNIXTIME(startTime / 1000)) BETWEEN ${EVENING_RUSH_START} AND ${EVENING_RUSH_END} THEN true
    WHEN HOUR(FROM_UNIXTIME(endTime / 1000)) BETWEEN ${MORNING_RUSH_START} AND ${MORNING_RUSH_END} THEN true
    WHEN HOUR(FROM_UNIXTIME(endTime / 1000)) BETWEEN ${EVENING_RUSH_START} AND ${EVENING_RUSH_END} THEN true
    ELSE false
  END AS overlaps_rush_hour,

  -- Is event on weekend?
  -- Loan parallel: application_on_weekend
  CASE
    WHEN DAYOFWEEK(FROM_UNIXTIME(startTime / 1000)) IN (1, 7) THEN true  -- Sunday=1, Saturday=7
    ELSE false
  END AS is_weekend_event,

  -- Event type demand category (high/medium/low demand)
  -- Loan parallel: loan_program_category (conventional/government/specialty)
  CASE
    WHEN eventType IN ('SPORTS', 'CONCERT') THEN 'high_demand'
    WHEN eventType IN ('CONFERENCE', 'FESTIVAL') THEN 'medium_demand'
    ELSE 'low_demand'
  END AS demand_category,

  -- Event start hour (for time-series analysis)
  -- Loan parallel: application_hour
  HOUR(FROM_UNIXTIME(startTime / 1000)) AS event_start_hour,

  -- Event end hour (for demand duration modeling)
  -- Loan parallel: expected_closing_hour
  HOUR(FROM_UNIXTIME(endTime / 1000)) AS event_end_hour,

  -- Is venue information complete? (data quality indicator)
  -- Loan parallel: has_guarantor_details
  CASE
    WHEN venueName IS NOT NULL AND TRIM(venueName) != '' THEN true
    ELSE false
  END AS has_venue_details,

  -- Duration category (for impact analysis)
  -- Loan parallel: term_category (short/medium/long)
  CASE
    WHEN (endTime - startTime) / 3600000.0 < ${DURATION_BRIEF_MAX} THEN 'brief'
    WHEN (endTime - startTime) / 3600000.0 < ${DURATION_MODERATE_MAX} THEN 'moderate'
    WHEN (endTime - startTime) / 3600000.0 < ${DURATION_EXTENDED_MAX} THEN 'extended'
    ELSE 'multi_day'
  END AS duration_category,

  -- ========================================================================
  -- QUALITY VALIDATION
  -- Validation now handled by Deequ in EventsCleanedJob.scala
  -- Deequ enforces:
  --   - Completeness checks (timestamp, location_id, event_type, start_time, end_time)
  --   - Range validation (attendance_estimate: 1-100,000)
  --
  -- EXCEPTION: Cross-column constraint (start_time < end_time) handled in WHERE clause below
  -- Deequ excels at single-column validation; SQL is clearer for cross-column logic
  -- ========================================================================

  -- ========================================================================
  -- Deduplication Flag
  -- ========================================================================
  CASE WHEN row_num > 1 THEN true ELSE false END AS was_duplicate,

  -- ========================================================================
  -- Metadata (AUDIT TRAIL)
  -- TEACHABLE: Track when special circumstances data was processed
  -- For loans: Track when co-borrower/program data was validated
  -- ========================================================================
  current_timestamp() AS silver_processed_at,
  'events_cleaned_v1_deequ' AS silver_transformation_version

-- ========================================================================
-- Source: Deduplicated Bronze Layer (from CTE above)
-- ========================================================================
FROM deduplicated_events

-- ========================================================================
-- DEDUPLICATION FILTER + LOGICAL CONSISTENCY
-- ========================================================================
WHERE row_num = 1
  AND startTime < endTime  -- Cross-column constraint: event must end after it starts

-- ========================================================================
-- TEACHING NOTES:
--
-- 1. TRANSFORMATION vs. VALIDATION:
--    - SQL handles feature engineering (categorization, derivation)
--    - Deequ handles validation (range checks, logical consistency)
--    - Separation of concerns makes code easier to maintain
--
-- 2. SPECIAL CIRCUMSTANCES PATTERN:
--    - Events are optional enrichment (not all trips have nearby events)
--    - Co-borrowers/programs are optional (not all loans have them)
--    - Both add value when present, but must be validated
--
-- 3. LOAN ORIGINATION MAPPING:
--    Events Feature               → Loan Special Circumstances Feature
--    ────────────────────────────────────────────────────────────────────
--    is_major_event               → has_guarantor
--    event_size_category          → co_borrower_tier
--    event_duration_hours         → loan_term_years
--    overlaps_rush_hour           → application_during_business_hours
--    demand_category              → loan_program_category
--    has_venue_details            → has_guarantor_details
--    duration_category            → term_category
--
-- 4. VALIDATION (Moved to Deequ):
--    - Range checks: attendanceEstimate (1-100,000), duration (< 24 hours)
--    - Completeness: locationId, eventType, startTime, endTime not null
--    - Logical consistency: startTime < endTime
--    - Uniqueness: event records should not duplicate
-- ============================================================================
