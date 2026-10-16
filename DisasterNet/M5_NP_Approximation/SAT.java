import java.util.ArrayList;
import java.util.List;

/**
 * Boolean satisfiability by DPLL - module M5.
 *
 * <p>Use case in DisasterNet: feasibility questions that have to be answered
 * exactly, where a greedy heuristic is not good enough. "Can these constraints
 * all hold at once?" - a district must be reachable, a team must have fuel
 * remaining, no corridor may be double-booked, certain teams are paired with
 * certain districts. Written as clauses over booleans, that is SAT, and DPLL is
 * the reference solver the rest of the module is measured against.
 *
 * <p>SAT is NP-complete, so nothing here is a guarantee of speed. What the
 * solver buys is completeness: it either produces an assignment that can be
 * checked directly, or proves that none exists. The practical machinery is all
 * about refusing to branch when a decision is already forced.
 * <ul>
 *   <li><b>Unit propagation.</b> A clause with one literal still unassigned and
 *       all others false forces that literal. Iterating to a fixed point is the
 *       cheapest inference there is.</li>
 *   <li><b>Branch on the most constrained variable.</b> Which variable to split
 *       on changes nothing about correctness and everything about runtime, so
 *       pick the one appearing in the most unsatisfied clauses.</li>
 * </ul>
 *
 * <p>Complexity: exponential in the worst case, which is what NP-completeness
 * means. Each recursive call costs O(C * L) to simplify, with a branching factor
 * of 2, giving O(2^V * C * L).
 */
public final class SAT {

    private SAT() {
    }

    /** One literal: a variable index, possibly negated. Variables are 0-based. */
    public record Literal(int variable, boolean negated) {

        /** The same variable with the opposite sign. */
        public Literal negate() {
            return new Literal(variable, !negated);
        }

        /** True when this literal is satisfied by the given assignment. */
        public boolean holds(boolean[] assignment) {
            return negated != assignment[variable];
        }

        /** Text form {@code x3} or {@code -x3}, numbering variables from 1. */
        @Override
        public String toString() {
            return (negated ? "-x" : "x") + (variable + 1);
        }
    }

    /** A conjunction of clauses over a fixed set of variables. */
    public record Formula(int variables, List<int[]> clauses) {

        public Formula {
            clauses = List.copyOf(clauses);
        }

        /**
         * Parses the compact text form: clauses separated by {@code |}, literals
         * separated by whitespace, {@code -} for negation. So
         * {@code "x1 -x2 | x3"} is two clauses over three variables.
         */
        public static Formula parse(String text) {
            List<int[]> parsed = new ArrayList<>();
            int variables = 0;
            for (String group : text.split("\\|")) {
                String trimmed = group.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] tokens = trimmed.split("\\s+");
                int[] clause = new int[tokens.length];
                int at = 0;
                for (String token : tokens) {
                    boolean negated = token.startsWith("-");
                    String digits = negated ? token.substring(1) : token;
                    if (digits.isEmpty() || digits.charAt(0) != 'x') {
                        throw new IllegalArgumentException("literal must look like x1 or -x1: " + token);
                    }
                    int number = Integer.parseInt(digits.substring(1));
                    if (number < 1) {
                        throw new IllegalArgumentException("variables are numbered from 1: " + token);
                    }
                    // Positive literals are stored as the bare variable index,
                    // negative ones as -(index + 1), so that index 0 can be
                    // negated without colliding with the encoding.
                    clause[at++] = negated ? -number : number - 1;
                    variables = Math.max(variables, number);
                }
                parsed.add(clause);
            }
            return new Formula(variables, parsed);
        }

        /** Builds a formula from clauses given as signed variable indices. */
        public static Formula of(int variables, List<int[]> clauses) {
            return new Formula(variables, clauses);
        }

        /** A clause with no literals can never be satisfied. */
        public boolean hasEmptyClause() {
            for (int[] clause : clauses) {
                if (clause.length == 0) {
                    return true;
                }
            }
            return false;
        }

        /** Longest clause, which bounds the work of one simplification pass. */
        public int longestClause() {
            int longest = 0;
            for (int[] clause : clauses) {
                longest = Math.max(longest, clause.length);
            }
            return longest;
        }

