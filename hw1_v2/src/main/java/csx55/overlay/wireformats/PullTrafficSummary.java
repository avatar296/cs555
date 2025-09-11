package csx55.overlay.wireformats;

import java.io.*;

public class PullTrafficSummary implements Event {
  public PullTrafficSummary() {}

  public PullTrafficSummary(DataInputStream in) {}

  @Override
  public int type() {
    return Protocol.PULL_TRAFFIC_SUMMARY;
  }

  @Override
  public void write(DataOutputStream out) throws IOException {
    out.writeInt(type());
  }
}
