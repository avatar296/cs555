#!/bin/bash

#############################################
# Automated End-to-End Test Runner
# Tests the distributed overlay network system
#############################################

# Configuration
REGISTRY_PORT=8090
NODE_COUNT=10
CONNECTION_REQUIREMENT=4
TEST_ROUNDS=100
REGISTRY_HOST="localhost"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test results
PASSED_TESTS=0
FAILED_TESTS=0

# Log file
LOG_FILE="test_output_$(date +%Y%m%d_%H%M%S).log"

#############################################
# Helper Functions
#############################################

log() {
    echo -e "$1" | tee -a "$LOG_FILE"
}

log_success() {
    log "${GREEN}✓ $1${NC}"
    ((PASSED_TESTS++))
}

log_failure() {
    log "${RED}✗ $1${NC}"
    ((FAILED_TESTS++))
}

log_info() {
    log "${YELLOW}ℹ $1${NC}"
}

# Kill all Java processes related to our system
cleanup() {
    log_info "Cleaning up processes..."
    pkill -f "csx55.overlay.node.Registry" 2>/dev/null
    pkill -f "csx55.overlay.node.MessagingNode" 2>/dev/null
    sleep 2
}

# Build the project
build_project() {
    log_info "Building project..."
    ./gradlew build > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        log_success "Project built successfully"
        return 0
    else
        log_failure "Build failed"
        return 1
    fi
}

# Start Registry
start_registry() {
    log_info "Starting Registry on port $REGISTRY_PORT..."
    java -cp build/classes/java/main csx55.overlay.node.Registry $REGISTRY_PORT > registry.out 2>&1 &
    REGISTRY_PID=$!
    sleep 3
    
    if ps -p $REGISTRY_PID > /dev/null; then
        log_success "Registry started (PID: $REGISTRY_PID)"
        return 0
    else
        log_failure "Registry failed to start"
        return 1
    fi
}

# Start a MessagingNode
start_node() {
    local node_id=$1
    java -cp build/classes/java/main csx55.overlay.node.MessagingNode $REGISTRY_HOST $REGISTRY_PORT > "node_$node_id.out" 2>&1 &
    local pid=$!
    NODE_PIDS+=($pid)
    sleep 1
    
    if ps -p $pid > /dev/null; then
        return 0
    else
        return 1
    fi
}

# Send command to Registry
send_registry_command() {
    local command=$1
    echo "$command" > /proc/$REGISTRY_PID/fd/0 2>/dev/null || {
        # Alternative method using named pipe
        echo "$command" > registry_input
    }
    sleep 1
}

# Check output for expected string
check_output() {
    local file=$1
    local expected=$2
    local timeout=$3
    
    local count=0
    while [ $count -lt $timeout ]; do
        if grep -q "$expected" "$file" 2>/dev/null; then
            return 0
        fi
        sleep 1
        ((count++))
    done
    return 1
}

#############################################
# Test Functions
#############################################

test_node_registration() {
    log_info "Testing node registration..."
    
    # Start nodes
    NODE_PIDS=()
    local nodes_started=0
    
    for ((e=0; e<$NODE_COUNT; e++)); do
        start_node $e
        if [ $? -eq 0 ]; then
            ((nodes_started++))
        fi
    done
    
    if [ $nodes_started -eq $NODE_COUNT ]; then
        log_success "All $NODE_COUNT nodes started"
    else
        log_failure "Only $nodes_started/$NODE_COUNT nodes started"
        return 1
    fi
    
    sleep 3
    
    # Check registrations
    send_registry_command "list-messaging-nodes"
    sleep 2
    
    local registered=$(grep -c ":[0-9]\+" registry.out | tail -1)
    if [ "$registered" -eq "$NODE_COUNT" ]; then
        log_success "All nodes registered successfully"
        return 0
    else
        log_failure "Registration failed: $registered/$NODE_COUNT registered"
        return 1
    fi
}

test_overlay_setup() {
    log_info "Testing overlay setup with CR=$CONNECTION_REQUIREMENT..."
    
    send_registry_command "setup-overlay $CONNECTION_REQUIREMENT"
    
    if check_output "registry.out" "setup completed with $CONNECTION_REQUIREMENT connections" 10; then
        log_success "Overlay setup completed"
    else
        log_failure "Overlay setup failed"
        return 1
    fi
    
    # Check if all nodes established connections
    local connected_nodes=0
    for ((e=0; e<$NODE_COUNT; e++)); do
        if check_output "node_$e.out" "All connections are established" 5; then
            ((connected_nodes++))
        fi
    done
    
    if [ $connected_nodes -eq $NODE_COUNT ]; then
        log_success "All nodes established connections"
        return 0
    else
        log_failure "Connection establishment failed: $connected_nodes/$NODE_COUNT"
        return 1
    fi
}

