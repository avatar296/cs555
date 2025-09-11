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

/**
 * MessagingNode:
 * - Registers with Registry, keeps the connection open for control messages.
 * - Handles:
 * * MESSAGING_NODE_LIST -> dials peers, prints "All connections are
 * established. Number of connections: X"
 * * LINK_WEIGHTS -> stores overlay, prints "Link weights received and
 * processed. Ready to send messages."
 * * TASK_INITIATE -> sends 5 messages per round to random other node (routes on
 * MST rooted at source)
 * * PULL_TRAFFIC_SUMMARY -> sends TRAFFIC_SUMMARY and resets counters
 * - CLI:
 * * print-mst
 * * exit-overlay -> sends DEREGISTER and prints "exited overlay"
 */
public class MessagingNode {

    // ---- identity ----
    private String selfIp;
    private int selfPort;

    private String selfId() {
        return selfIp + ":" + selfPort;
    }

    // ---- registry socket ----
    private TCPSender registry;

    // ---- peer sockets ----
    private ServerSocket peerServer;
    private final Map<String, Conn> neighbor = new ConcurrentHashMap<>(); // neighborId -> Conn
    private final Set<Conn> anonymous = ConcurrentHashMap.newKeySet(); // before HELLO exchange

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

    // ---- overlay model ----
    private final Set<String> vertices = ConcurrentHashMap.newKeySet();
    private final List<MinimumSpanningTree.Edge> links = new CopyOnWriteArrayList<>();
    private final Map<String, List<MinimumSpanningTree.Edge>> mstCache = new ConcurrentHashMap<>();

    // ---- stats ----
    private final AtomicInteger sendTracker = new AtomicInteger(0);
    private final AtomicInteger receiveTracker = new AtomicInteger(0);
    private final AtomicInteger relayTracker = new AtomicInteger(0);
    private final AtomicLong sendSummation = new AtomicLong(0);
    private final AtomicLong receiveSummation = new AtomicLong(0);

