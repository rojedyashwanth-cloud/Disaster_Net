import java.util.Arrays;

/**
 * SA-IS suffix array construction - module M2.
 *
 * <p>Use case in DisasterNet: the report index is rebuilt every time the
 * incident log grows. Prefix doubling rebuilds in O(n log n); SA-IS does it
 * in O(n), which is what keeps the "reindex on every new radio transcript"
 * step cheap.
 *
 * <p>The algorithm, following Nong, Zhang and Chan (2009):
 * <ol>
 *   <li>Classify every position as S-type (its suffix sorts before the
 *       suffix starting one character later) or L-type. A position is
 *       <i>LMS</i> when it is S-type and its predecessor is L-type; LMS
 *       positions are the only ones that need to be sorted explicitly.</li>
 *   <li><i>Induce</i> a candidate order from the LMS positions: seed them
 *       into their buckets, then sweep left to right placing L-type
 *       predecessors at bucket heads, and right to left placing S-type
 *       predecessors at bucket tails. Induction is O(n).</li>
 *   <li>Name each LMS substring by comparing neighbouring blocks, recurse on
 *       the reduced string of length m &lt;= n/2, and induce once more from
 *       the now correctly ordered LMS positions.</li>
 * </ol>
 *
 * <p>Bucket cursors are the subtle part. Each bucket v splits into an
 * L-region followed by an S-region, which is why the reference counts L-type
 * into {@code sum_s[v]} and S-type into {@code sum_l[v + 1]}: after the
 * prefix sums, {@code sum_l[v]} is the start of bucket v and
 * {@code sum_l[v + 1]} is the position just past its end. L-type induction
 * increments from {@code sum_l[v]}, S-type induction decrements from
 * {@code sum_l[v + 1]}, so the two phases fill disjoint halves of every
 * bucket and never overwrite each other.
 *
 * <p>Small inputs fall back to a naive sort or to prefix doubling, which
 * bounds the recursion depth and keeps the constant factor sane.
 *
 * <p>Complexity: O(n) time, O(n) space.
 */
public final class SAIS {

    private static final int THRESHOLD_NAIVE = 10;
    private static final int THRESHOLD_DOUBLING = 40;

    private SAIS() {
    }

