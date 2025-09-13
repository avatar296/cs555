# Implementation Guide

This guide provides step-by-step instructions for implementing the Distributed Loan Ledger system. It's designed to be followed sequentially, with each phase building on the previous one.

## Prerequisites

Before starting implementation, ensure you have:

- Java 11+ installed
- Gradle 7.0+ installed
- IDE with Java support (IntelliJ IDEA, Eclipse, VS Code)
- Basic understanding of:
  - Event-driven architecture
  - Distributed systems concepts
  - REST APIs and WebSockets
  - Consensus algorithms (Raft, PBFT)

## Project Setup

### 1. Initialize Project Structure

```bash
# Create project directory
mkdir -p distributed-loan-ledger
cd distributed-loan-ledger

# Create Gradle project
gradle init --type java-application --dsl groovy --test-framework junit

# Create package structure
mkdir -p src/main/java/edu/csu/cs555/ledger/{api,consensus,core,models,network,storage,security,main}
mkdir -p src/test/java/edu/csu/cs555/ledger/{unit,integration,performance}
mkdir -p docs scripts config src/main/resources/web
```

### 2. Configure Build File

Create `build.gradle`:

```gradle
plugins {
    id 'java'
    id 'application'
    id 'com.github.johnrengelman.shadow' version '8.1.1'
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    mavenCentral()
}

dependencies {
    // Web Framework
    implementation 'io.javalin:javalin:5.6.3'
    implementation 'org.eclipse.jetty.websocket:websocket-server:11.0.20'
    
    // JSON Processing
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.16.1'
    implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1'
    
    // Database
    implementation 'com.h2database:h2:2.2.224'
    
    // Networking
    implementation 'io.netty:netty-all:4.1.107.Final'
    
    // Security
    implementation 'org.bouncycastle:bcprov-jdk15on:1.70'
    
    // Utilities
    implementation 'com.google.guava:guava:33.0.0-jre'
    implementation 'org.apache.commons:commons-lang3:3.14.0'
    
    // Logging
    implementation 'org.slf4j:slf4j-api:2.0.11'
    implementation 'ch.qos.logback:logback-classic:1.4.14'
    
    // Testing
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.mockito:mockito-core:5.10.0'
    testImplementation 'org.assertj:assertj-core:3.25.3'
}

application {
    mainClass = 'edu.csu.cs555.ledger.main.LedgerNode'
}

// Custom tasks for running different components
task runRegistry(type: JavaExec) {
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'edu.csu.cs555.ledger.main.Registry'
}

task runDemo(type: JavaExec) {
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'edu.csu.cs555.ledger.main.DemoRunner'
}
```

## Phase 1: Core Models and Event System

### Step 1: Define Data Models

#### Create Event Model (`models/LoanEvent.java`)

```java
package edu.csu.cs555.ledger.models;

import java.util.Map;
import java.util.UUID;

public class LoanEvent {
    private String eventId;
    private String loanId;
    private EventType type;
    private long timestamp;
    private Map<String, Object> payload;
    private String previousHash;
    private String hash;
    
    public LoanEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
    }
    
    // Constructor with parameters
    public LoanEvent(String loanId, EventType type, Map<String, Object> payload) {
        this();
        this.loanId = loanId;
        this.type = type;
        this.payload = payload;
    }
    
    // Getters and setters...
    
    // Compute hash of event content
    public void computeHash(String previousHash) {
        this.previousHash = previousHash;
        String content = eventId + loanId + type + timestamp + payload + previousHash;
        this.hash = HashUtil.sha256(content);
    }
}
```

#### Create Event Types (`models/EventType.java`)

```java
package edu.csu.cs555.ledger.models;

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
    
    // Underwriting
    UNDERWRITING_STARTED,
    UNDERWRITING_COMPLETE,
    
    // Decision
    APPLICATION_APPROVED,
    APPLICATION_REJECTED,
    
    // Funding
    FUNDING_INITIATED,
    FUNDING_COMPLETE,
    
    // System events
    NODE_JOINED,
    NODE_LEFT,
    CONSENSUS_CHANGED
}
```

#### Create Loan Application Model (`models/LoanApplication.java`)

```java
package edu.csu.cs555.ledger.models;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class LoanApplication {
    private String loanId;
    private String applicantId;
    private BigDecimal amount;
    private int termMonths;
    private String purpose;
    private LoanState state;
    private List<LoanEvent> events;
    private long createdAt;
    private long lastUpdated;
    
    public LoanApplication() {
        this.events = new ArrayList<>();
        this.state = LoanState.SUBMITTED;
        this.createdAt = System.currentTimeMillis();
        this.lastUpdated = this.createdAt;
    }
    
    // Add event to history
    public void addEvent(LoanEvent event) {
        this.events.add(event);
        this.lastUpdated = event.getTimestamp();
        updateState(event);
    }
    
    // Update state based on event
    private void updateState(LoanEvent event) {
        // State transition logic
        switch (event.getType()) {
            case APPLICATION_SUBMITTED:
                this.state = LoanState.SUBMITTED;
                break;
            case CREDIT_CHECK_INITIATED:
                this.state = LoanState.CREDIT_CHECK;
                break;
            case APPLICATION_APPROVED:
                this.state = LoanState.APPROVED;
                break;
            case APPLICATION_REJECTED:
                this.state = LoanState.REJECTED;
                break;
            // ... other cases
        }
    }
    
    // Getters and setters...
}
```

