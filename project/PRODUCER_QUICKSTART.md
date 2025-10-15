# Producer Quick Start Guide - Synthetic Data Generation

## Overview

The producer generates **synthetic** NYC Taxi trip data and publishes it to Kafka. No external data files needed!

## One-Time Setup

1. **Make sure Java 17+ is installed:**
   ```bash
   java -version
   ```

2. **Build the project:**
   ```bash
   ./gradlew build
   ```

## Every Time You Want to Run

### 1. Start Kafka Infrastructure
```bash
docker-compose up -d kafka schema-registry kafka-ui
```

Wait ~30 seconds for services to start, then verify:
```bash
docker-compose ps
```

### 2. Run the Producer

**Basic run (default settings):**
```bash
./gradlew :producer:run
```

This will generate trips at **500 msg/sec** with **0% error rate** indefinitely.

**Custom configuration:**
```bash
# Generate 10,000 events at 1000 msg/sec with 5% bad data
RATE=1000 ERROR_RATE=0.05 TOTAL_EVENTS=10000 ./gradlew :producer:run

# Continuous generation with high error rate for testing
RATE=500 ERROR_RATE=0.2 ./gradlew :producer:run

# Stress test - 2000 msg/sec, 10% errors, 50K events
RATE=2000 ERROR_RATE=0.1 TOTAL_EVENTS=50000 ./gradlew :producer:run
```

### 3. Monitor the Data

**View in Kafka UI:**
- Open http://localhost:8080
- Click on `trips.yellow` topic
- View messages in real-time

**Check topic exists:**
```bash
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
```

## Configuration Options

| Environment Variable | Description | Default | Example |
|---------------------|-------------|---------|---------|
| `RATE` | Messages per second | 500 | `RATE=1000` |
| `ERROR_RATE` | Percentage of bad records (0.0-1.0) | 0.0 | `ERROR_RATE=0.05` (5%) |
| `TOTAL_EVENTS` | Total events to generate (-1 = infinite) | -1 | `TOTAL_EVENTS=10000` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address | localhost:29092 | |
| `SCHEMA_REGISTRY_URL` | Schema Registry URL | http://localhost:8082 | |
| `TOPIC_NAME` | Kafka topic name | trips.yellow | |
| `USE_REALTIME` | Use current time vs simulated | true | `USE_REALTIME=false` |
| `TIME_PROGRESSION_SECONDS` | Seconds to advance per event (if USE_REALTIME=false) | 1 | |

## Example Scenarios

### Scenario 1: Quick Test (1000 events)
```bash
TOTAL_EVENTS=1000 ./gradlew :producer:run
```

### Scenario 2: Error Testing (20% bad data)
```bash
ERROR_RATE=0.2 TOTAL_EVENTS=5000 ./gradlew :producer:run
```

### Scenario 3: High Volume Stress Test
```bash
RATE=2000 TOTAL_EVENTS=100000 ./gradlew :producer:run
```

### Scenario 4: Simulated Time Progression
```bash
USE_REALTIME=false TIME_PROGRESSION_SECONDS=60 RATE=100 ./gradlew :producer:run
```
This generates events with timestamps advancing by 60 seconds each, useful for testing time-based windowing.

## Error Injection Types

When `ERROR_RATE` > 0, the producer randomly injects these error types:

- **Null fields** - Missing fare amount or passenger count
- **Invalid zones** - Pickup/dropoff location IDs outside valid range (1-263)
- **Negative distances** - Trips with negative mileage
- **Zero distances** - Trips with no distance
- **Excessive distances** - Unrealistic trip lengths (>100 miles)
- **Negative fares** - Trips with negative cost
- **Invalid passengers** - Zero or excessive passenger counts

Perfect for testing downstream error handling and data quality validation!

## Output Statistics

The producer logs progress every 10 seconds and final statistics:

```
Progress: 10000 events generated | 502.3/s actual rate | 9998 sent | 2 failed | 9500 valid | 500 invalid
```

- **events generated** - Total events created
- **actual rate** - Real achieved throughput
- **sent** - Successfully sent to Kafka
- **failed** - Failed to send
- **valid** - Good quality events
- **invalid** - Events with injected errors

## Troubleshooting

**Producer won't connect to Kafka:**
- Check Kafka is running: `docker-compose ps kafka`
- Check ports: `lsof -i :29092`
- View Kafka logs: `docker logs kafka`

**Build fails:**
```bash
./gradlew clean build --refresh-dependencies
```

**Want to modify the code:**
1. Edit files in `producer/src/main/java/csx55/sta/producer/`
2. Rebuild: `./gradlew :producer:build`
3. Run: `./gradlew :producer:run`

**Stop producer gracefully:**
Press `Ctrl+C` - it will print final statistics before exiting

## Stop Everything

```bash
docker-compose down
```

## What the Producer Does

1. Generates synthetic NYC Taxi trip data:
   - Random pickup/dropoff locations (1-263, real NYC TLC zones)
   - Realistic trip distances (0.5-20 miles, normal distribution)
   - Calculated fares based on distance (~$2.50 base + $2.50/mile)
   - Passenger counts (1-6, weighted toward 1-2)
   - Current or simulated timestamps

2. Optionally injects errors based on `ERROR_RATE`

3. Serializes to Avro format with Schema Registry

4. Publishes to Kafka with rate limiting

5. Tracks and reports statistics

## Data Schema

Each event contains:
- `timestamp` - Event time (epoch millis)
- `pickupLocationId` - NYC TLC zone ID (1-263)
- `dropoffLocationId` - NYC TLC zone ID (1-263)
- `tripDistance` - Miles traveled (0.5-20.0)
- `fareAmount` - Cost in USD (optional, can be null)
- `passengerCount` - Number of passengers (optional, 1-6)

## Why Synthetic Data?

- ✅ **No dependencies** - No internet, no downloads
- ✅ **Fast startup** - Instant, no file loading
- ✅ **Repeatable** - Controlled test scenarios
- ✅ **Configurable errors** - Test data quality handling
- ✅ **Perfect for architecture testing** - Focus on the system, not the data
