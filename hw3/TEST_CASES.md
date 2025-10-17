# Pastry DHT Manual Test Cases

## Overview
Comprehensive test scenarios to verify routing table population, prefix-based routing, and edge cases in the Pastry DHT implementation.

---

## Test Category 1: Prefix Matching Priority

### Test 1.1: Basic Prefix vs Distance Conflict
**Scenario:** Node with longer prefix but greater distance should be chosen
**Nodes:** `7eaf`, `85d9`, `8aa6`, `2293`, `da7f`
**Target Hash:** `818e`
**Expected Behavior:**
- Node `85d9` should store the file (prefix='8', length=1, distance=1099)
- NOT node `7eaf` (prefix='', length=0, distance=735 - closer by distance!)
**Verification:**
```bash
# Check routing path stops at 85d9
# Check file is stored at 85d9, not forwarded to 7eaf
```

### Test 1.2: Multiple Prefix Lengths
**Scenario:** Test nodes with 0, 1, 2, 3, 4 character prefix matches
**Nodes:** `0000`, `8000`, `8100`, `8180`, `818e`
**Target Hash:** `818e`
**Expected Behavior:**
- Routing should prefer: `818e` (4) > `8180` (3) > `8100` (2) > `8000` (1) > `0000` (0)
**Verification:**
- Each node should recognize nodes with longer prefixes as closer
- `isClosestNode()` should return false if longer prefix exists in leaf set

### Test 1.3: Equal Prefix, Different Distance
**Scenario:** When prefix lengths are equal, distance should be tiebreaker
**Nodes:** `8000`, `8fff`, `85d9`
**Target Hash:** `8200`
**Expected Behavior:**
- All have prefix='8' (length 1)
- Distance: `8000`→`8200` = 512, `8fff`→`8200` = 28413, `85d9`→`8200` = 919
- Should route to `8000` (closest by distance with same prefix length)

### Test 1.4: No Prefix Match Scenario
**Scenario:** None of the nodes share a prefix with target
**Nodes:** `1111`, `2222`, `3333`, `4444`
**Target Hash:** `aaaa`
**Expected Behavior:**
- Fall back to pure distance-based routing
- Should route to closest by circular distance

### Test 1.5: All Same Prefix
**Scenario:** All nodes share the same prefix with target
**Nodes:** `a000`, `a123`, `a456`, `a789`, `afff`
**Target Hash:** `a5b2`
**Expected Behavior:**
- All have prefix='a' (length 1)
- Should route to `a456` or `a789` (whichever is closer by distance)

---

## Test Category 2: Routing Table Population

### Test 2.1: Small Network (5 nodes)
**Scenario:** Verify routing tables populate in small network
**Nodes:** `0a1b`, `3c4d`, `7e8f`, `b2c3`, `f5e6`
**Expected Behavior:**
- Each node should have 4+ routing table entries (all other nodes)
- Entries should be in correct rows based on prefix matching
**Verification:**
```bash
# Check routing-table command for each node
# Count non-null entries (should be 4 minimum)
```

### Test 2.2: Medium Network (10 nodes)
**Scenario:** Test reciprocal updates scale correctly
**Nodes:** `1000`, `2000`, `3000`, `4000`, `5000`, `6000`, `7000`, `8000`, `9000`, `a000`
**Expected Behavior:**
- Each node should have 8-9 routing table entries
- Routing tables should be symmetric (if A has B, then B has A in appropriate row)
**Verification:**
- Check all routing tables have sufficient entries
- Verify reciprocal relationships

### Test 2.3: Large Network (20 nodes)
**Scenario:** Stress test routing table population
**Nodes:** 20 nodes with IDs `0800`, `1000`, `1800`, ..., `f800` (every 2048)
**Expected Behavior:**
- Each node should have 15+ routing table entries
- No node should have sparse routing table
**Verification:**
- Count entries per node (minimum 15)
- Check for gaps in routing table

### Test 2.4: Sequential Join Order
**Scenario:** Nodes join one at a time, slowly
**Nodes:** `aaaa`, `bbbb`, `cccc`, `dddd` (join with 5 second delays)
**Expected Behavior:**
- Each new node learns from existing nodes
- Existing nodes receive reciprocal updates
- Final routing tables should be fully populated
**Verification:**
- Monitor logs for ROUTING_TABLE_UPDATE messages
- Check reciprocal updates occur

### Test 2.5: Parallel Join
**Scenario:** Multiple nodes join simultaneously
**Nodes:** `1111`, `2222`, `3333`, `4444` (all join within 1 second)
**Expected Behavior:**
- All nodes eventually discover each other
- Routing tables converge to consistent state
- No nodes are "invisible" to others
**Verification:**
- Wait 10 seconds after joins
- Check all nodes have full routing tables

