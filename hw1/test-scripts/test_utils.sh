#!/bin/bash

#############################################
# Utility Functions for Test Scripts
#############################################

# Process tracking
REGISTRY_PID=""
NODE_PIDS=()
REGISTRY_OUT="registry_test.out"
NODE_OUT_PREFIX="node_test"

# Start Registry
start_registry() {
    local port=$1
    java -cp build/classes/java/main csx55.overlay.node.Registry $port > "$REGISTRY_OUT" 2>&1 &
    REGISTRY_PID=$!
    sleep 3
    
    if ps -p $REGISTRY_PID > /dev/null; then
        echo "Registry started (PID: $REGISTRY_PID)"
        return 0
    else
        echo "Failed to start Registry"
        return 1
    fi
}

# Start MessagingNode
start_messaging_node() {
    local host=$1
    local port=$2
    local node_id=$3
    
    java -cp build/classes/java/main csx55.overlay.node.MessagingNode $host $port > "${NODE_OUT_PREFIX}_${node_id}.out" 2>&1 &
    local pid=$!
    NODE_PIDS+=($pid)
    
    if ps -p $pid > /dev/null; then
        echo "Node $node_id started (PID: $pid)"
        return 0
    else
        echo "Failed to start Node $node_id"
        return 1
    fi
}

# Send command to Registry stdin
send_registry_cmd() {
    local cmd=$1
    if [ ! -z "$REGISTRY_PID" ] && ps -p $REGISTRY_PID > /dev/null; then
        echo "$cmd" >> registry_input.pipe 2>/dev/null || {
            # Alternative: write to a file that Registry reads
            echo "$cmd" > registry_cmd.txt
        }
    fi
}

# Send command to Node stdin
send_node_cmd() {
    local node_id=$1
    local cmd=$2
    
    if [ $node_id -lt ${#NODE_PIDS[@]} ]; then
        local pid=${NODE_PIDS[$node_id]}
        if ps -p $pid > /dev/null; then
            echo "$cmd" >> "node_${node_id}_input.pipe" 2>/dev/null
        fi
    fi
}

# Get Registry output
get_registry_output() {
    if [ -f "$REGISTRY_OUT" ]; then
        cat "$REGISTRY_OUT"
    fi
}

# Get Node output
get_node_output() {
    local node_id=$1
    local file="${NODE_OUT_PREFIX}_${node_id}.out"
    if [ -f "$file" ]; then
        cat "$file"
    fi
}

# Check if Registry output contains string
check_registry_output() {
    local expected=$1
    get_registry_output | grep -q "$expected"
    return $?
}

# Check if Node output contains string
check_node_output() {
    local node_id=$1
    local expected=$2
    get_node_output $node_id | grep -q "$expected"
    return $?
}

# Wait for output with timeout
wait_for_output() {
    local file=$1
    local expected=$2
    local timeout=$3
    
    local elapsed=0
    while [ $elapsed -lt $timeout ]; do
        if grep -q "$expected" "$file" 2>/dev/null; then
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    return 1
}

# Cleanup all processes
cleanup_all() {
    echo "Cleaning up processes..."
    
    # Kill Registry
    if [ ! -z "$REGISTRY_PID" ] && ps -p $REGISTRY_PID > /dev/null; then
        kill $REGISTRY_PID 2>/dev/null
        wait $REGISTRY_PID 2>/dev/null
    fi
    
    # Kill all nodes
    for pid in "${NODE_PIDS[@]}"; do
        if ps -p $pid > /dev/null; then
            kill $pid 2>/dev/null
            wait $pid 2>/dev/null
        fi
    done
    
    # Clean up pipes and temp files
    rm -f *.pipe registry_cmd.txt
    
    echo "Cleanup complete"
}

# Trap to ensure cleanup on exit
trap cleanup_all EXIT INT TERM

# Create named pipes for input
create_pipes() {
    mkfifo registry_input.pipe 2>/dev/null
    for ((e=0; e<20; e++)); do
        mkfifo "node_${e}_input.pipe" 2>/dev/null
    done
}

# Extract statistics from traffic summary
parse_traffic_summary() {
    local output=$1
    
    # Extract sum line
    local sum_line=$(echo "$output" | grep "^sum " | tail -1)
    
    if [ ! -z "$sum_line" ]; then
        local sent=$(echo $sum_line | awk '{print $2}')
        local received=$(echo $sum_line | awk '{print $3}')
        local sum_sent=$(echo $sum_line | awk '{print $4}')
        local sum_received=$(echo $sum_line | awk '{print $5}')
        
        echo "Sent: $sent, Received: $received"
        echo "Sum Sent: $sum_sent, Sum Received: $sum_received"
        
        # Return 0 if sent equals received
        if [ "$sent" -eq "$received" ]; then
            return 0
        else
            return 1
        fi
    else
        echo "No traffic summary found"
        return 1
    fi
}

# Validate MST properties
validate_mst() {
    local output=$1
    local expected_nodes=$2
    
    # Count edges
    local edge_count=$(echo "$output" | grep -c "^[0-9.]*:[0-9]*, [0-9.]*:[0-9]*, [0-9]$")
    local expected_edges=$((expected_nodes - 1))
    
    if [ "$edge_count" -eq "$expected_edges" ]; then
        echo "MST valid: $edge_count edges for $expected_nodes nodes"
        return 0
    else
        echo "MST invalid: $edge_count edges (expected $expected_edges)"
        return 1
    fi
}

# Initialize test environment
init_test() {
    echo "Initializing test environment..."
    create_pipes
    
    # Clean any existing Java processes
    pkill -f "csx55.overlay.node" 2>/dev/null
    sleep 2
    
    # Clear output files
    rm -f "$REGISTRY_OUT" ${NODE_OUT_PREFIX}_*.out
    
    echo "Test environment ready"
}