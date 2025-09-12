#!/usr/bin/env bash
# cleanup.sh - Clean up all HW2 processes and logs
set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}CS555 HW2 - Cleanup Script${NC}"
echo "==========================="
echo ""

# Kill ComputeNodes
echo "Stopping ComputeNodes..."
if pgrep -f "csx55.threads.ComputeNode" > /dev/null; then
    pkill -f "csx55.threads.ComputeNode"
    echo -e "${GREEN}✓ ComputeNodes stopped${NC}"
else
    echo "  No ComputeNodes running"
fi

# Kill Registry
echo "Stopping Registry..."
if pgrep -f "csx55.threads.Registry" > /dev/null; then
    pkill -f "csx55.threads.Registry"
    echo -e "${GREEN}✓ Registry stopped${NC}"
else
    echo "  No Registry running"
fi

# Clean logs if requested
if [ "${1:-}" = "--logs" ]; then
    echo ""
    echo "Cleaning log files..."
    rm -f registry.log
    rm -f compute_node_*.log
    echo -e "${GREEN}✓ Log files removed${NC}"
fi

# Check if port 5555 is still in use
sleep 1
if lsof -i :5555 2>/dev/null | grep -q LISTEN; then
    echo ""
    echo -e "${RED}Warning: Port 5555 is still in use${NC}"
    echo "You may need to manually kill the process:"
    echo "  lsof -i :5555"
else
    echo ""
    echo -e "${GREEN}✓ Port 5555 is free${NC}"
fi

echo ""
echo -e "${GREEN}Cleanup complete!${NC}"
echo ""
echo "To also remove log files, run: ./cleanup.sh --logs"