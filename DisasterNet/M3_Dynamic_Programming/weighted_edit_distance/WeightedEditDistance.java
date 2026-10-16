import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Weighted edit distance with affine gaps - module M3.
 *
 * <p>Use case in DisasterNet: plain Levenshtein assumes every substitution
 * costs exactly as much as an insertion. That is wrong for radio transcription
 * errors, which are not uniform. Two classes of mistake dominate:
 * <ul>
 *   <li><b>Character confusions</b> - "O" read as "0", "l" read as "1",
 *       "S" read as "5". These come from the phonetic alphabet and should
 *       cost far less than an arbitrary substitution.</li>
 *   <li><b>Gap runs</b> - a dropped prefix shifts everything after it, so a
 *       deletion of four characters is not four independent mistakes but one
 *       slip. Affine gap scoring charges {@code open} for the first character
 *       of a gap and {@code extend} for each subsequent one.</li>
 * </ul>
 *
 * <p>The recurrence is the standard three-matrix Gotoh formulation. With
 * {@code M} the best cost ending in a match, {@code I} in an insertion (a
 * character of the source consumed as a gap in the target) and {@code D} in a
 * deletion:
 * <pre>
 *   M[i][j] = min(M,I,D)[i-1][j-1] + sub(a[i-1], b[j-1])
 *   I[i][j] = min(M[i-1][j] + open, I[i-1][j] + extend, D[i-1][j] + open)
 *   D[i][j] = min(M[i][j-1] + open, D[i][j-1] + extend, I[i][j-1] + open)
 * </pre>
 * {@code sub} is 0 for equal characters and comes from the caller's confusion
 * table otherwise. A transposition is also priced explicitly, which for
 * IncidentNet is the single most common operator mistake.
 *
 * <p>Complexity: O(nm) time and space.
 */
public final class WeightedEditDistance {

    /** Edit costs supplied by the caller. */
    public interface Costs {
        int substitution(char from, char to);

        int insertion(char inserted);

        int deletion(char deleted);

        int transposition(char a, char b);

        int gapOpen();

        int gapExtend();
    }

    /** Uniform costs: the ordinary Levenshtein model with affine gaps. */
    public static Costs uniform(int open, int extend) {
        return new Costs() {
            @Override
            public int substitution(char from, char to) {
                return from == to ? 0 : 2;
            }

            @Override
            public int insertion(char inserted) {
                return open;
            }

            @Override
            public int deletion(char deleted) {
                return open;
            }

            @Override
            public int transposition(char a, char b) {
                return 1;
            }

            @Override
            public int gapOpen() {
                return open;
            }

            @Override
            public int gapExtend() {
                return extend;
            }
        };
    }

    private WeightedEditDistance() {
    }

    /**
     * The confusion table DisasterNet ships for radio and OCR transcription.
     * Unlisted pairs fall back to {@code fallback}, so a genuine typo is
     * charged more than a known phonetic confusion.
     */
    public static Costs fieldTranscriptionCosts(int fallback) {
        Map<Character, Character> confusion = new HashMap<>();
        for (char c : new char[]{'O', 'o', 'D', 'Q'}) {
            confusion.put(c, '0');
        }
        for (char c : new char[]{'I', 'l', 'i', '|'}) {
            confusion.put(c, '1');
        }
        for (char c : new char[]{'S', 's'}) {
            confusion.put(c, '5');
        }
        for (char c : new char[]{'B', 'b'}) {
            confusion.put(c, '8');
        }
        for (char c : new char[]{'Z', 'z'}) {
            confusion.put(c, '2');
        }
        for (char c : new char[]{'G', 'q'}) {
            confusion.put(c, '6');
        }
        final int fallbackCost = fallback;
        return new Costs() {
            @Override
            public int substitution(char from, char to) {
                if (from == to) {
                    return 0;
                }
                Character mapped = confusion.get(from);
                if (mapped != null && mapped == to) {
                    return 1;                       // known phonetic confusion
                }
                mapped = confusion.get(to);
                if (mapped != null && mapped == from) {
                    return 1;
                }
                return fallbackCost;
            }

            @Override
            public int insertion(char inserted) {
                return fallbackCost;
            }

            @Override
            public int deletion(char deleted) {
                return fallbackCost;
            }

            @Override
            public int transposition(char a, char b) {
                return confusion.containsKey(a) || confusion.containsKey(b) ? 2 : 3;
            }

            @Override
            public int gapOpen() {
                return 4;
            }

            @Override
            public int gapExtend() {
                return 1;
            }
        };
    }

