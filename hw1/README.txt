CS555 - Distributed Systems - Homework 1
Christopher Cowart
Fall 2025

FILES MANIFEST:
==============
build.gradle                        - Gradle build configuration
settings.gradle                     - Gradle project settings
README.txt                         - This file

Source Code Organization:
------------------------
src/main/java/csx55/overlay/
    node/
        Registry.java              - Registry server implementation
        MessagingNode.java         - Messaging node client implementation
    
    transport/
        TCPSender.java             - TCP sender for outgoing messages
        TCPServerThread.java       - TCP server thread for accepting connections
    
    spanning/
        MinimumSpanningTree.java   - MST computation using Prim's algorithm
    
    util/
        OverlayCreator.java        - Overlay topology creation and management
        StatisticsCollectorAndDisplay.java - Statistics collection and reporting
    
    wireformats/
        Protocol.java              - Protocol constants and message types
        Event.java                 - Base event interface
        EventFactory.java          - Factory for creating event objects
        Register.java              - Registration request message
        RegisterResponse.java      - Registration response message
        Deregister.java           - Deregistration request message
        DeregisterResponse.java    - Deregistration response message
        MessagingNodeList.java     - List of nodes to connect to
        LinkWeights.java           - Weighted link information
        TaskInitiate.java          - Start task rounds message
        TaskComplete.java          - Task completion notification
        TaskSummaryRequest.java    - Request traffic statistics
        TaskSummaryResponse.java   - Traffic statistics response  
        Message.java               - Data message between nodes
        PeerHello.java             - Peer connection handshake

BUILDING THE PROJECT:
====================
./gradlew build

RUNNING THE COMPONENTS:
======================
Registry:
    ./gradlew runRegistry -Pport=<port>

Messaging Node:
    ./gradlew runMessagingNode -Phost=<registry-host> -Pport=<registry-port>

REGISTRY COMMANDS:
==================
- list-messaging-nodes      : List all registered nodes
- setup-overlay <CR>        : Create overlay with connection requirement
- send-overlay-link-weights : Distribute link weights to nodes
- list-weights             : Display all link weights
- start <rounds>           : Initiate message exchange rounds

MESSAGING NODE COMMANDS:
========================
- print-mst                : Display computed MST
- exit-overlay             : Deregister and exit
