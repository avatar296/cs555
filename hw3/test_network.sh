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
PEER_IDS=()
NUM_PEERS=${1:-3}  # Default to 3 peers if not specified

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
    echo -e "${BLUE}Starting $NUM_PEERS Peer Nodes...${NC}"

    for i in $(seq 1 "$NUM_PEERS"); do
        # Generate random 16-bit hex ID (4 hex digits)
        local peer_id=$(openssl rand -hex 2)
        PEER_IDS+=("$peer_id")

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
    echo -e "${GREEN}All $NUM_PEERS peers started successfully!${NC}"
}

# Show peer status
show_status() {
    echo ""
    echo -e "${CYAN}=== Network Status ===${NC}"
    echo ""
    echo -e "${BLUE}Discovery Node:${NC} localhost:$DISCOVER_PORT (PID $DISCOVER_PID)"
    echo ""
    echo -e "${BLUE}Peer Nodes ($NUM_PEERS):${NC}"
    for i in "${!PEER_IDS[@]}"; do
        local peer_id="${PEER_IDS[$i]}"
        local pid="${PEER_PIDS[$i]}"

        if kill -0 "$pid" 2>/dev/null; then
            echo -e "  ${GREEN}✓${NC} Peer $peer_id (PID $pid)"
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
    local option_num=1

    # Leaf-set options
    for peer_id in "${PEER_IDS[@]}"; do
        echo "  $option_num) Check leaf-set for peer $peer_id"
        ((option_num++))
    done

    echo ""

    # Routing-table options
    for peer_id in "${PEER_IDS[@]}"; do
        echo "  $option_num) Check routing-table for peer $peer_id"
        ((option_num++))
    done

    echo ""
    echo -e "${YELLOW}Discovery Commands:${NC}"
    echo "  d) List all registered nodes"
    echo ""
    echo -e "${YELLOW}Logs:${NC}"
    echo "  ld) Tail Discovery log"

    # Log options for each peer
    for i in "${!PEER_IDS[@]}"; do
        local peer_id="${PEER_IDS[$i]}"
        echo "  l$i) Tail Peer $peer_id log"
    done

    echo ""
    echo -e "${YELLOW}Other:${NC}"
    echo "  s) Show network status"
    echo "  r) Restart network"
    echo "  q) Quit"
    echo ""
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

        # Handle numeric options
        if [[ "$choice" =~ ^[0-9]+$ ]]; then
            local total_peers=${#PEER_IDS[@]}

            # Check if it's a leaf-set option (1 to NUM_PEERS)
            if [ "$choice" -ge 1 ] && [ "$choice" -le "$total_peers" ]; then
                local idx=$((choice - 1))
                local peer_id="${PEER_IDS[$idx]}"
                local log_file="peer_${peer_id}_test.log"

                echo -e "${BLUE}Checking leaf-set for peer $peer_id...${NC}"
                echo -e "${YELLOW}Log file: $log_file${NC}"
                tail -20 "$log_file" | grep -A 10 "leaf" || echo "No leaf-set data found"

            # Check if it's a routing-table option (NUM_PEERS+1 to 2*NUM_PEERS)
            elif [ "$choice" -ge $((total_peers + 1)) ] && [ "$choice" -le $((total_peers * 2)) ]; then
                local idx=$((choice - total_peers - 1))
                local peer_id="${PEER_IDS[$idx]}"
                local log_file="peer_${peer_id}_test.log"

                echo -e "${BLUE}Checking routing-table for peer $peer_id...${NC}"
                echo -e "${YELLOW}Log file: $log_file${NC}"
                tail -30 "$log_file" | grep -A 20 "Routing" || echo "No routing-table data found"
            else
                echo -e "${RED}Invalid option: $choice${NC}"
            fi
        else
            # Handle letter options
            case "$choice" in
                d|D)
                    echo -e "${BLUE}Registered nodes in Discovery:${NC}"
                    tail -50 discover_test.log | grep "Registered node\|registered nodes" || echo "No registration data found"
                    ;;
                ld|LD)
                    tail_log "discover_test.log"
                    ;;
                l[0-9]*)
                    # Extract peer index from log option (e.g., l0, l1, l2)
                    local peer_idx="${choice#l}"
                    if [ "$peer_idx" -lt "${#PEER_IDS[@]}" ]; then
                        local peer_id="${PEER_IDS[$peer_idx]}"
                        tail_log "peer_${peer_id}_test.log"
                    else
                        echo -e "${RED}Invalid peer index: $peer_idx${NC}"
                    fi
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
        fi

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
echo -e "${BLUE}Configuration:${NC}"
echo "  Number of peers: $NUM_PEERS"
echo "  Discovery port: $DISCOVER_PORT"
echo ""
echo -e "${YELLOW}Usage: $0 [NUM_PEERS]${NC}"
echo "  Default: 3 peers"
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
