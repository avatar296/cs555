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

