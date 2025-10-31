CS555 - Fall 2025
Homework 4: Fault Tolerant File System
Christopher Cowart

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