#### Create Loan States (`models/LoanState.java`)

```java
package edu.csu.cs555.ledger.models;

public enum LoanState {
    SUBMITTED,
    CREDIT_CHECK,
    FRAUD_CHECK,
    UNDERWRITING,
    APPROVED,
    REJECTED,
    FUNDED,
    WITHDRAWN,
    CLOSED
}
```

### Step 2: Implement State Machine

#### Create State Machine (`core/LoanStateMachine.java`)

```java
package edu.csu.cs555.ledger.core;

import edu.csu.cs555.ledger.models.*;
import java.util.*;

public class LoanStateMachine {
    private static final Map<LoanState, Set<EventType>> VALID_TRANSITIONS;
    
    static {
        VALID_TRANSITIONS = new HashMap<>();
        
        // Define valid transitions from each state
        VALID_TRANSITIONS.put(LoanState.SUBMITTED, 
            Set.of(EventType.CREDIT_CHECK_INITIATED, 
                   EventType.APPLICATION_WITHDRAWN));
        
        VALID_TRANSITIONS.put(LoanState.CREDIT_CHECK,
            Set.of(EventType.CREDIT_CHECK_COMPLETE,
                   EventType.APPLICATION_REJECTED));
        
        VALID_TRANSITIONS.put(LoanState.FRAUD_CHECK,
            Set.of(EventType.FRAUD_CHECK_COMPLETE,
                   EventType.APPLICATION_REJECTED));
        
        // ... define other transitions
    }
    
    public boolean isValidTransition(LoanState currentState, EventType eventType) {
        Set<EventType> validEvents = VALID_TRANSITIONS.get(currentState);
        return validEvents != null && validEvents.contains(eventType);
    }
    
    public LoanState processEvent(LoanState currentState, LoanEvent event) {
        if (!isValidTransition(currentState, event.getType())) {
            throw new IllegalStateException(
                String.format("Invalid transition from %s with event %s", 
                    currentState, event.getType()));
        }
        
        // Return new state based on event
        switch (event.getType()) {
            case CREDIT_CHECK_INITIATED:
                return LoanState.CREDIT_CHECK;
            case FRAUD_CHECK_INITIATED:
                return LoanState.FRAUD_CHECK;
            case APPLICATION_APPROVED:
                return LoanState.APPROVED;
            case APPLICATION_REJECTED:
                return LoanState.REJECTED;
            // ... other cases
            default:
                return currentState;
        }
    }
}
```

### Step 3: Implement Event Processor

#### Create Event Processor (`core/LoanEventProcessor.java`)

```java
package edu.csu.cs555.ledger.core;

import edu.csu.cs555.ledger.models.*;
import edu.csu.cs555.ledger.storage.EventStore;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class LoanEventProcessor {
    private final Map<String, LoanApplication> loans;
    private final EventStore eventStore;
    private final LoanStateMachine stateMachine;
    private final EventValidator validator;
    
    public LoanEventProcessor(EventStore eventStore) {
        this.loans = new ConcurrentHashMap<>();
        this.eventStore = eventStore;
        this.stateMachine = new LoanStateMachine();
        this.validator = new EventValidator();
    }
    
    public void processEvent(LoanEvent event) {
        // Validate event
        if (!validator.validate(event)) {
            throw new IllegalArgumentException("Invalid event: " + event);
        }
        
        // Get or create loan application
        LoanApplication loan = loans.computeIfAbsent(
            event.getLoanId(), 
            k -> createNewLoan(event)
        );
        
        // Validate state transition
        if (!stateMachine.isValidTransition(loan.getState(), event.getType())) {
            throw new IllegalStateException(
                "Invalid state transition for loan " + event.getLoanId());
        }
        
        // Update state
        LoanState newState = stateMachine.processEvent(loan.getState(), event);
        loan.setState(newState);
        loan.addEvent(event);
        
        // Persist event
        eventStore.append(event);
        
        // Trigger side effects
        triggerSideEffects(event, loan);
    }
    
    private LoanApplication createNewLoan(LoanEvent event) {
        if (event.getType() != EventType.APPLICATION_SUBMITTED) {
            throw new IllegalArgumentException(
                "First event must be APPLICATION_SUBMITTED");
        }
        
        LoanApplication loan = new LoanApplication();
        loan.setLoanId(event.getLoanId());
        // Set other fields from event payload
        Map<String, Object> payload = event.getPayload();
        loan.setApplicantId((String) payload.get("applicantId"));
        loan.setAmount((BigDecimal) payload.get("amount"));
        loan.setTermMonths((Integer) payload.get("termMonths"));
        
        return loan;
    }
    
    private void triggerSideEffects(LoanEvent event, LoanApplication loan) {
        // Notify listeners, trigger workflows, etc.
        switch (event.getType()) {
            case APPLICATION_SUBMITTED:
                // Trigger credit check
                break;
            case CREDIT_CHECK_COMPLETE:
                // Trigger fraud check
                break;
            case APPLICATION_APPROVED:
                // Notify applicant
                break;
            // ... other cases
        }
    }
    
    public LoanApplication getLoan(String loanId) {
        return loans.get(loanId);
    }
}
```

