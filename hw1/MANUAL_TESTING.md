# Manual Testing Scenarios for CS555 HW1 Overlay Network

## Overview
This file provides step-by-step testing scenarios to validate overlay network functionality and catch autograder issues before submission.

## Critical Issues to Address

Based on your autograder feedback, focus on:

1. **macOS Metadata Files** - Remove all `._*` files from submissions
2. **Output Format Compliance** - Exact output text matching
3. **Edge Count Logic** - Correct link counting in `list-weights`
4. **Missing Node Information** - Include all required IP addresses
5. **Code Formatting** - Spotless compliance

## Pre-Test Setup

### Environment Preparation
```bash
# Clean up macOS metadata files (CRITICAL)
find . -name '._*' -delete
find . -name '.DS_Store' -delete

# Verify cleanup (should return nothing)
find . -name '._*' -o -name '.DS_Store'

# Format code
gradle spotlessApply

# Build project
gradle build

# Start Docker environment
docker-compose down -v
docker-compose up -d
docker-compose ps  # Verify all containers running
```

## Test Scenarios

### Scenario 1: Basic Registration and Listing
**Purpose**: Verify node registration and `list-messaging-nodes` output format

**Steps**:
1. **Start Registry** (Terminal 1):
   ```bash
   docker-compose exec registry bash
   java -cp /app csx55.overlay.node.Registry 8080
   ```

2. **Start 3 MessagingNodes** (Terminals 2-4):
   ```bash
   # Terminal 2
   docker-compose exec node-1 bash
   java -cp /app csx55.overlay.node.MessagingNode registry 8080
   
   # Terminal 3
   docker-compose exec node-2 bash
   java -cp /app csx55.overlay.node.MessagingNode registry 8080
   
   # Terminal 4
   docker-compose exec node-3 bash
   java -cp /app csx55.overlay.node.MessagingNode registry 8080
   ```

3. **Test Registry Commands** (Terminal 1):
   ```bash
   list-messaging-nodes
   ```

**Expected Output Format**:
```
172.18.0.3:35432
172.18.0.4:35433
172.18.0.5:35434
```

**Validation Checklist**:
- [ ] Each line contains exactly one IP:port
- [ ] No extra text, logging, or formatting
- [ ] All registered nodes appear
- [ ] Format is `IP:PORT` (no spaces, brackets, etc.)

---

### Scenario 2: Setup Overlay Output Format
**Purpose**: Verify exact output format for `setup-overlay` command

**Prerequisites**: 10 nodes registered (use Scenario 1 but with 10 nodes)

**Steps**:
1. **Register 10 Nodes** (follow Scenario 1 pattern for node-1 through node-10)

2. **Setup Overlay** (Registry terminal):
   ```bash
   setup-overlay 4
   ```

**Expected Output (EXACT)**:
```
setup completed with 20 connections
```

**Critical Requirements**:
- [ ] Must say "setup completed with X connections"
- [ ] For CR=4 with 10 nodes: X = (10 * 4) / 2 = 20
- [ ] No logging prefixes, timestamps, or extra text
- [ ] Case-sensitive exact match

**Debug If Wrong**:
- Check OverlayCreator edge counting logic
- Verify output isn't including logging statements
- Ensure bidirectional edges counted once, not twice

---

### Scenario 3: Link Weights Output Format
**Purpose**: Verify `list-weights` format and edge counting

**Prerequisites**: Scenario 2 completed (overlay setup with 4 connections)

**Steps**:
1. **Send Link Weights** (Registry terminal):
   ```bash
   send-overlay-link-weights
   ```

2. **Expected Output (EXACT)**:
   ```
   link weights assigned
   ```

3. **List Weights** (Registry terminal):
   ```bash
   list-weights
   ```

**Expected Output Format**:
```
172.18.0.3:35432 172.18.0.4:35433 7
172.18.0.3:35432 172.18.0.5:35434 3
172.18.0.4:35433 172.18.0.6:35435 9
...
(exactly 20 lines total)
```

**Critical Requirements**:
- [ ] Exactly 20 edges for CR=4 with 10 nodes
- [ ] Format: `IP1:PORT1 IP2:PORT2 WEIGHT`
- [ ] Each edge appears exactly once (not bidirectional duplicates)
- [ ] Weights between 1-10 inclusive
- [ ] All registered nodes have IP addresses

