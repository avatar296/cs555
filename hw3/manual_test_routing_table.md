# Manual Test: Routing Table Population

## Overview

This test verifies that routing tables are properly populated during the JOIN protocol, specifically addressing the autograder failure where node `b589` had insufficient routing table entries.

**Autograder Error:**
```
Incorrect routing-table command. Got only 3 entries excluding the current node.
```

**Root Cause:**
The original implementation used `routingTable.setRow()` which **replaced** entire rows instead of **merging** entries. When multiple JOIN_RESPONSE messages arrived from different nodes along the path, each one overwrote previous entries.

**Fix Applied:**
Changed `MessageHandler.handleJoinResponse()` (lines 204-213) to iterate through entries and use `routingTable.setEntry()` for each non-null entry, accumulating routing information instead of replacing it.

---

## Test Scenario

### Network Configuration

**13 Nodes from Autograder Test:**
```
1804, 2770, 3e37, 43ac, 6050, 93c8, 98a7, 9991, 9a71, b4f9, b589, cd9e, d481
```

**Focus Node:** `b589`

**Ring Order (sorted):**
```
1804 → 2770 → 3e37 → 43ac → 6050 → 93c8 → 98a7 → 9991 → 9a71 → b4f9 → b589 → cd9e → d481 → [wraps to 1804]
```

---

## Expected Routing Table for Node b589

### Understanding the Structure

The routing table has **4 rows × 16 columns** for 16-bit hexadecimal IDs:
- **Row 0:** Entries where 1st hex digit differs from 'b' (0-9, a, c-f)
- **Row 1:** Entries where 1st digit = 'b' AND 2nd digit differs from '5' (b0-b4, b6-bf)
- **Row 2:** Entries where 1st-2nd = 'b5' AND 3rd digit differs from '8' (b50-b57, b59-b5f)
- **Row 3:** Entries where 1st-3rd = 'b58' AND 4th digit differs from '9' (b580-b588, b58a-b58f)

### Expected Entries

#### Row 0 (1st digit differs)
Node b589 should have entries for all nodes where the first hex digit differs from 'b':

| Column | Prefix | Expected Node | Node ID |
|--------|--------|---------------|---------|
| 1      | 1-     | ✓             | 1804    |
| 2      | 2-     | ✓             | 2770    |
| 3      | 3-     | ✓             | 3e37    |
| 4      | 4-     | ✓             | 43ac    |
| 6      | 6-     | ✓             | 6050    |
| 9      | 9-     | ✓ (one of)    | 93c8, 98a7, 9991, or 9a71 |
| c      | c-     | ✓             | cd9e    |
| d      | d-     | ✓             | d481    |

**Expected: 8 entries in row 0**

#### Row 1 (b + 2nd digit differs)
Node b589 should have entries where 1st digit = 'b' but 2nd digit ≠ '5':

| Column | Prefix | Expected Node | Node ID |
|--------|--------|---------------|---------|
| 4      | b4-    | ✓             | b4f9    |

**Expected: At least 1 entry in row 1**

#### Row 2 & Row 3
With only 13 nodes in the ring, most will be in row 0-1. Rows 2-3 will likely be empty or sparse.

---

## Running the Test

### Option 1: Using Existing Test Script

If `test_autograder_scenario.sh` has been updated with Test Case 5:

```bash
# Start the test network
./test_autograder_scenario.sh 5

# In the interactive menu:
# Press 'v' to verify routing table
# Press 'a' to check all nodes
# Press 'f b589' to focus on node b589
```

### Option 2: Manual Node Startup

```bash
# 1. Build the project
./gradlew build --quiet

# 2. Start Discovery node
java -cp build/classes/java/main csx55.pastry.node.Discover 5555 &
sleep 2

# 3. Start nodes in order (wait 3 seconds between each)
for id in 1804 2770 3e37 43ac 6050 93c8 98a7 9991 9a71 b4f9 b589 cd9e d481; do
    java -cp build/classes/java/main csx55.pastry.node.Peer localhost 5555 "$id" &
    sleep 3
done

# 4. Wait for network to stabilize
sleep 5

# 5. Connect to node b589 and check routing table
# (Find the port from logs/peer-b589.log or peer_b589_*.log)
echo "routing-table" | nc localhost <PORT>
```

