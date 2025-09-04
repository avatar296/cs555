# Claude Agent Configuration for CS555 Distributed Systems - HW1

## Assignment Overview: Network Overlay with MST Routing

This is CS555 Homework 1 - a sophisticated distributed systems assignment implementing a network overlay that uses Minimum Spanning Trees (MST) to route packets. The system demonstrates advanced distributed computing concepts including overlay network construction, shortest-path routing, and distributed message coordination.

**Due Date**: Wednesday September 24th, 2025 @ 8:00 pm  
**Points**: 10% of course grade (10 points total)  
**Restrictions**: No Java serialization, no external JAR files, Java 11 + Gradle 8.3 required

### Core Architecture
- **Registry Node**: Single central coordinator for node registration, overlay setup, and task orchestration
- **Messaging Nodes**: Minimum 10 distributed nodes participating in overlay network 
- **Overlay Network**: Each node connected to CR other nodes (default 4 connections)
- **MST Routing**: Each source computes its own MST for routing to destination nodes
- **TCP Communication**: All inter-node communication via TCP with byte[] marshalling
- **Message Tracking**: Comprehensive statistics tracking for correctness verification

## Development Environment

### Build System  
- **Build Tool**: Gradle 8.3 (CS lab environment: `module load courses/cs555`)
- **Java Version**: Java 11 (sourceCompatibility and targetCompatibility)
- **Package Structure**: All code in `csx55.overlay` package
- **Main Classes**: 
  - Registry: `csx55.overlay.node.Registry portnum`
  - MessagingNode: `csx55.overlay.node.MessagingNode registry-host registry-port`

### Key Commands
```bash
# Build and format code
gradle build
gradle spotlessApply

# Run components (as specified in assignment)
gradle runRegistry -Pargs="<port>"
gradle runMessagingNode -Pargs="<registry-host> <registry-port>"

# Alternative direct execution
java csx55.overlay.node.Registry portnum
java csx55.overlay.node.MessagingNode registry-host registry-port

# Testing
gradle test
gradle check

# Docker deployment (for development)
docker-compose up --build
docker-compose -f docker-compose.test.yml up

# Clean submission preparation  
# First clean any macOS metadata files
find . -name '._*' -delete
find . -name '.DS_Store' -delete

# Create clean submission archive
gradle createTar

# Verify archive contents (should only show required files)
tar -tf build/distributions/Christopher_Cowart_HW1.tar
```

### Code Quality Standards
- **Formatter**: Google Java Format (version 1.19.2)
- **Linting**: Spotless plugin with import organization
- **Testing**: JUnit 5 with AssertJ assertions  
- **Coverage**: Comprehensive integration and unit tests
- **Restrictions**: NO Java serialization (no Serializable interface), NO external JARs, NO GUIs
- **Deductions**: 10-point penalty for violating restrictions

## Package Structure & Conventions

**Required Package**: All classes must be in `csx55.overlay` package

```
csx55.overlay/
├── node/                    # Registry and MessagingNode main classes
├── wireformats/            # Protocol interface and message types
│   └── Protocol.java       # Message type constants (REGISTER_REQUEST, etc.)
├── transport/              # TCP connection management
├── spanning/               # MST algorithm implementation
├── util/                   # Statistics, validation, routing utilities
├── registry/               # Registry-specific services
└── cli/                    # Interactive command handlers
```

### Wire Protocol Message Types
Implement `csx55.overlay.wireformats.Protocol` interface with constants:
- `REGISTER_REQUEST` / `REGISTER_RESPONSE`
- `DEREGISTER_REQUEST` 
- `MESSAGING_NODES_LIST`
- `LINK_WEIGHTS`
- `TASK_INITIATE` / `TASK_COMPLETE`
- `PULL_TRAFFIC_SUMMARY` / `TRAFFIC_SUMMARY`

## Assignment-Specific Requirements

### Registry Functions
1. **Node Registration**: Track messaging nodes (IP:port), validate requests
2. **Overlay Construction**: Orchestrate connections ensuring CR links per node  
3. **Link Weight Assignment**: Random weights 1-10, broadcast to all nodes
4. **Task Coordination**: Initiate message rounds, collect completion status
5. **Statistics Collection**: Aggregate traffic summaries for verification

