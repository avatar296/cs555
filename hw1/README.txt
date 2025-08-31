CS555 HW1 - Network Overlay with MST Routing
============================================
Author: Christopher Cowart
Date: 2025

Compilation and Execution:
--------------------------
To compile:
gradle build

To run Registry:
gradle runRegistry -Pargs="<port>"
OR
java csx55.overlay.node.Registry <port>

To run MessagingNode:
gradle runMessagingNode -Pargs="<registry-host> <registry-port>"
OR
java csx55.overlay.node.MessagingNode <registry-host> <registry-port>

File Structure:
--------------
- csx55.overlay.node: Main components (Registry, MessagingNode)
- csx55.overlay.wireformats: Message types and protocol definitions
- csx55.overlay.transport: TCP connection management
- csx55.overlay.spanning: MST routing implementation
- csx55.overlay.util: Utility classes for overlay creation and statistics

Implementation Notes:
--------------------
- Uses Prim's algorithm for MST computation from each source node
- Supports configurable connection requirements (CR)
- Tracks message statistics (sent, received, relayed)
- All communication is TCP-based with custom byte[] marshalling
- No Java serialization used

Testing:
--------
1. Start Registry with desired port
2. Start multiple MessagingNode instances (minimum 10)
3. Use Registry commands:
   - list-messaging-nodes
   - setup-overlay <connections>
   - send-overlay-link-weights
   - list-weights
   - start <rounds>
4. Use MessagingNode commands:
   - print-mst
   - exit-overlay