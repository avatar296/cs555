# DFS Manual Testing Guide

This document provides comprehensive manual testing instructions for the Distributed File System (DFS) implementation.

## Table of Contents
1. [Setup and Preparation](#setup-and-preparation)
2. [Phase 1: Basic Functionality Testing](#phase-1-basic-functionality-testing)
3. [Phase 2: Heartbeat and Registration](#phase-2-heartbeat-and-registration)
4. [Phase 3: Data Integrity Testing](#phase-3-data-integrity-testing)
5. [Phase 4: Failure Recovery Testing](#phase-4-failure-recovery-testing)
6. [Phase 5: Edge Cases and Error Handling](#phase-5-edge-cases-and-error-handling)
7. [Phase 6: Code Review Checklist](#phase-6-code-review-checklist)

---

## Setup and Preparation

### Build the Project
```bash
./gradlew clean build
```

Expected: `BUILD SUCCESSFUL`

### Create Test Files
```bash
# Small file (< 64KB)
echo "This is a small test file for DFS testing." > /tmp/small-test.txt

# Medium file (~200KB, 3-4 chunks)
dd if=/dev/urandom of=/tmp/medium-test.txt bs=1024 count=200

# Large file (~1MB)
dd if=/dev/urandom of=/tmp/large-test.txt bs=1024 count=1024
```

### Cleanup Between Tests
```bash
./scripts/cleanup.sh
```

This kills all DFS processes and removes test data.

---

## Phase 1: Basic Functionality Testing

### Test 1.1: Replication Mode - Small File Upload/Download

**Setup:**
```bash
# Terminal 1: Start Controller
./gradlew runReplicationController -PappArgs="8000" --console=plain

# Terminal 2-4: Start 3 ChunkServers
./gradlew runReplicationChunkServer -PappArgs="localhost 8000" --console=plain
./gradlew runReplicationChunkServer -PappArgs="localhost 8000" --console=plain
./gradlew runReplicationChunkServer -PappArgs="localhost 8000" --console=plain
```

**Expected Controller Output:**
```
Replication Controller started on port 8000
New chunk server registered: <hostname>:<port1>
New chunk server registered: <hostname>:<port2>
New chunk server registered: <hostname>:<port3>
```

**Expected ChunkServer Output (each):**
```
ChunkServer started on port <port>
Sending MAJOR heartbeat to Controller
Heartbeat acknowledged by Controller
```

**Test Upload:**
```bash
# Terminal 5: Start Client
./gradlew runReplicationClient -PappArgs="localhost 8000" --console=plain

# In client prompt:
> upload /tmp/small-test.txt /test/small.txt
```

**Expected Client Output:**
```
Uploading file: /tmp/small-test.txt -> /test/small.txt
File size: <size> bytes, Chunks: 1
Selected chunk servers for /test/small.txt chunk 1: [<server1>, <server2>, <server3>]
Stored chunk: /test/small.txt_chunk1
Upload completed successfully
<hostname>:<port1>
<hostname>:<port2>
<hostname>:<port3>
```

**Expected ChunkServer Output (all 3):**
```
Stored chunk: /test/small.txt_chunk1
```

**Test Download:**
```bash
> download /test/small.txt /tmp/downloaded-small.txt
```

**Expected Client Output:**
```
Downloading file: /test/small.txt -> /tmp/downloaded-small.txt
Download completed successfully
<hostname>:<port>
```

**Verify Integrity:**
```bash
diff /tmp/small-test.txt /tmp/downloaded-small.txt
```

Expected: No output (files identical)

**Verify Storage:**
```bash
find /tmp/chunk-server-* -name "*small.txt*" -type f
```

Expected: 3 files found (one in each server)

**Result:** ✅ PASS / ❌ FAIL

---

### Test 1.2: Replication Mode - Multi-Chunk File

**Test Upload:**
```bash
> upload /tmp/medium-test.txt /test/medium.txt
```

**Expected Output:**
```
File size: 204800 bytes, Chunks: 4
Selected chunk servers for /test/medium.txt chunk 1: [...]
Selected chunk servers for /test/medium.txt chunk 2: [...]
Selected chunk servers for /test/medium.txt chunk 3: [...]
Selected chunk servers for /test/medium.txt chunk 4: [...]
Upload completed successfully
```

**Test Download:**
```bash
> download /test/medium.txt /tmp/downloaded-medium.txt
```

**Verify Integrity:**
```bash
diff /tmp/medium-test.txt /tmp/downloaded-medium.txt
md5sum /tmp/medium-test.txt /tmp/downloaded-medium.txt
```

Expected: Files identical

**Verify Chunk Distribution:**
```bash
for i in 1 2 3 4; do
  echo "Chunk $i:"
  find /tmp/chunk-server-* -name "*medium.txt_chunk$i" | wc -l
done
```

Expected: Each chunk should have 3 replicas

**Result:** ✅ PASS / ❌ FAIL

---

### Test 1.3: Erasure Coding Mode - Small File

**Cleanup:**
```bash
./scripts/cleanup.sh
```

**Setup:**
```bash
# Terminal 1: Start Erasure Controller
./gradlew runErasureController -PappArgs="9000" --console=plain

# Terminals 2-10: Start 9 ChunkServers
for i in {1..9}; do
  # In separate terminals:
  ./gradlew runErasureChunkServer -PappArgs="localhost 9000" --console=plain
done
```

**Expected Controller Output:**
```
Erasure Coding Controller started on port 9000
New chunk server registered: <hostname>:<port1>
...
New chunk server registered: <hostname>:<port9>
```

**Test Upload:**
```bash
# Terminal 11: Start Erasure Client
./gradlew runErasureClient -PappArgs="localhost 9000" --console=plain

> upload /tmp/small-test.txt /test/erasure-small.txt
```

**Expected Output:**
```
Uploading file with erasure coding: /tmp/small-test.txt -> /test/erasure-small.txt
File size: <size> bytes, Chunks: 1
Upload completed successfully
<9 server addresses listed>
```

**Verify Fragment Distribution:**
```bash
find /tmp/chunk-server-* -name "*erasure-small.txt*" -type f | wc -l
```

Expected: Exactly 9 fragments (6 data + 3 parity)

**Test Download:**
```bash
> download /test/erasure-small.txt /tmp/downloaded-erasure-small.txt
```

**Verify Integrity:**
```bash
diff /tmp/small-test.txt /tmp/downloaded-erasure-small.txt
```

Expected: Files identical

**Result:** ✅ PASS / ❌ FAIL

---

### Test 1.4: Erasure Coding Mode - Multi-Chunk File

**Test Upload:**
```bash
> upload /tmp/medium-test.txt /test/erasure-medium.txt
```

**Expected Output:**
```
File size: 204800 bytes, Chunks: 4
Upload completed successfully
```

**Verify Fragment Distribution:**
```bash
for i in 1 2 3 4; do
  echo "Chunk $i fragments:"
  find /tmp/chunk-server-* -name "*erasure-medium.txt_chunk$i" | wc -l
done
```

Expected: Each chunk should have 9 fragments

**Test Download and Verify:**
```bash
> download /test/erasure-medium.txt /tmp/downloaded-erasure-medium.txt
md5sum /tmp/medium-test.txt /tmp/downloaded-erasure-medium.txt
```

Expected: Files identical

**Result:** ✅ PASS / ❌ FAIL

---

## Phase 2: Heartbeat and Registration

### Test 2.1: Initial Registration (MAJOR Heartbeat)

**Setup:** Start Controller and 3 ChunkServers

**Watch Controller Logs:**
```
New chunk server registered: <hostname>:<port1>
New chunk server registered: <hostname>:<port2>
New chunk server registered: <hostname>:<port3>
```

**Watch ChunkServer Logs:**
```
Sending MAJOR heartbeat to Controller
Heartbeat acknowledged by Controller
```

**Timing:** Should occur within 1 second of startup

**Result:** ✅ PASS / ❌ FAIL

---

### Test 2.2: Minor Heartbeat (15 second interval)

**Setup:** System running with uploaded files

**Wait 15 seconds after upload**

**Expected ChunkServer Output:**
```
Sending MINOR heartbeat to Controller
Heartbeat acknowledged by Controller
```

**Verify:** Only new chunks reported (not full inventory)

**Timing:** Should occur every 15 seconds after activity

**Result:** ✅ PASS / ❌ FAIL

---

### Test 2.3: Major Heartbeat (60 second interval)

**Setup:** System running

**Wait 60 seconds from last MAJOR heartbeat**

**Expected ChunkServer Output:**
```
Sending MAJOR heartbeat to Controller
Heartbeat acknowledged by Controller
```

**Verify in Controller:** Full chunk inventory received

**Timing:** Should occur every 60 seconds

**Result:** ✅ PASS / ❌ FAIL

---

## Phase 3: Data Integrity Testing

### Test 3.1: Replication Checksum Corruption Detection

**Setup:** Replication mode with 3 servers, file uploaded

**Upload Test File:**
```bash
echo "This is a corruption test file for CS555 HW4." > /tmp/corrupt-test.txt
> upload /tmp/corrupt-test.txt /test/corrupt-test.txt
```

**Corrupt One Replica:**
```bash
./scripts/corrupt-chunk.sh /test/corrupt-test.txt
```

**Expected Output:**
```
✓ Found 3 replica(s)
Corrupting first replica only (port <port>)
✓ Chunk corrupted successfully

Replica Status:
  Port <port1>: CORRUPTED (slice 1)
  Port <port2>: OK (valid copy)
  Port <port3>: OK (valid copy)
```

**Test Downloads (repeat multiple times):**
```bash
> download /test/corrupt-test.txt /tmp/test-download-1.txt
> download /test/corrupt-test.txt /tmp/test-download-2.txt
> download /test/corrupt-test.txt /tmp/test-download-3.txt
> download /test/corrupt-test.txt /tmp/test-download-4.txt
> download /test/corrupt-test.txt /tmp/test-download-5.txt
```

**Expected Behavior:**
- **When hitting valid replica:** Download succeeds
- **When hitting corrupted replica:** Error message appears:
  ```
  <hostname>:<port> 1 1 is corrupted
  Error: Chunk corruption detected: /test/corrupt-test.txt_chunk1 slice 1
  ```

**Verify Valid Downloads:**
```bash
diff /tmp/corrupt-test.txt /tmp/test-download-*.txt
```

Expected: Valid downloads match original

**Result:** ✅ PASS / ❌ FAIL

---

### Test 3.2: Erasure Coding Fragment Loss Tolerance

**Setup:** Erasure mode with 9 servers, file uploaded

**Upload Test File:**
```bash
> upload /tmp/medium-test.txt /test/erasure-tolerance.txt
```

**Test 1: Kill 1 server (8/9 fragments available)**
```bash
# Identify one ChunkServer process and kill it
pkill -f "csx55.dfs.erasure.ChunkServer" -c 1
```

**Download File:**
```bash
> download /test/erasure-tolerance.txt /tmp/erasure-tolerance-1.txt
```

Expected: ✅ Download succeeds (need only 6/9)

**Test 2: Kill 2 more servers (6/9 fragments available)**
```bash
# Kill 2 more servers (total 3 dead)
pkill -f "csx55.dfs.erasure.ChunkServer" -c 2
```

**Download File:**
```bash
> download /test/erasure-tolerance.txt /tmp/erasure-tolerance-2.txt
```

Expected: ✅ Download succeeds (exactly 6/9)

**Test 3: Kill 1 more server (5/9 fragments available)**
```bash
# Kill 1 more (total 4 dead, 5 alive)
pkill -f "csx55.dfs.erasure.ChunkServer" -c 1
```

**Download File:**
```bash
> download /test/erasure-tolerance.txt /tmp/erasure-tolerance-3.txt
```

Expected: ❌ Download fails (insufficient fragments)

**Expected Error:**
```
Not enough fragments available to reconstruct chunk 1 (have 5, need 6)
```

**Result:** ✅ PASS / ❌ FAIL

---

## Phase 4: Failure Recovery Testing

### Test 4.1: Replication Single Server Failure

**Setup:** Replication mode with 3 servers, file uploaded

**Upload Test File:**
```bash
> upload /tmp/medium-test.txt /test/auto-repair-test.txt
```

**Verify Initial Replicas:**
```bash
find /tmp/chunk-server-* -name "*auto-repair-test.txt*" | wc -l
```

Expected: 12 files (4 chunks × 3 replicas)

**Kill One ChunkServer:**
```bash
# Identify one ChunkServer PID
ps aux | grep "csx55.dfs.replication.ChunkServer"

# Kill specific server
kill <PID>
```

**Wait for Failure Detection (180 seconds):**
```bash
# Monitor Controller logs
```

**Expected Controller Output (after 180s):**
```
FAILURE DETECTED: <hostname>:<port>
Initiating recovery for failed server: <hostname>:<port>
Found 4 chunks affected by failure
Recovering chunk: /test/auto-repair-test.txt:1
Recovering chunk: /test/auto-repair-test.txt:2
Recovering chunk: /test/auto-repair-test.txt:3
Recovering chunk: /test/auto-repair-test.txt:4
```

**Expected Remaining ChunkServer Output:**
```
Replicated /test/auto-repair-test.txt_chunk1 to <new-server>
Replicated /test/auto-repair-test.txt_chunk2 to <new-server>
Replicated /test/auto-repair-test.txt_chunk3 to <new-server>
Replicated /test/auto-repair-test.txt_chunk4 to <new-server>
```

**Verify Recovery:**
```bash
find /tmp/chunk-server-* -name "*auto-repair-test.txt*" | wc -l
```

Expected: 12 files (restored to 3 replicas per chunk)

**Download and Verify:**
```bash
> download /test/auto-repair-test.txt /tmp/auto-repair-recovered.txt
diff /tmp/medium-test.txt /tmp/auto-repair-recovered.txt
```

Expected: Files identical

**Result:** ✅ PASS / ❌ FAIL

---

### Test 4.2: Replication Multiple Server Failures

**Setup:** Replication mode with 4-5 servers, file uploaded with 3 replicas

**Upload Test File:**
```bash
> upload /tmp/large-test.txt /test/multi-failure.txt
```

**Kill 2 of 3 Servers with Replicas:**
```bash
# Kill 2 servers
kill <PID1> <PID2>
```

**Wait 180 seconds**

**Expected Controller Output:**
```
FAILURE DETECTED: <server1>
FAILURE DETECTED: <server2>
Initiating recovery for failed server: <server1>
Found <N> chunks affected by failure
Initiating recovery for failed server: <server2>
Found <N> chunks affected by failure
```

**Verify Recovery:**
```bash
# Check that replicas restored from single remaining copy
find /tmp/chunk-server-* -name "*multi-failure.txt*" | wc -l
```

Expected: Each chunk back to 3 replicas

**Download and Verify:**
```bash
> download /test/multi-failure.txt /tmp/multi-failure-recovered.txt
diff /tmp/large-test.txt /tmp/multi-failure-recovered.txt
```

Expected: Files identical

**Result:** ✅ PASS / ❌ FAIL

---

### Test 4.3: Erasure Coding Fragment Recovery

**Setup:** Erasure mode with 9 servers, file uploaded

**Upload Test File:**
```bash
> upload /tmp/medium-test.txt /test/erasure-recovery.txt
```

**Verify Initial Fragments:**
```bash
find /tmp/chunk-server-* -name "*erasure-recovery.txt*" | wc -l
```

Expected: 36 fragments (4 chunks × 9 fragments)

**Kill 3 ChunkServers:**
```bash
# Kill 3 servers
kill <PID1> <PID2> <PID3>
```

**Wait 180 seconds**

**Expected Controller Output:**
```
FAILURE DETECTED: <server1>
FAILURE DETECTED: <server2>
FAILURE DETECTED: <server3>
Initiating recovery for failed server: <server1>
Found <N> fragments affected by failure
Recovering fragment: /test/erasure-recovery.txt:1:X
```

**Expected ChunkServer Output (servers with fragments):**
```
Reconstructing fragment X for /test/erasure-recovery.txt_chunk1 to <new-server>
```

**Verify Recovery:**
```bash
find /tmp/chunk-server-* -name "*erasure-recovery.txt*" | wc -l
```

Expected: 36 fragments (restored)

**Download and Verify:**
```bash
> download /test/erasure-recovery.txt /tmp/erasure-recovery-recovered.txt
diff /tmp/medium-test.txt /tmp/erasure-recovery-recovered.txt
```

Expected: Files identical

**Result:** ✅ PASS / ❌ FAIL

---

## Phase 5: Edge Cases and Error Handling

### Test 5.1: Insufficient Chunk Servers

**Setup:** Start only 2 ChunkServers (need 3 for replication)

**Attempt Upload:**
```bash
> upload /tmp/small-test.txt /test/insufficient.txt
```

**Expected Output:**
```
Not enough chunk servers available. Need 3, have 2
```

**Result:** ✅ PASS / ❌ FAIL

---

### Test 5.2: File Not Found

**Setup:** Running system

**Attempt Download:**
```bash
> download /nonexistent/file.txt /tmp/output.txt
```

**Expected Output:**
```
File not found: /nonexistent/file.txt
```

**Result:** ✅ PASS / ❌ FAIL

---

### Test 5.3: Empty File

**Create Empty File:**
```bash
touch /tmp/empty.txt
```

**Upload:**
```bash
> upload /tmp/empty.txt /test/empty.txt
```

**Expected Behavior:** Should handle gracefully

**Download:**
```bash
> download /test/empty.txt /tmp/downloaded-empty.txt
```

**Verify:**
```bash
ls -l /tmp/empty.txt /tmp/downloaded-empty.txt
```

Expected: Both 0 bytes

**Result:** ✅ PASS / ❌ FAIL

---

### Test 5.4: Exact Chunk Boundary Files

**Create 64KB File:**
```bash
dd if=/dev/urandom of=/tmp/exactly-64k.txt bs=1024 count=64
```

**Upload and Download:**
```bash
> upload /tmp/exactly-64k.txt /test/exact-64k.txt
> download /test/exact-64k.txt /tmp/downloaded-exact-64k.txt
```

**Verify:**
```bash
ls -l /tmp/exactly-64k.txt
md5sum /tmp/exactly-64k.txt /tmp/downloaded-exact-64k.txt
```

Expected: Exactly 65536 bytes, checksums match

**Create 128KB File:**
```bash
dd if=/dev/urandom of=/tmp/exactly-128k.txt bs=1024 count=128
```

**Upload and Download:**
```bash
> upload /tmp/exactly-128k.txt /test/exact-128k.txt
> download /test/exact-128k.txt /tmp/downloaded-exact-128k.txt
md5sum /tmp/exactly-128k.txt /tmp/downloaded-exact-128k.txt
```

Expected: Exactly 131072 bytes, checksums match

**Result:** ✅ PASS / ❌ FAIL

---

### Test 5.5: Source File Not Found

**Attempt Upload:**
```bash
> upload /nonexistent/source.txt /test/destination.txt
```

**Expected Output:**
```
Source file not found: /nonexistent/source.txt
```

**Result:** ✅ PASS / ❌ FAIL

---

### Test 5.6: Invalid Controller Connection

**Setup:** No controller running

**Start Client:**
```bash
./gradlew runReplicationClient -PappArgs="localhost 8000" --console=plain
```

**Attempt Upload:**
```bash
> upload /tmp/small-test.txt /test/file.txt
```

**Expected Output:**
```
Error: Connection refused
```

**Result:** ✅ PASS / ❌ FAIL

---

## Phase 6: Code Review Checklist

### 6.1 Base Class Architecture

**BaseController (src/main/java/csx55/dfs/base/BaseController.java)**

Review Points:
- [ ] `initiateRecovery()` correctly iterates through affected chunks
- [ ] Abstract methods `findAffectedChunks()` and `recoverChunk()` properly defined
- [ ] Failure detection threshold (180s) correctly implemented
- [ ] `selectNewServerForRecovery()` excludes existing servers and sorts by free space
- [ ] Heartbeat processing updates metadata correctly
- [ ] Thread safety with ConcurrentHashMap for server tracking

**BaseChunkServer (src/main/java/csx55/dfs/base/BaseChunkServer.java)**

Review Points:
- [ ] `sendMajorHeartbeat()` sends complete chunk inventory
- [ ] `sendMinorHeartbeat()` sends only new chunks
- [ ] Abstract methods `buildMajorHeartbeatChunks()` and `buildMinorHeartbeatChunks()` properly defined
- [ ] Heartbeat intervals (15s minor, 60s major) correctly implemented
- [x] **FIXED**: Immediate registration - `sendMajorHeartbeat()` called on startup before starting periodic threads (line 80)
- [ ] Server ID generation includes port number
- [ ] Free space calculation accurate

**BaseClient (src/main/java/csx55/dfs/base/BaseClient.java)**

Review Points:
- [ ] `readFileInChunks()` correctly splits files into 64KB chunks
- [ ] `validateChunkDataResponse()` properly checks for errors
- [ ] `requireResponseType()` validates message types
- [ ] Path normalization via PathUtil works correctly
- [ ] Socket connections properly closed (try-with-resources)
- [ ] Command parsing handles whitespace correctly

---

### 6.2 Replication Implementation

**replication/Controller (src/main/java/csx55/dfs/replication/Controller.java)**

Review Points:
- [ ] `findAffectedChunks()` correctly identifies chunks on failed server
- [ ] `recoverChunk()` selects source and target servers appropriately
- [ ] Replication factor = 3 enforced
- [ ] Chunk location tracking (Map<String, List<String>>) thread-safe
- [ ] Recovery only initiated if replicas < 3
- [ ] Error handling for replication failures

**replication/ChunkServer (src/main/java/csx55/dfs/replication/ChunkServer.java)**

Review Points:
- [ ] Slice checksums (8KB slices) computed correctly using SHA-1
- [ ] `verifyChunkIntegrity()` detects corruption and reports slice number
- [ ] Pipeline forwarding to next servers works correctly
- [ ] Checksum header (160 bytes = 8 slices × 20 bytes) stored correctly
- [ ] `buildMajorHeartbeatChunks()` includes all chunks
- [ ] `buildMinorHeartbeatChunks()` includes only new chunks, then clears newChunks set
- [ ] Chunk metadata tracking accurate

**replication/Client (src/main/java/csx55/dfs/replication/Client.java)**

Review Points:
- [ ] Upload creates pipeline of servers correctly
- [ ] Download randomly selects from available replicas
- [ ] File reconstruction from chunks correct
- [ ] Error handling for corrupted chunks
- [ ] Server list output matches specification

---

### 6.3 Erasure Coding Implementation

**erasure/Controller (src/main/java/csx55/dfs/erasure/Controller.java)**

Review Points:
- [ ] `findAffectedChunks()` identifies affected fragments
- [ ] `recoverChunk()` reconstructs missing fragments using Reed-Solomon
- [ ] Replication factor = 9 (6 data + 3 parity) enforced
- [ ] Fragment location tracking (Map<String, Map<Integer, String>>) correct
- [ ] Recovery requires at least 6/9 fragments available
- [ ] Fragment reconstruction request sent correctly

**erasure/ChunkServer (src/main/java/csx55/dfs/erasure/ChunkServer.java)**

Review Points:
- [ ] Fragments stored with correct naming: filename_chunkN
- [ ] Fragment number included in metadata
- [ ] `buildMajorHeartbeatChunks()` includes fragment numbers
- [ ] `buildMinorHeartbeatChunks()` includes only new fragments
- [ ] `storeFragment()` uses IOUtils correctly
- [ ] `readFragment()` with existence check
- [ ] Reconstruction logic assembles fragments correctly

**erasure/Client (src/main/java/csx55/dfs/erasure/Client.java)**

Review Points:
- [ ] `encodeChunk()` creates 6 data + 3 parity shards correctly
- [ ] Shard size calculation: (chunkSize + 5) / 6 correct
- [ ] `decodeFragments()` reconstructs data from any 6+ fragments
- [ ] Fragment retrieval tries all fragments, handles failures
- [ ] Download requires minimum 6 fragments per chunk
- [ ] Reed-Solomon library used correctly (encodeParity, decodeMissing)

---

### 6.4 Utility Classes

**util/NetworkUtils (src/main/java/csx55/dfs/util/NetworkUtils.java)**

Review Points:
- [ ] `sendRequestToServer()` parses address correctly
- [ ] Socket connections closed properly
- [ ] Error handling includes logging
- [ ] `sendRequestWithLogging()` returns null on error (callers must check)

**util/IOUtils (src/main/java/csx55/dfs/util/IOUtils.java)**

Review Points:
- [ ] `writeWithDirectoryCreation()` creates parent directories
- [ ] `readWithExistenceCheck()` throws FileNotFoundException appropriately
- [ ] Atomic file operations (no partial writes)

**util/PathUtil (src/main/java/csx55/dfs/util/PathUtil.java)**

Review Points:
- [ ] Path normalization adds leading slash if missing
- [ ] Handles absolute paths correctly
- [ ] No path traversal vulnerabilities (../)

**util/DFSConfig (src/main/java/csx55/dfs/util/DFSConfig.java)**

Review Points:
- [ ] CHUNK_SIZE = 64KB correct
- [ ] DATA_SHARDS = 6, PARITY_SHARDS = 3, TOTAL_SHARDS = 9 correct
- [ ] Heartbeat intervals: MINOR = 15s, MAJOR = 60s correct
- [ ] Failure threshold = 180s correct

---

### 6.5 Protocol and Transport

**protocol/* Messages**

Review Points:
- [ ] All messages implement Serializable
- [ ] Message types correctly defined in MessageType enum
- [ ] Request/response pairing correct
- [ ] HeartbeatMessage includes ChunkInfo properly
- [ ] ChunkDataResponse includes success flag and error message

**transport/TCPConnection**

Review Points:
- [ ] Object serialization/deserialization works correctly
- [ ] Address parsing handles hostname:port format
- [ ] Socket streams flushed after writes
- [ ] Resources closed properly

---

## Test Results Summary

| Test ID | Test Name | Status | Notes |
|---------|-----------|--------|-------|
| 1.1 | Replication Small File | ⬜ | |
| 1.2 | Replication Multi-Chunk | ⬜ | |
| 1.3 | Erasure Small File | ⬜ | |
| 1.4 | Erasure Multi-Chunk | ⬜ | |
| 2.1 | Initial Registration | ⬜ | |
| 2.2 | Minor Heartbeat | ⬜ | |
| 2.3 | Major Heartbeat | ⬜ | |
| 3.1 | Corruption Detection | ⬜ | |
| 3.2 | Fragment Loss Tolerance | ⬜ | |
| 4.1 | Single Server Failure | ⬜ | |
| 4.2 | Multiple Server Failures | ⬜ | |
| 4.3 | Fragment Recovery | ⬜ | |
| 5.1 | Insufficient Servers | ⬜ | |
| 5.2 | File Not Found | ⬜ | |
| 5.3 | Empty File | ⬜ | |
| 5.4 | Exact Chunk Boundary | ⬜ | |
| 5.5 | Source Not Found | ⬜ | |
| 5.6 | Invalid Controller | ⬜ | |

Legend: ⬜ Not Tested | ✅ PASS | ❌ FAIL

---

## Notes and Observations

Use this section to record any issues, unexpected behavior, or observations during testing.

### Issues Found

1. **ChunkServer Registration Delay (FIXED)** - BaseChunkServer.java:79
   - **Problem**: Heartbeat threads slept before first send, causing 60-second delay before servers registered with Controller
   - **Impact**: Upload operations failed with "Controller did not return N chunk servers" until registration completed
   - **Fix**: Added immediate `sendMajorHeartbeat()` call in `start()` method before starting periodic threads
   - **Status**: ✅ Fixed - servers now register immediately upon startup

2. **Erasure Coding File Size Padding (CRITICAL)** - erasure/Client.java:149
   - **Problem**: `decodeFragments()` always receives `CHUNK_SIZE` (64KB) instead of actual chunk data size
   - **Impact**: Downloaded files are padded with zeros to full CHUNK_SIZE, corrupting file integrity
   - **Example**: 43-byte file downloads as 65,536 bytes (first 43 bytes correct, rest zeros)
   - **Root Cause**: System lacks metadata to track actual file/chunk sizes
   - **Required Fix**: Add file size metadata storage in Controller + FileInfoResponse
   - **Status**: ❌ Needs architectural changes - blocks Erasure Coding mode functionality

### Performance Observations

1.

### Code Review Findings

1.

---

## Conclusion

Summary of testing results and overall system assessment.

**Overall Status:** ⬜ Not Complete | ✅ All Tests Passed | ❌ Issues Found

**Blockers:**

**Recommendations:**
