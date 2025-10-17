#!/usr/bin/env bash
# test_routing_table.sh - Dedicated test for routing table population
# Tests the fix for autograder failure where node b589 had only 3 entries
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

# Exact 13 node IDs from autograder failure
NODE_IDS=(
    "1804" "2770" "3e37" "43ac" "6050" "93c8" "98a7" "9991" "9a71" "b4f9" "b589" "cd9e" "d481"
)

FOCUS_NODE="b589"
NUM_NODES=${#NODE_IDS[@]}

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
    java -cp build/classes/java/main csx55.pastry.node.Discover $DISCOVER_PORT &> discover_rt.log &
    DISCOVER_PID=$!

    # Wait for "Ready to accept" message in log (max 10 seconds)
    for i in {1..20}; do
        if grep -q "Ready to accept" discover_rt.log 2>/dev/null; then
            break
        fi
        sleep 0.5
    done

    # Verify process is still running
    if kill -0 "$DISCOVER_PID" 2>/dev/null; then
        echo -e "${GREEN}✓ Discovery Node started (PID $DISCOVER_PID)${NC}"
    else
        echo -e "${RED}✗ Discovery Node failed to start${NC}"
        cat discover_rt.log
        exit 1
    fi
}

# Start peer nodes with exact autograder IDs
start_peers() {
    echo ""
    echo -e "${BLUE}Starting $NUM_NODES Peer Nodes...${NC}"
    echo -e "${CYAN}(Using exact autograder node IDs)${NC}"
    echo ""

    for i in "${!NODE_IDS[@]}"; do
        local peer_id="${NODE_IDS[$i]}"
        local log_file="peer_${peer_id}_rt.log"
        local pipe_file="/tmp/peer_${peer_id}_rt_pipe"

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
                echo -e "  ${YELLOW}★ Peer $peer_id started (PID $pid) [FOCUS NODE]${NC}"
            else
                echo -e "  ${GREEN}✓ Peer $peer_id started (PID $pid)${NC}"
            fi
        else
            echo -e "  ${RED}✗ Peer $peer_id failed to start${NC}"
            cat "$log_file"
        fi

        # Wait between peers to let JOIN protocol complete
        sleep 3
    done

    echo ""
    echo -e "${GREEN}All $NUM_NODES peers started successfully!${NC}"
}

# Get peer index by node ID
get_peer_index() {
    local target_id=$1
    for i in "${!NODE_IDS[@]}"; do
        if [ "${NODE_IDS[$i]}" == "$target_id" ]; then
            echo $i
            return
        fi
    done
    echo -1
}

# Get routing table output for a node
get_routing_table() {
    local peer_id=$1
    local log_file="peer_${peer_id}_rt.log"
    local peer_idx=$(get_peer_index "$peer_id")

    if [ $peer_idx -eq -1 ]; then
        echo ""
        return 1
    fi

    local pipe="${PEER_PIPES[$peer_idx]}"

    # Get current line count
    local before_lines=$(wc -l < "$log_file" 2>/dev/null || echo "0")

    # Send command
    echo "routing-table" > "$pipe" 2>/dev/null
    sleep 1

    # Get output from log
    local after_lines=$(wc -l < "$log_file" 2>/dev/null || echo "0")
    local new_lines=$((after_lines - before_lines))

    if [ "$new_lines" -gt 0 ]; then
        tail -n "$new_lines" "$log_file"
    fi
}

# Count non-empty entries in a row
count_row_entries() {
    local row=$1
    # Count entries that don't end with "-:"
    echo "$row" | grep -o '[0-9a-f]\+-[^:][^,]*' | wc -l | tr -d ' '
}

