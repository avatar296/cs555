#!/bin/bash

# Corruption Testing Script for CS555 HW4
# Creates test file and corrupts a chunk for testing Phase 3

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR/.."

CHUNK_DIR_PATTERN="/tmp/chunk-server-*"
TEST_FILE="/tmp/test.txt"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "========================================="
echo "  Corruption Testing Script"
echo "========================================="
echo ""

# Step 1: Ensure test file exists
if [ ! -f "$TEST_FILE" ]; then
    echo -e "${YELLOW}Creating test file: $TEST_FILE${NC}"
    echo "This is a test file for CS555 HW4 corruption detection." > "$TEST_FILE"
    echo "✓ Test file created (59 bytes)"
else
    echo -e "${GREEN}✓ Test file exists: $TEST_FILE${NC}"
    ls -lh "$TEST_FILE"
fi
echo ""

# Step 2: Check if chunk directories exist
CHUNK_DIRS=$(find /tmp -maxdepth 1 -type d -name "chunk-server-*" 2>/dev/null)

if [ -z "$CHUNK_DIRS" ]; then
    echo -e "${RED}✗ No ChunkServer directories found!${NC}"
    echo "  Start ChunkServers first!"
    exit 1
fi

echo -e "${BLUE}Found ChunkServer directories:${NC}"
echo "$CHUNK_DIRS" | nl -w2 -s'. '
echo ""

# Step 3: Find chunks for specified file
if [ $# -eq 0 ]; then
    echo -e "${YELLOW}Usage: $0 <dfs-filename>${NC}"
    echo ""
    echo "Available files:"
    find /tmp/chunk-server-* -type f -name "*_chunk*" 2>/dev/null | \
        sed 's|/tmp/chunk-server-[0-9]*/||' | \
        sed 's/_chunk.*//' | \
        sort -u | \
        sed 's|^|  /|'
    exit 0
fi

DFS_FILE="$1"
# Remove leading slash for path matching
SEARCH_PATH="${DFS_FILE#/}"

# Find all replicas of this chunk
echo -e "${BLUE}Searching for replicas of: $DFS_FILE${NC}"
CHUNK_FILES=$(find /tmp/chunk-server-* -type f -path "*/${SEARCH_PATH}_chunk*" 2>/dev/null)

if [ -z "$CHUNK_FILES" ]; then
    echo -e "${RED}✗ No chunks found for: $DFS_FILE${NC}"
    echo ""
    echo "Available files:"
    find /tmp/chunk-server-* -type f -name "*_chunk*" 2>/dev/null | \
        sed 's|/tmp/chunk-server-[0-9]*/||' | \
        sed 's/_chunk.*//' | \
        sort -u | \
        sed 's|^|  /|'
    exit 1
fi

NUM_REPLICAS=$(echo "$CHUNK_FILES" | wc -l | tr -d ' ')
echo -e "${GREEN}✓ Found $NUM_REPLICAS replica(s):${NC}"
echo "$CHUNK_FILES" | nl -w2 -s'. '
echo ""

# Step 4: Corrupt first replica only (for testing corruption detection)
CHUNK_FILE=$(echo "$CHUNK_FILES" | head -1)
PORT=$(echo "$CHUNK_FILE" | sed 's|/tmp/chunk-server-||' | sed 's|/.*||')

echo -e "${YELLOW}Corrupting first replica only (port $PORT):${NC}"
echo "  $CHUNK_FILE"
echo ""

# Get file size
if [[ "$OSTYPE" == "darwin"* ]]; then
    FILE_SIZE=$(stat -f%z "$CHUNK_FILE" 2>/dev/null)
else
    FILE_SIZE=$(stat -c%s "$CHUNK_FILE" 2>/dev/null)
fi

echo "Chunk file size: $FILE_SIZE bytes"
echo "Checksum header: 160 bytes (first 160 bytes)"
echo "Data section: bytes 160-$FILE_SIZE"
echo ""

# Corrupt byte 200 (in the data section, slice 1)
# Slices: 160-8351 (slice 1), 8352-16543 (slice 2), etc.
CORRUPT_OFFSET=200
CORRUPT_VALUE='\xFF'

echo "Corrupting byte at offset $CORRUPT_OFFSET (slice 1) with value $CORRUPT_VALUE"
printf "$CORRUPT_VALUE" | dd of="$CHUNK_FILE" bs=1 seek=$CORRUPT_OFFSET count=1 conv=notrunc 2>/dev/null

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Chunk corrupted successfully${NC}"
    echo ""
    echo -e "${BLUE}Replica Status:${NC}"
    echo "  Port $PORT: CORRUPTED (slice 1)"

    # Show other replicas
    OTHER_REPLICAS=$(echo "$CHUNK_FILES" | tail -n +2)
    if [ ! -z "$OTHER_REPLICAS" ]; then
        echo "$OTHER_REPLICAS" | while read replica; do
            other_port=$(echo "$replica" | sed 's|/tmp/chunk-server-||' | sed 's|/.*||')
            echo "  Port $other_port: OK (valid copy)"
        done
    fi

    echo ""
    echo "========================================="
    echo "  Next Steps"
    echo "========================================="
    echo "1. Start client:"
    echo "   ./gradlew runReplicationClient -PappArgs=\"localhost 8000\" --console=plain"
    echo ""
    echo "2. Download the file multiple times:"
    echo "   > download $DFS_FILE /tmp/corrupted-download.txt"
    echo ""
    echo "3. Expected behavior:"
    echo "   - ${GREEN}33% chance${NC}: Download succeeds from valid replica"
    echo "   - ${RED}33% chance${NC}: Corruption detected:"
    echo "     ${YELLOW}REMLP03210.local:$PORT 1 1 is corrupted${NC}"
    echo ""
    echo "4. Keep downloading until you hit the corrupted replica!"
    echo ""
else
    echo -e "${RED}✗ Failed to corrupt chunk${NC}"
    exit 1
fi
