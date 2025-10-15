# Manual Testing Guide

## Current Status
- ✅ Avro schemas created (TripEvent, WeatherEvent, SpecialEvent)
- ✅ Data generators completed
- ✅ Configuration system updated
- 🚧 Multi-threading not yet implemented

## Testing What's Available Now

### 1. Verify Schemas Compiled
```bash
# Build the common module
./gradlew :common:build

# Check generated classes exist
ls -la common/build/generated-main-avro-java/csx55/sta/schema/
```

**Expected output:**
- `TripEvent.java`
- `WeatherEvent.java`
- `WeatherCondition.java`
- `SpecialEvent.java`
- `EventType.java`

### 2. Verify Configuration Loads
```bash
# Try building producer (will fail to run but config should parse)
./gradlew :producer:build
```

**Check for compilation errors** - should compile successfully even though ProducerApp needs updating.

### 3. Test Configuration Parsing
Create a simple test to verify config:

```bash
# Set various config options
export TRIP_RATE=1000
export WEATHER_RATE=20
export EVENT_RATE=0.5
export TRIP_ERROR_RATE=0.05

# Try to build (config will be validated during build)
./gradlew :producer:build
```

## Testing After Multi-Threading Implementation

### Test 1: Basic Single Stream (Trip Events Only)
```bash
# Start Kafka
docker-compose up -d kafka schema-registry kafka-ui

# Wait for services
sleep 30

# Run only trip stream
WEATHER_RATE=0 EVENT_RATE=0 TRIP_RATE=100 TRIP_TOTAL_EVENTS=1000 ./gradlew :producer:run
```

**Verify:**
1. Producer starts without errors
2. Console shows progress logs
3. Kafka UI (http://localhost:8080) shows `trips.yellow` topic
4. Messages appear in topic
5. Producer stops after 1000 events
6. Final statistics printed

### Test 2: All Three Streams Together
```bash
# Run all streams with different rates
TRIP_RATE=500 WEATHER_RATE=10 EVENT_RATE=0.1 ./gradlew :producer:run
```

**Verify:**
1. Three topics created:
   - `trips.yellow`
   - `weather.updates`
   - `special.events`
2. Different message rates visible in Kafka UI
3. Statistics shown for each stream
4. All streams running concurrently

### Test 3: Error Injection
```bash
# 10% errors on trips, 20% on weather
TRIP_ERROR_RATE=0.1 WEATHER_ERROR_RATE=0.2 TRIP_TOTAL_EVENTS=1000 WEATHER_TOTAL_EVENTS=100 ./gradlew :producer:run
```

**Verify:**
1. Statistics show valid vs invalid counts
2. ~10% invalid trips
3. ~20% invalid weather events
4. Invalid events have expected error patterns

### Test 4: Disable Specific Streams
```bash
# Only weather and events (no trips)
TRIP_RATE=0 WEATHER_RATE=20 EVENT_RATE=1 ./gradlew :producer:run
```

**Verify:**
1. Only 2 topics created
2. No `trips.yellow` topic
3. Statistics only show weather and events

### Test 5: High Volume Stress Test
```bash
# High rate on all streams
TRIP_RATE=2000 WEATHER_RATE=100 EVENT_RATE=5 TRIP_TOTAL_EVENTS=50000 ./gradlew :producer:run
```

**Verify:**
1. Sustained high throughput
2. No errors/crashes
3. Actual rate matches target rate (check statistics)
4. CPU/memory reasonable

## Manual Verification Steps

### Check Kafka Topics
```bash
# List all topics
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list

# Should see:
# - trips.yellow
# - weather.updates
# - special.events
```

### View Messages in Kafka UI
1. Open http://localhost:8080
2. Click on each topic
3. View "Messages" tab
4. **Verify trip events:**
   - Have pickupLocationId, dropoffLocationId
   - Distances are reasonable (0.5-20 miles)
   - Fares calculated correctly

5. **Verify weather events:**
   - Temperatures in reasonable range
   - Precipitation matches condition (0 for CLEAR, >0 for RAIN/SNOW)
   - Wind speeds reasonable

6. **Verify special events:**
   - Event types are valid
   - Attendance numbers reasonable
   - Some have venue names (70%)
   - Start time < end time

### Check Schema Registry
```bash
# List registered schemas
curl http://localhost:8082/subjects

# Should see:
# - trips.yellow-value
# - weather.updates-value
# - special.events-value

# View specific schema
curl http://localhost:8082/subjects/weather.updates-value/versions/latest
```

### Verify Error Injection Works
With `ERROR_RATE=0.2`, check that ~20% of messages have errors:

**Trip errors might have:**
- Invalid zone IDs (0 or > 263)
- Negative distances
- Null fare amounts

**Weather errors might have:**
- Extreme temperatures (< -50 or > 150°F)
- Negative precipitation
- Negative wind speeds

**Event errors might have:**
- Negative attendance
- End time before start time
- Invalid zone IDs

## Quick Test Commands

### Minimal Test (10 seconds)
```bash
TRIP_TOTAL_EVENTS=100 WEATHER_TOTAL_EVENTS=10 EVENT_TOTAL_EVENTS=1 ./gradlew :producer:run
```

### Medium Test (1 minute)
```bash
TRIP_RATE=1000 WEATHER_RATE=20 EVENT_RATE=1 TRIP_TOTAL_EVENTS=60000 ./gradlew :producer:run
```

### Error Test
```bash
TRIP_ERROR_RATE=0.5 TRIP_TOTAL_EVENTS=100 ./gradlew :producer:run
# Should see ~50 valid, ~50 invalid in statistics
```

### Single Event Type Test
```bash
# Test weather only
TRIP_RATE=0 EVENT_RATE=0 WEATHER_RATE=10 WEATHER_TOTAL_EVENTS=100 ./gradlew :producer:run
```

## Troubleshooting

**Producer won't start:**
```bash
# Check Kafka is running
docker-compose ps

# Check logs
docker logs kafka

# Restart Kafka if needed
docker-compose restart kafka
```

**No messages in Kafka UI:**
```bash
# Check producer logs for errors
./gradlew :producer:run --info

# Verify topic exists
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic trips.yellow
```

**Configuration errors:**
```bash
# Test with explicit valid values
TRIP_RATE=100 TRIP_ERROR_RATE=0.0 TRIP_TOTAL_EVENTS=10 ./gradlew :producer:run
```

**Build fails:**
```bash
# Clean and rebuild
./gradlew clean build

# Check for compilation errors
./gradlew :producer:compileJava
```

## Success Criteria

✅ **Configuration works:**
- Different rates per stream
- Error rates adjustable
- Streams can be disabled

✅ **Data quality:**
- All events have realistic values
- Error injection produces expected errors
- Timestamps are correct

✅ **Performance:**
- Achieves target rates
- No crashes under load
- Memory usage stable

✅ **Kafka integration:**
- Topics created automatically
- Schema Registry integration works
- Messages viewable in Kafka UI
