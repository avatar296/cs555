package csx55.threads;

public class ComputeNodeMock {
  private String ip;
  private int port;
  private ComputeNodeMock successor;

  public ComputeNodeMock(String ip, int port) {
    this.ip = ip;
    this.port = port;
  }

  public void setSuccessor(ComputeNodeMock successor) {
    this.successor = successor;
  }

  public String getIpPort() {
    return ip + ":" + port;
  }

  public void printOverlay() {
    String succ = (successor != null) ? successor.getIpPort() : "none";
    System.out.println("Node " + getIpPort() + " successor → " + succ);
  }
}
