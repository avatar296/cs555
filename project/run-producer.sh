#!/bin/bash
# Run the Kafka producer to replay NYC Taxi trip data

set -e

echo "Building producer..."
./gradlew :producer:build

echo "Running producer..."
echo "Environment variables:"
echo "  KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-localhost:29092}"
echo "  SCHEMA_REGISTRY_URL=${SCHEMA_REGISTRY_URL:-http://localhost:8082}"
echo "  RATE=${RATE:-500} messages/sec"
echo ""

./gradlew :producer:run