    /** Suffix array of {@code text}. */
    public static int[] build(String text) {
        if (text.isEmpty()) {
            return new int[0];
        }
        int[] symbols = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            symbols[i] = text.charAt(i);
        }
        return build(symbols, 255);
    }

    /** Suffix array of raw symbols. */
    public static int[] build(int[] text) {
        int upper = 0;
        for (int value : text) {
            upper = Math.max(upper, value);
        }
        return build(text, upper);
    }

    /**
     * Core SA-IS.
     *
     * @param s     integer alphabet with every value in {@code [0, upper]}
     * @param upper largest symbol value
     */
    public static int[] build(int[] s, int upper) {
        int n = s.length;
        if (n == 0) {
            return new int[0];
        }
        if (n == 1) {
            return new int[]{0};
        }
        if (n == 2) {
            // Equal symbols: the shorter suffix ("x") sorts first.
            return s[0] < s[1] ? new int[]{0, 1} : new int[]{1, 0};
        }
        if (n < THRESHOLD_NAIVE) {
            return naive(s);
        }
        if (n < THRESHOLD_DOUBLING) {
            return doubling(s);
        }

        int[] sa = new int[n];

        // ls[i] == true means S-type. The final position keeps the default
        // false (L-type), which is what makes induction reach every position.
        boolean[] ls = new boolean[n];
        for (int i = n - 2; i >= 0; i--) {
            ls[i] = s[i] == s[i + 1] ? ls[i + 1] : s[i] < s[i + 1];
        }

        // Bucket bookkeeping: sum_l[v] ends up as the start of bucket v and
        // sum_l[v + 1] as one past its end; sum_s[v] as the end of the
        // L-region of bucket v.
        int[] sumL = new int[upper + 2];
        int[] sumS = new int[upper + 2];
        for (int i = 0; i < n; i++) {
            if (!ls[i]) {
                sumS[s[i]]++;
            } else {
                sumL[s[i] + 1]++;
            }
        }
        for (int i = 0; i <= upper; i++) {
            sumS[i] += sumL[i];
            if (i < upper) {
                sumL[i + 1] += sumS[i];
            }
        }

        int[] lmsMap = new int[n + 1];
        Arrays.fill(lmsMap, -1);
        int m = 0;
        for (int i = 1; i < n; i++) {
            if (!ls[i - 1] && ls[i]) {
                lmsMap[i] = m++;
            }
        }
        int[] lms = new int[m];
        for (int i = 1, k = 0; i < n; i++) {
            if (!ls[i - 1] && ls[i]) {
                lms[k++] = i;
            }
        }

        induce(s, sa, ls, sumL, sumS, upper, lmsMap, lms, m);

        if (m > 0) {
            // Compact the LMS positions out of the induced order.
            int[] sortedLms = new int[m];
            int found = 0;
            for (int v : sa) {
                if (v >= 0 && lmsMap[v] != -1) {
                    sortedLms[found++] = v;
                }
            }

            // Name each LMS substring; equal names mean equal blocks.
            int[] reduced = new int[m];
            reduced[lmsMap[sortedLms[0]]] = 0;
            int recUpper = 0;
            for (int i = 1; i < m; i++) {
                int l = sortedLms[i - 1];
                int r = sortedLms[i];
                int endL = lmsMap[l] + 1 < m ? lms[lmsMap[l] + 1] : n;
                int endR = lmsMap[r] + 1 < m ? lms[lmsMap[r] + 1] : n;
                boolean same;
                if (endL - l != endR - r) {
                    same = false;
                } else {
                    while (l < endL && s[l] == s[r]) {
                        l++;
                        r++;
                    }
                    if (l == n || s[l] != s[r]) {
                        same = false;
                    } else {
                        same = true;
                    }
                }
                if (!same) {
                    recUpper++;
                }
                reduced[lmsMap[sortedLms[i]]] = recUpper;
            }

            int[] reducedSa = build(reduced, recUpper);
            for (int i = 0; i < m; i++) {
                sortedLms[i] = lms[reducedSa[i]];
            }
            induce(s, sa, ls, sumL, sumS, upper, lmsMap, sortedLms, m);
        }
        return sa;
    }

    /** One LMS-ordering induction pass, O(n). */
    private static void induce(int[] s, int[] sa, boolean[] ls, int[] sumL, int[] sumS,
                               int upper, int[] lmsMap, int[] orderedLms, int m) {
        int n = s.length;
        Arrays.fill(sa, -1);
        int[] buf = new int[upper + 2];

        // Seed the LMS positions after the L-region of their bucket.
        System.arraycopy(sumS, 0, buf, 0, upper + 1);
        for (int i = 0; i < m; i++) {
            int d = orderedLms[i];
            if (d == n) {
                continue;
            }
            sa[buf[s[d]]++] = d;
        }

        // The last position is L-type and has no successor to induce it, so it
        // is seeded explicitly at the head of its bucket.
        System.arraycopy(sumL, 0, buf, 0, upper + 1);
        sa[buf[s[n - 1]]++] = n - 1;

        // L-type induction: bucket heads, left to right.
        System.arraycopy(sumL, 0, buf, 0, upper + 1);
        for (int i = 0; i < n; i++) {
            int v = sa[i];
            if (v >= 1 && !ls[v - 1]) {
                sa[buf[s[v - 1]]++] = v - 1;
            }
        }

        // S-type induction: bucket tails, right to left.
        System.arraycopy(sumL, 0, buf, 0, upper + 1);
        for (int i = n - 1; i >= 0; i--) {
            int v = sa[i];
            if (v >= 1 && ls[v - 1]) {
                sa[--buf[s[v - 1] + 1]] = v - 1;
            }
        }
    }

    /** O(n^2 log n) comparison sort, used only for tiny inputs. */
    private static int[] naive(int[] s) {
        int n = s.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            if (a == b) {
                return 0;
            }
            int x = a;
            int y = b;
            while (x < n && y < n) {
                if (s[x] != s[y]) {
                    return Integer.compare(s[x], s[y]);
                }
                x++;
                y++;
            }
            return x == n ? -1 : 1;
        });
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = order[i];
        }
        return out;
    }

    /** O(n log^2 n) prefix doubling, used to stop the recursion early. */
    private static int[] doubling(int[] s) {
        int n = s.length;
        int[] sa = new int[n];
        int[] rank = s.clone();
        int[] tmp = new int[n];
        for (int i = 0; i < n; i++) {
            sa[i] = i;
        }
        for (int k = 1; k < n; k *= 2) {
            final int step = k;
            final int[] rnk = rank;   // snapshot: rank is swapped every round
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) {
                order[i] = i;
            }
            Arrays.sort(order, (x, y) -> {
                if (rnk[x] != rnk[y]) {
                    return Integer.compare(rnk[x], rnk[y]);
                }
                int rx = x + step < n ? rnk[x + step] : -1;
                int ry = y + step < n ? rnk[y + step] : -1;
                return Integer.compare(rx, ry);
            });
            for (int i = 0; i < n; i++) {
                sa[i] = order[i];
            }
            tmp[sa[0]] = 0;
            for (int i = 1; i < n; i++) {
                int prev = sa[i - 1];
                int cur = sa[i];
                boolean less;
                if (rank[prev] != rank[cur]) {
                    less = rank[prev] < rank[cur];
                } else {
                    int rp = prev + k < n ? rank[prev + k] : -1;
                    int rc = cur + k < n ? rank[cur + k] : -1;
                    less = rp < rc;
                }
                tmp[cur] = tmp[prev] + (less ? 1 : 0);
            }
            int[] swap = rank;
            rank = tmp;
            tmp = swap;
        }
        return sa;
    }
}
