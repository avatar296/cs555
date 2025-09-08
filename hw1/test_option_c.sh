#!/bin/bash

# Option C Full-Stack Loop Test (Native - No Docker)
# Tests determinism of overlay construction and MST calculation

set -e  # Exit on error

echo "============================================================"
echo "    OPTION C - FULL STACK DETERMINISM TEST"
echo "============================================================"

# Configuration
RUNS=${RUNS:-3}            # Number of test runs
NODES=${NODES:-10}         # Number of messaging nodes
CR=${CR:-4}                 # Connection requirement
BASE_PORT=5000             # Base port for registry
JAVA_CP="build/classes/java/main"  # Classpath for Java
TEST_DIR="test_option_c_$(date +%Y%m%d_%H%M%S)"

# Create test directory
mkdir -p "$TEST_DIR"
echo "Test outputs will be saved to: $TEST_DIR"
echo ""

# Arrays to store process IDs
declare -a NODE_PIDS
declare -a NODE_PIPES
REG_PID=""
REG_PIPE=""

# Cleanup function
cleanup() {
    echo "Cleaning up processes..."
    
    # Kill all node processes
    for pid in "${NODE_PIDS[@]}"; do
        if [[ -n "$pid" ]]; then
            kill "$pid" 2>/dev/null || true
        fi
    done
    
    # Kill registry
    if [[ -n "$REG_PID" ]]; then
        kill "$REG_PID" 2>/dev/null || true
    fi
    
    # Clean up named pipes
    for pipe in "${NODE_PIPES[@]}"; do
        if [[ -e "$pipe" ]]; then
            rm -f "$pipe"
        fi
    done
    
    if [[ -e "$REG_PIPE" ]]; then
        rm -f "$REG_PIPE"
    fi
    
    # Kill any remaining Java processes
    pkill -f "csx55.overlay.node.Registry" 2>/dev/null || true
    pkill -f "csx55.overlay.node.MessagingNode" 2>/dev/null || true
}

# Set trap for cleanup
trap cleanup EXIT INT TERM

# Function to start registry
start_registry() {
    local run=$1
    local port=$((BASE_PORT + run))
    local log_file="$TEST_DIR/run_${run}_registry.log"
    
    echo "Starting Registry on port $port..."
    
    # Create named pipe for sending commands
    REG_PIPE="$TEST_DIR/reg_pipe_${run}"
    mkfifo "$REG_PIPE"
    
    # Start registry with input from pipe
    java -cp "$JAVA_CP" csx55.overlay.node.Registry "$port" < "$REG_PIPE" > "$log_file" 2>&1 &
    REG_PID=$!
    
    # Keep pipe open
    exec 3>"$REG_PIPE"
    
    sleep 2  # Wait for registry to start
    echo "Registry started (PID: $REG_PID)"
}

# Function to send command to registry
send_to_registry() {
    local cmd=$1
    echo "  [Registry] << $cmd"
    echo "$cmd" >&3
    sleep 1
}

# Function to start messaging nodes
start_nodes() {
    local run=$1
    local port=$((BASE_PORT + run))
    
    echo "Starting $NODES MessagingNodes..."
    NODE_PIDS=()
    NODE_PIPES=()
    
    for i in $(seq 1 "$NODES"); do
        local log_file="$TEST_DIR/run_${run}_node_${i}.log"
        local pipe="$TEST_DIR/node_pipe_${run}_${i}"
        
        # Create named pipe
        mkfifo "$pipe"
        NODE_PIPES+=("$pipe")
        
        # Start node with input from pipe
        java -cp "$JAVA_CP" csx55.overlay.node.MessagingNode localhost "$port" < "$pipe" > "$log_file" 2>&1 &
        NODE_PIDS+=($!)
        
        # Keep pipe open
        exec 4>"$pipe"
        
        echo -n "."
    done
    
    echo " done!"
    sleep 5  # Wait for all nodes to register
    echo "All nodes started"
}

# Function to send command to a specific node
send_to_node() {
    local node_idx=$1
    local cmd=$2
    local pipe="${NODE_PIPES[$((node_idx-1))]}"
    
    echo "  [Node $node_idx] << $cmd"
    echo "$cmd" > "$pipe"
    sleep 1
}

# Function to send command to all nodes
send_to_all_nodes() {
    local cmd=$1
    echo "Sending to all nodes: $cmd"
    
    for i in $(seq 1 "$NODES"); do
        send_to_node "$i" "$cmd"
    done
}

