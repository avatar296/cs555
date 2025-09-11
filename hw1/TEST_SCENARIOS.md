# CS555 HW1 Test Scenarios - Complete Testing Guide

## Overview
This document provides comprehensive test scenarios for validating the CS555 HW1 Distributed Systems overlay network implementation. Each scenario tests specific requirements from the specification.

## Setup Instructions

### Prerequisites
1. Build the project: `./gradlew clean build`
2. Clean any existing processes: `./cleanup.sh`
3. Have two terminal windows ready (one for Registry, one for nodes)

### Basic Testing Pattern
```bash
# Terminal 1 - Registry
./start_registry.sh

# Terminal 2 - Nodes
./start_nodes.sh [number_of_nodes]

# Back to Terminal 1 - Commands
# Run registry commands as specified in each test
```

---

## Test Scenario 1: Node Registration and Minimum Requirements

### 1.1 Test with Less Than 10 Nodes
**Purpose:** Verify system enforces minimum 10 nodes requirement

**Steps:**
```bash
# Terminal 1
./start_registry.sh

# Terminal 2 - Start only 8 nodes
for i in {1..8}; do
  java -cp build/classes/java/main csx55.overlay.node.MessagingNode 127.0.0.1 5555 &> node_$i.log &
done

# Terminal 1
list-messaging-nodes
setup-overlay 4
```

**Expected Result:**
- `list-messaging-nodes` shows only 8 nodes
- `setup-overlay` should fail or warn about insufficient nodes
- System should enforce minimum 10 nodes for overlay

### 1.2 Test with Exactly 10 Nodes
**Purpose:** Verify system works with minimum required nodes

**Steps:**
```bash
./cleanup.sh
./start_registry.sh  # Terminal 1
./start_nodes.sh 10   # Terminal 2

# Terminal 1
list-messaging-nodes
```

**Expected Result:**
- Shows exactly 10 nodes with format: `127.0.0.1:PORT`
- Each node has unique port number
- All nodes successfully registered

### 1.3 Test with More Than 10 Nodes (15 nodes)
**Purpose:** Verify system scales beyond minimum

**Steps:**
```bash
./cleanup.sh
./start_registry.sh  # Terminal 1
./start_nodes.sh 15  # Terminal 2

# Terminal 1
list-messaging-nodes
setup-overlay 5
```

**Expected Result:**
- Shows all 15 nodes
- Overlay setup succeeds with higher node count

---

## Test Scenario 2: Connection Requirements (CR) Testing

### 2.1 Test CR=2 (Minimum Connectivity)
**Purpose:** Test minimal overlay connectivity

**Steps:**
```bash
./cleanup.sh
./start_registry.sh
./start_nodes.sh 10

# In Registry
setup-overlay 2
send-overlay-link-weights
list-weights
```

**Expected Result:**
- `setup completed with 2 connections`
- Each node has exactly 2 connections
- Network forms connected graph (verify with connectivity check)

### 2.2 Test CR=4 (Standard Configuration)
**Purpose:** Test standard overlay configuration

**Steps:**
```bash
setup-overlay 4
send-overlay-link-weights
```

**Expected Result:**
- `setup completed with 4 connections`
- Each node has exactly 4 connections

### 2.3 Test CR=9 (Maximum for 10 nodes)
**Purpose:** Test maximum possible connections

**Steps:**
```bash
setup-overlay 9
```

**Expected Result:**
- `setup completed with 9 connections`
- Each node connected to all other nodes (complete graph)

### 2.4 Test CR=10 (Impossible Configuration)
**Purpose:** Test error handling for impossible CR

**Steps:**
```bash
setup-overlay 10
```

**Expected Result:**
- Should fail or timeout
- Error message about impossible configuration
- No overlay created

---

## Test Scenario 3: Link Weights Testing

### 3.1 Verify Weight Range (1-10)
**Purpose:** Ensure all weights are within valid range

**Steps:**
```bash
setup-overlay 4
send-overlay-link-weights
list-weights
```

**Expected Result:**
- Output format: `nodeA, nodeB, weight`
- All weights between 1 and 10 inclusive
- Each link listed only once (not duplicated)

