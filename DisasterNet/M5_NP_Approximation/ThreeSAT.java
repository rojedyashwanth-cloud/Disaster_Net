import java.util.ArrayList;
import java.util.List;

/**
 * 3-SAT: the three-literal restriction, and how to get there from general SAT -
 * module M5.
 *
 * <p>Use case in DisasterNet: 3-SAT is the canonical NP-complete problem, so
 * every other hard problem in this module is judged by whether it can be
 * expressed as one. Concretely, the scheduling constraints in the pipeline
 * (a district needs cover, a team has limited fuel, two teams cannot share a
 * corridor) naturally produce clauses of mixed length, and 3-SAT is the form
 * that makes the reductions and the hard cases legible.
 *
 * <p>Two things are implemented here.
 * <ul>
 *   <li><b>Recognition.</b> Whether a formula is already in 3-CNF, which
 *       includes 1-CNF and 2-CNF. Both are polynomial to solve, so knowing which
 *       case you are in is genuinely useful rather than pedantic.</li>
 *   <li><b>Tseitin conversion.</b> A clause with k literals is replaced by k - 2
 *       three-literal clauses over fresh variables, with no new assignment able
 *       to satisfy the formula that did not already satisfy the original. That
 *       makes 3-SAT exactly as hard as general SAT while staying inside the
 *       three-literal bound.</li>
 * </ul>
 *
 * <p>Complexity: conversion is O(total literals) time and space, adding at most
 * one variable per literal beyond the third in a clause. Enumerating all models
 * costs O(2^V) solutions' worth of solver calls, which is only useful for small
 * formulas.
 */
public final class ThreeSAT {

    private ThreeSAT() {
    }

    /** Whether every clause has at most three literals. */
    public static boolean isThreeCNF(SAT.Formula formula) {
        for (int[] clause : formula.clauses()) {
            if (clause.length > 3) {
                return false;
            }
        }
        return true;
    }

    /** Largest clause length, to show how far a formula is from 3-CNF. */
    public static int longestClause(SAT.Formula formula) {
        return formula.longestClause();
    }

    /**
     * Converts any CNF formula into an equisatisfiable 3-CNF formula.
     *
     * <p>A clause {@code (x1 ... xk)} with k &gt; 3 becomes k - 2 clauses over
     * fresh variables y1..y_{k-3}:
     *
     * <pre>
     * (x1  x2  y1)
     * (-y1 x3  y2)
     * (-y2 x4  y3)
     * ...
     * (-y_{k-4}  x_{k-1}  x_k)
     * </pre>
     *
     * <p>The original clause is satisfied exactly when the y's can be chosen to
     * satisfy the new clauses, so satisfiability is preserved in both
     * directions. The new variables are genuinely free, which is why the blow-up
     * lands in the variable count rather than the clause sizes.
     */
    public static SAT.Formula toThreeCNF(SAT.Formula formula) {
        List<int[]> clauses = new ArrayList<>();
        int variables = formula.variables();

        for (int[] clause : formula.clauses()) {
            if (clause.length <= 3) {
                clauses.add(clause.clone());
                continue;
            }
            int previous = clause[0];
            for (int i = 1; i < clause.length - 2; i++) {
                int fresh = variables++;
                // (previous  x_i  y). Each step consumes exactly the literal at
                // index i, so every literal of the original clause is used once:
                // sliding the window by two here would repeat one literal and
                // silently drop another.
                clauses.add(new int[] {previous, clause[i], fresh});
                // (-y  ...) seeds the next clause's first literal.
                previous = -fresh - 1;
            }
            clauses.add(new int[] {previous, clause[clause.length - 2], clause[clause.length - 1]});
        }
        return new SAT.Formula(variables, clauses);
    }

    /**
     * Solves a 3-CNF formula. Long clauses are converted first, so the caller
     * may pass general CNF and still get a correct answer.
     */
    public static SAT.Solution solve(SAT.Formula formula) {
        return SAT.dpll(isThreeCNF(formula) ? formula : toThreeCNF(formula));
    }

    /**
     * Enumerates satisfying assignments by re-solving with a blocking clause
     * added each time, which is exponential in the number of models and so only
     * sensible for reporting how constrained a small formula is.
     *
     * @param limit stop after this many models
     */
    public static List<boolean[]> allModels(SAT.Formula formula, int limit) {
        List<boolean[]> models = new ArrayList<>();
        List<int[]> working = new ArrayList<>(formula.clauses());
        int variables = formula.variables();

        while (models.size() < limit) {
            SAT.Solution solution = SAT.dpll(new SAT.Formula(variables, working));
            if (!solution.satisfiable()) {
                break;
            }
            boolean[] assignment = solution.assignment();
            models.add(assignment.clone());

            // Block exactly this assignment: at least one variable must differ.
            int[] block = new int[variables];
            for (int v = 0; v < variables; v++) {
                // Negate the chosen value, so this clause fails for this model only.
                block[v] = assignment[v] ? -(v + 1) : v;
            }
            working.add(block);
        }
        return models;
    }

    /**
     * The formula the module uses as its hard case: eight clauses over four
     * variables, one fewer than the pigeonhole principle needs, so it is
     * unsatisfiable. Dropping any single clause makes it satisfiable, which is
     * what makes it a minimal unsatisfiable instance rather than merely an
     * unsatisfiable one.
     */
    public static SAT.Formula minimalUnsatisfiable() {
        return SAT.Formula.parse(
                "x1 x2 x3 | -x1 -x2 -x3 | x1 x2 -x3 | x1 -x2 x3 | -x1 x2 x3 "
                        + "| x1 -x2 -x3 | -x1 x2 -x3 | -x1 -x2 x3");
    }
}