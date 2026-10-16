/**
 * Matrix-chain multiplication order - module M3.
 *
 * <p>Use case in DisasterNet: composing the district similarity matrices that
 * the M5 stage builds. Merging the hazard profile of district A with B and then
 * with C costs {@code (a*b*c)}, while merging A with (B*C) first costs
 * {@code (a*b*c)} too, but the naive left-to-right order {@code ((AB)C)} costs
 * {@code (a*b + a)*c}. With 12 districts the naive order does thousands of
 * scalar multiplications; the optimal order does a fraction of that. The
 * answer is not "left to right" in general, which is exactly why DP is needed.
 *
 * <p>Recurrence: let {@code m[i][j]} be the fewest scalar multiplications to
 * compute the product of matrices {@code A_i .. A_j}. The final multiplication
 * must split the chain at some {@code k}, so
 * <pre>
 *   m[i][j] = min over i &lt;= k &lt; j of  m[i][k] + m[k+1][j] + p[i-1]*p[k]*p[j]
 * </pre>
 * where {@code A_t} is {@code p[t-1] x p[t]}. The cost term is the scalar count
 * of the single multiplication that joins the two sub-products.
 *
 * <p>Complexity: O(n^3) time, O(n^2) space, against O(n!) parenthesisations.
 */
public final class MatrixChainMultiplication {

    /** A dense square or rectangular matrix of longs. */
    public static final class Mat {
        public final int rows;
        public final int cols;
        public final long[][] cells;

        public Mat(int rows, int cols) {
            this.rows = rows;
            this.cols = cols;
            this.cells = new long[rows][cols];
        }

        public static Mat identity(int n) {
            Mat m = new Mat(n, n);
            for (int i = 0; i < n; i++) {
                m.cells[i][i] = 1;
            }
            return m;
        }
    }

    /** Optimal parenthesisation of a chain with the given dimensions. */
    public record Result(int cost, int[] split, String order) {
    }

    private MatrixChainMultiplication() {
    }

    /** Optimal order for the chain {@code A_0 .. A_{n-1}} of dimensions {@code p}. */
    public static Result optimize(int[] p) {
        int n = p.length - 1;
        if (n < 1) {
            return new Result(0, new int[0], "");
        }
        int[][] cost = new int[n][n];
        int[][] split = new int[n][n];

        // cost[i][j] is infinite until filled; single matrices cost nothing.
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                cost[i][j] = Integer.MAX_VALUE;
            }
            cost[i][i] = 0;
        }

        for (int length = 2; length <= n; length++) {
            for (int i = 0; i + length - 1 < n; i++) {
                int j = i + length - 1;
                for (int k = i; k < j; k++) {
                    long candidate = (long) cost[i][k] + cost[k + 1][j]
                            + (long) p[i] * p[k + 1] * p[j + 1];
                    if (candidate < cost[i][j]) {
                        cost[i][j] = (int) candidate;
                        split[i][j] = k;
                    }
                }
            }
        }

        StringBuilder order = new StringBuilder();
        buildOrder(order, split, 0, n - 1, n);
        return new Result(cost[0][n - 1], split[0], order.toString());
    }

    private static void buildOrder(StringBuilder out, int[][] split, int i, int j, int n) {
        if (i == j) {
            if (n == 1) {
                out.append('A');
            } else {
                out.append((char) ('A' + i));
            }
            return;
        }
        int k = split[i][j];
        out.append('(');
        buildOrder(out, split, i, k, n);
        out.append('x');
        buildOrder(out, split, k + 1, j, n);
        out.append(')');
    }

    /** Cost of always multiplying left to right: {@code (((A0A1)A2)A3)...}. */
    public static long leftToRightCost(int[] p) {
        long total = 0;
        for (int j = 1; j < p.length - 1; j++) {
            total += (long) p[0] * p[j] * p[j + 1];
        }
        return total;
    }

    /** Cost of always multiplying right to left. */
    public static long rightToLeftCost(int[] p) {
        long total = 0;
        int n = p.length - 1;
        for (int i = n - 2; i >= 0; i--) {
            total += (long) p[i] * p[i + 1] * p[n];
        }
        return total;
    }
}