---

## Test Category 3: Leaf Set Management

### Test 3.1: Basic Left/Right Neighbors
**Scenario:** Verify correct immediate neighbors in circular space
**Nodes:** `2000`, `4000`, `6000`, `8000`, `a000`
**Expected Behavior:**
- `6000`: LEFT=`4000`, RIGHT=`8000`
- `2000`: LEFT=`a000` (wrap), RIGHT=`4000`
- `a000`: LEFT=`8000`, RIGHT=`2000` (wrap)
**Verification:**
```bash
# Check leaf-set command for each node
# Verify left/right neighbors are correct
```

### Test 3.2: Circular Space Wrap-Around
**Scenario:** Test nodes near boundaries (0000 and ffff)
**Nodes:** `0100`, `8000`, `ff00`
**Expected Behavior:**
- `0100`: LEFT=`ff00` (wraps around), RIGHT=`8000`
- `ff00`: LEFT=`8000`, RIGHT=`0100` (wraps around)
**Verification:**
- Check leaf sets handle wrap correctly
- Verify distance calculations use min(diff, wrap_diff)

### Test 3.3: Dense Cluster
**Scenario:** Many nodes clustered in small ID range
**Nodes:** `8000`, `8001`, `8002`, `8003`, `8004`
**Expected Behavior:**
- Each node's neighbors should be immediate adjacent nodes
- Leaf sets should update correctly as nodes join
**Verification:**
- Check each node has correct immediate neighbors
- Verify no skipped nodes in leaf sets

### Test 3.4: Sparse Network
**Scenario:** Few nodes spread across entire ID space
**Nodes:** `0000`, `4000`, `c000`
**Expected Behavior:**
- Large distances between neighbors
- Leaf sets should still correctly identify closest nodes
**Verification:**
- Check leaf sets contain actual closest nodes
- Verify no closer nodes are missed

---

## Test Category 4: LOOKUP/STORE Operations

### Test 4.1: Direct Hit
**Scenario:** Target hash exactly matches a node ID
**Nodes:** `1234`, `5678`, `9abc`, `def0`
**Target Hash:** `5678` (exact match)
**Expected Behavior:**
- Routing should go directly to node `5678`
- No further routing after reaching exact match
**Verification:**
- Check routing path includes `5678` as final node
- File stored at `5678`

### Test 4.2: Optimal Routing Path
**Scenario:** Verify routing takes optimal path (not excessive hops)
**Nodes:** `1000`, `2000`, `3000`, `4000`, `5000`, `6000`, `7000`, `8000`
**Target Hash:** `7500`
**Expected Behavior:**
- Routing path should converge quickly (max 3-4 hops for 8 nodes)
- Each hop should increase prefix match length or decrease distance
**Verification:**
- Trace routing path
- Verify each hop is "closer" than previous

### Test 4.3: Multiple Stores with Same Prefix
**Scenario:** Store multiple files with hashes sharing prefixes
**Target Hashes:** `a100`, `a200`, `a300`, `a400`
**Nodes:** `a000`, `a150`, `a250`, `a350`, `a450`, `b000`
**Expected Behavior:**
- Each file routes to node with best prefix+distance match
- Files don't all go to same node unless actually closest
**Verification:**
- Check file distribution across nodes
- Verify each file at correct node

### Test 4.4: Routing Past Closest Node Bug
**Scenario:** Ensure routing stops at closest node (bug we fixed)
**Nodes:** `85d9`, `7eaf`, `8aa6`
**Target Hash:** `818e`
**Expected Behavior:**
- Routing must STOP at `85d9` (prefix match)
- Must NOT continue to `7eaf` (closer by distance but no prefix)
**Verification:**
- Check `85d9.isClosestNode("818e")` returns true
- Verify file stored at `85d9`

### Test 4.5: Autograder Exact Scenario
**Scenario:** Reproduce exact autograder failure scenario
**Nodes:** `2293`, `8aa6`, `85d9`, `7eaf` (+ others from autograder)
**Target Hash:** `818e`
**Expected Routing Path:** `2293` → `8aa6` → `85d9` (STOP)
**Wrong Path (bug):** `2293` → `8aa6` → `85d9` → `7eaf` (incorrect)
**Verification:**
- File must be stored at `85d9`
- Autograder check: "Expected id 85d9 to match first 2 characters of file id 818e" should pass

---

## Test Category 5: Circular Distance Edge Cases

### Test 5.1: Minimum Distance (Adjacent Nodes)
**Scenario:** Nodes with IDs differing by 1
**Nodes:** `8000`, `8001`
**Target Hash:** `8000`
**Expected Behavior:**
- Distance from `8001` to `8000` should be 1
- Routing should choose `8000` (exact match)