        @Override
        public String toString() {
            return clauseList(clauses);
        }
    }

    /** The outcome of a solve: an assignment, a refutation, and the work done. */
    public record Solution(boolean satisfiable, boolean[] assignment, int decisions,
            List<String> trace) {

        /** Renders the satisfying assignment, for reports. */
        public String describe() {
            if (!satisfiable) {
                return "unsatisfiable";
            }
            StringBuilder sb = new StringBuilder();
            for (int v = 0; v < assignment.length; v++) {
                if (v > 0) {
                    sb.append(' ');
                }
                sb.append('x').append(v + 1).append('=').append(assignment[v]);
            }
            return sb.toString();
        }
    }

    private static final int UNASSIGNED = -1;

    /**
     * Solves a formula with DPLL, returning either a satisfying assignment or a
     * proof that none exists. The assignment is non-null exactly when
     * {@code satisfiable} is true.
     */
    public static Solution dpll(Formula formula) {
        if (formula.hasEmptyClause()) {
            return new Solution(false, null, 0, List.of("empty clause: unsatisfiable"));
        }
        int[] value = new int[formula.variables()];
        java.util.Arrays.fill(value, UNASSIGNED);
        List<String> trace = new ArrayList<>();
        int[] decisions = {0};
        List<Integer> trail = new ArrayList<>();
        boolean solved = search(formula, value, decisions, trace, trail, 0);

        boolean[] assignment = null;
        if (solved) {
            assignment = new boolean[formula.variables()];
            for (int v = 0; v < value.length; v++) {
                assignment[v] = value[v] == 1;
            }
        }
        return new Solution(solved, assignment, decisions[0], trace);
    }

    /**
     * One recursive step: propagate as far as possible, detect a conflict, then
     * branch. {@code value} is the partial assignment, with -1 for undecided, and
     * doubles as the undo log - a failed branch clears exactly what it set.
     */
    private static boolean search(Formula formula, int[] value, int[] decisions,
            List<String> trace, List<Integer> trail, int depth) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int[] clause : formula.clauses()) {
                boolean satisfied = false;
                int unassignedCount = 0;
                int forced = 0;
                for (int literal : clause) {
                    int variable = literal < 0 ? -literal - 1 : literal;
                    if (value[variable] != UNASSIGNED) {
                        boolean holds = (literal >= 0) == (value[variable] == 1);
                        if (holds) {
                            satisfied = true;
                            break;
                        }
                    } else {
                        unassignedCount++;
                        forced = literal;
                    }
                }
                if (satisfied) {
                    continue;
                }
                if (unassignedCount == 0) {
                    trace.add(indent(depth) + "conflict on " + clauseText(clause));
                    return false;
                }
                if (unassignedCount == 1) {
                    int variable = forced < 0 ? -forced - 1 : forced;
                    assign(variable, forced >= 0 ? 1 : 0, value, trail);
                    trace.add(indent(depth) + "propagate " + clauseText(clause)
                            + " -> x" + (variable + 1) + "=" + (value[variable] == 1));
                    changed = true;
                }
            }
        }

        // Branch on the undecided variable appearing in the most clauses.
        int[] occurrences = new int[formula.variables()];
        for (int[] clause : formula.clauses()) {
            for (int literal : clause) {
                int variable = literal < 0 ? -literal - 1 : literal;
                if (value[variable] == UNASSIGNED) {
                    occurrences[variable]++;
                }
            }
        }
        int branch = -1;
        int best = -1;
        for (int v = 0; v < occurrences.length; v++) {
            if (value[v] == UNASSIGNED && occurrences[v] > best) {
                best = occurrences[v];
                branch = v;
            }
        }
        if (branch < 0) {
            return true;
        }

        for (int choice = 0; choice <= 1; choice++) {
            int mark = trail.size();
            assign(branch, choice, value, trail);
            decisions[0]++;
            trace.add(indent(depth) + "decide x" + (branch + 1) + "=" + (choice == 1));
            if (search(formula, value, decisions, trace, trail, depth + 1)) {
                return true;
            }
            // Undo the decision *and* everything propagation deduced beneath
            // it. Clearing only the branch variable would leave inferred values
            // behind and make the sibling branch explore a state that no
            // assignment can produce.
            undoTo(trail, value, mark);
        }
        return false;
    }

    /** Sets a variable and records it so the assignment can be rolled back. */
    private static void assign(int variable, int choice, int[] value, List<Integer> trail) {
        value[variable] = choice;
        trail.add(variable);
    }

    /** Returns every variable assigned since {@code mark} to undecided. */
    private static void undoTo(List<Integer> trail, int[] value, int mark) {
        while (trail.size() > mark) {
            value[trail.remove(trail.size() - 1)] = UNASSIGNED;
        }
    }

    /**
     * Brute-force satisfiability by trying all 2^V assignments. Far too slow to
     * use, but it is the definition, so the test suite uses it to check the
     * solver rather than trusting the solver's own verdict.
     *
     * <p>Complexity: O(2^V * C * L) time, O(V) space.
     */
    public static boolean bruteForce(Formula formula) {
        for (long bits = 0; bits < (1L << formula.variables()); bits++) {
            boolean[] assignment = new boolean[formula.variables()];
            for (int v = 0; v < formula.variables(); v++) {
                assignment[v] = (bits >> v & 1) == 1;
            }
            if (satisfies(formula, assignment)) {
                return true;
            }
        }
        return false;
    }

    /** Whether an assignment satisfies every clause. */
    public static boolean satisfies(Formula formula, boolean[] assignment) {
        for (int[] clause : formula.clauses()) {
            boolean satisfied = false;
            for (int literal : clause) {
                int variable = literal < 0 ? -literal - 1 : literal;
                if (variable < assignment.length && (literal >= 0) == assignment[variable]) {
                    satisfied = true;
                    break;
                }
            }
            if (!satisfied) {
                return false;
            }
        }
        return true;
    }

    private static String indent(int depth) {
        return "  ".repeat(depth);
    }

    private static String clauseText(int[] clause) {
        return clauseList(List.of(clause));
    }

    private static String clauseList(List<int[]> clauses) {
        StringBuilder sb = new StringBuilder();
        for (int[] clause : clauses) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            for (int i = 0; i < clause.length; i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                // A positive literal is stored as the variable index itself and a
                // negative one as -(index + 1), so the two need different offsets
                // to name the same variable.
                sb.append(clause[i] < 0 ? "-x" : "x")
                        .append(clause[i] < 0 ? -clause[i] : clause[i] + 1);
            }
        }
        return sb.toString();
    }
}