### Step 4: Implement Storage Layer

#### Create Event Store Interface (`storage/EventStore.java`)

```java
package edu.csu.cs555.ledger.storage;

import edu.csu.cs555.ledger.models.LoanEvent;
import java.util.List;

public interface EventStore {
    void append(LoanEvent event);
    List<LoanEvent> getEventsByLoan(String loanId);
    List<LoanEvent> getEventsByTimeRange(long from, long to);
    List<LoanEvent> getAllEvents();
    LoanEvent getLastEvent();
    void clear();
}
```

#### Create In-Memory Implementation (`storage/impl/InMemoryEventStore.java`)

```java
package edu.csu.cs555.ledger.storage.impl;

import edu.csu.cs555.ledger.storage.EventStore;
import edu.csu.cs555.ledger.models.LoanEvent;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class InMemoryEventStore implements EventStore {
    private final List<LoanEvent> events;
    private String lastHash = "0";
    
    public InMemoryEventStore() {
        this.events = new CopyOnWriteArrayList<>();
    }
    
    @Override
    public synchronized void append(LoanEvent event) {
        event.computeHash(lastHash);
        events.add(event);
        lastHash = event.getHash();
    }
    
    @Override
    public List<LoanEvent> getEventsByLoan(String loanId) {
        return events.stream()
            .filter(e -> e.getLoanId().equals(loanId))
            .collect(Collectors.toList());
    }
    
    @Override
    public List<LoanEvent> getEventsByTimeRange(long from, long to) {
        return events.stream()
            .filter(e -> e.getTimestamp() >= from && e.getTimestamp() <= to)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<LoanEvent> getAllEvents() {
        return new ArrayList<>(events);
    }
    
    @Override
    public LoanEvent getLastEvent() {
        return events.isEmpty() ? null : events.get(events.size() - 1);
    }
    
    @Override
    public void clear() {
        events.clear();
        lastHash = "0";
    }
}
```

## Phase 2: REST API and WebSocket

### Step 1: Create REST API

#### Create Loan Controller (`api/rest/LoanApplicationController.java`)

```java
package edu.csu.cs555.ledger.api.rest;

import io.javalin.Javalin;
import io.javalin.http.Context;
import edu.csu.cs555.ledger.core.LoanEventProcessor;
import edu.csu.cs555.ledger.models.*;
import java.util.Map;
import java.util.UUID;

public class LoanApplicationController {
    private final LoanEventProcessor processor;
    
    public LoanApplicationController(LoanEventProcessor processor) {
        this.processor = processor;
    }
    
    public void registerRoutes(Javalin app) {
        app.post("/api/v1/loans", this::submitLoan);
        app.get("/api/v1/loans/:id", this::getLoan);
        app.get("/api/v1/loans/:id/events", this::getLoanEvents);
    }
    
    private void submitLoan(Context ctx) {
        Map<String, Object> request = ctx.bodyAsClass(Map.class);
        
        // Create loan ID
        String loanId = "LOAN-" + UUID.randomUUID().toString();
        
        // Create submission event
        LoanEvent event = new LoanEvent(
            loanId,
            EventType.APPLICATION_SUBMITTED,
            request
        );
        
        // Process event
        processor.processEvent(event);
        
        // Return response
        ctx.status(201);
        ctx.json(Map.of(
            "loanId", loanId,
            "status", "SUBMITTED",
            "timestamp", event.getTimestamp()
        ));
    }
    
    private void getLoan(Context ctx) {
        String loanId = ctx.pathParam("id");
        LoanApplication loan = processor.getLoan(loanId);
        
        if (loan == null) {
            ctx.status(404);
            ctx.json(Map.of("error", "Loan not found"));
            return;
        }
        
        ctx.json(loan);
    }
    
    private void getLoanEvents(Context ctx) {
        String loanId = ctx.pathParam("id");
        LoanApplication loan = processor.getLoan(loanId);
        
        if (loan == null) {
            ctx.status(404);
            ctx.json(Map.of("error", "Loan not found"));
            return;
        }
        
        ctx.json(loan.getEvents());
    }
}
```

