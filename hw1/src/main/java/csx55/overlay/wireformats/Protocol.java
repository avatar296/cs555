package csx55.overlay.wireformats;

public interface Protocol {
    
    int DEREGISTER_REQUEST = 0;
    int REGISTER_REQUEST = 1;
    int REGISTER_RESPONSE = 2;
    int MESSAGING_NODES_LIST = 3;
    int LINK_WEIGHTS = 4;
    int TASK_INITIATE = 5;
    int DATA_MESSAGE = 6;
    int TASK_COMPLETE = 7;
    int PULL_TRAFFIC_SUMMARY = 8;
    int TRAFFIC_SUMMARY = 9;
    int DEREGISTER_RESPONSE = 10;
    int PEER_IDENTIFICATION = 11;
    
}