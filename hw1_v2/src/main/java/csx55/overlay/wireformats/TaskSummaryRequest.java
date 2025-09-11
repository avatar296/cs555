package csx55.overlay.wireformats;

import java.io.*;

public class TaskSummaryRequest implements Event {
  public TaskSummaryRequest() {}

  public TaskSummaryRequest(DataInputStream in) {}

  @Override
  public int type() {
    return Protocol.PULL_TRAFFIC_SUMMARY;
  }

  @Override
  public void write(DataOutputStream out) throws IOException {
    out.writeInt(type());
  }
}
