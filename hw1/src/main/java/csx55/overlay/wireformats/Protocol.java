package csx55.overlay.wireformats;

/**
 * Defines protocol message type constants for the overlay network.
 * Each constant represents a unique message type that can be
 * transmitted between nodes in the system.
 * 
 * Message types are used by EventFactory to deserialize incoming
 * byte streams into the appropriate Event implementation.
 */
public interface Protocol {
    
    /** Request to deregister a node from the overlay */
    int DEREGISTER_REQUEST = 0;
    
    /** Request to register a new node with the overlay */
    int REGISTER_REQUEST = 1;
    
    /** Response to a node registration request */
    int REGISTER_RESPONSE = 2;
    
    /** List of peer nodes for establishing connections */
    int MESSAGING_NODES_LIST = 3;
    
    /** Network topology with weighted links between nodes */
    int LINK_WEIGHTS = 4;
    
    /** Command to initiate a messaging task */
    int TASK_INITIATE = 5;
    
    /** Data message routed through the overlay */
    int DATA_MESSAGE = 6;
    
    /** Notification that a task has been completed */
    int TASK_COMPLETE = 7;
    
    /** Request to pull traffic statistics from a node */
    int PULL_TRAFFIC_SUMMARY = 8;
    
    /** Traffic statistics report from a node */
    int TRAFFIC_SUMMARY = 9;
    
    /** Response to a node deregistration request */
    int DEREGISTER_RESPONSE = 10;
    
    /** Identification message sent between peer nodes */
    int PEER_IDENTIFICATION = 11;
    
}