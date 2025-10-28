#!/bin/bash
#
# Start the Replication Controller
# Usage: ./scripts/start-replication-controller.sh [port]
#

# Default port
PORT=${1:-8000}

echo "=========================================="
echo "Starting Replication Controller"
echo "Port: $PORT"
echo "=========================================="

# Build the project first
echo "Building project..."
./gradlew build -q

# Start the controller
echo "Starting controller on port $PORT..."
./gradlew runReplicationController -PappArgs="$PORT" --console=plain
