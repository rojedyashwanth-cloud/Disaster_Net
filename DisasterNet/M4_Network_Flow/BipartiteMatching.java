import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class BipartiteMatching {

    private BipartiteMatching() {
    }

    /** A matching: the pairs themselves plus the size of the matching. */
    public record Matching(Map<String, String> pairs, int size) {
        @Override
        public String toString() {
            return "size=" + size + " " + pairs;
        }
    }

    /**
     * Hopcroft-Karp maximum matching.
     *
     * @param left  left-hand vertex names
     * @param right right-hand vertex names
     * @param edges pairs {@code [team, district]}, each meaning "can serve"
     */
    public static Matching hopcroftKarp(List<String> left, List<String> right, List<String[]> edges) {
        Map<String, Integer> leftIndex = index(left);
        Map<String, Integer> rightIndex = index(right);

        List<List<Integer>> adjacency = new ArrayList<>();
        for (int i = 0; i < left.size(); i++) {
            adjacency.add(new ArrayList<>());
        }
        for (String[] edge : edges) {
            Integer u = leftIndex.get(edge[0]);
            Integer v = rightIndex.get(edge[1]);
            if (u == null || v == null) {
                throw new IllegalArgumentException("edge refers to an unknown vertex: "
                        + edge[0] + " -> " + edge[1]);
            }
            adjacency.get(u).add(v);
        }

        int[] matchLeft = new int[left.size()];
        int[] matchRight = new int[right.size()];
        java.util.Arrays.fill(matchLeft, -1);
        java.util.Arrays.fill(matchRight, -1);
        int[] distance = new int[left.size()];
        int[] nextEdge = new int[left.size()];

        int size = 0;
        while (buildLayers(adjacency, matchLeft, matchRight, distance)) {
            java.util.Arrays.fill(nextEdge, 0);
            for (int u = 0; u < left.size(); u++) {
                if (matchLeft[u] == -1 && augment(adjacency, matchLeft, matchRight, distance,
                        nextEdge, u)) {
                    size++;
                }
            }
        }

        Map<String, String> pairs = new LinkedHashMap<>();
        for (int u = 0; u < left.size(); u++) {
            if (matchLeft[u] != -1) {
                pairs.put(left.get(u), right.get(matchLeft[u]));
            }
        }
        return new Matching(pairs, size);
    }

    /**
     * Layers the unmatched left vertices by distance to a free right vertex.
     * Returns false when no augmenting path remains, which is exactly when the
     * matching is maximum.
     */
    private static boolean buildLayers(List<List<Integer>> adjacency, int[] matchLeft,
            int[] matchRight, int[] distance) {
        java.util.Arrays.fill(distance, -1);
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        for (int u = 0; u < matchLeft.length; u++) {
            if (matchLeft[u] == -1) {
                distance[u] = 0;
                queue.add(u);
            }
        }
        boolean reachableFreeRight = false;
        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (int v : adjacency.get(u)) {
                int next = matchRight[v];
                if (next == -1) {
                    reachableFreeRight = true;
                } else if (distance[next] == -1) {
                    // Step across a matched edge to the next left vertex.
                    distance[next] = distance[u] + 1;
                    queue.add(next);
                }
            }
        }
        return reachableFreeRight;
    }

    /**
     * Follows the layered graph from one free left vertex, flipping matched and
     * unmatched edges along the way so that the path becomes an augmenting one.
     */
    private static boolean augment(List<List<Integer>> adjacency, int[] matchLeft, int[] matchRight,
            int[] distance, int[] nextEdge, int u) {
        List<Integer> neighbours = adjacency.get(u);
        while (nextEdge[u] < neighbours.size()) {
            int v = neighbours.get(nextEdge[u]++);
            int next = matchRight[v];
            if (next == -1 || (distance[next] == distance[u] + 1
                    && augment(adjacency, matchLeft, matchRight, distance, nextEdge, next))) {
                matchLeft[u] = v;
                matchRight[v] = u;
                return true;
            }
        }
        distance[u] = -1;
        return false;
    }

    /**
     * Maximum matching by reducing to maximum flow: unit capacity from the
     * source to each team, unit capacity along every "can serve" edge, and unit
     * capacity from each district to the sink.
     */
    public static Matching viaMaxFlow(List<String> left, List<String> right, List<String[]> edges) {
        FlowNetwork network = new FlowNetwork();
        network.addNode("source");
        network.addNode("sink");
        for (String team : left) {
            network.addEdge("source", "L:" + team, 1);
        }
        for (String district : right) {
            network.addEdge("R:" + district, "sink", 1);
        }
        for (String[] edge : edges) {
            network.addEdge("L:" + edge[0], "R:" + edge[1], 1);
        }
        Dinic.Result flow = Dinic.maxFlow(network, "source", "sink");

        Map<String, String> pairs = new LinkedHashMap<>();
        for (String[] edge : edges) {
            int arc = network.node("L:" + edge[0]) >= 0 ? arcOf(network, edge) : -1;
            if (arc >= 0 && network.flowOn(arc) > 0) {
                pairs.put(edge[0], edge[1]);
            }
        }
        return new Matching(pairs, flow.value());
    }

    /** Finds the forward arc for a given edge, or -1 if the edge was unknown. */
    private static int arcOf(FlowNetwork network, String[] edge) {
        int tail = network.node("L:" + edge[0]);
        for (int arc : network.arcsFrom(tail)) {
            if ((arc & 1) == 0 && network.name(network.head(arc)).equals("R:" + edge[1])) {
                return arc;
            }
        }
        return -1;
    }

    /** Distinct right-hand names reachable from a left vertex. */
    public static List<String> matchedDistricts(Matching matching) {
        return new ArrayList<>(matching.pairs().values());
    }

    private static Map<String, Integer> index(List<String> names) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            result.put(names.get(i), i);
        }
        return result;
    }
}