#!/usr/bin/env bash
# start_discover.sh - Start the Discovery Node for HW3 Pastry DHT
set -euo pipefail

PORT=${1:-5555}

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}CS555 HW3 - Pastry Discovery Node${NC}"
echo "===================================="

# Check port
if lsof -i :$PORT 2>/dev/null | grep -q LISTEN; then
    echo -e "${RED}Error: Port $PORT is already in use!${NC}"
    echo "Run ./cleanup.sh to kill existing processes"
    exit 1
fi

# Build
echo -e "${YELLOW}Building project...${NC}"
./gradlew build --quiet || exit 1

# Start Discovery Node
echo -e "${GREEN}Starting Discovery Node on port $PORT${NC}"
echo ""
echo -e "${YELLOW}Available commands:${NC}"
echo "  list-nodes"
echo "  exit"
echo ""
echo "========================================="
echo ""

java -cp build/classes/java/main csx55.pastry.node.Discover $PORT 2>&1 | tee discover.log
