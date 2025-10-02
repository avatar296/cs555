#!/usr/bin/env bash
# cleanup.sh - Clean up all HW3 Pastry processes and files
set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}CS555 HW3 - Cleanup Script${NC}"
echo "==========================="
echo ""

# Kill Peer nodes
echo "Stopping Peer nodes..."
if pgrep -f "csx55.pastry.node.Peer" > /dev/null; then
    pkill -f "csx55.pastry.node.Peer"
    echo -e "${GREEN}✓ Peer nodes stopped${NC}"
else
    echo "  No Peer nodes running"
fi

# Kill Discovery node
echo "Stopping Discovery node..."
if pgrep -f "csx55.pastry.node.Discover" > /dev/null; then
    pkill -f "csx55.pastry.node.Discover"
    echo -e "${GREEN}✓ Discovery node stopped${NC}"
else
    echo "  No Discovery node running"
fi

# Kill Data processes
echo "Stopping Data processes..."
if pgrep -f "csx55.pastry.node.Data" > /dev/null; then
    pkill -f "csx55.pastry.node.Data"
    echo -e "${GREEN}✓ Data processes stopped${NC}"
else
    echo "  No Data processes running"
fi

# Clean logs if requested
if [ "${1:-}" = "--logs" ]; then
    echo ""
    echo "Cleaning log files..."
    rm -f discover.log peer_*.log data_*.log 2>/dev/null
    echo -e "${GREEN}✓ Log files removed${NC}"
fi

# Clean /tmp peer directories if requested
if [ "${1:-}" = "--all" ]; then
    echo ""
    echo "Cleaning /tmp peer directories..."
    # Find and remove directories that match 4-character hex pattern
    find /tmp -maxdepth 1 -type d -name '[0-9a-f][0-9a-f][0-9a-f][0-9a-f]' -exec rm -rf {} \; 2>/dev/null || true
    echo -e "${GREEN}✓ /tmp peer directories removed${NC}"

    echo "Cleaning log files..."
    rm -f discover.log peer_*.log data_*.log 2>/dev/null
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
echo "Options:"
echo "  ./cleanup.sh         - Stop all processes only"
echo "  ./cleanup.sh --logs  - Stop processes and remove logs"
echo "  ./cleanup.sh --all   - Stop processes, remove logs and /tmp dirs"
