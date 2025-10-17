# Comprehensive Manual Test Cases for Pastry DHT Implementation

## Overview
These test cases are designed to validate our leaf swapping implementation and catch any issues before final submission. Focus on testing the order-of-operations fix where replacement neighbors must take priority over routing table entries.

---

## Test Case 1: Basic Leaf Swapping (3-Node Ring)
**Purpose:** Verify basic leaf swapping when middle node exits

**Setup:**
```bash
# Start nodes in order to create a small ring
Node A (id: 1000)
Node B (id: 5000)
Node C (id: 9000)
```

**Actions:**
1. Start all 3 nodes
2. Verify ring: 1000 ← 5000 → 9000 (circular)
3. Check each node's leaf-set:
   - `1000`: should have left=9000, right=5000
   - `5000`: should have left=1000, right=9000
   - `9000`: should have left=5000, right=1000
4. Exit node 5000 (middle node)

**Expected After Exit:**
- `1000`: left=9000, right=9000 (both point to same neighbor)
- `9000`: left=1000, right=1000
- Verify logs show: "Replacement neighbor provided"

**Verification Commands:**
```bash
# On node 1000
leaf-set  # Should show: 9000 (both before and after)

# On node 9000
leaf-set  # Should show: 1000 (both before and after)
```

**Success Criteria:**
- Both remaining nodes have each other as neighbors
- No null neighbors
- Logs show "Removing neighbor (no auto-replacement)" followed by "Replacement neighbor provided"

---

## Test Case 2: The Autograder Scenario (Reproduction)
**Purpose:** Reproduce the exact autograder failure scenario

**Setup:**
Start nodes with IDs that create the autograder ring:
```
03c0, 06fb, 1335, 1d05, 23e1, 46e2, 4ad7, 6e6a,
7332, 99b4, 9af1, b5ad, b715, b869, eae5, ecdd
```

**Focus Node:** `9af1`

**Expected Initial State:**
```
9af1:
  left (before): 99b4
  right (after): b5ad
```

**Actions:**
1. Start all 16 nodes
2. Verify `9af1` has correct neighbors initially
3. If any nodes exit (simulate autograder exits), verify neighbors remain correct

**Critical Verification:**
- Node `9af1` MUST have right=b5ad (NOT b715)
- Ring order: `99b4 → 9af1 → b5ad → b715`
- b5ad is the immediate clockwise neighbor

**Failure Indicator:**
If `9af1` shows right=b715 instead of b5ad, the bug is still present.

---

## Test Case 3: Order-of-Operations (The Bug We Fixed)
**Purpose:** Verify replacement neighbor takes priority over routing table entries

**Setup:**
```bash
Node A (id: 1000)
Node B (id: 2000)
Node C (id: 3000)
Node D (id: 8000)  # Far away, will be in routing table
```

**Initial State:**
- `1000` has: left=8000 (wraparound), right=2000
- `1000` routing table contains: 8000, 2000, 3000

**Actions:**
1. Start all 4 nodes
2. Verify node 1000 has 8000 in routing table
3. Exit node 2000 (which will tell 1000: "your new right is 3000")

**Expected:**
- Node `1000` should have right=3000 (from leaf swap)
- NOT right=8000 (even though 8000 is in routing table and might be processed first)
- This directly tests our fix: explicit replacement beats routing table candidates

**Verification in Logs:**
```
[1000] Removing RIGHT neighbor (no auto-replacement): 2000
Replacement neighbor provided: 3000 (from departing node 2000)
[1000] Adding node 3000: CW=8192, CCW=57344, isRight=true
[1000] Setting RIGHT to 3000 (was null)
Finding replacements for vacant leaf slots
[1000] findReplacements() complete: processed X candidates
[1000] FINAL STATE: LEFT=8000, RIGHT=3000
```

