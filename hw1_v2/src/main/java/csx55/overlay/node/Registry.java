package csx55.overlay.node;

import csx55.overlay.transport.TCPServerThread;
import csx55.overlay.wireformats.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Registry {
    private final Map<String, InetSocketAddress> registry = new ConcurrentHashMap<>(); // key: ip:port

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java csx55.overlay.node.Registry <port>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        new Registry().run(port);
    }

    private void run(int port) throws Exception {
        TCPServerThread server = new TCPServerThread(port, this::handleClient);
        new Thread(server, "registry-acceptor").start();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.equals("list-messaging-nodes")) {
                    listMessagingNodes();
                }
            }
        }
    }

    private void handleClient(Socket s, DataInputStream in, DataOutputStream out) throws IOException {
        EventFactory f = EventFactory.getInstance();
        while (!s.isClosed()) {
            Event ev = f.read(in);
            if (ev instanceof Register) {
                handleRegister((Register) ev, s, out);
            } else if (ev instanceof Deregister) {
                handleDeregister((Deregister) ev, s, out);
            } else {
                // Ignore others for Step 2
            }
        }
    }

    private void handleRegister(Register r, Socket s, DataOutputStream out) throws IOException {
        String remoteIp = s.getInetAddress().getHostAddress();
        boolean ipMatch = remoteIp.equals(r.ip());
        String key = r.ip() + ":" + r.port();

        if (!ipMatch) {
            new RegisterResponse(RegisterResponse.FAILURE, "IP mismatch").write(out);
            out.flush();
            return;
        }
        if (registry.containsKey(key)) {
            new RegisterResponse(RegisterResponse.FAILURE, "Already registered").write(out);
            out.flush();
            return;
        }
        registry.put(key, new InetSocketAddress(r.ip(), r.port()));
        String info = "Registration request successful. The number of messaging nodes currently constituting the overlay is ("
                + registry.size() + ")";
        new RegisterResponse(RegisterResponse.SUCCESS, info).write(out);
        out.flush();
    }

    private void handleDeregister(Deregister d, Socket s, DataOutputStream out) throws IOException {
        String remoteIp = s.getInetAddress().getHostAddress();
        boolean ipMatch = remoteIp.equals(d.ip());
        String key = d.ip() + ":" + d.port();

        if (!ipMatch) {
            new DeregisterResponse(DeregisterResponse.FAILURE, "IP mismatch").write(out);
            out.flush();
            return;
        }
        if (!registry.containsKey(key)) {
            new DeregisterResponse(DeregisterResponse.FAILURE, "Not registered").write(out);
            out.flush();
            return;
        }
        registry.remove(key);
        String info = "Deregistration successful. The number of messaging nodes currently constituting the overlay is ("
                + registry.size() + ")";
        new DeregisterResponse(DeregisterResponse.SUCCESS, info).write(out);
        out.flush();
    }

    private void listMessagingNodes() {
        for (String node : registry.keySet()) {
            System.out.println(node);
        }
    }

}