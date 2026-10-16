import java.util.ArrayList;
import java.util.List;

/**
 * Vertex cover: exact optimum and the greedy approximation - module M5.
 *
 * <p>Use case in DisasterNet: which teams and districts must be locked down so
 * that no remaining pairing is workable. A cover is the complement of an
 * independent set, so an exact answer comes from the independent-set solver;
 * for graphs too large to search exactly, the greedy cover is the practical
 * fallback and it comes with a guarantee.
 *
 * <p>Two results matter here.
 * <ul>
 *   <li><b>Complementarity.</b> A set is a vertex cover exactly when its
 *       complement is independent, so the optimum sizes satisfy
 *       {@code tau(G) + alpha(G) = n}. The exact solver uses this.</li>
 *   <li><b>Bipartite special case.</b> In a bipartite graph Koenig's theorem
 *       gives an exact linear-time answer, {@code tau = matching size}; see
 *       {@code KonigTheorem} in module M4.</li>
 * </ul>
 *
 * <p>Complexity: exact cover is NP-hard and inherits the cost of the
 * independent-set search. The greedy cover is O(V + E) for the pass and gets
 * worse with repeated passes, since each pass re-scans every edge.
 */
public final class VertexCover {

    private VertexCover() {
    }

    /** A vertex cover: its vertices, its size, and how it was obtained. */
    public record Cover(List<Integer> vertices, int size, boolean optimal, String method) {
        @Override
        public String toString() {
            return "size=" + size + (optimal ? " (optimal)" : " (approximate)") + " " + vertices;
        }
    }

    /**
     * Minimum vertex cover, computed exactly as the complement of a maximum
     * independent set.
     */
    public static Cover minimumVertexCover(int n, List<int[]> edges) {
        IndependentSet.Independent independent =
                IndependentSet.maximumIndependentSet(n, edges);
        List<Integer> cover = new ArrayList<>();
        boolean[] inside = new boolean[n];
        for (int v : independent.vertices()) {
            inside[v] = true;
        }
        for (int v = 0; v < n; v++) {
            if (!inside[v]) {
                cover.add(v);
            }
        }
        return new Cover(cover, cover.size(), true, "complement of maximum independent set");
    }

    /**
     * Greedy vertex cover: take both endpoints of any uncovered edge until every
     * edge is covered. Choosing both ends at once is what makes the two-times
     * guarantee work - see {@link TwoApproximation} for the argument.
     *
     * <p>The chosen flag is updated as the pass proceeds, which is what keeps the
     * count honest: a vertex already taken is not counted again when a later
     * edge selects it too.
     */
    public static Cover greedyVertexCover(int n, List<int[]> edges) {
        boolean[] taken = new boolean[n];
        int size = 0;
        for (int[] edge : edges) {
            if (taken[edge[0]] || taken[edge[1]]) {
                continue;
            }
            if (!taken[edge[0]]) {
                taken[edge[0]] = true;
                size++;
            }
            if (!taken[edge[1]]) {
                taken[edge[1]] = true;
                size++;
            }
        }
        List<Integer> vertices = new ArrayList<>();
        for (int v = 0; v < n; v++) {
            if (taken[v]) {
                vertices.add(v);
            }
        }
        return new Cover(vertices, size, false, "greedy, both endpoints of each uncovered edge");
    }

    /**
     * Whether a vertex set really covers every edge.
     *
     * <p>The membership array is sized from the largest vertex mentioned by
     * either the edges or the candidate set, not from the candidate set alone.
     * Sizing from the candidate set alone would silently skip edges whose
     * endpoints both lie beyond it, reporting an incomplete cover as valid.
     */
    public static boolean covers(List<int[]> edges, List<Integer> vertices) {
        int highest = -1;
        for (int v : vertices) {
            highest = Math.max(highest, v);
        }
        for (int[] edge : edges) {
            highest = Math.max(highest, Math.max(edge[0], edge[1]));
        }
        if (highest < 0) {
            return edges.isEmpty();
        }
        boolean[] inside = new boolean[highest + 1];
        for (int v : vertices) {
            if (v >= 0 && v < inside.length) {
                inside[v] = true;
            }
        }
        for (int[] edge : edges) {
            if (edge[0] >= 0 && edge[1] >= 0 && edge[0] < inside.length && edge[1] < inside.length
                    && !inside[edge[0]] && !inside[edge[1]]) {
                return false;
            }
        }
        return true;
    }
}