    // ---- registry event coordination ----
    private final BlockingQueue<Event> regEvents = new LinkedBlockingQueue<>();

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java csx55.overlay.node.MessagingNode <registry-host> <registry-port>");
            return;
        }
        new MessagingNode().run(args[0], Integer.parseInt(args[1]));
    }

    private void run(String registryHost, int registryPort) throws Exception {
        // Start peer listener
        peerServer = new ServerSocket(0);
        selfPort = peerServer.getLocalPort();

        Thread acceptPeers = new Thread(() -> {
            try {
                while (!peerServer.isClosed()) {
                    Socket s = peerServer.accept();
                    Conn c = new Conn(s);
                    anonymous.add(c);
                    startPeerReader(c);
                    // Say hello so each side can learn ids
                    sendHello(c);
                }
            } catch (IOException ignored) {
            }
        }, "peer-acceptor");
        acceptPeers.setDaemon(true);
        acceptPeers.start();

        // Connect to registry
        try (TCPSender reg = new TCPSender(registryHost, registryPort)) {
            this.registry = reg;

            // Use the exact bound local address from this TCP socket
            selfIp = reg.socket().getLocalAddress().getHostAddress();

            // REGISTER
            reg.send(new Register(selfIp, selfPort));
            Event resp = reg.read();
            if (!(resp instanceof RegisterResponse)) {
                return;
            }
            RegisterResponse rr = (RegisterResponse) resp;
            if (rr.status() != RegisterResponse.SUCCESS) {
                return;
            }

            // Registry reader
            Thread regReader = new Thread(() -> {
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
            }, "registry-reader");
            regReader.setDaemon(true);
            regReader.start();

            // CLI
            final Object blocker = new Object();
            Thread cli = new Thread(() -> {
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
            }, "node-cli");
            cli.setDaemon(true);
            cli.start();

            // Keep process alive till exit-overlay
            synchronized (blocker) {
                blocker.wait();
            }

        } finally {
            try {
                peerServer.close();
            } catch (IOException ignored) {
            }
            neighbor.values().forEach(c -> {
                try {
                    c.s.close();
                } catch (IOException ignored) {
                }
            });
            anonymous.forEach(c -> {
                try {
                    c.s.close();
                } catch (IOException ignored) {
                }
            });
        }
    }

    // ---------------- Registry message handlers ----------------

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
        // run in a sender thread
        new Thread(() -> runRounds(ti.rounds()), "sender-rounds").start();
    }

    private void handlePullSummary() {
        // Send TRAFFIC_SUMMARY, then reset counters
        TrafficSummary ts = new TrafficSummary(
                selfIp, selfPort,
                sendTracker.get(), sendSummation.get(),
                receiveTracker.get(), receiveSummation.get(),
                relayTracker.get());
        try {
            registry.send(ts);
        } catch (IOException ignored) {
        }
        // Reset for next run
        sendTracker.set(0);
        receiveTracker.set(0);
        relayTracker.set(0);
        sendSummation.set(0);
        receiveSummation.set(0);
    }

    // ---------------- Peer plumbing ----------------

    private void startPeerReader(Conn c) {
        new Thread(() -> {
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
        }, "peer-reader").start();
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

    // ---------------- Routing & rounds ----------------

    private void runRounds(int rounds) {
        if (vertices.isEmpty() || links.isEmpty())
            return;

        // Node set for destination choices
        List<String> nodes = new ArrayList<>(vertices);
        Random rnd = new Random();

        for (int r = 0; r < rounds; r++) {
            // choose any sink != self
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

        // Notify registry that we're done
        try {
            registry.send(new TaskComplete(selfIp, selfPort));
        } catch (IOException ignored) {
        }
    }

    private void routeMessage(String src, String dst, int payload) {
        if (src.equals(dst)) { // trivial
            receiveTracker.incrementAndGet();
            receiveSummation.addAndGet(payload);
            return;
        }

        // get or build MST for this source (weights fixed per run)
        List<MinimumSpanningTree.Edge> mst = mstCache.computeIfAbsent(src,
                s -> MinimumSpanningTree.kruskal(vertices, links));

        // adjacency for MST
        Map<String, List<String>> a = new HashMap<>();
        for (String v : vertices)
            a.put(v, new ArrayList<>());
        for (MinimumSpanningTree.Edge e : mst) {
            a.get(e.u).add(e.v);
            a.get(e.v).add(e.u);
        }

        // BFS from self to dst along MST to find next hop
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
            if (u.equals(dst))
                break;
            for (String v : a.getOrDefault(u, Collections.emptyList())) {
                if (!parent.containsKey(v)) {
                    parent.put(v, u);
                    q.addLast(v);
                }
            }
        }
        if (!parent.containsKey(dst))
            return; // (shouldn't happen in connected MST)

        // backtrack to find immediate next hop from cur toward dst
        String step = dst;
        String prev = parent.get(step);
        while (prev != null && !prev.isEmpty() && !prev.equals(cur)) {
            step = prev;
            prev = parent.get(step);
        }

        Conn c = neighbor.get(step);
        if (c == null)
            return; // connection not ready

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
            // deliver
            receiveTracker.incrementAndGet();
            receiveSummation.addAndGet(m.payload());
        } else {
            // forward along MST rooted at source
            relayTracker.incrementAndGet();

            List<MinimumSpanningTree.Edge> mst = mstCache.computeIfAbsent(m.sourceId(),
                    s -> MinimumSpanningTree.kruskal(vertices, links));

            Map<String, List<String>> a = new HashMap<>();
            for (String v : vertices)
                a.put(v, new ArrayList<>());
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
                if (u.equals(dst))
                    break;
                for (String v : a.getOrDefault(u, Collections.emptyList())) {
                    if (!parent.containsKey(v)) {
                        parent.put(v, u);
                        q.addLast(v);
                    }
                }
            }
            if (!parent.containsKey(dst))
                return;

            String step = dst;
            String prev = parent.get(step);
            while (prev != null && !prev.isEmpty() && !prev.equals(cur)) {
                step = prev;
                prev = parent.get(step);
            }

            Conn c = neighbor.get(step);
            if (c == null)
                return;

            try {
                synchronized (c.out) {
                    m.write(c.out);
                    c.out.flush();
                }
            } catch (IOException ignored) {
            }
        }
    }

    // ---------------- print-mst (BFS order from this node) ----------------

    private void printMST() {
        if (vertices.isEmpty() || links.isEmpty())
            return;

        String root = selfId();
        List<MinimumSpanningTree.Edge> mstEdges = mstCache.computeIfAbsent(root,
                s -> MinimumSpanningTree.kruskal(vertices, links));

        Map<String, List<MinimumSpanningTree.Edge>> adj = new HashMap<>();
        for (String v : vertices)
            adj.put(v, new ArrayList<>());
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