### Step 2: Create WebSocket Handler

#### Create Event Broadcaster (`api/websocket/EventBroadcaster.java`)

```java
package edu.csu.cs555.ledger.api.websocket;

import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;
import edu.csu.cs555.ledger.models.LoanEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EventBroadcaster {
    private final Map<String, WsContext> sessions;
    private final Map<String, Set<String>> subscriptions;
    private final ObjectMapper mapper;
    
    public EventBroadcaster() {
        this.sessions = new ConcurrentHashMap<>();
        this.subscriptions = new ConcurrentHashMap<>();
        this.mapper = new ObjectMapper();
    }
    
    public void configure(WsConfig ws) {
        ws.onConnect(ctx -> {
            String sessionId = ctx.getSessionId();
            sessions.put(sessionId, ctx);
            subscriptions.put(sessionId, new HashSet<>());
            
            ctx.send(Map.of(
                "type", "CONNECTED",
                "sessionId", sessionId
            ));
        });
        
        ws.onMessage(ctx -> {
            Map<String, Object> message = mapper.readValue(
                ctx.message(), Map.class);
            
            String action = (String) message.get("action");
            if ("SUBSCRIBE".equals(action)) {
                handleSubscribe(ctx, message);
            } else if ("UNSUBSCRIBE".equals(action)) {
                handleUnsubscribe(ctx, message);
            }
        });
        
        ws.onClose(ctx -> {
            sessions.remove(ctx.getSessionId());
            subscriptions.remove(ctx.getSessionId());
        });
    }
    
    private void handleSubscribe(WsContext ctx, Map<String, Object> message) {
        Map<String, Object> filters = (Map<String, Object>) message.get("filters");
        String loanId = (String) filters.get("loanId");
        
        if (loanId != null) {
            subscriptions.get(ctx.getSessionId()).add(loanId);
            ctx.send(Map.of(
                "type", "SUBSCRIBED",
                "loanId", loanId
            ));
        }
    }
    
    private void handleUnsubscribe(WsContext ctx, Map<String, Object> message) {
        String loanId = (String) message.get("loanId");
        subscriptions.get(ctx.getSessionId()).remove(loanId);
    }
    
    public void broadcast(LoanEvent event) {
        subscriptions.forEach((sessionId, loanIds) -> {
            if (loanIds.contains(event.getLoanId())) {
                WsContext ctx = sessions.get(sessionId);
                if (ctx != null) {
                    try {
                        ctx.send(event);
                    } catch (Exception e) {
                        // Handle send failure
                    }
                }
            }
        });
    }
}
```

## Phase 3: Raft Consensus Implementation

### Step 1: Define Raft Core Classes

#### Create Raft Node (`consensus/raft/RaftNode.java`)

