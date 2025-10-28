# Distributed File System - Research & Reference Implementations

This document summarizes research findings on distributed file system implementations that can serve as references for the CS555 HW4 project.

## Table of Contents
1. [Key Reference Implementations](#key-reference-implementations)
2. [Architecture Patterns](#architecture-patterns)
3. [Replication Strategies](#replication-strategies)
4. [Erasure Coding Details](#erasure-coding-details)
5. [Implementation Patterns](#implementation-patterns)
6. [Code Examples & Resources](#code-examples--resources)

---

## Key Reference Implementations

### 1. maxhayne/distributed-file-system (GitHub)
**Most Relevant - Java-based DFS with both replication and erasure coding**

- **Language**: Java
- **Architecture**: Controller + ChunkServer + Client (exactly matches our assignment)
- **Storage**: 64KB chunks stored in `/tmp/` (matches assignment)
- **Replication**: 3x replication factor (matches assignment)
- **Erasure Coding**: 9 shards (6 data + 3 parity) using Reed-Solomon (matches assignment k=6, m=3)
- **Data Integrity**: SHA-1 checksums for 8KB slices (matches assignment)
- **GitHub**: https://github.com/maxhayne/distributed-file-system

**Key Insights**:
- Uses Backblaze Reed-Solomon library (same one we have)
- Controller maintains metadata about file-to-server mappings
- Monitors node health through heartbeat messages
- Coordinates chunk relocation when servers fail
- Client stores retrieved files in local `reads` directory

**Performance Notes**:
- Erasure coding: ~20MB disk usage, higher CPU/network overhead
- Replication: ~28.5MB disk usage, lower CPU overhead
- Trade-off: 30% storage savings vs. increased computation

### 2. Apache Hadoop HDFS
**Production-grade reference for heartbeat and pipeline patterns**

- **Heartbeat Protocol**: DataNode → NameNode every 3 seconds
- **Heartbeat Contents**: Capacity, space used, active transfers
- **Failure Detection**: 10 minutes without heartbeat = node failure
- **Pipeline Replication**: Client → A → B → C (exactly our pattern)
- **Documentation**: https://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-hdfs/HdfsDesign.html

**Key Implementation Details**:
```java
// HDFS heartbeat pattern
namenode.sendHeartbeat(
    dnRegistration,
    data.getCapacity(),
    data.getDfsUsed(),
    data.getRemaining(),
    ...
)
// Returns DatanodeCommand[] for execution
```

### 3. Backblaze Reed-Solomon (JavaReedSolomon)
**The exact library used in our assignment**

- **GitHub**: https://github.com/Backblaze/JavaReedSolomon
- **License**: MIT
- **Performance**: 149 MB/s single-threaded, up to 525 MB/s with optimizations
- **Classes**:
  - `ReedSolomon`: Encoding/decoding operations
  - `Matrix`: Matrix arithmetic
  - `Galois`: Finite field mathematics

**Example Usage Pattern**:
```java
// Encoding
ReedSolomon reedSolomon = new ReedSolomon(DATA_SHARDS, PARITY_SHARDS);
byte[][] shards = new byte[TOTAL_SHARDS][shardSize];
// Fill data shards...
reedSolomon.encodeParity(shards, 0, shardSize);

// Decoding
boolean[] shardPresent = new boolean[TOTAL_SHARDS];
// Mark which shards are available...
reedSolomon.decodeMissing(shards, shardPresent, 0, shardSize);
```

---

## Architecture Patterns

### Distributed Controller Pattern
**Used by GFS, HDFS, and our assignment**

```
┌──────────────┐
│  Controller  │ ← Centralized metadata, coordination
└──────┬───────┘
       │
   ┌───┴───┬─────┬─────┐
   ▼       ▼     ▼     ▼
┌────┐  ┌────┐ ┌────┐ ┌────┐
│CS1 │  │CS2 │ │CS3 │ │CS4 │  ← Distributed storage nodes
└────┘  └────┘ └────┘ └────┘
```

**Responsibilities**:
- **Controller**: Metadata management, chunk placement, failure detection, recovery coordination
- **ChunkServer**: Local storage, heartbeat transmission, data serving
- **Client**: File splitting/assembly, coordinator communication

### Heartbeat Pattern
**Standard distributed system health monitoring**

**Major Heartbeat (60s)**:
- ALL chunks metadata
- Total storage capacity
- Free space available
- Chunk count

**Minor Heartbeat (15s)**:
- Newly added chunks only
- Current statistics

**Failure Detection**:
- Threshold: 3 missed major heartbeats (180 seconds)
- Action: Mark node failed, initiate recovery

---

## Replication Strategies

### 3x Replication (Standard Pattern)
**Used by GFS, HDFS, and assignment requirement**

**Key Requirements**:
1. Each chunk replicated exactly 3 times
2. No server holds multiple replicas of same chunk
3. Geographic/rack diversity (if applicable)

**Write Pipeline Pattern**:
```
Client
  │
  └─> ChunkServer A
        │
        └─> ChunkServer B
              │
              └─> ChunkServer C
```

**Benefits**:
- Simple implementation
- Fast reads (pick any replica)
- Low CPU overhead
- Immediate consistency

**Drawbacks**:
- 3x storage overhead (200% overhead)
- Network bandwidth for 3 copies

**Read Pattern**:
- Controller returns random replica
- Load balancing across replicas
- Fallback to other replicas if one fails

**Recovery**:
- Detect missing replica via heartbeat
- Controller instructs healthy node to replicate
- Direct chunk-to-chunk transfer (no controller data flow)

---

## Erasure Coding Details

### Reed-Solomon (k=6, m=3)
**Assignment specification**

**Configuration**:
- **k (data shards)**: 6
- **m (parity shards)**: 3
- **Total shards**: 9
- **Minimum for reconstruction**: Any 6 out of 9

**Storage Efficiency**:
- Overhead: 50% (vs 200% for 3x replication)
- Can tolerate up to 3 simultaneous failures
- Same fault tolerance as 3x replication with less storage

### Encoding Process

```
1. Take 64KB chunk
2. Add 4-byte length prefix → storedSize
3. Calculate shard size: (storedSize + k - 1) / k
4. Pad to make divisible by k
5. Split into k data shards
6. Use Reed-Solomon to generate m parity shards
7. Distribute 9 shards to 9 different servers
```

**Mathematical Foundation**:
- Based on polynomial interpolation
- Uses Galois Field (GF) arithmetic
- Matrix representation of data
- Any k equations can solve for k unknowns

### Decoding/Recovery Process

```
1. Retrieve available shards (need at least k=6)
2. Create empty buffers for missing shards
3. Call reedSolomon.decodeMissing(shards, shardPresent, ...)
4. Reconstruct missing shards
5. Combine first k shards to get original data
6. Remove padding, extract length, return data
```

**Corruption Handling**:
- Each shard has SHA-1 checksum
- Corrupted shard = missing shard
- Need k valid shards for reconstruction
- Can reconstruct from any k of n shards

---

## Implementation Patterns

### 1. SHA-1 Checksum Pattern (8KB Slices)

**For Replication (64KB chunk → 8 slices)**:
```java
public byte[][] computeSliceChecksums(byte[] chunkData) {
    MessageDigest digest = MessageDigest.getInstance("SHA-1");
    int SLICE_SIZE = 8 * 1024; // 8KB
    int numSlices = 8; // 64KB / 8KB
    byte[][] checksums = new byte[numSlices][20]; // SHA-1 = 20 bytes

    for (int i = 0; i < numSlices; i++) {
        int offset = i * SLICE_SIZE;
        int length = Math.min(SLICE_SIZE, chunkData.length - offset);
        digest.update(chunkData, offset, length);
        checksums[i] = digest.digest();
    }
    return checksums;
}
```

**Storage Format**:
```
[20B checksum slice 0]
[20B checksum slice 1]
...
[20B checksum slice 7]
[actual 64KB chunk data]
```

**Verification**:
```java
public int verifyChunkIntegrity(byte[] data, byte[][] storedChecksums) {
    byte[][] computed = computeSliceChecksums(data);
    for (int i = 0; i < computed.length; i++) {
        if (!Arrays.equals(computed[i], storedChecksums[i])) {
            return i + 1; // Return 1-indexed slice number (for output)
        }
    }
    return -1; // All valid
}
```

### 2. TCP Connection Management Pattern

**Message Protocol**:
```java
// Send message
public void sendMessage(Message msg, OutputStream out) {
    byte[] data = serialize(msg);
    DataOutputStream dos = new DataOutputStream(out);
    dos.writeInt(data.length); // Length prefix
    dos.write(data);           // Message data
    dos.flush();
}

// Receive message
public Message receiveMessage(InputStream in) {
    DataInputStream dis = new DataInputStream(in);
    int length = dis.readInt();
    byte[] data = new byte[length];
    dis.readFully(data);
    return deserialize(data);
}
```

**Connection Pattern**:
```java
// Server side
ServerSocket serverSocket = new ServerSocket(port);
while (running) {
    Socket client = serverSocket.accept();
    new Thread(() -> handleConnection(client)).start();
}

// Client side
Socket socket = new Socket(host, port);
// Send request, receive response
socket.close();
```

### 3. Heartbeat Thread Pattern

```java
// Minor heartbeat thread (15s)
Thread minorHeartbeat = new Thread(() -> {
    while (running) {
        Thread.sleep(15000);
        sendMinorHeartbeat();
    }
});
minorHeartbeat.setDaemon(true);
minorHeartbeat.start();

// Major heartbeat thread (60s)
Thread majorHeartbeat = new Thread(() -> {
    while (running) {
        Thread.sleep(60000);
        sendMajorHeartbeat();
    }
});
majorHeartbeat.setDaemon(true);
majorHeartbeat.start();
```

**Important**: At 2-minute mark, ONLY send major heartbeat (not both).

### 4. Chunk Server Selection Algorithm

**Considerations**:
- Free space available (1GB - used)
- Load balancing
- Cannot select same server multiple times
- Prefer servers with more free space

```java
public List<String> selectChunkServers(int count) {
    List<ChunkServerInfo> servers = new ArrayList<>(chunkServers.values());

    // Sort by free space (descending)
    servers.sort((a, b) -> Long.compare(b.freeSpace, a.freeSpace));

    // Select top N with most free space
    List<String> selected = new ArrayList<>();
    for (int i = 0; i < count && i < servers.size(); i++) {
        selected.add(servers.get(i).serverId);
    }

    return selected;
}
```

### 5. Pipeline Write Pattern

**Client sends to first server only**:
```java
public void writeChunkPipeline(String filename, int chunkNumber,
                               byte[] data, List<String> servers) {
    // Connect to first server
    Socket socket = new Socket(servers.get(0).host, servers.get(0).port);

    // Send chunk + list of next servers
    StoreChunkRequest request = new StoreChunkRequest(
        filename,
        chunkNumber,
        data,
        servers.subList(1, servers.size()) // Next servers
    );

    sendMessage(request, socket.getOutputStream());

    // Wait for ack
    Message response = receiveMessage(socket.getInputStream());
    socket.close();
}
```

**ChunkServer forwards to next**:
```java
public void handleStoreChunk(StoreChunkRequest request) {
    // Store locally
    storeChunk(request.getFilename(), request.getChunkNumber(), request.getData());

    // Forward to next server if any
    if (!request.getNextServers().isEmpty()) {
        String nextServer = request.getNextServers().get(0);
        forwardChunk(nextServer, request);
    }

    // Send ack back to client
    sendAck();
}
```

---

## Code Examples & Resources

### Reference Implementations

1. **maxhayne/distributed-file-system**
   - URL: https://github.com/maxhayne/distributed-file-system
   - Language: Java
   - Relevance: ⭐⭐⭐⭐⭐ (Exact match to assignment)
   - Contains: Complete implementation of controller, chunk server, client

2. **Backblaze JavaReedSolomon**
   - URL: https://github.com/Backblaze/JavaReedSolomon
   - Language: Java
   - Relevance: ⭐⭐⭐⭐⭐ (We're using this library)
   - Contains: Reed-Solomon encoding/decoding, examples

3. **ruxuebu/Distributed-File-System-based-on-Java-RMI**
   - URL: https://github.com/ruxuebu/Distributed-File-System-based-on-Java-RMI
   - Language: Java (RMI-based)
   - Relevance: ⭐⭐⭐ (Similar architecture, but RMI instead of TCP)

### Documentation Resources

1. **Apache HDFS Architecture**
   - URL: https://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-hdfs/HdfsDesign.html
   - Relevance: ⭐⭐⭐⭐ (Production patterns for heartbeat, replication)

2. **GFS Paper Notes**
   - URL: https://distributed-computing-musings.com/2023/07/paper-notes-the-google-file-system/
   - Relevance: ⭐⭐⭐⭐ (Original design patterns)

3. **Erasure Coding Deep Dive**
   - URL: https://transactional.blog/blog/2024-erasure-coding
   - Relevance: ⭐⭐⭐⭐ (Theory and implementation)

### Code Snippets

1. **Java SHA-1 File Checksum**
   - Source: HowToDoInJava, Mkyong, Baeldung
   - Pattern: Read file in chunks, update MessageDigest

2. **HDFS Heartbeat Protocol**
   - Source: Apache Hadoop source code
   - API: `DatanodeProtocol.sendHeartbeat()`

3. **Reed-Solomon Example**
   - Source: Backblaze blog post
   - URL: https://www.backblaze.com/blog/reed-solomon/

---

## Key Takeaways for Implementation

### What to Implement First (Priority Order)

1. **Basic Infrastructure** (Week 1)
   - TCP connection handling
   - Message protocol
   - Basic Controller/ChunkServer/Client shells

2. **Replication Mode** (Week 2)
   - File chunking (64KB)
   - Pipeline replication (A→B→C)
   - SHA-1 checksum computation
   - Heartbeat system
   - Basic upload/download

3. **Fault Tolerance** (Week 3)
   - Corruption detection
   - Error correction from replicas
   - Failure detection
   - Recovery coordination

4. **Erasure Coding** (Week 4)
   - Reed-Solomon integration
   - Fragment distribution
   - Reconstruction logic
   - Same fault tolerance as replication

### Critical Implementation Notes

1. **Controller NEVER sees chunk data**
   - Controller coordinates, doesn't transfer
   - All data flows: Client ↔ ChunkServer or ChunkServer ↔ ChunkServer
   - Deduction: -1 point if violated

2. **No duplicate replicas on same server**
   - Check before assignment
   - Deduction: -1 point if violated

3. **Heartbeat timing**
   - Minor: Every 15s
   - Major: Every 60s
   - At 2-minute mark: ONLY major (not both)

4. **Chunk numbering**
   - 1-indexed for output (not 0-indexed)
   - Slice numbering also 1-indexed

5. **Output format matters**
   - Upload: Print all replicas (3 per chunk)
   - Download: Print only servers used (1 per chunk)
   - Corruption: `<ip>:<port> <chunk> <slice> is corrupted`

---

## Performance Comparison

### Storage Efficiency
- **3x Replication**: 300% of original size (200% overhead)
- **Erasure (6+3)**: 150% of original size (50% overhead)
- **Savings**: ~47% reduction with erasure coding

### Computational Cost
- **Replication**: Low (just copy operations)
- **Erasure**: High (matrix operations, Galois field arithmetic)

### Network Cost
- **Replication**: 3 transfers per chunk (in pipeline)
- **Erasure**: 9 transfers per chunk (parallel)

### Fault Tolerance
- **Both**: Can tolerate 3 simultaneous failures
- **Replication**: Direct copy recovery
- **Erasure**: Reconstruction from any 6 shards

---

## Testing Strategy

### Unit Testing
1. SHA-1 checksum computation
2. Reed-Solomon encoding/decoding
3. Message serialization
4. Chunk splitting/assembly

### Integration Testing
1. Controller ↔ ChunkServer communication
2. Client ↔ Controller communication
3. Pipeline replication flow
4. Heartbeat mechanism

### System Testing
1. Upload/download with 3 chunk servers
2. Corruption detection and recovery
3. Server failure and recovery
4. Large file handling (multiple chunks)

### Comparison Testing
1. Run same test in both modes
2. Compare storage usage
3. Compare performance
4. Verify same fault tolerance

---

## Questions to Resolve

1. **Path normalization**: Assignment says no leading `./`, but allow leading `/` or not?
   - Answer: Both accepted, normalize internally

2. **Failure detection threshold**: Assignment says "does not receive heartbeats" but doesn't specify timeout
   - Reference: HDFS uses 10 minutes (20 heartbeats)
   - Recommendation: 3 major heartbeats = 180 seconds

3. **Recovery timing**: When to initiate recovery after failure?
   - Reference: HDFS initiates after detecting failure
   - Recommendation: Immediate on detection

4. **Checksum storage**: Where to store checksums?
   - Reference: maxhayne stores as prefix in chunk file
   - Assignment: Says "adds integrity information before writing"

---

*Research compiled: 2025-10-28*
*Assignment: CS555-Fall25-HW4*
*Sources: GitHub, Apache Hadoop, Backblaze, Academic Papers*