---

## Interpreting Results

### Output Format

The `routing-table` command outputs 4 rows (one per prefix length):

```
Row 0: 0-:,1-<IP:PORT>,2-<IP:PORT>,3-<IP:PORT>,4-<IP:PORT>,5-:,6-<IP:PORT>,7-:,8-:,9-<IP:PORT>,a-:,b-:,c-<IP:PORT>,d-<IP:PORT>,e-:,f-:
Row 1: b0-:,b1-:,b2-:,b3-:,b4-<IP:PORT>,b5-:,b6-:,b7-:,b8-:,b9-:,ba-:,bb-:,bc-:,bd-:,be-:,bf-:
Row 2: b50-:,b51-:,b52-:,...
Row 3: b580-:,b581-:,b582-:,...
```

**Empty entries** are marked with `:` after the prefix (e.g., `1-:` means no entry)
**Filled entries** show the node's IP:PORT (e.g., `1-129.82.44.149:39833`)

### Success Criteria

**PASS Conditions:**
- Row 0 has **at least 7 entries** (out of 8 possible)
- Row 1 has **at least 1 entry** (b4-)
- Total non-empty entries (excluding self): **8 or more**

**FAIL Conditions:**
- Row 0 has **3 or fewer entries** (like the autograder failure)
- Missing obvious entries like 1-, 2-, 3-, 4-, 6-, c-, d-

### Example: Before Fix (FAIL)

```
0-:,1-:,2-:,3-:,4-:,5-:,6-:,7-:,8-:,9-129.82.44.131:39445,a-:,b-:,c-129.82.44.146:46537,d-:,e-:,f-:
b0-:,b1-:,b2-:,b3-:,b4-129.82.44.132:35209,b5-:,b6-:,b7-:,b8-:,b9-:,ba-:,bb-:,bc-:,bd-:,be-:,bf-:
```

**Only 3 entries:** 9-, c-, b4-
**Missing:** 1-, 2-, 3-, 4-, 6-, d- (6 missing!)

### Example: After Fix (PASS)

```
0-:,1-129.82.44.149:39833,2-129.82.44.153:44955,3-129.82.44.164:33931,4-129.82.44.134:34537,5-:,6-129.82.44.151:43625,7-:,8-:,9-129.82.44.131:39445,a-:,b-:,c-129.82.44.146:46537,d-129.82.44.152:35027,e-:,f-:
b0-:,b1-:,b2-:,b3-:,b4-129.82.44.132:35209,b5-:,b6-:,b7-:,b8-:,b9-:,ba-:,bb-:,bc-:,bd-:,be-:,bf-:
```

**8 entries in row 0:** 1-, 2-, 3-, 4-, 6-, 9-, c-, d- ✓
**1 entry in row 1:** b4- ✓
**Total: 9 entries** ✓

---

## Verification Checklist

### 1. Check Node b589's Routing Table

```bash
# Method 1: Via interactive test script
./test_autograder_scenario.sh 5
> v  # Verify routing table

# Method 2: Direct connection (find port in logs)
grep "Peer server started" logs/peer-b589.log
echo "routing-table" | nc localhost <PORT>
```

**Expected Result:** At least 8 entries in rows 0-1 combined

### 2. Verify JOIN Protocol Logs

```bash
# Check what routing info b589 received during JOIN
grep -E "JOIN_RESPONSE|Sent JOIN_RESPONSE|routing table row" logs/peer-b589.log

# Look for lines like:
# "Sent JOIN_RESPONSE to b589 with routing table row 0 (entries: 5)"
```

**Expected:** Multiple JOIN_RESPONSE messages from different nodes along the path

### 3. Check Other Nodes' Routing Tables