```java
package edu.csu.cs555.ledger.consensus.raft;

import edu.csu.cs555.ledger.models.LoanEvent;
import java.util.*;
import java.util.concurrent.*;

public class RaftNode {
    public enum NodeState {
        FOLLOWER, CANDIDATE, LEADER
    }
    
    private final String nodeId;
    private NodeState state;
    private int currentTerm;
    private String votedFor;
    private final List<LogEntry> log;
    private final Map<String, Integer> nextIndex;
    private final Map<String, Integer> matchIndex;
    private final Set<String> peers;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> electionTimer;
    private ScheduledFuture<?> heartbeatTimer;
    
    // Configuration
    private final int electionTimeoutMin = 150;
    private final int electionTimeoutMax = 300;
    private final int heartbeatInterval = 50;
    
    public RaftNode(String nodeId, Set<String> peers) {
        this.nodeId = nodeId;
        this.peers = peers;
        this.state = NodeState.FOLLOWER;
        this.currentTerm = 0;
        this.votedFor = null;
        this.log = new ArrayList<>();
        this.nextIndex = new ConcurrentHashMap<>();
        this.matchIndex = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        resetElectionTimer();
    }
    
    // Start election when timeout occurs
    private void startElection() {
        state = NodeState.CANDIDATE;
        currentTerm++;
        votedFor = nodeId;
        
        // Request votes from all peers
        int votesReceived = 1; // Vote for self
        
        for (String peer : peers) {
            CompletableFuture<Boolean> voteFuture = requestVote(peer);
            voteFuture.thenAccept(granted -> {
                if (granted) {
                    // Count vote
                }
            });
        }
        
        // If received majority, become leader
        if (votesReceived > (peers.size() + 1) / 2) {
            becomeLeader();
        }
    }
    
    private void becomeLeader() {
        state = NodeState.LEADER;
        cancelElectionTimer();
        
        // Initialize nextIndex and matchIndex
        for (String peer : peers) {
            nextIndex.put(peer, log.size());
            matchIndex.put(peer, 0);
        }
        
        // Start sending heartbeats
        startHeartbeat();
    }
    
    private void becomeFollower(int term) {
        state = NodeState.FOLLOWER;
        currentTerm = term;
        votedFor = null;
        cancelHeartbeatTimer();
        resetElectionTimer();
    }
    
    private void resetElectionTimer() {
        if (electionTimer != null) {
            electionTimer.cancel(false);
        }
        
        int timeout = ThreadLocalRandom.current().nextInt(
            electionTimeoutMin, electionTimeoutMax);
        
        electionTimer = scheduler.schedule(
            this::startElection, timeout, TimeUnit.MILLISECONDS);
    }
    
    private void startHeartbeat() {
        heartbeatTimer = scheduler.scheduleAtFixedRate(
            this::sendHeartbeats, 0, heartbeatInterval, TimeUnit.MILLISECONDS);
    }
    
    private void sendHeartbeats() {
        for (String peer : peers) {
            sendAppendEntries(peer);
        }
    }
    
    // RPC methods
    private CompletableFuture<Boolean> requestVote(String peer) {
        // Send RequestVote RPC to peer
        // Return future with vote result
        return CompletableFuture.completedFuture(false);
    }
    
    private void sendAppendEntries(String peer) {
        // Send AppendEntries RPC to peer
    }
    
    // Public API
    public CompletableFuture<Boolean> propose(LoanEvent event) {
        if (state != NodeState.LEADER) {
            return CompletableFuture.completedFuture(false);
        }
        
        // Append to log
        LogEntry entry = new LogEntry(currentTerm, event);
        log.add(entry);
        
        // Replicate to followers
        for (String peer : peers) {
            sendAppendEntries(peer);
        }
        
        return CompletableFuture.completedFuture(true);
    }
    
    public void shutdown() {
        scheduler.shutdown();
    }
}
```

#### Create Log Entry (`consensus/raft/LogEntry.java`)

```java
package edu.csu.cs555.ledger.consensus.raft;

import edu.csu.cs555.ledger.models.LoanEvent;

public class LogEntry {
    private final int term;
    private final LoanEvent event;
    private final long timestamp;
    
    public LogEntry(int term, LoanEvent event) {
        this.term = term;
        this.event = event;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters...
}
```

## Phase 4: PBFT Implementation

### Step 1: Define PBFT Core Classes

#### Create PBFT Node (`consensus/pbft/PBFTNode.java`)

