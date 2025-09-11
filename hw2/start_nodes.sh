#!/usr/bin/env bash
# start_nodes.sh - Start ComputeNodes for HW2 Thread Pool
set -euo pipefail

NODES=${1:-3}
REG_HOST=${2:-localhost}
REG_PORT=${3:-5555}

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}Starting $NODES Compute Nodes${NC}"
echo "=============================="
echo "Registry: $REG_HOST:$REG_PORT"
echo ""

# Check if Registry is running
if ! nc -z $REG_HOST $REG_PORT 2>/dev/null; then
    echo -e "${RED}Error: Registry is not running on $REG_HOST:$REG_PORT${NC}"
    echo "Please start the Registry first with: ./start_registry.sh"
    exit 1
fi

# Clean old logs
rm -f compute_node_*.log

# Build if needed
if [ ! -d "build/classes/java/main" ]; then
    echo -e "${YELLOW}Building project...${NC}"
    ./gradlew build --quiet || exit 1
fi

CLASSPATH="build/classes/java/main"

# Optional: Set system properties for configuration
JAVA_OPTS=""
# Uncomment to customize:
# JAVA_OPTS="-Dcs555.printTasks=true"
# JAVA_OPTS="$JAVA_OPTS -Dcs555.difficultyBits=17"
# JAVA_OPTS="$JAVA_OPTS -Dcs555.pushThreshold=20"
# JAVA_OPTS="$JAVA_OPTS -Dcs555.pullThreshold=5"

# Start nodes
for i in $(seq 1 "$NODES"); do
    java $JAVA_OPTS -cp "$CLASSPATH" csx55.threads.ComputeNode $REG_HOST $REG_PORT &> "compute_node_${i}.log" &
    PID=$!
    echo "Started ComputeNode $i (PID $PID)"
    sleep 0.2
done

echo ""
echo -e "${GREEN}All $NODES compute nodes started successfully!${NC}"
echo ""
echo "Nodes are running in background. Logs are in compute_node_*.log files."
echo "To stop all nodes: pkill -f ComputeNode"
echo ""
echo "Now go to the Registry terminal and run:"
echo "  setup-overlay <thread-pool-size>"
echo "  start <number-of-rounds>"