**Failure Pattern (Bug Present):**
```
[1000] Calling findReplacements() after removing 2000  # ← TOO EARLY!
[1000] Setting RIGHT to 8000  # ← WRONG NODE from routing table
Replacement neighbor provided: 3000
[1000] KEEPING RIGHT: 8000  # ← REJECTED replacement!
```

---

## Test Case 4: Sequential Exits
**Purpose:** Verify ring integrity after multiple exits

**Setup:**
```bash
Nodes: 1000, 2000, 3000, 4000, 5000 (5-node ring)
```

**Actions:**
1. Start all nodes, verify initial ring structure
2. Exit 2000 → verify 1000 now has right=3000
3. Exit 3000 → verify 1000 now has right=4000
4. Exit 4000 → verify 1000 now has right=5000

**Expected Final State:**
After all exits:
- `1000`: left=5000 (wraps), right=5000 (only other node)
- `5000`: left=1000, right=1000
- Ring: 1000 ↔ 5000

**Verification:**
Each exit should trigger leaf swapping, and ring should remain consistent after each step.

---

## Test Case 5: Exit and Rejoin
**Purpose:** Verify a node can rejoin and rediscover correct neighbors

**Setup:**
```bash
Nodes: 1000, 5000, 9000 (3-node ring)
```

**Actions:**
1. Start all nodes, verify ring structure:
   - 1000: left=9000, right=5000
   - 5000: left=1000, right=9000
   - 9000: left=5000, right=1000
2. Exit node 5000 (graceful exit with leaf swapping)
3. Verify 1000 and 9000 are now neighbors:
   - 1000: left=9000, right=9000
   - 9000: left=1000, right=1000
4. **Rejoin** node 5000 (restart it with same ID)
5. Wait for join protocol to complete (>500ms)

**Expected After Rejoin:**
- `5000` should rediscover both neighbors:
  - left: 1000
  - right: 9000
- `1000` should update right to 5000
- `9000` should update left to 5000
- Ring fully restored: 1000 → 5000 → 9000 → 1000

**Verification Commands:**
```bash
# On node 5000
leaf-set  # Should show: 1000 and 9000

# On node 1000
leaf-set  # Should show: left=9000, right=5000

# On node 9000
leaf-set  # Should show: left=5000, right=1000
```

**Common Issues to Watch:**
- Node 5000 only discovers one neighbor (incomplete join)
- Existing nodes don't update to include 5000
- Ring has gaps

---

## Test Case 6: Two-Node Edge Case
**Purpose:** Test minimum viable ring

**Setup:**
```bash
Node A (id: 1000)
Node B (id: 5000)
```

**Actions:**
1. Start both nodes
2. Verify each has the other as both left AND right:
   - 1000: left=5000, right=5000
   - 5000: left=1000, right=1000
3. Exit node B (graceful)

**Expected After Exit:**
- Node A: left=null, right=null (no neighbors)
- Node A still functional (can handle lookups to itself)
- No crashes or errors

**Rejoin Test:**
4. Restart node B
5. Verify bidirectional link restored
6. Both nodes again have each other as left and right

---

## Test Case 7: Entry Point Matters
**Purpose:** Verify node discovers all neighbors regardless of entry point

**Setup:**
```bash
Nodes: 1000, 3000, 5000, 7000, 9000 (5 evenly spaced nodes)
```

**Actions:**
1. Start initial 5 nodes
2. New node 4000 joins via entry point 1000 (far away from where it belongs)
3. Check if 4000 discovers correct neighbors despite distant entry point

**Expected:**
- Node 4000 finds correct neighbors:
  - left: 3000 (closest counter-clockwise)
  - right: 5000 (closest clockwise)
- Ring: ... → 3000 → 4000 → 5000 → ...
- Entry point distance doesn't matter

**Verification:**
The JOIN protocol should route 4000 to its correct position, where it discovers local neighbors.

---

## Test Case 8: Simultaneous Operations (Stress Test)
**Purpose:** Stress test with concurrent joins and exits

