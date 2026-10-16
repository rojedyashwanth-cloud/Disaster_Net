import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Suffix tree built from a suffix array and LCP array - module M2.
 *
 * <p>Use case in DisasterNet: the phrase index. Every distinct substring of
 * the concatenated report corpus is represented by exactly one root-to-node
 * path, so the tree answers "which reports contain this phrase" by walking
 * down once and collecting the suffix starts in the subtree. That makes
 * substring retrieval O(pattern length + occurrences) instead of a scan.
 *
 * <p>Construction without Ukkonen's algorithm: the suffix array lists suffixes
 * in lexicographic order, so prefixes shared by consecutive suffixes appear as
 * contiguous runs. The LCP value of each adjacent pair is precisely the depth
 * of the deepest node shared by those two paths. Walking the array while
 * maintaining a stack of open nodes at increasing depth, popping back to
 * {@code lcp[i]} and splitting an edge when {@code lcp[i]} falls strictly
 * inside it, reconstructs the tree in O(n).
 *
 * <p>Node fields: {@code depth} is the length of the string on the path from
 * the root, {@code start} is a text offset whose suffix spells that path, and
 * {@code firstChar} is the character labelling the edge from the parent, which
 * is what makes child lookup possible.
 *
 * <p>Complexity: build O(n), query O(m + output).
 */
public final class SuffixTree {

    /** A node of the suffix tree. */
    public static final class Node {
        private final int depth;
        private final int start;
        private char firstChar;
        private final Map<Character, Node> children = new HashMap<>();
        private final List<Integer> suffixStarts = new ArrayList<>();
        /** Suffixes that end exactly at this node (prefix case). */
        private final List<Integer> terminals = new ArrayList<>();
        private Node parent;

        Node(int depth, int start, char firstChar) {
            this.depth = depth;
            this.start = start;
            this.firstChar = firstChar;
        }

        /** Length of the substring spelled by the root-to-node path. */
        public int depth() {
            return depth;
        }

        /** A text offset of an occurrence of the node's path. */
        public int start() {
            return start;
        }

        /** Character labelling the edge from the parent. */
        public char firstChar() {
            return firstChar;
        }

        public Node parent() {
            return parent;
        }

        public Map<Character, Node> children() {
            return children;
        }

        /** Suffixes whose path continues past this node. */
        public List<Integer> suffixStarts() {
            return suffixStarts;
        }

        /** Suffixes that terminate at this node. */
        public List<Integer> terminals() {
            return terminals;
        }

        public boolean isLeaf() {
            return children.isEmpty();
        }

        void attach(Node child, char key) {
            children.put(key, child);
            child.firstChar = key;
            child.parent = this;
        }

        @Override
        public String toString() {
            return "Node[depth=" + depth + ", start=" + start + ", children=" + children.size() + "]";
        }
    }

    private final String text;
    private final Node root;

    private SuffixTree(String text, Node root) {
        this.text = text;
        this.root = root;
    }

    /** Builds the suffix tree of {@code text}. */
    public static SuffixTree build(String text) {
        int n = text.length();
        Node root = new Node(0, 0, '\0');
        if (n == 0) {
            return new SuffixTree(text, root);
        }

        int[] sa = SuffixArray.build(text).sa();
        int[] lcp = Kasai.compute(text, sa);

        List<Node> stack = new ArrayList<>();
        stack.add(root);

        for (int i = 0; i < n; i++) {
            int common = lcp[i];
            Node last = null;

            // Close every node deeper than the LCP with the previous suffix.
            while (stack.size() > 1 && stack.get(stack.size() - 1).depth > common) {
                last = stack.remove(stack.size() - 1);
            }

            Node top = stack.get(stack.size() - 1);
            if (top.depth < common) {
                // The shared prefix ends inside top's outgoing edge: split it.
                // Both keys are relative to the node being attached to, which
                // is not necessarily the node the child was attached to before.
                Node inner = new Node(common, last.start, '\0');
                top.attach(inner, text.charAt(last.start + top.depth));
                inner.attach(last, text.charAt(last.start + common));
                stack.add(inner);
                top = inner;
            }

            int suffixStart = sa[i];
            int leafDepth = n - suffixStart;
            if (top.depth == leafDepth) {
                // The whole remaining path is already present: this suffix is
                // a prefix of the previous one (e.g. "a" inside "aa").
                top.terminals.add(suffixStart);
                continue;
            }
            char key = text.charAt(suffixStart + top.depth);
            Node leaf = new Node(leafDepth, suffixStart, '\0');
            leaf.suffixStarts.add(suffixStart);
            top.attach(leaf, key);
            stack.add(leaf);
        }
        return new SuffixTree(text, root);
    }