Test a few other nodes to ensure they also have well-populated routing tables:

```bash
# Check node 9a71
echo "routing-table" | nc localhost <PORT_9a71>

# Check node cd9e
echo "routing-table" | nc localhost <PORT_cd9e>
```

**Expected:** Each node should have 5-10 entries

### 4. Verify MessageHandler Fix

```bash
# Confirm the fix is in place
grep -A 10 "handleJoinResponse" src/main/java/csx55/pastry/node/peer/MessageHandler.java | grep -E "setEntry|setRow"
```

**Expected:** Should see `setEntry` calls, NOT `setRow`

---

## Troubleshooting

### Issue: Still Only 3 Entries

**Possible Causes:**
1. Fix not applied correctly (still using `setRow`)
2. Project not rebuilt after fix
3. Nodes joining too quickly (not waiting for JOIN protocol)

**Solutions:**
1. Verify MessageHandler.java lines 204-213 use `setEntry` in a loop
2. Run `./gradlew clean build`
3. Increase sleep time between node starts to 3-5 seconds

### Issue: Some Entries Missing

**Possible Causes:**
1. Nodes joined via different paths (normal)
2. Network timing issues
3. Some nodes not on b589's JOIN path

**Solutions:**
1. Check JOIN logs to see which nodes forwarded the JOIN request
2. Verify at least 5-6 entries are present (complete coverage not guaranteed)
3. Ensure nodes are joining sequentially with adequate delays

### Issue: No Entries at All

**Possible Causes:**
1. JOIN protocol not completing
2. Entry point not responding
3. Routing table not being sent in JOIN_RESPONSE

**Solutions:**
1. Check Discovery node is running: `lsof -i :5555`
2. Check JOIN logs for errors
3. Verify `handleJoinRequest` sends routing table row

---

## Expected JOIN Path for b589

When node **b589** joins the network:

1. **Discovery** returns a random existing node (e.g., 6050)
2. **6050** (entry point):
   - Common prefix with b589: 0 digits
   - Sends row 0 of its routing table
   - Routes to next hop (e.g., b4f9)
3. **b4f9** (intermediate):
   - Common prefix with b589: 2 digits ('b' and '5')
   - Sends row 2 of its routing table
   - Routes to next hop or recognizes self as closest
4. **b4f9 or cd9e** (destination):
   - Recognizes itself as numerically closest to b589
   - Sends leaf set (left/right neighbors)
   - Adds b589 to its own leaf set

**Key Point:** Node b589 should accumulate routing table entries from **all intermediate nodes** along the path, not just the last one.

---

## Autograder Alignment

This test directly addresses the autograder failure:

**Autograder Test:**
```
Incorrect routing-table command. Got only 3 entries excluding the current node.
```

**Our Test:**
- Uses exact same node IDs
- Tests exact same command: `routing-table`
- Verifies same success criteria: more than 3 entries
- Focuses on same node: b589

**Pass Criteria:** 8+ entries (matches autograder expectation)

---

## Next Steps

After verifying the routing table works locally:

1. **Submit to autograder** - The fix should resolve the routing-table failure
2. **Monitor for edge cases** - Ensure different network sizes work
3. **Test with random IDs** - Verify fix works beyond the 13-node scenario
4. **Check leaf-set interaction** - Ensure leaf set nodes are also added to routing table

---

## Summary

**What We're Testing:** Routing table population via JOIN protocol
**What Was Broken:** `setRow()` replaced entries instead of merging
**What We Fixed:** Use `setEntry()` to accumulate entries from all JOIN_RESPONSE messages
**Expected Outcome:** Node b589 has 8+ routing table entries (vs. 3 before fix)
**Success Indicator:** `routing-table` command shows entries in columns 1,2,3,4,6,9,c,d for row 0

---

## Additional Test Scenarios

Beyond the basic routing table population test, these advanced scenarios validate edge cases and spec compliance:

### 1. Routing Table Updates After Node Joins

