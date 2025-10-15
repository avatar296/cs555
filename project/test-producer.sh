#!/bin/bash
# Quick test script for the multi-stream producer

echo "========================================="
echo "Multi-Stream Producer Test"
echo "========================================="
echo ""

# Check if Kafka is running
echo "Checking if Kafka is running..."
if ! docker ps | grep -q kafka; then
    echo "❌ Kafka is not running!"
    echo ""
    echo "Starting Kafka, Schema Registry, and Kafka UI..."
    docker-compose up -d kafka schema-registry kafka-ui
    echo ""
    echo "Waiting 30 seconds for services to start..."
    sleep 30
else
    echo "✅ Kafka is running"
fi

echo ""
echo "========================================="
echo "Test 1: Quick Test (all three streams)"
echo "========================================="
echo "Generating:"
echo "  - 100 trip events"
echo "  - 10 weather events"
echo "  - 1 special event"
echo ""
echo "Press Enter to start..."
read

TRIP_TOTAL_EVENTS=100 WEATHER_TOTAL_EVENTS=10 EVENT_TOTAL_EVENTS=1 ./gradlew :producer:run

echo ""
echo "========================================="
echo "✅ Test Complete!"
echo "========================================="
echo ""
echo "View results in Kafka UI: http://localhost:8080"
echo ""
echo "Expected topics:"
echo "  - trips.yellow (100 messages)"
echo "  - weather.updates (10 messages)"
echo "  - special.events (1 message)"
echo ""
echo "Would you like to run more tests? (y/n)"
read -r response

if [[ "$response" == "y" ]]; then
    echo ""
    echo "========================================="
    echo "Test 2: Error Injection Test"
    echo "========================================="
    echo "Generating 100 trip events with 50% error rate"
    echo "Press Enter to start..."
    read

    TRIP_ERROR_RATE=0.5 TRIP_TOTAL_EVENTS=100 WEATHER_RATE=0 EVENT_RATE=0 ./gradlew :producer:run

    echo ""
    echo "Check the statistics - should show ~50% valid, ~50% invalid"
fi

echo ""
echo "========================================="
echo "Testing Complete!"
echo "========================================="
echo ""
echo "To run custom tests, use:"
echo ""
echo "  # All streams with custom rates"
echo "  TRIP_RATE=1000 WEATHER_RATE=20 EVENT_RATE=1 ./gradlew :producer:run"
echo ""
echo "  # Only weather stream"
echo "  TRIP_RATE=0 EVENT_RATE=0 WEATHER_RATE=50 ./gradlew :producer:run"
echo ""
echo "  # High error rate test"
echo "  TRIP_ERROR_RATE=0.3 WEATHER_ERROR_RATE=0.2 ./gradlew :producer:run"
echo ""
