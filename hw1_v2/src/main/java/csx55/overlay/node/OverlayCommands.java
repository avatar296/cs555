package csx55.overlay.node;

import csx55.overlay.wireformats.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

final class OverlayCommands {
    private static List<String> nodeOrder = new ArrayList<>();
    private static final Map<String, Set<String>> adj = new HashMap<>();
    private static final List<LinkWeights.Link> weightedLinks = new ArrayList<>();

    static void setupOverlay(
            String cmd,
            Map<String, java.net.InetSocketAddress> registered,
            Map<String, DataOutputStream> outs) {
        String[] parts = cmd.split("\\s+");
        if (parts.length != 2)
            return;
        int CR = Integer.parseInt(parts[1]);
        int N = registered.size();
        if (N == 0 || CR < 0 || CR >= N) {
            System.out.println("setup completed with " + CR + " connections");
            return;
        }
        nodeOrder = new ArrayList<>(registered.keySet());
        Collections.sort(nodeOrder);
        adj.clear();
        Map<String, Integer> deg = new HashMap<>();
        Set<String> edges = new HashSet<>();
        for (String id : nodeOrder) {
            adj.put(id, new HashSet<>());
            deg.put(id, 0);
        }

        for (int i = 0; i < N; i++)
            addEdge(nodeOrder.get(i), nodeOrder.get((i + 1) % N), adj, deg, edges);

        Random rnd = new Random();
        List<String> need = new ArrayList<>();
        while (true) {
            need.clear();
            for (String id : nodeOrder)
                if (deg.get(id) < CR)
                    need.add(id);
            if (need.isEmpty())
                break;
            String a = need.get(rnd.nextInt(need.size()));
            String b = need.get(rnd.nextInt(need.size()));
            if (!a.equals(b) && !adj.get(a).contains(b))
                addEdge(a, b, adj, deg, edges);
        }
        Map<String, List<String>> dial = new HashMap<>();
        nodeOrder.forEach(id -> dial.put(id, new ArrayList<>()));
        Random r = new Random();
        for (String a : nodeOrder)
            for (String b : adj.get(a))
                if (a.compareTo(b) < 0) {
                    String dialer = r.nextBoolean() ? a : b;
                    dial.get(dialer).add(dialer.equals(a) ? b : a);
                }
        // send lists
        dial.forEach(
                (id, peers) -> {
                    DataOutputStream o = outs.get(id);
                    if (o != null)
                        try {
                            synchronized (o) {
                                new MessagingNodeList(peers).write(o);
                                o.flush();
                            }
                        } catch (IOException ignored) {
                        }
                });
        System.out.println("setup completed with " + CR + " connections");
    }

    static void sendLinkWeights(Map<String, DataOutputStream> outs) {
        if (nodeOrder.isEmpty()) {
            System.out.println("link weights assigned");
            return;
        }
        weightedLinks.clear();
        Random rnd = new Random();
        for (String a : nodeOrder)
            for (String b : adj.get(a))
                if (a.compareTo(b) < 0)
                    weightedLinks.add(new LinkWeights.Link(a, b, 1 + rnd.nextInt(10)));
        LinkWeights lw = new LinkWeights(weightedLinks);
        for (String id : nodeOrder) {
            DataOutputStream o = outs.get(id);
            if (o != null)
                try {
                    synchronized (o) {
                        lw.write(o);
                        o.flush();
                    }
                } catch (IOException ignored) {
                }
        }
        System.out.println("link weights assigned");
    }

    private static void addEdge(
            String a,
            String b,
            Map<String, Set<String>> adj,
            Map<String, Integer> deg,
            Set<String> edges) {
        String k = a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
        if (edges.contains(k))
            return;
        adj.get(a).add(b);
        adj.get(b).add(a);
        deg.put(a, deg.get(a) + 1);
        deg.put(b, deg.get(b) + 1);
        edges.add(k);
    }
}
