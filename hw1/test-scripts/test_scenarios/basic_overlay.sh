#!/bin/bash

#############################################
# Basic Overlay Test Scenario
# Tests standard 10-node overlay with CR=4
#############################################

source "$(dirname "$0")/../test_utils.sh"

# Test configuration
REGISTRY_PORT=8080
NODE_COUNT=10
CR=4
TEST_ROUNDS=100

echo "========================================="
echo "Basic Overlay Test Scenario"
echo "Nodes: $NODE_COUNT, CR: $CR, Rounds: $TEST_ROUNDS"
echo "========================================="

# Step 1: Build and start system
echo "[Step 1] Building project..."
cd ../..
./gradlew build || exit 1

# Step 2: Start Registry
echo "[Step 2] Starting Registry on port $REGISTRY_PORT..."
start_registry $REGISTRY_PORT

# Step 3: Start MessagingNodes
echo "[Step 3] Starting $NODE_COUNT MessagingNodes..."
for ((e=0; e<$NODE_COUNT; e++)); do
    start_messaging_node localhost $REGISTRY_PORT $e
    sleep 0.5
done

sleep 3

# Step 4: Verify all nodes registered
echo "[Step 4] Verifying node registration..."
send_registry_cmd "list-messaging-nodes"
sleep 2

node_count=$(get_registry_output | grep -c ":[0-9]*$")
if [ "$node_count" -eq "$NODE_COUNT" ]; then
    echo "✓ All $NODE_COUNT nodes registered"
else
    echo "✗ Registration failed: $node_count/$NODE_COUNT nodes"
    cleanup_all
    exit 1
fi

# Step 5: Setup overlay
echo "[Step 5] Setting up overlay with CR=$CR..."
send_registry_cmd "setup-overlay $CR"
sleep 5

if check_registry_output "setup completed with $CR connections"; then
    echo "✓ Overlay setup completed"
else
    echo "✗ Overlay setup failed"
    cleanup_all
    exit 1
fi

# Step 6: Send link weights
echo "[Step 6] Sending link weights..."
send_registry_cmd "send-overlay-link-weights"
sleep 5

if check_registry_output "link weights assigned"; then
    echo "✓ Link weights assigned"
else
    echo "✗ Link weight assignment failed"
    cleanup_all
    exit 1
fi

# Step 7: Verify link weights
echo "[Step 7] Verifying link weights..."
send_registry_cmd "list-weights"
sleep 2

expected_edges=$((NODE_COUNT * CR / 2))
edge_count=$(get_registry_output | grep -c "^[0-9.]*:[0-9]*, [0-9.]*:[0-9]*, [0-9]$")

if [ "$edge_count" -eq "$expected_edges" ]; then
    echo "✓ Correct number of edges: $edge_count"
else
    echo "✗ Incorrect edges: $edge_count (expected $expected_edges)"
fi

# Step 8: Start messaging task
echo "[Step 8] Starting messaging task with $TEST_ROUNDS rounds..."
send_registry_cmd "start $TEST_ROUNDS"

# Wait for completion
echo "Waiting for task completion..."
max_wait=$((TEST_ROUNDS * 2 + 30))
elapsed=0

while [ $elapsed -lt $max_wait ]; do
    if check_registry_output "$TEST_ROUNDS rounds completed"; then
        echo "✓ Messaging task completed"
        break
    fi
    sleep 5
    elapsed=$((elapsed + 5))
    echo "  Waiting... ($elapsed/$max_wait seconds)"
done

# Wait for traffic summaries
echo "Waiting for traffic summaries..."
sleep 20

# Step 9: Verify traffic summary
echo "[Step 9] Verifying traffic summary..."
expected_messages=$((NODE_COUNT * TEST_ROUNDS * 5))

summary=$(get_registry_output | grep "^sum " | tail -1)
if [ ! -z "$summary" ]; then
    sent=$(echo $summary | awk '{print $2}')
    received=$(echo $summary | awk '{print $3}')
    
    if [ "$sent" -eq "$expected_messages" ] && [ "$received" -eq "$expected_messages" ]; then
        echo "✓ Traffic summary correct:"
        echo "  Sent: $sent"
        echo "  Received: $received"
        echo "  Expected: $expected_messages"
    else
        echo "✗ Traffic summary incorrect:"
        echo "  Sent: $sent"
        echo "  Received: $received"
        echo "  Expected: $expected_messages"
    fi
else
    echo "✗ No traffic summary found"
fi

# Step 10: Test MST at nodes
echo "[Step 10] Testing MST computation..."
send_node_cmd 0 "print-mst"
sleep 2

mst_edges=$(get_node_output 0 | grep -c "^[0-9.]*:[0-9]*, [0-9.]*:[0-9]*, [0-9]$")
expected_mst_edges=$((NODE_COUNT - 1))

if [ "$mst_edges" -eq "$expected_mst_edges" ]; then
    echo "✓ MST has correct number of edges: $mst_edges"
else
    echo "✗ MST edge count incorrect: $mst_edges (expected $expected_mst_edges)"
fi

# Cleanup
echo "========================================="
echo "Test completed. Cleaning up..."
cleanup_all

echo "Basic overlay test scenario complete!"