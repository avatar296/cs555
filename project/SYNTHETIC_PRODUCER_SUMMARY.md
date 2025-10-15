# Synthetic Data Producer - Implementation Summary

## Overview

The producer has been completely refactored to generate **synthetic** NYC Taxi trip data instead of reading from real Parquet files. This makes it perfect for architecture testing with full control over data characteristics and error injection.

## Architecture

```
Producer Pipeline:
┌─────────────────────────┐
│ SyntheticTripGenerator  │  Generates realistic trip events
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│    ErrorInjector        │  Optionally injects errors (configurable rate)
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│   Avro Serialization    │  Encodes to Avro with Schema Registry
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│   Kafka Producer        │  Publishes to trips.yellow topic
└─────────────────────────┘
```

## Components

### 1. SyntheticProducerConfig.java (77 lines)
**Purpose:** Centralized configuration management

**Features:**
- Reads from environment variables or system properties
- Validates all configuration values
- Provides sensible defaults

**Configuration Options:**
- Kafka settings (bootstrap servers, schema registry, topic)
- Producer behavior (arrival rate, error rate, total events)
- Data generation (realtime vs simulated time)

### 2. SyntheticTripGenerator.java (112 lines)
**Purpose:** Generate realistic synthetic trip events

**Features:**
- **Location IDs:** Random selection from valid NYC TLC zones (1-263)
- **Trip Distances:** Normal distribution (mean=3.5mi, stddev=2.5mi), clamped to 0.5-20mi
- **Fares:** Calculated realistically ($2.50 base + $2.50/mile + optional surcharges)
- **Passengers:** Weighted distribution (heavily toward 1-2 passengers)
- **Timestamps:** Current time or simulated progression

**Data Quality:**
- All values within realistic bounds
- Proper statistical distributions
- Deterministic but varied

### 3. ErrorInjector.java (149 lines)
**Purpose:** Inject controlled errors for testing data quality handling

**Error Types (10 types with weighted probabilities):**
1. **Null fare amount** (20%) - Optional field set to null
2. **Null passenger count** (15%) - Optional field set to null
3. **Invalid pickup zone** (15%) - Zone ID outside 1-263
4. **Invalid dropoff zone** (15%) - Zone ID outside 1-263
5. **Negative distance** (10%) - Trip distance < 0
6. **Zero distance** (10%) - Trip distance = 0
7. **Excessive distance** (5%) - Trip > 100 miles
8. **Negative fare** (5%) - Fare amount < 0
9. **Zero passengers** (3%) - Passenger count = 0
10. **Excessive passengers** (2%) - Passengers > 10

**Features:**
- Configurable error rate (0.0 to 1.0)
- Weighted random selection of error types
- Validation function to detect bad events

### 4. ProducerApp.java (170 lines)
**Purpose:** Main application orchestrating data generation and Kafka publishing

**Features:**
- **Rate limiting:** Precise nanosecond-level timing to hit target rate
- **Statistics tracking:**
  - Events generated (total count)
  - Successfully sent vs failed
  - Valid vs invalid events
  - Actual achieved throughput
- **Progress logging:** Updates every 10 seconds
- **Graceful shutdown:** Prints final statistics on Ctrl+C
- **Kafka optimization:** Batching, compression (snappy), pipelining

## Key Features

### ✅ No External Dependencies
- No Parquet files to download
- No DuckDB required
- Instant startup

### ✅ Configurable Behavior
```bash
# Default: 500 msg/sec, 0% errors, infinite
./gradlew :producer:run

# Custom: 1000 msg/sec, 5% errors, 10K events
RATE=1000 ERROR_RATE=0.05 TOTAL_EVENTS=10000 ./gradlew :producer:run
```

### ✅ Comprehensive Statistics
```
Progress: 10000 events generated | 502.3/s actual rate | 9998 sent | 2 failed | 9500 valid | 500 invalid
```

### ✅ Realistic Data
- Valid NYC taxi zone IDs
- Normal distribution for distances
- Calculated fares based on realistic rates
- Weighted passenger distributions

### ✅ Error Injection for Testing
- 10 different error types
- Configurable error rate (0-100%)
- Validation function included

## Code Statistics

- **Total Lines:** 508 lines of Java
- **Classes:** 4 main classes
- **Configuration Options:** 8 environment variables
- **Error Types:** 10 distinct error types
- **Build Time:** ~2 seconds (clean build)

## Usage Examples

### Basic Usage
```bash
# Start Kafka
docker-compose up -d kafka schema-registry kafka-ui

# Run producer (defaults)
./gradlew :producer:run
```

### Testing Scenarios

**Quick Test (1000 events):**
```bash
TOTAL_EVENTS=1000 ./gradlew :producer:run
```

**Error Handling Test (20% bad data):**
```bash
ERROR_RATE=0.2 TOTAL_EVENTS=5000 ./gradlew :producer:run
```

**High Volume Stress Test:**
```bash
RATE=2000 TOTAL_EVENTS=100000 ./gradlew :producer:run
```

**Simulated Time for Windowing Tests:**
```bash
USE_REALTIME=false TIME_PROGRESSION_SECONDS=60 RATE=100 ./gradlew :producer:run
```

## Monitoring

**Kafka UI:** http://localhost:8080
- View messages in real-time
- Check schema evolution
- Monitor topic metrics

**Producer Logs:**
- Progress updates every 10 seconds
- Final statistics on shutdown
- Error tracking and reporting

## Advantages for Architecture Testing

1. **Repeatable:** Same configuration = same behavior
2. **Fast:** No I/O delays from reading files
3. **Controllable:** Tweak rates and error characteristics
4. **Isolated:** No external dependencies
5. **Realistic:** Data follows proper distributions
6. **Error Testing:** Built-in bad data injection

## Next Steps

The consumer can now be implemented to:
1. Read from `trips.yellow` topic
2. Handle both valid and invalid events
3. Perform data quality validation
4. Apply windowed aggregations
5. Write to downstream systems

The synthetic producer provides a perfect controlled test environment for developing and testing the entire streaming pipeline!