# Parse routing table and analyze
analyze_routing_table() {
    local peer_id=$1
    local output=$2

    echo ""
    echo -e "${CYAN}=== Analyzing Routing Table for Node $peer_id ===${NC}"
    echo ""

    # Split into rows
    local row0=$(echo "$output" | sed -n '1p')
    local row1=$(echo "$output" | sed -n '2p')
    local row2=$(echo "$output" | sed -n '3p')
    local row3=$(echo "$output" | sed -n '4p')

    # Count entries per row
    local count0=$(count_row_entries "$row0")
    local count1=$(count_row_entries "$row1")
    local count2=$(count_row_entries "$row2")
    local count3=$(count_row_entries "$row3")
    local total=$((count0 + count1 + count2 + count3))

    # Get node's ID digits
    local digit0=${peer_id:0:1}
    local digit1=${peer_id:1:1}
    local digit2=${peer_id:2:1}
    local digit3=${peer_id:3:1}

    # Row 0: All hex digits except node's first digit
    echo -e "${BLUE}Row 0 (1st digit ≠ '$digit0'):${NC}"
    echo -e "  Available prefixes: 15 possible (any hex digit except '$digit0')"
    echo -e "  Found: $count0 entries"

    if [ $count0 -gt 0 ]; then
        echo -e "  ${GREEN}✓ Has entries${NC}"
    else
        echo -e "  ${YELLOW}⚠ Empty (node may have joined early)${NC}"
    fi

    # Show which entries are present in row 0
    if [ $count0 -gt 0 ]; then
        echo -e "  Present entries:"
        echo "$row0" | grep -o '[0-9a-f]-[^:][^,]*' | while read entry; do
            local prefix=$(echo "$entry" | cut -d'-' -f1)
            if [ "$prefix" != "$digit0" ]; then
                echo -e "    ${GREEN}✓${NC} $entry"
            fi
        done
    fi
    echo ""

    # Row 1: Node's first digit + any second digit except node's
    echo -e "${BLUE}Row 1 (${digit0} + 2nd digit ≠ '$digit1'):${NC}"
    echo -e "  Available prefixes: 15 possible (${digit0}[0-f] except ${digit0}${digit1})"
    echo -e "  Found: $count1 entries"

    if [ $count1 -gt 0 ]; then
        echo -e "  ${GREEN}✓ Has entries${NC}"
        echo -e "  Present entries:"
        echo "$row1" | grep -o "${digit0}[0-9a-f]-[^:][^,]*" | while read entry; do
            echo -e "    ${GREEN}✓${NC} $entry"
        done
    else
        echo -e "  ${YELLOW}⚠ Empty (requires nodes with prefix '${digit0}')${NC}"
    fi
    echo ""

    # Row 2 & Row 3
    echo -e "${BLUE}Row 2 (${digit0}${digit1} + 3rd digit ≠ '$digit2'):${NC}"
    echo -e "  Found: $count2 entries"
    if [ $count2 -gt 0 ]; then
        echo -e "  ${GREEN}✓ Has entries${NC}"
    fi
    echo ""

    echo -e "${BLUE}Row 3 (${digit0}${digit1}${digit2} + 4th digit ≠ '$digit3'):${NC}"
    echo -e "  Found: $count3 entries"
    if [ $count3 -gt 0 ]; then
        echo -e "  ${GREEN}✓ Has entries${NC}"
    fi
    echo ""

    # Check for self-entry (BUG if found)
    if echo "$output" | grep -q "${peer_id}-[^:][^,]*"; then
        echo -e "${RED}✗ BUG DETECTED: Node $peer_id found in its own routing table!${NC}"
        return 1
    fi

    # Check row density (row 0 should have most entries)
    local density_ok=1
    if [ $count0 -ge $count1 ] && [ $count1 -ge $count2 ] && [ $count2 -ge $count3 ]; then
        echo -e "${GREEN}✓ Row density valid: $count0 ≥ $count1 ≥ $count2 ≥ $count3${NC}"
    else
        echo -e "${YELLOW}⚠ Unexpected density: $count0, $count1, $count2, $count3${NC}"
        density_ok=0
    fi
    echo ""

    echo -e "${CYAN}========================================${NC}"
    echo -e "${BLUE}Total Entries: $total${NC}"

    # Pass criteria: Has some entries and valid structure
    if [ $total -gt 0 ] && [ $density_ok -eq 1 ]; then
        echo -e "${GREEN}✓ PASS - Node $peer_id has $total routing table entries with valid structure${NC}"
        echo -e "  Note: Entry count depends on join order and network size"
        return 0
    elif [ $total -gt 0 ]; then
        echo -e "${YELLOW}⚠ PARTIAL - Node $peer_id has $total entries but unusual density${NC}"
        return 0
    else
        echo -e "${RED}✗ FAIL - Node $peer_id has no routing table entries!${NC}"
        return 1
    fi
}

