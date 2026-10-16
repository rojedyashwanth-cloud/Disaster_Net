import java.util.ArrayList;
import java.util.List;

/**
 * The two-times approximation for vertex cover - module M5.
 *
 * <p>Use case in DisasterNet: locking down teams and districts quickly enough to
 * act on a live incident. Exact vertex cover is NP-hard, so the question is not
     * whether the answer is optimal but whether it is <i>close</i>, and the
 * honest answer is: always within a factor of two, and no more than that.
 *
 * <p>The algorithm is a single greedy pass. Walk the edges; whenever an edge has
 * neither endpoint chosen, choose both. One pass, no optimisation, and it gives
 * a cover.
 *
 * <p>The two-times guarantee is a charging argument. Charge the cost of the
 * algorithm's output against the optimum. Every vertex the algorithm picks was
 * picked because of an edge with both endpoints unchosen at that moment. Orient
 * each such edge towards the endpoint picked first. Now:
 * <ul>
 *   <li>All charges into a vertex arrive along edges oriented towards it, and
 *       distinct charges arrive along distinct edges, because an edge is only
 *       used once - afterwards it is covered.</li>
 *   <li>For every edge of the graph, either the algorithm charged some vertex,
 *       or the edge already had a chosen endpoint when it was examined.</li>
 * </ul>
 * So each vertex is charged at most its degree, and the total charge is at most
 * the sum of degrees divided by two - every edge counted at most twice. Hence
 * |output| &lt;= 2 |OPT|.
 *
 * <p>Being explicit about the bound matters more than the algorithm: a factor of
 * two says the answer is never catastrophically wrong, which is what makes it
 * safe to act on immediately while a better computation runs.
 *
 * <p>Complexity: O(V + E) time, O(V) space.
 */
public final class TwoApproximation {

    private TwoApproximation() {
    }

    /** An approximate cover with the guarantee that can be claimed for it. */
    public record Approximation(List<Integer> vertices, int size, int optimum, double ratio) {

        /** True when the cover came within the promised factor of two. */
        public boolean withinFactorTwo() {
            return optimum == 0 || (double) size <= 2.0 * optimum;
        }

        @Override
        public String toString() {
            return "size=" + size + " optimum=" + optimum
                    + String.format(" ratio=%.2f%n", ratio);
        }
    }

    /**
     * Greedy cover in one pass, with the optimum supplied so the guarantee can
     * be reported. Passing the exact optimum in is only for measurement; the
     * algorithm itself never uses it.
     */
    public static Approximation cover(int n, List<int[]> edges, int optimum) {
        boolean[] chosen = new boolean[n];
        int size = 0;
        for (int[] edge : edges) {
            if (chosen[edge[0]] || chosen[edge[1]]) {
                continue;
            }
            chosen[edge[0]] = true;
            chosen[edge[1]] = true;
            size += 2;
        }
        List<Integer> vertices = new ArrayList<>();
        for (int v = 0; v < n; v++) {
            if (chosen[v]) {
                vertices.add(v);
            }
        }
        double ratio = optimum == 0 ? 1.0 : (double) size / optimum;
        return new Approximation(vertices, size, optimum, ratio);
    }

    /** The approximation without needing to know the optimum. */
    public static List<Integer> cover(int n, List<int[]> edges) {
        return cover(n, edges, 0).vertices();
    }

    /**
     * The greedy independent set heuristic, whose complement is also within a
     * factor of two of a minimum cover. Useful when the cover itself is not what
     * is wanted but the set of vertices that can stay open is.
     */
    public static List<Integer> openVertices(int n, List<int[]> edges) {
        return IndependentSet.greedyIndependentSet(n, edges).vertices();
    }

    /**
     * The worst case for this algorithm, exhibited rather than merely asserted:
     * a star graph. The optimum is the centre alone, but the first edge forces
     * the centre and its leaf both in, and every other edge is then already
     * covered. The answer is 2 against an optimum of 1, so the ratio is exactly
     * two however many leaves the star has - the bound is tight, not loose.
     */
    public static Approximation worstCaseStar(int leaves) {
        List<int[]> edges = new ArrayList<>();
        for (int leaf = 1; leaf <= leaves; leaf++) {
            edges.add(new int[] {0, leaf});
        }
        return cover(leaves + 1, edges, 1);
    }
}