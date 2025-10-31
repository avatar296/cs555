#!/bin/bash
#
# Start a ChunkServer
# Usage: ./scripts/chunkserver.sh [controller-host] [controller-port]
# Default: localhost 8000
#

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
# Navigate to project root (parent of scripts directory)
cd "$SCRIPT_DIR/.."

HOST=${1:-localhost}
PORT=${2:-8000}

echo "=========================================="
echo "Starting ChunkServer"
echo "Controller: $HOST:$PORT"
echo "=========================================="

# Start the chunk server in local mode (unique directories per server)
./gradlew runReplicationChunkServer -PappArgs="$HOST $PORT" -Ddfs.local.mode=true --console=plain
