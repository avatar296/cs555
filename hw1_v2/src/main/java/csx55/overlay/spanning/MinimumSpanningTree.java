package csx55.overlay.spanning;

import java.util.*;

public final class MinimumSpanningTree {

    public static final class Edge {
        public final String u, v;
        public final int w;

        public Edge(String u, String v, int w) {
            this.u = u;
            this.v = v;
            this.w = w;
        }

        public String other(String x) {
            return x.equals(u) ? v : u;
        }
    }

    private static final class DSU {
        private final Map<String, String> parent = new HashMap<>();
        private final Map<String, Integer> rank = new HashMap<>();

        void makeSet(Collection<String> verts) {
            for (String v : verts) {
                parent.put(v, v);
                rank.put(v, 0);
            }
        }

        String find(String x) {
            String p = parent.get(x);
            if (!p.equals(x))
                parent.put(x, p = find(p));
            return p;
        }

        boolean union(String a, String b) {
            String ra = find(a), rb = find(b);
            if (ra.equals(rb))
                return false;
            int rka = rank.get(ra), rkb = rank.get(rb);
            if (rka < rkb)
                parent.put(ra, rb);
            else if (rka > rkb)
                parent.put(rb, ra);
            else {
                parent.put(rb, ra);
                rank.put(ra, rka + 1);
            }
            return true;
        }
    }

    public static List<Edge> kruskal(Collection<String> vertices, Collection<Edge> edges) {
        List<Edge> sorted = new ArrayList<>(edges);
        sorted.sort(
                (a, b) -> {
                    if (a.w != b.w)
                        return Integer.compare(a.w, b.w);
                    int cu = a.u.compareTo(b.u);
                    if (cu != 0)
                        return cu;
                    return a.v.compareTo(b.v);
                });

        DSU dsu = new DSU();
        dsu.makeSet(vertices);

        List<Edge> mst = new ArrayList<>();
        for (Edge e : sorted) {
            if (dsu.union(e.u, e.v)) {
                mst.add(e);
                if (mst.size() == vertices.size() - 1)
                    break;
            }
        }
        return mst;

    }

    private MinimumSpanningTree() {
    }
}