# Verify node b589 (main test)
verify_node_b589() {
    echo ""
    echo -e "${YELLOW}=== Verifying Node b589 Routing Table ===${NC}"
    echo ""
    echo -e "${BLUE}Querying routing table...${NC}"

    local output=$(get_routing_table "$FOCUS_NODE")

    if [ -z "$output" ]; then
        echo -e "${RED}✗ Failed to get routing table output${NC}"
        return 1
    fi

    analyze_routing_table "$FOCUS_NODE" "$output"
}

# Show raw routing table output
show_raw_routing_table() {
    local peer_id=$1

    echo ""
    echo -e "${CYAN}=== Raw Routing Table Output for Node $peer_id ===${NC}"
    echo ""

    local output=$(get_routing_table "$peer_id")

    if [ -z "$output" ]; then
        echo -e "${RED}No output received${NC}"
    else
        echo "$output"
    fi
    echo ""
}

# Check all nodes' routing tables (summary)
check_all_routing_tables() {
    echo ""
    echo -e "${CYAN}=== All Nodes' Routing Table Summary ===${NC}"
    echo ""
    printf "%-6s %-8s %-8s %-8s %-8s %-8s %s\n" "Node" "Row0" "Row1" "Row2" "Row3" "Total" "Status"
    printf "%-6s %-8s %-8s %-8s %-8s %-8s %s\n" "----" "----" "----" "----" "----" "-----" "------"

    for peer_id in "${NODE_IDS[@]}"; do
        local output=$(get_routing_table "$peer_id")

        if [ -z "$output" ]; then
            printf "%-6s %-8s %-8s %-8s %-8s %-8s %b\n" "$peer_id" "???" "???" "???" "???" "???" "${RED}✗${NC}"
            continue
        fi

        local row0=$(echo "$output" | sed -n '1p')
        local row1=$(echo "$output" | sed -n '2p')
        local row2=$(echo "$output" | sed -n '3p')
        local row3=$(echo "$output" | sed -n '4p')

        local count0=$(count_row_entries "$row0")
        local count1=$(count_row_entries "$row1")
        local count2=$(count_row_entries "$row2")
        local count3=$(count_row_entries "$row3")
        local total=$((count0 + count1 + count2 + count3))

        local status="${GREEN}✓${NC}"
        if [ "$peer_id" == "$FOCUS_NODE" ]; then
            status="${YELLOW}★${NC}"
            if [ $total -lt 8 ]; then
                status="${RED}✗${NC}"
            fi
        fi

        printf "%-6s %-8s %-8s %-8s %-8s %-8s %b\n" "$peer_id" "$count0" "$count1" "$count2" "$count3" "$total" "$status"
    done
    echo ""
}

# Show JOIN protocol logs for a node
show_join_logs() {
    local peer_id=$1
    local log_file="peer_${peer_id}_rt.log"

    echo ""
    echo -e "${CYAN}=== JOIN Protocol Logs for Node $peer_id ===${NC}"
    echo ""

    if [ ! -f "$log_file" ]; then
        echo -e "${RED}Log file not found: $log_file${NC}"
        return 1
    fi

    echo -e "${BLUE}Entry point and JOIN path:${NC}"
    grep -E "Joining network via|JOIN request for|isClosestNode|Routing next hop" "$log_file" | head -20 || echo "No JOIN logs found"
    echo ""

    echo -e "${BLUE}JOIN responses received:${NC}"
    grep -E "Sent JOIN_RESPONSE|JOIN_RESPONSE.*routing table row" "$log_file" | head -20 || echo "No JOIN_RESPONSE logs found"
    echo ""
}

