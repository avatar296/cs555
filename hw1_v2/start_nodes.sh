#!/usr/bin/env bash
# start_nodes.sh - Start messaging nodes
set -euo pipefail

NODES=${1:-10}
REG_HOST=${2:-127.0.0.1}
REG_PORT=${3:-5555}

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}Starting $NODES Messaging Nodes${NC}"
echo "================================"
echo "Registry: $REG_HOST:$REG_PORT"
echo ""

# Check if Registry is running
if ! nc -z $REG_HOST $REG_PORT 2>/dev/null; then
    echo -e "${RED}Error: Registry is not running on $REG_HOST:$REG_PORT${NC}"
    echo "Please start the Registry first with: ./start_registry.sh"
    exit 1
fi

# Clean old logs
rm -f node_*.log

# Build if needed
if [ ! -d "build/classes/java/main" ]; then
    echo -e "${YELLOW}Building project...${NC}"
    ./gradlew build --quiet || exit 1
fi

CLASSPATH="build/classes/java/main"

# Start nodes
for i in $(seq 1 "$NODES"); do
    java -cp "$CLASSPATH" csx55.overlay.node.MessagingNode $REG_HOST $REG_PORT &> "node_${i}.log" &
    PID=$!
    echo "Started node $i (PID $PID)"
    sleep 0.1
done

echo ""
echo -e "${GREEN}All $NODES nodes started successfully!${NC}"
echo ""
echo "Nodes are running in background. Logs are in node_*.log files."
echo "To stop all nodes: pkill -f MessagingNode"
echo ""
echo "Now go to the Registry terminal and run:"
echo "  list-messaging-nodes"