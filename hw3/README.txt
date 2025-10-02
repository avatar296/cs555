CS555 - Fall 2025
Homework 3: Implementing the Pastry Peer to Peer Network
Christopher Cowart

================================================================================
MANIFEST
================================================================================

Main Programs:
  - csx55.pastry.node.Discover   : Discovery Node (registry of peers)
  - csx55.pastry.node.Peer       : Peer Node with routing functionality
  - csx55.pastry.node.Data       : Data Storage/Retrieval program

Package Structure:
  - csx55.pastry.node.*          : Main program entry points
  - csx55.pastry.routing.*       : Routing table and leaf set implementations
  - csx55.pastry.transport.*     : TCP messaging and wire protocols
  - csx55.pastry.util.*          : Utility classes (hex conversion, etc.)

================================================================================
BUILDING
================================================================================

To build the project:
  gradle clean build

To run programs:
  gradle runDiscover -Pargs="<port>"
  gradle runPeer -Pargs="<discover-host> <discover-port> <id>"
  gradle runData -Pargs="<discover-host> <discover-port> <mode> <path>"

To create submission tar:
  gradle createTar

================================================================================
RUNNING
================================================================================

Discovery Node:
  java csx55.pastry.node.Discover <port>

Peer Node:
  java csx55.pastry.node.Peer <discover-host> <discover-port> <id>

  Commands:
    - id              : Print node ID
    - leaf-set        : Print leaf set
    - routing-table   : Print routing table
    - list-files      : List files stored on this node
    - exit            : Exit from ring and terminate

Data Storage:
  java csx55.pastry.node.Data <discover-host> <discover-port> store <path>
  java csx55.pastry.node.Data <discover-host> <discover-port> retrieve <path>

================================================================================
NOTES
================================================================================

- Uses Java 11
- TCP-based communications
- 16-bit hexadecimal node identifiers
- Leaf set size: l=1 (2 neighbors total)
- Routing table: 4 rows x 16 columns
- Files stored in /tmp/<peer-id>/

================================================================================
