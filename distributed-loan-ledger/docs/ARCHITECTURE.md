# System Architecture

## Overview

The Distributed Loan Ledger is designed as a layered architecture that separates concerns and allows for pluggable implementations at each layer. This document details each component, its responsibilities, and how they interact to create a robust distributed system.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      External Systems                        │
│         (Credit Bureaus, Fraud Detection, Analytics)         │
└─────────────────────┬───────────────────────────────────────┘
                      │ Events/Queries
┌─────────────────────▼───────────────────────────────────────┐
│                    API Gateway Layer                         │
│  ┌─────────────┐ ┌──────────────┐ ┌──────────────────┐    │
│  │   REST API  │ │   WebSocket  │ │  Admin Interface │    │
│  └─────────────┘ └──────────────┘ └──────────────────┘    │
└─────────────────────┬───────────────────────────────────────┘
                      │ Requests
┌─────────────────────▼───────────────────────────────────────┐
│                  Consensus Engine Layer                      │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         Pluggable Consensus Implementation           │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────────┐      │  │
│  │  │   None   │  │   Raft   │  │     PBFT     │      │  │
│  │  └──────────┘  └──────────┘  └──────────────┘      │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────┬───────────────────────────────────────┘
                      │ Ordered Events
┌─────────────────────▼───────────────────────────────────────┐
│                   Core Business Layer                        │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐   │
│  │ State Machine│ │Event Processor│ │   Audit Logger   │   │
│  └──────────────┘ └──────────────┘ └──────────────────┘   │
└─────────────────────┬───────────────────────────────────────┘
                      │ Persist
┌─────────────────────▼───────────────────────────────────────┐
│                     Storage Layer                            │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐   │
│  │ Event Store  │ │State Snapshot│ │   Blockchain     │   │
│  └──────────────┘ └──────────────┘ └──────────────────┘   │
└──────────────────────────────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                    Network Layer                             │
│         (P2P Communication, Node Discovery, RPC)            │
└──────────────────────────────────────────────────────────────┘
```

## Layer Descriptions

### 1. API Gateway Layer

The API Gateway serves as the external interface for all client interactions with the distributed ledger.

#### Components

**REST API Controller** (`api/rest/LoanApplicationController.java`)
- **Purpose**: Handle HTTP requests for loan operations
- **Endpoints**:
  - `POST /api/v1/loans` - Submit new loan application
  - `GET /api/v1/loans/{id}` - Query loan status
  - `GET /api/v1/loans/{id}/events` - Get loan event history
  - `GET /api/v1/events` - Query events with filters
- **Responsibilities**:
  - Request validation
  - Response formatting
  - Error handling
  - Rate limiting

**WebSocket Manager** (`api/websocket/EventBroadcaster.java`)
- **Purpose**: Real-time event streaming to subscribers
- **Features**:
  - Event filtering by type, loan ID, or participant
  - Connection management
  - Automatic reconnection
  - Backpressure handling
- **Protocol**:
  ```json
  {
    "action": "SUBSCRIBE",
    "filters": {
      "loanId": "loan-123",
      "eventTypes": ["APPROVED", "REJECTED"]
    }
  }
  ```

**Admin Interface** (`api/admin/NodeStatusController.java`)
- **Purpose**: Cluster management and monitoring
- **Endpoints**:
  - `/admin/nodes` - List all nodes and their status
  - `/admin/metrics` - Performance metrics
  - `/admin/consensus` - Consensus state information
  - `/admin/health` - Health checks

### 2. Consensus Engine Layer

The Consensus Engine ensures all nodes agree on the order and content of events, even in the presence of failures or malicious actors.

#### Abstract Interface

```java
public abstract class ConsensusEngine {
    // Propose a new event to be added to the ledger
    public abstract CompletableFuture<Boolean> propose(LoanEvent event);
    
    // Start the consensus engine
    public abstract void start();
    
    // Stop the consensus engine
    public abstract void stop();
    
    // Get current consensus state
    public abstract ConsensusState getState();
    
