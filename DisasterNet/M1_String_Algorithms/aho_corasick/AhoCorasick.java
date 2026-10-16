
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AhoCorasick {


    private static final class Node {
        final Map<Character, Integer> next = new HashMap<>();
        int fail;
        int output;      // nearest terminal on the fail chain, or -1; the root keeps 0
        int depth;
        List<Integer> patternsHere;  // pattern ids ending exactly at this node
    }

    private final List<Node> nodes = new ArrayList<>();
    private final List<String> patterns = new ArrayList<>();
    private final boolean ignoreCase;

    /**
     * Builds the automaton over {@code rawPatterns}, ignoring case.
     *
     * <p>Null and empty patterns are skipped rather than inserted: a zero-length
     * terminal would match at every position and flood the caller with hits
     * carrying no information. Use {@link #patterns()} to see what was actually
     * indexed.
     */
    public AhoCorasick(Collection<String> rawPatterns) {
        this(rawPatterns, true);
    }

  
    /**
     * Builds the automaton over {@code rawPatterns}.
     *
     * @param ignoreCase true to fold case in both patterns and text
     */
    public AhoCorasick(Collection<String> rawPatterns, boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
        nodes.add(new Node());
        for (String p : rawPatterns) {
            if (p != null && !p.isEmpty()) {
                addPattern(p);
            }
        }
        buildFailureLinks();
    }

    private void addPattern(String pattern) {
        String p = normalize(pattern);
        patterns.add(p);
        int id = patterns.size() - 1;
        int v = 0;
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            Integer next = nodes.get(v).next.get(c);
            if (next == null) {
                Node fresh = new Node();
                fresh.depth = nodes.get(v).depth + 1;
                nodes.add(fresh);
                nodes.get(v).next.put(c, nodes.size() - 1);
                next = nodes.size() - 1;
            }
            v = next;
        }
        List<Integer> here = nodes.get(v).patternsHere;
        if (here == null) {
            here = new ArrayList<>();
            nodes.get(v).patternsHere = here;
        }
        here.add(id);
    }

    private void buildFailureLinks() {
        Deque<Integer> queue = new ArrayDeque<>();
        for (Integer child : nodes.get(0).next.values()) {
            nodes.get(child).fail = 0;
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            int v = queue.poll();
            Node node = nodes.get(v);
            node.output = nearestTerminal(node.fail);
            for (Map.Entry<Character, Integer> e : node.next.entrySet()) {
                char c = e.getKey();
                int child = e.getValue();
                int f = node.fail;
                while (f != 0 && !nodes.get(f).next.containsKey(c)) {
                    f = nodes.get(f).fail;
                }
                Integer candidate = nodes.get(f).next.get(c);
                nodes.get(child).fail = (candidate == null || candidate == child) ? 0 : candidate;
                queue.add(child);
            }
        }
    }

    private int nearestTerminal(int v) {
        while (v != 0) {
            if (nodes.get(v).patternsHere != null) {
                return v;
            }
            v = nodes.get(v).fail;
        }
        return nodes.get(0).patternsHere != null ? 0 : -1;
    }

    private int step(int state, char raw) {
        char c = normalizeChar(raw);
        int v = state;
        while (v != 0 && !nodes.get(v).next.containsKey(c)) {
            v = nodes.get(v).fail;
        }
        Integer next = nodes.get(v).next.get(c);
        return next == null ? 0 : next;
    }

    /**
     * A single keyword occurrence.
     *
     * <p>{@code end} is exclusive, matching {@link String#substring} and
     * {@code Matcher.end()}, so {@code text.substring(start, end)} is exactly
     * {@code pattern}. An inclusive end would make {@link #length()} disagree with
     * the pattern length by one, which is the bug this convention avoids.
     *
     * @param pattern the keyword that matched
     * @param start inclusive start offset
     * @param end exclusive end offset
     * @param textLabel document the hit came from, or null if unscanned
     */
    public record Match(String pattern, int start, int end, String textLabel) {
        public int length() {
            return end - start;
        }
    }

    /** All matches in {@code text}, in order of end position. */
    public List<Match> search(String text) {
        return search(text, null);
    }

    /**
     * All matches in {@code text}, tagged with {@code label} so that a caller
     * scanning many reports can tell which document a hit came from.
     */
    public List<Match> search(String text, String label) {
        List<Match> matches = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return matches;
        }
        int v = 0;
        for (int i = 0; i < text.length(); i++) {
            v = step(v, text.charAt(i));
            int terminal = nodes.get(v).patternsHere != null ? v : nodes.get(v).output;
            while (terminal >= 0 && terminal != 0) {
                for (int id : nodes.get(terminal).patternsHere) {
                    String p = patterns.get(id);
                    matches.add(new Match(p, i - p.length() + 1, i + 1, label));
                }
                terminal = nodes.get(terminal).output;
            }
        }
        return matches;
    }

    /** Distinct patterns that occur at least once, with hit counts. */
    public Map<String, Integer> hitCounts(String text) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Match m : search(text)) {
            counts.merge(m.pattern(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * True when at least one pattern occurs.
     *
     * <p>Mirrors the terminal test in {@link #search}, including its treatment of
     * node 0. {@code step} returns the root whenever a character has no outgoing
     * edge, and the root's {@code output} is 0 rather than -1, so testing
     * {@code output >= 0} would report a hit on the first unmatched character of
     * any text. The sentinel for "no terminal" is therefore {@code > 0}, not
     * {@code >= 0}.
     */
    public boolean matchesAny(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        int v = 0;
        for (int i = 0; i < text.length(); i++) {
            v = step(v, text.charAt(i));
            Node node = nodes.get(v);
            if (node.patternsHere != null || node.output > 0) {
                return true;
            }
        }
        return false;
    }

    /** First pattern that occurs, scanning patterns in insertion order. */
    public String firstMatch(String text) {
        List<Match> all = search(text);
        return all.isEmpty() ? null : all.get(0).pattern();
    }

    public List<String> patterns() {
        return Collections.unmodifiableList(patterns);
    }

    /** Number of trie nodes; the automaton's memory footprint. */
    public int nodeCount() {
        return nodes.size();
    }

    private String normalize(String s) {
        return ignoreCase ? s.toLowerCase(Locale.ROOT) : s;
    }

    private char normalizeChar(char c) {
        return ignoreCase ? Character.toLowerCase(c) : c;
    }
}
