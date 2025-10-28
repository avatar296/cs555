================================================================================
CS 555: DISTRIBUTED SYSTEMS - PROGRAMMING ASSIGNMENT 4
Distributed, Replicated, and Fault-Tolerant File System
================================================================================

Student Name: [Your Name]
Student ID: [Your ID]
Date: [Date]

================================================================================
PROJECT DESCRIPTION
================================================================================

This project implements a distributed file system with fault tolerance achieved
through two techniques:
1. Replication (3x replication factor)
2. Erasure Coding (Reed-Solomon with k=6 data shards, m=3 parity shards)

The system consists of three main components:
- Controller: Manages metadata about chunk servers and chunks
- ChunkServer: Stores file chunks/fragments on local disk
- Client: Splits files into chunks and assembles them during retrieval

All communication is based on TCP and implemented in Java.

================================================================================
PROJECT STRUCTURE
================================================================================

hw4/
├── src/main/java/csx55/dfs/
│   ├── replication/          # Replication-based implementation
│   │   ├── Controller.java   # Controller for replication mode
│   │   ├── ChunkServer.java  # ChunkServer for replication mode
│   │   └── Client.java       # Client for replication mode
│   │
│   ├── erasure/              # Erasure coding-based implementation
│   │   ├── Controller.java   # Controller for erasure mode
│   │   ├── ChunkServer.java  # ChunkServer for erasure mode
│   │   └── Client.java       # Client for erasure mode
│   │
│   ├── protocol/             # Network protocol messages
│   │   ├── Message.java
│   │   ├── MessageType.java
│   │   ├── HeartbeatMessage.java
│   │   ├── ChunkServersRequest.java
│   │   ├── ChunkServersResponse.java
│   │   ├── StoreChunkRequest.java
│   │   ├── ReadChunkRequest.java
│   │   └── ChunkDataResponse.java
│   │
│   ├── transport/            # TCP networking layer
│   │   ├── TCPConnection.java
│   │   └── TCPServerThread.java
│   │
│   └── util/                 # Utility classes
│       ├── ChecksumUtil.java
│       ├── ChunkMetadata.java
│       ├── FragmentMetadata.java
│       └── FileUtil.java
│
├── scripts/                  # Helper scripts
│   ├── start-replication-controller.sh
│   ├── start-replication-chunkserver.sh
│   ├── start-replication-client.sh
│   ├── start-erasure-controller.sh
│   ├── start-erasure-chunkserver.sh
│   ├── start-erasure-client.sh
│   ├── cleanup.sh
│   ├── format-code.sh
│   ├── test-system.sh
│   └── README.md
│
├── build.gradle              # Gradle build configuration (includes Spotless)
├── gradlew, gradlew.bat      # Gradle wrapper scripts
├── gradle/wrapper/           # Gradle wrapper files
├── reed-solomon-erasure-coding.jar  # Reed-Solomon library
├── .gitignore                # Git ignore rules
└── README.txt                # This file

================================================================================
BUILDING THE PROJECT
================================================================================

To build the project using Gradle wrapper:

    ./gradlew build

To clean and rebuild:

    ./gradlew clean build

To check code formatting (Spotless):

    ./gradlew spotlessCheck

To apply code formatting:

    ./gradlew spotlessApply
    # Or use the helper script:
    ./scripts/format-code.sh

To compile without Gradle (if needed):

    javac -cp ".:reed-solomon-erasure-coding.jar" -d bin \
        src/main/java/csx55/dfs/**/*.java

================================================================================
RUNNING THE SYSTEM
================================================================================

1. REPLICATION MODE
-------------------

Start Controller:
    java csx55.dfs.replication.Controller <port>

Start ChunkServer (on each machine):
    java csx55.dfs.replication.ChunkServer <controller-ip> <controller-port>

Start Client:
    java csx55.dfs.replication.Client <controller-ip> <controller-port>

Client Commands:
    upload <source> <destination>    - Upload file to DFS
    download <source> <destination>  - Download file from DFS
    exit                            - Exit client


2. ERASURE CODING MODE
----------------------

Start Controller:
    java csx55.dfs.erasure.Controller <port>

Start ChunkServer (on each machine):
    java csx55.dfs.erasure.ChunkServer <controller-ip> <controller-port>

Start Client:
    java csx55.dfs.erasure.Client <controller-ip> <controller-port>