### Test 5.2: Maximum Distance (Opposite Side)
**Scenario:** Nodes on opposite sides of circular space
**Nodes:** `0000`, `8000`
**Target Hash:** `4000`
**Expected Behavior:**
- Both nodes equidistant (distance = 16384 = 0x4000)
- Tiebreaker: higher ID wins (8000 > 0000)

### Test 5.3: Wrap-Around Distance
**Scenario:** Verify wrap-around gives shorter distance
**Nodes:** `0100`, `ff00`
**Target Hash:** `0050`
**Expected Behavior:**
- Distance `0100`→`0050` = 176 (direct)
- Distance `ff00`→`0050` = 336 (wrap: 0x10000 - 0xFEB0 = 336)
- Should prefer `0100` (closer)
**Verification:**
```python
# Verify: min(|0x0100 - 0x0050|, 0x10000 - |0x0100 - 0x0050|) = min(176, 65360) = 176
# Verify: min(|0xff00 - 0x0050|, 0x10000 - |0xff00 - 0x0050|) = min(65200, 336) = 336
```

### Test 5.4: Near-Boundary Targets
**Scenario:** Targets very close to 0000 or ffff
**Nodes:** `0010`, `5000`, `fff0`
**Target Hash:** `0005`
**Expected Behavior:**
- `0010` is closest (distance 11)
- `fff0` is second closest (distance 21 via wrap)
- Should route to `0010`

---

## Test Category 6: Network Topology Scenarios

### Test 6.1: Linear Chain
**Scenario:** Nodes form a linear chain in ID space
**Nodes:** `1000`, `2000`, `3000`, `4000`, `5000` (evenly spaced)
**Expected Behavior:**
- Routing tables form predictable pattern
- Each node knows about all others
- Routing follows chain optimally

### Test 6.2: Clustered Nodes (Same Prefix)
**Scenario:** Many nodes share long common prefix
**Nodes:** `abc0`, `abc1`, `abc2`, `abc3`, `abc4`, `abc5`, `abc6`, `abc7`
**Target Hash:** `abc5`
**Expected Behavior:**
- All nodes have prefix='abc' (length 3)
- Routing quickly converges to cluster
- Within cluster, routes by distance

### Test 6.3: Gap in ID Space
**Scenario:** Large gap with no nodes
**Nodes:** `1000`, `2000`, `9000`, `a000` (gap from 2000 to 9000)
**Target Hash:** `5000`
**Expected Behavior:**
- Routing finds closest available node despite gap
- Should route to `2000` or `9000` (whichever closer)

### Test 6.4: Uniform Distribution
**Scenario:** Nodes evenly distributed across entire space
**Nodes:** 16 nodes at `0000`, `1000`, `2000`, ..., `f000`
**Expected Behavior:**
- Routing tables well-balanced
- Each node knows about diverse set of nodes
- Optimal routing paths for any target

---

## Test Category 7: Join Protocol Edge Cases

### Test 7.1: First Node (Bootstrap)
**Scenario:** Very first node joins empty network
**Node:** `5000` (only node)
**Expected Behavior:**
- Node has empty leaf set
- Node has empty routing table (no other nodes)
- Node can still handle store requests (stores locally)

### Test 7.2: Second Node Join
**Scenario:** Second node joins network with one existing node
**Nodes:** `3000` (existing), `7000` (new)
**Expected Behavior:**
- Both nodes discover each other
- Leaf sets: `3000` knows `7000`, `7000` knows `3000`
- Routing tables updated reciprocally

### Test 7.3: Node with Exact Prefix Match Exists
**Scenario:** New node joins with ID sharing long prefix with existing node
**Existing Node:** `abcd`
**New Node:** `abc0`
**Expected Behavior:**
- Both nodes immediately added to each other's routing tables
- Same row in routing table (row 3, based on first 3 matching chars)

### Test 7.4: Late Joiner (Network Already Large)
**Scenario:** Node joins when network already has 20+ nodes
**Existing Nodes:** 20 nodes already running
**New Node:** `5555`
**Expected Behavior:**
- New node quickly learns from discovery
- New node broadcasts to all existing nodes
- All nodes update routing tables to include new node
- Converges within seconds

---

## Test Category 8: Message Handling & Protocol

### Test 8.1: Reciprocal Routing Table Updates
**Scenario:** Verify bidirectional routing table updates
**Nodes:** `1111`, `2222`
**Expected Behavior:**
- When `1111` sends ROUTING_TABLE_UPDATE to `2222`
- `2222` should send reciprocal update back to `1111`
- Both routing tables should include each other
**Verification:**
- Check logs for reciprocal update messages
- Verify both routing tables populated

