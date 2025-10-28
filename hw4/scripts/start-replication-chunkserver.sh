#!/bin/bash
#
# Start a Replication ChunkServer
# Usage: ./scripts/start-replication-chunkserver.sh [controller-host] [controller-port]
#

# Default values
CONTROLLER_HOST=${1:-localhost}
CONTROLLER_PORT=${2:-8000}

echo "=========================================="
echo "Starting Replication ChunkServer"
echo "Controller: $CONTROLLER_HOST:$CONTROLLER_PORT"
echo "=========================================="

# Build the project first
echo "Building project..."
./gradlew build -q

# Start the chunk server
echo "Starting chunk server..."
./gradlew runReplicationChunkServer -PappArgs="$CONTROLLER_HOST $CONTROLLER_PORT" --console=plain