    // Register event commit callback
    public abstract void onCommit(Consumer<LoanEvent> callback);
}
```

#### Consensus Implementations

**No Consensus** (`consensus/none/DirectConsensus.java`)
- **Use Case**: Development, testing, single-node deployments
- **Characteristics**:
  - Events immediately committed
  - No fault tolerance
  - Maximum performance
  - No network overhead

**Raft Consensus** (`consensus/raft/`)
- **Use Case**: Trusted environments (single organization)
- **Components**:
  - `RaftNode.java` - Core state machine
  - `RaftLog.java` - Replicated log management
  - `LeaderElection.java` - Election protocol
  - `LogReplication.java` - AppendEntries protocol
- **Key Features**:
  - Leader-based consensus
  - Log replication to majority
  - Automatic leader election
  - Strong consistency guarantees
- **Configuration**:
  ```java
  class RaftConfig {
      int electionTimeoutMin = 150;  // ms
      int electionTimeoutMax = 300;  // ms
      int heartbeatInterval = 50;    // ms
      int maxLogEntriesPerAppend = 100;
  }
  ```

**PBFT Consensus** (`consensus/pbft/`)
- **Use Case**: Untrusted environments (multi-organization)
- **Components**:
  - `PBFTNode.java` - Byzantine fault tolerant node
  - `ViewManager.java` - View changes on primary failure
  - `phases/` - Request, PrePrepare, Prepare, Commit phases
  - `MessageValidator.java` - Cryptographic verification
- **Key Features**:
  - Tolerates Byzantine failures
  - Three-phase commit protocol
  - View changes for liveness
  - Digital signatures for authenticity
- **Configuration**:
  ```java
  class PBFTConfig {
      int f = 1;                    // Number of faults to tolerate
      int nodeCount = 3 * f + 1;    // Total nodes needed
      int requestTimeout = 1000;    // ms
      int viewChangeTimeout = 5000; // ms
  }
  ```

### 3. Core Business Layer

The Core Business Layer implements the loan application domain logic and state management.

#### Components

**Loan State Machine** (`core/LoanStateMachine.java`)
- **Purpose**: Manage loan application state transitions
- **States**:
  ```
  SUBMITTED → CREDIT_CHECK → FRAUD_CHECK → UNDERWRITING → DECISION → FUNDED/REJECTED
  ```
- **Transition Rules**:
  ```java
  Map<LoanState, Set<EventType>> validTransitions = Map.of(
      SUBMITTED, Set.of(CREDIT_CHECK_INITIATED),
      CREDIT_CHECK, Set.of(CREDIT_CHECK_COMPLETE, APPLICATION_REJECTED),
      FRAUD_CHECK, Set.of(FRAUD_CHECK_COMPLETE, APPLICATION_REJECTED),
      // ...
  );
  ```

**Event Processor** (`core/LoanEventProcessor.java`)
- **Purpose**: Process events and update application state
- **Responsibilities**:
  - Validate events against business rules
  - Update loan state
  - Trigger side effects (notifications, etc.)
  - Maintain consistency

**Event Validator** (`core/EventValidator.java`)
- **Purpose**: Ensure event integrity and validity
- **Validations**:
  - Schema validation
  - Business rule validation
  - State transition validation
  - Duplicate detection

**Audit Logger** (`core/AuditLogger.java`)
- **Purpose**: Maintain compliance audit trail
- **Features**:
  - Tamper-proof logging
  - Regulatory compliance formatting
  - Query interface for auditors
  - Retention policies

### 4. Storage Layer

The Storage Layer provides persistent storage for events and state, with multiple implementation options.

#### Storage Interface

```java
public interface EventStore {
    // Append event to the store
    void append(LoanEvent event);
    
    // Get events for a specific loan
    List<LoanEvent> getEventsByLoan(String loanId);
    
    // Get events in a time range
    List<LoanEvent> getEventsByTimeRange(long from, long to);
    
    // Get current state snapshot
    Map<String, LoanApplication> getSnapshot();
    
