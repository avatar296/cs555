package csx55.sta.silver.config

/**
 * Centralized business rules configuration for Silver layer transformations.
 *
 * <p><b>TEACHING NOTE - BUSINESS RULES MANAGEMENT:</b></p>
 * <p>This centralizes all business logic thresholds in one place, making them:</p>
 * <ul>
 *   <li><b>Testable:</b> Change rules for unit tests without modifying SQL</li>
 *   <li><b>Maintainable:</b> Update thresholds without editing multiple SQL files</li>
 *   <li><b>Auditable:</b> Version control tracks business rule changes</li>
 *   <li><b>Documentable:</b> Rationale for thresholds lives with the code</li>
 * </ul>
 *
 * <p><b>Loan Origination Parallel:</b></p>
 * <p>In loan systems, this would contain:</p>
 * <ul>
 *   <li>DTI ratio thresholds (43% qualified mortgage rule)</li>
 *   <li>Credit score tiers (prime: 680+, near-prime: 620-679, subprime: <620)</li>
 *   <li>Loan-to-value (LTV) limits (80% conventional, 96.5% FHA)</li>
 *   <li>Income documentation requirements</li>
 * </ul>
 */
object BusinessRules {

  // ==========================================================================
  // TRIPS - Business Logic Thresholds
  // ==========================================================================

  /**
   * Rush hour time ranges (hours of day, 24-hour format).
   * Morning: 7-9 AM, Evening: 4-7 PM
   *
   * Loan parallel: Business hours for loan applications (9 AM - 5 PM)
   */
  val MORNING_RUSH_START = 7
  val MORNING_RUSH_END = 9
  val EVENING_RUSH_START = 16
  val EVENING_RUSH_END = 19

  /**
   * Trip distance category thresholds (miles).
   *
   * Categories:
   * - Short: < 1 mile
   * - Medium: 1-5 miles
   * - Long: 5-20 miles
   * - Very long: > 20 miles
   *
   * Loan parallel: Loan size categories
   * - Small: < $50K
   * - Medium: $50K-$250K
   * - Large: $250K-$750K (conforming limit)
   * - Jumbo: > $750K
   */
  val DISTANCE_SHORT_MAX = 1.0
  val DISTANCE_MEDIUM_MAX = 5.0
  val DISTANCE_LONG_MAX = 20.0

  /**
   * Fare category thresholds (dollars).
   *
   * Categories:
   * - Economy: < $10
   * - Standard: $10-$30
   * - Premium: $30-$100
   * - Luxury: > $100
   *
   * Loan parallel: Interest rate tiers based on credit risk
   */
  val FARE_ECONOMY_MAX = 10.0
  val FARE_STANDARD_MAX = 30.0
  val FARE_PREMIUM_MAX = 100.0

  // ==========================================================================
  // WEATHER - Business Logic Thresholds
  // ==========================================================================

  /**
   * Weather condition thresholds for severe weather classification.
   *
   * Severe weather criteria:
   * - Precipitation > 1.0 inch
   * - Wind speed > 30 mph
   * - Condition = SNOW or STORM
   *
   * Loan parallel: High-risk credit indicators
   * - DTI > 43%
   * - Credit score < 620
   * - Recent bankruptcy/foreclosure
   */
  val SEVERE_PRECIPITATION_THRESHOLD = 1.0
  val SEVERE_WIND_SPEED_THRESHOLD = 30.0

  /**
   * Temperature category thresholds (Fahrenheit).
   *
   * Categories:
   * - Freezing: < 32°F
   * - Cold: 32-50°F
   * - Mild: 50-70°F
   * - Warm: 70-85°F
   * - Hot: > 85°F
   *
   * Loan parallel: Income brackets for loan product targeting
   */
  val TEMP_FREEZING_MAX = 32.0
  val TEMP_COLD_MAX = 50.0
  val TEMP_MILD_MAX = 70.0
  val TEMP_WARM_MAX = 85.0