**Debug If Wrong**:
- Check if showing both (A,B) and (B,A) - should only show one
- Verify weight assignment logic
- Ensure all nodes have proper IP addresses

---

### Scenario 4: MST and Message Exchange
**Purpose**: Test complete functionality including MST computation and messaging

**Prerequisites**: Scenario 3 completed (link weights sent)

**Steps**:
1. **Test MST Display** (any node terminal):
   ```bash
   print-mst
   ```

**Expected Output Format**:
```
172.18.0.3:35432
172.18.0.4:35433 172.18.0.3:35432
172.18.0.5:35434 172.18.0.3:35432
...
```

2. **Start Message Exchange** (Registry terminal):
   ```bash
   start 5
   ```

**Expected Behavior**:
- Each node sends 25 messages (5 rounds × 5 messages per round)
- Registry collects traffic summaries
- Final verification: total sent = total received

**Critical Requirements**:
- [ ] MST shows parent-child relationships
- [ ] All nodes reachable in MST
- [ ] Message statistics are accurate
- [ ] No infinite loops or deadlocks

---

### Scenario 5: Edge Cases and Error Conditions
**Purpose**: Test boundary conditions and error handling

**Test Cases**:

1. **Insufficient Nodes for Overlay**:
   ```bash
   # With only 2 nodes registered
   setup-overlay 4
   ```
   **Expected**: Error message about insufficient nodes

2. **Commands Before Setup**:
   ```bash
   send-overlay-link-weights  # Before setup-overlay
   start 5                    # Before send-overlay-link-weights
   ```
   **Expected**: Appropriate error messages

3. **Node Deregistration**:
   ```bash
   # In a node terminal
   exit-overlay
   ```
   **Expected**: Clean deregistration from registry

---

### Scenario 7: Autograder Validation
**Purpose**: Test exact scenario that matches autograder expectations for "4 connections"

**Setup**: Fresh environment with exactly 4 nodes and CR=2

**Steps**:
1. **Clean Environment**:
   ```bash
   docker-compose down -v
   docker-compose up -d
   ```

2. **Start Registry** (Terminal 1):
   ```bash
   docker-compose exec registry bash
   java -cp /app csx55.overlay.node.Registry 8080
   ```

3. **Register Exactly 4 Nodes** (Terminals 2-5):
   ```bash
   # Terminal 2
   docker-compose exec node-1 bash
   java -cp /app csx55.overlay.node.MessagingNode registry 8080
   
   # Terminal 3
   docker-compose exec node-2 bash
   java -cp /app csx55.overlay.node.MessagingNode registry 8080
   
   # Terminal 4
   docker-compose exec node-3 bash
   java -cp /app csx55.overlay.node.MessagingNode registry 8080
   
   # Terminal 5
   docker-compose exec node-4 bash
   java -cp /app csx55.overlay.node.MessagingNode registry 8080
   ```

4. **Validate Node Registration**:
   ```bash
   list-messaging-nodes  # Should show exactly 4 nodes
   ```

5. **Test Autograder Scenario** (Registry terminal):
   ```bash
   setup-overlay 2
   ```

**Expected Output (EXACT)**:
```
setup completed with 4 connections
```

**Math Validation**:
- 4 nodes × 2 connections each ÷ 2 = 4 total connections ✅

**Critical Success Criteria**:
- [ ] Exactly 4 nodes registered
- [ ] Output format: "setup completed with 4 connections" 
- [ ] No logging prefixes or extra text
- [ ] Connection count matches: 4 nodes × CR=2 ÷ 2 = 4

**Follow-up Testing**:
After successful setup-overlay, test:
```bash
send-overlay-link-weights  # Should show "link weights assigned"
list-weights              # Should show exactly 4 edges
```

**Purpose**: This scenario replicates the exact conditions that caused the September 4th autograder failure, ensuring our fix works for all test cases.

---

### Scenario 8: Large Scale Testing (12-14 Nodes)
**Purpose**: Test scenarios that match September 4th autograder runs with 12-14 nodes

**Background**: Autograder runs showed:
- 14 nodes initially, then 12 nodes (node disconnections)
- "Incorrect number of edges in list-weights output" error
- Need to validate connection counting at larger scale

#### Test 8a: 12 Nodes Scale Test

**Setup**: 12 nodes with CR=4

**Steps**:
1. **Clean Environment**:
   ```bash
   docker-compose down -v
   docker-compose up -d
   ```

