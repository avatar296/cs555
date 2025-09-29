#!/bin/bash
# check_stats.sh - Verify compute node task counts match Registry summary

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== HW2 Stats Verification ===${NC}"
echo ""

# First, extract the Registry summary table
echo -e "${YELLOW}Registry Summary Table:${NC}"
if [ -f registry.log ]; then
    grep -E "^127\.0\.0\.1:[0-9]+" registry.log | while read line; do
        echo "  $line"
    done
    echo ""
else
    echo -e "${RED}registry.log not found!${NC}"
    exit 1
fi

# Extract node IDs and their mapping to log files
echo -e "${YELLOW}Mapping Compute Nodes to IDs:${NC}"
declare -a node_ids
declare -a log_files
i=0

for log in compute_node_*.log; do
    if [ -f "$log" ]; then
        # Extract node ID (first occurrence of 127.0.0.1:port pattern)
        node_id=$(grep -oE "127\.0\.0\.1:[0-9]+" "$log" 2>/dev/null | head -1)

        if [ -n "$node_id" ]; then
            node_ids[$i]=$node_id
            log_files[$i]=$log
            echo "  $log -> $node_id"
            i=$((i+1))
        fi
    fi
done
echo ""

# Count actual tasks completed by each node
echo -e "${YELLOW}Actual Task Counts from Compute Nodes:${NC}"
echo "(Counting task output lines)"
echo ""

for idx in "${!node_ids[@]}"; do
    node_id="${node_ids[$idx]}"
    log_file="${log_files[$idx]}"

    # Count tasks (lines with the task output format: ip:port:round:payload:timestamp:threadId:nonce)
    # The format is: 127.0.0.1:port:round:payload:timestamp:threadId:nonce
    task_count=$(grep -E "^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+:[0-9]+:" "$log_file" 2>/dev/null | wc -l | tr -d ' ')

    # Get the Registry reported count for this node
    registry_line=$(grep "^$node_id " registry.log 2>/dev/null)
    if [ -n "$registry_line" ]; then
        # Extract the completed count (5th field)
        registry_completed=$(echo "$registry_line" | awk '{print $5}')

        # Compare
        if [ "$task_count" -eq "$registry_completed" ]; then
            echo -e "${GREEN}✓${NC} $node_id: Actual=$task_count, Registry=$registry_completed ${GREEN}MATCH${NC}"
        else
            echo -e "${RED}✗${NC} $node_id: Actual=$task_count, Registry=$registry_completed ${RED}MISMATCH${NC}"
        fi
    else
        echo -e "${YELLOW}?${NC} $node_id: Actual=$task_count, Registry=NOT FOUND"
    fi
done

echo ""
echo -e "${YELLOW}Summary Verification:${NC}"

# Calculate totals
total_actual=0
for idx in "${!node_ids[@]}"; do
    log_file="${log_files[$idx]}"
    count=$(grep -E "^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+:[0-9]+:" "$log_file" 2>/dev/null | wc -l | tr -d ' ')
    total_actual=$((total_actual + count))
done

# Get Registry total
registry_total=$(grep "^Total " registry.log 2>/dev/null | awk '{print $5}')

echo "  Total Actual Tasks: $total_actual"
echo "  Registry Total: $registry_total"

if [ "$total_actual" -eq "$registry_total" ]; then
    echo -e "  ${GREEN}✓ Totals MATCH${NC}"
else
    echo -e "  ${RED}✗ Totals MISMATCH (difference: $((registry_total - total_actual)))${NC}"
fi

echo ""
echo -e "${YELLOW}Conservation Law Check:${NC}"
# Check if Generated + Pulled - Pushed = Completed for each node
grep -E "^127\.0\.0\.1:[0-9]+" registry.log | while read line; do
    node_id=$(echo "$line" | awk '{print $1}')
    generated=$(echo "$line" | awk '{print $2}')
    pulled=$(echo "$line" | awk '{print $3}')
    pushed=$(echo "$line" | awk '{print $4}')
    completed=$(echo "$line" | awk '{print $5}')

    expected=$((generated + pulled - pushed))

    if [ "$expected" -eq "$completed" ]; then
        echo -e "  ${GREEN}✓${NC} $node_id: $generated + $pulled - $pushed = $expected (matches $completed)"
    else
        echo -e "  ${RED}✗${NC} $node_id: $generated + $pulled - $pushed = $expected (should be $completed)"
    fi
done