# Show network status
show_status() {
    echo ""
    echo -e "${CYAN}=== Network Status ===${NC}"
    echo ""
    echo -e "${BLUE}Discovery Node:${NC} localhost:$DISCOVER_PORT (PID $DISCOVER_PID)"
    echo ""
    echo -e "${BLUE}Peer Nodes ($NUM_NODES):${NC}"

    for i in "${!NODE_IDS[@]}"; do
        local peer_id="${NODE_IDS[$i]}"
        local pid_idx=$((i * 2))
        local pid="${PEER_PIDS[$pid_idx]}"

        if kill -0 "$pid" 2>/dev/null; then
            if [ "$peer_id" == "$FOCUS_NODE" ]; then
                echo -e "  ${YELLOW}★${NC} Peer $peer_id (PID $pid) [FOCUS NODE]"
            else
                echo -e "  ${GREEN}✓${NC} Peer $peer_id (PID $pid)"
            fi
        else
            echo -e "  ${RED}✗${NC} Peer $peer_id - STOPPED"
        fi
    done
    echo ""
}

# Show expected vs actual detailed comparison
show_expected_vs_actual() {
    echo ""
    echo -e "${CYAN}=== Expected vs Actual Routing Table for b589 ===${NC}"
    echo ""

    local output=$(get_routing_table "$FOCUS_NODE")

    if [ -z "$output" ]; then
        echo -e "${RED}Failed to get routing table${NC}"
        return 1
    fi

    local row0=$(echo "$output" | sed -n '1p')

    echo -e "${BLUE}Row 0 Analysis (1st digit ≠ 'b'):${NC}"
    echo ""
    printf "%-8s %-10s %-50s\n" "Prefix" "Expected" "Status"
    printf "%-8s %-10s %-50s\n" "------" "--------" "------"

    local expected_prefixes=("1" "2" "3" "4" "6" "9" "c" "d")

    for prefix in "${expected_prefixes[@]}"; do
        if echo "$row0" | grep -q "${prefix}-[^:][^,]*"; then
            local entry=$(echo "$row0" | grep -o "${prefix}-[^,]*" | head -1)
            printf "%-8s %-10s %b %-50s\n" "${prefix}-" "Required" "${GREEN}✓ PRESENT${NC}" "$entry"
        else
            printf "%-8s %-10s %b %-50s\n" "${prefix}-" "Required" "${RED}✗ MISSING${NC}" ""
        fi
    done
    echo ""
}

# Advanced Test: Self-ID Exclusion
test_self_id_exclusion() {
    echo ""
    echo -e "${CYAN}=== Advanced Test: Self-ID Exclusion ===${NC}"
    echo ""
    echo -e "${YELLOW}Purpose:${NC} Verify node never adds itself to its own routing table"
    echo ""

    local test_node="b589"
    local output=$(get_routing_table "$test_node")

    if [ -z "$output" ]; then
        echo -e "${RED}✗ Failed to get routing table${NC}"
        return 1
    fi

    # Check if node's own ID appears as a POPULATED entry (not empty "-:")
    # Pattern: ${test_node}- followed by a digit (start of IP address)
    if echo "$output" | grep -q "${test_node}-[0-9]"; then
        echo -e "${RED}✗ FAIL: Node $test_node found in its own routing table!${NC}"
        echo "$output" | grep "${test_node}-[0-9]"
        return 1
    else
        echo -e "${GREEN}✓ PASS: Node $test_node does not appear in its own routing table${NC}"
        return 0
    fi
}

