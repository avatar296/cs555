#!/bin/bash
#
# Test the distributed file system with a simple upload/download test
# This script starts 1 controller and 3 chunk servers locally
#

MODE=${1:-replication}  # replication or erasure
PORT=8000

echo "=========================================="
echo "Testing Distributed File System"
echo "Mode: $MODE"
echo "=========================================="

# Clean previous test data
echo "Cleaning previous test data..."
rm -rf /tmp/chunk-server
rm -f test-file.txt test-file-downloaded.txt

# Create a test file
echo "Creating test file..."
echo "This is a test file for the distributed file system." > test-file.txt
echo "Line 2: Testing replication and erasure coding." >> test-file.txt
echo "Line 3: CS555 Distributed Systems - HW4" >> test-file.txt

# Build the project
echo "Building project..."
./gradlew build -q

echo ""
echo "=========================================="
echo "To test the system:"
echo "=========================================="
echo ""
echo "1. Start the controller:"
echo "   ./scripts/start-${MODE}-controller.sh $PORT"
echo ""
echo "2. Start chunk servers (in separate terminals):"
echo "   ./scripts/start-${MODE}-chunkserver.sh localhost $PORT"
echo "   ./scripts/start-${MODE}-chunkserver.sh localhost $PORT"
echo "   ./scripts/start-${MODE}-chunkserver.sh localhost $PORT"
echo ""
echo "3. Start the client:"
echo "   ./scripts/start-${MODE}-client.sh localhost $PORT"
echo ""
echo "4. In the client, run:"
echo "   upload test-file.txt /test/file.txt"
echo "   download /test/file.txt test-file-downloaded.txt"
echo ""
echo "5. Verify the files match:"
echo "   diff test-file.txt test-file-downloaded.txt"
echo ""
echo "=========================================="
