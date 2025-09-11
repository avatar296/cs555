package csx55.overlay.wireformats;

public interface Protocol {
    int REGISTER = 1;
    int REGISTER_RESPONSE = 2;
    int DEREGISTER = 3;
    int DEREGISTER_RESPONSE = 4;

    int MESSAGING_NODE_LIST = 10;
    int LINK_WEIGHTS = 11;
    int TASK_INITIATE = 12;
    int TASK_COMPLETE = 13;
    int TASK_SUMMARY_REQUEST = 14;
    int TASK_SUMMARY_RESPONSE = 15;

    int DATA_MESSAGE = 20; // payload + hoplist
}