```java
package edu.csu.cs555.ledger.consensus.pbft;

import edu.csu.cs555.ledger.models.LoanEvent;
import java.util.*;
import java.util.concurrent.*;

public class PBFTNode {
    private final int nodeId;
    private final int totalNodes;
    private final int f; // Number of faults to tolerate
    private int viewNumber;
    private boolean isPrimary;
    
    // Message logs for consensus phases
    private final Map<String, PrePrepareMessage> prePrepareLog;
    private final Map<String, Set<PrepareMessage>> prepareLog;
    private final Map<String, Set<CommitMessage>> commitLog;
    
    // Pending requests
    private final Queue<ClientRequest> pendingRequests;
    private int sequenceNumber;
    
    public PBFTNode(int nodeId, int totalNodes) {
        this.nodeId = nodeId;
        this.totalNodes = totalNodes;
        this.f = (totalNodes - 1) / 3;
        this.viewNumber = 0;
        this.isPrimary = (nodeId == viewNumber % totalNodes);
        this.prePrepareLog = new ConcurrentHashMap<>();
        this.prepareLog = new ConcurrentHashMap<>();
        this.commitLog = new ConcurrentHashMap<>();
        this.pendingRequests = new ConcurrentLinkedQueue<>();
        this.sequenceNumber = 0;
    }
    
    // Phase 1: Client request
    public void handleClientRequest(LoanEvent event) {
        ClientRequest request = new ClientRequest(event);
        
        if (isPrimary) {
            // Assign sequence number and start consensus
            sequenceNumber++;
            String digest = computeDigest(request);
            
            // Create and broadcast PRE-PREPARE
            PrePrepareMessage prePrepare = new PrePrepareMessage(
                viewNumber, sequenceNumber, digest, request);
            
            prePrepareLog.put(digest, prePrepare);
            broadcastPrePrepare(prePrepare);
        } else {
            // Forward to primary
            forwardToPrimary(request);
        }
    }
    
    // Phase 2: Pre-Prepare (Primary -> All)
    public void handlePrePrepare(PrePrepareMessage msg) {
        if (!isPrimary && validatePrePrepare(msg)) {
            String digest = msg.getDigest();
            prePrepareLog.put(digest, msg);
            
            // Broadcast PREPARE to all nodes
            PrepareMessage prepare = new PrepareMessage(
                viewNumber, msg.getSequenceNumber(), digest, nodeId);
            
            broadcastPrepare(prepare);
        }
    }
    
    // Phase 3: Prepare (All -> All)
    public void handlePrepare(PrepareMessage msg) {
        String key = msg.getViewNumber() + "-" + msg.getSequenceNumber();
        
        prepareLog.computeIfAbsent(key, k -> new HashSet<>()).add(msg);
        
        // Check if we have 2f PREPARE messages
        if (prepareLog.get(key).size() >= 2 * f) {
            // Move to COMMIT phase
            CommitMessage commit = new CommitMessage(
                msg.getViewNumber(), msg.getSequenceNumber(), 
                msg.getDigest(), nodeId);
            
            broadcastCommit(commit);
        }
    }
    
    // Phase 4: Commit (All -> All)
    public void handleCommit(CommitMessage msg) {
        String key = msg.getViewNumber() + "-" + msg.getSequenceNumber();
        
        commitLog.computeIfAbsent(key, k -> new HashSet<>()).add(msg);
        
        // Check if we have 2f+1 COMMIT messages
        if (commitLog.get(key).size() > 2 * f) {
            // Execute the request
            PrePrepareMessage prePrepare = prePrepareLog.get(msg.getDigest());
            if (prePrepare != null) {
                executeRequest(prePrepare.getRequest());
            }
        }
    }
    
    // View change protocol
    public void initiateViewChange() {
        viewNumber++;
        isPrimary = (nodeId == viewNumber % totalNodes);
        
        // Clear message logs
        prePrepareLog.clear();
        prepareLog.clear();
        commitLog.clear();
        
        // Broadcast VIEW-CHANGE message
        // ... implementation
    }
    
    // Helper methods
    private String computeDigest(ClientRequest request) {
        return HashUtil.sha256(request.toString());
    }
    
    private boolean validatePrePrepare(PrePrepareMessage msg) {
        // Validate view number, sequence number, signature
        return msg.getViewNumber() == viewNumber &&
               msg.getSequenceNumber() > 0;
    }
    
    private void executeRequest(ClientRequest request) {
        // Execute the loan event
        // Update local state
        // Send reply to client
    }
    
    // Network methods (to be implemented)
    private void broadcastPrePrepare(PrePrepareMessage msg) { }
    private void broadcastPrepare(PrepareMessage msg) { }
    private void broadcastCommit(CommitMessage msg) { }
    private void forwardToPrimary(ClientRequest request) { }
}
```

## Phase 5: Integration and Testing

### Step 1: Create Main Application

#### Create Ledger Node (`main/LedgerNode.java`)

```java
package edu.csu.cs555.ledger.main;

import io.javalin.Javalin;
import edu.csu.cs555.ledger.api.rest.LoanApplicationController;
import edu.csu.cs555.ledger.api.websocket.EventBroadcaster;
import edu.csu.cs555.ledger.consensus.ConsensusEngine;
import edu.csu.cs555.ledger.consensus.raft.RaftNode;
import edu.csu.cs555.ledger.consensus.pbft.PBFTNode;
import edu.csu.cs555.ledger.core.LoanEventProcessor;
import edu.csu.cs555.ledger.storage.EventStore;
import edu.csu.cs555.ledger.storage.impl.InMemoryEventStore;

public class LedgerNode {
    private final Javalin app;
    private final ConsensusEngine consensus;
    private final LoanEventProcessor processor;
    private final EventBroadcaster broadcaster;
    
    public LedgerNode(NodeConfig config) {
        // Initialize storage
        EventStore eventStore = new InMemoryEventStore();
        
        // Initialize processor
        this.processor = new LoanEventProcessor(eventStore);
        
        // Initialize consensus based on config
        this.consensus = createConsensusEngine(config);
        
        // Initialize API
        this.app = Javalin.create();
        this.broadcaster = new EventBroadcaster();
        
        // Setup routes
        LoanApplicationController loanController = 
            new LoanApplicationController(processor);
        loanController.registerRoutes(app);
        
        // Setup WebSocket
        app.ws("/events", ws -> broadcaster.configure(ws));
    }
    
    private ConsensusEngine createConsensusEngine(NodeConfig config) {
        switch (config.getConsensusType()) {
            case RAFT:
                return new RaftConsensusAdapter(
                    new RaftNode(config.getNodeId(), config.getPeers()));
            case PBFT:
                return new PBFTConsensusAdapter(
                    new PBFTNode(config.getNodeId(), config.getTotalNodes()));
            default:
                return new NoConsensus();
        }
    }
    
    public void start(int port) {
        consensus.start();
        app.start(port);
        System.out.println("Ledger node started on port " + port);
    }
    
    public void stop() {
        app.stop();
        consensus.stop();
    }
    
    public static void main(String[] args) {
        // Parse command line arguments
        NodeConfig config = NodeConfig.fromArgs(args);
        
        // Create and start node
        LedgerNode node = new LedgerNode(config);
        node.start(config.getPort());
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(node::stop));
    }
}
```

