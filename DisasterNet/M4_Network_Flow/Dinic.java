import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Dinic maximum flow: level graph plus blocking flow - module M4.
 *
 * <p>Use case in DisasterNet: the allocation pass used for the largest networks,
 * where roads are dense enough that repeatedly rescanning the whole graph per
 * convoy starts to dominate the run time.
 *
 * <p>The idea is to stop treating each augmenting path as independent. Dinic
 * builds a <i>level graph</i> by breadth-first search, keeping only arcs that go
 * from level k to level k+1. Within that acyclic layered graph it then pushes
 * <i>many</i> paths before rebuilding anything: a single depth-first pass that
 * always returns as soon as it reaches the sink computes a blocking flow, which
 * saturates every level-0-to-level-1 arc. Only when no such path is left does it
 * recompute levels, and because the shortest path length strictly increases
 * each time, that happens only O(V) times.
 *
 * <p>Complexity: O(V^2 * E) time in general, O(E * sqrt(V)) for networks with
 * unit capacities, and O(V + E) space. The current-arc optimisation below is
 * what keeps each blocking-flow pass linear: an arc that has already been
 * exhausted in this phase is skipped instead of being retried.
 */
public final class Dinic {

    private Dinic() {
    }

    /** A maximum flow together with how many phases and pushes it took. */
    public record Result(int value, List<String> phases, long augmentations) {
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

        int n = network.nodeCount();
        int[] level = new int[n];
        int[] nextArc = new int[n];
        List<String> phases = new ArrayList<>();
        long augmentations = 0;
        int value = 0;

        while (buildLevels(network, s, t, level)) {
            Arrays.fill(nextArc, 0);
            StringBuilder pushed = new StringBuilder();
            int phaseValue = 0;
            while (true) {
                int flow = sendBlockingFlow(network, s, t, level, nextArc);
                if (flow <= 0) {
                    break;
                }
                phaseValue += flow;
                augmentations++;
                pushed.append(' ').append(flow);
            }
            value += phaseValue;
            phases.add("shortest path length " + level[t] + " delivered" + pushed);
        }
        return new Result(value, phases, augmentations);
    }

    /**
     * Labels nodes by distance from the source using arcs that still have
     * residual capacity. Returns false when the sink is not reached, which is
     * exactly the condition for the flow to be maximum.
     */
    private static boolean buildLevels(FlowNetwork network, int source, int sink, int[] level) {
        Arrays.fill(level, -1);
        Deque<Integer> queue = new ArrayDeque<>();
        level[source] = 0;
        queue.add(source);
        while (!queue.isEmpty()) {
            int node = queue.poll();
            for (int arc : network.arcsFrom(node)) {
                if (network.residualCapacity(arc) <= 0) {
                    continue;
                }
                int next = network.head(arc);
                if (level[next] >= 0) {
                    continue;
                }
                level[next] = level[node] + 1;
                queue.add(next);
            }
        }
        return level[sink] >= 0;
    }

    /**
     * One depth-first pass over the level graph, pushing until either the sink is
     * unreachable within the levels or every level-0 arc is saturated. Returns
     * the flow pushed during this call.
     */
    private static int sendBlockingFlow(FlowNetwork network, int source, int sink,
            int[] level, int[] nextArc) {
        int n = network.nodeCount();
        // Two parallel stacks: nodesAt[d] is the node reached at depth d, and
        // arcsAt[d] the arc used to get there. Keeping them apart matters,
        // because depth 0 holds the source node rather than an arc.
        int[] nodesAt = new int[n];
        int[] arcsAt = new int[n];
        int[] posAt = new int[n];
        int[] bottleneck = new int[n];

        int top = 0;
        nodesAt[0] = source;
        bottleneck[0] = Integer.MAX_VALUE;
        int pushed = 0;

        while (top >= 0) {
            int node = nodesAt[top];
            if (node == sink) {
                int amount = bottleneck[top];
                for (int d = 1; d <= top; d++) {
                    network.push(arcsAt[d], amount);
                }
                pushed += amount;
                // Restart from the source: the level graph is acyclic, so the
                // same path cannot be reused after being saturated.
                top = 0;
                nodesAt[0] = source;
                bottleneck[0] = Integer.MAX_VALUE;
                continue;
            }
            List<Integer> arcs = network.arcsFrom(node);
            boolean descended = false;
            while (nextArc[node] < arcs.size()) {
                int arc = arcs.get(nextArc[node]);
                int next = network.head(arc);
                if (network.residualCapacity(arc) > 0
                        && level[next] == level[node] + 1) {
                    top++;
                    nodesAt[top] = next;
                    arcsAt[top] = arc;
                    posAt[top] = nextArc[node];
                    bottleneck[top] = Math.min(bottleneck[top - 1], network.residualCapacity(arc));
                    descended = true;
                    break;
                }
                nextArc[node]++;
            }
            if (!descended) {
                // This node is a dead end in this phase: the level graph is
                // acyclic, so nothing reachable from it can reach the sink
                // later either. Skip the arc that led here at the parent,
                // otherwise the parent would offer the very same arc again on
                // the next visit and the search would never advance.
                top--;
                if (top >= 0) {
                    nextArc[nodesAt[top]] = posAt[top + 1] + 1;
                }
            }
        }
        return pushed;
    }
}