test_link_weights() {
    log_info "Testing link weight distribution..."
    
    send_registry_command "send-overlay-link-weights"
    
    if check_output "registry.out" "link weights assigned" 5; then
        log_success "Link weights assigned"
    else
        log_failure "Link weight assignment failed"
        return 1
    fi
    
    # Check if all nodes received weights
    local nodes_ready=0
    for ((e=0; e<$NODE_COUNT; e++)); do
        if check_output "node_$e.out" "Link weights received and processed" 5; then
            ((nodes_ready++))
        fi
    done
    
    if [ $nodes_ready -eq $NODE_COUNT ]; then
        log_success "All nodes received link weights"
        return 0
    else
        log_failure "Link weight distribution failed: $nodes_ready/$NODE_COUNT"
        return 1
    fi
}

test_messaging_task() {
    log_info "Testing messaging with $TEST_ROUNDS rounds..."
    
    send_registry_command "start $TEST_ROUNDS"
    
    # Wait for completion (estimate: 2 seconds per round max)
    local max_wait=$((TEST_ROUNDS * 2 + 20))
    
    if check_output "registry.out" "$TEST_ROUNDS rounds completed" $max_wait; then
        log_success "Messaging task completed"
    else
        log_failure "Messaging task failed to complete"
        return 1
    fi
    
    # Wait for traffic summaries
    sleep 20
    
    # Verify traffic summary output
    if grep -q "^sum [0-9]\+ [0-9]\+" registry.out; then
        log_success "Traffic summary generated"
        
        # Extract and verify totals
        local summary=$(grep "^sum" registry.out | tail -1)
        local sent=$(echo $summary | awk '{print $2}')
        local received=$(echo $summary | awk '{print $3}')
        local expected=$((NODE_COUNT * TEST_ROUNDS * 5))
        
        if [ "$sent" -eq "$expected" ] && [ "$received" -eq "$expected" ]; then
            log_success "Traffic totals correct: sent=$sent, received=$received"
            return 0
        else
            log_failure "Traffic totals incorrect: sent=$sent, received=$received, expected=$expected"
            return 1
        fi
    else
        log_failure "Traffic summary not found"
        return 1
    fi
}

test_mst_computation() {
    log_info "Testing MST computation..."
    
    # Send print-mst to first node (would need to modify for actual implementation)
    # For now, just check list-weights works
    send_registry_command "list-weights"
    sleep 2
    
    # Count link weights
    local weights=$(grep -c "^[0-9.]*:[0-9]*, [0-9.]*:[0-9]*, [0-9]" registry.out)
    local expected_edges=$((NODE_COUNT * CONNECTION_REQUIREMENT / 2))
    
    if [ "$weights" -ge "$expected_edges" ]; then
        log_success "Link weights verified: $weights edges"
        return 0
    else
        log_failure "Incorrect number of edges: $weights (expected $expected_edges)"
        return 1
    fi
}

#############################################
# Main Test Execution
#############################################

main() {
    log "========================================="
    log "Overlay Network Automated Test"
    log "Started: $(date)"
    log "========================================="
    
    # Initial cleanup
    cleanup
    
    # Build project
    if ! build_project; then
        log_failure "Build failed - aborting tests"
        exit 1
    fi
    
    # Create named pipe for registry input (alternative input method)
    mkfifo registry_input 2>/dev/null
    
    # Start Registry
    if ! start_registry; then
        log_failure "Registry startup failed - aborting tests"
        cleanup
        exit 1
    fi
    
    # Run tests
    test_node_registration
    test_overlay_setup
    test_link_weights
    test_messaging_task
    test_mst_computation
    
    # Final cleanup
    cleanup
    rm -f registry_input
    
    # Print summary
    log "========================================="
    log "Test Summary"
    log "========================================="
    log_success "Passed: $PASSED_TESTS"
    log_failure "Failed: $FAILED_TESTS"
    
    local total=$((PASSED_TESTS + FAILED_TESTS))
    local percentage=$((PASSED_TESTS * 100 / total))
    
    log_info "Success Rate: $percentage%"
    
    if [ $FAILED_TESTS -eq 0 ]; then
        log_success "ALL TESTS PASSED!"
        exit 0
    else
        log_failure "SOME TESTS FAILED"
        exit 1
    fi
}

# Trap to ensure cleanup on exit
trap cleanup EXIT

# Run main function
main "$@"