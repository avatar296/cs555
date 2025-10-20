#!/bin/bash

###############################################################################
# Clean Slate Script - Complete System Reset
# This script completely cleans and resets the lakehouse streaming system
###############################################################################

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "🧹 LAKEHOUSE CLEAN SLATE SCRIPT"
echo "=========================================="
echo ""

# Parse arguments
REBUILD_JARS=true  # Always rebuild JARs by default
REBUILD_IMAGES=false
SKIP_JARS=false

while [[ $# -gt 0 ]]; do
  case $1 in
    --skip-jars)
      SKIP_JARS=true
      REBUILD_JARS=false
      shift
      ;;
    --rebuild-images)
      REBUILD_IMAGES=true
      shift
      ;;
    --rebuild-all)
      REBUILD_JARS=true
      REBUILD_IMAGES=true
      shift
      ;;
    --help)
      echo "Usage: ./clean-slate.sh [OPTIONS]"
      echo ""
      echo "Clean slate script - Resets the lakehouse system"
      echo ""
      echo "By default: Rebuilds JARs (your code) but NOT Docker images (managed dependencies)"
      echo ""
      echo "Options:"
      echo "  --skip-jars         Skip rebuilding JAR files (fast cleanup only)"
      echo "  --rebuild-images    Also rebuild Docker images (slow, requires network)"
      echo "  --rebuild-all       Rebuild both JARs and images"
      echo "  --help              Show this help message"
      echo ""
      echo "Examples:"
      echo "  ./clean-slate.sh                  # Clean + rebuild JARs (recommended)"
      echo "  ./clean-slate.sh --skip-jars      # Fast cleanup, no builds"
      echo "  ./clean-slate.sh --rebuild-all    # Full rebuild (slow)"
      echo ""
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      echo "Use --help for usage information"
      exit 1
      ;;
  esac
done

echo "📋 Cleanup Plan:"
echo "  ✓ Stop all containers"
echo "  ✓ Remove containers and networks"
echo "  ✓ Clean Iceberg catalog"
echo "  ✓ Clean MinIO storage"
echo "  ✓ Clean checkpoints"
if [ "$REBUILD_JARS" = true ]; then
  echo "  ✓ Rebuild JARs (your code)"
else
  echo "  ⊘ Skip rebuilding JARs"
fi
if [ "$REBUILD_IMAGES" = true ]; then
  echo "  ✓ Rebuild Docker images (managed dependencies)"
else
  echo "  ⊘ Skip rebuilding Docker images"
fi
echo ""

read -p "Continue? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cancelled."
    exit 1
fi

echo ""
echo "=========================================="
echo "Step 1: Stopping Containers"
echo "=========================================="

# Force kill all running containers
echo "→ Killing all running containers..."
docker ps -q | xargs docker kill 2>/dev/null || true

# Stop docker-compose services
echo "→ Stopping docker-compose services..."
docker-compose -f infra/docker-compose.yml down 2>/dev/null || true

# Remove any orphaned containers
echo "→ Removing containers..."
docker ps -aq | xargs docker rm -f 2>/dev/null || true

echo "✅ Containers stopped and removed"
echo ""

echo "=========================================="
echo "Step 2: Cleaning Networks"
echo "=========================================="

echo "→ Removing infra network..."
docker network rm infra_default 2>/dev/null || true

echo "→ Pruning unused networks..."
docker network prune -f

echo "✅ Networks cleaned"
echo ""

echo "=========================================="
echo "Step 3: Starting Storage Infrastructure"
echo "=========================================="

echo "→ Starting MinIO and Postgres..."
docker-compose -f infra/docker-compose.yml up -d minio postgres-iceberg

echo "→ Waiting for services to be healthy..."
sleep 10

# Check health
if ! docker ps --format "table {{.Names}}\t{{.Status}}" | grep -E "minio.*healthy" > /dev/null; then
    echo "❌ MinIO failed to start healthy"
    exit 1
fi

if ! docker ps --format "table {{.Names}}\t{{.Status}}" | grep -E "postgres-iceberg.*healthy" > /dev/null; then
    echo "❌ Postgres failed to start healthy"
    exit 1
fi

echo "✅ Storage infrastructure running"
echo ""

echo "=========================================="
echo "Step 4: Cleaning Iceberg Catalog"
echo "=========================================="

echo "→ Deleting all tables from Iceberg catalog..."
docker exec postgres-iceberg psql -U iceberg -d iceberg -c "DELETE FROM iceberg_tables;" 2>/dev/null || true

echo "→ Verifying catalog is empty..."
TABLE_COUNT=$(docker exec postgres-iceberg psql -U iceberg -d iceberg -t -c "SELECT COUNT(*) FROM iceberg_tables;" 2>/dev/null | xargs)

if [ "$TABLE_COUNT" = "0" ]; then
    echo "✅ Iceberg catalog cleaned (0 tables)"
else
    echo "⚠️  Warning: Found $TABLE_COUNT tables remaining"
fi
echo ""

