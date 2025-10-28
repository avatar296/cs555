#!/bin/bash
#
# Start the Replication Controller
# Usage: ./scripts/controller.sh [port]
# Default port: 8000
#

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
# Navigate to project root (parent of scripts directory)
cd "$SCRIPT_DIR/.."

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
