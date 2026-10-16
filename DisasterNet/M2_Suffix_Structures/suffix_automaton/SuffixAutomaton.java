import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Suffix automaton - module M2.
 *
 * <p>Use case in DisasterNet: unlike the suffix tree (O(n) states) the
 * automaton has at most {@code 2n - 1} states but, more importantly, it can
 * be built and streamed incrementally: field telemetry arrives one character
 * at a time and each chunk is fed to {@link #extend}. That makes it the right
 * structure for the continuously growing incident log, and it is what the
 * "streaming duplicate-phrase detection" stage uses.
 *
 * <p>States group substrings that share their end positions: the strings
 * spelled on the paths into a state form one end-position equivalence class,
 * so a state can be summarised by a single occurrence count. Transitions
 * carry one character; each state keeps the longest string in its class
 * ({@code len}) and a suffix link to the class of its longest proper suffix.
 *
 * <p>Occurrence counts come from the standard two-pass method: sort states by
 * {@code len}, then push each count along suffix links from longer to shorter,
 * so a state ends up holding the total number of end positions of its class.
 *
 * <p>Complexity: build O(n) for a constant alphabet, query O(m) per substring.
 */
public final class SuffixAutomaton {

    private static final class State {
        final Map<Character, Integer> next = new HashMap<>();
        int link = -1;
        int len;
        int occurrences;
        int firstPos = -1;

        State(int len) {
            this.len = len;
        }
    }

    private final List<State> states = new ArrayList<>();
    private int last = 0;
    private final int length;
    private final char[] chars;

    /** Builds an automaton over {@code text}. */
    public SuffixAutomaton(String text) {
        this.length = text.length();
        this.chars = text.toCharArray();
        states.add(new State(0));   // root
        for (int i = 0; i < text.length(); i++) {
            extend(text.charAt(i), i);
        }
        propagateOccurrences();
    }

    /** Appends one character; the automaton grows in O(1) amortised. */
    public void extend(char c) {
        extend(c, -1);
    }

    private void extend(char c, int position) {
        int cur = states.size();
        states.add(new State(states.get(last).len + 1));
        // A freshly created state corresponds to the new end position, so it
        // starts with count 1. Clone states start at 0: they split an existing
        // class and must not add an end position of their own.
        states.get(cur).occurrences = 1;
        if (position >= 0) {
            states.get(cur).firstPos = position;
        }

        int p = last;
        while (p != -1 && !states.get(p).next.containsKey(c)) {
            states.get(p).next.put(c, cur);
            p = states.get(p).link;
        }
        if (p == -1) {
            states.get(cur).link = 0;
        } else {
            int q = states.get(p).next.get(c);
            if (states.get(p).len + 1 == states.get(q).len) {
                states.get(cur).link = q;
            } else {
                int clone = states.size();
                State cloneState = new State(states.get(p).len + 1);
                cloneState.next.putAll(states.get(q).next);
                cloneState.link = states.get(q).link;
                cloneState.firstPos = states.get(q).firstPos;
                cloneState.occurrences = 0;   // a clone is not an end position
                states.add(cloneState);

                while (p != -1 && states.get(p).next.get(c) == q) {
                    states.get(p).next.put(c, clone);
                    p = states.get(p).link;
                }
                states.get(q).link = clone;
                states.get(cur).link = clone;
            }
        }
        last = cur;
    }

    /** Pushes end-position counts from long states to their suffix links. */
    private void propagateOccurrences() {
        int maxLen = 0;
        for (State s : states) {
            maxLen = Math.max(maxLen, s.len);
        }
        int[] count = new int[maxLen + 1];
        for (State s : states) {
            count[s.len]++;
        }
        for (int i = 1; i <= maxLen; i++) {
            count[i] += count[i - 1];
        }
        int[] order = new int[states.size()];
        for (int i = states.size() - 1; i >= 0; i--) {
            order[--count[states.get(i).len]] = i;
        }
        for (int i = order.length - 1; i > 0; i--) {
            int v = order[i];
            int parent = states.get(v).link;
            if (parent >= 0) {
                states.get(parent).occurrences += states.get(v).occurrences;
            }
        }
    }

    /** Walks the automaton along {@code pattern}; returns the state or null. */
    private Integer walk(String pattern) {
        int v = 0;
        for (int i = 0; i < pattern.length(); i++) {
            Integer next = states.get(v).next.get(pattern.charAt(i));
            if (next == null) {
                return null;
            }
            v = next;
        }
        return v;
    }

    /** True when {@code pattern} is a substring of the indexed text. */
    public boolean contains(String pattern) {
        return walk(pattern) != null;
    }

    /** Number of occurrences of {@code pattern}, 0 if absent. */
    public int occurrencesOf(String pattern) {
        Integer v = walk(pattern);
        return v == null ? 0 : states.get(v).occurrences;
    }

    /** One end position of {@code pattern}, or -1. */
    public int firstPositionOf(String pattern) {
        Integer v = walk(pattern);
        return v == null ? -1 : states.get(v).firstPos;
    }

    /** Length of the longest suffix of {@code pattern} that occurs in the text. */
    public int longestMatchedSuffix(String pattern) {
        int v = 0;
        int best = 0;
        int bestLen = 0;
        for (int i = 0; i < pattern.length(); i++) {
            Integer next = states.get(v).next.get(pattern.charAt(i));
            if (next == null) {
                break;
            }
            v = next;
            if (states.get(v).len > bestLen) {
                bestLen = states.get(v).len;
                best = i + 1;
            }
        }
        return best;
    }

    /**
     * Longest common substring of the indexed text and {@code other}.
     * Scans {@code other} once, falling back along suffix links on a miss.
     */
    public String longestCommonSubstring(String other) {
        int v = 0;
        int l = 0;
        int best = 0;
        int bestEnd = 0;
        for (int i = 0; i < other.length(); i++) {
            char c = other.charAt(i);
            while (v != 0 && !states.get(v).next.containsKey(c)) {
                v = states.get(v).link;
                l = states.get(v).len;
            }
            Integer next = states.get(v).next.get(c);
            if (next != null) {
                v = next;
                l++;
            } else {
                v = 0;
                l = 0;
            }
            if (l > best) {
                best = l;
                bestEnd = i + 1;
            }
        }
        return other.substring(bestEnd - best, bestEnd);
    }

    /** Every distinct substring with its occurrence count, keyed by substring. */
    public Map<String, Integer> substringFrequencies(int maxSubstringLength) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (State s : states) {
            if (s.len == 0 || s.occurrences == 0) {
                continue;
            }
            int end = s.firstPos;
            for (int l = Math.max(1, s.len - maxSubstringLength + 1); l <= s.len; l++) {
                if (end - l + 1 < 0) {
                    continue;
                }
                String sub = new String(chars, end - l + 1, l);
                out.merge(sub, s.occurrences, (a, b) -> Math.min(a, b));
            }
        }
        return out;
    }

    /** Builds and keeps the source characters so occurrences can be sliced out. */
    public static SuffixAutomaton of(String text) {
        return new SuffixAutomaton(text);
    }
    public int stateCount() {
        return states.size();
    }

    public int length() {
        return length;
    }

    /** State lengths in increasing order; a compact fingerprint of the shape. */
    public int[] stateLengths() {
        Map<Integer, Integer> histogram = new TreeMap<>();
        for (State s : states) {
            histogram.merge(s.len, 1, Integer::sum);
        }
        int[] out = new int[histogram.size()];
        int i = 0;
        for (int count : histogram.values()) {
            out[i++] = count;
        }
        return out;
    }
}