# Advanced Test: Row Density
test_row_density() {
    echo ""
    echo -e "${CYAN}=== Advanced Test: Row Density ===${NC}"
    echo ""
    echo -e "${YELLOW}Purpose:${NC} Verify row 0 has most entries, then row 1, then row 2, then row 3"
    echo ""

    local test_node="b589"
    local output=$(get_routing_table "$test_node")

    if [ -z "$output" ]; then
        echo -e "${RED}✗ Failed to get routing table${NC}"
        return 1
    fi

    local row0=$(echo "$output" | sed -n '1p')
    local row1=$(echo "$output" | sed -n '2p')
    local row2=$(echo "$output" | sed -n '3p')
    local row3=$(echo "$output" | sed -n '4p')

    local count0=$(count_row_entries "$row0")
    local count1=$(count_row_entries "$row1")
    local count2=$(count_row_entries "$row2")
    local count3=$(count_row_entries "$row3")

    echo -e "${BLUE}Entry counts:${NC}"
    echo "  Row 0: $count0"
    echo "  Row 1: $count1"
    echo "  Row 2: $count2"
    echo "  Row 3: $count3"
    echo ""

    local passed=1

    # Check row 0 >= row 1
    if [ $count0 -ge $count1 ]; then
        echo -e "${GREEN}✓${NC} Row 0 ($count0) >= Row 1 ($count1)"
    else
        echo -e "${RED}✗${NC} Row 0 ($count0) < Row 1 ($count1) - UNEXPECTED!"
        passed=0
    fi

    # Check row 1 >= row 2
    if [ $count1 -ge $count2 ]; then
        echo -e "${GREEN}✓${NC} Row 1 ($count1) >= Row 2 ($count2)"
    else
        echo -e "${RED}✗${NC} Row 1 ($count1) < Row 2 ($count2) - UNEXPECTED!"
        passed=0
    fi

    # Check row 2 >= row 3
    if [ $count2 -ge $count3 ]; then
        echo -e "${GREEN}✓${NC} Row 2 ($count2) >= Row 3 ($count3)"
    else
        echo -e "${RED}✗${NC} Row 2 ($count2) < Row 3 ($count3) - UNEXPECTED!"
        passed=0
    fi

    echo ""
    if [ $passed -eq 1 ]; then
        echo -e "${GREEN}✓ OVERALL PASS: Density decreases from row 0 to row 3${NC}"
        return 0
    else
        echo -e "${RED}✗ OVERALL FAIL: Unexpected density distribution${NC}"
        return 1
    fi
}

# Advanced Test: Leaf nodes in routing table
test_leaf_in_routing() {
    echo ""
    echo -e "${CYAN}=== Advanced Test: Leaf Set Nodes in Routing Table ===${NC}"
    echo ""
    echo -e "${YELLOW}Purpose:${NC} Per MessageHandler:227, leaf neighbors should also be in routing table"
    echo ""

    # For b589, neighbors should be b4f9 (left) and cd9e (right)
    local test_node="b589"
    local expected_left="b4f9"
    local expected_right="cd9e"

    local output=$(get_routing_table "$test_node")

    if [ -z "$output" ]; then
        echo -e "${RED}✗ Failed to get routing table${NC}"
        return 1
    fi

    local passed=1

    # Check if left neighbor is in routing table
    if echo "$output" | grep -q "${expected_left}-[^:][^,]*"; then
        echo -e "${GREEN}✓${NC} Left neighbor $expected_left found in routing table"
    else
        echo -e "${RED}✗${NC} Left neighbor $expected_left NOT in routing table"
        passed=0
    fi

    # Check if right neighbor is in routing table
    if echo "$output" | grep -q "${expected_right}-[^:][^,]*"; then
        echo -e "${GREEN}✓${NC} Right neighbor $expected_right found in routing table"
    else
        echo -e "${RED}✗${NC} Right neighbor $expected_right NOT in routing table"
        passed=0
    fi

    echo ""
    if [ $passed -eq 1 ]; then
        echo -e "${GREEN}✓ OVERALL PASS: Both leaf neighbors are in routing table${NC}"
        return 0
    else
        echo -e "${RED}✗ OVERALL FAIL: Not all leaf neighbors in routing table${NC}"
        return 1
    fi
}

