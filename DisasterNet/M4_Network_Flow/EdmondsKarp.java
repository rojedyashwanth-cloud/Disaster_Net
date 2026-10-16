import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Edmonds-Karp maximum flow: breadth-first augmenting paths - module M4.
 *
 * <p>Use case in DisasterNet: the improvement over naive routing. Choosing the
 * path with the fewest roads rather than the first one found means convoys are
 * not sent down long detours just because the depth-first walk happened to look
 * there first.
 *
 * <p>The algorithm is Ford-Fulkerson with one change: augmenting paths are found
 * by breadth-first search, so each one uses the fewest possible arcs. That makes
 * the iteration count depend only on the shape of the network. The classical
 * bound is O(V * E^2): once a shortest path has been augmented, the length of
 * the next shortest path strictly increases, so the shortest path can grow at
 * most V times, and each breadth-first pass costs O(E).
 *
 * <p>Complexity: O(V * E^2) time, O(V + E) space.
 */
public final class EdmondsKarp {

    private EdmondsKarp() {
    }

    /** A maximum flow together with a description of the augmentations. */
    public record Result(int value, List<String> augmentedPaths, long augmentations) {
        @Override
        public String toString() {
            return "value=" + value + " augmentations=" + augmentations;
        }
    }

    /**
     * Computes a maximum flow from {@code source} to {@code sink}, leaving the
     * flow in the network's residual graph.
     */
    public static Result maxFlow(FlowNetwork network, String source, String sink) {
        int s = network.node(source);
        int t = network.node(sink);
        if (s < 0 || t < 0) {
            throw new IllegalArgumentException("unknown endpoint: " + source + " or " + sink);
        }
        network.resetFlow();

        List<String> paths = new ArrayList<>();
        long augmentations = 0;
        int value = 0;

        while (true) {
            int[] parent = shortestPath(network, s, t);
            if (parent == null) {
                break;
            }
            List<Integer> path = walkBack(network, parent, s, t);
            int bottleneck = Integer.MAX_VALUE;
            for (int arc : path) {
                bottleneck = Math.min(bottleneck, network.residualCapacity(arc));
            }
            List<String> trail = new ArrayList<>();
            for (int arc : path) {
                network.push(arc, bottleneck);
                trail.add(network.describe(arc));
            }
            paths.add("push " + bottleneck + " via " + String.join(" then ", trail));
            value += bottleneck;
            augmentations++;
        }
        return new Result(value, paths, augmentations);
    }

    /**
     * Breadth-first search for a shortest augmenting path. Returns the arc used
     * to enter each node on the path, the source marked -1, or {@code null} if
     * the sink cannot be reached.
     */
    private static int[] shortestPath(FlowNetwork network, int source, int sink) {
        int n = network.nodeCount();
        int[] parent = new int[n];
        java.util.Arrays.fill(parent, -2);
        parent[source] = -1;

        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(source);
        while (!queue.isEmpty()) {
            int node = queue.poll();
            for (int arc : network.arcsFrom(node)) {
                if (network.residualCapacity(arc) <= 0) {
                    continue;
                }
                int next = network.head(arc);
                if (parent[next] != -2) {
                    continue;
                }
                parent[next] = arc;
                if (next == sink) {
                    return parent;
                }
                queue.add(next);
            }
        }
        return null;
    }

    /**
     * Turns a parent array (entry per node, holding the arc used to enter it)
     * into the arcs of the path, in the order they are travelled.
     */
    private static List<Integer> walkBack(FlowNetwork network, int[] parent, int source, int sink) {
        List<Integer> path = new ArrayList<>();
        int node = sink;
        while (node != source) {
            int arc = parent[node];
            path.add(arc);
            node = network.tail(arc);
        }
        java.util.Collections.reverse(path);
        return path;
    }
}