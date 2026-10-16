import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Minimum cut recovered from a maximum flow - module M4.
 *
 * <p>Use case in DisasterNet: the flow value answers "how much relief can be
 * delivered", but operations wants to know <i>where</i> the network falls
 * apart. The minimum cut names the smallest set of roads whose closure would
 * cut off the districts entirely, which is where reinforcement or a redundant
 * route is worth spending money.
 *
 * <p>The method needs no flow algorithm of its own. Take any maximum flow, then
 * walk the residual graph from the source. Let S be everything still
 * reachable. No arc leaves S with residual capacity left, so every edge crossing
 * from S to its complement is saturated; and the flow value equals the total
 * capacity of exactly those edges. So that edge set is a minimum cut. This is
 * the constructive half of the max-flow min-cut theorem: the dual certificate
 * falls out of the primal solution.
 *
 * <p>Complexity: O(V + E) time and space, one traversal of the residual graph.
 */
public final class MinimumCut {

    private MinimumCut() {
    }

    /**
     * A minimum cut: the two sides, the roads crossing between them, and the
     * capacity that closing those roads would cost.
     */
    public record Cut(List<String> sourceSide, List<String> sinkSide,
            List<String> crossingEdges, int capacity) {
        @Override
        public String toString() {
            return "capacity=" + capacity + " sourceSide=" + sourceSide
                    + " crossing=" + crossingEdges;
        }
    }

    /**
     * Reads a minimum cut off the flow currently held in the network. The
     * network must already carry a maximum flow.
     */
    public static Cut fromResidual(FlowNetwork network, String source, String sink) {
        int s = network.node(source);
        int t = network.node(sink);
        if (s < 0 || t < 0) {
            throw new IllegalArgumentException("unknown endpoint: " + source + " or " + sink);
        }

        boolean[] reachable = new boolean[network.nodeCount()];
        Deque<Integer> queue = new ArrayDeque<>();
        reachable[s] = true;
        queue.add(s);
        while (!queue.isEmpty()) {
            int node = queue.poll();
            for (int arc : network.arcsFrom(node)) {
                int next = network.head(arc);
                if (network.residualCapacity(arc) > 0 && !reachable[next]) {
                    reachable[next] = true;
                    queue.add(next);
                }
            }
        }

        List<String> sourceSide = new ArrayList<>();
        List<String> sinkSide = new ArrayList<>();
        for (int node = 0; node < network.nodeCount(); node++) {
            (reachable[node] ? sourceSide : sinkSide).add(network.name(node));
        }

        // Crossing edges are the forward arcs leaving S. A cut is directional,
        // so the reverse arcs are deliberately not counted.
        List<String> crossing = new ArrayList<>();
        int capacity = 0;
        for (int arc = 0; arc < network.arcCount(); arc += 2) {
            if (reachable[network.tail(arc)] && !reachable[network.head(arc)]) {
                crossing.add(network.name(network.tail(arc)) + " -> " + network.name(network.head(arc)));
                capacity += network.declaredCapacity(arc);
            }
        }
        return new Cut(sourceSide, sinkSide, crossing, capacity);
    }

    /**
     * Convenience wrapper: runs Dinic on a private copy of the network and then
     * reads the minimum cut back off it, so the caller never has to sequence
     * the two steps itself.
     */
    public static Cut compute(FlowNetwork template, String source, String sink) {
        FlowNetwork working = template.copy();
        Dinic.maxFlow(working, source, sink);
        return fromResidual(working, source, sink);
    }

    /** Closes the listed roads, in the same sense a real road closure would. */
    public static Set<String> asRoadClosures(Cut cut) {
        return new LinkedHashSet<>(cut.crossingEdges());
    }
}