### Step 2: Create Test Suite

#### Unit Test Example (`test/unit/LoanStateMachineTest.java`)

```java
package edu.csu.cs555.ledger.test.unit;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;
import edu.csu.cs555.ledger.core.LoanStateMachine;
import edu.csu.cs555.ledger.models.*;

public class LoanStateMachineTest {
    private LoanStateMachine stateMachine;
    
    @Before
    public void setup() {
        stateMachine = new LoanStateMachine();
    }
    
    @Test
    public void testValidTransition() {
        assertTrue(stateMachine.isValidTransition(
            LoanState.SUBMITTED, 
            EventType.CREDIT_CHECK_INITIATED));
    }
    
    @Test
    public void testInvalidTransition() {
        assertFalse(stateMachine.isValidTransition(
            LoanState.SUBMITTED, 
            EventType.FUNDING_COMPLETE));
    }
    
    @Test
    public void testProcessEvent() {
        LoanEvent event = new LoanEvent(
            "loan-123",
            EventType.CREDIT_CHECK_INITIATED,
            Map.of());
        
        LoanState newState = stateMachine.processEvent(
            LoanState.SUBMITTED, event);
        
        assertEquals(LoanState.CREDIT_CHECK, newState);
    }
}
```

#### Integration Test Example (`test/integration/RaftClusterTest.java`)

```java
package edu.csu.cs555.ledger.test.integration;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;
import java.util.*;

public class RaftClusterTest {
    private List<RaftNode> nodes;
    
    @Before
    public void setup() {
        nodes = new ArrayList<>();
        Set<String> peers = Set.of("node1", "node2", "node3");
        
        for (String nodeId : peers) {
            nodes.add(new RaftNode(nodeId, peers));
        }
    }
    
    @Test
    public void testLeaderElection() throws Exception {
        // Start all nodes
        nodes.forEach(RaftNode::start);
        
        // Wait for election
        Thread.sleep(500);
        
        // Verify exactly one leader
        long leaderCount = nodes.stream()
            .filter(n -> n.getState() == NodeState.LEADER)
            .count();
        
        assertEquals(1, leaderCount);
    }
    
    @Test
    public void testLogReplication() throws Exception {
        // Start cluster
        nodes.forEach(RaftNode::start);
        Thread.sleep(500);
        
        // Find leader
        RaftNode leader = nodes.stream()
            .filter(n -> n.getState() == NodeState.LEADER)
            .findFirst()
            .orElseThrow();
        
        // Propose event
        LoanEvent event = new LoanEvent(
            "loan-123",
            EventType.APPLICATION_SUBMITTED,
            Map.of("amount", 50000));
        
        boolean success = leader.propose(event).get();
        assertTrue(success);
        
        // Wait for replication
        Thread.sleep(200);
        
        // Verify all nodes have the event
        for (RaftNode node : nodes) {
            assertTrue(node.getLog().contains(event));
        }
    }
    
    @After
    public void teardown() {
        nodes.forEach(RaftNode::shutdown);
    }
}
```

## Deployment and Operations

### Create Start Scripts

#### Start Single Node (`scripts/start-single.sh`)

```bash
#!/bin/bash
java -jar build/libs/distributed-loan-ledger.jar \
    --mode=CENTRALIZED \
    --port=8080 \
    --data-dir=./data
```

#### Start Raft Cluster (`scripts/start-raft-cluster.sh`)

```bash
#!/bin/bash

# Start registry
java -cp build/libs/distributed-loan-ledger.jar \
    edu.csu.cs555.ledger.main.Registry \
    --port=9000 &

sleep 2

# Start Raft nodes
for i in 1 2 3; do
    java -jar build/libs/distributed-loan-ledger.jar \
        --mode=RAFT \
        --node-id=node$i \
        --port=808$i \
        --registry=localhost:9000 \
        --data-dir=./data/node$i &
done

echo "Raft cluster started with 3 nodes"
```

#### Start PBFT Cluster (`scripts/start-pbft-cluster.sh`)

```bash
#!/bin/bash

# Start registry
java -cp build/libs/distributed-loan-ledger.jar \
    edu.csu.cs555.ledger.main.Registry \
    --port=9000 &

sleep 2

# Start PBFT nodes (4 nodes to tolerate 1 Byzantine fault)
for i in 1 2 3 4; do
    java -jar build/libs/distributed-loan-ledger.jar \
        --mode=PBFT \
        --node-id=$i \
        --port=808$i \
        --registry=localhost:9000 \
        --total-nodes=4 \
        --data-dir=./data/node$i &
done

echo "PBFT cluster started with 4 nodes"
```