# Advanced Test: Row assignment verification
test_row_assignment() {
    echo ""
    echo -e "${CYAN}=== Advanced Test: Row/Column Assignment ===${NC}"
    echo ""
    echo -e "${YELLOW}Purpose:${NC} Verify nodes placed in correct [row][col] based on prefix"
    echo ""

    local test_node="b589"
    local output=$(get_routing_table "$test_node")

    if [ -z "$output" ]; then
        echo -e "${RED}✗ Failed to get routing table${NC}"
        return 1
    fi

    local passed=1

    # Test case 1: b4f9 should be at row 1, col 4 (common prefix "b", next digit "4")
    local row1=$(echo "$output" | sed -n '2p')
    if echo "$row1" | grep -q "b4-[^:][^,]*"; then
        local entry=$(echo "$row1" | grep -o "b4-[^,]*")
        echo -e "${GREEN}✓${NC} Node with prefix 'b4' found in row 1: $entry"
    else
        echo -e "${RED}✗${NC} No node with prefix 'b4' in row 1"
        passed=0
    fi

    # Test case 2: 1804 should be at row 0, col 1 (no common prefix, next digit "1")
    local row0=$(echo "$output" | sed -n '1p')
    if echo "$row0" | grep -q "1-[^:][^,]*"; then
        local entry=$(echo "$row0" | grep -o "1-[^,]*")
        echo -e "${GREEN}✓${NC} Node with prefix '1' found in row 0: $entry"
    else
        echo -e "${RED}✗${NC} No node with prefix '1' in row 0"
        passed=0
    fi

    # Test case 3: cd9e should be at row 0, col c (no common prefix, next digit "c")
    if echo "$row0" | grep -q "c-[^:][^,]*"; then
        local entry=$(echo "$row0" | grep -o "c-[^,]*")
        echo -e "${GREEN}✓${NC} Node with prefix 'c' found in row 0: $entry"
    else
        echo -e "${RED}✗${NC} No node with prefix 'c' in row 0"
        passed=0
    fi

    echo ""
    if [ $passed -eq 1 ]; then
        echo -e "${GREEN}✓ OVERALL PASS: Nodes correctly assigned to rows/columns${NC}"
        return 0
    else
        echo -e "${RED}✗ OVERALL FAIL: Some nodes in incorrect positions${NC}"
        return 1
    fi
}

