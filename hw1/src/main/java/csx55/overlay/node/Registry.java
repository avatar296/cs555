package csx55.overlay.node;

import csx55.overlay.transport.TCPServerThread;
import csx55.overlay.util.OverlayCreator;
import csx55.overlay.util.StatisticsCollectorAndDisplay;
import csx55.overlay.wireformats.*;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Registry {

  private final Map<String, InetSocketAddress> registered = new ConcurrentHashMap<>();
  private final Map<String, DataOutputStream> outs = new ConcurrentHashMap<>();
  private final OverlayCreator overlayCreator = new OverlayCreator();
  private volatile StatisticsCollectorAndDisplay.StatisticsRun statisticsRun = null;

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("Usage: java csx55.overlay.node.Registry <port>");
      return;
    }
    int port = Integer.parseInt(args[0]);
    new Registry().run(port);
  }

  private void run(int port) throws Exception {
    TCPServerThread server;
    try {
      server = new TCPServerThread(port, this::handleClient);
    } catch (BindException be) {
      System.err.println(
          "Port " + port + " is already in use. Try: ./gradlew runRegistry -Pport=6001");
      return;
    }

    Thread acceptor = new Thread(server, "registry-acceptor");
    acceptor.setDaemon(true);
    acceptor.start();

    System.out.println("Registry listening on port " + port);

    Thread cli =
        new Thread(
            () -> {
              try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = br.readLine()) != null) {
                  line = line.trim();
                  if (line.equals("list-messaging-nodes")) {
                    listMessagingNodes();
                  } else if (line.startsWith("setup-overlay")) {
                    doSetupOverlay(line);
                  } else if (line.equals("send-overlay-link-weights")) {
                    doSendLinkWeights();
                  } else if (line.equals("list-weights")) {
                    doListWeights();
                  } else if (line.startsWith("start")) {
                    doStart(line);
                  }
                }
              } catch (IOException ignored) {
              }
            },
            "registry-cli");
    cli.setDaemon(true);
    cli.start();

    synchronized (this) {
      try {
        this.wait();
      } catch (InterruptedException ignored) {
      }
    }
  }

  private void listMessagingNodes() {
    registered.keySet().forEach(System.out::println);
  }

  private void handleClient(Socket s, DataInputStream in, DataOutputStream out) throws IOException {
    EventFactory f = EventFactory.getInstance();
    try {
      while (!s.isClosed()) {
        Event ev = f.read(in);
        if (ev instanceof Register) {
          handleRegister((Register) ev, s, out);
        } else if (ev instanceof Deregister) {
          handleDeregister((Deregister) ev, s, out);
        } else if (ev instanceof TaskComplete) {
          handleTaskComplete((TaskComplete) ev);
        } else if (ev instanceof TaskSummaryResponse) {
          handleTaskSummaryResponse((TaskSummaryResponse) ev);
        }
      }
    } catch (IOException ignored) {
      // client closed; cleanup on deregister or on push-fail
    }
  }

  private void handleRegister(Register r, Socket s, DataOutputStream out) throws IOException {
    String remoteIp = s.getInetAddress().getHostAddress();
    String id = r.ip() + ":" + r.port();

    if (!remoteIp.equals(r.ip())) {
      new RegisterResponse(RegisterResponse.FAILURE, "IP mismatch").write(out);
      out.flush();
      return;
    }
    if (registered.containsKey(id)) {
      new RegisterResponse(RegisterResponse.FAILURE, "Already registered").write(out);
      out.flush();
      return;
    }

    registered.put(id, new InetSocketAddress(r.ip(), r.port()));
    outs.put(id, out);

    String info =
        "Registration request successful. The number of messaging nodes currently constituting the overlay is ("
            + registered.size()
            + ")";
    new RegisterResponse(RegisterResponse.SUCCESS, info).write(out);
    out.flush();
  }

  private void handleDeregister(Deregister d, Socket s, DataOutputStream out) throws IOException {
    String remoteIp = s.getInetAddress().getHostAddress();
    String id = d.ip() + ":" + d.port();

    if (!remoteIp.equals(d.ip())) {
      new DeregisterResponse(DeregisterResponse.FAILURE, "IP mismatch").write(out);
      out.flush();
      return;
    }
    if (!registered.containsKey(id)) {
      new DeregisterResponse(DeregisterResponse.FAILURE, "Not registered").write(out);
      out.flush();
      return;
    }

    registered.remove(id);
    outs.remove(id);
    new DeregisterResponse(DeregisterResponse.SUCCESS, "Deregistration successful").write(out);
    out.flush();
  }

  private void doSetupOverlay(String cmd) {
    String[] parts = cmd.split("\\s+");
    if (parts.length != 2) {
      System.out.println("Usage: setup-overlay <connection-requirement>");
      return;
    }
    int CR = Integer.parseInt(parts[1]);

    // Use the OverlayCreator utility
    overlayCreator.setupOverlay(CR, registered, outs);
  }

  private void doSendLinkWeights() {
    // Use the OverlayCreator utility
    overlayCreator.sendLinkWeights(outs);
  }

  private void doListWeights() {
    for (LinkWeights.Link l : overlayCreator.getWeightedLinks()) {
      System.out.println(l.a + ", " + l.b + ", " + l.w);
    }
  }

  private void doStart(String cmd) {
    String[] parts = cmd.split("\\s+");
    if (parts.length != 2) return;
    int rounds = Integer.parseInt(parts[1]);

    Set<String> expected = new LinkedHashSet<>(registered.keySet());
    statisticsRun = new StatisticsCollectorAndDisplay.StatisticsRun(expected, rounds);

    TaskInitiate ti = new TaskInitiate(rounds);
    expected.forEach(
        id -> {
          DataOutputStream o = outs.get(id);
          if (o != null) {
            try {
              synchronized (o) {
                ti.write(o);
                o.flush();
              }
            } catch (IOException ignored) {
            }
          }
        });

    new Thread(
            () ->
                StatisticsCollectorAndDisplay.collectAndDisplayStatistics(
                    statisticsRun, outs, 15000),
            "run-orchestrator")
        .start();
  }

  private void handleTaskComplete(TaskComplete tc) {
    StatisticsCollectorAndDisplay.StatisticsRun run = statisticsRun;
    if (run == null) return;
    String id = tc.ip() + ":" + tc.port();
    run.markNodeCompleted(id);
  }

  private void handleTaskSummaryResponse(TaskSummaryResponse ts) {
    StatisticsCollectorAndDisplay.StatisticsRun run = statisticsRun;
    if (run == null) return;
    StatisticsCollectorAndDisplay.NodeStatistics stats =
        StatisticsCollectorAndDisplay.processTrafficSummary(ts);
    run.addStatistics(stats);
  }
}