2. **Start Registry**:
   ```bash
   docker-compose exec registry bash
   java -cp /app csx55.overlay.node.Registry 8080
   ```

3. **Register 12 Nodes** (use node-1 through node-10, then reuse containers):
   ```bash
   # Standard 10 containers
   docker-compose exec node-1 bash; java -cp /app csx55.overlay.node.MessagingNode registry 8080
   docker-compose exec node-2 bash; java -cp /app csx55.overlay.node.MessagingNode registry 8080
   # ... continue for node-3 through node-10
   
   # Additional 2 nodes (reuse containers with different processes)
   # In separate terminals from same containers
   ```

4. **Validate Registration**:
   ```bash
   list-messaging-nodes  # Should show exactly 12 nodes
   ```

5. **Large Scale Test**:
   ```bash
   setup-overlay 4
   ```

**Expected Output**:
```
setup completed with 24 connections
```

**Math**: 12 nodes × 4 CR ÷ 2 = 24 connections

**Follow-up**:
```bash
send-overlay-link-weights
list-weights  # Should show exactly 24 edges
```

#### Test 8b: 14 Nodes Scale Test

**Setup**: 14 nodes with CR=3

**Expected Output**:
```
setup completed with 21 connections
```

**Math**: 14 nodes × 3 CR ÷ 2 = 21 connections

#### Test 8c: Node Disconnection Scenario

**Purpose**: Test behavior when nodes disconnect between commands

**Steps**:
1. Register 14 nodes
2. Run `list-messaging-nodes` (should show 14)
3. **Simulate disconnection**: Stop 2 node processes
4. Run `list-messaging-nodes` again (should show 12)
5. Test `setup-overlay` with remaining nodes

**Validation Points**:
- [ ] Registry properly detects disconnected nodes
- [ ] Connection counting adjusts for actual node count
- [ ] Edge counting remains accurate after disconnections
- [ ] No stale nodes in overlay calculations

#### Test 8d: Various CR Combinations at Scale

Test multiple combinations to ensure formula works:
- **12 nodes, CR=2** → 12 connections
- **12 nodes, CR=3** → 18 connections  
- **14 nodes, CR=2** → 14 connections
- **14 nodes, CR=4** → 28 connections

**Critical Validation**:
- [ ] Connection counting formula works at all scales
- [ ] Edge count in list-weights matches connection count
- [ ] Output format remains clean with larger datasets
- [ ] No performance issues with 12+ nodes

---

### Scenario 9: Node Disconnection and Stale Data Testing
**Purpose**: Test system behavior when nodes disconnect during overlay operations - directly addresses autograder failure patterns

**Background**: The root cause of autograder failures was nodes disconnecting during setup, leaving stale data in overlay structures, causing connection count inflation (24 vs 4 connections) and missing IP addresses in list-weights.

#### Test 9a: Disconnection Before Setup-Overlay

**Purpose**: Verify that disconnected nodes don't inflate connection counts

**Steps**:
1. **Clean Environment**:
   ```bash
   docker-compose down -v
   docker-compose up -d
   ```

2. **Start Registry** (Terminal 1):
   ```bash
   docker-compose exec registry bash
   java -cp /app csx55.overlay.node.Registry 8080
   ```

3. **Register 6 Nodes** (Terminals 2-7):
   ```bash
   # Start 6 nodes
   docker-compose exec node-1 bash; java -cp /app csx55.overlay.node.MessagingNode registry 8080
   docker-compose exec node-2 bash; java -cp /app csx55.overlay.node.MessagingNode registry 8080
   docker-compose exec node-3 bash; java -cp /app csx55.overlay.node.MessagingNode registry 8080
   docker-compose exec node-4 bash; java -cp /app csx55.overlay.node.MessagingNode registry 8080
   docker-compose exec node-5 bash; java -cp /app csx55.overlay.node.MessagingNode registry 8080
   docker-compose exec node-6 bash; java -cp /app csx55.overlay.node.MessagingNode registry 8080
   ```

4. **Verify Initial Registration**:
   ```bash
   list-messaging-nodes  # Should show 6 nodes
   ```

5. **Simulate Node Disconnections**:
   ```bash
   # In node-5 and node-6 terminals: Ctrl+C or close terminals
   # Wait 5-10 seconds for registry to detect disconnections
   ```

6. **Verify Disconnection Detection**:
   ```bash
   list-messaging-nodes  # Should now show 4 nodes (6 - 2 disconnected)
   ```