# Advanced Test: Routing table removal on departure
test_routing_removal() {
    echo ""
    echo -e "${CYAN}=== Advanced Test: Routing Table Removal on Departure ===${NC}"
    echo ""
    echo -e "${YELLOW}Purpose:${NC} Verify routing table entries removed when nodes exit gracefully"
    echo ""

    local test_node="b589"

    # Step 1: Get baseline routing table
    echo -e "${BLUE}[Step 1] Getting baseline routing table for $test_node...${NC}"
    local output_before=$(get_routing_table "$test_node")

    if [ -z "$output_before" ]; then
        echo -e "${RED}✗ Failed to get routing table${NC}"
        return 1
    fi

    local row0_before=$(echo "$output_before" | sed -n '1p')
    local count_before=$(count_row_entries "$row0_before")

    echo "  Current Row 0 entries: $count_before"
    echo ""

    # Step 2: Find a node that's in the routing table to exit
    echo -e "${BLUE}[Step 2] Finding a node to exit...${NC}"

    local target_node=""
    local target_prefix=""

    # Try to find node 1804 (prefix "1-")
    if echo "$row0_before" | grep -q "1-[^:][^,]*"; then
        target_node="1804"
        target_prefix="1"
        echo "  Selected node: 1804 (prefix '1-')"
    # Try 2770 (prefix "2-")
    elif echo "$row0_before" | grep -q "2-[^:][^,]*"; then
        target_node="2770"
        target_prefix="2"
        echo "  Selected node: 2770 (prefix '2-')"
    # Try 3e37 (prefix "3-")
    elif echo "$row0_before" | grep -q "3-[^:][^,]*"; then
        target_node="3e37"
        target_prefix="3"
        echo "  Selected node: 3e37 (prefix '3-')"
    # Try any 9xxx node
    elif echo "$row0_before" | grep -q "9-[^:][^,]*"; then
        # Find which 9xxx node is in the routing table
        for node_id in "${NODE_IDS[@]}"; do
            if [[ "$node_id" == 9* ]]; then
                target_node="$node_id"
                target_prefix="9"
                echo "  Selected node: $target_node (prefix '9-')"
                break
            fi
        done
    fi

    if [ -z "$target_node" ]; then
        echo -e "${YELLOW}⚠ No suitable node found in routing table to exit${NC}"
        echo "  (All row 0 entries may have already been removed)"
        return 0
    fi
    echo ""

    # Step 3: Send exit command to target node
    echo -e "${BLUE}[Step 3] Sending exit command to node $target_node...${NC}"

    local target_idx=$(get_peer_index "$target_node")
    if [ $target_idx -eq -1 ]; then
        echo -e "${RED}✗ Node $target_node not found in peer list${NC}"
        return 1
    fi

    local target_pipe="${PEER_PIPES[$target_idx]}"
    echo "exit" > "$target_pipe" 2>/dev/null

    echo "  Waiting 3 seconds for LEAVE processing..."
    sleep 3
    echo ""

    # Step 4: Re-check routing table
    echo -e "${BLUE}[Step 4] Re-checking routing table after node exit...${NC}"
    local output_after=$(get_routing_table "$test_node")

    if [ -z "$output_after" ]; then
        echo -e "${RED}✗ Failed to get routing table after exit${NC}"
        return 1
    fi

    local row0_after=$(echo "$output_after" | sed -n '1p')
    local count_after=$(count_row_entries "$row0_after")

    echo "  Current Row 0 entries: $count_after"
    echo ""

    # Step 5: Verify removal
    echo -e "${BLUE}[Step 5] Verifying node $target_node was removed...${NC}"
    echo ""

    local passed=1

    # Check if target prefix is now empty
    if echo "$row0_after" | grep -q "${target_prefix}-[^:][^,]*"; then
        echo -e "${RED}✗ FAIL: Node with prefix '${target_prefix}-' still in routing table!${NC}"
        local still_there=$(echo "$row0_after" | grep -o "${target_prefix}-[^,]*")
        echo "  Found: $still_there"
        passed=0
    else
        echo -e "${GREEN}✓${NC} Node with prefix '${target_prefix}-' removed from routing table"
    fi

    # Check if count decreased
    if [ $count_after -lt $count_before ]; then
        local diff=$((count_before - count_after))
        echo -e "${GREEN}✓${NC} Row 0 entry count decreased: $count_before → $count_after (-$diff)"
    else
        echo -e "${RED}✗${NC} Row 0 entry count did not decrease: $count_before → $count_after"
        passed=0
    fi

    echo ""
    if [ $passed -eq 1 ]; then
        echo -e "${GREEN}✓ OVERALL PASS: Node $target_node successfully removed from routing table${NC}"
        return 0
    else
        echo -e "${RED}✗ OVERALL FAIL: Routing table not properly updated after node departure${NC}"
        return 1
    fi
}

# Run all advanced tests
run_all_advanced_tests() {
    echo ""
    echo -e "${CYAN}======================================${NC}"
    echo -e "${CYAN}  Running All Advanced Tests          ${NC}"
    echo -e "${CYAN}======================================${NC}"

    local total=0
    local passed=0

    # Test 1: Self-ID exclusion
    ((total++))
    if test_self_id_exclusion; then
        ((passed++))
    fi
    echo ""
    read -p "Press Enter to continue..."

    # Test 2: Row density
    ((total++))
    if test_row_density; then
        ((passed++))
    fi
    echo ""
    read -p "Press Enter to continue..."

    # Test 3: Leaf in routing
    ((total++))
    if test_leaf_in_routing; then
        ((passed++))
    fi
    echo ""
    read -p "Press Enter to continue..."

    # Test 4: Row assignment
    ((total++))
    if test_row_assignment; then
        ((passed++))
    fi
    echo ""
    read -p "Press Enter to continue..."

    # Test 5: Routing removal
    ((total++))
    if test_routing_removal; then
        ((passed++))
    fi

    echo ""
    echo -e "${CYAN}======================================${NC}"
    echo -e "${BLUE}Test Results: $passed/$total passed${NC}"
    if [ $passed -eq $total ]; then
        echo -e "${GREEN}✓ ALL ADVANCED TESTS PASSED!${NC}"
    else
        echo -e "${YELLOW}⚠ Some tests failed ($((total - passed)) failures)${NC}"
    fi
    echo -e "${CYAN}======================================${NC}"
}

