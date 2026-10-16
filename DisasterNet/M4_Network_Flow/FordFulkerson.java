import java.util.ArrayList;
import java.util.List;

/**
 * Ford-Fulkerson maximum flow with a depth-first search for augmenting paths -
 * module M4.
 *
 * <p>Use case in DisasterNet: the baseline allocation pass. Convoys are routed
 * one improvement at a time, each time pushing as much as any single road
 * allows. It is the algorithm to understand first because the residual-graph
 * argument that proves it terminates is the same one the faster algorithms
 * inherit.
 *
 * <p>The loop is: while an augmenting path from source to sink exists in the
 * <i>residual</i> graph, push the bottleneck capacity along it. Each push
 * saturates at least one arc, so no arc can be re-saturated more than the total
 * capacity divided by the smallest unit of capacity; with integer capacities
 * that bounds the iteration count and the algorithm terminates. Reaching a state
 * with no augmenting path proves optimality: any larger flow would have to cross
 * the cut implied by the reachable set, and every arc of that cut is saturated.
 *
 * <p>The weakness is that the running time depends on the <i>values</i> rather
 * than the shape of the graph. Choosing augmenting paths badly can take
 * capacity-sized numbers of iterations, which is what Edmonds-Karp and Dinic
 * exist to avoid.
 *
 * <p>Complexity: O(E * F) time, where F is the maximum-flow value, and O(V + E)
 * space. Integral capacities guarantee an integral flow.
 */
public final class FordFulkerson {

    private FordFulkerson() {
    }

    /** A maximum flow together with how the search got there. */
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
            int[] parent = search(network, s, t);
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
     * Depth-first search for one augmenting path. Returns, for every node on
     * the path, the arc used to enter it, with the source marked by -1, or
     * {@code null} when the sink is unreachable.
     */
    private static int[] search(FlowNetwork network, int source, int sink) {
        int n = network.nodeCount();
        int[] parent = new int[n];
        java.util.Arrays.fill(parent, -2);
        parent[source] = -1;

        // Explicit stack rather than recursion: a long corridor network would
        // otherwise risk a stack overflow.
        int[] stack = new int[n];
        int[] nextArc = new int[n];
        int top = 0;
        stack[top] = source;
        nextArc[source] = 0;

        while (top >= 0) {
            int node = stack[top];
            if (node == sink) {
                return parent;
            }
            List<Integer> arcs = network.arcsFrom(node);
            if (nextArc[node] >= arcs.size()) {
                top--;
                continue;
            }
            int arc = arcs.get(nextArc[node]++);
            int next = network.head(arc);
            if (network.residualCapacity(arc) > 0 && parent[next] == -2) {
                parent[next] = arc;
                top++;
                stack[top] = next;
                nextArc[next] = 0;
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