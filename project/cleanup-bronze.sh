#!/bin/bash

# Bronze Layer Cleanup Script
# Removes all bronze layer data to allow fresh initialization

set -e  # Exit on error

echo "=========================================="
echo "Bronze Layer Cleanup Script"
echo "=========================================="
echo ""

# Step 1: Stop bronze streaming consumers
echo "Step 1: Stopping bronze streaming consumers..."
docker-compose -f infra/docker-compose.yml stop bronze-trips-consumer bronze-weather-consumer bronze-events-consumer
echo "✓ Bronze consumers stopped"
echo ""

# Step 2: Delete Kafka topics
echo "Step 2: Deleting Kafka topics..."
docker exec kafka /opt/bitnami/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --delete --topic trips.yellow 2>/dev/null || echo "  - trips.yellow already deleted or doesn't exist"

docker exec kafka /opt/bitnami/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --delete --topic weather.updates 2>/dev/null || echo "  - weather.updates already deleted or doesn't exist"

docker exec kafka /opt/bitnami/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --delete --topic special.events 2>/dev/null || echo "  - special.events already deleted or doesn't exist"

echo "✓ Kafka topics deleted"
echo ""

# Step 3: Drop Iceberg tables from catalog
echo "Step 3: Dropping Iceberg tables from catalog..."
docker exec postgres-iceberg psql -U iceberg -d iceberg -c \
  "DELETE FROM iceberg_tables WHERE table_namespace = 'bronze';" > /dev/null
echo "✓ Iceberg table metadata removed from catalog"
echo ""

# Step 4: Clear MinIO warehouse and checkpoint data
echo "Step 4: Clearing MinIO warehouse and checkpoint data..."
docker exec minio rm -r --force /data/lakehouse/warehouse/bronze/ 2>/dev/null || echo "  - warehouse/bronze/ already deleted or doesn't exist"
docker exec minio rm -r --force /data/lakehouse/checkpoints/bronze/ 2>/dev/null || echo "  - checkpoints/bronze/ already deleted or doesn't exist"
echo "✓ MinIO data cleared"
echo ""

echo "=========================================="
echo "✓ Cleanup complete!"
echo "=========================================="
echo ""
echo "System is ready for fresh initialization."
echo "To start fresh:"
echo "  1. docker-compose -f infra/docker-compose.yml restart producer"
echo "  2. docker-compose -f infra/docker-compose.yml up -d bronze-trips-consumer bronze-weather-consumer bronze-events-consumer"
echo ""
