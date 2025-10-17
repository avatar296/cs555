#!/usr/bin/env bash
# test_autograder_scenario.sh - Test script for autograder node IDs
# Uses the exact 16 node IDs from the autograder test case
set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

DISCOVER_PORT=5555
DISCOVER_PID=""
PEER_PIDS=()
PEER_PIPES=()

# Test case selection (default to TC2)
TEST_CASE="${1:-2}"

# Autograder Test Case 2 Node IDs (exact order matters!)
TC2_IDS=(
    "03c0" "06fb" "1335" "1d05" "23e1" "46e2" "4ad7" "6e6a"
    "7332" "99b4" "9af1" "b5ad" "b715" "b869" "eae5" "ecdd"
)

# Test Case 3 Node IDs (Order-of-Operations test)
TC3_IDS=(
    "1000" "2000" "3000" "8000"
)

# Test Case 4 Node IDs (Sequential Exits test)
TC4_IDS=(
    "1000" "2000" "3000" "4000" "5000"
)

# Select which test case to run
if [ "$TEST_CASE" == "3" ]; then
    AUTOGRADER_IDS=("${TC3_IDS[@]}")
    TEST_NAME="Test Case 3: Order-of-Operations"
    FOCUS_NODE="1000"
elif [ "$TEST_CASE" == "4" ]; then
    AUTOGRADER_IDS=("${TC4_IDS[@]}")
    TEST_NAME="Test Case 4: Sequential Exits"
    FOCUS_NODE="1000"
else
    AUTOGRADER_IDS=("${TC2_IDS[@]}")
    TEST_NAME="Test Case 2: Ring Structure"
    FOCUS_NODE="9af1"
fi

