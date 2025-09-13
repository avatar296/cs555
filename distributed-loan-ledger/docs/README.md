# Distributed Loan Ledger

A proof-of-concept distributed ledger system for loan applications that demonstrates consensus algorithms in both trusted and untrusted multi-party environments.

## Overview

This project implements a distributed event-driven system for managing loan applications across multiple financial institutions (banks, brokers, credit bureaus). It demonstrates the progression from a simple centralized system to a Byzantine fault-tolerant distributed ledger, showing how different consensus mechanisms address various trust models.

## Key Features

- **Event-driven architecture** for loan application lifecycle management
- **Pluggable consensus mechanisms**:
  - Centralized (single node, development/testing)
  - Raft (crash fault tolerant, trusted environments)  
  - PBFT (Byzantine fault tolerant, untrusted environments)
- **Multi-party coordination** between banks, brokers, and credit bureaus
- **Tamper-proof audit trail** using cryptographic hashing and event chaining
- **Real-time event streaming** via WebSocket connections
- **Comprehensive simulation tools** for testing failure scenarios

## Problem Statement

Current loan origination systems face several challenges when multiple organizations need to coordinate:

1. **Trust Dependencies**: Systems rely on a single vendor or institution to maintain the source of truth
2. **Data Inconsistency**: Each party maintains separate records, leading to reconciliation overhead
3. **Fraud Vulnerability**: Duplicate applications and data tampering are difficult to detect
4. **Audit Complexity**: No unified, verifiable history across all participants
5. **Vendor Lock-in**: Centralized platforms control access and charge fees

## Solution

This distributed ledger provides:

- **Shared Truth**: All participants maintain synchronized copies of the event log
- **No Single Point of Control**: Consensus ensures no single party can manipulate records
- **Immutable History**: Events are cryptographically linked and cannot be altered
- **Byzantine Fault Tolerance**: System operates correctly even with malicious participants
- **Transparent Audit Trail**: Regulators can verify the complete history independently

## Architecture Overview

The system is organized into distinct layers:

```
┌─────────────────────────────┐
│      External Systems       │  (Credit Bureaus, Fraud Detection)
└──────────────┬──────────────┘
               │
┌──────────────▼──────────────┐
│       API Gateway           │  (REST, WebSocket interfaces)
└──────────────┬──────────────┘
               │
┌──────────────▼──────────────┐
│     Consensus Engine        │  (Pluggable: None/Raft/PBFT)
└──────────────┬──────────────┘
               │
┌──────────────▼──────────────┐
│      Core Business          │  (Loan state machine, validation)
└──────────────┬──────────────┘
               │
┌──────────────▼──────────────┐
│        Storage              │  (Event store, blockchain)
└─────────────────────────────┘
```

## Quick Start

### Prerequisites

- Java 11 or higher
- Gradle 7.0 or higher
- 4GB RAM minimum (for running multiple nodes)

### Building the Project

```bash
# Clone the repository
git clone <repository-url>
cd distributed-loan-ledger

# Build the project
./gradlew build

# Run tests
./gradlew test
```

### Running a Single Node (Centralized Mode)

```bash
# Start the node
./gradlew run --args="--mode=CENTRALIZED --port=8080"

# The API will be available at http://localhost:8080
```

### Running a Raft Cluster (Trusted Environment)

```bash
# Start the registry (service discovery)
./gradlew runRegistry --args="--port=9000"

# Start 3 Raft nodes (in separate terminals)
./gradlew run --args="--mode=RAFT --node-id=node1 --port=8081 --registry=localhost:9000"
./gradlew run --args="--mode=RAFT --node-id=node2 --port=8082 --registry=localhost:9000"
./gradlew run --args="--mode=RAFT --node-id=node3 --port=8083 --registry=localhost:9000"
```

### Running a PBFT Cluster (Untrusted Environment)