echo "=========================================="
echo "Step 5: Cleaning MinIO Storage"
echo "=========================================="

echo "→ Removing Bronze data..."
docker exec minio sh -c "rm -rf /data/lakehouse/warehouse/bronze/* 2>/dev/null || true"

echo "→ Removing Silver data..."
docker exec minio sh -c "rm -rf /data/lakehouse/warehouse/silver/* 2>/dev/null || true"

echo "→ Removing Gold data..."
docker exec minio sh -c "rm -rf /data/lakehouse/warehouse/gold/* 2>/dev/null || true"

echo "→ Verifying storage is clean..."
BRONZE_COUNT=$(docker exec minio sh -c "ls /data/lakehouse/warehouse/bronze/ 2>/dev/null | wc -l" | xargs)
SILVER_COUNT=$(docker exec minio sh -c "ls /data/lakehouse/warehouse/silver/ 2>/dev/null | wc -l" | xargs)

echo "✅ MinIO storage cleaned (bronze: $BRONZE_COUNT files, silver: $SILVER_COUNT files)"
echo ""

echo "=========================================="
echo "Step 6: Cleaning Checkpoints"
echo "=========================================="

echo "→ Starting Spark master..."
docker-compose -f infra/docker-compose.yml up -d spark-master
sleep 5

echo "→ Cleaning Bronze checkpoints..."
docker exec spark-master sh -c "rm -rf /tmp/checkpoint/bronze/* 2>/dev/null || true"

echo "→ Cleaning Silver checkpoints..."
docker exec spark-master sh -c "rm -rf /tmp/checkpoint/silver/* 2>/dev/null || true"

echo "→ Cleaning Gold checkpoints..."
docker exec spark-master sh -c "rm -rf /tmp/checkpoint/gold/* 2>/dev/null || true"

echo "→ Verifying checkpoints are clean..."
docker exec spark-master ls -la /tmp/checkpoint/bronze/ /tmp/checkpoint/silver/ /tmp/checkpoint/gold/

echo "✅ Checkpoints cleaned"
echo ""

echo "=========================================="
echo "Step 7: Stopping Infrastructure"
echo "=========================================="

echo "→ Stopping all services..."
docker-compose -f infra/docker-compose.yml down

echo "✅ Infrastructure stopped"
echo ""

if [ "$REBUILD_JARS" = true ]; then
    echo "=========================================="
    echo "Step 8: Rebuilding JARs"
    echo "=========================================="

    echo "→ Cleaning and building schema-management, bronze, silver..."
    ./gradlew clean :lakehouse:schema-management:jar :lakehouse:bronze:jar :lakehouse:silver:jar --console=plain

    echo "✅ JARs rebuilt"
    echo ""
fi

if [ "$REBUILD_IMAGES" = true ]; then
    echo "=========================================="
    echo "Step 9: Rebuilding Docker Images"
    echo "=========================================="

    echo "→ Rebuilding spark-master image..."
    docker-compose -f infra/docker-compose.yml build --no-cache spark-master

    echo "✅ Docker images rebuilt"
    echo ""
fi

echo "=========================================="
echo "✅ CLEAN SLATE COMPLETE!"
echo "=========================================="
echo ""
echo "System is now completely reset:"
echo "  ✓ All containers stopped and removed"
echo "  ✓ Networks cleaned"
echo "  ✓ Iceberg catalog empty"
echo "  ✓ MinIO storage clean"
echo "  ✓ Checkpoints cleared"
if [ "$REBUILD_JARS" = true ]; then
  echo "  ✓ JARs rebuilt (your code ready)"
fi
if [ "$REBUILD_IMAGES" = true ]; then
  echo "  ✓ Docker images rebuilt"
fi
echo ""
echo "Next steps to start the pipeline:"
echo "  1. Start infrastructure:"
echo "     docker-compose -f infra/docker-compose.yml up -d minio postgres-iceberg kafka schema-registry spark-master spark-worker"
echo ""
echo "  2. Setup MinIO buckets:"
echo "     docker-compose -f infra/docker-compose.yml up minio-setup"
echo ""
echo "  3. Create Bronze tables (with hourly/daily partitioning):"
echo "     docker-compose -f infra/docker-compose.yml up bronze-table-setup"
echo ""
echo "  4. Create Monitoring tables (with daily partitioning):"
echo "     docker-compose -f infra/docker-compose.yml up monitoring-table-setup"
echo ""
echo "  5. Create Silver tables (with daily/monthly partitioning):"
echo "     docker-compose -f infra/docker-compose.yml up silver-table-setup"
echo ""
echo "  6. Start your producers and streaming consumers:"
echo "     docker-compose -f infra/docker-compose.yml up -d bronze-trips-consumer silver-trips-consumer"
echo ""
echo "NOTE: All tables now created with partitioning via schema-management!"
echo "      Bronze: hourly (trips/weather), daily (events)"
echo "      Silver: daily (trips/weather), monthly (events)"
echo "      Monitoring: daily"
echo ""
