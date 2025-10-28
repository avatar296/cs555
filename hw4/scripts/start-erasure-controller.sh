#!/bin/bash
#
# Start the Erasure Coding Controller
# Usage: ./scripts/start-erasure-controller.sh [port]
#

# Default port
PORT=${1:-8000}

echo "=========================================="
echo "Starting Erasure Coding Controller"
echo "Port: $PORT"
echo "=========================================="

# Build the project first
echo "Building project..."
./gradlew build -q

# Start the controller
echo "Starting controller on port $PORT..."
./gradlew runErasureController -PappArgs="$PORT" --console=plain
