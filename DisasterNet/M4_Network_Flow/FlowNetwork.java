import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Residual graph shared by the maximum-flow algorithms - module M4.
 *
 * <p>Use case in DisasterNet: relief supplies sit in warehouses and have to be
 * pushed through a road network to districts that need them. Warehouses, road
 * junctions and districts are nodes; the amount a road can carry is its
 * capacity. "How much relief can be delivered at all?" is then exactly a
 * maximum-flow question, and the roads that end up carrying nothing form the
 * set of routes worth reinforcing.
 *
 * <p>Every edge is stored twice: a forward arc and its reverse arc at adjacent
 * positions, so the partner of arc {@code e} is always {@code e ^ 1}. Only
 * residual capacity is kept. Pushing {@code f} units along arc {@code e} spends
 * {@code f} of its residual capacity and hands {@code f} to the partner, which
 * is what lets an algorithm cancel a flow decision it no longer wants. That
 * reversibility is the whole point of the residual formulation: a greedy
 * forward-only walk can strand flow on a road that a different ordering would
 * have used better.
 *
 * <p>Complexity: construction and lookup are O(1) amortised; a traversal of the
 * residual graph is O(V + E).
 */
public final class FlowNetwork {

    private final List<String> names = new ArrayList<>();
    private final Map<String, Integer> ids = new LinkedHashMap<>();
    private final List<List<Integer>> outgoing = new ArrayList<>();
    private final List<Integer> arcHead = new ArrayList<>();
    private final List<Integer> arcTail = new ArrayList<>();
    private final List<Integer> residual = new ArrayList<>();
    private final List<Integer> logicalCapacity = new ArrayList<>();

    /** Builds a network with no nodes. */
    public FlowNetwork() {
    }

    /**
     * Deep copy, used when the same network has to be handed to more than one
     * algorithm: each algorithm works on the residual graph in place, so
     * sharing one instance would let the first run's flow colour the second
     * run's search.
     */
    public FlowNetwork copy() {
        FlowNetwork clone = new FlowNetwork();
        for (String name : names) {
            clone.addNode(name);
        }
        for (int arc = 0; arc < arcHead.size(); arc += 2) {
            clone.addEdge(name(tail(arc)), name(head(arc)), logicalCapacity.get(arc));
        }
        return clone;
    }

    /**
     * Adds a node, or returns the existing index if the name is already known.
     * Node order is insertion order, which keeps output stable across runs.
     */
    public int addNode(String name) {
        Integer existing = ids.get(name);
        if (existing != null) {
            return existing;
        }
        int id = names.size();
        names.add(name);
        ids.put(name, id);
        outgoing.add(new ArrayList<>());
        return id;
    }

    /** Adds both endpoints if needed and returns the index of {@code name}. */
    public int node(String name) {
        return ids.getOrDefault(name, -1);
    }

    public int nodeCount() {
        return names.size();
    }

    public String name(int node) {
        return names.get(node);
    }

    /** Node names in insertion order. */
    public List<String> nodeNames() {
        return Collections.unmodifiableList(names);
    }

    /**
     * Adds a directed edge of the given capacity and returns the index of its
     * forward arc. A repeated edge in the same direction is kept as a separate
     * edge rather than merged, because merging would quietly change what the
     * network means.
     */
    public int addEdge(String tailName, String headName, int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must not be negative: " + capacity);
        }
        int u = addNode(tailName);
        int v = addNode(headName);
        int arc = arcHead.size();
        arcTail.add(u);
        arcHead.add(v);
        residual.add(capacity);
        logicalCapacity.add(capacity);
        outgoing.get(u).add(arc);
        arcTail.add(v);
        arcHead.add(u);
        residual.add(0);
        logicalCapacity.add(0);
        outgoing.get(v).add(arc + 1);
        return arc;
    }

    /** Number of arcs, counting each edge's reverse. */
    public int arcCount() {
        return arcHead.size();
    }

    /** Number of real edges, ignoring the reverse arcs. */
    public int edgeCount() {
        return arcHead.size() / 2;
    }

    /** Arcs leaving a node, in insertion order. */
    public List<Integer> arcsFrom(int node) {
        return Collections.unmodifiableList(outgoing.get(node));
    }

    /** Node an arc points at. */
    public int head(int arc) {
        return arcHead.get(arc);
    }

    /** Node an arc leaves from. */
    public int tail(int arc) {
        return arcTail.get(arc);
    }

    /** The reverse of an arc is its partner at the adjacent index. */
    public int reverse(int arc) {
        return arc ^ 1;
    }

    /** Undirected neighbours reached by an arc with remaining capacity. */
    public int residualCapacity(int arc) {
        return residual.get(arc);
    }

    /** Capacity the edge was declared with, unaffected by flow. */
    public int declaredCapacity(int arc) {
        return logicalCapacity.get(arc);
    }

    /** Flow currently carried by the edge {@code arc} belongs to. */
    public int flowOn(int arc) {
        return residual.get(arc ^ 1);
    }

    /** Capacity still available on an edge, whichever direction it is read. */
    public int remainingOnEdge(int arc) {
        return residual.get(arc) + residual.get(arc ^ 1);
    }

    /**
     * Pushes {@code flow} units along an arc, moving capacity to its reverse.
     * Saturating an arc is how the algorithms mark a road as fully used.
     */
    public void push(int arc, int flow) {
        if (flow < 0 || flow > residual.get(arc)) {
            throw new IllegalArgumentException(
                    "cannot push " + flow + " through an arc with residual " + residual.get(arc));
        }
        residual.set(arc, residual.get(arc) - flow);
        residual.set(arc ^ 1, residual.get(arc ^ 1) + flow);
    }

    /** Removes all flow, returning every edge to its declared capacity. */
    public void resetFlow() {
        for (int arc = 0; arc < arcHead.size(); arc += 2) {
            residual.set(arc, logicalCapacity.get(arc));
            residual.set(arc + 1, 0);
        }
    }

    /** A readable edge as it currently stands, for reports. */
    public String describe(int arc) {
        return name(tail(arc)) + " -> " + name(head(arc))
                + "  capacity=" + declaredCapacity(arc)
                + "  flow=" + flowOn(arc);
    }

    @Override
    public String toString() {
        return "FlowNetwork{nodes=" + nodeCount() + ", edges=" + edgeCount() + "}";
    }
}