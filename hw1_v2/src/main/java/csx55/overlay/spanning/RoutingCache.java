package csx55.overlay.spanning;

import java.util.*;

public final class RoutingCache {
    private String lastVersionKey = null;
    private Map<String, List<MinimumSpanningTree.Edge>> cacheByRoot = new HashMap<>();

    public void invalidate(String versionKey) {
        if (!Objects.equals(versionKey, lastVersionKey)) {
            cacheByRoot.clear();
            lastVersionKey = versionKey;
        }
    }

    public List<MinimumSpanningTree.Edge> get(String root) {
        return cacheByRoot.get(root);
    }

    public void put(String root, List<MinimumSpanningTree.Edge> edges) {
        cacheByRoot.put(root, edges);
    }
}