7. **Critical Test - Setup Overlay**:
   ```bash
   setup-overlay 2
   ```

**Expected Output**:
```
setup completed with 4 connections
```

**Critical Validation**:
- [ ] Connection count = 4 (4 remaining nodes × 2 CR ÷ 2)
- [ ] NOT 12 connections (6 original nodes × 2 CR ÷ 2) 
- [ ] System uses only currently registered nodes

#### Test 9b: Disconnection After Setup-Overlay

**Purpose**: Test list-weights handling of disconnected nodes

**Steps**:
1. **Continue from Test 9a** or start fresh with 4 nodes registered

2. **Setup Overlay Successfully**:
   ```bash
   setup-overlay 2  # Should show "setup completed with 4 connections"
   ```

3. **Send Link Weights**:
   ```bash
   send-overlay-link-weights  # Should show "link weights assigned"
   ```

4. **Verify Initial List-Weights**:
   ```bash
   list-weights  # Should show exactly 4 edges, all with valid IP addresses
   ```

5. **Simulate Additional Disconnection**:
   ```bash
   # Disconnect 1 more node (e.g., close node-4 terminal)
   # Wait for registry to detect disconnection
   ```

6. **Check Updated Registration**:
   ```bash
   list-messaging-nodes  # Should show 3 nodes
   ```

7. **Test List-Weights With Stale Data**:
   ```bash
   list-weights  # Should filter out edges involving disconnected nodes
   ```

**Expected Behavior**:
- [ ] list-weights shows only edges between currently registered nodes
- [ ] No "Missing IP address" errors
- [ ] Disconnected nodes' links are filtered out
- [ ] No stale or invalid IP:PORT combinations displayed

#### Test 9c: Rebuilding After Disconnections

**Purpose**: Test overlay rebuild functionality after node disconnections

**Steps**:
1. **Start with established overlay** (from previous tests)

2. **Force Overlay Rebuild** (this happens automatically when nodes disconnect):
   ```bash
   # Registry should automatically call rebuildOverlay() when nodes disconnect
   # Check logs for "Rebuilding overlay" messages
   ```

3. **Verify Rebuild Uses Correct Parameters**:
   - Registry should use stored CR value (not derive from stale data)
   - New overlay should only include currently registered nodes

4. **Test Setup With Remaining Nodes**:
   ```bash
   setup-overlay 2  # Should work with remaining nodes
   ```

**Expected Results**:
- [ ] Rebuild uses correct connection requirement (2, not inflated value)
- [ ] New overlay includes only active nodes  
- [ ] Connection count reflects actual node count
- [ ] System recovers gracefully from disconnections

#### Test 9d: Stress Test - Multiple Disconnection Waves

**Purpose**: Test system stability under repeated disconnection scenarios

**Steps**:
1. **Start Large Setup**:
   ```bash
   # Register 8 nodes
   # setup-overlay 3  (should give 12 connections: 8×3÷2)
   ```

2. **Wave 1 Disconnections**:
   ```bash
   # Disconnect 2 nodes → 6 remaining
   # Verify: list-messaging-nodes shows 6
   # Test: setup-overlay 2 → should give 6 connections (6×2÷2)
   ```

3. **Wave 2 Disconnections**:
   ```bash
   # Disconnect 2 more → 4 remaining  
   # Verify: list-messaging-nodes shows 4
   # Test: setup-overlay 2 → should give 4 connections (4×2÷2)
   ```

4. **Final Validation**:
   ```bash
   send-overlay-link-weights
   list-weights  # Should show clean edges for remaining 4 nodes
   ```

**Success Criteria**:
- [ ] Connection counts always match remaining active nodes
- [ ] No connection count inflation from disconnected nodes
- [ ] list-weights never shows "missing IP address" errors
- [ ] System maintains data integrity through multiple disconnection events

### Critical Validation Points

**This scenario validates our key fixes**:

1. **Stored Connection Requirement**: `rebuildOverlay()` uses stored CR instead of deriving from stale adjacency data
2. **Active Node Filtering**: Connection counting only includes currently registered nodes
3. **List-Weights Filtering**: Only shows edges between active nodes
4. **Stale Data Prevention**: No inflation of connection counts due to disconnected nodes

**Expected Fix Validation**:
- ✅ **Connection count accuracy**: Always matches (activeNodes × CR ÷ 2)
- ✅ **No missing IP addresses**: list-weights filters out disconnected nodes
- ✅ **Graceful recovery**: System handles disconnections without data corruption
- ✅ **Autograder compliance**: Prevents "24 vs 4 connections" type failures