    public Node root() {
        return root;
    }

    public String text() {
        return text;
    }

    /** Total nodes, internal plus leaves. */
    public int nodeCount() {
        return count(root);
    }

    private int count(Node node) {
        int total = 1;
        for (Node child : node.children.values()) {
            total += count(child);
        }
        return total;
    }

    /** Longest root-to-node path. */
    public int maxDepth() {
        return maxDepth(root);
    }

    private int maxDepth(Node node) {
        int best = node.depth;
        for (Node child : node.children.values()) {
            best = Math.max(best, maxDepth(child));
        }
        return best;
    }

    /**
     * Node whose subtree is exactly the set of suffixes starting with
     * {@code pattern}, or null if the pattern does not occur.
     *
     * <p>Path compression makes this less obvious than a plain trie descent.
     * One edge can cover many pattern characters, so each node's path is
     * verified character by character against {@code start + i} before
     * descending: matching "aa" against the path "ab" must fail even though
     * an 'a' child exists. And among the nodes that cover the pattern we want
     * the shallowest, because its subtree is the largest: for "ana" in
     * "banana" the depth-5 node ("anana") is a strict subset of the depth-3
     * node ("ana"), and only the latter reports both occurrences.
     */
    public Node find(String pattern) {
        int m = pattern.length();
        if (m == 0) {
            return root;
        }
        Node node = root;
        int consumed = 0;
        while (consumed < m) {
            int limit = Math.min(node.depth, m);
            while (consumed < limit && text.charAt(node.start + consumed) == pattern.charAt(consumed)) {
                consumed++;
            }
            if (consumed < limit) {
                return null;                    // mismatch inside this edge
            }
            if (node.depth >= m) {
                break;                          // pattern ends inside this node
            }
            Node next = node.children.get(pattern.charAt(consumed));
            if (next == null) {
                return null;
            }
            node = next;
        }
        while (node.parent() != null && node.parent().depth >= m) {
            node = node.parent();
        }
        return node;
    }

    /**
     * All text offsets at which {@code pattern} occurs. Combines the suffix
     * starts of the whole subtree with the terminals of the matched node
     * (the pattern being a prefix of longer suffixes).
     */
    public List<Integer> occurrencesOf(String pattern) {
        Node node = find(pattern);
        if (node == null) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        collect(node, out);
        out.sort(Integer::compareTo);
        return out;
    }

    private void collect(Node node, List<Integer> out) {
        out.addAll(node.terminals());
        for (int start : node.suffixStarts) {
            out.add(start);
        }
        for (Node child : node.children.values()) {
            collect(child, out);
        }
    }

    /**
     * Longest prefix of {@code pattern} that occurs in the indexed text.
     *
     * <p>Shares the walk with {@link #find}: characters are verified against
     * the current node's whole path before descending, so a pattern that dies
     * in the middle of an edge is handled correctly.
     */
    public String longestCommonSubstring(String pattern) {
        int m = pattern.length();
        Node node = root;
        int consumed = 0;
        while (consumed < m) {
            int limit = Math.min(node.depth, m);
            while (consumed < limit && text.charAt(node.start + consumed) == pattern.charAt(consumed)) {
                consumed++;
            }
            if (consumed < limit || node.depth >= m) {
                break;
            }
            Node next = node.children.get(pattern.charAt(consumed));
            if (next == null) {
                break;
            }
            node = next;
        }
        return pattern.substring(0, consumed);
    }

    /** Number of distinct substrings of the indexed text. */
    public long distinctSubstringCount() {
        long total = 0;
        total += sumSubstrings(root);
        return total;
    }

    private long sumSubstrings(Node node) {
        long total = 0;
        for (Node child : node.children.values()) {
            total += (long) (child.depth - node.depth) * (child.suffixStarts.size()
                    + child.terminals.size());
            total += sumSubstrings(child);
        }
        return total;
    }
}
