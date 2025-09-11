package csx55.overlay.wireformats;

/**
 * Central registry of wire-level message type IDs.
 * Values just need to be unique and consistent across sender/receiver.
 */
public interface Protocol {
    // Registration / lifecycle
    int REGISTER = 2;
    int REGISTER_RESPONSE = 3;
    int DEREGISTER = 4;
    int DEREGISTER_RESPONSE = 5;

    // Overlay construction
    int MESSAGING_NODE_LIST = 6; // Registry -> Node
    int LINK_WEIGHTS = 7; // Registry -> all Nodes

    // Task orchestration
    int TASK_INITIATE = 8; // Registry -> all Nodes
    int TASK_COMPLETE = 9; // Node -> Registry
    int PULL_TRAFFIC_SUMMARY = 10; // Registry -> all Nodes
    int TRAFFIC_SUMMARY = 11; // Node -> Registry

    // Data plane
    int MESSAGE = 12; // Node <-> Node (routed payload)

    // Peer bootstrapping (identify neighbor on TCP connect)
    int PEER_HELLO = 13; // Node <-> Node
}