NUM_PEERS=${#AUTOGRADER_IDS[@]}

# Cleanup on exit
cleanup() {
    echo ""
    echo -e "${YELLOW}Shutting down test network...${NC}"

    # Kill peers
    if [ ${#PEER_PIDS[@]} -gt 0 ]; then
        for pid in "${PEER_PIDS[@]}"; do
            kill "$pid" 2>/dev/null || true
        done
    fi

    # Kill discovery
    if [ -n "$DISCOVER_PID" ]; then
        kill "$DISCOVER_PID" 2>/dev/null || true
    fi

    # Remove named pipes
    for pipe in "${PEER_PIPES[@]}"; do
        rm -f "$pipe" 2>/dev/null || true
    done

    sleep 1
    echo -e "${GREEN}Test network stopped${NC}"
}

trap cleanup EXIT INT TERM

# Start Discovery node
start_discovery() {
    echo -e "${BLUE}Starting Discovery Node on port $DISCOVER_PORT...${NC}"

    # Check if port is in use
    if lsof -i :$DISCOVER_PORT 2>/dev/null | grep -q LISTEN; then
        echo -e "${RED}Error: Port $DISCOVER_PORT is already in use!${NC}"
        echo "Run ./cleanup.sh to kill existing processes"
        exit 1
    fi

    # Build project
    echo -e "${YELLOW}Building project...${NC}"
    ./gradlew build --quiet || exit 1

    # Start Discovery in background
    java -cp build/classes/java/main csx55.pastry.node.Discover $DISCOVER_PORT &> discover_autograder.log &
    DISCOVER_PID=$!

    # Wait for "Ready to accept" message in log (max 10 seconds)
    for i in {1..20}; do
        if grep -q "Ready to accept" discover_autograder.log 2>/dev/null; then
            break
        fi
        sleep 0.5
    done

    # Verify process is still running
    if kill -0 "$DISCOVER_PID" 2>/dev/null; then
        echo -e "${GREEN}✓ Discovery Node started (PID $DISCOVER_PID)${NC}"
        echo -e "  Log: discover_autograder.log"
    else
        echo -e "${RED}✗ Discovery Node failed to start${NC}"
        cat discover_autograder.log
        exit 1
    fi
}

# Start peer nodes with autograder IDs
start_peers() {
    echo ""
    echo -e "${BLUE}Starting $NUM_PEERS Autograder Peer Nodes...${NC}"
    echo -e "${CYAN}(Using exact autograder node IDs)${NC}"
    echo ""

    for i in "${!AUTOGRADER_IDS[@]}"; do
        local peer_id="${AUTOGRADER_IDS[$i]}"
        local log_file="peer_${peer_id}_autograder.log"
        local pipe_file="/tmp/peer_${peer_id}_pipe"

        # Create /tmp/<peer-id> directory
        mkdir -p "/tmp/${peer_id}"

        # Create named pipe for sending commands
        mkfifo "$pipe_file" 2>/dev/null || true
        PEER_PIPES+=("$pipe_file")

        # Keep pipe open in background to prevent EOF
        tail -f /dev/null > "$pipe_file" &
        local pipe_keeper_pid=$!

        # Start peer in background with pipe as stdin
        java -cp build/classes/java/main csx55.pastry.node.Peer localhost $DISCOVER_PORT "$peer_id" < "$pipe_file" &> "$log_file" &
        local pid=$!
        PEER_PIDS+=("$pid")
        PEER_PIDS+=("$pipe_keeper_pid")

        sleep 1

        if kill -0 "$pid" 2>/dev/null; then
            if [ "$peer_id" == "$FOCUS_NODE" ]; then
                echo -e "  ${YELLOW}★ Peer $peer_id started (PID $pid) -> $log_file [FOCUS NODE]${NC}"
            else
                echo -e "  ${GREEN}✓ Peer $peer_id started (PID $pid)${NC} -> $log_file"
            fi
        else
            echo -e "  ${RED}✗ Peer $peer_id failed to start${NC}"
            cat "$log_file"
        fi

        # Wait between peers to let JOIN protocol complete
        sleep 2
    done

    echo ""
    echo -e "${GREEN}All $NUM_PEERS autograder peers started successfully!${NC}"
}

# Show peer status
show_status() {
    echo ""
    echo -e "${CYAN}=== Autograder Network Status ===${NC}"
    echo ""
    echo -e "${BLUE}Discovery Node:${NC} localhost:$DISCOVER_PORT (PID $DISCOVER_PID)"
    echo ""
    echo -e "${BLUE}Peer Nodes (Autograder IDs):${NC}"

    for i in "${!AUTOGRADER_IDS[@]}"; do
        local peer_id="${AUTOGRADER_IDS[$i]}"
        # PID array has both peer PID and pipe keeper PID, so multiply index by 2
        local pid_idx=$((i * 2))
        local pid="${PEER_PIDS[$pid_idx]}"

        if kill -0 "$pid" 2>/dev/null; then
            if [ "$peer_id" == "$FOCUS_NODE" ]; then
                echo -e "  ${YELLOW}★${NC} Peer $peer_id (PID $pid) [FOCUS NODE]"
            elif [ "$TEST_CASE" != "3" ] && ([ "$peer_id" == "99b4" ] || [ "$peer_id" == "b5ad" ]); then
                echo -e "  ${CYAN}◆${NC} Peer $peer_id (PID $pid) [Expected neighbor of 9af1]"
            else
                echo -e "  ${GREEN}✓${NC} Peer $peer_id (PID $pid)"
            fi
        else
            echo -e "  ${RED}✗${NC} Peer $peer_id - STOPPED"
        fi
    done
    echo ""
}

# Check specific node's leaf-set
check_leaf_set() {
    local peer_id=$1
    local log_file="peer_${peer_id}_autograder.log"

    # Find the peer index
    local peer_idx=-1
    for i in "${!AUTOGRADER_IDS[@]}"; do
        if [ "${AUTOGRADER_IDS[$i]}" == "$peer_id" ]; then
            peer_idx=$i
            break
        fi
    done

    if [ $peer_idx -eq -1 ]; then
        echo -e "${RED}Error: Peer $peer_id not found${NC}"
        return
    fi

    local pipe="${PEER_PIPES[$peer_idx]}"

    echo -e "${BLUE}Checking leaf-set for peer $peer_id...${NC}"

    # Get current line count
    local before_lines=$(wc -l < "$log_file" 2>/dev/null || echo "0")

    # Send command
    echo "leaf-set" > "$pipe"
    sleep 1

    # Get output from log
    local after_lines=$(wc -l < "$log_file" 2>/dev/null || echo "0")
    local new_lines=$((after_lines - before_lines))

    if [ "$new_lines" -gt 0 ]; then
        tail -n "$new_lines" "$log_file"
    else
        echo -e "${YELLOW}No output (leaf set may be empty)${NC}"
    fi
}

# Tail log file
tail_log() {
    local log_file=$1
    echo ""
    echo -e "${CYAN}=== Tailing $log_file (Ctrl+C to stop) ===${NC}"
    echo ""
    tail -f "$log_file" 2>/dev/null || echo -e "${RED}Log file not found: $log_file${NC}"
}

# Show Test Case 2 verification
verify_test_case_2() {
    echo ""
    echo -e "${CYAN}=== Test Case 2 Verification ===${NC}"
    echo ""
    echo -e "${YELLOW}Expected for node 9af1:${NC}"
    echo "  left (before):  99b4"
    echo "  right (after):  b5ad"
    echo ""
    echo -e "${YELLOW}Ring order context:${NC}"
    echo "  ... → 99b4 → 9af1 → b5ad → b715 → ..."
    echo ""
    echo -e "${BLUE}Checking actual state:${NC}"
    echo ""

    check_leaf_set "9af1"

    echo ""
    echo -e "${YELLOW}Checking neighbors:${NC}"
    echo ""

    echo -e "${BLUE}Node 99b4:${NC}"
    check_leaf_set "99b4"
    echo ""

    echo -e "${BLUE}Node b5ad:${NC}"
    check_leaf_set "b5ad"
    echo ""
}

# Test Case 3: Order-of-Operations verification
verify_test_case_3() {
    echo ""
    echo -e "${CYAN}=== Test Case 3: Order-of-Operations Verification ===${NC}"
    echo ""
    echo -e "${YELLOW}Scenario:${NC}"
    echo "  4 nodes in ring: 1000 → 2000 → 3000 → 8000 → (wraps) 1000"
    echo "  Node 2000 exits (sends replacement: 3000 to node 1000)"
    echo ""
    echo -e "${YELLOW}Critical Test:${NC}"
    echo "  After exit, node 1000 must choose 3000 (explicit replacement)"
    echo "  NOT 8000 (from routing table), even though 8000 is processed by findReplacements()"
    echo ""

    # Step 1: Check initial state
    echo -e "${BLUE}[Step 1] Checking initial state of node 1000...${NC}"
    check_leaf_set "1000"
    echo ""

    local log_file="peer_1000_autograder.log"
    local peer_idx=$(get_peer_index "1000")
    local pipe="${PEER_PIPES[$peer_idx]}"

    # Get initial neighbors
    local before_lines=$(wc -l < "$log_file" 2>/dev/null || echo "0")
    echo "leaf-set" > "$pipe" 2>/dev/null
    sleep 0.5
    local after_lines=$(wc -l < "$log_file" 2>/dev/null || echo "0")
    local new_lines=$((after_lines - before_lines))

    local initial_left=""
    local initial_right=""
    if [ "$new_lines" -gt 0 ]; then
        local output=$(tail -n "$new_lines" "$log_file" 2>/dev/null)
        initial_left=$(echo "$output" | head -1 | awk -F', ' '{print $2}' | tr -d '\n\r')
        initial_right=$(echo "$output" | tail -1 | awk -F', ' '{print $2}' | tr -d '\n\r')
    fi

    echo -e "${YELLOW}Initial state: left=$initial_left, right=$initial_right${NC}"

    # Accept valid initial states (ring hasn't fully propagated yet)
    if [ "$initial_right" == "2000" ]; then
        echo -e "${GREEN}✓ Initial state valid - node 1000 has 2000 as right neighbor${NC}"
    else
        echo -e "${YELLOW}⚠ Unexpected initial state (right=$initial_right)${NC}"
    fi
    echo ""

    # Step 2: Trigger exit on node 2000
    echo -e "${BLUE}[Step 2] Triggering graceful exit on node 2000...${NC}"
    trigger_node_exit "2000"
    echo ""

    # Wait for LEAVE processing
    echo -e "${YELLOW}Waiting 3 seconds for LEAVE message processing...${NC}"
    sleep 3
    echo ""

    # Step 3: Check final state
    echo -e "${BLUE}[Step 3] Checking final state of node 1000...${NC}"
    check_leaf_set "1000"
    echo ""

    # Get final neighbors
    before_lines=$(wc -l < "$log_file" 2>/dev/null || echo "0")
    echo "leaf-set" > "$pipe" 2>/dev/null
    sleep 0.5
    after_lines=$(wc -l < "$log_file" 2>/dev/null || echo "0")
    new_lines=$((after_lines - before_lines))

    local final_left=""
    local final_right=""
    if [ "$new_lines" -gt 0 ]; then
        local output=$(tail -n "$new_lines" "$log_file" 2>/dev/null)
        final_left=$(echo "$output" | head -1 | awk -F', ' '{print $2}' | tr -d '\n\r')
        final_right=$(echo "$output" | tail -1 | awk -F', ' '{print $2}' | tr -d '\n\r')
    fi

    echo -e "${YELLOW}Final state: left=$final_left, right=$final_right${NC}"
    echo ""

    # Step 4: Verify result
    echo -e "${BLUE}[Step 4] Verifying Test Case 3 result...${NC}"
    echo ""

    local test_passed=1

    if [ "$final_right" == "3000" ]; then
        echo -e "${GREEN}✓ PASS: Node 1000 has right=3000 (correct replacement neighbor)${NC}"
    elif [ "$final_right" == "8000" ]; then
        echo -e "${RED}✗ FAIL: Node 1000 has right=8000 (routing table candidate took priority - BUG!)${NC}"
        test_passed=0
    else
        echo -e "${RED}✗ FAIL: Node 1000 has right=$final_right (unexpected value)${NC}"
        test_passed=0
    fi
    echo ""

    # Step 5: Check logs for bug pattern
    echo -e "${BLUE}[Step 5] Checking LEAVE protocol logs...${NC}"

    # Check detailed logs if available
    if [ -f "logs/peer-1000.log" ]; then
        echo -e "${BLUE}Checking detailed logs (logs/peer-1000.log)...${NC}"
        echo ""

        # Look for the critical sequence
        if grep -q "Replacement neighbor provided: 3000" "logs/peer-1000.log"; then
            echo -e "${GREEN}✓${NC} Found: Replacement neighbor provided: 3000"
        fi

        if grep -q "KEEPING RIGHT: 3000" "logs/peer-1000.log"; then
            echo -e "${GREEN}✓${NC} Found: KEEPING RIGHT: 3000 (rejected 8000 from routing table)"
            grep "Comparing RIGHT.*3000.*8000\|KEEPING RIGHT: 3000" "logs/peer-1000.log" | tail -2
        fi
        echo ""
    fi

    # Check command-output logs
    check_leave_logs "1000"

    # Final verdict
    echo ""
    echo -e "${CYAN}======================================${NC}"
    if [ $test_passed -eq 1 ]; then
        echo -e "${GREEN}✓ Test Case 3 PASSED!${NC}"
        echo -e "${GREEN}  Replacement neighbor priority is working correctly.${NC}"
        echo ""
        echo -e "${GREEN}Key Success Indicators:${NC}"
        echo -e "  • Node 1000's right neighbor = 3000 (explicit replacement) ✓"
        echo -e "  • findReplacements() found 8000 but correctly KEPT 3000 ✓"
        echo -e "  • Order-of-operations fix is working as designed ✓"
    else
        echo -e "${RED}✗ Test Case 3 FAILED!${NC}"
        echo -e "${RED}  The order-of-operations bug is still present.${NC}"
    fi
    echo -e "${CYAN}======================================${NC}"
    echo ""
}

# Test Case 4: Sequential Exits verification
verify_test_case_4() {
    echo ""
    echo -e "${CYAN}=== Test Case 4: Sequential Exits Verification ===${NC}"
    echo ""
    echo -e "${YELLOW}Scenario:${NC}"
    echo "  5-node ring: 1000 → 2000 → 3000 → 4000 → 5000 → (wraps) 1000"
    echo "  Sequential exits: 2000, then 3000, then 4000"
    echo ""
    echo -e "${YELLOW}Expected Progression:${NC}"
    echo "  After 2000 exits: 1000's right should be 3000"
    echo "  After 3000 exits: 1000's right should be 4000"
    echo "  After 4000 exits: 1000's right should be 5000"
    echo "  Final state: 2-node ring (1000 ↔ 5000)"
    echo ""

    local log_file="peer_1000_autograder.log"
    local peer_idx=$(get_peer_index "1000")
    local pipe="${PEER_PIPES[$peer_idx]}"
    local test_passed=1

    # Helper function to get current neighbors
    get_neighbors() {
        local before_lines=$(wc -l < "$log_file" 2>/dev/null || echo "0")
        echo "leaf-set" > "$pipe" 2>/dev/null
        sleep 0.5
        local after_lines=$(wc -l < "$log_file" 2>/dev/null || echo "0")
        local new_lines=$((after_lines - before_lines))

        if [ "$new_lines" -gt 0 ]; then
            local output=$(tail -n "$new_lines" "$log_file" 2>/dev/null)
            local left=$(echo "$output" | head -1 | awk -F', ' '{print $2}' | tr -d '\n\r')
            local right=$(echo "$output" | tail -1 | awk -F', ' '{print $2}' | tr -d '\n\r')
            echo "$left,$right"
        else
            echo "null,null"
        fi
    }

    # Step 1: Check initial 5-node ring
    echo -e "${BLUE}[Step 1] Checking initial state of 5-node ring...${NC}"
    check_leaf_set "1000"
    echo ""

    local neighbors=$(get_neighbors)
    local initial_left=$(echo "$neighbors" | cut -d',' -f1)
    local initial_right=$(echo "$neighbors" | cut -d',' -f2)
    echo -e "${YELLOW}Initial state: left=$initial_left, right=$initial_right${NC}"
    echo ""

    # Step 2: Exit node 2000
    echo -e "${BLUE}[Step 2] Exiting node 2000...${NC}"
    trigger_node_exit "2000"
    sleep 3
    echo ""

    neighbors=$(get_neighbors)
    local after_2000_left=$(echo "$neighbors" | cut -d',' -f1)
    local after_2000_right=$(echo "$neighbors" | cut -d',' -f2)
    echo -e "${YELLOW}After 2000 exits: left=$after_2000_left, right=$after_2000_right${NC}"

    if [ "$after_2000_right" == "3000" ]; then
        echo -e "${GREEN}✓ PASS: Node 1000's right = 3000 (expected after 2000 exits)${NC}"
    else
        echo -e "${RED}✗ FAIL: Node 1000's right = $after_2000_right (expected 3000)${NC}"
        test_passed=0
    fi
    echo ""

    # Step 3: Exit node 3000
    echo -e "${BLUE}[Step 3] Exiting node 3000...${NC}"
    trigger_node_exit "3000"
    sleep 3
    echo ""

    neighbors=$(get_neighbors)
    local after_3000_left=$(echo "$neighbors" | cut -d',' -f1)
    local after_3000_right=$(echo "$neighbors" | cut -d',' -f2)
    echo -e "${YELLOW}After 3000 exits: left=$after_3000_left, right=$after_3000_right${NC}"

    if [ "$after_3000_right" == "4000" ]; then
        echo -e "${GREEN}✓ PASS: Node 1000's right = 4000 (expected after 3000 exits)${NC}"
    else
        echo -e "${RED}✗ FAIL: Node 1000's right = $after_3000_right (expected 4000)${NC}"
        test_passed=0
    fi
    echo ""

    # Step 4: Exit node 4000
    echo -e "${BLUE}[Step 4] Exiting node 4000...${NC}"
    trigger_node_exit "4000"
    sleep 3
    echo ""

    neighbors=$(get_neighbors)
    local after_4000_left=$(echo "$neighbors" | cut -d',' -f1)
    local after_4000_right=$(echo "$neighbors" | cut -d',' -f2)
    echo -e "${YELLOW}After 4000 exits: left=$after_4000_left, right=$after_4000_right${NC}"

    if [ "$after_4000_right" == "5000" ]; then
        echo -e "${GREEN}✓ PASS: Node 1000's right = 5000 (expected after 4000 exits)${NC}"
    else
        echo -e "${RED}✗ FAIL: Node 1000's right = $after_4000_right (expected 5000)${NC}"
        test_passed=0
    fi
    echo ""

    # Step 5: Verify final 2-node ring
    echo -e "${BLUE}[Step 5] Verifying final 2-node ring (1000 ↔ 5000)...${NC}"
    echo ""

    # Check node 1000
    echo -e "${BLUE}Node 1000:${NC}"
    check_leaf_set "1000"
    echo ""

    # Check node 5000
    echo -e "${BLUE}Node 5000:${NC}"
    check_leaf_set "5000"
    echo ""

    # Verify symmetric relationship
    if [ "$after_4000_left" == "5000" ] && [ "$after_4000_right" == "5000" ]; then
        echo -e "${GREEN}✓ Node 1000 has both left=5000 and right=5000 (2-node ring)${NC}"
    else
        echo -e "${YELLOW}⚠ Node 1000: left=$after_4000_left, right=$after_4000_right${NC}"
        echo -e "${YELLOW}  (Expected both to be 5000 in a 2-node ring)${NC}"
    fi
    echo ""

    # Final verdict
    echo -e "${CYAN}======================================${NC}"
    if [ $test_passed -eq 1 ]; then
        echo -e "${GREEN}✓ Test Case 4 PASSED!${NC}"
        echo -e "${GREEN}  Sequential exits handled correctly.${NC}"
        echo ""
        echo -e "${GREEN}Key Success Indicators:${NC}"
        echo -e "  • After 2000 exits: 1000's right = 3000 ✓"
        echo -e "  • After 3000 exits: 1000's right = 4000 ✓"
        echo -e "  • After 4000 exits: 1000's right = 5000 ✓"
        echo -e "  • Final 2-node ring: 1000 ↔ 5000 ✓"
        echo -e "  • All leaf swaps executed in correct order ✓"
    else
        echo -e "${RED}✗ Test Case 4 FAILED!${NC}"
        echo -e "${RED}  Sequential exits did not update neighbors correctly.${NC}"
    fi
    echo -e "${CYAN}======================================${NC}"
    echo ""
}

# Show critical logs
show_join_logs() {
    echo ""
    echo -e "${CYAN}=== JOIN Protocol Logs for Node 9af1 ===${NC}"
    echo ""

    local log_file="peer_9af1_autograder.log"

    if [ -f "$log_file" ]; then
        echo -e "${BLUE}Entry point and JOIN path:${NC}"
        grep "Joining network via\|JOIN request for\|isClosestNode\|Routing next hop" "$log_file" || echo "No JOIN logs found"
        echo ""

        echo -e "${BLUE}Leaf set updates:${NC}"
        grep "Adding node.*isRight\|Setting.*to.*was\|FINAL STATE" "$log_file" || echo "No leaf set update logs found"
        echo ""
    else
        echo -e "${RED}Log file not found: $log_file${NC}"
    fi
}

# Check all nodes' leaf sets
check_all_nodes() {
    echo ""
    echo -e "${CYAN}=== All Nodes' Leaf Sets ===${NC}"
    echo ""
    printf "%-6s %-10s %-10s %s\n" "Node" "Left" "Right" "Status"
    printf "%-6s %-10s %-10s %s\n" "----" "----" "-----" "------"

    for peer_id in "${AUTOGRADER_IDS[@]}"; do
        local log_file="peer_${peer_id}_autograder.log"
        local peer_idx=-1

        for i in "${!AUTOGRADER_IDS[@]}"; do
            if [ "${AUTOGRADER_IDS[$i]}" == "$peer_id" ]; then
                peer_idx=$i
                break
            fi
        done

        local pipe="${PEER_PIPES[$peer_idx]}"
        local before_lines=$(wc -l < "$log_file" 2>/dev/null || echo "0")

        echo "leaf-set" > "$pipe" 2>/dev/null
        sleep 0.3

        local after_lines=$(wc -l < "$log_file" 2>/dev/null || echo "0")
        local new_lines=$((after_lines - before_lines))

        if [ "$new_lines" -gt 0 ]; then
            local output=$(tail -n "$new_lines" "$log_file" 2>/dev/null)
            local left_id=$(echo "$output" | head -1 | awk -F', ' '{print $2}' | tr -d '\n\r')
            local right_id=$(echo "$output" | tail -1 | awk -F', ' '{print $2}' | tr -d '\n\r')

            local status="${GREEN}✓${NC}"
            if [ "$peer_id" == "9af1" ]; then
                status="${YELLOW}★${NC}"
            fi

            printf "%-6s %-10s %-10s %b\n" "$peer_id" "$left_id" "$right_id" "$status"
        else
            printf "%-6s %-10s %-10s %b\n" "$peer_id" "???" "???" "${RED}✗${NC}"
        fi
    done
    echo ""
}

# Helper function to find index of a peer ID (bash 3.2 compatible)
get_peer_index() {
    local target_id=$1
    for i in "${!AUTOGRADER_IDS[@]}"; do
        if [ "${AUTOGRADER_IDS[$i]}" == "$target_id" ]; then
            echo $i
            return
        fi
    done
    echo -1
}

# Verify ring consistency (bash 3.2 compatible - uses indexed arrays)
verify_ring_consistency() {
    echo ""
    echo -e "${CYAN}=== Verifying Ring Consistency ===${NC}"
    echo ""

    # Use indexed arrays instead of associative arrays (bash 3.2 compatible)
    local left_neighbors=()
    local right_neighbors=()
    local errors=0
    local null_count=0

    # Collect all neighbors
    echo -e "${BLUE}Collecting neighbor information...${NC}"
    for i in "${!AUTOGRADER_IDS[@]}"; do
        local peer_id="${AUTOGRADER_IDS[$i]}"
        local log_file="peer_${peer_id}_autograder.log"

        local pipe="${PEER_PIPES[$i]}"
        local before_lines=$(wc -l < "$log_file" 2>/dev/null || echo "0")

        echo "leaf-set" > "$pipe" 2>/dev/null
        sleep 0.3

        local after_lines=$(wc -l < "$log_file" 2>/dev/null || echo "0")
        local new_lines=$((after_lines - before_lines))

        if [ "$new_lines" -ge 2 ]; then
            local output=$(tail -n "$new_lines" "$log_file" 2>/dev/null)
            left_neighbors[$i]=$(echo "$output" | head -1 | awk -F', ' '{print $2}' | tr -d '\n\r')
            right_neighbors[$i]=$(echo "$output" | tail -1 | awk -F', ' '{print $2}' | tr -d '\n\r')
        else
            left_neighbors[$i]="null"
            right_neighbors[$i]="null"
        fi
    done

    echo ""
    echo -e "${BLUE}Checking bidirectional consistency...${NC}"

    # Check bidirectional relationships
    for i in "${!AUTOGRADER_IDS[@]}"; do
        local peer_id="${AUTOGRADER_IDS[$i]}"
        local right="${right_neighbors[$i]}"
        local left="${left_neighbors[$i]}"

        # Check for nulls
        if [ "$right" == "null" ] || [ "$left" == "null" ]; then
            echo -e "${RED}✗${NC} Node $peer_id has null neighbors (left=$left, right=$right)"
            ((null_count++))
            ((errors++))
            continue
        fi

        # Check if right neighbor's left points back
        local right_idx=$(get_peer_index "$right")
        if [ $right_idx -ge 0 ]; then
            if [ "${left_neighbors[$right_idx]}" != "$peer_id" ]; then
                echo -e "${RED}✗${NC} $peer_id → right=$right, but $right → left=${left_neighbors[$right_idx]} (should be $peer_id)"
                ((errors++))
            fi
        fi
    done

    # Check wraparound
    local first_idx=0
    local last_idx=$((${#AUTOGRADER_IDS[@]} - 1))
    local first_node="${AUTOGRADER_IDS[$first_idx]}"
    local last_node="${AUTOGRADER_IDS[$last_idx]}"

    echo ""
    echo -e "${BLUE}Checking wraparound...${NC}"
    if [ "${right_neighbors[$last_idx]}" == "$first_node" ]; then
        echo -e "${GREEN}✓${NC} Last node (ecdd) → right points to first node (03c0)"
    else
        echo -e "${RED}✗${NC} Last node (ecdd) → right=${right_neighbors[$last_idx]} (should be 03c0)"
        ((errors++))
    fi

    if [ "${left_neighbors[$first_idx]}" == "$last_node" ]; then
        echo -e "${GREEN}✓${NC} First node (03c0) → left points to last node (ecdd)"
    else
        echo -e "${RED}✗${NC} First node (03c0) → left=${left_neighbors[$first_idx]} (should be ecdd)"
        ((errors++))
    fi

    echo ""
    if [ $errors -eq 0 ]; then
        echo -e "${GREEN}✓ Ring consistency check PASSED!${NC}"
        echo -e "  All ${#AUTOGRADER_IDS[@]} nodes have correct bidirectional neighbors"
    else
        echo -e "${RED}✗ Ring consistency check FAILED!${NC}"
        echo -e "  Found $errors errors ($null_count nodes with null neighbors)"
    fi
    echo ""
}

# Focus on specific node
focus_on_node() {
    local node_id=$1

    if [ -z "$node_id" ]; then
        echo -e "${RED}Error: No node ID provided${NC}"
        echo "Usage: f <node-id> (e.g., 'f b5ad')"
        return
    fi

    # Check if node exists
    local found=0
    for peer_id in "${AUTOGRADER_IDS[@]}"; do
        if [ "$peer_id" == "$node_id" ]; then
            found=1
            break
        fi
    done

    if [ $found -eq 0 ]; then
        echo -e "${RED}Error: Node $node_id not found in autograder IDs${NC}"
        return
    fi

    check_leaf_set "$node_id"
}

# Show ring segment around node
show_ring_segment() {
    local node_id=$1

    if [ -z "$node_id" ]; then
        echo -e "${RED}Error: No node ID provided${NC}"
        echo "Usage: r <node-id> (e.g., 'r 9af1')"
        return
    fi

    echo ""
    echo -e "${CYAN}=== Ring Segment Around $node_id ===${NC}"
    echo ""

    # Get node's neighbors
    local log_file="peer_${node_id}_autograder.log"
    local peer_idx=-1

    for i in "${!AUTOGRADER_IDS[@]}"; do
        if [ "${AUTOGRADER_IDS[$i]}" == "$node_id" ]; then
            peer_idx=$i
            break
        fi
    done

    if [ $peer_idx -eq -1 ]; then
        echo -e "${RED}Error: Node $node_id not found${NC}"
        return
    fi

    local pipe="${PEER_PIPES[$peer_idx]}"
    local before_lines=$(wc -l < "$log_file" 2>/dev/null || echo "0")

    echo "leaf-set" > "$pipe" 2>/dev/null
    sleep 0.5

    local after_lines=$(wc -l < "$log_file" 2>/dev/null || echo "0")
    local new_lines=$((after_lines - before_lines))

    if [ "$new_lines" -ge 2 ]; then
        local output=$(tail -n "$new_lines" "$log_file" 2>/dev/null)
        local left_id=$(echo "$output" | head -1 | awk -F', ' '{print $2}' | tr -d '\n\r')
        local right_id=$(echo "$output" | tail -1 | awk -F', ' '{print $2}' | tr -d '\n\r')

        echo -e "${BLUE}Ring segment:${NC}"
        echo -e "  $left_id → ${YELLOW}$node_id${NC} → $right_id"
        echo ""
    else
        echo -e "${RED}Could not retrieve neighbors for $node_id${NC}"
    fi
}

# Export ring structure
export_ring_structure() {
    local output_file="ring_structure.txt"

    echo ""
    echo -e "${BLUE}Exporting ring structure to $output_file...${NC}"

    {
        echo "=========================================="
        echo "Pastry DHT Ring Structure"
        echo "Autograder Test Case 2"
        echo "Generated: $(date)"
        echo "=========================================="
        echo ""
        echo "Node    Left      Right"
        echo "----    ----      -----"

        for peer_id in "${AUTOGRADER_IDS[@]}"; do
            local log_file="peer_${peer_id}_autograder.log"
            local peer_idx=-1

            for i in "${!AUTOGRADER_IDS[@]}"; do
                if [ "${AUTOGRADER_IDS[$i]}" == "$peer_id" ]; then
                    peer_idx=$i
                    break
                fi
            done

            local pipe="${PEER_PIPES[$peer_idx]}"
            local before_lines=$(wc -l < "$log_file" 2>/dev/null || echo "0")

            echo "leaf-set" > "$pipe" 2>/dev/null
            sleep 0.3

            local after_lines=$(wc -l < "$log_file" 2>/dev/null || echo "0")
            local new_lines=$((after_lines - before_lines))

            if [ "$new_lines" -gt 0 ]; then
                local output=$(tail -n "$new_lines" "$log_file" 2>/dev/null)
                local left_id=$(echo "$output" | head -1 | awk -F', ' '{print $2}' | tr -d '\n\r')
                local right_id=$(echo "$output" | tail -1 | awk -F', ' '{print $2}' | tr -d '\n\r')

                printf "%-6s  %-8s  %-8s\n" "$peer_id" "$left_id" "$right_id"
            fi
        done
    } > "$output_file"

    echo -e "${GREEN}✓ Ring structure exported to $output_file${NC}"
    echo ""
}

# Trigger graceful exit on a peer node
trigger_node_exit() {
    local peer_id=$1

    if [ -z "$peer_id" ]; then
        echo -e "${RED}Error: No node ID provided${NC}"
        return 1
    fi

    # Find the peer index
    local peer_idx=-1
    for i in "${!AUTOGRADER_IDS[@]}"; do
        if [ "${AUTOGRADER_IDS[$i]}" == "$peer_id" ]; then
            peer_idx=$i
            break
        fi
    done

    if [ $peer_idx -eq -1 ]; then
        echo -e "${RED}Error: Peer $peer_id not found${NC}"
        return 1
    fi

    local pipe="${PEER_PIPES[$peer_idx]}"
    local log_file="peer_${peer_id}_autograder.log"

    echo -e "${YELLOW}Triggering graceful exit on node $peer_id...${NC}"

    # Get line count before exit
    local before_lines=$(wc -l < "$log_file" 2>/dev/null || echo "0")

    # Send exit command
    echo "exit" > "$pipe" 2>/dev/null

    # Wait for graceful shutdown
    sleep 2

    # Show exit logs
    local after_lines=$(wc -l < "$log_file" 2>/dev/null || echo "0")
    local new_lines=$((after_lines - before_lines))

    if [ "$new_lines" -gt 0 ]; then
        echo -e "${BLUE}Exit logs for $peer_id:${NC}"
        tail -n "$new_lines" "$log_file" | grep -E "LEAVE|Shutting down|Sent.*notification" || echo "(No LEAVE messages found)"
    fi

    echo -e "${GREEN}✓ Node $peer_id exit triggered${NC}"
}

# Check LEAVE message logs for correct order
check_leave_logs() {
    local peer_id=$1
    local log_file="peer_${peer_id}_autograder.log"

    echo ""
    echo -e "${CYAN}=== Checking LEAVE Protocol Logs for $peer_id ===${NC}"
    echo ""

    if [ ! -f "$log_file" ]; then
        echo -e "${RED}Log file not found: $log_file${NC}"
        return 1
    fi

    # Look for the critical log patterns
    echo -e "${BLUE}Searching for LEAVE message handling...${NC}"
    echo ""

    # Check for "Removing neighbor (no auto-replacement)"
    if grep -q "Removing.*neighbor (no auto-replacement)" "$log_file"; then
        echo -e "${GREEN}✓${NC} Found: Removing neighbor (no auto-replacement)"
        grep "Removing.*neighbor (no auto-replacement)" "$log_file" | tail -1
    else
        echo -e "${YELLOW}⚠${NC} Not found: Removing neighbor (no auto-replacement)"
    fi
    echo ""

    # Check for "Replacement neighbor provided"
    if grep -q "Replacement neighbor provided" "$log_file"; then
        echo -e "${GREEN}✓${NC} Found: Replacement neighbor provided"
        grep "Replacement neighbor provided" "$log_file" | tail -1
    else
        echo -e "${YELLOW}⚠${NC} Not found: Replacement neighbor provided"
    fi
    echo ""

    # Check for "Finding replacements for vacant leaf slots"
    if grep -q "Finding replacements for vacant leaf slots" "$log_file"; then
        echo -e "${GREEN}✓${NC} Found: Finding replacements for vacant leaf slots"
        grep "Finding replacements for vacant leaf slots" "$log_file" | tail -1
    else
        echo -e "${YELLOW}⚠${NC} Not found: Finding replacements for vacant leaf slots"
    fi
    echo ""

    # CRITICAL: Check for bug pattern (should NOT exist!)
    if grep -q "Calling findReplacements() after removing" "$log_file"; then
        echo -e "${RED}✗ BUG DETECTED!${NC} Found: Calling findReplacements() after removing"
        echo -e "${RED}This indicates the bug is NOT fixed!${NC}"
        grep "Calling findReplacements() after removing" "$log_file" | tail -1
        return 1
    else
        echo -e "${GREEN}✓ Bug pattern NOT found (good!)${NC}"
        echo -e "  Did not find: 'Calling findReplacements() after removing'"
    fi
    echo ""

    # Show the order of operations
    echo -e "${BLUE}Order of operations:${NC}"
    grep -E "Removing.*neighbor|Replacement neighbor provided|Finding replacements|FINAL STATE" "$log_file" | tail -10
    echo ""
}

# Run all validations
run_all_validations() {
    echo ""
    echo -e "${CYAN}======================================${NC}"
    echo -e "${CYAN}  Running All Test Validations${NC}"
    echo -e "${CYAN}======================================${NC}"

    # Test 1: Check 9af1
    echo ""
    echo -e "${YELLOW}[1/3] Checking node 9af1 (critical autograder node)...${NC}"
    verify_test_case_2

    # Test 2: Ring consistency
    echo ""
    echo -e "${YELLOW}[2/3] Verifying ring consistency...${NC}"
    verify_ring_consistency

    # Test 3: Wraparound check already done in ring consistency

    echo ""
    echo -e "${CYAN}======================================${NC}"
    echo -e "${GREEN}Validation complete!${NC}"
    echo -e "${CYAN}======================================${NC}"
    echo ""
}

# Show menu
show_menu() {
    echo -e "${CYAN}=== Autograder Test Menu ($TEST_NAME) ===${NC}"
    echo ""
    echo -e "${YELLOW}Quick Actions:${NC}"

    if [ "$TEST_CASE" == "3" ]; then
        echo "  v) Run Test Case 3 (Order-of-Operations test)"
    elif [ "$TEST_CASE" == "4" ]; then
        echo "  v) Run Test Case 4 (Sequential Exits test)"
    else
        echo "  v) Verify Test Case 2 (check 9af1 neighbors)"
    fi

    echo "  a) Check all nodes' leaf-sets (table view)"

    if [ "$TEST_CASE" == "2" ]; then
        echo "  c) Verify ring consistency"
        echo "  t) Run all test validations"
    fi

    echo ""
    echo -e "${YELLOW}Individual Checks:${NC}"
    echo "  f <id>) Focus on specific node (e.g., 'f $FOCUS_NODE')"
    echo "  r <id>) Show ring segment around node (e.g., 'r $FOCUS_NODE')"

    if [ "$TEST_CASE" == "3" ] || [ "$TEST_CASE" == "4" ]; then
        echo "  x <id>) Trigger graceful exit on node (e.g., 'x 2000')"
        echo "  lv <id>) Check LEAVE logs for node (e.g., 'lv 1000')"
    fi

    echo ""
    echo -e "${YELLOW}Logs:${NC}"
    echo "  ld) Tail Discovery log"
    echo "  l <id>) Tail specific node log (e.g., 'l $FOCUS_NODE')"
    echo ""
    echo -e "${YELLOW}Other:${NC}"
    echo "  e) Export ring structure to file"
    echo "  s) Show network status"
    echo "  q) Quit"
    echo ""
}

# Main interactive loop
interactive_mode() {
    while true; do
        show_menu
        read -p "Select option: " choice
        echo ""

        case "$choice" in
            v|V)
                if [ "$TEST_CASE" == "3" ]; then
                    verify_test_case_3
                elif [ "$TEST_CASE" == "4" ]; then
                    verify_test_case_4
                else
                    verify_test_case_2
                fi
                ;;
            a|A)
                check_all_nodes
                ;;
            c|C)
                if [ "$TEST_CASE" == "2" ]; then
                    verify_ring_consistency
                else
                    echo -e "${RED}Ring consistency check only applicable for Test Case 2${NC}"
                fi
                ;;
            t|T)
                if [ "$TEST_CASE" == "2" ]; then
                    run_all_validations
                else
                    echo -e "${RED}Full validation only applicable for Test Case 2${NC}"
                fi
                ;;
            f\ *|F\ *)
                local node_id=$(echo "$choice" | awk '{print $2}')
                focus_on_node "$node_id"
                ;;
            r\ *|R\ *)
                local node_id=$(echo "$choice" | awk '{print $2}')
                show_ring_segment "$node_id"
                ;;
            x\ *|X\ *)
                if [ "$TEST_CASE" == "3" ] || [ "$TEST_CASE" == "4" ]; then
                    local node_id=$(echo "$choice" | awk '{print $2}')
                    trigger_node_exit "$node_id"
                else
                    echo -e "${RED}Exit command only available in Test Cases 3 and 4${NC}"
                fi
                ;;
            lv\ *|LV\ *)
                if [ "$TEST_CASE" == "3" ] || [ "$TEST_CASE" == "4" ]; then
                    local node_id=$(echo "$choice" | awk '{print $2}')
                    check_leave_logs "$node_id"
                else
                    echo -e "${RED}LEAVE log check only available in Test Cases 3 and 4${NC}"
                fi
                ;;
            l\ *|L\ *)
                local node_id=$(echo "$choice" | awk '{print $2}')
                tail_log "peer_${node_id}_autograder.log"
                ;;
            e|E)
                export_ring_structure
                ;;
            j|J)
                show_join_logs
                ;;
            1)
                check_leaf_set "9af1"
                ;;
            2)
                check_leaf_set "99b4"
                ;;
            3)
                check_leaf_set "b5ad"
                ;;
            4)
                check_leaf_set "b715"
                ;;
            ld|LD)
                tail_log "discover_autograder.log"
                ;;
            l9|L9)
                tail_log "peer_9af1_autograder.log"
                ;;
            la|LA)
                tail_log "peer_99b4_autograder.log"
                ;;
            lb|LB)
                tail_log "peer_b5ad_autograder.log"
                ;;
            s|S)
                show_status
                ;;
            q|Q)
                echo -e "${YELLOW}Exiting...${NC}"
                exit 0
                ;;
            *)
                echo -e "${RED}Invalid option: $choice${NC}"
                ;;
        esac

        echo ""
        read -p "Press Enter to continue..."
        clear
    done
}

