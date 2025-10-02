#!/usr/bin/env bash
# test_network.sh - Interactive testing script for HW3 Pastry DHT
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
PEER_IDS=(1000 5000 9000)
PEER_PORTS=(6001 6002 6003)

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

    sleep 1
    echo -e "${GREEN}Test network stopped${NC}"
}

trap cleanup EXIT INT TERM

# Send command to peer via socket
send_command() {
    local host=$1
    local port=$2
    local command=$3

    echo "$command" | nc "$host" "$port" 2>/dev/null || echo -e "${RED}Error: Failed to connect to $host:$port${NC}"
}

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
    java -cp build/classes/java/main csx55.pastry.node.Discover $DISCOVER_PORT &> discover_test.log &
    DISCOVER_PID=$!

    # Wait for "Ready to accept" message in log (max 10 seconds)
    for i in {1..20}; do
        if grep -q "Ready to accept" discover_test.log 2>/dev/null; then
            break
        fi
        sleep 0.5
    done

    # Verify process is still running
    if kill -0 "$DISCOVER_PID" 2>/dev/null; then
        echo -e "${GREEN}✓ Discovery Node started (PID $DISCOVER_PID)${NC}"
        echo -e "  Log: discover_test.log"
    else
        echo -e "${RED}✗ Discovery Node failed to start${NC}"
        cat discover_test.log
        exit 1
    fi
}

# Start peer nodes
start_peers() {
    echo ""
    echo -e "${BLUE}Starting Peer Nodes...${NC}"

    for i in "${!PEER_IDS[@]}"; do
        local peer_id="${PEER_IDS[$i]}"
        local peer_port="${PEER_PORTS[$i]}"
        local log_file="peer_${peer_id}_test.log"

        # Create /tmp/<peer-id> directory
        mkdir -p "/tmp/${peer_id}"

        # Start peer in background
        java -cp build/classes/java/main csx55.pastry.node.Peer localhost $DISCOVER_PORT "$peer_id" &> "$log_file" &
        local pid=$!
        PEER_PIDS+=("$pid")

        sleep 1

        if kill -0 "$pid" 2>/dev/null; then
            echo -e "  ${GREEN}✓ Peer $peer_id started (PID $pid)${NC} -> $log_file"
        else
            echo -e "  ${RED}✗ Peer $peer_id failed to start${NC}"
            cat "$log_file"
        fi

        # Wait between peers to let JOIN protocol complete
        sleep 2
    done

    echo ""
    echo -e "${GREEN}All peers started successfully!${NC}"
}

# Show peer status
show_status() {
    echo ""
    echo -e "${CYAN}=== Network Status ===${NC}"
    echo ""
    echo -e "${BLUE}Discovery Node:${NC} localhost:$DISCOVER_PORT (PID $DISCOVER_PID)"
    echo ""
    echo -e "${BLUE}Peer Nodes:${NC}"
    for i in "${!PEER_IDS[@]}"; do
        local peer_id="${PEER_IDS[$i]}"
        local peer_port="${PEER_PORTS[$i]}"
        local pid="${PEER_PIDS[$i]}"

        if kill -0 "$pid" 2>/dev/null; then
            echo -e "  ${GREEN}✓${NC} Peer $peer_id - localhost:$peer_port (PID $pid)"
        else
            echo -e "  ${RED}✗${NC} Peer $peer_id - STOPPED"
        fi
    done
    echo ""
}

# Show menu
show_menu() {
    echo -e "${CYAN}=== Test Menu ===${NC}"
    echo ""
    echo -e "${YELLOW}Peer Commands:${NC}"
    echo "  1) Check leaf-set for peer 1000"
    echo "  2) Check leaf-set for peer 5000"
    echo "  3) Check leaf-set for peer 9000"
    echo "  4) Check routing-table for peer 1000"
    echo "  5) Check routing-table for peer 5000"
    echo "  6) Check routing-table for peer 9000"
    echo ""
    echo -e "${YELLOW}Discovery Commands:${NC}"
    echo "  7) List all registered nodes"
    echo ""
    echo -e "${YELLOW}Logs:${NC}"
    echo "  8) Tail Discovery log"
    echo "  9) Tail Peer 1000 log"
    echo "  10) Tail Peer 5000 log"
    echo "  11) Tail Peer 9000 log"
    echo ""
    echo -e "${YELLOW}Other:${NC}"
    echo "  s) Show network status"
    echo "  r) Restart network"
    echo "  q) Quit"
    echo ""
}

