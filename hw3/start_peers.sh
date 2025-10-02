#!/usr/bin/env bash
# start_peers.sh - Start Peer nodes for HW3 Pastry DHT
set -euo pipefail

NUM_PEERS=${1:-5}
DISCOVER_HOST=${2:-127.0.0.1}
DISCOVER_PORT=${3:-5555}

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}CS555 HW3 - Starting Pastry Peer Nodes${NC}"
echo "========================================"
echo "Peers: $NUM_PEERS"
echo "Discovery: $DISCOVER_HOST:$DISCOVER_PORT"
echo ""

# Check if Discovery is running
if ! nc -z $DISCOVER_HOST $DISCOVER_PORT 2>/dev/null; then
    echo -e "${RED}Error: Discovery node is not running on $DISCOVER_HOST:$DISCOVER_PORT${NC}"
    echo "Please start the Discovery node first with: ./start_discover.sh"
    exit 1
fi

# Clean old logs
rm -f peer_*.log

# Build if needed
if [ ! -d "build/classes/java/main" ]; then
    echo -e "${YELLOW}Building project...${NC}"
    ./gradlew build --quiet || exit 1
fi

CLASSPATH="build/classes/java/main"

# Generate random hex IDs and start peers
echo "Starting peers..."
for i in $(seq 1 "$NUM_PEERS"); do
    # Generate random 16-bit hex ID (4 hex digits)
    HEX_ID=$(openssl rand -hex 2)

    # Create /tmp/<peer-id> directory for file storage
    mkdir -p "/tmp/${HEX_ID}"

    # Start peer in background
    java -cp "$CLASSPATH" csx55.pastry.node.Peer $DISCOVER_HOST $DISCOVER_PORT $HEX_ID &> "peer_${HEX_ID}.log" &
    PID=$!

    echo -e "  Started peer ${GREEN}${HEX_ID}${NC} (PID $PID) -> peer_${HEX_ID}.log"
    sleep 0.2
done

echo ""
echo -e "${GREEN}All $NUM_PEERS peers started successfully!${NC}"
echo ""
echo "Peer logs: peer_*.log"
echo "Storage dirs: /tmp/<peer-id>/"
echo ""
echo "To interact with a peer, attach to its process or check its log file"
echo "To stop all peers: ./cleanup.sh"
