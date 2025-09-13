# Consensus Algorithms Documentation

This document provides a comprehensive explanation of the consensus algorithms implemented in the Distributed Loan Ledger system, including their theoretical foundations, implementation details, and practical considerations.

## Table of Contents

1. [Overview](#overview)
2. [Consensus Problem](#consensus-problem)
3. [Trust Models](#trust-models)
4. [Raft Consensus Algorithm](#raft-consensus-algorithm)
5. [PBFT (Practical Byzantine Fault Tolerance)](#pbft-practical-byzantine-fault-tolerance)
6. [Comparison and Trade-offs](#comparison-and-trade-offs)
7. [Implementation Considerations](#implementation-considerations)
8. [Testing Consensus](#testing-consensus)

## Overview

Consensus algorithms enable distributed systems to agree on a single value or sequence of values despite failures, network delays, and potentially malicious actors. In our loan ledger system, consensus ensures all nodes agree on:

1. The order of loan application events
2. The current state of each loan application
3. Which node is the leader (in leader-based algorithms)

## Consensus Problem

### The Fundamental Challenge

In a distributed system with multiple nodes:
- Nodes may fail (crash, become unresponsive)
- Network may partition (nodes can't communicate)
- Messages may be delayed, lost, or reordered
- Nodes may be malicious (Byzantine failures)

Despite these challenges, we need:
- **Agreement**: All honest nodes decide on the same value
- **Validity**: The decided value was proposed by some node
- **Termination**: All honest nodes eventually decide
- **Integrity**: Each node decides at most once

### CAP Theorem Implications

According to the CAP theorem, distributed systems can guarantee at most two of:
- **Consistency**: All nodes see the same data
- **Availability**: System remains operational
- **Partition Tolerance**: System continues despite network failures

Our consensus implementations make different trade-offs:
- **Raft**: Prioritizes consistency over availability during partitions
- **PBFT**: Maintains consistency with Byzantine fault tolerance but requires more communication

## Trust Models

### Crash Fault Tolerance (CFT)

**Assumption**: Nodes may crash but never lie or act maliciously

**Characteristics**:
- Nodes follow the protocol correctly or stop responding
- Failed nodes don't send incorrect messages
- Suitable for systems within a single organization

**Algorithms**: Raft, Paxos, Viewstamped Replication

### Byzantine Fault Tolerance (BFT)

**Assumption**: Nodes may exhibit arbitrary behavior, including lying

**Characteristics**:
- Nodes may send conflicting messages
- Nodes may collude to subvert the system
- Suitable for systems spanning multiple untrusted organizations

**Algorithms**: PBFT, HotStuff, Tendermint

## Raft Consensus Algorithm

### Overview

Raft is designed for understandability while providing the same guarantees as Paxos. It separates consensus into three key problems:

1. **Leader Election**: Selecting one node to coordinate
2. **Log Replication**: Distributing operations from leader to followers
3. **Safety**: Ensuring consistency despite failures

### Node States

```
        ┌─────────────┐
        │  Follower   │◄────────────┐
        └─────┬───────┘             │
              │                     │
     Election │                     │ Discovers
     Timeout  │                     │ current leader
              │                     │ or new term
              ▼                     │
        ┌─────────────┐             │
        │  Candidate  │─────────────┘
        └─────┬───────┘
              │
     Receives │
     majority │
     votes    │
              ▼
        ┌─────────────┐
        │   Leader    │
        └─────────────┘
```

### Leader Election

#### Election Process

1. **Follower State**
   - Nodes start as followers
   - Reset election timer on receiving heartbeat from leader
   - Timer duration: random between 150-300ms

2. **Becoming Candidate**
   ```java
   // When election timer expires
   currentTerm++;
   votedFor = self;
   state = CANDIDATE;
   
   // Request votes from all other nodes
   for (Node peer : peers) {
       sendRequestVote(peer, currentTerm, lastLogIndex, lastLogTerm);
   }
   ```

3. **Voting Rules**
   - Vote for at most one candidate per term
   - Only vote if candidate's log is at least as up-to-date
   - First-come-first-served within a term

4. **Becoming Leader**
   - Need votes from majority (⌊n/2⌋ + 1)
   - Start sending heartbeats immediately
   - Initialize nextIndex[] and matchIndex[] for each follower

#### Split Vote Handling

If multiple candidates split the vote:
- No candidate gets majority
- Candidates timeout and start new election
- Random timeouts reduce probability of repeated splits

### Log Replication

#### Log Structure

```
Leader's Log:
┌───┬───┬───┬───┬───┬───┬───┐
│ 1 │ 1 │ 1 │ 2 │ 2 │ 3 │ 3 │  Terms
├───┼───┼───┼───┼───┼───┼───┤
│ x │ y │ z │ a │ b │ c │ d │  Commands
└───┴───┴───┴───┴───┴───┴───┘
  1   2   3   4   5   6   7    Log Index

Follower's Log (needs updates):
┌───┬───┬───┬───┬───┐
│ 1 │ 1 │ 1 │ 2 │ 2 │
├───┼───┼───┼───┼───┤
│ x │ y │ z │ a │ b │
└───┴───┴───┴───┴───┘
  1   2   3   4   5
```

#### AppendEntries RPC

**Leader sends:**
```java
class AppendEntriesRequest {
    int term;                // Leader's term
    String leaderId;          // So follower can redirect clients
    int prevLogIndex;         // Index of log entry immediately preceding new ones
    int prevLogTerm;          // Term of prevLogIndex entry
    LogEntry[] entries;       // Log entries to store (empty for heartbeat)
    int leaderCommit;         // Leader's commitIndex
}
```

**Follower responds:**
```java
class AppendEntriesResponse {
    int term;                 // Current term, for leader to update itself
    boolean success;          // True if follower contained entry matching prevLogIndex and prevLogTerm
    int matchIndex;           // Highest log index known to be replicated
}
```

#### Replication Process

1. **Client request arrives at leader**
2. **Leader appends to local log**
3. **Leader sends AppendEntries to all followers**
4. **Followers append entries and respond**
5. **Leader commits when majority acknowledge**
6. **Leader includes commit index in next AppendEntries**
7. **Followers commit up to leader's commit index**

### Safety Properties

#### Election Restriction

**Property**: Leader for term T must contain all entries committed in terms < T

**Implementation**:
```java
boolean isUpToDate(int lastLogIndex, int lastLogTerm) {
    int ourLastTerm = getLastLogTerm();
    if (lastLogTerm != ourLastTerm) {
        return lastLogTerm > ourLastTerm;
    }
    return lastLogIndex >= getLastLogIndex();
}
```

#### Log Matching Property

If two logs contain an entry with the same index and term:
1. They store the same command
2. The logs are identical in all preceding entries

#### Leader Completeness Property

If a log entry is committed in a given term, it will be present in the logs of leaders for all higher-numbered terms.

### Cluster Membership Changes

#### Joint Consensus Approach

1. Leader creates log entry with C_old,new configuration
2. Replicates to majority of both old and new configuration
3. Creates log entry with C_new configuration
4. Replicates to majority of new configuration

```
Time →
     C_old          C_old,new           C_new
       │                │                 │
───────┴────────────────┴─────────────────┴───────►
   Old config      Joint consensus    New config
```

## PBFT (Practical Byzantine Fault Tolerance)

### Overview

PBFT enables consensus among n = 3f + 1 nodes where f nodes may be Byzantine (malicious). It provides:
- Safety under asynchrony
- Liveness under weak synchrony assumptions
- Optimal Byzantine fault tolerance

### System Model

**Assumptions**:
- Network may fail to deliver, delay, duplicate, or reorder messages
- Faulty nodes may behave arbitrarily
- Cryptographic techniques cannot be subverted
- At most f out of 3f+1 nodes are faulty

### Protocol Phases

```
Client    Primary    Replica1   Replica2   Replica3
  │          │           │          │          │
  ├─Request─>│           │          │          │
  │          ├─PrePrepare>          │          │
  │          ├─PrePrepare───────────>          │
  │          ├─PrePrepare──────────────────────>
  │          │           │          │          │
  │          │<─Prepare──│          │          │
  │          │<─Prepare──────────────          │
  │          │<─Prepare─────────────────────────
  │          ├─Prepare──>│          │          │
  │          ├─Prepare──────────────>          │
  │          ├─Prepare─────────────────────────>
  │          │           ├─Prepare─>│          │
  │          │           ├─Prepare─────────────>
  │          │           │<─Prepare─│          │
  │          │           │          ├─Prepare─>│
  │          │           │          │<─Prepare─│
  │          │           │          │          │
  │          │<─Commit───│          │          │
  │          │<─Commit───────────────          │
  │          │<─Commit──────────────────────────
  │          ├─Commit───>│          │          │
  │          ├─Commit───────────────>          │
  │          ├─Commit──────────────────────────>
  │          │           ├─Commit──>│          │
  │          │           ├─Commit──────────────>
  │          │           │<─Commit──│          │
  │          │           │          ├─Commit──>│
  │          │           │          │<─Commit──│
  │          │           │          │          │
  │<─Reply───│           │          │          │
  │<─Reply───────────────│          │          │
  │<─Reply──────────────────────────│          │
  │<─Reply─────────────────────────────────────│
```

### Phase 1: Request

**Client → Primary**

```java
class ClientRequest {
    String clientId;
    long timestamp;      // For ordering and duplicate detection
    Operation operation; // The loan event to process
    String signature;    // Client's signature
}
```

The primary assigns a sequence number and initiates consensus.

### Phase 2: Pre-Prepare

**Primary → All Replicas**

```java
class PrePrepareMessage {
    int viewNumber;           // Current view
    int sequenceNumber;       // Assigned sequence
    String digest;            // Hash of client request
    ClientRequest request;    // The actual request
    String primarySignature;  // Primary's signature
}
```

**Acceptance Criteria**:
1. Signatures are valid
2. View number is current
3. Sequence number is in valid range
4. No other pre-prepare with same v,n but different digest

### Phase 3: Prepare

**All Replicas → All Replicas**

```java
class PrepareMessage {
    int viewNumber;
    int sequenceNumber;
    String digest;
    int replicaId;
    String signature;
}
```

**Purpose**:
- Ensure total order across views
- Replicas agree on sequence number for request

**Prepared Predicate**:
A replica considers a request prepared when it has:
1. The pre-prepare message
2. 2f matching prepare messages from different replicas

### Phase 4: Commit

**All Replicas → All Replicas**

```java
class CommitMessage {
    int viewNumber;
    int sequenceNumber;
    String digest;
    int replicaId;
    String signature;
}
```

**Purpose**:
- Ensure request commits at all non-faulty replicas
- Agreement on execution order

**Committed Predicate**:
A replica considers a request committed when it has:
1. The request is prepared
2. 2f+1 matching commit messages (including its own)

### Phase 5: Reply

**All Replicas → Client**

```java
class ReplyMessage {
    int viewNumber;
    long timestamp;      // Client's timestamp
    int replicaId;
    Result result;       // Execution result
    String signature;
}
```

**Client Acceptance**:
- Client waits for f+1 matching replies
- Ensures at least one honest replica executed request

### View Changes

View changes handle primary failures:

#### Triggering View Change

1. **Backup detects primary failure** (timeout waiting for request execution)
2. **Backup broadcasts VIEW-CHANGE message**

```java
class ViewChangeMessage {
    int newViewNumber;
    int lastStableCheckpoint;
    Set<CheckpointProof> checkpointProofs;
    Set<PreparedProof> preparedProofs;
    int replicaId;
    String signature;
}
```

#### New View Message

When new primary receives 2f view-change messages:

```java
class NewViewMessage {
    int newViewNumber;
    Set<ViewChangeMessage> viewChangeProofs;
    Set<PrePrepareMessage> prePrepares; // For uncommitted requests
    String signature;
}
```

### Checkpoints

Checkpoints garbage-collect the log and provide recovery points:

```java
class CheckpointMessage {
    int sequenceNumber;  // Last executed request
    String stateDigest;  // Hash of application state
    int replicaId;
    String signature;
}
```

**Stable Checkpoint**: When 2f+1 replicas have matching checkpoint

### Optimizations

#### Tentative Execution

- Execute requests after prepare phase (optimistically)
- Roll back if commit phase fails
- Reduces latency by one message delay

#### Read-Only Optimization

- Client sends read request to all replicas
- Replicas execute immediately and reply
- Client waits for f+1 matching responses

#### Message Authentication Codes (MACs)

- Use MACs instead of digital signatures for most messages
- Reduces computational overhead significantly
- Keep signatures only for checkpoints and view changes

## Comparison and Trade-offs

### Performance Comparison

| Metric | Raft | PBFT |
|--------|------|------|
| **Message Complexity** | O(n) | O(n²) |
| **Round Trips** | 1-2 | 3-4 |
| **Throughput** | ~1000 tx/s | ~100 tx/s |
| **Latency** | ~10ms | ~100ms |
| **Fault Tolerance** | n/2 crash faults | n/3 Byzantine faults |

### When to Use Each

#### Use Raft When:
- All nodes belong to same organization
- Trust exists between nodes
- High performance is critical
- Simple implementation is desired
- Network is reliable

#### Use PBFT When:
- Multiple organizations participate
- Nodes may be compromised
- Regulatory compliance requires Byzantine tolerance
- Can tolerate higher latency
- Need protection against malicious behavior

### Scalability Considerations

#### Raft Scalability

**Limitations**:
- Leader bottleneck (all writes go through leader)
- Heartbeat traffic increases with cluster size
- Practical limit: ~7-9 nodes

**Solutions**:
- Use Raft groups (sharding)
- Delegate reads to followers
- Batch operations

#### PBFT Scalability

**Limitations**:
- O(n²) message complexity
- All-to-all communication pattern
- Practical limit: ~20-30 nodes

**Solutions**:
- Hierarchical PBFT
- Committee-based approaches
- Combine with proof-of-stake for node selection

## Implementation Considerations

### Network Assumptions

#### Raft Network Requirements
```java
class RaftNetworkConfig {
    int messageTimeout = 100;        // ms
    int electionTimeout = 150-300;   // ms
    int heartbeatInterval = 50;      // ms
    int maxMessageSize = 1048576;    // 1MB
    boolean requireOrderedDelivery = false;
}
```

#### PBFT Network Requirements
```java
class PBFTNetworkConfig {
    int requestTimeout = 1000;       // ms
    int viewChangeTimeout = 5000;    // ms
    int checkpointInterval = 100;    // requests
    int maxMessageSize = 1048576;    // 1MB
    boolean requireAuthentication = true;
}
```

### Storage Requirements

#### Raft Storage
```java
interface RaftStorage {
    // Persistent state (must survive restarts)
    void saveCurrentTerm(int term);
    void saveVotedFor(String nodeId);
    void saveLog(List<LogEntry> log);
    
    // Volatile state (can be rebuilt)
    void saveCommitIndex(int index);
    void saveLastApplied(int index);
}
```

#### PBFT Storage
```java
interface PBFTStorage {
    // Persistent state
    void saveViewNumber(int view);
    void saveLog(List<RequestEntry> log);
    void saveCheckpoint(Checkpoint checkpoint);
    
    // Message logs (can be garbage collected)
    void savePrePrepare(PrePrepareMessage msg);
    void savePrepare(PrepareMessage msg);
    void saveCommit(CommitMessage msg);
}
```

### Cryptographic Requirements

#### Raft Cryptography
- Optional TLS for transport security
- No cryptographic consensus requirements

#### PBFT Cryptography
```java
class PBFTCrypto {
    // Key generation
    KeyPair generateKeyPair();
    
    // Signing
    byte[] sign(byte[] message, PrivateKey key);
    
    // Verification
    boolean verify(byte[] message, byte[] signature, PublicKey key);
    
    // Digest
    byte[] sha256(byte[] data);
    
    // MAC for optimization
    byte[] hmac(byte[] message, byte[] key);
}
```

### Configuration Management

#### Dynamic Reconfiguration

**Raft**: Joint consensus for membership changes
```java
class RaftReconfiguration {
    void addNode(NodeInfo node) {
        // 1. Replicate C_old,new
        // 2. Replicate C_new
    }
    
    void removeNode(String nodeId) {
        // Similar two-phase process
    }
}
```

**PBFT**: Requires view change + reconfiguration protocol
```java
class PBFTReconfiguration {
    void changeConfiguration(Set<NodeInfo> newNodes) {
        // 1. Agree on reconfiguration request
        // 2. Execute view change to new configuration
        // 3. Transfer state if needed
    }
}
```

## Testing Consensus

### Unit Testing

#### Testing State Transitions
```java
@Test
public void testRaftFollowerToCandidate() {
    RaftNode node = new RaftNode("node1");
    node.setState(NodeState.FOLLOWER);
    
    // Trigger election timeout
    node.handleElectionTimeout();
    
    assertEquals(NodeState.CANDIDATE, node.getState());
    assertEquals(1, node.getCurrentTerm());
    assertEquals("node1", node.getVotedFor());
}
```

#### Testing Message Handling
```java
@Test
public void testPBFTPreparePhase() {
    PBFTNode node = new PBFTNode(1, 4);
    PrePrepareMessage prePrepare = createPrePrepare();
    
    node.handlePrePrepare(prePrepare);
    
    // Verify prepare message was broadcast
    verify(mockNetwork).broadcast(argThat(msg -> 
        msg instanceof PrepareMessage &&
        ((PrepareMessage) msg).getDigest().equals(prePrepare.getDigest())
    ));
}
```

### Integration Testing

#### Testing Leader Election
```java
@Test
public void testRaftLeaderElection() {
    RaftCluster cluster = new RaftCluster(3);
    cluster.start();
    
    // Wait for election
    Thread.sleep(500);
    
    // Verify exactly one leader
    long leaders = cluster.getNodes().stream()
        .filter(n -> n.getState() == NodeState.LEADER)
        .count();
    assertEquals(1, leaders);
}
```

#### Testing Byzantine Behavior
```java
@Test
public void testPBFTByzantineNode() {
    PBFTCluster cluster = new PBFTCluster(4);
    cluster.makeNodeByzantine(3); // Node 3 is Byzantine
    
    ClientRequest request = createRequest();
    cluster.submitRequest(request);
    
    // Should still reach consensus with 1 Byzantine node
    Eventually.assertThat(() -> 
        cluster.getHonestNodes().stream()
            .allMatch(n -> n.hasExecuted(request))
    );
}
```

### Chaos Testing

#### Network Partition Testing
```java
@Test
public void testRaftNetworkPartition() {
    RaftCluster cluster = new RaftCluster(5);
    cluster.start();
    
    // Create partition: {1,2} | {3,4,5}
    cluster.partition(Set.of(1,2), Set.of(3,4,5));
    
    // Minority should not have leader
    assertFalse(cluster.getNodes(1,2).stream()
        .anyMatch(n -> n.getState() == NodeState.LEADER));
    
    // Majority should elect leader
    assertTrue(cluster.getNodes(3,4,5).stream()
        .anyMatch(n -> n.getState() == NodeState.LEADER));
    
    // Heal partition
    cluster.healPartition();
    
    // Should converge to single leader
    Thread.sleep(1000);
    assertEquals(1, cluster.countLeaders());
}
```

#### Stress Testing
```java
@Test
public void testHighLoadConsensus() {
    int numRequests = 10000;
    ConsensusCluster cluster = createCluster();
    
    List<CompletableFuture<Result>> futures = new ArrayList<>();
    for (int i = 0; i < numRequests; i++) {
        futures.add(cluster.submitRequest(createRequest(i)));
    }
    
    // Wait for all requests
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    
    // Verify all nodes have same state
    List<String> stateHashes = cluster.getNodes().stream()
        .map(Node::getStateHash)
        .distinct()
        .collect(Collectors.toList());
    
    assertEquals(1, stateHashes.size());
}
```

### Performance Benchmarking

```java
public class ConsensusBenchmark {
    public static void main(String[] args) {
        int numNodes = 5;
        int numClients = 10;
        int requestsPerClient = 1000;
        
        ConsensusCluster cluster = new RaftCluster(numNodes);
        cluster.start();
        
        long startTime = System.currentTimeMillis();
        AtomicInteger completed = new AtomicInteger(0);
        
        // Concurrent clients
        ExecutorService executor = Executors.newFixedThreadPool(numClients);
        for (int c = 0; c < numClients; c++) {
            executor.submit(() -> {
                for (int r = 0; r < requestsPerClient; r++) {
                    cluster.submitRequest(createRequest());
                    completed.incrementAndGet();
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.MINUTES);
        
        long duration = System.currentTimeMillis() - startTime;
        double throughput = (completed.get() * 1000.0) / duration;
        
        System.out.println("Throughput: " + throughput + " requests/second");
    }
}
```

## Failure Scenarios

### Raft Failure Scenarios

1. **Leader Failure**
   - Followers detect via heartbeat timeout
   - New election initiated
   - New leader continues from last committed index

2. **Follower Failure**
   - Leader continues operating with remaining nodes
   - Failed follower catches up when recovered
   - May need snapshot if too far behind

3. **Network Partition**
   - Minority partition: No leader, no progress
   - Majority partition: Elects leader, continues
   - On heal: Minority accepts majority's log

4. **Split Brain Prevention**
   - Term numbers prevent old leaders
   - Majority requirement prevents multiple leaders

### PBFT Failure Scenarios

1. **Primary Failure**
   - Backups timeout waiting for pre-prepare
   - View change initiated
   - New primary selected deterministically

2. **Byzantine Primary**
   - Assigns same sequence number to different requests
   - Backups detect inconsistency in prepare phase
   - View change to remove Byzantine primary

3. **Byzantine Backup**
   - Sends conflicting prepare/commit messages
   - Honest nodes ignore after detecting inconsistency
   - Consensus proceeds with remaining 2f honest nodes

4. **Network Asynchrony**
   - Messages delayed arbitrarily
   - Safety maintained (no incorrect commits)
   - Liveness may be affected (may trigger view changes)

## Best Practices

### General Consensus Best Practices

1. **Use appropriate timeout values**
   - Too short: Unnecessary elections/view changes
   - Too long: Slow failure detection

2. **Implement proper retry logic**
   - Exponential backoff for failed requests
   - Circuit breakers for failed nodes

3. **Monitor consensus health**
   - Track leader changes / view changes
   - Monitor consensus latency
   - Alert on split-brain or Byzantine behavior

4. **Test failure scenarios**
   - Regular chaos engineering
   - Test network partitions
   - Verify recovery procedures

### Raft Best Practices

1. **Use odd number of nodes** (3, 5, 7)
2. **Place nodes in different failure domains**
3. **Use persistent storage for term, vote, and log**
4. **Implement log compaction/snapshots**
5. **Rate limit client requests at leader**

### PBFT Best Practices

1. **Use 3f+1 nodes for f Byzantine faults**
2. **Implement checkpoint garbage collection**
3. **Use MACs for optimization where possible**
4. **Monitor for Byzantine behavior patterns**
5. **Implement rate limiting to prevent DoS**

## Conclusion

The choice between Raft and PBFT depends on your trust model and performance requirements:

- **Choose Raft** for high-performance, trusted environments where nodes may crash but won't lie
- **Choose PBFT** for untrusted, multi-organizational deployments where Byzantine behavior is possible

Both algorithms provide strong consistency guarantees but with different trade-offs in terms of performance, complexity, and fault tolerance. Understanding these trade-offs is crucial for building robust distributed systems.