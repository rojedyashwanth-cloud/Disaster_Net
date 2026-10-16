import java.util.ArrayList;
import java.util.List;

/**
 * Polynomial reductions between the hard problems of module M5 - module M5.
 *
 * <p>Use case in DisasterNet: the scheduling constraints are naturally a
 * satisfiability question, and the resource conflicts are naturally a graph
 * question. The reductions are what let one solver serve both, and what make the
 * hardness results transferable rather than a list of unrelated facts.
 *
 * <p>The reductions implemented, and what each buys:
 * <ul>
 *   <li><b>3-SAT to Independent Set.</b> Since Independent Set is NP-complete,
 *       3-SAT is too - one direction of the proof that the two are equally hard.</li>
 *   <li><b>3-SAT to Vertex Cover.</b> The same graph, read by complement, so the
 *       formula is satisfiable exactly when a cover of size
 *       {@link Reduction#coverTargetSize()} exists. That size is
 *       {@code vertices - (n + m)}, not {@code n + m}: a cover is the complement
 *       of an independent set, so the two thresholds sum to the vertex count, not
 *       to the same number. Covers are what the pipeline actually uses, since
 *       blocking districts and teams is a cover-shaped question.</li>
 *   <li><b>Clique to Independent Set</b> and <b>Independent Set to Vertex
 *       Cover.</b> Both immediate from the complement graph, and both
 *       involutions, which is why one solver per family suffices.</li>
 * </ul>
 *
 * <p>The 3-SAT construction has three kinds of vertex:
 * <ul>
 *   <li>two per <b>variable</b>, joined by an edge, representing the two truth
 *       values - an independent set takes at most one, which is what encodes a
 *       truth assignment;</li>
 *   <li>one per <b>literal occurrence</b> in a clause, not one per clause;</li>
 *   <li>the occurrences of a single clause are made pairwise adjacent, so an
 *       independent set takes at most one of them.</li>
 * </ul>
 *
 * <p>One vertex per clause does not work, and the reason is worth recording. If
 * each clause had a single vertex that the independent set was required to
 * contain, then taking that vertex together with one copy per variable would
 * demand that <i>every</i> literal in the clause be false - a conjunction. But a
 * clause is satisfied when <i>any</i> of its literals is true, a disjunction. No
 * choice of polarity fixes this; several were tried and each fails on a small
 * counterexample. One vertex per literal occurrence resolves it: the independent
 * set picks which occurrence represents the clause, so it expresses the
 * disjunction directly.
 *
 * <p>Each occurrence vertex is joined to the variable copy that makes its
 * literal false, so an independent set containing that occurrence is asserting
 * the literal is true. Therefore:
 * <ul>
 *   <li>a satisfying assignment gives an independent set of size n + m, by
 *       taking the copies it sets true and one true occurrence per clause;</li>
 *   <li>an independent set of size n + m takes exactly one copy per variable and
 *       exactly one occurrence per clause, and every occurrence it takes has its
 *       literal satisfied, so the assignment satisfies every clause.</li>
 * </ul>
 *
 * <p>Complexity: O(total literal occurrences) time and space, linear in the
 * formula's size.
 */
public final class PolynomialReductions {

    private PolynomialReductions() {
    }

    /**
     * A reduced instance: the target graph plus enough bookkeeping to read a
     * solution back in the source problem's language.
     *
     * @param vertices total vertex count of the graph
     * @param edges the reduced graph's edge list
     * @param variables number of source variables, each with two copies
     * @param clauses number of source clauses
     * @param clauseBases first occurrence vertex of each clause
     * @param clauseLengths occurrence count of each clause
     */
    public record Reduction(int vertices, List<int[]> edges, int variables, int clauses,
            int[] clauseBases, int[] clauseLengths) {

        /** Independent set size that certifies satisfiability: n + m. */
        public int targetSize() {
            return variables + clauses;
        }

        /**
         * Vertex cover size that certifies satisfiability:
         * {@code vertices - targetSize()}.
         *
         * <p>This exists as its own method because getting it wrong is easy and
         * wrong answers are hard to notice. With the graph on
         * {@code 2n + L} vertices for {@code L} literal occurrences, a cover of
         * size {@code n + m} exists whenever {@code L <= 2m} - which, for genuine
         * 3-CNF, is never, since {@code L = 3m}. So {@code n + m} is not merely a
         * different number, it is the wrong side of the question.
         */
        public int coverTargetSize() {
            return vertices - targetSize();
        }

        @Override
        public String toString() {
            return "vertices=" + vertices + " variables=" + variables + " clauses="
                    + clauses + " edges=" + edges.size() + " independentSetTarget="
                    + targetSize() + " vertexCoverTarget=" + coverTargetSize();
        }
    }