  /**
   * Precipitation category thresholds (inches).
   *
   * Categories:
   * - None: 0
   * - Light: < 0.1
   * - Moderate: 0.1-0.5
   * - Heavy: > 0.5
   *
   * Loan parallel: Debt level categories
   */
  val PRECIP_LIGHT_MAX = 0.1
  val PRECIP_MODERATE_MAX = 0.5

  /**
   * Wind category thresholds (mph).
   *
   * Categories:
   * - Calm: < 10 mph
   * - Breezy: 10-20 mph
   * - Windy: 20-30 mph
   * - Dangerous: > 30 mph
   *
   * Loan parallel: Employment stability indicators
   */
  val WIND_CALM_MAX = 10.0
  val WIND_BREEZY_MAX = 20.0
  val WIND_WINDY_MAX = 30.0

  /**
   * Weather impact thresholds.
   *
   * High impact:
   * - Wind speed > 50 mph
   * - Condition = STORM
   *
   * Medium impact:
   * - Precipitation > 0.5 inches
   * - Condition = SNOW or RAIN
   *
   * Loan parallel: Risk tier thresholds
   */
  val HIGH_IMPACT_WIND_THRESHOLD = 50.0
  val MEDIUM_IMPACT_PRECIP_THRESHOLD = 0.5

  /**
   * Extreme temperature thresholds (affects travel decisions).
   *
   * - Too cold: < 20°F
   * - Too hot: > 95°F
   *
   * Loan parallel: Income extremes requiring manual review
   */
  val EXTREME_COLD_THRESHOLD = 20.0
  val EXTREME_HOT_THRESHOLD = 95.0

  // ==========================================================================
  // EVENTS - Business Logic Thresholds
  // ==========================================================================

  /**
   * Major event attendance thresholds.
   *
   * Major event criteria:
   * - Attendance > 20,000 (any event type)
   * - Attendance > 10,000 (SPORTS or CONCERT)
   *
   * Loan parallel: High-value loan thresholds requiring senior approval
   */
  val MAJOR_EVENT_ATTENDANCE_THRESHOLD = 20000
  val MAJOR_SPORTS_CONCERT_THRESHOLD = 10000

  /**
   * Event size category thresholds (attendance).
   *
   * Categories:
   * - Small: < 1,000
   * - Medium: 1,000-10,000
   * - Large: 10,000-50,000
   * - Mega: > 50,000
   *
   * Loan parallel: Loan size tiers (co-borrower income contribution)
   */
  val EVENT_SMALL_MAX = 1000
  val EVENT_MEDIUM_MAX = 10000
  val EVENT_LARGE_MAX = 50000

  /**
   * Event duration category thresholds (hours).
   *
   * Categories:
   * - Brief: < 2 hours
   * - Moderate: 2-6 hours
   * - Extended: 6-12 hours
   * - Multi-day: > 12 hours
   *
   * Loan parallel: Loan term categories
   * - Short: < 15 years
   * - Medium: 15-20 years
   * - Long: 20-30 years
   * - Extended: > 30 years
   */
  val DURATION_BRIEF_MAX = 2.0
  val DURATION_MODERATE_MAX = 6.0
  val DURATION_EXTENDED_MAX = 12.0

  // ==========================================================================
  // INCREMENTAL PROCESSING - Watermark Configuration
  // ==========================================================================

  /**
   * Grace period for late-arriving data (hours).
   *
   * When processing incrementally, we look back this many hours from the
   * last processed watermark to catch late-arriving data.
   *
   * Example: If last processed timestamp was 2PM, with 1-hour grace period,
   * we'll reprocess data from 1PM onward.
   *
   * Loan parallel: Backdate window for loan modifications
   */
  val INCREMENTAL_GRACE_PERIOD_HOURS = 1

  /**
   * Default epoch timestamp for first run (when no watermark exists).
   *
   * Loan parallel: System go-live date for historical data processing
   */
  val DEFAULT_WATERMARK_EPOCH = "1970-01-01 00:00:00"
}
