/*
 * Z-algorithm (linear time string matching) - module M1.
 *
 * <p>Use case in DisasterNet: pattern matching where the pattern and the text
 * must both be available up front, e.g. scanning a report against the full
 * hazard keyword list. The Z array of {@code pattern + '#' + text} gives every
 * match position in one pass.
 *
 * <p>{@code z[i]} is the length of the longest common prefix between the whole
 * string and its suffix starting at {@code i}, so for positions inside the text
 * region, {@code z[i]} is exactly the match length at {@code i - (m+1)}.
 *
 * <p>The algorithm reuses a rightmost "Z-box" [l, r]: while inside the box we
 * already know a prefix of length {@code z[i-l]}, which avoids re-comparison.
 * This is what makes it O(n) instead of O(nm).
 *
 * <p>Complexity: O(n) time, O(n) space.
 */
import java.util.ArrayList;
import java.util.List;

public final class ZAlgorithm {

    /** Sentinel that must not occur inside {@code pattern}. */
    private static final char SENTINEL = '#';

    private ZAlgorithm() {
    }

    /** Z array of the string itself: {@code z[0] == length}. */
    public static int[] zArray(String s) {
        int n = s.length();
        int[] z = new int[n];
        if (n > 0) {
            z[0] = n;
        }
        int l = 0;
        int r = 0;
        for (int i = 1; i < n; i++) {
            if (i <= r) {
                z[i] = Math.min(r - i + 1, z[i - l]);
            }
            while (i + z[i] < n && s.charAt(z[i]) == s.charAt(i + z[i])) {
                z[i]++;
            }
            if (i + z[i] - 1 > r) {
                l = i;
                r = i + z[i] - 1;
            }
        }
        return z;
    }

    /**
     * All start positions of {@code pattern} inside {@code text}, overlapping
     * allowed. Delegates to {@link #searchAllZ} rather than scanning with
     * {@code indexOf}: a class named for the Z algorithm should contain one
     * search, and the Z form is linear in {@code n + m} without relying on the
     * pattern appearing at every position.
     */
    public static List<Integer> searchAll(String text, String pattern) {
        return searchAllZ(text, pattern);
    }

    /**
     * Z-based search. Equivalent to KMP's result but expressed through the
     * Z box, which is the useful form when the same pattern is reused across
     * many reports (the pattern's own Z box can then be cached).
     */
    public static List<Integer> searchAllZ(String text, String pattern) {
        List<Integer> hits = new ArrayList<>();
        int m = pattern.length();
        if (m == 0 || m > text.length()) {
            return hits;
        }
        String combined = pattern + SENTINEL + text;
        int[] z = zArray(combined);
        for (int i = m + 1; i < combined.length(); i++) {
            if (z[i] == m) {
                hits.add(i - m - 1);
            }
        }
        return hits;
    }

    /** First occurrence index, or -1. */
    public static int search(String text, String pattern) {
        List<Integer> hits = searchAllZ(text, pattern);
        return hits.isEmpty() ? -1 : hits.get(0);
    }

    /**
     * Longest common prefix length of {@code a} and {@code b}, computed with a
     * single Z pass over {@code a + '#' + b}.
     */
    public static int longestCommonPrefix(String a, String b) {
        int[] z = zArray(a + SENTINEL + b);
        int at = a.length() + 1;
        return at < z.length ? z[at] : 0;
    }

    /**
     * Suffix-prefix overlap length: the largest k such that a prefix of
     * {@code right} equals a suffix of {@code left}. Used to stitch streaming
     * telemetry chunks without duplicating boundary records.
     */
    public static int overlapLength(String left, String right) {
        int limit = Math.min(left.length(), right.length());
        for (int k = limit; k > 0; k--) {
            if (left.regionMatches(left.length() - k, right, 0, k)) {
                return k;
            }
        }
        return 0;
    }
}
