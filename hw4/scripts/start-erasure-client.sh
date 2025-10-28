#!/bin/bash
#
# Start the Erasure Coding Client
# Usage: ./scripts/start-erasure-client.sh [controller-host] [controller-port]
#

# Default values
CONTROLLER_HOST=${1:-localhost}
CONTROLLER_PORT=${2:-8000}

echo "=========================================="
echo "Starting Erasure Coding Client"
echo "Controller: $CONTROLLER_HOST:$CONTROLLER_PORT"
echo "=========================================="
echo ""
echo "Available commands:"
echo "  upload <source> <destination>"
echo "  download <source> <destination>"
echo "  exit"
echo ""

# Build the project first
echo "Building project..."
./gradlew build -q

# Start the client
./gradlew runErasureClient -PappArgs="$CONTROLLER_HOST $CONTROLLER_PORT" --console=plain
