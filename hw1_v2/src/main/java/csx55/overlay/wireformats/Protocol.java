package csx55.overlay.wireformats;

public interface Protocol {
  int REGISTER = 2;
  int REGISTER_RESPONSE = 3;
  int DEREGISTER = 4;
  int DEREGISTER_RESPONSE = 5;
  int MESSAGING_NODE_LIST = 6;
  int LINK_WEIGHTS = 7;
  int TASK_INITIATE = 8;
  int TASK_COMPLETE = 9;
  int PULL_TRAFFIC_SUMMARY = 10;
  int TRAFFIC_SUMMARY = 11;
  int MESSAGE = 12;
  int PEER_HELLO = 13;
}