    // Create state snapshot
    void createSnapshot();
}
```

#### Implementations

**In-Memory Store** (`storage/impl/InMemoryEventStore.java`)
- **Use Case**: Testing, development
- **Features**: Fast, simple, no persistence

**H2 Database Store** (`storage/impl/H2EventStore.java`)
- **Use Case**: Single node, moderate scale
- **Features**: SQL queries, ACID transactions, embedded database

**Blockchain Store** (`storage/impl/BlockchainEventStore.java`)
- **Use Case**: Immutable audit trail
- **Features**:
  - Events grouped into blocks
  - Cryptographic linking via hashes
  - Merkle tree for efficient verification
  - Block structure:
    ```java
    class Block {
        long blockNumber;
        List<LoanEvent> events;
        String previousBlockHash;
        String merkleRoot;
        long timestamp;
        String hash;
    }
    ```

### 5. Network Layer

The Network Layer handles all node-to-node communication for consensus and state synchronization.

#### Components

**P2P Network Manager** (`network/P2PNetwork.java`)
- **Purpose**: Manage peer connections
- **Features**:
  - Peer discovery
  - Connection pooling
  - Automatic reconnection
  - Load balancing

**Message Protocol** (`network/protocol/MessageProtocol.java`)
- **Purpose**: Define wire format for messages
- **Formats Supported**:
  - JSON (development, debugging)
  - Protocol Buffers (production)
  - Custom binary (performance)

**RPC Framework** (`network/rpc/`)
- **Purpose**: Remote procedure calls between nodes
- **Features**:
  - Async/sync calls
  - Timeout handling
  - Retry logic
  - Circuit breaker pattern

**Node Discovery** (`network/discovery/NodeDiscovery.java`)
- **Purpose**: Find and register peer nodes
- **Methods**:
  - Static configuration
  - Registry service
  - Multicast discovery
  - DNS-based discovery

### 6. Security Layer

The Security Layer provides cryptographic operations and security features.

#### Components

**Crypto Utils** (`security/CryptoUtils.java`)
- **Hash Functions**: SHA-256 for event hashing
- **Digital Signatures**: RSA/ECDSA for message signing
- **Key Derivation**: PBKDF2 for key generation

**Key Manager** (`security/KeyManager.java`)
- **Purpose**: Manage cryptographic keys
- **Features**:
  - Key generation
  - Key storage (file, HSM)
  - Key rotation
  - Certificate management

**Message Authentication** (`security/MessageAuthenticator.java`)
- **Purpose**: Sign and verify messages
- **Process**:
  ```java
  // Signing
  String signature = sign(message, privateKey);
  SignedMessage signed = new SignedMessage(message, signature, nodeId);
  
  // Verification
  boolean valid = verify(signed.message, signed.signature, publicKey);
  ```

## Data Models

### Core Models

**Loan Event**
```java
public class LoanEvent {
    private String eventId;          // Unique event identifier
    private String loanId;           // Associated loan
    private EventType type;          // Event type enum
    private long timestamp;          // Event timestamp
    private Map<String, Object> payload; // Event-specific data
    private String previousHash;     // Link to previous event
    private String hash;             // SHA-256 of event content
}
```

**Loan Application**
```java
public class LoanApplication {
    private String loanId;           // Unique loan identifier
    private String applicantId;      // Applicant identifier
    private BigDecimal amount;       // Loan amount
    private int termMonths;          // Loan term
    private LoanState state;         // Current state
    private List<LoanEvent> events;  // Event history
    private long lastUpdated;        // Last update timestamp
}
```

**Event Types**
```java
public enum EventType {
    // Application lifecycle
    APPLICATION_SUBMITTED,
    APPLICATION_WITHDRAWN,
    
    // Credit checks
    CREDIT_CHECK_INITIATED,
    CREDIT_CHECK_COMPLETE,
    
    // Fraud checks  
    FRAUD_CHECK_INITIATED,
    FRAUD_CHECK_COMPLETE,
    
    // Decision
    APPLICATION_APPROVED,
    APPLICATION_REJECTED,
    
