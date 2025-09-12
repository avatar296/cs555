package csx55.threads.core;

public class OverlayState {
  private volatile String successor; // "<ip>:<port>"
  private volatile String predecessor; // "<ip>:<port>"
  private volatile int ringSize;

  public String getSuccessor() {
    return successor;
  }

  public void setSuccessor(String successor) {
    this.successor = successor;
  }

  public String getPredecessor() {
    return predecessor;
  }

  public void setPredecessor(String predecessor) {
    this.predecessor = predecessor;
  }

  public int getRingSize() {
    return ringSize;
  }

  public void setRingSize(int ringSize) {
    this.ringSize = ringSize;
  }
}