### MessagingNode Functions
1. **Registration**: Auto-configure port, register with registry
2. **Connection Management**: Accept incoming, initiate outgoing connections
3. **MST Computation**: Calculate routing tree from own perspective
4. **Message Routing**: Route via MST path, track sent/received/relayed counts
5. **Statistics Tracking**: Maintain sendTracker, receiveTracker, summation values

### Interactive Commands

#### Registry Commands:
- `list-messaging-nodes` - Show all registered nodes (IP:port format)
- `list-weights` - Display overlay links with weights
- `setup-overlay <CR>` - Create overlay with CR connections per node
- `send-overlay-link-weights` - Broadcast link weights to all nodes  
- `start <rounds>` - Begin message exchange (each node sends rounds * 5 messages)

#### MessagingNode Commands:
- `print-mst` - Display MST in BFS order from this node's perspective
- `exit-overlay` - Deregister and exit cleanly

## Distributed Systems Best Practices

### Connection Management
- Use `TCPConnectionsCache` for connection pooling and reuse
- Implement proper connection lifecycle management with cleanup
- Handle connection failures with `ConnectionRetryPolicy`
- Monitor connection health with heartbeats

### Message Protocols
- **NO Java Serialization**: All messages use byte[] marshalling/unmarshalling
- **Required Fields**: Each message type has specific mandatory fields
- **Protocol Constants**: Use interface for message type values (avoid hard-coding)
- **Binary Format**: Custom serialization for all wire formats
- **Request-Response**: Registry interactions follow request-response pattern

#### Key Message Formats:
```java
// Registration Request
Message Type (int): REGISTER_REQUEST
IP address (String)
Port number (int)

// Link Weights  
Message Type: LINK_WEIGHTS
Number of links: L
Linkinfo1 (ipA:portA ipB:portB weight)
...
LinkinfoL

// Traffic Summary
Message Type: TRAFFIC_SUMMARY  
Node IP/Port, Messages Sent, Send Summation, 
Messages Received, Receive Summation, Messages Relayed
```

### MST Routing Implementation
- **Algorithm**: Implement Prim's or Kruskal's algorithm
- **Per-Source MST**: Each source node computes its own MST when sending
- **Root Perspective**: MST rooted at source node, spans all network nodes
- **Routing Path**: Follow MST edges from source to destination
- **No Cycles**: MST ensures loop-free routing without duplicate detection
- **Caching**: Cache computed MST since link weights are static

### Service Architecture  
- **Foreground Processes**: Both components run with interactive command support
- **TCP Server Sockets**: MessagingNodes auto-configure ports (no hard-coding)
- **Connection Tracking**: Registry orchestrates who connects to whom
- **Statistics Tracking**: Each node maintains integer counters and long summations
- **Correctness Verification**: Total sent must equal total received across all nodes

### Error Handling
- Use ValidationUtil for input validation
- Implement graceful degradation for network failures
- Log errors appropriately with LoggerUtil
- Provide meaningful error messages in protocols

### Threading and Concurrency
- Use thread-safe collections where needed
- Implement proper synchronization for shared state
- Handle concurrent connections properly
- Use volatile fields for flags shared across threads

## Testing Strategy & Grading

### Automated Grading (45-60 seconds feedback)
- **Output Format Compliance**: Exact format matching required for autograder
- **Command Testing**: Each command worth specific points (1-3 points each)
- **Unlimited Submissions**: Until deadline, highest score retained
- **Real-time Feedback**: Know your score before submission deadline

### Test Categories
- **Registration Flow**: Node registration/deregistration scenarios
- **Overlay Construction**: Various CR values, partition prevention
- **Message Routing**: MST computation and routing verification  
- **Statistics Verification**: Message count and summation matching
- **Scale Testing**: 10+ nodes, various network topologies

### Command Point Values:
- `list-messaging-nodes`: 1pt
- `list-weights`: 1pt  
- `setup-overlay`: 1pt
- `send-overlay-link-weights`: 1pt
- `start <rounds>`: 3pt
- `print-mst`: 2pt
- `exit-overlay`: 1pt

