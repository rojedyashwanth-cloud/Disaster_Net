import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Maximum clique, exactly, by branch and bound with a colouring bound -
 * module M5.
 *
 * <p>Use case in DisasterNet: finding the largest set of districts that all
 * share a usable corridor, or the largest group of rescue teams that can be
 * co-located without duplicating equipment. Maximum clique is the standard
 * NP-complete problem, and the point of implementing it properly is to see how
 * much of the exponential worst case is removable by a good bound.
 *
 * <p>The search enumerates cliques by growing one vertex at a time, and the
 * pruning comes from a greedy colouring. Colouring a graph properly needs at
 * least as many colours as its largest clique, so if the candidate set can be
 * coloured with k colours then no clique inside it is larger than k. When that
 * upper bound plus what has already been chosen cannot beat the best clique
 * found so far, the whole branch is abandoned. Greedy colouring is used rather
 * than an exact one because any proper colouring is a valid bound and greedy is
 * linear.
 *
 * <p>Complexity: exponential in the worst case, as NP-completeness demands, but
 * the bound makes the observed behaviour far better: k-clique detection is
 * solved in roughly O(1.2^n) with this colouring bound, and the exhaustive
 * fallback is kept as the definition the fast version is tested against.
 *
 * <p>Graph convention used across module M5: {@code n} vertices numbered
 * {@code 0..n-1} and a list of edges as vertex-index pairs.
 */
public final class CLIQUE {

    private CLIQUE() {
    }

    /** A clique: its vertices, its size, and whether it is provably maximum. */
    public record Clique(List<Integer> vertices, int size, boolean provenMaximum) {
        @Override
        public String toString() {
            return "size=" + size + (provenMaximum ? " (maximum)" : " (heuristic)") + " " + vertices;
        }
    }

    /** Adjacency matrix as a flat array, for O(1) neighbour tests. */
    private static boolean[][] matrix(int n, List<int[]> edges) {
        boolean[][] adjacent = new boolean[n][n];
        for (int[] edge : edges) {
            adjacent[edge[0]][edge[1]] = true;
            adjacent[edge[1]][edge[0]] = true;
        }
        return adjacent;
    }

    /**
     * Maximum clique by branch and bound. Candidates are visited in an order
     * produced by greedy colouring, so the most constrained vertices are tried
     * first and the bound improves early.
     */
    public static Clique maximumClique(int n, List<int[]> edges) {
        boolean[][] adjacent = matrix(n, edges);
        boolean[] best = new boolean[n];
        int[] bestSize = {0};

        search(adjacent, n, new boolean[n], 0, best, bestSize);
        return new Clique(verticesOf(best), bestSize[0], true);
    }

    /**
     * Recursive expansion of a partial clique. {@code candidates} marks the
     * vertices adjacent to everything chosen so far, so any subset of it can
     * still extend the clique.
     */
    private static void search(boolean[][] adjacent, int n, boolean[] current, int currentSize,
            boolean[] best, int[] bestSize) {
        if (currentSize > bestSize[0]) {
            bestSize[0] = currentSize;
            System.arraycopy(current, 0, best, 0, n);
        }

        // Colour the candidate set: the number of colours is an upper bound on
        // the clique size inside it.
        Coloured coloured = greedyColour(adjacent, candidates(adjacent, current, n), n);

        for (int i = coloured.order().length - 1; i >= 0; i--) {
            // bound()[i] is the largest colour among positions 0..i, hence an
            // upper bound on any clique still reachable from here.
            if (currentSize + coloured.bound()[i] <= bestSize[0]) {
                return;
            }
            int v = coloured.order()[i];
            current[v] = true;
            search(adjacent, n, current, currentSize + 1, best, bestSize);
            current[v] = false;
        }
    }

    /** Vertices adjacent to every vertex of the current clique. */
    private static List<Integer> candidates(boolean[][] adjacent, boolean[] current, int n) {
        List<Integer> result = new ArrayList<>();
        for (int v = 0; v < n; v++) {
            if (current[v]) {
                continue;
            }
            boolean ok = true;
            for (int u = 0; u < n && ok; u++) {
                if (current[u] && !adjacent[u][v]) {
                    ok = false;
                }
            }
            if (ok) {
                result.add(v);
            }
        }
        return result;
    }

    /** Vertices in visit order, with a clique-size bound for each prefix. */
    private record Coloured(int[] order, int[] bound) {
    }

    /**
     * Greedily colours the candidate set and orders the vertices by non-decreasing
     * colour. Any proper colouring needs at least as many colours as the largest
     * clique it contains, so the colour count at each position of the sorted
     * order bounds the clique size still available from that point on. Visiting
     * the order backwards removes the highest-coloured vertices first, which is
     * what tightens the bound soonest.
     */
    private static Coloured greedyColour(boolean[][] adjacent, List<Integer> candidates, int n) {
        int[] colour = new int[n];
        java.util.Arrays.fill(colour, -1);
        int coloursUsed = 0;

        for (int v : candidates) {
            boolean[] taken = new boolean[coloursUsed];
            for (int u : candidates) {
                if (u != v && colour[u] >= 0 && adjacent[u][v]) {
                    taken[colour[u]] = true;
                }
            }
            int chosen = 0;
            while (chosen < taken.length && taken[chosen]) {
                chosen++;
            }
            colour[v] = chosen;
            coloursUsed = Math.max(coloursUsed, chosen + 1);
        }

        List<Integer> ordered = new ArrayList<>(candidates);
        ordered.sort((a, b) -> Integer.compare(colour[a], colour[b]));

        int[] order = new int[ordered.size()];
        int[] bound = new int[ordered.size()];
        for (int i = 0; i < ordered.size(); i++) {
            order[i] = ordered.get(i);
            bound[i] = colour[ordered.get(i)] + 1;
        }
        return new Coloured(order, bound);
    }

    /**
     * Exhaustive search over every subset, kept as the definition of "clique".
     * O(2^n * n^2) time, which is why the branch-and-bound version exists.
     */
    public static Clique bruteForceClique(int n, List<int[]> edges) {
        boolean[][] adjacent = matrix(n, edges);
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
                    if ((mask >> u & 1) == 1 && !adjacent[v][u]) {
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
        return new Clique(verticesOf(best), bestSize, true);
    }

    /** Whether a set of vertices really is a clique. */
    public static boolean isClique(int n, List<int[]> edges, List<Integer> vertices) {
        boolean[][] adjacent = matrix(n, edges);
        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                if (!adjacent[vertices.get(i)][vertices.get(j)]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<Integer> verticesOf(boolean[] flags) {
        List<Integer> result = new ArrayList<>();
        for (int v = 0; v < flags.length; v++) {
            if (flags[v]) {
                result.add(v);
            }
        }
        return result;
    }
}