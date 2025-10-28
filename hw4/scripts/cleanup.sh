#!/bin/bash
#
# Cleanup script for DFS testing
# Kills all DFS processes and removes test data
#

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
# Navigate to project root (parent of scripts directory)
cd "$SCRIPT_DIR/.."

echo "Cleaning up DFS..."

# Kill all Java processes for this project
pkill -f "csx55.dfs" 2>/dev/null || true

# Remove chunk server storage
rm -rf /tmp/chunk-server

# Remove test files
rm -f /tmp/test*.txt

echo "✓ Cleanup complete"
