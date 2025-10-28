#!/bin/bash
#
# Start an Erasure Coding ChunkServer
# Usage: ./scripts/start-erasure-chunkserver.sh [controller-host] [controller-port]
#

# Default values
CONTROLLER_HOST=${1:-localhost}
CONTROLLER_PORT=${2:-8000}

echo "=========================================="
echo "Starting Erasure Coding ChunkServer"
echo "Controller: $CONTROLLER_HOST:$CONTROLLER_PORT"
echo "=========================================="

# Build the project first
echo "Building project..."
./gradlew build -q

# Start the chunk server
echo "Starting chunk server..."
./gradlew runErasureChunkServer -PappArgs="$CONTROLLER_HOST $CONTROLLER_PORT" --console=plain