### Configuration Files

#### Node Configuration (`config/node.properties`)

```properties
# Node configuration
node.id=node1
node.port=8081
node.data-dir=./data/node1

# Consensus configuration
consensus.type=RAFT
consensus.peers=node2:8082,node3:8083

# Raft specific
raft.election-timeout-min=150
raft.election-timeout-max=300
raft.heartbeat-interval=50

# PBFT specific
pbft.request-timeout=1000
pbft.view-change-timeout=5000
pbft.checkpoint-interval=100

# Storage configuration
storage.type=H2
storage.path=./data/node1/events.db

# API configuration
api.rate-limit=100
api.cors-enabled=true
```

## Testing and Validation

### Performance Testing

Create a load testing script to measure throughput and latency:

```java
package edu.csu.cs555.ledger.test.performance;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class LoadTest {
    public static void main(String[] args) throws Exception {
        int numThreads = 10;
        int requestsPerThread = 1000;
        String serverUrl = "http://localhost:8081";
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        AtomicLong totalTime = new AtomicLong(0);
        AtomicInteger successCount = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();
        
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            futures.add(executor.submit(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    long reqStart = System.nanoTime();
                    
                    // Submit loan application
                    boolean success = submitLoan(serverUrl);
                    
                    long reqTime = System.nanoTime() - reqStart;
                    totalTime.addAndGet(reqTime);
                    
                    if (success) {
                        successCount.incrementAndGet();
                    }
                }
            }));
        }
        
        // Wait for completion
        for (Future<?> future : futures) {
            future.get();
        }
        
        long endTime = System.currentTimeMillis();
        
        // Calculate metrics
        long totalRequests = numThreads * requestsPerThread;
        double throughput = totalRequests * 1000.0 / (endTime - startTime);
        double avgLatency = totalTime.get() / 1_000_000.0 / totalRequests;
        
        System.out.println("Performance Test Results:");
        System.out.println("Total requests: " + totalRequests);
        System.out.println("Success rate: " + (successCount.get() * 100.0 / totalRequests) + "%");
        System.out.println("Throughput: " + throughput + " req/s");
        System.out.println("Average latency: " + avgLatency + " ms");
        
        executor.shutdown();
    }
}
```

## Monitoring and Observability

### Add Metrics Collection

```java
package edu.csu.cs555.ledger.monitoring;

import java.util.concurrent.atomic.*;

public class MetricsCollector {
    private final AtomicLong eventsProcessed = new AtomicLong(0);
    private final AtomicLong consensusRounds = new AtomicLong(0);
    private final AtomicLong leaderChanges = new AtomicLong(0);
    private final AtomicLong byzantineFaultsDetected = new AtomicLong(0);
    
    public void recordEvent() {
        eventsProcessed.incrementAndGet();
    }
    
    public void recordConsensusRound() {
        consensusRounds.incrementAndGet();
    }
    
    public void recordLeaderChange() {
        leaderChanges.incrementAndGet();
    }
    
    public void recordByzantineFault() {
        byzantineFaultsDetected.incrementAndGet();
    }
    
    public Map<String, Object> getMetrics() {
        return Map.of(
            "eventsProcessed", eventsProcessed.get(),
            "consensusRounds", consensusRounds.get(),
            "leaderChanges", leaderChanges.get(),
            "byzantineFaultsDetected", byzantineFaultsDetected.get()
        );
    }
}
```

## Next Steps

1. **Complete Implementation**: Follow this guide to implement each component
2. **Test Thoroughly**: Run unit, integration, and performance tests
3. **Create Demo UI**: Build a web interface to visualize the system
4. **Document Results**: Measure and document performance characteristics
5. **Present Findings**: Prepare presentation comparing consensus algorithms

## Troubleshooting

### Common Issues

1. **Port Already in Use**
   - Solution: Change port in configuration or kill existing process

2. **Leader Election Failing**
   - Check network connectivity between nodes
   - Verify election timeout configuration
   - Ensure odd number of nodes for Raft

3. **PBFT Not Reaching Consensus**
   - Verify you have 3f+1 nodes for f faults
   - Check message signatures are valid
   - Ensure network is not partitioned

4. **High Latency**
   - Reduce batch size
   - Optimize serialization
   - Check network latency between nodes

## Resources

- [Raft Paper](https://raft.github.io/raft.pdf)
- [PBFT Paper](http://pmg.csail.mit.edu/papers/osdi99.pdf)
- [Javalin Documentation](https://javalin.io/documentation)
- [Apache Ratis](https://ratis.apache.org/) - Production Raft implementation