import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the three maximum-flow algorithms side by side - module M4.
 *
 * <p>Use case in DisasterNet: the report needs both an answer and a sense of
 * which algorithm earned it. On a handful of warehouses and districts every
 * method finishes instantly and the simplest one is the clearest to explain. As
 * the network grows, Ford-Fulkerson's dependence on capacity <i>values</i>
 * becomes the bottleneck, and the table makes that visible instead of leaving
 * it as folklore.
 *
 * <p>Each algorithm runs on its own copy of the network. That matters: every
 * one of them mutates the residual graph in place while searching, so sharing a
 * single instance would let the first run's flow become the second run's
 * starting point and quietly inflate the comparison.
 *
 * <p>All three must agree on the flow value. That agreement is the real check
 * on the implementation, and it holds for every input because the value is
 * uniquely determined even though the flow itself is not.
 */
public final class MaximumFlow {

    private MaximumFlow() {
    }

    /** One algorithm's result and how long it took. */
    public record Run(String algorithm, int value, long nanos, long augmentations, List<String> detail) {
        @Override
        public String toString() {
            return algorithm + " value=" + value + " augmentations=" + augmentations
                    + String.format(" micros=%d", nanos / 1000);
        }
    }

    /** All three runs plus whether they agreed. */
    public record Comparison(List<Run> runs, int value, boolean agree) {
        /** The fastest run, which is not always the most instructive one. */
        public Run fastest() {
            Run best = runs.get(0);
            for (Run run : runs) {
                if (run.nanos() < best.nanos()) {
                    best = run;
                }
            }
            return best;
        }

        @Override
        public String toString() {
            return "value=" + value + " agree=" + agree + " fastest=" + fastest().algorithm();
        }
    }

    /** Runs one named algorithm on a private copy of the template. */
    public static Run run(FlowNetwork template, String source, String sink, String algorithm) {
        FlowNetwork working = template.copy();
        long start = System.nanoTime();
        int value;
        long augmentations;
        List<String> detail;
        switch (algorithm) {
            case "FordFulkerson" -> {
                FordFulkerson.Result result = FordFulkerson.maxFlow(working, source, sink);
                value = result.value();
                augmentations = result.augmentations();
                detail = result.augmentedPaths();
            }
            case "EdmondsKarp" -> {
                EdmondsKarp.Result result = EdmondsKarp.maxFlow(working, source, sink);
                value = result.value();
                augmentations = result.augmentations();
                detail = result.augmentedPaths();
            }
            case "Dinic" -> {
                Dinic.Result result = Dinic.maxFlow(working, source, sink);
                value = result.value();
                augmentations = result.augmentations();
                detail = result.phases();
            }
            default -> throw new IllegalArgumentException("unknown algorithm: " + algorithm);
        }
        long elapsed = System.nanoTime() - start;
        return new Run(algorithm, value, elapsed, augmentations, detail);
    }

    /** Runs all three algorithms on separate copies and compares the answers. */
    public static Comparison compare(FlowNetwork template, String source, String sink) {
        List<Run> runs = new ArrayList<>();
        runs.add(run(template, source, sink, "FordFulkerson"));
        runs.add(run(template, source, sink, "EdmondsKarp"));
        runs.add(run(template, source, sink, "Dinic"));

        int value = runs.get(0).value();
        boolean agree = true;
        for (Run candidate : runs) {
            agree &= candidate.value() == value;
        }
        return new Comparison(runs, value, agree);
    }

    /**
     * Maximum flow plus the cut it implies, computed by Dinic. This is the call
     * the pipeline uses when it wants an allocation and the roads that limit it.
     */
    public static Map<String, Object> flowAndCut(FlowNetwork template, String source, String sink) {
        FlowNetwork working = template.copy();
        Dinic.Result flow = Dinic.maxFlow(working, source, sink);
        MinimumCut.Cut cut = MinimumCut.fromResidual(working, source, sink);

        Map<String, Integer> edgeFlows = new LinkedHashMap<>();
        for (int arc = 0; arc < working.arcCount(); arc += 2) {
            edgeFlows.put(working.name(working.tail(arc)) + " -> " + working.name(working.head(arc)),
                    working.flowOn(arc));
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("flow", flow.value());
        report.put("augmentations", flow.augmentations());
        report.put("phases", flow.phases());
        report.put("edgeFlows", edgeFlows);
        report.put("cut", cut);
        return report;
    }
}