# Function to extract edges from log file
extract_edges() {
    local log_file=$1
    local output_file=$2
    
    # Extract lines that look like: "IP:port, IP:port, weight"
    grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+, [0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+, [0-9]+$' \
        "$log_file" 2>/dev/null | sort > "$output_file" || touch "$output_file"
}

# Function to normalize edges by stripping ports and sorting
normalize_edges() {
    # Strip ports, sort node pairs, sort all edges
    awk -F', ' '
        function strip_port(s){ sub(/:[0-9]+$/, "", s); return s }
        {
            a = strip_port($1); b = strip_port($2); w = $3
            if (a <= b) printf "%s,%s,%s\n", a, b, w
            else         printf "%s,%s,%s\n", b, a, w
        }
    ' "$1" | sort
}

# Function to normalize a file in place
normalize_file_inplace() {
    local infile="$1"
    local tmp="$(mktemp)"
    normalize_edges "$infile" > "$tmp" && mv "$tmp" "$infile"
}

# Function to run a single test iteration
run_test() {
    local run=$1
    
    echo ""
    echo "============================================================"
    echo "                    RUN $run of $RUNS"
    echo "============================================================"
    
    # Start registry and nodes
    start_registry "$run"
    start_nodes "$run"
    
    # Run registry commands
    echo ""
    echo "Executing registry commands..."
    send_to_registry "list-messaging-nodes"
    sleep 2
    
    send_to_registry "setup-overlay $CR"
    sleep 3
    
    send_to_registry "send-overlay-link-weights"
    sleep 3
    
    send_to_registry "list-weights"
    sleep 2
    
    # Extract list-weights output
    extract_edges "$TEST_DIR/run_${run}_registry.log" "$TEST_DIR/run_${run}_weights.txt"
    normalize_file_inplace "$TEST_DIR/run_${run}_weights.txt"
    
    # Run print-mst on selected nodes
    echo ""
    echo "Running print-mst on nodes..."
    for node in 1 3 5 7 10; do
        if [[ $node -le $NODES ]]; then
            send_to_node "$node" "print-mst"
            sleep 2
            
            # Extract MST edges
            extract_edges "$TEST_DIR/run_${run}_node_${node}.log" \
                "$TEST_DIR/run_${run}_mst_node_${node}.txt"
            normalize_file_inplace "$TEST_DIR/run_${run}_mst_node_${node}.txt"
        fi
    done
    
    # Cleanup for this run
    echo "Cleaning up run $run..."
    cleanup
    
    # Reset cleanup variables
    NODE_PIDS=()
    NODE_PIPES=()
    REG_PID=""
    REG_PIPE=""
    
    sleep 2  # Wait before next run
}

# Main test execution
echo "Configuration:"
echo "  Runs: $RUNS"
echo "  Nodes: $NODES"
echo "  Connection Requirement: $CR"
echo "  Test Directory: $TEST_DIR"
echo ""

# Build the project
echo "Building project..."
./gradlew build -x test > /dev/null 2>&1
echo "Build complete"

# Run tests
for run in $(seq 1 "$RUNS"); do
    run_test "$run"
done

# Compare outputs
echo ""
echo "============================================================"
echo "                 COMPARING OUTPUTS"
echo "============================================================"

# Compare list-weights
echo ""
echo "=== Comparing list-weights across runs ==="
WEIGHTS_OK=true
for ((i=2; i<=RUNS; i++)); do
    echo -n "  Run 1 vs Run $i: "
    if diff -q "$TEST_DIR/run_1_weights.txt" "$TEST_DIR/run_${i}_weights.txt" > /dev/null 2>&1; then
        echo "✓ IDENTICAL"
    else
        echo "✗ DIFFERENT"
        WEIGHTS_OK=false
        echo "    Differences:"
        diff "$TEST_DIR/run_1_weights.txt" "$TEST_DIR/run_${i}_weights.txt" | head -10
    fi
done

# Compare MST outputs
echo ""
echo "=== Comparing print-mst across runs ==="
MST_OK=true
for node in 1 3 5 7 10; do
    if [[ $node -le $NODES ]]; then
        echo ""
        echo "Node $node MST:"
        for ((i=2; i<=RUNS; i++)); do
            echo -n "  Run 1 vs Run $i: "
            if diff -q "$TEST_DIR/run_1_mst_node_${node}.txt" \
                    "$TEST_DIR/run_${i}_mst_node_${node}.txt" > /dev/null 2>&1; then
                echo "✓ IDENTICAL"
            else
                echo "✗ DIFFERENT"
                MST_OK=false
                echo "    Differences:"
                diff "$TEST_DIR/run_1_mst_node_${node}.txt" \
                    "$TEST_DIR/run_${i}_mst_node_${node}.txt" | head -10
            fi
        done
    fi
done

# Count edges and weights
echo ""
echo "=== Edge counts and total weights ==="
for run in $(seq 1 "$RUNS"); do
    echo ""
    echo "Run $run:"
    
    # Count overlay edges
    if [[ -f "$TEST_DIR/run_${run}_weights.txt" ]]; then
        edge_count=$(wc -l < "$TEST_DIR/run_${run}_weights.txt")
        total_weight=$(awk -F', ' '{sum += $3} END {print sum}' "$TEST_DIR/run_${run}_weights.txt" 2>/dev/null || echo "0")
        echo "  Overlay edges: $edge_count, Total weight: $total_weight"
    fi
    
    # Count MST edges for each node
    for node in 1 3 5 7 10; do
        if [[ $node -le $NODES ]] && [[ -f "$TEST_DIR/run_${run}_mst_node_${node}.txt" ]]; then
            mst_edges=$(wc -l < "$TEST_DIR/run_${run}_mst_node_${node}.txt")
            mst_weight=$(awk -F', ' '{sum += $3} END {print sum}' "$TEST_DIR/run_${run}_mst_node_${node}.txt" 2>/dev/null || echo "0")
            echo "  Node $node MST: edges=$mst_edges, weight=$mst_weight"
        fi
    done
done

# Final summary
echo ""
echo "============================================================"
echo "                    TEST SUMMARY"
echo "============================================================"

if [[ "$WEIGHTS_OK" == "true" ]] && [[ "$MST_OK" == "true" ]]; then
    echo "✓✓✓ SUCCESS: All outputs are deterministic!"
    echo ""
    echo "  ✓ list-weights identical across all $RUNS runs"
    echo "  ✓ print-mst identical for all tested nodes"
    echo ""
    echo "Your implementation is deterministic and ready for submission!"
else
    echo "✗✗✗ FAILURE: Non-deterministic behavior detected!"
    echo ""
    if [[ "$WEIGHTS_OK" == "false" ]]; then
        echo "  ✗ list-weights varies across runs"
    fi
    if [[ "$MST_OK" == "false" ]]; then
        echo "  ✗ print-mst varies across runs"
    fi
    echo ""
    echo "Review the output files in: $TEST_DIR"
fi

echo "============================================================"