### Testing Patterns
```java
// Message routing test
@Test
void testMSTRouting() {
    // Given: 10-node overlay with known weights
    // When: Node computes MST and routes message  
    // Then: Message follows MST path, statistics updated
}

// Statistics verification
@Test  
void testMessageTracking() {
    // Given: Completed message rounds
    // When: Collect traffic summaries
    // Then: Total sent == total received, summations match
}

// Use AssertJ for fluent assertions
assertThat(trafficSummary)
    .satisfies(summary -> {
        assertThat(summary.getSentCount()).isEqualTo(expectedSent);
        assertThat(summary.getReceivedCount()).isMultipleOf(5); // Rounds have 5 messages
    });
```

### Docker Testing
- Use `docker-compose.test.yml` for integration testing
- Test with multiple messaging nodes (10+ nodes)
- Verify overlay construction and message routing
- Monitor container health and resource usage

## Assignment Milestones & Workflows

### 4-Week Development Timeline

**Week 1 (Milestone 1)**:
- Implement basic TCP communication between two nodes
- Create Registry and MessagingNode skeletons
- Test message exchange between registry and single node

**Week 2 (Milestone 2)**:
- Complete registration/deregistration protocols  
- Implement overlay construction logic (setup-overlay command)
- Test with 10 messaging nodes, command processing
- **Submit first version for autograder feedback**

**Week 3 (Milestone 3)**:
- Implement MST algorithm and routing logic
- Add message tracking and statistics collection
- Complete start command with message rounds
- **Submit 3+ times for iterative improvement**

**Week 4 (Milestone 4)**:
- Perfect output formatting for autograder compliance
- Handle edge cases and error conditions  
- Final testing and optimization
- **Multiple submissions to achieve perfect score**

## Development Workflows

### Implementing Wire Protocol Messages
1. **Define Constants**: Add message type to `Protocol` interface
2. **Create Message Class**: Implement in `csx55.overlay.wireformats`
3. **Marshalling Methods**: Convert to/from byte[] (NO Serializable interface)
4. **Field Validation**: Ensure all required fields present
5. **Handler Integration**: Update Registry/MessagingNode message processing
6. **Testing**: Verify message format and processing

### Example Message Implementation:
```java
public class RegisterRequest {
    private int messageType;
    private String ipAddress;
    private int portNumber;
    
    public byte[] marshall() {
        // Convert to byte[] without serialization
    }
    
    public static RegisterRequest unmarshall(byte[] data) {
        // Parse from byte[] without serialization  
    }
}
```

### Adding Registry Commands
1. **Command Parser**: Handle command input in foreground process
2. **Validation**: Check preconditions (e.g., minimum nodes for overlay)
3. **Orchestration**: Coordinate messaging node actions  
4. **Output Format**: Match exact format for autograder compatibility
5. **Error Handling**: Provide meaningful error messages

### Example Command Implementation:
```java
public void handleSetupOverlay(int connectionRequirement) {
    if (registeredNodes.size() < connectionRequirement) {
        System.out.println("Error: Not enough nodes");
        return;
    }
    // Orchestrate overlay construction
    // Send MESSAGING_NODES_LIST to each node
    System.out.println("setup completed with " + totalConnections + " connections");
}
```

### MST Algorithm Implementation
1. **Choose Algorithm**: Prim's or Kruskal's for MST computation
2. **Root Perspective**: Always compute from source node as root
3. **Edge Weights**: Use link weights from LINK_WEIGHTS message
4. **Path Following**: Route messages along MST edges toward destination
5. **Optimization**: Cache MST since weights are static after assignment

### Example MST Usage:
```java
public class MessagingNode {
    private Map<String, MinimumSpanningTree> mstCache = new HashMap<>();
    
    public void routeMessage(Message msg, String destinationId) {
        String sourceId = getNodeId();
        MinimumSpanningTree mst = mstCache.computeIfAbsent(sourceId, 
            id -> computeMST(id, linkWeights));
        
        List<String> path = mst.getPathTo(destinationId);
        forwardAlongPath(msg, path);
    }
}
```

### Performance Optimization
1. Profile connection usage and identify bottlenecks
2. Optimize message serialization/deserialization
3. Tune thread pool sizes and connection limits
4. Monitor garbage collection and memory usage
5. Use `StatisticsCollector` to track performance metrics

## Containerization Best Practices

### Docker Configuration
- Multi-stage builds for optimized image size
- Proper health checks for service discovery
- Resource limits and JVM tuning
- Structured logging with volume mounts
- Network isolation with custom bridge networks

### Scaling Considerations
- Use Docker Compose scaling features
- Implement service discovery for dynamic scaling
- Monitor container resource usage
- Handle node failures gracefully
- Use rolling updates for zero-downtime deployment

