package csx55.pastry.transport;

/** Message types for Pastry DHT protocol. */
public enum MessageType {
  // Discovery Node messages
  REGISTER, // Peer registering with Discovery
  REGISTER_RESPONSE, // Discovery response (success/collision)
  GET_RANDOM_NODE, // Request random node from Discovery
  RANDOM_NODE_RESPONSE, // Discovery returns random node
  DEREGISTER, // Peer leaving the network

  // Peer-to-peer messages
  JOIN_REQUEST, // New peer joining through DHT
  JOIN_RESPONSE, // Response with routing table row/leaf set
  ROUTING_TABLE_UPDATE, // Update routing table entry
  LEAF_SET_UPDATE, // Update leaf set

  // Data storage messages
  LOOKUP, // Lookup key in DHT
  LOOKUP_RESPONSE, // Response with destination node
  STORE_FILE, // Store file at node
  RETRIEVE_FILE, // Retrieve file from node
  FILE_DATA, // File content transfer

  // Utility
  HEARTBEAT, // Keep-alive
  ACK // Acknowledgment
}
