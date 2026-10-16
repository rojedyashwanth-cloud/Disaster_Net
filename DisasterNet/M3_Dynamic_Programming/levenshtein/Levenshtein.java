import java.util.ArrayList;
import java.util.List;

public final class Levenshtein {


    public record Op(char from, char to, String kind) {
        @Override
        public String toString() {
            return switch (kind) {
                case "match" -> "=" + from;
                case "substitute" -> from + "->" + to;
                case "delete" -> "-" + from;
                default -> "+" + to;
            };
        }
    }

    private Levenshtein() {
    }

    public static int distance(String a, String b) {
        return distance(a.toCharArray(), b.toCharArray());
    }

    public static int distance(char[] a, char[] b) {
        int n = a.length;
        int m = b.length;
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }
        int[] previous = new int[m + 1];
        int[] current = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            current[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a[i - 1] == b[j - 1] ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[m];
    }

    /** Full DP table, exposed for inspection and for the traceback. */
    public static int[][] table(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                        d[i - 1][j - 1] + cost);
            }
        }
        return d;
    }

    /**
     * An optimal alignment, as a list of operations turning {@code a} into
     * {@code b}. Walks the table backwards from the bottom-right corner,
     * preferring substitution over insertion or deletion on ties.
     */
    public static List<Op> alignment(String a, String b) {
        int[][] d = table(a, b);
        List<Op> ops = new ArrayList<>();
        int i = a.length();
        int j = b.length();
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                if (d[i][j] == d[i - 1][j - 1] + cost) {
                    ops.add(new Op(a.charAt(i - 1), b.charAt(j - 1),
                            cost == 0 ? "match" : "substitute"));
                    i--;
                    j--;
                    continue;
                }
            }
            if (i > 0 && d[i][j] == d[i - 1][j] + 1) {
                ops.add(new Op(a.charAt(i - 1), '\0', "delete"));
                i--;
                continue;
            }
            ops.add(new Op('\0', b.charAt(j - 1), "insert"));
            j--;
        }
        java.util.Collections.reverse(ops);
        return ops;
    }

    /** The corrected string produced by an optimal alignment of a onto b. */
    public static String corrected(String a, String b) {
        StringBuilder out = new StringBuilder();
        for (Op op : alignment(a, b)) {
            if (!op.kind().equals("delete")) {
                out.append(op.to());
            }
        }
        return out.toString();
    }

    /** Similarity in [0,1]: {@code 1 - distance / maxLength}. */
    public static double similarity(String a, String b) {
        int max = Math.max(a.length(), b.length());
        if (max == 0) {
            return 1.0;
        }
        return 1.0 - (double) distance(a, b) / max;
    }

    /** True when {@code a} is within {@code threshold} edits of {@code b}. */
    public static boolean isWithin(String a, String b, int threshold) {
        return distance(a, b) <= threshold;
    }

    /**
     * Bounded distance: stops early once every cell in a row exceeds the
     * threshold. Used when scanning a large registry for near matches, where
     * most candidates are hopeless and the full table is wasted work.
     *
     * @return the distance, or {@code threshold + 1} if it exceeds it
     */
    public static int distanceWithin(String a, String b, int threshold) {
        if (Math.abs(a.length() - b.length()) > threshold) {
            return threshold + 1;
        }
        int n = a.length();
        int m = b.length();
        int[] previous = new int[m + 1];
        int[] current = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            current[0] = i;
            int rowMin = i;                     // d[i][0] counts as a row entry
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
                rowMin = Math.min(rowMin, current[j]);
            }
            // Every optimal alignment to (n,m) crosses this row at some cell
            // whose cost is at most the total, so an all-above-threshold row
            // proves the distance exceeds the threshold.
            if (rowMin > threshold) {
                return threshold + 1;
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[m];
    }
}