### 3.2 Test Weight Distribution
**Purpose:** Verify weights are assigned to all links

**Command Validation:**
```bash
list-weights | awk -F', ' '{print $3}' | sort | uniq -c
```

**Expected Result:**
- Weights should be randomly distributed 1-10
- No weights outside valid range

---

## Test Scenario 4: Message Sending and Statistics

### 4.1 Test Single Round
**Purpose:** Verify basic message sending

**Steps:**
```bash
setup-overlay 4
send-overlay-link-weights
start 1
```

**Expected Result:**
```
127.0.0.1:PORT SENT RECV SENT_SUM.00 RECV_SUM.00 RELAYED
...
sum TOTAL_SENT TOTAL_RECV TOTAL_SENT_SUM.00 TOTAL_RECV_SUM.00
1 rounds completed
```
- Total messages: 10 nodes × 1 round × 5 messages = 50
- Sent count == Received count
- All sums show with .00 decimal format

### 4.2 Test Multiple Rounds (5)
**Purpose:** Standard test configuration

**Steps:**
```bash
start 5
```

**Expected Result:**
- Total messages: 10 × 5 × 5 = 250
- Format matches specification exactly
- Consistency: sent == received

### 4.3 Test Large Round Count (100)
**Purpose:** Stress test and verify scaling

**Steps:**
```bash
start 100
```

**Expected Result:**
- Total messages: 10 × 100 × 5 = 5000
- System handles large message volume
- Statistics remain consistent

### 4.4 Verify Output Format
**Purpose:** Ensure autograder compatibility

**Critical Format Requirements:**
1. Node rows: `IP:PORT SENT RECV SENT_SUM.00 RECV_SUM.00 RELAYED`
2. Sum row: `sum TOTAL_SENT TOTAL_RECV TOTAL_SUM.00 TOTAL_SUM.00`
3. Rounds line: `N rounds completed`
4. NO extra lines within table
5. Consistency check to stderr only

---

## Test Scenario 5: MST (Minimum Spanning Tree) Testing

### 5.1 Test print-mst Command
**Purpose:** Verify MST computation and display

**Steps:**
```bash
# After overlay setup and weights sent
# In a MessagingNode terminal (need to start node interactively)
java -cp build/classes/java/main csx55.overlay.node.MessagingNode 127.0.0.1 5555

# After registration, setup, and weights:
print-mst
```

**Expected Result:**
- Format: `nodeA, nodeB, weight`
- Shows MST edges from node's perspective
- Total weight should be minimal

### 5.2 Test MST Before Weights
**Purpose:** Verify MST requires weights

**Steps:**
```bash
# Before send-overlay-link-weights
print-mst
```

**Expected Result:**
- Should show empty or error message
- No MST available without weights

---

## Test Scenario 6: Deregistration Testing

### 6.1 Test exit-overlay Command
**Purpose:** Verify clean node departure

**Steps:**
```bash
# In MessagingNode console
exit-overlay
```

**Expected Result:**
- Node deregisters from Registry
- "exited overlay" confirmation
- Registry updates node count

### 6.2 Test Overlay After Deregistration
**Purpose:** Verify system handles node departures

**Steps:**
```bash
# After one node exits
list-messaging-nodes  # Should show 9 nodes
setup-overlay 4       # Should fail (< 10 nodes)
```

**Expected Result:**
- Cannot setup overlay with < 10 nodes
- System maintains minimum requirements

---

## Test Scenario 7: Error Handling

### 7.1 Duplicate Registration
**Purpose:** Test port conflict handling

**Steps:**
```bash
# Try to start Registry on same port twice
./start_registry.sh 5555
# In another terminal
./start_registry.sh 5555
```

**Expected Result:**
- Second attempt fails with "Port already in use"
- Suggests alternative port

### 7.2 Invalid Commands
**Purpose:** Test command parsing

**Steps:**
```bash
# In Registry
invalid-command
setup-overlay
setup-overlay abc
start
start abc
```

