import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Damerau-Levenshtein distance - module M3.
 *
 * <p>Use case in DisasterNet: radio operators transpose digits constantly, so
 * "FL-1000" for "FL-0100" is one transposition, not two substitutions.
 * Levenshtein charges 2 for that, which is enough to push a genuine match
 * below the acceptance threshold once other errors pile up.
 *
 * <p>Two variants are provided because textbooks disagree on which one
 * Damerau-Levenshtein means:
 * <ul>
 *   <li><b>Optimal string alignment</b> ({@link #osaDistance}): edits may not
 *       be applied to a substring that was itself just edited. Two adjacent
 *       transpositions are therefore not allowed. This is what most spell
 *       checkers use, and it is simple DP.</li>
 *   <li><b>True (unrestricted) Damerau-Levenshtein</b> ({@link #distance}):
 *       any sequence of edits is allowed, so a character may be transposed
 *       more than once. This requires the last-occurrence table below.</li>
 * </ul>
 * On the code strings used by DisasterNet the two agree; they diverge on
 * inputs such as "CA" to "ABC", where unrestricted gives 2 and OSA gives 3.
 *
 * <p>Complexity: O(nm) time and space for both.
 */
public final class DamerauLevenshtein {

    private DamerauLevenshtein() {
    }

    /**
     * Unrestricted Damerau-Levenshtein distance (Lowrance and Wagner).
     *
     * <p>The array is indexed from 1 with a sentinel border of {@code inf}, so
     * that the transposition term can address {@code h[i1][j1]} even when no
     * earlier match exists: those cells then hold {@code inf} and the term is
     * correctly ignored. {@code da} records the most recent row in which each
     * character of the source occurred, and {@code db} the most recent column
     * in which the current source character matched, which together identify
     * the block that a transposition would have to cross.
     *
     * <p>Complexity: O(nm) time and space.
     */
    public static int distance(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }

        int inf = n + m;
        int[][] h = new int[n + 2][m + 2];
        h[0][0] = inf;
        // h[i][j] holds the reference table's d[i-1][j-1], so the origin lands
        // at h[1][1] = 0 and the first row and column start one below it. The
        // sentinel column and row must stay at inf all the way to index 1:
        // writing h[1][0] would make the transposition term go negative.
        for (int i = 0; i <= n + 1; i++) {
            h[i][0] = inf;
        }
        for (int i = 1; i <= n + 1; i++) {
            h[i][1] = i - 1;
        }
        for (int j = 0; j <= m + 1; j++) {
            h[0][j] = inf;
        }
        for (int j = 1; j <= m + 1; j++) {
            h[1][j] = j - 1;
        }

        Map<Character, Integer> da = new HashMap<>();
        for (int i = 1; i <= n; i++) {
            char source = a.charAt(i - 1);
            int db = 0;
            for (int j = 1; j <= m; j++) {
                char target = b.charAt(j - 1);
                int i1 = da.getOrDefault(target, 0);
                int j1 = db;
                int cost = 1;
                if (source == target) {
                    cost = 0;
                    db = j;
                }
                h[i + 1][j + 1] = Math.min(
                        Math.min(h[i][j] + cost, h[i + 1][j] + 1),
                        Math.min(h[i][j + 1] + 1,
                                h[i1][j1] + (i - i1 - 1) + cost + (j - j1 - 1)));
            }
            da.put(source, i);
        }
        return h[n + 1][m + 1];
    }

    /** Optimal string alignment distance: no substring may be edited twice. */
    public static int osaDistance(String a, String b) {
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
                int best = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1);
                best = Math.min(best, d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1
                        && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    // Transpose the last two characters for one edit.
                    best = Math.min(best, d[i - 2][j - 2] + 1);
                }
                d[i][j] = best;
            }
        }
        return d[n][m];
    }

    /**
     * Nearest registered code for a mistyped one, or null if nothing is within
     * {@code threshold}. This is the code-registry lookup the incident desk
     * performs on every inbound transmission.
     */
    public static String nearest(String typo, List<String> registry, int threshold) {
        String best = null;
        int bestDistance = threshold + 1;
        for (String candidate : registry) {
            int d = distance(typo, candidate);
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        return best;
    }

    /** Every registry entry within {@code threshold}, nearest first. */
    public static List<String> nearestAll(String typo, List<String> registry, int threshold) {
        List<String[]> scored = new ArrayList<>();
        for (String candidate : registry) {
            int d = distance(typo, candidate);
            if (d <= threshold) {
                scored.add(new String[]{candidate, String.valueOf(d)});
            }
        }
        scored.sort((x, y) -> {
            int dx = Integer.parseInt(x[1]);
            int dy = Integer.parseInt(y[1]);
            return dx != dy ? Integer.compare(dx, dy) : x[0].compareTo(y[0]);
        });
        List<String> out = new ArrayList<>();
        for (String[] pair : scored) {
            out.add(pair[0]);
        }
        return out;
    }
}