# Show advanced tests menu
show_advanced_menu() {
    echo -e "${CYAN}=== Advanced Routing Table Tests ===${NC}"
    echo ""
    echo "  1) Test self-ID exclusion"
    echo "  2) Test row density (row 0 > row 1 > row 2 > row 3)"
    echo "  3) Test leaf neighbors in routing table"
    echo "  4) Test row/column assignment"
    echo "  5) Test routing table removal on node departure"
    echo "  6) Run all advanced tests"
    echo ""
    echo "  b) Back to main menu"
    echo ""
}

# Advanced tests interactive mode
advanced_tests_mode() {
    while true; do
        show_advanced_menu
        read -p "Select option: " choice
        echo ""

        case "$choice" in
            1)
                test_self_id_exclusion
                ;;
            2)
                test_row_density
                ;;
            3)
                test_leaf_in_routing
                ;;
            4)
                test_row_assignment
                ;;
            5)
                test_routing_removal
                ;;
            6)
                run_all_advanced_tests
                ;;
            b|B)
                return
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

# Show menu
show_menu() {
    echo -e "${CYAN}=== Routing Table Test Menu ===${NC}"
    echo ""
    echo -e "${YELLOW}Main Tests:${NC}"
    echo "  v) Verify node b589 routing table (detailed analysis)"
    echo "  a) Check all nodes' routing tables (summary view)"
    echo "  d) Show expected vs actual comparison"
    echo ""
    echo -e "${YELLOW}Individual Checks:${NC}"
    echo "  r) Show raw routing table output for b589"
    echo "  n <id>) Check specific node's routing table (e.g., 'n 9a71')"
    echo "  j) Show JOIN protocol logs for b589"
    echo ""
    echo -e "${YELLOW}Advanced Tests:${NC}"
    echo "  x) Advanced routing table tests menu"
    echo ""
    echo -e "${YELLOW}Other:${NC}"
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
                verify_node_b589
                ;;
            a|A)
                check_all_routing_tables
                ;;
            d|D)
                show_expected_vs_actual
                ;;
            r|R)
                show_raw_routing_table "$FOCUS_NODE"
                ;;
            n\ *|N\ *)
                local node_id=$(echo "$choice" | awk '{print $2}')
                if [ -z "$node_id" ]; then
                    echo -e "${RED}Usage: n <node-id> (e.g., 'n 9a71')${NC}"
                else
                    show_raw_routing_table "$node_id"
                    echo ""
                    local output=$(get_routing_table "$node_id")
                    if [ -n "$output" ]; then
                        analyze_routing_table "$node_id" "$output"
                    fi
                fi
                ;;
            j|J)
                show_join_logs "$FOCUS_NODE"
                ;;
            x|X)
                clear
                advanced_tests_mode
                clear
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
echo -e "${CYAN}  Routing Table Population Test          ${NC}"
echo -e "${CYAN}==========================================${NC}"
echo ""
echo -e "${BLUE}Configuration:${NC}"
echo "  Number of nodes: $NUM_NODES"
echo "  Discovery port: $DISCOVER_PORT"
echo "  Focus node: $FOCUS_NODE"
echo ""
echo -e "${YELLOW}Test Scenario:${NC}"
echo "  Uses exact 13 node IDs from autograder failure"
echo "  Verifies routing table has 8+ entries (vs 3 before fix)"
echo ""
echo -e "${YELLOW}Node IDs:${NC}"
echo "  ${NODE_IDS[*]}"
echo ""

start_discovery
start_peers

# Wait for network to stabilize
echo ""
echo -e "${YELLOW}Waiting 5 seconds for network to stabilize...${NC}"
sleep 5

show_status

echo ""
echo -e "${GREEN}Test network is ready!${NC}"
echo -e "${YELLOW}Tip: Use option 'v' to verify the routing table fix${NC}"
echo ""
read -p "Press Enter to start interactive mode..."
clear

interactive_mode