**Purpose:** Verify existing nodes update their routing tables when new nodes join

**Scenario:**
```bash
# Start initial network
./test_routing_table.sh
# Wait for startup, check node 5000's routing table (baseline)
> n 5000

# In separate terminal, add new node
java -cp build/classes/java/main csx55.pastry.node.Peer localhost 5555 5500 &

# Wait 5 seconds, check node 5000 again
> n 5000
```

**Expected:**
- Node 5000 should have node 5500 in row 1, column 5
- Routing table row 1 count increases by 1
- Verifies `handleRoutingTableUpdate()` works

**Pass Criteria:** Node 5500 appears in 5000's routing table at [1][5]

---

### 2. Routing Table Removal on Node Departure

**Purpose:** Verify routing table entries are cleaned when nodes exit gracefully

**Scenario:**
```bash
# Start 8-node network
# Check node 1000's routing table (should have multiple entries)
> n 1000

# Exit node 4000 (assuming it's in 1000's routing table)
# Check node 1000's routing table again
> n 1000
```

**Expected:**
- Node 4000 should disappear from all routing tables
- Verifies `removeNode()` is called during LEAVE handling
- Other entries remain intact

**Pass Criteria:** Node 4000 no longer appears in any routing table cell

---

### 3. Routing Table Density by Row

**Purpose:** Verify row 0 has more entries than row 1, row 1 more than row 2, etc.

**Scenario:**
```bash
# Start 13-node network with diverse IDs
# Check node b589's routing table
> v
```

**Expected Distribution:**
- **Row 0:** 6-8 entries (most populated)
- **Row 1:** 1-2 entries (less populated)
- **Row 2:** 0-1 entries (sparse)
- **Row 3:** 0 entries (usually empty with 13 nodes)

**Reasoning:**
- Row 0 matches on 0 prefix digits (broadest match)
- Row 1 matches on 1 prefix digit (narrower)
- Row 2 matches on 2 prefix digits (very narrow)
- Row 3 matches on 3 prefix digits (extremely narrow)

**Pass Criteria:** count(row 0) > count(row 1) >= count(row 2) >= count(row 3)

---

### 4. Empty Routing Table Cells

**Purpose:** Verify routing works even with incomplete routing tables

**Scenario:**
```bash
# Start minimal 4-node network: 1000, 5000, 9000, d000
# Node 5000 will have many empty cells
> n 5000

# Verify routing still works despite gaps
# Check logs for successful routing
```

**Expected:**
- Routing table has gaps (many `:` entries)
- Routing still succeeds by finding best available match
- May take more hops but eventually routes correctly

**Pass Criteria:** Routing succeeds despite sparse routing table

---

### 5. Self-ID Exclusion

**Purpose:** Verify node never adds itself to its own routing table

**Scenario:**
```bash
# Check any node's routing table
> n b589

# Look for b589 appearing anywhere in its own output
# Should NOT find b589 in any cell
```

**Expected:**
- No cell contains the node's own ID
- `setEntry()` checks prevent self-addition (line 22-24 in RoutingTable.java)

**Pass Criteria:** Node's own ID never appears in its routing table output

---

### 6. Leaf Set Nodes Also in Routing Table

**Purpose:** Per MessageHandler.java:227, leaf set nodes are ALWAYS added to routing table

**Scenario:**
```bash
# Start 5-node ring: 1000, 2000, 3000, 4000, 5000
# Check node 3000's leaf set
> (check leaf-set via peer command interface)

# Check node 3000's routing table
> n 3000
```

**Expected:**
- Node 3000's leaf neighbors (2000 and 4000) also appear in routing table
- Ensures dual storage for better connectivity
- Verifies `routingTable.addNode(node)` in `handleLeafSetUpdate()`

**Pass Criteria:** Both leaf neighbors appear in routing table

---

### 7. Routing Table Row Assignment

**Purpose:** Verify correct row/column calculation based on common prefix length

**Test Cases:**