    // Funding
    FUNDING_INITIATED,
    FUNDING_COMPLETE
}
```

## Message Flows

### Loan Application Flow (Raft)

```
Client          Leader         Follower1      Follower2
  │                │               │              │
  ├──Submit Loan──>│               │              │
  │                ├──AppendEntry─>│              │
  │                ├──AppendEntry──────────────>│
  │                │<─────ACK──────│              │
  │                │<─────ACK───────────────────│
  │                ├──Commit────>│              │
  │                ├──Commit─────────────────>│
  │<───Success─────│               │              │
```

### PBFT Consensus Flow

```
Client    Primary     Replica1    Replica2    Replica3
  │          │           │           │           │
  ├─Request─>│           │           │           │
  │          ├─PrePrepare>│           │           │
  │          ├─PrePrepare────────────>│           │
  │          ├─PrePrepare──────────────────────>│
  │          │<─Prepare──│           │           │
  │          │<─Prepare───────────────│           │
  │          │<─Prepare────────────────────────>│
  │          ├─Prepare──>│           │           │
  │          ├─Prepare───────────────>│           │
  │          ├─Prepare─────────────────────────>│
  │          │<─Commit───│           │           │
  │          │<─Commit────────────────│           │
  │          │<─Commit─────────────────────────>│
  │          ├─Execute   │           │           │
  │<─Reply───│           │           │           │
```

## Deployment Topologies

### Single Organization (Raft)
```
┌─────────────────────────────────────┐
│         Bank Internal Network         │
│  ┌──────┐  ┌──────┐  ┌──────┐      │
│  │Node 1│──│Node 2│──│Node 3│      │
│  │Leader│  │Follow│  │Follow│      │
│  └──────┘  └──────┘  └──────┘      │
└─────────────────────────────────────┘
```

### Multi-Organization Consortium (PBFT)
```
┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐
│ Bank A  │  │ Bank B  │  │ Bureau  │  │ Broker  │
│ Node 1  │──│ Node 2  │──│ Node 3  │──│ Node 4  │
│ Primary │  │ Replica │  │ Replica │  │ Replica │
└─────────┘  └─────────┘  └─────────┘  └─────────┘
     │            │            │            │
     └────────────┴────────────┴────────────┘
              Shared Consensus Network
```

## Performance Considerations

### Throughput Optimization
- Batch multiple events per consensus round
- Parallel validation of independent events
- Async I/O for network operations
- Connection pooling for peer communication

### Latency Optimization
- Minimize consensus rounds via batching
- Cache frequently accessed state
- Use efficient serialization (Protocol Buffers)
- Optimize cryptographic operations

### Scalability Patterns
- Horizontal scaling via sharding
- Read replicas for query load
- Event sourcing for state reconstruction
- Snapshot + incremental updates

## Security Considerations

### Threat Model
- **External Attackers**: Cannot forge signatures or break encryption
- **Malicious Nodes**: Up to f nodes in 3f+1 total (PBFT)
- **Network Attacks**: Handle partitions, delays, reordering
- **Data Tampering**: Detect via cryptographic hashes

### Security Measures
- TLS for all network communication
- Digital signatures for message authenticity
- Rate limiting to prevent DoS
- Access control for API endpoints
- Audit logging for compliance

## Monitoring and Observability

### Metrics
- **Consensus Metrics**: Leader changes, election duration, commit latency
- **Business Metrics**: Loans processed, approval rate, processing time
- **System Metrics**: CPU, memory, disk, network usage
- **Error Metrics**: Failed proposals, timeout, Byzantine faults detected

### Logging
- Structured logging (JSON format)
- Log levels: ERROR, WARN, INFO, DEBUG, TRACE
- Correlation IDs for request tracing
- Centralized log aggregation

### Health Checks
- `/health/live` - Is the process running?
- `/health/ready` - Is the node ready to accept requests?
- `/health/consensus` - Is consensus operational?

## Future Enhancements

1. **Proof of Stake Consensus**: More efficient than PBFT for larger networks
2. **Smart Contracts**: Programmable loan terms and conditions
3. **Cross-Chain Interoperability**: Connect with other blockchain networks
4. **Zero-Knowledge Proofs**: Privacy-preserving credit checks
5. **Machine Learning**: Fraud detection and risk assessment
6. **Multi-Region Deployment**: Geographic distribution for resilience