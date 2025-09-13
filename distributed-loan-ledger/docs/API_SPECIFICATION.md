# API Specification

This document provides detailed specifications for all APIs in the Distributed Loan Ledger system, including REST endpoints, WebSocket protocols, and internal RPC interfaces.

## Table of Contents

1. [REST API](#rest-api)
2. [WebSocket API](#websocket-api)
3. [Internal RPC APIs](#internal-rpc-apis)
4. [Error Handling](#error-handling)
5. [Authentication & Security](#authentication--security)

## REST API

### Base URL

```
http://{host}:{port}/api/v1
```

### Content Types

- Request: `application/json`
- Response: `application/json`

### Endpoints

#### 1. Submit Loan Application

Creates a new loan application and adds it to the distributed ledger.

**Endpoint:** `POST /api/v1/loans`

**Request Body:**

```json
{
  "applicantId": "string",
  "amount": "number",
  "termMonths": "integer",
  "purpose": "string",
  "applicantInfo": {
    "firstName": "string",
    "lastName": "string",
    "ssn": "string",
    "dateOfBirth": "string (ISO 8601)",
    "email": "string",
    "phone": "string",
    "address": {
      "street": "string",
      "city": "string",
      "state": "string",
      "zipCode": "string"
    }
  },
  "employmentInfo": {
    "employer": "string",
    "position": "string",
    "monthlyIncome": "number",
    "yearsEmployed": "number"
  }
}
```

**Response:** `201 Created`

```json
{
  "loanId": "LOAN-550e8400-e29b-41d4-a716-446655440000",
  "status": "SUBMITTED",
  "timestamp": "2025-01-15T10:30:00Z",
  "eventId": "EVT-123e4567-e89b-12d3-a456-426614174000"
}
```

**Error Responses:**

- `400 Bad Request` - Invalid input data
- `503 Service Unavailable` - Consensus not available

#### 2. Get Loan Application Status

Retrieves the current status and details of a loan application.

**Endpoint:** `GET /api/v1/loans/{loanId}`

**Path Parameters:**

- `loanId` (string, required) - The loan application ID

**Response:** `200 OK`

```json
{
  "loanId": "LOAN-550e8400-e29b-41d4-a716-446655440000",
  "applicantId": "user-123",
  "amount": 50000,
  "termMonths": 60,
  "purpose": "AUTO_LOAN",
  "state": "CREDIT_CHECK",
  "createdAt": "2025-01-15T10:30:00Z",
  "lastUpdated": "2025-01-15T10:35:00Z",
  "currentStep": {
    "name": "Credit Check",
    "status": "IN_PROGRESS",
    "startedAt": "2025-01-15T10:35:00Z"
  },
  "completedSteps": [
    {
      "name": "Application Submission",
      "status": "COMPLETED",
      "completedAt": "2025-01-15T10:30:00Z"
    }
  ]
}
```

**Error Responses:**

- `404 Not Found` - Loan application not found

#### 3. Get Loan Event History

Retrieves all events associated with a loan application.

**Endpoint:** `GET /api/v1/loans/{loanId}/events`

**Path Parameters:**

- `loanId` (string, required) - The loan application ID

**Query Parameters:**

- `from` (timestamp, optional) - Start time for event filtering
- `to` (timestamp, optional) - End time for event filtering
- `limit` (integer, optional, default=100) - Maximum number of events to return
- `offset` (integer, optional, default=0) - Pagination offset

**Response:** `200 OK`

```json
{
  "loanId": "LOAN-550e8400-e29b-41d4-a716-446655440000",
  "events": [
    {
      "eventId": "EVT-123e4567-e89b-12d3-a456-426614174000",
      "type": "APPLICATION_SUBMITTED",
      "timestamp": "2025-01-15T10:30:00Z",
      "payload": {
        "applicantId": "user-123",
        "amount": 50000,
        "termMonths": 60
      },
      "hash": "0x3f4e5c...",
      "previousHash": "0x2a3b4c..."
    },
    {
      "eventId": "EVT-223e4567-e89b-12d3-a456-426614174001",
      "type": "CREDIT_CHECK_INITIATED",
      "timestamp": "2025-01-15T10:35:00Z",
      "payload": {
        "bureau": "EXPERIAN",
        "requestId": "REQ-789"
      },
      "hash": "0x4f5e6c...",
      "previousHash": "0x3f4e5c..."
    }
  ],
  "totalEvents": 2,
  "hasMore": false
}
```

#### 4. Query Events

Search for events across all loan applications.

**Endpoint:** `GET /api/v1/events`

**Query Parameters:**

- `type` (string, optional) - Filter by event type
- `from` (timestamp, optional) - Start time
- `to` (timestamp, optional) - End time
- `applicantId` (string, optional) - Filter by applicant
- `state` (string, optional) - Filter by loan state
- `limit` (integer, optional, default=100) - Results per page
- `offset` (integer, optional, default=0) - Pagination offset

**Response:** `200 OK`

```json
{
  "events": [
    {
      "eventId": "EVT-123e4567-e89b-12d3-a456-426614174000",
      "loanId": "LOAN-550e8400-e29b-41d4-a716-446655440000",
      "type": "APPLICATION_APPROVED",
      "timestamp": "2025-01-15T11:00:00Z",
      "payload": {
        "approvedAmount": 45000,
        "interestRate": 5.5,
        "conditions": []
      }
    }
  ],
  "totalEvents": 150,
  "limit": 100,
  "offset": 0,
  "hasMore": true
}
```

#### 5. Trigger Event

Manually trigger an event for a loan application (for authorized systems).

**Endpoint:** `POST /api/v1/loans/{loanId}/events`

**Request Body:**

```json
{
  "type": "CREDIT_CHECK_COMPLETE",
  "payload": {
    "bureau": "EXPERIAN",
    "score": 720,
    "report": {
      "totalAccounts": 12,
      "openAccounts": 5,
      "totalDebt": 25000,
      "paymentHistory": "EXCELLENT"
    }
  }
}
```

**Response:** `201 Created`

```json
{
  "eventId": "EVT-323e4567-e89b-12d3-a456-426614174002",
  "loanId": "LOAN-550e8400-e29b-41d4-a716-446655440000",
  "type": "CREDIT_CHECK_COMPLETE",
  "timestamp": "2025-01-15T10:40:00Z",
  "status": "ACCEPTED"
}
```

### Admin Endpoints

#### 6. Get Node Status

Retrieves the status of the current node.

**Endpoint:** `GET /api/v1/admin/node/status`

**Response:** `200 OK`

```json
{
  "nodeId": "node-1",
  "state": "LEADER",
  "consensusType": "RAFT",
  "term": 5,
  "lastLogIndex": 1234,
  "commitIndex": 1230,
  "peers": [
    {
      "nodeId": "node-2",
      "state": "FOLLOWER",
      "lastHeartbeat": "2025-01-15T11:30:45Z",
      "isHealthy": true
    },
    {
      "nodeId": "node-3",
      "state": "FOLLOWER",
      "lastHeartbeat": "2025-01-15T11:30:44Z",
      "isHealthy": true
    }
  ],
  "uptime": 3600000,
  "eventsProcessed": 5678
}
```

#### 7. Get Cluster Status

Retrieves the status of the entire cluster.

**Endpoint:** `GET /api/v1/admin/cluster/status`

**Response:** `200 OK`

```json
{
  "clusterSize": 3,
  "consensusType": "RAFT",
  "leader": "node-1",
  "nodes": [
    {
      "nodeId": "node-1",
      "host": "192.168.1.10",
      "port": 8081,
      "state": "LEADER",
      "isHealthy": true
    },
    {
      "nodeId": "node-2",
      "host": "192.168.1.11",
      "port": 8082,
      "state": "FOLLOWER",
      "isHealthy": true
    },
    {
      "nodeId": "node-3",
      "host": "192.168.1.12",
      "port": 8083,
      "state": "FOLLOWER",
      "isHealthy": true
    }
  ],
  "totalEvents": 15678,
  "consensusRounds": 1234,
  "lastConsensusTime": "2025-01-15T11:30:50Z"
}
```

#### 8. Get Metrics

Retrieves performance metrics.

**Endpoint:** `GET /api/v1/admin/metrics`

**Response:** `200 OK`

```json
{
  "throughput": {
    "eventsPerSecond": 125.5,
    "requestsPerSecond": 200.3
  },
  "latency": {
    "p50": 12.5,
    "p95": 45.2,
    "p99": 120.8,
    "mean": 18.7
  },
  "consensus": {
    "roundsCompleted": 5678,
    "failedRounds": 12,
    "averageRoundTime": 15.3,
    "leaderChanges": 2
  },
  "storage": {
    "totalEvents": 45678,
    "diskUsage": 1234567890,
    "compressionRatio": 0.65
  },
  "network": {
    "messagesReceived": 123456,
    "messagesSent": 123789,
    "bytesReceived": 98765432,
    "bytesSent": 98769876
  }
}
```

## WebSocket API

### Connection

```
ws://{host}:{port}/events
```

### Message Format

All WebSocket messages use JSON format.

### Client to Server Messages

#### Subscribe to Events

```json
{
  "action": "SUBSCRIBE",
  "requestId": "req-123",
  "filters": {
    "loanId": "LOAN-550e8400-e29b-41d4-a716-446655440000",
    "eventTypes": ["APPLICATION_APPROVED", "APPLICATION_REJECTED"],
    "applicantId": "user-123"
  }
}
```

#### Unsubscribe from Events

```json
{
  "action": "UNSUBSCRIBE",
  "requestId": "req-124",
  "subscriptionId": "sub-456"
}
```

#### Ping

```json
{
  "action": "PING",
  "timestamp": "2025-01-15T11:30:00Z"
}
```

### Server to Client Messages

#### Connection Established

```json
{
  "type": "CONNECTED",
  "sessionId": "sess-789",
  "timestamp": "2025-01-15T11:30:00Z"
}
```

#### Subscription Confirmed

```json
{
  "type": "SUBSCRIBED",
  "requestId": "req-123",
  "subscriptionId": "sub-456",
  "filters": {
    "loanId": "LOAN-550e8400-e29b-41d4-a716-446655440000"
  }
}
```

#### Event Notification

```json
{
  "type": "EVENT",
  "subscriptionId": "sub-456",
  "event": {
    "eventId": "EVT-523e4567-e89b-12d3-a456-426614174003",
    "loanId": "LOAN-550e8400-e29b-41d4-a716-446655440000",
    "type": "APPLICATION_APPROVED",
    "timestamp": "2025-01-15T11:35:00Z",
    "payload": {
      "approvedAmount": 45000,
      "interestRate": 5.5
    }
  }
}
```

#### Error Message

```json
{
  "type": "ERROR",
  "requestId": "req-123",
  "error": {
    "code": "INVALID_FILTER",
    "message": "Invalid event type specified",
    "details": {
      "invalidType": "UNKNOWN_EVENT"
    }
  }
}
```

#### Pong

```json
{
  "type": "PONG",
  "timestamp": "2025-01-15T11:30:01Z"
}
```

## Internal RPC APIs

### Raft Consensus RPCs

#### AppendEntries RPC

Used by the leader to replicate log entries and send heartbeats.

**Request:**

```json
{
  "type": "APPEND_ENTRIES",
  "term": 5,
  "leaderId": "node-1",
  "prevLogIndex": 1229,
  "prevLogTerm": 4,
  "entries": [
    {
      "term": 5,
      "index": 1230,
      "event": {
        "eventId": "EVT-623e4567-e89b-12d3-a456-426614174004",
        "loanId": "LOAN-650e8400-e29b-41d4-a716-446655440001",
        "type": "APPLICATION_SUBMITTED",
        "timestamp": "2025-01-15T11:40:00Z",
        "payload": {}
      }
    }
  ],
  "leaderCommit": 1228
}
```

**Response:**

```json
{
  "type": "APPEND_ENTRIES_RESPONSE",
  "term": 5,
  "success": true,
  "matchIndex": 1230
}
```

#### RequestVote RPC

Used by candidates to request votes during leader election.

**Request:**

```json
{
  "type": "REQUEST_VOTE",
  "term": 6,
  "candidateId": "node-2",
  "lastLogIndex": 1230,
  "lastLogTerm": 5
}
```

**Response:**

```json
{
  "type": "REQUEST_VOTE_RESPONSE",
  "term": 6,
  "voteGranted": true
}
```

### PBFT Consensus Messages

#### Pre-Prepare Message

Sent by the primary to propose an ordering for a request.

```json
{
  "type": "PRE_PREPARE",
  "viewNumber": 2,
  "sequenceNumber": 456,
  "digest": "0xa1b2c3d4e5f6...",
  "request": {
    "clientId": "client-123",
    "requestId": "req-789",
    "operation": {
      "type": "LOAN_EVENT",
      "event": {
        "loanId": "LOAN-750e8400-e29b-41d4-a716-446655440002",
        "type": "CREDIT_CHECK_INITIATED",
        "payload": {}
      }
    },
    "timestamp": "2025-01-15T11:45:00Z"
  },
  "signature": "0x9f8e7d6c5b4a..."
}
```

#### Prepare Message

Broadcast by all replicas after receiving a valid pre-prepare.

```json
{
  "type": "PREPARE",
  "viewNumber": 2,
  "sequenceNumber": 456,
  "digest": "0xa1b2c3d4e5f6...",
  "nodeId": 2,
  "signature": "0x1a2b3c4d5e6f..."
}
```

#### Commit Message

Broadcast after receiving 2f prepare messages.

```json
{
  "type": "COMMIT",
  "viewNumber": 2,
  "sequenceNumber": 456,
  "digest": "0xa1b2c3d4e5f6...",
  "nodeId": 2,
  "signature": "0x2b3c4d5e6f7a..."
}
```

#### View Change Message

Sent when detecting primary failure.

```json
{
  "type": "VIEW_CHANGE",
  "newViewNumber": 3,
  "lastStableCheckpoint": 400,
  "checkpointProof": [
    {
      "nodeId": 1,
      "checkpoint": 400,
      "signature": "0x3c4d5e6f7a8b..."
    }
  ],
  "preparedMessages": [
    {
      "sequenceNumber": 401,
      "digest": "0xb2c3d4e5f6a7...",
      "viewNumber": 2
    }
  ],
  "nodeId": 2,
  "signature": "0x4d5e6f7a8b9c..."
}
```

## Error Handling

### Error Response Format

All error responses follow this format:

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable error message",
    "details": {
      "field": "additional context"
    },
    "timestamp": "2025-01-15T11:50:00Z",
    "traceId": "trace-123456"
  }
}
```

### Common Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `INVALID_REQUEST` | 400 | Request validation failed |
| `UNAUTHORIZED` | 401 | Authentication required |
| `FORBIDDEN` | 403 | Insufficient permissions |
| `NOT_FOUND` | 404 | Resource not found |
| `DUPLICATE_REQUEST` | 409 | Duplicate request detected |
| `CONSENSUS_FAILURE` | 503 | Consensus unavailable |
| `INTERNAL_ERROR` | 500 | Internal server error |
| `RATE_LIMITED` | 429 | Too many requests |

### Validation Errors

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": {
      "violations": [
        {
          "field": "amount",
          "message": "Must be greater than 0"
        },
        {
          "field": "termMonths",
          "message": "Must be between 12 and 360"
        }
      ]
    }
  }
}
```

## Authentication & Security

### API Key Authentication

Include API key in request header:

```
X-API-Key: your-api-key-here
```

### JWT Authentication

Include JWT token in Authorization header:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Rate Limiting

Default rate limits:

- Standard endpoints: 100 requests per minute
- Admin endpoints: 10 requests per minute
- WebSocket connections: 10 per IP address

Rate limit headers in response:

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1642255200
```

### CORS Configuration

CORS headers for browser-based clients:

```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type, X-API-Key, Authorization
Access-Control-Max-Age: 86400
```

## Versioning

The API uses URL versioning. Current version: `v1`

Future versions will be available at:
- `/api/v2`
- `/api/v3`

Deprecated versions will be supported for 6 months after new version release.

## Pagination

Standard pagination parameters:

- `limit` - Number of results per page (max: 1000, default: 100)
- `offset` - Starting position (default: 0)
- `cursor` - Cursor-based pagination token (alternative to offset)

Pagination response format:

```json
{
  "data": [...],
  "pagination": {
    "limit": 100,
    "offset": 200,
    "total": 5678,
    "hasMore": true,
    "nextCursor": "eyJpZCI6MTIzNH0="
  }
}
```

## Filtering and Sorting

### Filtering

Use query parameters for filtering:

```
GET /api/v1/events?type=APPLICATION_APPROVED&from=2025-01-01&to=2025-01-31
```

### Sorting

Use `sort` parameter with field name and direction:

```
GET /api/v1/events?sort=timestamp:desc
GET /api/v1/loans?sort=amount:asc,createdAt:desc
```

## Webhooks

### Webhook Registration

Register a webhook endpoint to receive event notifications:

**Endpoint:** `POST /api/v1/webhooks`

**Request:**

```json
{
  "url": "https://your-service.com/webhook",
  "events": ["APPLICATION_APPROVED", "APPLICATION_REJECTED"],
  "secret": "webhook-secret-key"
}
```

**Response:**

```json
{
  "webhookId": "webhook-123",
  "url": "https://your-service.com/webhook",
  "events": ["APPLICATION_APPROVED", "APPLICATION_REJECTED"],
  "status": "ACTIVE",
  "createdAt": "2025-01-15T12:00:00Z"
}
```

### Webhook Payload

```json
{
  "webhookId": "webhook-123",
  "event": {
    "eventId": "EVT-723e4567-e89b-12d3-a456-426614174005",
    "loanId": "LOAN-850e8400-e29b-41d4-a716-446655440003",
    "type": "APPLICATION_APPROVED",
    "timestamp": "2025-01-15T12:05:00Z",
    "payload": {...}
  },
  "signature": "sha256=a1b2c3d4e5f6..."
}
```

### Webhook Signature Verification

Verify webhook authenticity using HMAC-SHA256:

```java
String payload = request.getBody();
String signature = request.getHeader("X-Webhook-Signature");
String expected = "sha256=" + hmacSha256(payload, webhookSecret);
boolean isValid = signature.equals(expected);
```

## Health Checks

### Liveness Probe

**Endpoint:** `GET /health/live`

**Response:** `200 OK`

```json
{
  "status": "UP",
  "timestamp": "2025-01-15T12:10:00Z"
}
```

### Readiness Probe

**Endpoint:** `GET /health/ready`

**Response:** `200 OK` if ready, `503 Service Unavailable` if not

```json
{
  "status": "UP",
  "checks": {
    "database": "UP",
    "consensus": "UP",
    "storage": "UP"
  },
  "timestamp": "2025-01-15T12:10:00Z"
}
```

## SDK Examples

### Java Client

```java
// Initialize client
LedgerClient client = new LedgerClient("http://localhost:8081", "api-key");

// Submit loan application
LoanApplication loan = new LoanApplication()
    .applicantId("user-123")
    .amount(BigDecimal.valueOf(50000))
    .termMonths(60)
    .purpose("AUTO_LOAN");

LoanResponse response = client.submitLoan(loan);
System.out.println("Loan ID: " + response.getLoanId());

// Subscribe to events
client.subscribeToEvents(response.getLoanId(), event -> {
    System.out.println("Event received: " + event.getType());
});
```

### Python Client

```python
# Initialize client
client = LedgerClient(base_url="http://localhost:8081", api_key="api-key")

# Submit loan application
loan = {
    "applicantId": "user-123",
    "amount": 50000,
    "termMonths": 60,
    "purpose": "AUTO_LOAN"
}

response = client.submit_loan(loan)
print(f"Loan ID: {response['loanId']}")

# Subscribe to events
def on_event(event):
    print(f"Event received: {event['type']}")

client.subscribe_to_events(response['loanId'], on_event)
```

### JavaScript/TypeScript Client

```typescript
// Initialize client
const client = new LedgerClient({
  baseUrl: 'http://localhost:8081',
  apiKey: 'api-key'
});

// Submit loan application
const loan = {
  applicantId: 'user-123',
  amount: 50000,
  termMonths: 60,
  purpose: 'AUTO_LOAN'
};

const response = await client.submitLoan(loan);
console.log(`Loan ID: ${response.loanId}`);

// Subscribe to events
client.subscribeToEvents(response.loanId, (event) => {
  console.log(`Event received: ${event.type}`);
});
```