**Setup:**
```bash
Nodes: 1000, 2000, 3000, 4000, 5000 (stable ring)
```

**Actions:**
1. Start all nodes, verify stable ring
2. **Quickly** (within 1-2 seconds):
   - Exit node 2000
   - Start new node 2500
   - Exit node 4000
3. Wait for operations to settle (2-3 seconds)

**Expected:**
- Final ring remains consistent
- No nodes have null neighbors (unless only 2 nodes remain)
- All nodes agree on ring structure
- No crashes or hung processes

**Verification:**
Check each node's leaf-set independently and verify they form a consistent ring.

**Common Issues:**
- Race conditions during updates
- Nodes with conflicting views of ring
- Null pointer exceptions

---

## Test Case 9: Wraparound Edge Case
**Purpose:** Test nodes at ring boundaries (near 0x0000 and 0xFFFF)

**Setup:**
```bash
Node A (id: 0100)  # Near start of ring
Node B (id: 8000)  # Middle
Node C (id: ff00)  # Near end of ring
```

**Actions:**
1. Start all nodes
2. Verify wraparound calculation:
   - `0100`: left=ff00 (wraps around), right=8000
   - `ff00`: left=8000, right=0100 (wraps around)
   - `8000`: left=0100, right=ff00
3. Exit ff00

**Expected After Exit:**
- `0100`: left=8000, right=8000
- `8000`: left=0100, right=0100
- Wraparound math works correctly (no negative values or overflow)

**Critical:**
Verify clockwise/counter-clockwise distance calculations handle wraparound:
- Distance from ff00 to 0100 should be small (wraps)
- Not treated as huge distance

---

## Test Case 10: No Replacement Scenario
**Purpose:** Verify findReplacements works when no explicit replacement provided

**Setup:**
```bash
Nodes: 1000, 2000, 3000, 4000
All nodes have each other in routing tables
```

**Actions:**
1. Start all nodes
2. Manually simulate network failure: send LEAVE(2000, replacement=null) to 1000
3. Check if 1000 finds new right neighbor from routing table

**Expected:**
- `1000` removes 2000 from leaf set
- Since replacement=null, findReplacementsIfNeeded() is called
- `1000` scans routing table and finds 3000 as next best right neighbor
- Logs show: "Finding replacements for vacant leaf slots"

**Log Pattern:**
```
[1000] Removing RIGHT neighbor (no auto-replacement): 2000
[1000] Finding replacements for vacant leaf slots
[1000] findReplacements() found candidate: 3000 at [X][Y]
[1000] Setting RIGHT to 3000 (was null)
[1000] findReplacements() complete: processed N candidates
```

---

## Quick Validation Script

```bash
#!/bin/bash
# Run this to quickly validate basic scenarios

echo "=== Test 1: Basic Leaf Swapping ==="
echo "1. Start nodes with IDs: 1000, 5000, 9000"
echo "2. Verify each node's leaf-set forms a ring"
echo "3. Exit node 5000"
echo "4. Check 1000 and 9000 have each other as neighbors"
echo ""

echo "=== Test 2: Replacement Priority ==="
echo "1. Start nodes: 1000, 2000, 3000, 8000"
echo "2. Verify 1000 has: left=8000, right=2000"
echo "3. Exit 2000 (sends replacement: 3000)"
echo "4. Verify 1000 has right=3000 (NOT 8000)"
echo ""

echo "=== Test 3: Exit and Rejoin ==="
echo "1. Start 3-node ring: 1000, 5000, 9000"
echo "2. Exit node 5000"
echo "3. Verify 2-node ring: 1000 ↔ 9000"
echo "4. Restart node 5000"
echo "5. Verify full ring restored"
echo ""
```

---

## Expected Log Patterns (Success Indicators)