| Local Node | Added Node | Common Prefix | Expected Position | Reasoning |
|------------|------------|---------------|-------------------|-----------|
| b589       | b4f9       | "b" (1 digit) | [1][4]            | Next digit = '4' |
| b589       | 1804       | "" (0 digits) | [0][1]            | Next digit = '1' |
| 65a1       | 65b2       | "65" (2)      | [2][b]            | Next digit = 'b' |
| 5000       | 5fff       | "5" (1)       | [1][f]            | Next digit = 'f' |

**Verification:**
- Use logs or debug output to verify cell assignment
- Check routing table output to confirm placement

**Pass Criteria:** All nodes appear in correct [row][col] positions

---

### 8. Entry Point Row 0 Initialization

**Purpose:** Per spec page 4, new node gets row 0 from entry point

**Scenario:**
```bash
# Node b589 joins via entry point 6050
# Check b589's routing table row 0
# Check 6050's routing table row 0
# Compare them
```

**Expected:**
- b589's row 0 should contain entries from 6050's row 0
- Not necessarily identical (b589 may accumulate more)
- But should have overlap from entry point

**Pass Criteria:** At least 50% of b589's row 0 entries came from entry point

---

### 9. Intermediate Node Row Sharing

**Purpose:** Per spec, intermediate nodes send row matching their prefix length

**Scenario:**
```bash
# Node b589 joins, routed through intermediate node b4f9
# Common prefix: "b" (1 digit)
# b4f9 should send row 1, not row 0 or 2
```

**Verification:**
- Check JOIN logs: `grep "Sent JOIN_RESPONSE.*row" logs/peer-b4f9.log`
- Should see: "Sent JOIN_RESPONSE to b589 with routing table row 1"

**Pass Criteria:** Intermediate nodes send row matching common prefix length

---

### 10. Multiple Nodes with Same Prefix

**Purpose:** When multiple nodes share a prefix, only ONE should be in each routing table cell

**Scenario:**
```bash
# Start nodes: b100, b120, b150 (all start with 'b1')
# Check node 5000's routing table
# Look at row 1, column 1 (b1-)
```

**Expected:**
- Cell [1][1] contains exactly ONE of {b100, b120, b150}
- Implementation uses `setEntry()` which overwrites
- Last one wins, or based on arrival order

**Pass Criteria:** Each cell has 0 or 1 entry, never multiple

---

### 11. Routing Table Accumulation During Sequential Joins

**Purpose:** Verify routing tables grow correctly as network expands

**Scenario:**
```bash
# Start with 3 nodes: 1000, 5000, 9000
# Check 5000's routing table (baseline: ~2 entries)

# Add node 2000 (wait 5 seconds)
# Check 5000's routing table (should have +1 entry)

# Add node 3000 (wait 5 seconds)
# Check 5000's routing table (should have +1 more)

# Continue adding up to 15 nodes
# Track growth of 5000's routing table
```

**Expected Growth:**
- Routing table size increases monotonically
- Eventually plateaus as row 0 fills up
- No entries lost during additions

**Pass Criteria:** Routing table size never decreases, only increases or stays same

---

## Testing These Scenarios

The `test_routing_table.sh` script includes an "Advanced Tests" menu option that automates many of these scenarios:

```bash
./test_routing_table.sh

# In menu:
> x  # Advanced tests menu

# Options:
1) Test routing updates
2) Test self-ID exclusion
3) Test row density
4) Test leaf+routing dual storage
5) Run all advanced tests
```

Each advanced test provides:
- Clear pass/fail indication
- Detailed diagnostics
- Color-coded output
- Comparison with expected behavior

---

## Why These Tests Matter

1. **Spec Compliance:** Verify implementation matches Pastry protocol specification
2. **Edge Cases:** Catch bugs that don't appear in basic 13-node scenario
3. **Robustness:** Ensure routing works with sparse tables, node churn, timing issues
4. **Correctness:** Validate row/column assignment, self-exclusion, update handling
5. **Autograder Prep:** Additional confidence beyond the basic routing table test