---

### Scenario 6: Submission Validation
**Purpose**: Final validation before submission

**Steps**:
1. **Clean Build**:
   ```bash
   gradle clean
   gradle spotlessApply
   gradle build
   ```

2. **Remove Metadata Files**:
   ```bash
   find . -name '._*' -delete
   find . -name '.DS_Store' -delete
   ```

3. **Create Submission**:
   ```bash
   gradle createTar
   ```

4. **Validate Archive**:
   ```bash
   tar -tf build/distributions/Christopher_Cowart_HW1.tar
   ```

**Validation Checklist**:
- [ ] No files starting with `._`
- [ ] No `.DS_Store` files
- [ ] Only `.java`, `build.gradle`, and `README.txt` files
- [ ] No test files included
- [ ] Archive extracts and builds successfully

---

## Quick Testing Protocol

### 10-Minute Validation Run
Use this for rapid validation before submission:

```bash
# 1. Clean environment
docker-compose down -v && docker-compose up -d

# 2. Start registry
docker-compose exec registry bash
java -cp /app csx55.overlay.node.Registry 8080

# 3. Start 3 nodes (in separate terminals)
for i in {1..3}; do
    docker-compose exec node-$i bash
    java -cp /app csx55.overlay.node.MessagingNode registry 8080
done

# 4. Test commands (in registry terminal)
list-messaging-nodes       # Check format
setup-overlay 2            # Should show "setup completed with 3 connections"  
send-overlay-link-weights  # Should show "link weights assigned"
list-weights               # Should show exactly 3 edges
start 2                    # Test messaging

# 5. Test node command (in any node terminal)  
print-mst                  # Check MST format
```

## Debugging Commands

### Container Debugging
```bash
# Check container status
docker-compose ps

# View logs
docker-compose logs registry
docker-compose logs node-1

# Check network connectivity
docker-compose exec node-1 ping registry

# Check processes
docker-compose exec registry ps aux
```

### Application Debugging
```bash
# In registry container - check listening ports
netstat -tlnp | grep 8080

# Check Java process status
ps aux | grep java

# Monitor connections
netstat -an | grep ESTABLISHED
```

## Common Issues and Fixes

### Issue: "bash: list-messaging-nodes: command not found"
**Cause**: Typing commands in shell instead of Java application
**Fix**: Ensure Java application is running first

### Issue: Wrong number of edges
**Cause**: Counting bidirectional edges twice
**Fix**: In list-weights, show each edge only once

### Issue: Missing IP addresses
**Cause**: Node registration not storing IP properly
**Fix**: Verify node IP detection and storage logic

### Issue: Output format mismatch
**Cause**: Including logging statements in command output
**Fix**: Separate logging from command response output

### Issue: Compilation errors
**Cause**: Code formatting issues
**Fix**: Run `gradle spotlessApply` before building

## Output Templates

### Registry Command Outputs
```bash
# list-messaging-nodes
IP:PORT
IP:PORT
...

# setup-overlay CR
setup completed with X connections

# send-overlay-link-weights  
link weights assigned

# list-weights
IP1:PORT1 IP2:PORT2 WEIGHT
IP1:PORT1 IP3:PORT3 WEIGHT
...

# start ROUNDS
[Traffic summary table at completion]
```

### MessagingNode Command Outputs
```bash
# print-mst
ROOT_IP:ROOT_PORT
CHILD1_IP:CHILD1_PORT PARENT_IP:PARENT_PORT  
CHILD2_IP:CHILD2_PORT PARENT_IP:PARENT_PORT
...

# exit-overlay
[Clean deregistration message]
```

## Notes

- **Container IP Addresses**: Docker assigns IPs in 172.18.0.x range
- **Port Numbers**: Nodes auto-select available ports
- **Timing**: Allow time for connections to establish
- **Logging**: Keep application logs separate from command outputs
- **Testing Order**: Always test in the sequence shown (registration → setup → weights → messaging)

## Success Criteria

A successful test run should demonstrate:
1. ✅ All 10+ nodes register successfully
2. ✅ Overlay setup with correct connection count
3. ✅ Link weights assigned and listed correctly
4. ✅ MST computation works for all nodes
5. ✅ Message exchange completes with accurate statistics
6. ✅ Clean submission archive with no metadata files