### Successful Leaf Swap
```
[Node exiting]
Sent LEAVE notification to 1000 with replacement 9000
Sent LEAVE notification to 9000 with replacement 1000

[Node 1000 receiving]
Node 5000 is leaving the network
[1000] Removing RIGHT neighbor (no auto-replacement): 5000
Replacement neighbor provided: 9000 (from departing node 5000)
[1000] Adding node 9000: CW=32768, CCW=32768, isRight=true
[1000] Setting RIGHT to 9000 (was null)
[1000] FINAL STATE after adding 9000: LEFT=9000, RIGHT=9000
```

### Successful Priority Replacement (Our Fix)
```
[1000] Removing RIGHT neighbor (no auto-replacement): 2000
Replacement neighbor provided: 3000 (from departing node 2000)
[1000] Adding node 3000: CW=8192, CCW=57344, isRight=true
[1000] Setting RIGHT to 3000 (was null)
Finding replacements for vacant leaf slots
[1000] findReplacements() scanning routing table...
[1000] findReplacements() found candidate: 8000 at [X][Y]
[1000] Comparing RIGHT: current=3000 (CW=8192) vs new=8000 (CW=28672)
[1000] KEEPING RIGHT: 3000
[1000] findReplacements() complete: processed 1 candidates
```

### Failed Scenario (Bug Present - Should NOT See This)
```
[1000] BEFORE removeNode(2000): LEFT=8000, RIGHT=2000
[1000] Removing RIGHT neighbor: 2000
[1000] Calling findReplacements() after removing 2000  # ← BUG: TOO EARLY!
[1000] findReplacements() found candidate: 8000 at [0][8]
[1000] Setting RIGHT to 8000 (was null)  # ← BUG: WRONG NODE!
[1000] AFTER removeNode(2000): LEFT=8000, RIGHT=8000
Replacement neighbor provided: 3000 (from departing node 2000)
[1000] Adding node 3000: CW=8192, CCW=57344, isRight=true
[1000] Comparing RIGHT: current=8000 (CW=28672) vs new=3000 (CW=8192)
[1000] REPLACING RIGHT: 8000 -> 3000
```
**Note:** If you see the "Calling findReplacements() after removing" before "Replacement neighbor provided", the bug is NOT fixed!

---

## Critical Success Criteria

For submission to be safe:

1. **Test Case 3 MUST pass** - This directly validates our fix
2. **No "Calling findReplacements() after removing X" before replacement is processed**
3. **All 3-node ring tests should show correct leaf swapping**
4. **No null neighbors unless it's a 1-node network**
5. **Logs show "Removing neighbor (no auto-replacement)" followed by "Replacement neighbor provided"**

---

## How to Run Tests

### Manual Testing Steps:
```bash
# Terminal 1: Discovery Node
java csx55.pastry.node.Discover 5000

# Terminal 2: First Peer
java csx55.pastry.node.Peer localhost 5000 1000

# Terminal 3: Second Peer
java csx55.pastry.node.Peer localhost 5000 5000

# Terminal 4: Third Peer
java csx55.pastry.node.Peer localhost 5000 9000

# On each peer, type:
leaf-set

# To exit a node (triggers leaf swapping):
exit
```

### Check Logs:
```bash
tail -f logs/peer-1000.log
tail -f logs/peer-5000.log
tail -f logs/peer-9000.log
```

---

## Known Issues to Watch For

1. **Timing Issues:** If nodes join/exit too quickly, updates might not complete
2. **Routing Table Empty:** If routing table is empty during exit, findReplacements returns nothing
3. **Single Node:** Edge case where node has no neighbors (should handle gracefully)
4. **Wraparound Math:** Verify distance calculations near 0x0000 and 0xFFFF boundaries

---

## Automated Validation (Future)

To automate these tests, we could:
1. Write shell scripts to start/stop nodes
2. Parse log files for expected patterns
3. Query leaf-set via command interface
4. Assert ring consistency across all nodes

For now, manual testing with careful log inspection is recommended.
