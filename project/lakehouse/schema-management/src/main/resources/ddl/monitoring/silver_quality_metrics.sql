-- ============================================================================
-- Monitoring: silver_quality_metrics Table
-- ============================================================================
-- Description: Deequ validation results for Silver layer data quality monitoring
-- Purpose: Compliance audit trail, quality trending, SLA tracking
--
-- TEACHING NOTE - LOAN ORIGINATION PARALLEL:
-- This is your compliance audit trail that regulators require!
-- For loan originations, this would track:
-- - Credit validation results (DTI checks, credit score verification)
-- - Income documentation validation (pay stubs, W2s, tax returns)
-- - Property appraisal validation
-- - Compliance with lending regulations (TILA, RESPA, etc.)
--
-- Every loan application must have a validation record showing it met
-- underwriting criteria. This table provides that audit trail.
-- ============================================================================

CREATE TABLE IF NOT EXISTS lakehouse.monitoring.silver_quality_metrics (
  -- ==========================================================================
  -- When and What
  -- ==========================================================================
  timestamp TIMESTAMP NOT NULL COMMENT 'When validation ran (batch processing time)',
  table_name STRING NOT NULL COMMENT 'Which Silver table was validated (e.g., trips_cleaned, weather_cleaned)',
  job_name STRING NOT NULL COMMENT 'Which Spark streaming job produced this batch (e.g., TripsCleanedJob)',

  -- ==========================================================================
  -- Batch Identification
  -- ==========================================================================
  batch_id BIGINT NOT NULL COMMENT 'Unique batch identifier from Spark Structured Streaming',
  record_count BIGINT NOT NULL COMMENT 'Number of records in this batch',

  -- ==========================================================================
  -- Validation Results
  -- ==========================================================================
  status STRING NOT NULL COMMENT 'Overall validation status: Success, Error, or Warning',
  all_checks_passed BOOLEAN NOT NULL COMMENT 'True if all Deequ constraints passed, False otherwise',
  passed_checks BIGINT NOT NULL COMMENT 'Number of Deequ constraint checks that passed',
  failed_checks BIGINT NOT NULL COMMENT 'Number of Deequ constraint checks that failed',

  -- ==========================================================================
  -- Failure Details
  -- ==========================================================================
  constraint_failures STRING COMMENT 'JSON array of constraint violation details (nullable if all passed)',

  -- ==========================================================================
  -- Quarantine Tracking
  -- ==========================================================================
  batch_quarantined BOOLEAN NOT NULL COMMENT 'True if batch was sent to quarantine tables for manual review'
)
USING iceberg
COMMENT 'Deequ quality validation metrics for Silver layer - compliance audit trail and SLA tracking';

-- ============================================================================
-- USAGE EXAMPLES
-- ============================================================================
--
-- 1. QUALITY TRENDING (Last 7 days pass rate by table):
--    SELECT
--      DATE(timestamp) as date,
--      table_name,
--      AVG(CAST(all_checks_passed AS INT)) as pass_rate,
--      SUM(record_count) as total_records,
--      SUM(CAST(batch_quarantined AS INT)) as quarantined_batches
--    FROM lakehouse.monitoring.silver_quality_metrics
--    WHERE timestamp >= CURRENT_DATE - INTERVAL 7 DAYS
--    GROUP BY DATE(timestamp), table_name
--    ORDER BY date DESC, table_name;
--
-- 2. SLA MONITORING (Batches processed per hour):
--    SELECT
--      DATE_TRUNC('hour', timestamp) as hour,
--      table_name,
--      COUNT(*) as batches_processed,
--      SUM(record_count) as records_processed,
--      SUM(CAST(all_checks_passed AS INT)) * 100.0 / COUNT(*) as pass_rate_pct
--    FROM lakehouse.monitoring.silver_quality_metrics
--    WHERE timestamp >= CURRENT_TIMESTAMP - INTERVAL 24 HOURS
--    GROUP BY DATE_TRUNC('hour', timestamp), table_name
--    ORDER BY hour DESC, table_name;
--
-- 3. FAILURE INVESTIGATION (Recent failures with details):
--    SELECT
--      timestamp,
--      table_name,
--      job_name,
--      batch_id,
--      record_count,
--      failed_checks,
--      constraint_failures
--    FROM lakehouse.monitoring.silver_quality_metrics
--    WHERE all_checks_passed = false
--      AND timestamp >= CURRENT_DATE - INTERVAL 1 DAY
--    ORDER BY timestamp DESC
--    LIMIT 100;
--
-- 4. QUARANTINE AUDIT (Which batches were quarantined):
--    SELECT
--      timestamp,
--      table_name,
--      batch_id,
--      record_count,
--      failed_checks,
--      constraint_failures
--    FROM lakehouse.monitoring.silver_quality_metrics
--    WHERE batch_quarantined = true
--    ORDER BY timestamp DESC;
--
-- ============================================================================
-- LOAN ORIGINATION USE CASES
-- ============================================================================
--
-- For loan processing, this table answers critical compliance questions:
--
-- 1. "Show me all loan applications that failed credit validation in Q1 2024"
--    - Regulators require this for fair lending audits
--
-- 2. "What is our loan validation pass rate?"
--    - Underwriting efficiency KPI
--
-- 3. "Which validation checks fail most frequently?"
--    - Process improvement insights
--
-- 4. "Prove this loan met all underwriting criteria on date X"
--    - Compliance requirement for loan sale to secondary market
--
-- 5. "What percentage of loans require manual underwriter review?"
--    - batch_quarantined = true indicates manual review queue
--
-- ============================================================================
