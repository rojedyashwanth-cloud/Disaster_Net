import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Kasai's algorithm - module M2.
 *
 * <p>Use case in DisasterNet: Kasai supplies the LCP array in linear time, so
 * the report index can answer "which phrase repeats across incidents and how
 * many times" without the quadratic blow-up of comparing suffix pairs.
 *
 * <p>The key observation that makes it O(n): scan suffixes in the order given
 * by the suffix array, not in text order. Let {@code h} be the LCP of the
 * previous suffix with its predecessor. The next suffix in text order is
 * obtained by dropping the first character, which keeps at least {@code h - 1}
 * characters of agreement. So we start each comparison at {@code h - 1}
 * instead of 0 and only ever extend it, keeping the total work linear.
 *
 * <p>Formally the sequence of LCP values, read in text order rather than SA
 * order, never drops by more than one between consecutive entries, which caps
 * the total number of character comparisons at 2n.
 *
 * <p>Complexity: O(n) time, O(n) space. Output: {@code lcp[0] = 0},
 * {@code lcp[i] = lcp(text[sa[i-1]..], text[sa[i]..])}.
 */
public final class Kasai {

    private Kasai() {
    }

    /**
     * LCP array of {@code text} given its suffix array {@code sa}.
     *
     * @return array of length n with {@code result[0] == 0}
     */
    public static int[] compute(String text, int[] sa) {
        int n = text.length();
        int[] lcp = new int[n];
        if (n == 0) {
            return lcp;
        }

        // inverse suffix array: where[p] = rank of the suffix starting at p
        int[] where = new int[n];
        for (int i = 0; i < n; i++) {
            where[sa[i]] = i;
        }

        int h = 0;
        for (int p = 0; p < n; p++) {
            int rank = where[p];
            if (rank == 0) {
                // Smallest suffix has no predecessor; reset and move on.
                h = 0;
                continue;
            }
            int q = sa[rank - 1];
            while (p + h < n && q + h < n && text.charAt(p + h) == text.charAt(q + h)) {
                h++;
            }
            lcp[rank] = h;
            if (h > 0) {
                h--;
            }
        }
        return lcp;
    }

    /** Convenience: suffix array plus LCP in one call. */
    public static List<int[]> buildBoth(String text) {
        int[] sa = SuffixArray.build(text).sa();
        return List.of(sa, compute(text, sa));
    }

    /**
     * Every repeated substring that occurs at least twice, ranked by length.
     *
     * <p>Built by scanning maximal runs of equal LCP values with a monotone
     * stack. A run of value v covering LCP indices [p, q] means suffixes
     * sa[p-1] .. sa[q] all begin with the same v characters, hence that
     * substring occurs q - p + 2 times. The scan closes with a sentinel 0 at
     * index n, which flushes every open run.
     */
    public static List<LCPArray.Repeated> repeatedSubstrings(String text, int minLength) {
        int[] sa = SuffixArray.build(text).sa();
        int[] lcp = compute(text, sa);
        int n = lcp.length;

        List<LCPArray.Repeated> found = new ArrayList<>();
        Deque<int[]> stack = new ArrayDeque<>();  // {value, leftIndex}, decreasing values

        for (int i = 1; i <= n; i++) {
            int cur = i < n ? lcp[i] : 0;
            int left = i;
            while (!stack.isEmpty() && stack.peek()[0] > cur) {
                int[] top = stack.pop();
                int last = i - 1;
                if (top[0] > 0 && top[0] >= minLength) {
                    found.add(new LCPArray.Repeated(sa[top[1] - 1], top[0],
                            last - top[1] + 2));
                }
                left = top[1];
            }
            if (cur > 0 && (stack.isEmpty() || stack.peek()[0] < cur)) {
                stack.push(new int[]{cur, left});
            }
        }
        found.sort((x, y) -> {
            if (x.length() != y.length()) {
                return Integer.compare(y.length(), x.length());
            }
            return Integer.compare(y.occurrences(), x.occurrences());
        });
        return found;
    }
}