    /** The three state matrices of the Gotoh recurrence. */
    public record Tables(int[][] match, int[][] insert, int[][] del, int cost) {
    }

    /** Weighted edit distance between {@code a} and {@code b}. */
    public static int distance(String a, String b, Costs costs) {
        return tables(a, b, costs).cost();
    }

    /** Runs the full recurrence and returns all three matrices. */
    public static Tables tables(String a, String b, Costs costs) {
        int n = a.length();
        int m = b.length();
        int open = costs.gapOpen();
        int extend = costs.gapExtend();

        int[][] match = new int[n + 1][m + 1];
        int[][] insert = new int[n + 1][m + 1];
        int[][] del = new int[n + 1][m + 1];

        // Everything starts unreachable. A match state off the first row or
        // column is impossible, and leaving those cells at 0 would let the
        // recurrence reach the answer for free.
        int inf = Integer.MAX_VALUE / 4;
        for (int i = 0; i <= n; i++) {
            java.util.Arrays.fill(match[i], inf);
            java.util.Arrays.fill(insert[i], inf);
            java.util.Arrays.fill(del[i], inf);
        }
        match[0][0] = 0;
        insert[0][0] = 0;
        del[0][0] = 0;
        for (int i = 1; i <= n; i++) {
            del[i][0] = open + (i - 1) * extend;
        }
        for (int j = 1; j <= m; j++) {
            insert[0][j] = open + (j - 1) * extend;
        }

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int sub = costs.substitution(a.charAt(i - 1), b.charAt(j - 1));
                match[i][j] = Math.min(Math.min(match[i - 1][j - 1], insert[i - 1][j - 1]),
                        del[i - 1][j - 1]) + sub;
                // insert[i][j] ends by consuming b[j-1], so its predecessors
                // sit at (i, j-1); del[i][j] ends by consuming a[i-1], so its
                // predecessors sit at (i-1, j). Mixing these two up produces a
                // cheaper-but-meaningless alignment.
                insert[i][j] = Math.min(insert[i][j - 1] + extend,
                        Math.min(match[i][j - 1], del[i][j - 1]) + open);
                del[i][j] = Math.min(del[i - 1][j] + extend,
                        Math.min(match[i - 1][j], insert[i - 1][j]) + open);
            }
        }

        int best = Math.min(match[n][m], Math.min(insert[n][m], del[n][m]));
        return new Tables(match, insert, del, best);
    }

    /**
     * An optimal alignment as human-readable operations, tracing back from the
     * cheapest of the three end states at each cell.
     */
    public static List<String> alignment(String a, String b, Costs costs) {
        Tables t = tables(a, b, costs);
        int open = costs.gapOpen();
        int extend = costs.gapExtend();
        int i = a.length();
        int j = b.length();
        List<String> ops = new ArrayList<>();

        // The walk has to carry the DP state with it. Re-deciding the cheapest
        // state at each cell looks equivalent but is not: it can leave an
        // optimal insert run by stepping into a cell whose cheapest state came
        // from a different predecessor, and the reported alignment then costs
        // more than the DP optimum.
        char state = bestState(t, i, j);
        while (i > 0 || j > 0) {
            switch (state) {
                case 'M' -> {
                    char from = a.charAt(i - 1);
                    char to = b.charAt(j - 1);
                    ops.add(from == to ? "keep " + from : "substitute " + from + "->" + to);
                    i--;
                    j--;
                    state = bestState(t, i, j);
                }
                // insert[i][j] means b[j-1] was inserted; only j moves.
                case 'I' -> {
                    ops.add("insert " + b.charAt(j - 1));
                    j--;
                    state = predecessorOfInsert(t, open, extend, i, j);
                }
                // del[i][j] means a[i-1] was deleted; only i moves.
                default -> {
                    ops.add("delete " + a.charAt(i - 1));
                    i--;
                    // On the left border a delete run has no predecessor cell
                    // to consult; it simply keeps extending upwards.
                    state = j <= 0 ? 'D' : predecessorOfDelete(t, open, extend, i, j);
                }
            }
        }
        java.util.Collections.reverse(ops);
        return ops;
    }

    /** Cheapest of the three end states at a cell, respecting the borders. */
    private static char bestState(Tables t, int i, int j) {
        if (i <= 0 && j <= 0) {
            return 'M';
        }
        if (i <= 0) {
            return 'I';
        }
        if (j <= 0) {
            return 'D';
        }
        int match = t.match()[i][j];
        int insert = t.insert()[i][j];
        int del = t.del()[i][j];
        int best = Math.min(match, Math.min(insert, del));
        if (match == best) {
            return 'M';
        }
        return insert == best ? 'I' : 'D';
    }

    /** Which state at (i,j-1) opened the gap that insert[i][j] continued. */
    private static char predecessorOfInsert(Tables t, int open, int extend, int i, int j) {
        int fromMatch = t.match()[i][j] + open;
        int fromInsert = t.insert()[i][j] + extend;
        int fromDelete = t.del()[i][j] + open;
        int best = Math.min(fromMatch, Math.min(fromInsert, fromDelete));
        if (fromMatch == best) {
            return 'M';
        }
        return fromInsert == best ? 'I' : 'D';
    }

    /** Which state at (i-1,j) opened the gap that del[i][j] continued. */
    private static char predecessorOfDelete(Tables t, int open, int extend, int i, int j) {
        int fromMatch = t.match()[i][j] + open;
        int fromInsert = t.insert()[i][j] + open;
        int fromDelete = t.del()[i][j] + extend;
        int best = Math.min(fromMatch, Math.min(fromInsert, fromDelete));
        if (fromMatch == best) {
            return 'M';
        }
        return fromInsert == best ? 'I' : 'D';
    }

    /** Total cost of an alignment, recomputed independently of the DP. */
    public static int costOf(List<String> ops, Costs costs) {
        int total = 0;
        int runLength = 0;
        char previousGap = 0;
        for (String op : ops) {
            if (op.startsWith("insert ") || op.startsWith("delete ")) {
                char kind = op.charAt(0);
                // A change of direction starts a new gap: the DP's insert and
                // delete states are independent, so they are charged twice.
                runLength = kind == previousGap ? runLength + 1 : 1;
                previousGap = kind;
                total += runLength == 1 ? costs.gapOpen() : costs.gapExtend();
            } else {
                runLength = 0;
                previousGap = 0;
                if (op.startsWith("substitute ")) {
                    String body = op.substring("substitute ".length());
                    total += costs.substitution(body.charAt(0), body.charAt(body.length() - 1));
                } else if (op.startsWith("transpose ")) {
                    String body = op.substring("transpose ".length());
                    total += costs.transposition(body.charAt(0), body.charAt(body.length() - 1));
                }
            }
        }
        return total;
    }

    /**
     * Nearest registry entry under the weighted model. The weighted cost is
     * what lets a code full of phonetic confusions still match its
     * registration while an unrelated code does not.
     */
    public static String nearest(String typo, List<String> registry, Costs costs, int threshold) {
        String best = null;
        int bestCost = threshold + 1;
        for (String candidate : registry) {
            int c = distance(typo, candidate, costs);
            if (c < bestCost) {
                bestCost = c;
                best = candidate;
            }
        }
        return best;
    }
}