## Assignment Compliance Checklist

Before submission, verify:

**Core Requirements**:
- [ ] All classes in `csx55.overlay` package
- [ ] NO Java serialization (Serializable interface)
- [ ] NO external JAR files
- [ ] NO GUI components
- [ ] TCP-only communication with byte[] marshalling

**Functionality**:
- [ ] All Registry commands implemented with exact output format
- [ ] All MessagingNode commands working correctly
- [ ] MST computation and routing functional
- [ ] Message statistics tracking accurate
- [ ] 10+ nodes supported in overlay

**Output Compliance**:
- [ ] Command outputs match specification exactly  
- [ ] Table formatting uses spaces (not tabs) as separators
- [ ] IP:port format used consistently
- [ ] Statistics summation verification passes

**Submission Format**:
- [ ] Single .tar file named `<FirstName>_<LastName>_HW1.tar`
- [ ] Contains all Java files, build.gradle, README.txt
- [ ] NO additional files or folders included
- [ ] **NO macOS metadata files** (._*, .DS_Store, etc.)
- [ ] **NO test files** (src/test/**, *Test.java, *Tests.java)
- [ ] Tar contents verified with `tar -tf` command
- [ ] Code documented appropriately

## Common Patterns and Examples

### Message Tracking Pattern
```java
public class MessagingNode {
    private int sendTracker = 0;
    private int receiveTracker = 0; 
    private int relayTracker = 0;
    private long sendSummation = 0L;
    private long receiveSummation = 0L;
    
    public void sendMessage(int payload, String destinationId) {
        // Route via MST, update tracking
        sendTracker++;
        sendSummation += payload;
    }
    
    public void receiveMessage(int payload) {
        receiveTracker++;
        receiveSummation += payload;
    }
    
    public void relayMessage(Message msg) {
        relayTracker++;
        // Forward without updating send/receive counters
    }
}
```

### Registry Orchestration Pattern  
```java
public class Registry {
    private Map<String, NodeInfo> registeredNodes = new HashMap<>();
    
    public void setupOverlay(int connectionRequirement) {
        for (NodeInfo node : registeredNodes.values()) {
            List<NodeInfo> peers = selectPeersForNode(node, connectionRequirement);
            sendMessagingNodesList(node, peers);
        }
        System.out.println("setup completed with " + totalConnections + " connections");
    }
}
```

### Output Format Compliance Pattern
```java
// Registry list-messaging-nodes output
public void listMessagingNodes() {
    for (NodeInfo node : registeredNodes.values()) {
        System.out.println(node.getIpAddress() + ":" + node.getPort());
    }
}

// Registry traffic summary table (EXACT format required)
public void printTrafficSummaryTable(List<TrafficSummary> summaries) {
    long totalSent = 0, totalReceived = 0;
    long totalSentSum = 0, totalReceivedSum = 0;
    
    for (TrafficSummary summary : summaries) {
        System.out.println(summary.getNodeId() + " " + 
                          summary.getSentCount() + " " +
                          summary.getReceivedCount() + " " +
                          String.format("%.2f", summary.getSentSummation()) + " " +
                          String.format("%.2f", summary.getReceivedSummation()) + " " +
                          summary.getRelayedCount());
        totalSent += summary.getSentCount();
        totalReceived += summary.getReceivedCount();
        totalSentSum += summary.getSentSummation();
        totalReceivedSum += summary.getReceivedSummation();
    }
    System.out.println("sum " + totalSent + " " + totalReceived + " " +
                      String.format("%.2f", totalSentSum) + " " + 
                      String.format("%.2f", totalReceivedSum));
}
```

## macOS Development & Submission Issues

### ⚠️ Critical: macOS Metadata File Problem
**Issue**: Autograder rejects files starting with `._` (AppleDouble metadata files)  
**Cause**: macOS automatically creates hidden metadata files when accessing directories  
**Impact**: Immediate submission rejection, zero points until fixed  
**Solution**: Use updated build.gradle createTar task with proper exclusions

### Immediate Fix for Rejected Submissions
```bash
# Remove ALL existing macOS metadata files
find . -name '._*' -delete
find . -name '.DS_Store' -delete  
find . -name '.AppleDouble' -type d -exec rm -rf {} +
find . -name '.LSOverride' -delete

# Verify complete cleanup (should return no results)
find . -name '._*' -o -name '.DS_Store'

# Create clean submission
gradle createTar

# CRITICAL: Verify tar contents before submitting
tar -tf build/distributions/Christopher_Cowart_HW1.tar
```

### Expected Clean Tar Contents
✅ **Should contain ONLY**:
```
Christopher_Cowart_HW1/build.gradle
Christopher_Cowart_HW1/README.txt
Christopher_Cowart_HW1/src/main/java/csx55/overlay/node/Registry.java
Christopher_Cowart_HW1/src/main/java/csx55/overlay/node/MessagingNode.java
# ... other .java files only (no hidden files)
```

❌ **Should NOT contain** (autograder rejection triggers):
- Any files starting with `._`
- `.DS_Store` files  
- Files with `/._` in the path
- Hidden system directories

### Prevention Strategies

#### Add to .gitignore (Create if missing)
```gitignore
# macOS system files
.DS_Store
.AppleDouble  
.LSOverride
._*

# Other hidden files
Thumbs.db
*.tmp
*.swp
*~
```

#### Development Best Practices for macOS
1. **Use Terminal/IDE**: Avoid Finder operations in project directory
2. **Regular Cleanup**: Run cleanup commands before every submission attempt  
3. **Verify Everything**: Always inspect tar contents before Canvas upload
4. **Test Extraction**: Extract and compile your submission in a clean directory

### Comprehensive Submission Preparation Checklist

**Pre-Submission (EVERY TIME)**:
- [ ] Run `find . -name '._*' -delete` to remove AppleDouble files
- [ ] Run `find . -name '.DS_Store' -delete` to remove Finder metadata
- [ ] Execute `gradle spotlessApply` to format code
- [ ] Execute `gradle build` to ensure compilation success
- [ ] Execute `gradle createTar` to create submission archive  
- [ ] **CRITICAL**: Run `tar -tf build/distributions/Christopher_Cowart_HW1.tar` to verify contents
- [ ] Confirm NO `._*` or `.DS_Store` files in the listing
- [ ] **Verify NO test files**: `tar -tf build/distributions/Christopher_Cowart_HW1.tar | grep -i test` (should return no results)
- [ ] Count files: Should be ~58 total (1 build.gradle + 1 README.txt + ~43 main .java files + directories)
- [ ] Test extraction in clean directory: `tar -xf build/distributions/Christopher_Cowart_HW1.tar`
- [ ] Test compilation of extracted code: `cd Christopher_Cowart_HW1 && gradle build`

### Troubleshooting Continued Rejections

**If autograder still rejects after fixes**:
1. Download your submitted .tar file from Canvas
2. Extract and list ALL files: `tar -tf downloaded_file.tar | sort`
3. Look for problematic files: `tar -tf downloaded_file.tar | grep -E '(^\\._|/\\._|\\.DS_Store)'`
4. If any results appear, the tar still contains metadata files - recreate with updated build.gradle
5. Verify your build.gradle matches the updated version with proper exclusions

### Common macOS Development Issues & Solutions

**Issue**: Build.gradle task includes metadata files despite exclusions  
**Solution**: Use the updated createTar task with comprehensive exclude patterns

**Issue**: Files keep reappearing after deletion  
**Solution**: Avoid using Finder in the project directory; use terminal/IDE only

**Issue**: Git commits include metadata files  
**Solution**: Add comprehensive .gitignore file to project root

**Issue**: Docker builds include metadata files  
**Solution**: Add .dockerignore file with same patterns as .gitignore

## Key Success Factors

1. **Start Early**: Begin week 1, submit iteratively for autograder feedback
2. **Clean Submissions**: Always verify tar contents before submitting (macOS users especially!)
3. **Format Compliance**: Match exact output specifications for full points  
4. **Statistics Accuracy**: Ensure message counting and summation verification
5. **MST Implementation**: Correctly compute and use routing trees
6. **No Shortcuts**: Avoid Java serialization, implement custom marshalling
7. **Interactive Testing**: Use foreground commands to debug overlay behavior
8. **Scale Testing**: Verify with 10+ nodes and various connection requirements

This configuration transforms Claude into a specialized assistant for CS555 HW1, understanding the assignment requirements, grading criteria, and implementation patterns needed for success on this network overlay with MST routing project.