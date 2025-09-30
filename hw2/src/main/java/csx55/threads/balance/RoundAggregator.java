package csx55.threads.balance;

import csx55.threads.core.OverlayState;
import csx55.threads.util.NetworkUtil;
import csx55.threads.util.Protocol;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RoundAggregator {
  private final String myId;
  private final OverlayState state;
  private final Map<Integer, RoundInfo> roundInfoMap = new ConcurrentHashMap<>();

  public RoundAggregator(String myId, OverlayState state) {
    this.myId = myId;
    this.state = state;
  }

  private static class RoundInfo {
    final Set<String> seen = ConcurrentHashMap.newKeySet();

    synchronized void add(String origin, int count, int expectedSize) {
      seen.add(origin);
    }
  }

  public void announceRoundGeneration(int round, int generated) {
    RoundInfo info = roundInfoMap.computeIfAbsent(round, k -> new RoundInfo());
    info.add(myId, generated, Math.max(1, state.getRingSize()));
    String msg = Protocol.GEN + " " + round + " " + myId + " " + generated;
    forwardToSuccessor(msg);
  }

  public void handleGenMessage(String msg) {
    // GEN <round> <originId> <count>
    String[] parts = msg.split(" ");
    int round = Integer.parseInt(parts[1]);
    String origin = parts[2];
    int count = Integer.parseInt(parts[3]);

    RoundInfo info = roundInfoMap.computeIfAbsent(round, k -> new RoundInfo());
    info.add(origin, count, Math.max(1, state.getRingSize()));

    if (!origin.equals(myId)) {
      forwardToSuccessor(msg);
    }
  }

  private void forwardToSuccessor(String msg) {
    String successor = state.getSuccessor();
    if (successor == null) return;
    try {
      NetworkUtil.sendString(successor, msg);
    } catch (Exception ignored) {
    }
  }
}