Client Commands: (same as replication mode)
    upload <source> <destination>
    download <source> <destination>
    exit


3. USING HELPER SCRIPTS (RECOMMENDED)
--------------------------------------

The easiest way to start components is using the provided helper scripts:

    # Replication mode
    ./scripts/start-replication-controller.sh [port]
    ./scripts/start-replication-chunkserver.sh [controller-host] [controller-port]
    ./scripts/start-replication-client.sh [controller-host] [controller-port]

    # Erasure coding mode
    ./scripts/start-erasure-controller.sh [port]
    ./scripts/start-erasure-chunkserver.sh [controller-host] [controller-port]
    ./scripts/start-erasure-client.sh [controller-host] [controller-port]

Examples:
    ./scripts/start-replication-controller.sh 8000
    ./scripts/start-replication-chunkserver.sh localhost 8000
    ./scripts/start-replication-client.sh localhost 8000

These scripts automatically build the project before running.


4. USING GRADLE WRAPPER TASKS
------------------------------

You can also use Gradle wrapper to run components:

    ./gradlew runReplicationController -PappArgs="<port>"
    ./gradlew runReplicationChunkServer -PappArgs="<controller-ip> <controller-port>"
    ./gradlew runReplicationClient -PappArgs="<controller-ip> <controller-port>"

    ./gradlew runErasureController -PappArgs="<port>"
    ./gradlew runErasureChunkServer -PappArgs="<controller-ip> <controller-port>"
    ./gradlew runErasureClient -PappArgs="<controller-ip> <controller-port>"

Example:
    ./gradlew runReplicationController -PappArgs="8000"


5. UTILITY SCRIPTS
------------------

Format code with Spotless:
    ./scripts/format-code.sh

Clean project (removes build artifacts, chunk storage, test data):
    ./scripts/cleanup.sh

Get testing instructions:
    ./scripts/test-system.sh replication
    ./scripts/test-system.sh erasure

================================================================================
IMPLEMENTATION DETAILS
================================================================================

REPLICATION MODE:
-----------------
- Files split into 64KB chunks
- Each chunk replicated 3 times
- Chunks stored with SHA-1 checksums for 8KB slices
- Write pipeline: Client → Server A → Server B → Server C
- Controller tracks chunk locations (in-memory only)
- Heartbeats: Major (60s), Minor (15s)
- Corruption detection via checksum verification
- Automatic error correction from valid replicas
- Failure detection and recovery

ERASURE CODING MODE:
--------------------
- Files split into 64KB chunks
- Each chunk encoded into 9 fragments (6 data + 3 parity)
- Uses Reed-Solomon encoding
- Any 6 fragments can reconstruct the chunk
- More storage efficient than 3x replication
- Same heartbeat and failure detection as replication

CHUNK STORAGE:
--------------
- Storage location: /tmp/chunk-server/
- Replication: /tmp/chunk-server/path/to/file_chunk<N>
- Erasure: /tmp/chunk-server/path/to/file_chunk<N>_shard<M>

METADATA:
---------
- Versioning: Incremented on each write
- Sequence numbers: Track chunk order
- Timestamps: Last modification time
- Free space: 1GB initial capacity per server

================================================================================
KEY FEATURES IMPLEMENTED
================================================================================

[X] File chunking (64KB chunks)
[X] Replication (3x replication factor)
[X] Erasure coding (k=6, m=3 Reed-Solomon)
[X] SHA-1 checksums for integrity
[X] Heartbeat system (major/minor)
[X] Corruption detection
[X] Error correction
[X] Failure detection
[X] Recovery coordination
[X] Pipeline-based writes
[X] TCP-based communication

================================================================================
TESTING NOTES
================================================================================

Testing was performed with:
- [Number] chunk servers
- Files of various sizes: [list sizes]
- Induced corruptions: [describe tests]
- Chunk server failures: [describe tests]

Results:
- [Describe test results]
- [Performance observations]
- [Comparison of replication vs erasure coding]

================================================================================
KNOWN ISSUES / LIMITATIONS
================================================================================

[Document any known issues or limitations here]

================================================================================
REFERENCES
================================================================================

- Reed-Solomon Java implementation: https://github.com/Backblaze/JavaReedSolomon
- Assignment specification: CS555-Fall25-HW4.pdf
- Course website: http://www.cs.colostate.edu/~csx55

================================================================================
