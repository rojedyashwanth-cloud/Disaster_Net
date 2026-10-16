import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Koenig's theorem: in a bipartite graph the minimum vertex cover and the
 * maximum matching have equal size, and a minimum cover can be read off a
 * maximum matching - module M4.
 *
 * <p>Use case in DisasterNet: the matching says how many teams can be deployed.
 * The cover answers the different question operations actually asks: which is
 * the smallest set of teams <i>and</i> districts to lock down so that every
 * remaining team-to-district possibility is blocked. For bipartite graphs the
 * answer is exactly as large as the matching, which makes it cheap to certify
 * that no smaller lockdown exists.
 *
 * <p>The construction runs on alternating paths. Start from every unmatched left
 * vertex and follow <i>unmatched</i> edges left to right and <i>matched</i> edges
 * right to left. Let Z_L and Z_R be the left and right vertices reached. Then
 *
 * <pre>(L \ Z_L) union Z_R</pre>
 *
 * is a minimum vertex cover.
 *
 * <p>Why it covers: an edge from L \ Z_L to Z_R would be an unmatched edge out of
 * a reachable left vertex, so its right endpoint would be reachable too, a
 * contradiction; and a matched edge can only leave Z_L for a right endpoint that
 * is reached, again by reachability. Why it is minimum: the cover has one vertex
 * per matched edge and none for the rest, so its size equals the maximum
 * matching, and no cover can be smaller.
 *
 * <p>Complexity: O(V + E) after the matching is known, so the whole computation
 * is dominated by finding the maximum matching.
 */
public final class KonigTheorem {

    private KonigTheorem() {
    }

    /** A vertex cover together with the matching that certifies its size. */
    public record Cover(Set<String> vertices, int size, int matchingSize) {
        /** True when the cover really does touch every edge. */
        public boolean optimal() {
            return size == matchingSize;
        }

        @Override
        public String toString() {
            return "size=" + size + " matchingSize=" + matchingSize + " " + vertices;
        }
    }

    /**
     * Builds a minimum vertex cover from a maximum matching of the same graph.
     *
     * @param left  left-hand vertex names
     * @param right right-hand vertex names
     * @param edges the bipartite edges as {@code [left, right]}
     */
    public static Cover minVertexCover(List<String> left, List<String> right, List<String[]> edges) {
        BipartiteMatching.Matching matching =
                BipartiteMatching.hopcroftKarp(left, right, edges);

        Set<String> reachableLeft = new LinkedHashSet<>();
        Set<String> reachableRight = new LinkedHashSet<>();

        // Only unmatched edges may be followed left to right, which is what
        // keeps the walk alternating.
        java.util.Map<String, String> partnerOfDistrict = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, String> entry : matching.pairs().entrySet()) {
            partnerOfDistrict.put(entry.getValue(), entry.getKey());
        }

        Deque<String> queue = new ArrayDeque<>();
        for (String team : left) {
            if (!matching.pairs().containsKey(team)) {
                reachableLeft.add(team);
                queue.add(team);
            }
        }
        while (!queue.isEmpty()) {
            String team = queue.poll();
            String matchedDistrict = matching.pairs().get(team);
            for (String[] edge : edges) {
                if (!edge[0].equals(team) || edge[1].equals(matchedDistrict)) {
                    continue;
                }
                if (!reachableRight.add(edge[1])) {
                    continue;
                }
                String partner = partnerOfDistrict.get(edge[1]);
                if (partner != null && reachableLeft.add(partner)) {
                    queue.add(partner);
                }
            }
        }

        Set<String> cover = new LinkedHashSet<>();
        for (String team : left) {
            if (!reachableLeft.contains(team)) {
                cover.add(team);
            }
        }
        cover.addAll(reachableRight);
        return new Cover(cover, cover.size(), matching.size());
    }

    /**
     * Checks a candidate cover directly. Useful as an independent audit of the
     * constructed cover: every edge must have at least one endpoint inside it.
     */
    public static boolean covers(List<String[]> edges, Set<String> cover) {
        for (String[] edge : edges) {
            if (!cover.contains(edge[0]) && !cover.contains(edge[1])) {
                return false;
            }
        }
        return true;
    }

    /** True when no two chosen vertices share an edge, i.e. the cover is a matching. */
    public static boolean isIndependent(List<String[]> edges, Set<String> chosen) {
        for (String[] edge : edges) {
            if (chosen.contains(edge[0]) && chosen.contains(edge[1])) {
                return false;
            }
        }
        return true;
    }

    /**
     * The complementary maximum independent set: every vertex outside a minimum
     * cover, which in a bipartite graph is both maximum and a complement of the
     * cover. Relief teams that can stay uncommitted, in other words.
     */
    public static Set<String> complementIndependentSet(List<String> all, Cover cover) {
        Set<String> independent = new LinkedHashSet<>(all);
        independent.removeAll(cover.vertices());
        return independent;
    }
}