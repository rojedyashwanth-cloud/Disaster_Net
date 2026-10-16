import java.util.Arrays;


public final class SuffixArray {

    private final int[] sa;
    private final int[] rank;
    private final String text;

    private SuffixArray(String text, int[] sa, int[] rank) {
        this.text = text;
        this.sa = sa;
        this.rank = rank;
    }

    /** Builds the suffix array of {@code text}. */
    public static SuffixArray build(String text) {
        return build(text.toCharArray());
    }

    /** Builds the suffix array over raw characters. */
    public static SuffixArray build(char[] input) {
        int n = input.length;
        if (n == 0) {
            return new SuffixArray("", new int[0], new int[0]);
        }

        int[] sa = new int[n];
        int[] rank = new int[n];
        int[] order = new int[n];
        int[] newRank = new int[n];

        // Rank 0 is reserved for "past the end of the string", so characters
        // are mapped to 1..256 and a missing second half sorts first.
        for (int i = 0; i < n; i++) {
            rank[i] = input[i] + 1;
            order[i] = i;
        }
        countingSortByRank(sa, rank, order, 256, n);

        int classes = 1;
        newRank[sa[0]] = classes;
        for (int i = 1; i < n; i++) {
            if (input[sa[i]] != input[sa[i - 1]]) {
                classes++;
            }
            newRank[sa[i]] = classes;
        }
        int[] swap = rank;
        rank = newRank;
        newRank = swap;

        for (int k = 1; classes < n && k < n; k <<= 1) {
            // Build the order by second key. Positions p >= n - k have no
            // second half, so their second key is 0 and they must come first;
            // the remaining positions are visited as sa[i] - k, which is
            // already ordered by rank[sa[i]] = their second key.
            int at = 0;
            for (int p = n - k; p < n; p++) {
                order[at++] = p;
            }
            for (int i = 0; i < n; i++) {
                if (sa[i] >= k) {
                    order[at++] = sa[i] - k;
                }
            }
            // Stable counting sort on the first key finishes the radix pass.
            countingSortByRank(sa, rank, order, classes, n);

            int newClasses = 1;
            newRank[sa[0]] = newClasses;
            for (int i = 1; i < n; i++) {
                int a = sa[i - 1];
                int b = sa[i];
                int aSecond = a + k < n ? rank[a + k] : 0;
                int bSecond = b + k < n ? rank[b + k] : 0;
                if (rank[a] != rank[b] || aSecond != bSecond) {
                    newClasses++;
                }
                newRank[b] = newClasses;
            }
            swap = rank;
            rank = newRank;
            newRank = swap;
            classes = newClasses;
        }
        return new SuffixArray(new String(input), sa, rank);
    }

    private static void countingSortByRank(int[] sa, int[] rank, int[] order, int maxRank, int n) {
        int[] count = new int[maxRank + 1];
        for (int i = 0; i < n; i++) {
            count[rank[order[i]]]++;
        }
        for (int i = 1; i < count.length; i++) {
            count[i] += count[i - 1];
        }
        for (int i = n - 1; i >= 0; i--) {
            sa[--count[rank[order[i]]]] = order[i];
        }
    }

    /** {@code sa[i]} is the start index of the i-th smallest suffix. */
    public int[] sa() {
        return sa.clone();
    }

    /** {@code rank[p]} is the 1-based lexicographic rank of the suffix at p. */
    public int[] rank() {
        return rank.clone();
    }

    public int length() {
        return sa.length;
    }

    public String text() {
        return text;
    }

    /** The i-th smallest suffix as a string. */
    public String suffix(int i) {
        return text.substring(sa[i]);
    }

    /** All suffixes in sorted order. */
    public String[] sortedSuffixes() {
        String[] out = new String[sa.length];
        for (int i = 0; i < sa.length; i++) {
            out[i] = text.substring(sa[i]);
        }
        return out;
    }

    /** Lexicographic comparison of two suffixes by start index. */
    public int compareSuffixes(int a, int b) {
        int i = sa[a];
        int j = sa[b];
        while (i < text.length() && j < text.length()) {
            char ca = text.charAt(i);
            char cb = text.charAt(j);
            if (ca != cb) {
                return Character.compare(ca, cb);
            }
            i++;
            j++;
        }
        return Integer.compare(text.length() - i, text.length() - j);
    }

    /**
     * Reference implementation: sort suffixes with a plain comparator.
     * O(n^2 log n) but obviously correct, used by the module self-check to
     * validate the doubling algorithm.
     */
    public static int[] naive(String text) {
        Integer[] idx = new Integer[text.length()];
        for (int i = 0; i < idx.length; i++) {
            idx[i] = i;
        }
        Arrays.sort(idx, (a, b) -> {
            int x = a;
            int y = b;
            while (x < text.length() && y < text.length()) {
                char ca = text.charAt(x);
                char cb = text.charAt(y);
                if (ca != cb) {
                    return Character.compare(ca, cb);
                }
                x++;
                y++;
            }
            return Integer.compare(text.length() - x, text.length() - y);
        });
        int[] out = new int[idx.length];
        for (int i = 0; i < idx.length; i++) {
            out[i] = idx[i];
        }
        return out;
    }

    /**
     * Range of suffix-array positions whose suffixes start with
     * {@code pattern}, via binary search on the suffix array.
     *
     * @return {@code [lo, hi)} half-open range in suffix-array order
     */
    public int[] rangeForPrefix(String pattern) {
        int lo = lowerBound(pattern);
        int hi = lowerBoundPrefixSuccessor(pattern);
        return new int[]{lo, hi};
    }

    /** Start index of the first suffix beginning with {@code prefix}. */
    public int lowerBound(String prefix) {
        int lo = 0;
        int hi = sa.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (compareSuffixWithPrefix(sa[mid], prefix) < 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** Start index of the first suffix strictly greater than every {@code prefix}-suffix. */
    private int lowerBoundPrefixSuccessor(String prefix) {
        int lo = 0;
        int hi = sa.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (compareSuffixWithPrefix(sa[mid], prefix) <= 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** Negative if suffix at {@code start} sorts before the prefix, else 0 or positive. */
    private int compareSuffixWithPrefix(int start, String prefix) {
        int n = text.length();
        for (int i = 0; i < prefix.length(); i++) {
            if (start + i >= n) {
                return -1;
            }
            int cmp = Character.compare(text.charAt(start + i), prefix.charAt(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    /** Number of suffixes starting with {@code prefix}: its occurrence count. */
    public int countOccurrences(String prefix) {
        int[] range = rangeForPrefix(prefix);
        return range[1] - range[0];
    }
}