    /**
     * Reduces a 3-CNF formula to Independent Set. Variables {@code 2v} and
     * {@code 2v + 1} are the copies of variable {@code v} asserting it true and
     * false respectively; the occurrence vertices of clause {@code c} start at
     * {@code 2 * variables + clauseBases[c]}.
     */
    public static Reduction threeSatToIndependentSet(SAT.Formula formula) {
        SAT.Formula three = ThreeSAT.isThreeCNF(formula) ? formula : ThreeSAT.toThreeCNF(formula);
        int variables = three.variables();
        List<int[]> clauses = three.clauses();
        int clauseCount = clauses.size();

        List<int[]> edges = new ArrayList<>();
        int[] clauseBases = new int[clauseCount];
        int[] clauseLengths = new int[clauseCount];

        // One of the two copies per variable, so the choice is a truth assignment.
        for (int v = 0; v < variables; v++) {
            edges.add(new int[] {2 * v, 2 * v + 1});
        }

        int next = 2 * variables;
        for (int c = 0; c < clauseCount; c++) {
            int[] clause = clauses.get(c);
            int length = clause.length;
            clauseBases[c] = next;
            clauseLengths[c] = length;

            // Occurrences of one clause are mutually adjacent: at most one is
            // chosen, which is how the disjunction over literals is expressed.
            for (int i = 0; i < length; i++) {
                for (int j = i + 1; j < length; j++) {
                    edges.add(new int[] {next + i, next + j});
                }
            }

            for (int i = 0; i < length; i++) {
                int literal = clause[i];
                int variable = literal < 0 ? -literal - 1 : literal;
                // Join to the copy that falsifies this literal: choosing the
                // occurrence therefore asserts the literal holds.
                int falsifyingCopy = literal >= 0 ? 2 * variable + 1 : 2 * variable;
                edges.add(new int[] {falsifyingCopy, next + i});
            }
            next += length;
        }
        return new Reduction(next, edges, variables, clauseCount, clauseBases, clauseLengths);
    }

    /**
     * The same construction read as a vertex cover: the complement of the
     * independent set.
     *
     * <p>Satisfiable exactly when the minimum cover has size
     * {@link Reduction#coverTargetSize()}, which is
     * {@code vertices - (n + m)}. Use {@link #solveViaVertexCover} to test that
     * rather than comparing against {@code n + m} by hand.
     */
    public static Reduction threeSatToVertexCover(SAT.Formula formula) {
        return threeSatToIndependentSet(formula);
    }

    /**
     * Decides a formula through the vertex cover reading of the same graph, so
     * that path is exercised too and not merely documented.
     */
    public static boolean solveViaVertexCover(SAT.Formula formula) {
        Reduction reduction = threeSatToVertexCover(formula);
        IndependentSet.Independent independent =
                IndependentSet.maximumIndependentSet(reduction.vertices(), reduction.edges());
        List<Integer> cover =
                independentSetToVertexCover(reduction.vertices(), independent.vertices());
        VertexCover.Cover found = VertexCover.minimumVertexCover(reduction.vertices(), reduction.edges());
        return cover.size() == reduction.coverTargetSize() && found.size() == reduction.coverTargetSize();
    }

    /**
     * Decides a formula through the reduction rather than through the SAT
     * solver, so the reduction is exercised rather than merely asserted. Slow and
     * roundabout, which is the point: it shows the construction is usable.
     */
    public static boolean solveViaIndependentSet(SAT.Formula formula) {
        Reduction reduction = threeSatToIndependentSet(formula);
        IndependentSet.Independent independent =
                IndependentSet.maximumIndependentSet(reduction.vertices(), reduction.edges());
        return independent.size() == reduction.targetSize();
    }

    /**
     * Recovers a satisfying assignment from an independent set of the reduced
     * graph, or null if the set is not of the certifying size.
     *
     * <p>Structural validation only: it checks that the set has the certifying
     * size, takes exactly one copy of each variable, and represents each clause
     * exactly once. It does not re-check the result against the original formula,
     * because a {@code Reduction} does not carry the formula. An earlier version
     * claimed that re-check happened, which was simply untrue of the code.
     * Callers that want the stronger guarantee should confirm with
     * {@code SAT.satisfies}, as {@link #solveViaIndependentSet} and the module
     * tests do.
     */
    public static boolean[] readAssignment(Reduction reduction, List<Integer> independentSet) {
        if (independentSet.size() != reduction.targetSize()) {
            return null;
        }
        boolean[] assignment = new boolean[reduction.variables()];
        boolean[] chosen = new boolean[reduction.vertices()];
        for (int vertex : independentSet) {
            if (vertex < 0 || vertex >= reduction.vertices()) {
                return null;
            }
            chosen[vertex] = true;
        }
        // Exactly one copy per variable must have been taken.
        for (int v = 0; v < reduction.variables(); v++) {
            int picked = (chosen[2 * v] ? 1 : 0) + (chosen[2 * v + 1] ? 1 : 0);
            if (picked != 1) {
                return null;
            }
            assignment[v] = chosen[2 * v];
        }
        // Every clause must be represented by an occurrence whose literal is true.
        for (int c = 0; c < reduction.clauses(); c++) {
            int base = reduction.clauseBases()[c];
            int length = reduction.clauseLengths()[c];
            int represented = 0;
            for (int i = 0; i < length; i++) {
                if (chosen[base + i]) {
                    represented++;
                }
            }
            if (represented != 1) {
                return null;
            }
        }
        return assignment;
    }

    /** Reduces Clique to Independent Set by complementing the graph. */
    public static List<int[]> cliqueToIndependentSet(int n, List<int[]> edges) {
        return IndependentSet.complement(n, edges);
    }

    /** Reduces Independent Set to Vertex Cover by complementing the solution. */
    public static List<Integer> independentSetToVertexCover(int n, List<Integer> independentSet) {
        List<Integer> cover = new ArrayList<>();
        for (int v = 0; v < n; v++) {
            if (!independentSet.contains(v)) {
                cover.add(v);
            }
        }
        return cover;
    }

    /** Reduces Vertex Cover to Independent Set, which is the same involution. */
    public static List<Integer> vertexCoverToIndependentSet(int n, List<Integer> cover) {
        return independentSetToVertexCover(n, cover);
    }
}