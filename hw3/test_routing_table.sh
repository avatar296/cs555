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

    # Expected entries for node b589
    local expected_row0=8
    local expected_row1=1
    local expected_total=8

    echo -e "${BLUE}Row 0 (1st digit ≠ 'b'):${NC}"
    echo -e "  Expected: ${expected_row0}+ entries (1-, 2-, 3-, 4-, 6-, 9-, c-, d-)"
    echo -e "  Found: $count0 entries"

    if [ $count0 -ge $expected_row0 ]; then
        echo -e "  ${GREEN}✓ PASS${NC}"
    else
        echo -e "  ${RED}✗ FAIL (missing $((expected_row0 - count0)) entries)${NC}"
    fi

    # Show which entries are present
    echo -e "  Present entries:"
    for prefix in 1 2 3 4 6 9 c d; do
        if echo "$row0" | grep -q "${prefix}-[^:][^,]*"; then
            local entry=$(echo "$row0" | grep -o "${prefix}-[^,]*" | head -1)
            echo -e "    ${GREEN}✓${NC} $entry"
        else
            echo -e "    ${RED}✗${NC} ${prefix}- (missing)"
        fi
    done
    echo ""

    echo -e "${BLUE}Row 1 (b + 2nd digit ≠ '5'):${NC}"
    echo -e "  Expected: ${expected_row1}+ entries (b4-)"
    echo -e "  Found: $count1 entries"

    if [ $count1 -ge $expected_row1 ]; then
        echo -e "  ${GREEN}✓ PASS${NC}"
    else
        echo -e "  ${RED}✗ FAIL${NC}"
    fi

    # Show row 1 entries
    if [ $count1 -gt 0 ]; then
        echo -e "  Present entries:"
        echo "$row1" | grep -o 'b[0-9a-f]-[^:][^,]*' | while read entry; do
            echo -e "    ${GREEN}✓${NC} $entry"
        done
    fi
    echo ""

    echo -e "${BLUE}Row 2 & Row 3:${NC}"
    echo -e "  Row 2: $count2 entries"
    echo -e "  Row 3: $count3 entries"
    echo ""

    echo -e "${CYAN}========================================${NC}"
    echo -e "${BLUE}Total Entries: $total${NC}"

    if [ $total -ge $expected_total ]; then
        echo -e "${GREEN}✓ OVERALL PASS - Node $peer_id has $total routing table entries (expected ${expected_total}+)${NC}"
        return 0
    else
        echo -e "${RED}✗ OVERALL FAIL - Node $peer_id has only $total entries (expected ${expected_total}+)${NC}"
        echo -e "${RED}This matches the autograder failure!${NC}"
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
