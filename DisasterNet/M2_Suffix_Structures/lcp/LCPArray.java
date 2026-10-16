import java.util.Arrays;

/**
 * LCP array - module M2.
 *
 * <p>Use case in DisasterNet: the LCP array is the bridge between a suffix
 * array and a suffix tree. It records, for every adjacent pair of suffixes in
 * sorted order, how many leading characters they share, and those numbers are
 * exactly the depths of the internal nodes when the tree is built.
 *
 * <p>Two forms are provided:
 * <ul>
 *   <li>{@link #naive} computes all values by direct comparison, O(n^2). It is
 *       the definition, written plainly, and is used as the oracle in tests.</li>
 *   <li>{@link #fromSuffixArray} delegates to Kasai for O(n).</li>
 * </ul>
 *
 * <p>Convention: {@code lcp[0] = 0}, and {@code lcp[i]} is the common prefix
 * length of suffixes {@code sa[i-1]} and {@code sa[i]} for {@code i > 0}.
 */
public final class LCPArray {

    private final int[] lcp;

    private LCPArray(int[] lcp) {
        this.lcp = lcp;
    }

    /**
     * O(n^2) definition: compare each adjacent pair of suffixes directly.
     * Kept as the correctness oracle for the linear construction.
     */
    public static int[] naive(String text, int[] sa) {
        int n = text.length();
        int[] out = new int[n];
        for (int i = 1; i < n; i++) {
            int a = sa[i - 1];
            int b = sa[i];
            int k = 0;
            while (a + k < n && b + k < n && text.charAt(a + k) == text.charAt(b + k)) {
                k++;
            }
            out[i] = k;
        }
        return out;
    }

    /** O(n) LCP array via Kasai's algorithm. */
    public static LCPArray fromSuffixArray(String text, int[] sa) {
        return new LCPArray(Kasai.compute(text, sa));
    }

    /** O(n) LCP array, computing the suffix array internally. */
    public static LCPArray build(String text) {
        int[] sa = SuffixArray.build(text).sa();
        return new LCPArray(Kasai.compute(text, sa));
    }

    /** The raw array; a defensive copy is returned. */
    public int[] toArray() {
        return lcp.clone();
    }

    public int get(int i) {
        return lcp[i];
    }

    public int size() {
        return lcp.length;
    }

    /**
     * Longest run of equal values in the LCP array, and the substring it
     * represents. A run of length L at indices [i, j] means the substring
     * shared by suffixes sa[i-1] .. sa[j] has length L, so it occurs LCP-free
     * at least j-i+2 times: the longest repeated substring in the corpus.
     */
    public record Repeated(int start, int length, int occurrences) {
        public String substring(String text) {
            return text.substring(start, start + length);
        }
    }

    /**
     * Longest substring occurring at least twice, found by scanning maximal
     * runs of equal LCP values. A run of value v spanning indices [i, j)
     * means suffixes sa[i-1] .. sa[j-1] all share a v-character prefix, so that
     * prefix occurs j-i+1 times.
     */
    public Repeated longestRepeated(String text, int[] sa) {
        int best = 0;
        int bestStart = 0;
        int bestCount = 0;
        int i = 1;
        while (i < lcp.length) {
            int v = lcp[i];
            int j = i;
            while (j < lcp.length && lcp[j] == v) {
                j++;
            }
            if (v > best) {
                best = v;
                bestStart = sa[i - 1];
                bestCount = j - i + 1;
            }
            i = j;
        }
        return new Repeated(bestStart, best, bestCount);
    }
}
