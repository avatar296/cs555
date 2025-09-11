#!/usr/bin/env bash
# cleanup.sh - Clean up all overlay processes and logs

echo "Cleaning up CS555 HW1 processes..."

# Kill all Registry and MessagingNode processes
echo "Stopping all overlay processes..."
pkill -f "csx55.overlay.node.Registry" 2>/dev/null
pkill -f "csx55.overlay.node.MessagingNode" 2>/dev/null
pkill -f "runRegistry" 2>/dev/null
pkill -f "runMessagingNode" 2>/dev/null

# Give processes time to die
sleep 1

# Check if any are still running
remaining=$(ps aux | grep -E "csx55.overlay.node" | grep -v grep | wc -l)
if [ "$remaining" -gt 0 ]; then
    echo "Force killing remaining processes..."
    pkill -9 -f "csx55.overlay.node" 2>/dev/null
fi

# Clean up logs
echo "Removing log files..."
rm -f registry.log node_*.log table.txt 2>/dev/null

# Clean up any named pipes
rm -f /tmp/registry_pipe_* 2>/dev/null

echo "Cleanup complete!"

# Check ports
echo ""
echo "Checking common ports:"
for port in 5555 5556 6000 6001; do
    if lsof -i :$port 2>/dev/null | grep -q LISTEN; then
        echo "  Port $port is still in use"
    else
        echo "  Port $port is free"
    fi
done