# Get peer port by ID
get_peer_port() {
    local peer_id=$1
    for i in "${!PEER_IDS[@]}"; do
        if [ "${PEER_IDS[$i]}" = "$peer_id" ]; then
            echo "${PEER_PORTS[$i]}"
            return
        fi
    done
}

# Tail log file
tail_log() {
    local log_file=$1
    echo ""
    echo -e "${CYAN}=== Tailing $log_file (Ctrl+C to stop) ===${NC}"
    echo ""
    tail -f "$log_file" 2>/dev/null || echo -e "${RED}Log file not found: $log_file${NC}"
}

# Main interactive loop
interactive_mode() {
    while true; do
        show_menu
        read -p "Select option: " choice
        echo ""

        case "$choice" in
            1)
                echo -e "${BLUE}Checking leaf-set for peer 1000...${NC}"
                # For now, we need to manually check logs since peers don't accept socket commands
                echo -e "${YELLOW}Check peer_1000_test.log for leaf-set output${NC}"
                tail -20 peer_1000_test.log | grep -A 10 "leaf" || echo "No leaf-set data found"
                ;;
            2)
                echo -e "${BLUE}Checking leaf-set for peer 5000...${NC}"
                echo -e "${YELLOW}Check peer_5000_test.log for leaf-set output${NC}"
                tail -20 peer_5000_test.log | grep -A 10 "leaf" || echo "No leaf-set data found"
                ;;
            3)
                echo -e "${BLUE}Checking leaf-set for peer 9000...${NC}"
                echo -e "${YELLOW}Check peer_9000_test.log for leaf-set output${NC}"
                tail -20 peer_9000_test.log | grep -A 10 "leaf" || echo "No leaf-set data found"
                ;;
            4)
                echo -e "${BLUE}Checking routing-table for peer 1000...${NC}"
                echo -e "${YELLOW}Check peer_1000_test.log for routing-table output${NC}"
                tail -30 peer_1000_test.log | grep -A 20 "Routing" || echo "No routing-table data found"
                ;;
            5)
                echo -e "${BLUE}Checking routing-table for peer 5000...${NC}"
                echo -e "${YELLOW}Check peer_5000_test.log for routing-table output${NC}"
                tail -30 peer_5000_test.log | grep -A 20 "Routing" || echo "No routing-table data found"
                ;;
            6)
                echo -e "${BLUE}Checking routing-table for peer 9000...${NC}"
                echo -e "${YELLOW}Check peer_9009_test.log for routing-table output${NC}"
                tail -30 peer_9000_test.log | grep -A 20 "Routing" || echo "No routing-table data found"
                ;;
            7)
                echo -e "${BLUE}Registered nodes in Discovery:${NC}"
                tail -50 discover_test.log | grep "Registered node\|registered nodes" || echo "No registration data found"
                ;;
            8)
                tail_log "discover_test.log"
                ;;
            9)
                tail_log "peer_1000_test.log"
                ;;
            10)
                tail_log "peer_5000_test.log"
                ;;
            11)
                tail_log "peer_9000_test.log"
                ;;
            s|S)
                show_status
                ;;
            r|R)
                echo -e "${YELLOW}Restarting network...${NC}"
                cleanup
                sleep 2
                start_discovery
                start_peers
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
echo -e "${CYAN}=====================================${NC}"
echo -e "${CYAN}  CS555 HW3 - Pastry Network Tester  ${NC}"
echo -e "${CYAN}=====================================${NC}"
echo ""

start_discovery
start_peers
show_status

echo ""
echo -e "${GREEN}Test network is ready!${NC}"
echo ""
read -p "Press Enter to start interactive mode..."
clear

interactive_mode
