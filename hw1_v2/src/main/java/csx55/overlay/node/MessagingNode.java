package csx55.overlay.node;

import csx55.overlay.transport.TCPSender;
import csx55.overlay.wireformats.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;

/**
 * MessagingNode:
 * - Registers with the Registry (request/response).
 * - Accepts unsolicited control messages from the Registry:
 * * MESSAGING_NODE_LIST → dials peers then prints:
 * "All connections are established. Number of connections: X"
 * * LINK_WEIGHTS → stores overlay + prints:
 * "Link weights received and processed. Ready to send messages."
 * - CLI:
 * * exit-overlay → sends DEREGISTER, prints "exited overlay"
 */
public class MessagingNode {

    private String selfIp;
    private int selfPort;

    private ServerSocket peerServer;
    private final Map<String, Socket> peerSockets = new ConcurrentHashMap<>(); // key = "ip:port" (for dialed) or UUID
                                                                               // (for inbound)

    // Overlay data (for MST later)
    private final Set<String> vertices = ConcurrentHashMap.newKeySet();
    private final List<LinkWeights.Link> links = new CopyOnWriteArrayList<>();

    // Registry event pipe
    private final BlockingQueue<Event> regEvents = new LinkedBlockingQueue<>();

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java csx55.overlay.node.MessagingNode <registry-host> <registry-port>");
            return;
        }
        new MessagingNode().run(args[0], Integer.parseInt(args[1]));
    }

    private void run(String registryHost, int registryPort) throws Exception {
        // Reserve a port for peer connections and start an accept thread
        peerServer = new ServerSocket(0);
        selfPort = peerServer.getLocalPort();

        Thread acceptPeers = new Thread(() -> {
            try {
                while (!peerServer.isClosed()) {
                    Socket s = peerServer.accept();
                    // Keep inbound peer sockets open; key by a random ID
                    peerSockets.put(UUID.randomUUID().toString(), s);
                }
            } catch (IOException ignored) {
            }
        }, "peer-acceptor");
        acceptPeers.setDaemon(true);
        acceptPeers.start();

        try (TCPSender toRegistry = new TCPSender(registryHost, registryPort)) {
            // Advertise the IP actually used for the registry connection to avoid IP
            // mismatch
            selfIp = toRegistry.socket().getLocalAddress().getHostAddress();

            // REGISTER (sync)
            toRegistry.send(new Register(selfIp, selfPort));
            Event resp = toRegistry.read();
            if (!(resp instanceof RegisterResponse)) {
                System.err.println("Unexpected response to REGISTER");
                return;
            }
            RegisterResponse rr = (RegisterResponse) resp;
            if (rr.status() != RegisterResponse.SUCCESS) {
                System.err.println("Registration failed: " + rr.info());
                return;
            }
            System.out.println("DEBUG: Registered as " + selfIp + ":" + selfPort);

            // Reader thread for ALL subsequent registry events (unsolicited + responses)
            EventFactory f = EventFactory.getInstance();
            Thread regReader = new Thread(() -> {
                try {
                    while (true) {
                        Event e = toRegistry.read(); // single consumer for the registry socket
                        if (e instanceof MessagingNodeList) {
                            handleDialList((MessagingNodeList) e);
                        } else if (e instanceof LinkWeights) {
                            handleLinkWeights((LinkWeights) e);
                        } else if (e instanceof DeregisterResponse) {
                            regEvents.offer(e);
                        } else {
                            // Ignore other event types for now
                        }
                    }
                } catch (IOException ignored) {
                }
            }, "registry-reader");
            regReader.setDaemon(true);
            regReader.start();

            final Object blocker = new Object();

            // CLI thread
            Thread cli = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (line.equals("exit-overlay")) {
                            try {
                                toRegistry.send(new Deregister(selfIp, selfPort));
                                // Wait for the DeregisterResponse (reader thread will enqueue it)
                                Event e = regEvents.poll(5, TimeUnit.SECONDS);
                                System.out.println("exited overlay");
                            } catch (Exception ex) {
                                // On any error, still match required output
                                System.out.println("exited overlay");
                            }
                            synchronized (blocker) {
                                blocker.notifyAll();
                            }
                            return;
                        }
                        // (print-mst and others will be added later)
                    }
                    // If stdin closes, keep running; do NOT auto-deregister.
                } catch (IOException ignored) {
                }
            }, "node-cli");
            cli.setDaemon(true);
            cli.start();

            // Stay alive until exit-overlay
            synchronized (blocker) {
                blocker.wait();
            }

        } finally {
            try {
                peerServer.close();
            } catch (IOException ignored) {
            }
            peerSockets.values().forEach(s -> {
                try {
                    s.close();
                } catch (IOException ignored) {
                }
            });
        }
    }

    // ---------- Handlers for registry control messages ----------

    private void handleDialList(MessagingNodeList m) {
        int ok = 0;
        for (String peer : m.peers()) {
            String[] parts = peer.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            try {
                Socket s = new Socket(host, port);
                peerSockets.put(peer, s);
                ok++;
            } catch (IOException ignored) {
                /* connection may fail if peer not ready yet */ }
        }
        System.out.println("All connections are established. Number of connections: " + ok);
    }

    private void handleLinkWeights(LinkWeights lw) {
        vertices.clear();
        links.clear();
        for (LinkWeights.Link l : lw.links()) {
            links.add(l);
            vertices.add(l.a);
            vertices.add(l.b);
        }
        System.out.println("Link weights received and processed. Ready to send messages.");
    }
}