**Expected Result:**
- Invalid commands ignored or error shown
- Missing parameters caught
- Invalid parameters rejected

### 7.3 Out-of-Order Operations
**Purpose:** Test operation sequencing

**Steps:**
```bash
# Before any nodes register
setup-overlay 4

# Before overlay setup
send-overlay-link-weights

# Before weights sent
start 5
```

**Expected Result:**
- Each command fails appropriately
- Clear error messages
- System maintains consistent state

---

## Test Scenario 8: Connectivity Verification

### 8.1 Test Full Connectivity Check
**Purpose:** Verify BFS connectivity validation

**Monitor logs during overlay setup:**
```bash
tail -f registry.log | grep -i "connect"
```

**Expected Result:**
- "Overlay is fully connected" message
- No partitioned networks created
- All nodes reachable from any node

### 8.2 Test Retry Mechanism
**Purpose:** Verify overlay creation retries

**Monitor for:**
- Failed connection attempts
- Retry messages
- Final success confirmation

---

## Test Scenario 9: Performance and Stress Testing

### 9.1 Maximum Nodes (20+)
**Steps:**
```bash
./start_nodes.sh 20
setup-overlay 6
start 10
```

**Expected Result:**
- System handles larger networks
- Statistics scale properly
- Performance remains acceptable

### 9.2 Rapid Command Execution
**Purpose:** Test command queuing

**Steps:**
```bash
# Execute rapidly
list-messaging-nodes; setup-overlay 4; send-overlay-link-weights; start 5
```

**Expected Result:**
- Commands execute in order
- No race conditions
- Consistent output

---

## Validation Checklist

### Output Format Validation
- [ ] Node statistics show with .00 decimal format for sums
- [ ] Sum line shows with .00 decimal format
- [ ] No extra lines within statistics table
- [ ] "rounds completed" appears after table
- [ ] Consistency check only to stderr

### Functional Validation
- [ ] Minimum 10 nodes enforced
- [ ] CR properly applied (each node has CR connections)
- [ ] Weights in 1-10 range
- [ ] Message counts: nodes × rounds × 5
- [ ] Sent count == Received count
- [ ] Sent sum == Received sum
- [ ] MST computed correctly
- [ ] Deregistration works cleanly

### Error Handling Validation
- [ ] Port conflicts detected
- [ ] Invalid commands handled
- [ ] Out-of-order operations prevented
- [ ] Network partitions avoided
- [ ] Proper error messages displayed

---

## Troubleshooting Guide

### Common Issues and Solutions

1. **Nodes not registering:**
   - Ensure Registry started first
   - Check port availability
   - Verify network connectivity

2. **Overlay setup fails:**
   - Verify minimum 10 nodes registered
   - Check CR is valid (< number of nodes)
   - Ensure no network issues

3. **Statistics mismatch:**
   - Check for node failures during sending
   - Verify routing algorithm
   - Check for message loss

4. **Format issues:**
   - Ensure %.2f format for sums
   - Remove extra print statements
   - Check line endings

5. **MST not displaying:**
   - Ensure weights sent first
   - Verify node has received weights
   - Check MST computation logic

---

## Automated Test Script

Save as `run_all_tests.sh`:

```bash
#!/bin/bash
set -e

echo "CS555 HW1 - Automated Test Suite"
echo "================================="

# Test 1: Basic 10-node setup
echo "Test 1: Basic Setup (10 nodes, CR=4, 5 rounds)"
./cleanup.sh
./start_registry.sh &
REG_PID=$!
sleep 2
./start_nodes.sh 10
sleep 3

# Send commands to registry
echo -e "list-messaging-nodes\nsetup-overlay 4\nsend-overlay-link-weights\nstart 5" | nc 127.0.0.1 5555

sleep 10
kill $REG_PID
./cleanup.sh

echo "Test 1 Complete"
echo ""

# Add more automated tests as needed
```

---

## Summary

This comprehensive test suite covers:
- All required functionality from specification
- Edge cases and error conditions
- Performance and scaling scenarios
- Output format compliance
- Autograder compatibility

Execute these tests systematically to ensure full compliance with CS555 HW1 requirements.