```bash
# Start the registry
./gradlew runRegistry --args="--port=9000"

# Start 4 PBFT nodes (tolerates 1 Byzantine fault)
./gradlew run --args="--mode=PBFT --node-id=node1 --port=8081 --registry=localhost:9000"
./gradlew run --args="--mode=PBFT --node-id=node2 --port=8082 --registry=localhost:9000"
./gradlew run --args="--mode=PBFT --node-id=node3 --port=8083 --registry=localhost:9000"
./gradlew run --args="--mode=PBFT --node-id=node4 --port=8084 --registry=localhost:9000"
```

## API Examples

### Submit a Loan Application

```bash
curl -X POST http://localhost:8081/api/v1/loans \
  -H "Content-Type: application/json" \
  -d '{
    "applicantId": "user-123",
    "amount": 50000,
    "purpose": "AUTO_LOAN",
    "term": 60
  }'
```

### Query Loan Status

```bash
curl http://localhost:8081/api/v1/loans/loan-abc-123
```

### Stream Events via WebSocket

```javascript
const ws = new WebSocket('ws://localhost:8081/events');

ws.send(JSON.stringify({
  action: 'SUBSCRIBE',
  filters: {
    eventTypes: ['APPLICATION_APPROVED', 'APPLICATION_REJECTED']
  }
}));

ws.onmessage = (event) => {
  console.log('Event received:', JSON.parse(event.data));
};
```

## Running Demonstrations

### Basic Demo

```bash
# Run the automated demo scenario
./gradlew runDemo

# This will:
# 1. Start a 3-node cluster
# 2. Submit sample loan applications
# 3. Simulate credit checks and approvals
# 4. Display metrics and event flow
```

### Failure Simulation

```bash
# Run failure scenarios
./gradlew runSimulation --args="--scenario=LEADER_FAILURE"
./gradlew runSimulation --args="--scenario=NETWORK_PARTITION"
./gradlew runSimulation --args="--scenario=BYZANTINE_NODE"
```

## Project Structure

```
distributed-loan-ledger/
├── docs/                       # Documentation
├── src/main/java/             # Source code
│   └── edu/csu/cs555/ledger/
│       ├── api/               # REST and WebSocket APIs
│       ├── consensus/         # Consensus implementations
│       ├── core/              # Business logic
│       ├── models/            # Data models
│       ├── network/           # P2P networking
│       ├── storage/           # Persistence layer
│       └── main/              # Entry points
├── src/test/                  # Test suites
├── scripts/                   # Utility scripts
└── config/                    # Configuration files
```

## Documentation

- [Architecture Guide](ARCHITECTURE.md) - Detailed system design and component descriptions
- [Implementation Guide](IMPLEMENTATION_GUIDE.md) - Step-by-step implementation instructions
- [API Specification](API_SPECIFICATION.md) - Complete API documentation
- [Consensus Algorithms](CONSENSUS_ALGORITHMS.md) - Deep dive into Raft and PBFT
- [Deployment Guide](DEPLOYMENT_GUIDE.md) - Production deployment instructions

## Use Cases

### Internal Bank System (Raft)
- Multiple servers within one bank
- Handles server crashes gracefully
- High performance, low latency
- Trust between all nodes

### Multi-Bank Consortium (PBFT)
- Multiple banks share loan data
- No single bank controls the system
- Handles malicious behavior
- Regulatory compliance built-in

### Development/Testing (Centralized)
- Single node operation
- Easy debugging
- Quick iteration
- No consensus overhead

## Performance Characteristics

| Mode | Throughput | Latency | Fault Tolerance |
|------|------------|---------|-----------------|
| Centralized | 10,000 tx/s | ~1ms | None |
| Raft | 1,000 tx/s | ~10ms | f crashes (2f+1 nodes) |
| PBFT | 100 tx/s | ~100ms | f Byzantine (3f+1 nodes) |

## Contributing

This is an academic project for CS555 Distributed Systems. Contributions and feedback are welcome.

## License

This project is for educational purposes as part of CSU CS555 coursework.

## Contact

Christopher Cowart - Colorado State University

## Acknowledgments

- Based on the Raft paper by Ongaro and Ousterhout
- PBFT implementation inspired by Castro and Liskov's work
- Built for CS555 Distributed Systems class project