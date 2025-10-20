-- ============================================================================
-- Silver Layer: events_cleaned Table
-- ============================================================================
-- Description: Cleaned and enriched special events data with derived features for demand forecasting
-- Source: lakehouse.bronze.events
-- Transformation: lakehouse/silver/src/main/resources/sql/events_cleaned.sql
--
-- TEACHING NOTE - LOAN ORIGINATION PARALLEL:
-- This is analogous to a "co_borrower_data" or "loan_programs" table that contains:
-- - Core program/co-borrower data (program_type, co_borrower_id, income)
-- - Derived indicators (has_guarantor, major_risk_reduction)
-- - Categorical features (co_borrower_tier, program_category)
-- - Timing/duration features (program_duration, overlaps_key_dates)
-- ============================================================================

CREATE TABLE IF NOT EXISTS lakehouse.silver.events_cleaned (
  -- ==========================================================================
  -- Core Fields (pass-through from Bronze)
  -- ==========================================================================
  timestamp TIMESTAMP NOT NULL COMMENT 'Event announcement/update timestamp',
  location_id BIGINT NOT NULL COMMENT 'Location identifier (TLC Taxi Zone ID near venue)',
  event_type STRING NOT NULL COMMENT 'Event type: SPORTS, CONCERT, CONFERENCE, FESTIVAL, PARADE, OTHER',
  attendance_estimate INT NOT NULL COMMENT 'Estimated attendance for the event',
  start_time BIGINT NOT NULL COMMENT 'Event start time (epoch milliseconds)',
  end_time BIGINT NOT NULL COMMENT 'Event end time (epoch milliseconds)',
  venue_name STRING COMMENT 'Venue name (nullable - may not always be available)',
  bronze_ingestion_time TIMESTAMP NOT NULL COMMENT 'Timestamp when record was ingested into Bronze layer',
  bronze_offset BIGINT NOT NULL COMMENT 'Kafka offset for record traceability',
  bronze_partition INT NOT NULL COMMENT 'Kafka partition for record traceability',

  -- ==========================================================================
  -- Derived Business Features (Feature Engineering)
  -- ==========================================================================
  is_major_event BOOLEAN NOT NULL COMMENT 'Whether event is major (>20K attendance or >10K for SPORTS/CONCERT)',
  event_size_category STRING NOT NULL COMMENT 'Event size: small (<1K), medium (1-10K), large (10-50K), mega (>50K)',
  event_duration_hours DOUBLE NOT NULL COMMENT 'Event duration in hours',
  overlaps_rush_hour BOOLEAN NOT NULL COMMENT 'Whether event start/end times overlap with rush hours (7-9am, 4-7pm)',
  is_weekend_event BOOLEAN NOT NULL COMMENT 'Whether event occurs on Saturday or Sunday',
  demand_category STRING NOT NULL COMMENT 'Expected demand impact: high_demand (SPORTS/CONCERT), medium_demand (CONFERENCE/FESTIVAL), low_demand (OTHER)',
  event_start_hour INT NOT NULL COMMENT 'Hour of day when event starts (0-23)',
  event_end_hour INT NOT NULL COMMENT 'Hour of day when event ends (0-23)',
  has_venue_details BOOLEAN NOT NULL COMMENT 'Whether venue name information is available',
  duration_category STRING NOT NULL COMMENT 'Duration category: brief (<2h), moderate (2-6h), extended (6-12h), multi_day (>12h)',

  -- ==========================================================================
  -- Quality Tracking (Data Quality Audit)
  -- ==========================================================================
  was_duplicate BOOLEAN NOT NULL COMMENT 'Whether this record was identified as a duplicate during deduplication',

  -- ==========================================================================
  -- Metadata (Audit Trail)
  -- ==========================================================================
  silver_processed_at TIMESTAMP NOT NULL COMMENT 'Timestamp when record was processed into Silver layer',
  silver_transformation_version STRING NOT NULL COMMENT 'Version of transformation logic applied (e.g., events_cleaned_v1_deequ)'
)
USING iceberg
COMMENT 'Silver layer: Cleaned and enriched special events data with timing and impact features for demand forecasting';