# Main
clear
echo -e "${CYAN}==========================================${NC}"
echo -e "${CYAN}  CS555 HW3 - $TEST_NAME    ${NC}"
echo -e "${CYAN}==========================================${NC}"
echo ""
echo -e "${BLUE}Configuration:${NC}"
echo "  Number of peers: $NUM_PEERS"
echo "  Discovery port: $DISCOVER_PORT"
echo "  Node IDs: ${AUTOGRADER_IDS[*]}"
echo ""

if [ "$TEST_CASE" == "3" ]; then
    echo -e "${YELLOW}Test Case 3 Scenario:${NC}"
    echo "  Focus Node: 1000"
    echo "  Node 1000 initially: left=8000, right=2000"
    echo "  After 2000 exits: right should be 3000 (NOT 8000)"
elif [ "$TEST_CASE" == "4" ]; then
    echo -e "${YELLOW}Test Case 4 Scenario:${NC}"
    echo "  Focus Node: 1000"
    echo "  5-node ring: 1000 → 2000 → 3000 → 4000 → 5000"
    echo "  Sequential exits: 2000, then 3000, then 4000"
    echo "  Expected: 1000's right updates to 3000, then 4000, then 5000"
    echo "  Final state: 2-node ring (1000 ↔ 5000)"
else
    echo -e "${YELLOW}Focus Node: 9af1${NC}"
    echo -e "${YELLOW}Expected Neighbors:${NC}"
    echo "  left:  99b4"
    echo "  right: b5ad"
fi

echo ""

start_discovery
start_peers
show_status

echo ""
echo -e "${GREEN}$TEST_NAME network is ready!${NC}"

if [ "$TEST_CASE" == "3" ]; then
    echo -e "${YELLOW}Tip: Use option 'v' to run the full Test Case 3 verification${NC}"
elif [ "$TEST_CASE" == "4" ]; then
    echo -e "${YELLOW}Tip: Use option 'v' to run the full Test Case 4 verification${NC}"
fi

echo ""
read -p "Press Enter to start interactive mode..."
clear

interactive_mode
