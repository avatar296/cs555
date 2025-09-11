package csx55.overlay.node;

import csx55.overlay.spanning.MinimumSpanningTree;
import csx55.overlay.transport.TCPSender;
import csx55.overlay.wireformats.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MessagingNode {

  private String selfIp;
  private int selfPort;

  private String selfId() {
    return selfIp + ":" + selfPort;
  }

  private TCPSender registry;

  private ServerSocket peerServer;
  private final Map<String, Conn> neighbor = new ConcurrentHashMap<>();
  private final Set<Conn> anonymous = ConcurrentHashMap.newKeySet();

  private static final class Conn {
    final Socket s;
    final DataInputStream in;
    final DataOutputStream out;
    volatile String peerId;

    Conn(Socket s) throws IOException {
      this.s = s;
      this.in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
      this.out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
    }
  }

  private final Set<String> vertices = ConcurrentHashMap.newKeySet();
  private final List<MinimumSpanningTree.Edge> links = new CopyOnWriteArrayList<>();
  private final Map<String, List<MinimumSpanningTree.Edge>> mstCache = new ConcurrentHashMap<>();

  private final AtomicInteger sendTracker = new AtomicInteger(0);
  private final AtomicInteger receiveTracker = new AtomicInteger(0);
  private final AtomicInteger relayTracker = new AtomicInteger(0);
  private final AtomicLong sendSummation = new AtomicLong(0);
  private final AtomicLong receiveSummation = new AtomicLong(0);

  private final BlockingQueue<Event> regEvents = new LinkedBlockingQueue<>();

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println(
          "Usage: java csx55.overlay.node.MessagingNode <registry-host> <registry-port>");
      return;
    }
    new MessagingNode().run(args[0], Integer.parseInt(args[1]));
  }

  private void run(String registryHost, int registryPort) throws Exception {
    peerServer = new ServerSocket(0);
    selfPort = peerServer.getLocalPort();

    Thread acceptPeers =
        new Thread(
            () -> {
              try {
                while (!peerServer.isClosed()) {
                  Socket s = peerServer.accept();
                  Conn c = new Conn(s);
                  anonymous.add(c);
                  startPeerReader(c);
                  sendHello(c);
                }
              } catch (IOException ignored) {
              }
            },
            "peer-acceptor");
    acceptPeers.setDaemon(true);
    acceptPeers.start();

    try (TCPSender reg = new TCPSender(registryHost, registryPort)) {
      this.registry = reg;

      selfIp = reg.socket().getLocalAddress().getHostAddress();

      reg.send(new Register(selfIp, selfPort));
      Event resp = reg.read();
      if (!(resp instanceof RegisterResponse)) {
        return;
      }
      RegisterResponse rr = (RegisterResponse) resp;
      if (rr.status() != RegisterResponse.SUCCESS) {
        return;
      }

      Thread regReader =
          new Thread(
              () -> {
                try {
                  while (true) {
                    Event e = reg.read();
                    if (e instanceof MessagingNodeList) {
                      handleDialList((MessagingNodeList) e);
                    } else if (e instanceof LinkWeights) {
                      handleLinkWeights((LinkWeights) e);
                    } else if (e instanceof TaskInitiate) {
                      handleTaskInitiate((TaskInitiate) e);
                    } else if (e instanceof PullTrafficSummary) {
                      handlePullSummary();
                    } else if (e instanceof DeregisterResponse) {
                      regEvents.offer(e);
                    }
                  }
                } catch (IOException ignored) {
                }
              },
              "registry-reader");
      regReader.setDaemon(true);
      regReader.start();

      final Object blocker = new Object();
      Thread cli =
          new Thread(
              () -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                  String line;
                  while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.equals("exit-overlay")) {
                      try {
                        reg.send(new Deregister(selfIp, selfPort));
                        regEvents.poll(5, TimeUnit.SECONDS); // best-effort wait
                        System.out.println("exited overlay");
                      } catch (Exception ex) {
                        System.out.println("exited overlay");
                      }
                      synchronized (blocker) {
                        blocker.notifyAll();
                      }
                      return;
                    } else if (line.equals("print-mst")) {
                      printMST();
                    }
                  }
                } catch (IOException ignored) {
                }
              },
              "node-cli");
      cli.setDaemon(true);
      cli.start();

      synchronized (blocker) {
        blocker.wait();
      }

    } finally {
      try {
        peerServer.close();
      } catch (IOException ignored) {
      }
      neighbor
          .values()
          .forEach(
              c -> {
                try {
                  c.s.close();
                } catch (IOException ignored) {
                }
              });
      anonymous.forEach(
          c -> {
            try {
              c.s.close();
            } catch (IOException ignored) {
            }
          });
    }
  }

  private void handleDialList(MessagingNodeList m) {
    int ok = 0;
    for (String peer : m.peers()) {
      try {
        String[] parts = peer.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        Socket s = new Socket(host, port);
        Conn c = new Conn(s);
        c.peerId = peer;
        neighbor.put(peer, c);
        startPeerReader(c);
        sendHello(c);
        ok++;
      } catch (IOException ignored) {
      }
    }
    System.out.println("All connections are established. Number of connections: " + ok);
  }

  private void handleLinkWeights(LinkWeights lw) {
    vertices.clear();
    links.clear();
    mstCache.clear();

    for (LinkWeights.Link l : lw.links()) {
      links.add(new MinimumSpanningTree.Edge(l.a, l.b, l.w));
      vertices.add(l.a);
      vertices.add(l.b);
    }

    System.out.println("Link weights received and processed. Ready to send messages.");
  }

  private void handleTaskInitiate(TaskInitiate ti) {
    new Thread(() -> runRounds(ti.rounds()), "sender-rounds").start();
  }

  private void handlePullSummary() {
    TrafficSummary ts =
        new TrafficSummary(
            selfIp,
            selfPort,
            sendTracker.get(),
            sendSummation.get(),
            receiveTracker.get(),
            receiveSummation.get(),
            relayTracker.get());
    try {
      registry.send(ts);
    } catch (IOException ignored) {
    }
    sendTracker.set(0);
    receiveTracker.set(0);
    relayTracker.set(0);
    sendSummation.set(0);
    receiveSummation.set(0);
  }

  private void startPeerReader(Conn c) {
    new Thread(
            () -> {
              try {
                EventFactory f = EventFactory.getInstance();
                while (!c.s.isClosed()) {
                  Event e = f.read(c.in);
                  if (e instanceof PeerHello) {
                    String id = ((PeerHello) e).nodeId();
                    c.peerId = id;
                    anonymous.remove(c);
                    neighbor.put(id, c);
                  } else if (e instanceof Message) {
                    onMessage((Message) e);
                  }
                }
              } catch (IOException ignored) {
              }
            },
            "peer-reader")
        .start();
  }

  private void sendHello(Conn c) {
    try {
      synchronized (c.out) {
        new PeerHello(selfId()).write(c.out);
        c.out.flush();
      }
    } catch (IOException ignored) {
    }
  }

  private void runRounds(int rounds) {
    if (vertices.isEmpty() || links.isEmpty()) return;

    List<String> nodes = new ArrayList<>(vertices);
    Random rnd = new Random();

    for (int r = 0; r < rounds; r++) {
      String sink;
      do {
        sink = nodes.get(rnd.nextInt(nodes.size()));
      } while (sink.equals(selfId()));

      for (int i = 0; i < 5; i++) {
        int payload = rnd.nextInt();
        sendTracker.incrementAndGet();
        sendSummation.addAndGet(payload);
        routeMessage(selfId(), sink, payload);
      }
    }

    try {
      registry.send(new TaskComplete(selfIp, selfPort));
    } catch (IOException ignored) {
    }
  }

  private void routeMessage(String src, String dst, int payload) {
    if (src.equals(dst)) {
      receiveTracker.incrementAndGet();
      receiveSummation.addAndGet(payload);
      return;
    }

    List<MinimumSpanningTree.Edge> mst =
        mstCache.computeIfAbsent(src, s -> MinimumSpanningTree.kruskal(vertices, links));

    Map<String, List<String>> a = new HashMap<>();
    for (String v : vertices) a.put(v, new ArrayList<>());
    for (MinimumSpanningTree.Edge e : mst) {
      a.get(e.u).add(e.v);
      a.get(e.v).add(e.u);
    }

    String cur = selfId();
    if (cur.equals(dst)) {
      receiveTracker.incrementAndGet();
      receiveSummation.addAndGet(payload);
      return;
    }

    Map<String, String> parent = new HashMap<>();
    Deque<String> q = new ArrayDeque<>();
    q.add(cur);
    parent.put(cur, "");

    while (!q.isEmpty()) {
      String u = q.removeFirst();
      if (u.equals(dst)) break;
      for (String v : a.getOrDefault(u, Collections.emptyList())) {
        if (!parent.containsKey(v)) {
          parent.put(v, u);
          q.addLast(v);
        }
      }
    }
    if (!parent.containsKey(dst)) return;

    String step = dst;
    String prev = parent.get(step);
    while (prev != null && !prev.isEmpty() && !prev.equals(cur)) {
      step = prev;
      prev = parent.get(step);
    }

    Conn c = neighbor.get(step);
    if (c == null) return;

    try {
      synchronized (c.out) {
        new Message(src, dst, payload).write(c.out);
        c.out.flush();
      }
    } catch (IOException ignored) {
    }
  }

  private void onMessage(Message m) {
    if (m.sinkId().equals(selfId())) {
      receiveTracker.incrementAndGet();
      receiveSummation.addAndGet(m.payload());
    } else {
      relayTracker.incrementAndGet();

      List<MinimumSpanningTree.Edge> mst =
          mstCache.computeIfAbsent(m.sourceId(), s -> MinimumSpanningTree.kruskal(vertices, links));

      Map<String, List<String>> a = new HashMap<>();
      for (String v : vertices) a.put(v, new ArrayList<>());
      for (MinimumSpanningTree.Edge e : mst) {
        a.get(e.u).add(e.v);
        a.get(e.v).add(e.u);
      }

      String cur = selfId(), dst = m.sinkId();

      Map<String, String> parent = new HashMap<>();
      Deque<String> q = new ArrayDeque<>();
      q.add(cur);
      parent.put(cur, "");

      while (!q.isEmpty()) {
        String u = q.removeFirst();
        if (u.equals(dst)) break;
        for (String v : a.getOrDefault(u, Collections.emptyList())) {
          if (!parent.containsKey(v)) {
            parent.put(v, u);
            q.addLast(v);
          }
        }
      }
      if (!parent.containsKey(dst)) return;

      String step = dst;
      String prev = parent.get(step);
      while (prev != null && !prev.isEmpty() && !prev.equals(cur)) {
        step = prev;
        prev = parent.get(step);
      }

      Conn c = neighbor.get(step);
      if (c == null) return;

      try {
        synchronized (c.out) {
          m.write(c.out);
          c.out.flush();
        }
      } catch (IOException ignored) {
      }
    }
  }

  private void printMST() {
    if (vertices.isEmpty() || links.isEmpty()) return;

    String root = selfId();
    List<MinimumSpanningTree.Edge> mstEdges =
        mstCache.computeIfAbsent(root, s -> MinimumSpanningTree.kruskal(vertices, links));

    Map<String, List<MinimumSpanningTree.Edge>> adj = new HashMap<>();
    for (String v : vertices) adj.put(v, new ArrayList<>());
    for (MinimumSpanningTree.Edge e : mstEdges) {
      adj.get(e.u).add(e);
      adj.get(e.v).add(e);
    }

    Set<String> vis = new HashSet<>();
    Deque<String> q = new ArrayDeque<>();
    vis.add(root);
    q.add(root);

    while (!q.isEmpty()) {
      String u = q.removeFirst();
      List<MinimumSpanningTree.Edge> nbrs = new ArrayList<>(adj.get(u));
      nbrs.sort(Comparator.comparing(e -> e.other(u)));
      for (MinimumSpanningTree.Edge e : nbrs) {
        String v = e.other(u);
        if (!vis.contains(v)) {
          vis.add(v);
          q.addLast(v);
          System.out.println(u + ", " + v + ", " + e.w);
        }
      }
    }
  }
}
