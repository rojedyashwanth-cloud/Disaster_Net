import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Bitmask dynamic programming - module M3.
 *
 * <p>Use case in DisasterNet: two problems where the state must remember
 * <i>which</i> items have already been handled.
 * <ul>
 *   <li><b>Team routing.</b> A relief convoy must visit a chosen set of
 *       districts and return to base. Which districts remain matters, and a
 *       bitmask of districts says it exactly. This is Held-Karp TSP: O(n^2 2^n)
 *       instead of O(n!) for brute-force enumeration.</li>
 *   <li><b>Load partitioning.</b> Splitting a relief manifest into vehicle
 *       loads so each stays within capacity, again tracked by a subset mask.</li>
 * </ul>
 *
 * <p>The central idea is that a state is a subset of items, so subsets can be
 * held in a single integer, and transitions only ever add one item. Holding the
 * best cost per (subset, last item) is valid because the future cost depends
 * only on where you are and what is left, never on the order taken to get
 * there - the optimal substructure that makes memoisation work.
 *
 * <p>Complexity: TSP O(n^2 2^n) time and O(n 2^n) space, which is practical
 * for roughly n &lt;= 15 districts. Subset-sum is O(n 2^n) time, O(2^n) space.
 */
public final class BitmaskDP {

    private BitmaskDP() {
    }

    /** An optimal tour: total cost plus the visiting order. */
    public record Tour(int cost, List<Integer> order) {
        @Override
        public String toString() {
            return "cost=" + cost + " order=" + order;
        }
    }

    /**
     * Shortest closed tour visiting every node, starting and ending at 0.
     *
     * <p>{@code dp[mask][v]} is the cheapest way to have visited exactly the
     * nodes in {@code mask} and to be at {@code v}. The answer is
     * {@code dp[full][v] + dist(v, 0)} minimised over v, since the convoy has
     * to get home.
     */
    public static Tour shortestTour(double[][] dist) {
        int n = dist.length;
        if (n == 0) {
            return new Tour(0, List.of());
        }
        if (n == 1) {
            return new Tour(0, List.of(0));
        }

        int states = 1 << n;
        double[][] dp = new double[states][n];
        for (double[] row : dp) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        int[][] parent = new int[states][n];
        for (int[] row : parent) {
            Arrays.fill(row, -1);
        }

        dp[1][0] = 0;
        for (int mask = 1; mask < states; mask++) {
            for (int last = 0; last < n; last++) {
                double current = dp[mask][last];
                if (current == Double.POSITIVE_INFINITY || (mask & (1 << last)) == 0) {
                    continue;
                }
                for (int next = 0; next < n; next++) {
                    if ((mask & (1 << next)) != 0) {
                        continue;
                    }
                    int nextMask = mask | (1 << next);
                    double candidate = current + dist[last][next];
                    if (candidate < dp[nextMask][next]) {
                        dp[nextMask][next] = candidate;
                        parent[nextMask][next] = last;
                    }
                }
            }
        }

        int full = states - 1;
        int bestLast = 0;
        double best = Double.POSITIVE_INFINITY;
        for (int last = 1; last < n; last++) {
            double candidate = dp[full][last] + dist[last][0];
            if (candidate < best) {
                best = candidate;
                bestLast = last;
            }
        }

        List<Integer> order = new ArrayList<>();
        int mask = full;
        int node = bestLast;
        while (node != -1) {
            order.add(node);
            int previous = parent[mask][node];
            mask &= ~(1 << node);
            node = previous;
        }
        java.util.Collections.reverse(order);
        return new Tour((int) Math.round(best), order);
    }

    /**
     * Shortest open path visiting every node, starting at 0. Used when the
     * convoy finishes at the district that is most urgent rather than
     * returning to base.
     */
    public static Tour shortestPath(double[][] dist) {
        int n = dist.length;
        int states = 1 << n;
        double[][] dp = new double[states][n];
        for (double[] row : dp) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        dp[1][0] = 0;
        for (int mask = 1; mask < states; mask++) {
            for (int last = 0; last < n; last++) {
                double current = dp[mask][last];
                if (current == Double.POSITIVE_INFINITY || (mask & (1 << last)) == 0) {
                    continue;
                }
                for (int next = 0; next < n; next++) {
                    if ((mask & (1 << next)) == 0) {
                        int nextMask = mask | (1 << next);
                        dp[nextMask][next] = Math.min(dp[nextMask][next], current + dist[last][next]);
                    }
                }
            }
        }
        int full = states - 1;
        int bestLast = 0;
        double best = Double.POSITIVE_INFINITY;
        for (int last = 0; last < n; last++) {
            if (dp[full][last] < best) {
                best = dp[full][last];
                bestLast = last;
            }
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        return new Tour((int) Math.round(best), order);
    }

    /**
     * True when the items can be split into two groups of equal total weight.
     * Walks the reachable-subset DP: {@code reachable[mask]} is true when
     * {@code mask} can be formed, and adding one item extends the frontier.
     */
    public static boolean isBalancedPartition(int[] weights) {
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        if ((total & 1) == 1) {
            return false;
        }
        int target = total / 2;
        boolean[] reachable = new boolean[1 << weights.length];
        reachable[0] = true;
        for (int mask = 0; mask < reachable.length; mask++) {
            if (!reachable[mask]) {
                continue;
            }
            for (int i = 0; i < weights.length; i++) {
                if ((mask & (1 << i)) == 0 && sumOf(weights, mask) + weights[i] <= target) {
                    reachable[mask | (1 << i)] = true;
                }
            }
        }
        for (int mask = 0; mask < reachable.length; mask++) {
            if (reachable[mask] && sumOf(weights, mask) == target) {
                return true;
            }
        }
        return false;
    }

    /**
     * Minimum number of bins of capacity {@code capacity} holding every item
     * (bin packing, NP-hard in general; here solved exactly over masks, so it
     * is only usable for small manifests).
     */
    public static int minimumBins(int[] weights, int capacity) {
        int n = weights.length;
        int states = 1 << n;
        // dp[mask] = (bins used, load in the current bin), minimised bin-major.
        int[] bins = new int[states];
        int[] load = new int[states];
        Arrays.fill(bins, Integer.MAX_VALUE);
        bins[0] = 1;
        load[0] = 0;

        for (int mask = 0; mask < states; mask++) {
            if (bins[mask] == Integer.MAX_VALUE) {
                continue;
            }
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    continue;
                }
                int nextMask = mask | (1 << i);
                int newLoad;
                int newBins;
                if (load[mask] + weights[i] <= capacity) {
                    newLoad = load[mask] + weights[i];
                    newBins = bins[mask];
                } else {
                    newLoad = weights[i];
                    newBins = bins[mask] + 1;
                }
                if (newBins < bins[nextMask]
                        || (newBins == bins[nextMask] && newLoad < load[nextMask])) {
                    bins[nextMask] = newBins;
                    load[nextMask] = newLoad;
                }
            }
        }
        return bins[states - 1];
    }

    private static int sumOf(int[] weights, int mask) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            if ((mask & (1 << i)) != 0) {
                sum += weights[i];
            }
        }
        return sum;
    }

    /** Bitmask of the nodes in {@code tour}, useful for reporting coverage. */
    public static int maskOf(List<Integer> nodes) {
        int mask = 0;
        for (int node : nodes) {
            mask |= 1 << node;
        }
        return mask;
    }
}
