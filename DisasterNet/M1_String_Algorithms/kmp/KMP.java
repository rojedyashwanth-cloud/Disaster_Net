/*
 * KMP string matching - module M1.
 *
 * <p>Use case in DisasterNet: locating a fixed incident keyword such as
 * "evacuate" inside a 4 KB emergency report in O(n) time, worst case, with no
 * backtracking. The naive matcher degrades to O(nm) on repetitive field text
 * such as "water water water water level", which is exactly the pattern that
 * dominates rainfall telemetry.
 *
 * <p>The only non-trivial part is the prefix function (failure function):
 * {@code pi[i]} is the length of the longest proper prefix of pattern[0..i]
 * that is also a suffix of it. On a mismatch we jump directly to the next
 * candidate border instead of shifting the pattern one step.
 *
 * <p>Complexity: preprocessing O(m), search O(n), space O(m).
 */
import java.util.ArrayList;
import java.util.List;

public final class KMP {

    private KMP() {
    }

    /** Prefix (failure) function for {@code pattern}. */
    public static int[] prefixFunction(String pattern) {
        return prefixFunction(pattern.toCharArray());
    }

    /** Prefix (failure) function over raw characters. */
    public static int[] prefixFunction(char[] pattern) {
        int m = pattern.length;
        int[] pi = new int[m];
        for (int i = 1; i < m; i++) {
            int j = pi[i - 1];
            while (j > 0 && pattern[i] != pattern[j]) {
                j = pi[j - 1];
            }
            if (pattern[i] == pattern[j]) {
                j++;
            }
            pi[i] = j;
        }
        return pi;
    }

    /**
     * All occurrences of {@code pattern} in {@code text}, ascending and
     * possibly overlapping. An empty pattern has no occurrences.
     */
    public static List<Integer> searchAll(String text, String pattern) {
        List<Integer> hits = new ArrayList<>();
        int n = text.length();
        int m = pattern.length();
        if (m == 0 || m > n) {
            return hits;
        }
        int[] pi = prefixFunction(pattern);
        int j = 0;
        for (int i = 0; i < n; i++) {
            while (j > 0 && text.charAt(i) != pattern.charAt(j)) {
                j = pi[j - 1];
            }
            if (text.charAt(i) == pattern.charAt(j)) {
                j++;
            }
            if (j == m) {
                hits.add(i - m + 1);
                j = pi[j - 1];
            }
        }
        return hits;
    }

    /** First occurrence index, or -1. */
    public static int search(String text, String pattern) {
        List<Integer> hits = searchAll(text, pattern);
        return hits.isEmpty() ? -1 : hits.get(0);
    }

    /** Number of non-overlapping occurrences, greedy left to right. */
    public static int countMatches(String text, String pattern) {
        List<Integer> hits = searchAll(text, pattern);
        int count = 0;
        int nextAllowed = 0;
        for (int hit : hits) {
            if (hit >= nextAllowed) {
                count++;
                nextAllowed = hit + pattern.length();
            }
        }
        return count;
    }

    /**
     * The longest pattern from {@code patterns} that occurs in {@code text},
     * breaking ties by earliest start position.
     *
     * <p>Length is the primary criterion, which is what makes this useful for
     * severity keywords: in "evac then evacuate", a position-first rule would
     * return "evac" from the first clause and lose the more serious "evacuate"
     * entirely. Longest-first returns "evacuate", and only when two keywords are
     * equally long does the earlier occurrence decide.
     *
     * @return the winning pattern, or null if none of them occurs
     */
    public static String longestMatch(String text, List<String> patterns) {
        String best = null;
        int bestPos = Integer.MAX_VALUE;
        for (String p : patterns) {
            int at = search(text, p);
            if (at < 0) {
                continue;
            }
            if (best == null
                    || p.length() > best.length()
                    || (p.length() == best.length() && at < bestPos)) {
                best = p;
                bestPos = at;
            }
        }
        return best;
    }

    /** True when {@code pattern} occurs in {@code text}. */
    public static boolean contains(String text, String pattern) {
        return search(text, pattern) >= 0;
    }
}
