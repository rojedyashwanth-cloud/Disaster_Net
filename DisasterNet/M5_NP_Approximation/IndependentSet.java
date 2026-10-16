import java.util.ArrayList;
import java.util.List;

/**
 * Maximum independent set - module M5.
 *
 * <p>Use case in DisasterNet: which districts can be monitored independently,
 * meaning no two of them share a responder or a contested corridor; or which
 * relief tasks can run at the same time without competing for the same team.
 *
 * <p>The structural fact that makes this tractable to <i>express</i> is the
 * complement relationship:
 *
 * <pre>independent set  =  complement of a vertex cover</pre>
 *
 * <p>A set is independent exactly when no edge has both endpoints inside it,
 * which is the same as saying every edge has at least one endpoint outside it -
 * that outside set touches every edge, so it is a vertex cover. Taking
 * complements is exact and free, so maximum independent set and minimum vertex
 * cover always have sizes summing to n. Clique is the other half of the
 * triangle: a set is a clique in G exactly when it is an independent set in the
 * complement of G, so
 *
 * <pre>alpha(G) + theta(G) = n,   alpha(G) = omega(complement of G)</pre>
 *
 * <p>Complexity: solving it exactly is NP-hard, so this computes the complement
 * graph and calls the branch-and-bound clique solver, which brings the colouring
 * bound along with it.
 *
 * <p>Graph convention: {@code n} vertices {@code 0..n-1}, edges as index pairs.
 */
public final class IndependentSet {

    private IndependentSet() {
    }

    /** An independent set with its size and whether it is provably maximum. */
    public record Independent(List<Integer> vertices, int size, boolean provenMaximum) {
        @Override
        public String toString() {
            return "size=" + size + (provenMaximum ? " (maximum)" : " (heuristic)") + " " + vertices;
        }
    }

    /** Maximum independent set, found as a maximum clique in the complement. */
    public static Independent maximumIndependentSet(int n, List<int[]> edges) {
        List<int[]> complement = complement(n, edges);
        CLIQUE.Clique clique = CLIQUE.maximumClique(n, complement);
        return new Independent(clique.vertices(), clique.size(), true);
    }

    /** Every edge of the complement graph: all pairs that are not adjacent. */
    public static List<int[]> complement(int n, List<int[]> edges) {
        boolean[][] adjacent = new boolean[n][n];
        for (int[] edge : edges) {
            adjacent[edge[0]][edge[1]] = true;
            adjacent[edge[1]][edge[0]] = true;
        }
        List<int[]> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (!adjacent[i][j]) {
                    result.add(new int[] {i, j});
                }
            }
        }
        return result;
    }

    /** Whether a vertex set really is independent. */
    public static boolean isIndependent(int n, List<int[]> edges, List<Integer> vertices) {
        boolean[][] adjacent = new boolean[n][n];
        for (int[] edge : edges) {
            adjacent[edge[0]][edge[1]] = true;
            adjacent[edge[1]][edge[0]] = true;
        }
        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                if (adjacent[vertices.get(i)][vertices.get(j)]) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Exhaustive search over all subsets, the definition the fast path is
     * checked against. O(2^n * n^2) time, O(1) extra space.
     */
    public static Independent bruteForceIndependentSet(int n, List<int[]> edges) {
        boolean[][] adjacent = new boolean[n][n];
        for (int[] edge : edges) {
            adjacent[edge[0]][edge[1]] = true;
            adjacent[edge[1]][edge[0]] = true;
        }
        boolean[] best = new boolean[n];
        int bestSize = 0;
        for (long mask = 0; mask < (1L << n); mask++) {
            boolean ok = true;
            int size = 0;
            for (int v = 0; v < n && ok; v++) {
                if ((mask >> v & 1) == 0) {
                    continue;
                }
                size++;
                for (int u = v + 1; u < n; u++) {
                    if ((mask >> u & 1) == 1 && adjacent[v][u]) {
                        ok = false;
                        break;
                    }
                }
            }
            if (ok && size > bestSize) {
                bestSize = size;
                for (int v = 0; v < n; v++) {
                    best[v] = (mask >> v & 1) == 1;
                }
            }
        }
        List<Integer> vertices = new ArrayList<>();
        for (int v = 0; v < n; v++) {
            if (best[v]) {
                vertices.add(v);
            }
        }
        return new Independent(vertices, bestSize, true);
    }

    /**
     * Greedy independent set for large graphs: repeatedly take the vertex with
     * the fewest remaining neighbours and delete its neighbourhood. Not
     * guaranteed optimal, but linear in the number of edges per pass.
     */
    public static Independent greedyIndependentSet(int n, List<int[]> edges) {
        boolean[] removed = new boolean[n];
        boolean[] chosen = new boolean[n];
        int chosenCount = 0;

        while (true) {
            int best = -1;
            int bestDegree = Integer.MAX_VALUE;
            for (int v = 0; v < n; v++) {
                if (removed[v]) {
                    continue;
                }
                int degree = 0;
                for (int[] edge : edges) {
                    if ((edge[0] == v && !removed[edge[1]]) || (edge[1] == v && !removed[edge[0]])) {
                        degree++;
                    }
                }
                if (degree < bestDegree) {
                    bestDegree = degree;
                    best = v;
                }
            }
            if (best < 0) {
                break;
            }
            chosen[best] = true;
            chosenCount++;
            removed[best] = true;
            for (int[] edge : edges) {
                if (edge[0] == best) {
                    removed[edge[1]] = true;
                }
                if (edge[1] == best) {
                    removed[edge[0]] = true;
                }
            }
        }

        List<Integer> vertices = new ArrayList<>();
        for (int v = 0; v < n; v++) {
            if (chosen[v]) {
                vertices.add(v);
            }
        }
        return new Independent(vertices, chosenCount, false);
    }
}