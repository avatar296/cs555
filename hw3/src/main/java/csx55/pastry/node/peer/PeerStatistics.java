package csx55.pastry.node.peer;

import java.util.concurrent.atomic.AtomicInteger;

public class PeerStatistics {
  private final AtomicInteger filesStored = new AtomicInteger(0);
  private final AtomicInteger lookupsHandled = new AtomicInteger(0);
  private final AtomicInteger joinRequestsHandled = new AtomicInteger(0);
  private final AtomicInteger routingTableUpdates = new AtomicInteger(0);
  private final AtomicInteger leafSetUpdates = new AtomicInteger(0);

  public void incrementFilesStored() {
    filesStored.incrementAndGet();
  }

  public void incrementLookupsHandled() {
    lookupsHandled.incrementAndGet();
  }

  public void incrementJoinRequestsHandled() {
    joinRequestsHandled.incrementAndGet();
  }

  public void incrementRoutingTableUpdates() {
    routingTableUpdates.incrementAndGet();
  }

  public void incrementLeafSetUpdates() {
    leafSetUpdates.incrementAndGet();
  }

  public int getFilesStored() {
    return filesStored.get();
  }

  public int getLookupsHandled() {
    return lookupsHandled.get();
  }

  public int getJoinRequestsHandled() {
    return joinRequestsHandled.get();
  }

  public int getRoutingTableUpdates() {
    return routingTableUpdates.get();
  }

  public int getLeafSetUpdates() {
    return leafSetUpdates.get();
  }

  public String toOutputFormat() {
    StringBuilder sb = new StringBuilder();
    sb.append("Peer Statistics:\n");
    sb.append("  Files stored: ").append(getFilesStored()).append("\n");
    sb.append("  Lookups handled: ").append(getLookupsHandled()).append("\n");
    sb.append("  JOIN requests handled: ").append(getJoinRequestsHandled()).append("\n");
    sb.append("  Routing table updates: ").append(getRoutingTableUpdates()).append("\n");
    sb.append("  Leaf set updates: ").append(getLeafSetUpdates());
    return sb.toString();
  }
}