### Test 8.2: No Infinite Update Loop
**Scenario:** Reciprocal updates should not cause infinite loop
**Nodes:** `aaaa`, `bbbb`
**Expected Behavior:**
- Node checks if peer already in routing table before sending reciprocal update
- Only ONE reciprocal update sent (not infinite loop)
**Verification:**
- Check logs: should see ONE reciprocal update, not multiple

### Test 8.3: Discovery Node List Accuracy
**Scenario:** Discovery node maintains accurate list of all nodes
**Nodes:** 5 nodes join, 1 exits
**Expected Behavior:**
- Discovery list-nodes shows 4 active nodes
- Exited node appears in "Nodes that have exited" section
**Verification:**
```bash
# Check discovery node list-nodes output
# Verify counts match actual running nodes
```

---

## Test Category 9: Autograder Compliance

### Test 9.1: Required Commands
**Scenario:** All required commands work correctly
**Commands to Test:**
- `id` - returns node ID
- `exit` - cleanly exits node
- `list-nodes` - shows all nodes in network
- `leaf-set` - shows left/right neighbors
- `routing-table` - shows 4x16 routing table
- `store <file>` - stores file at correct node
**Verification:**
- Each command returns expected format
- Autograder can parse output

### Test 9.2: Routing Table Format
**Scenario:** Routing table output matches expected format
**Expected Output:**
```
Row 0: [node1, node2, ...]
Row 1: [node3, node4, ...]
Row 2: [node5, node6, ...]
Row 3: [node7, node8, ...]
```
**Verification:**
- 4 rows (for 4 hex digits)
- Each row shows nodes with matching prefix

### Test 9.3: Store Command Output
**Scenario:** Store command returns routing path correctly
**Expected Output:**
```
node1
node2
node3
target_id
```
**Verification:**
- Each line is a node ID in the routing path
- Final line is target hash

### Test 9.4: Minimum Routing Table Entries
**Scenario:** Each node has at least 8 routing table entries (autograder requirement)
**Network:** 14+ active nodes
**Expected Behavior:**
- Every node's routing table has 8+ non-null entries
**Verification:**
- Count routing table entries per node
- All must be >= 8

### Test 9.5: Correct Node Stores File
**Scenario:** File stored at node with best prefix match (not just closest distance)
**From Autograder:** "Expected id 85d9 to match first 2 characters of file id 818e"
**Expected Behavior:**
- Node `85d9` (prefix='8') stores file, not node `7eaf` (no prefix)
**Verification:**
- Autograder check passes
- File stored at node with longest matching prefix

---

## Test Category 10: Stress & Performance

### Test 10.1: Rapid Successive Stores
**Scenario:** Store many files in quick succession
**Operations:** Store 20 files within 5 seconds
**Expected Behavior:**
- All files route correctly
- No race conditions or dropped messages
- All files stored at appropriate nodes

### Test 10.2: Large Network (50+ nodes)
**Scenario:** Test scalability with large network
**Nodes:** 50 nodes with random IDs
**Expected Behavior:**
- Routing tables converge
- LOOKUP operations remain efficient (O(log N) hops)
- No performance degradation

### Test 10.3: Mixed Operations
**Scenario:** Combination of joins, stores, and queries
**Operations:** Nodes joining while files being stored
**Expected Behavior:**
- System remains consistent
- New nodes integrate smoothly
- Files stored correctly despite changing topology

---

## Automated Test Script Template

```bash
#!/usr/bin/env bash
# Test Case Runner
# Usage: ./run_test.sh <test_category> <test_number>

TEST_CATEGORY=$1
TEST_NUMBER=$2

case "$TEST_CATEGORY" in
    "prefix")
        # Run prefix matching tests
        ;;
    "routing")
        # Run routing table tests
        ;;
    "leafset")
        # Run leaf set tests
        ;;
    # ... more cases
esac
```

---

## Success Criteria Summary

✅ **Routing Table Population:**
- Every node has 8+ routing table entries
- Entries are in correct rows
- Reciprocal relationships exist

✅ **Prefix Matching:**
- Longer prefix always preferred over shorter distance
- Autograder scenario (85d9 vs 7eaf) passes
- All prefix-based test cases pass

✅ **Leaf Set Management:**
- Correct left/right neighbors
- Handles circular space wrap-around
- Updates correctly as nodes join

✅ **LOOKUP/STORE:**
- Files stored at correct nodes
- Routing paths optimal
- No routing past closest node

✅ **Protocol Correctness:**
- All commands work as specified
- Output formats match autograder expectations
- No infinite loops or deadlocks

✅ **Autograder Compliance:**
- Score: 6/6 (all tests pass)
- No errors in required commands
- Correct routing behavior verified
