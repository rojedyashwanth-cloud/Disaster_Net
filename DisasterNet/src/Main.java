/*
 * DisasterNet pipeline.
 *
 * Runs the six algorithm modules over the data directory and writes the results
 * to output/. Each stage is a separate method and each one names the module it
 * exercises, because the point of the project is that the modules are used, not
 * merely present:
 *
 *   stage 1  M1  text triage      Aho-Corasick hazard keyword scan, KMP phrase
 *                                 counts, Rabin-Karp duplicate narratives,
 *                                 Z common prefixes between reports
 *   stage 2  M2  phrase indexing  suffix array and Kasai LCP for boilerplate
 *                                 detection, suffix tree and suffix automaton
 *                                 for shared phrasing
 *   stage 3  M3  fuzzy resolution  Damerau-Levenshtein and weighted edit distance
 *                                 to repair field-typed incident codes, bin
 *                                 packing for convoy counts, held-Karp tour
 *   stage 4  M4  relief logistics  max flow per commodity with all three
 *                                 algorithms cross-checked, minimum cut for the
 *                                 corridors to close, bipartite matching for
 *                                 team placement, Koenig for the cover
 *   stage 5  M5  hardness          the team placement problem compiled to 3-SAT
 *                                 and solved by DPLL, cross-checked against the
 *                                 matching from stage 4 and against the
 *                                 independent-set and vertex-cover readings of
 *                                 the same reduction; 2-approximation for the
 *                                 team conflict cover
 *   stage 6  M6  telemetry        Blelloch scans over sensor levels, Fork/Join
 *                                 reductions, randomized quicksort, reservoir
 *                                 sampling for audit, Miller-Rabin for depot
 *                                 keys, Brent cycle detection on the legacy
 *                                 code generator
 *
 * Two stage-5 results are worth calling out because they are the ones a reader
 * should be most sceptical of. The SAT encoding of matching and the matching
 * solver are independent implementations of the same question, so their agreeing
 * on the optimum is evidence, not tautology. And the minimum cut is read off a
 * maximum flow, so it is only optimal if the flow was maximum; stage 4 checks
 * that all three flow algorithms agree before trusting the cut.
 */
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

public final class Main {

    private static final String SOURCE = "SRC";
    private static final String SINK = "SINK";

    private final OutputHandler out;

    Main(OutputHandler out) {
        this.out = out;
    }

    public static void main(String[] args) throws IOException {
        boolean pipeline = false;
        List<String> positional = new ArrayList<>();

        for (String arg : args) {
            if ("--pipeline".equals(arg) || "--auto".equals(arg)) {
                pipeline = true;
            } else {
                positional.add(arg);
            }
        }

        Path dataArg = Path.of(positional.size() > 0 ? positional.get(0) : "data");
        Path outArg = Path.of(positional.size() > 1 ? positional.get(1) : "output");

        Path dataDir = resolveDataDir(dataArg);
        Path outputDir = outArg.isAbsolute()
                ? outArg
                : dataDir.resolveSibling(outArg.getFileName().toString());

        if (pipeline) {
            runPipeline(dataDir, outputDir);
        } else {
            new Menu(dataDir, outputDir).run();
        }
    }

    /** Runs all six stages end to end, writing every artifact under output/. */
    static void runPipeline(Path dataDir, Path outputDir) throws IOException {
        OutputHandler out = new OutputHandler(outputDir);
        Main main = new Main(out);

        out.section("DisasterNet");
        InputHandler.Dataset dataset = InputHandler.loadOrFail(dataDir.toString());
        out.detail("input", dataDir);
        out.detail("loaded", InputHandler.describe(dataset));
        if (dataset.warnings().isEmpty()) {
            out.detail("data quality", "no warnings");
        } else {
            out.detail("data quality", dataset.warnings().size() + " warning(s)");
            for (String warning : dataset.warnings()) {
                out.line("    ! " + warning);
            }
        }

        main.stage1TextTriage(dataset);
        main.stage2PhraseIndexing(dataset);
        main.stage3FuzzyResolution(dataset, dataDir);
        main.stage4ReliefLogistics(dataset);
        main.stage5Hardness(dataset);
        main.stage6Telemetry(dataset);

        out.section("Run complete");
        out.writeTranscript();
        out.line("artifacts written under " + outputDir.toAbsolutePath());
    }

    /**
     * Locates the data directory even when the working directory is not the
     * project root (for example when the program is launched from an IDE whose
     * run configuration leaves the working directory at the repository root).
     * An absolute path is honoured as given; a relative path is first tried
     * against the working directory and then against every ancestor, checking
     * both {@code <dir>/<name>} and {@code <dir>/DisasterNet/<name>}.
     */
    private static Path resolveDataDir(Path name) {
        if (name.isAbsolute()) {
            return name;
        }
        if (isDataDir(name)) {
            return name.toAbsolutePath().normalize();
        }
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path direct = dir.resolve(name);
            if (isDataDir(direct)) {
                return direct.normalize();
            }
            Path nested = dir.resolve("DisasterNet").resolve(name);
            if (isDataDir(nested)) {
                return nested.normalize();
            }
        }
        return name;
    }

    private static boolean isDataDir(Path dir) {
        return Files.isRegularFile(dir.resolve("incident_codes/codes.txt"))
                || Files.isDirectory(dir.resolve("incident_codes"));
    }

    // ---------------------------------------------------------------- stage 1

    /**
     * M1. The registry's keywords are the vocabulary a report is expected to use,
     * so an Aho-Corasick scan over the whole registry says which codes a report
     * looks like, and disagreement with the code declared in the report header is
     * a triage flag rather than an error. Rabin-Karp then finds narratives that
     * are literally duplicated across reports, which is the signature of a
     * template that has been filled in twice.
     */
    void stage1TextTriage(InputHandler.Dataset dataset) throws IOException {
        out.section("Stage 1 - text triage (M1: KMP, Z, Rabin-Karp, Aho-Corasick)");

        Map<String, InputHandler.Code> registry = dataset.codesByCode();
        List<String> patterns = new ArrayList<>();
        for (InputHandler.Code code : dataset.codes()) {
            patterns.addAll(code.keywords());
        }
        AhoCorasick automaton = new AhoCorasick(patterns, true);

        out.detail("registry codes", dataset.codes().size());
        out.detail("distinct keywords", patterns.size());
        out.detail("automaton nodes", automaton.nodeCount());

        List<List<String>> rows = new ArrayList<>();
        List<String> mismatches = new ArrayList<>();
        for (InputHandler.Report report : dataset.reports()) {
            Map<String, Integer> hits = automaton.hitCounts(report.text());
            List<String> suggested = new ArrayList<>();
            for (InputHandler.Code code : dataset.codes()) {
                for (String keyword : code.keywords()) {
                    Integer count = hits.get(keyword.toLowerCase(Locale.ROOT));
                    if (count != null && count > 0) {
                        suggested.add(code.code() + "(" + keyword + "x" + count + ")");
                    }
                }
            }
            String declared = report.incidentCode();
            boolean agrees = suggested.stream().anyMatch(s -> s.startsWith(declared + "("));
            if (!agrees) {
                mismatches.add(report.reportId() + " declares " + declared + " but its keywords point at "
                        + (suggested.isEmpty() ? "nothing in the registry" : suggested.toString()));
            }
            rows.add(List.of(report.reportId(), report.hazardClass(), declared,
                    agrees ? "agrees" : "REVIEW", String.valueOf(hits.values().stream().mapToInt(Integer::intValue).sum()),
                    summariseKeywords(suggested)));
        }
        out.printTable(List.of("report", "hazard", "declared", "verdict", "keyword hits", "matched keywords"), rows);

        out.detail("keyword verdict mismatches", mismatches.size());
        for (String mismatch : mismatches) {
            out.line("    ! " + mismatch);
        }

        // KMP: fixed phrases an operations cell would search for by hand.
        String[] phrases = {"evacuate", "rescue", "shelter", "contamination", "breach"};
        out.line("  KMP phrase occurrences (overlapping search, non-overlapping count):");
        List<List<String>> phraseRows = new ArrayList<>();
        for (String phrase : phrases) {
            List<String> row = new ArrayList<>();
            row.add(phrase);
            for (InputHandler.Report report : dataset.reports()) {
                row.add(String.valueOf(
                        KMP.searchAll(report.text().toLowerCase(Locale.ROOT), phrase).size()));
            }
            phraseRows.add(row);
        }
        List<String> phraseHeader = new ArrayList<>(List.of("phrase"));
        for (InputHandler.Report report : dataset.reports()) {
            phraseHeader.add(report.reportId().replace("RPT-2024-", ""));
        }
        out.printTable(phraseHeader, phraseRows);

        int longest = 0;
        for (String phrase : phrases) {
            longest = Math.max(longest, phrase.length());
        }
        out.detail("longestMatch over the phrase list", KMP.longestMatch(
                "evac then evacuate the shelter", List.of(phrases)) + " (longest keyword is "
                + longest + " characters)");

        // Rabin-Karp: duplicated narratives across reports.
        Map<Long, List<String>> duplicates = RabinKarp.findDuplicates(
                dataset.reports().stream().map(InputHandler.Report::body).toList());
        out.detail("duplicate narrative groups", duplicates.size());
        for (Map.Entry<Long, List<String>> entry : duplicates.entrySet()) {
            out.line("    group " + Long.toHexString(entry.getKey()) + " holds " + entry.getValue().size()
                    + " identical narratives");
        }
        RabinKarp hasher = new RabinKarp();
        List<Long> fingerprints = dataset.reports().stream().map(r -> hasher.fingerprint(r.text())).toList();
        out.detail("distinct report fingerprints", new LinkedHashSet<>(fingerprints).size() + " of " + fingerprints.size());

        // Z: how similar two reports are at their start, which is cheap and catches
        // reports that were copied from the same template.
        out.line("  Z longest common prefix between report bodies:");
        for (int i = 0; i < dataset.reports().size(); i++) {
            for (int j = i + 1; j < dataset.reports().size(); j++) {
                int lcp = ZAlgorithm.longestCommonPrefix(dataset.reports().get(i).body(),
                        dataset.reports().get(j).body());
                if (lcp >= 20) {
                    out.line("    " + dataset.reports().get(i).reportId() + " vs "
                            + dataset.reports().get(j).reportId() + ": prefix of " + lcp + " characters");
                }
            }
        }

        List<String> artifact = new ArrayList<>();
        artifact.add("report_id,hazard,declared_code,verdict,keyword_hits");
        for (List<String> row : rows) {
            artifact.add(String.join(",", row.subList(0, 5)));
        }
        artifact.add("");
        artifact.add("# keyword matches per report, comma separated");
        for (int i = 0; i < rows.size(); i++) {
            artifact.add(dataset.reports().get(i).reportId() + "," + rows.get(i).get(5));
        }
        out.write("search_results/triage.csv", artifact);
        out.detail("written", "search_results/triage.csv");
    }

    private static String summariseKeywords(List<String> suggested) {
        if (suggested.isEmpty()) {
            return "-";
        }
        return suggested.size() <= 3 ? String.join(" ", suggested)
                : String.join(" ", suggested.subList(0, 3)) + " +" + (suggested.size() - 3) + " more";
    }

    // ---------------------------------------------------------------- stage 2

    /**
     * M2. Phrases that repeat inside a report are template text; phrases that
     * repeat across reports are shared doctrine. Both are found by grouping
     * suffixes and reading runs of equal LCP values, which is what Kasai's
     * structure is for. The suffix automaton then answers occurrence counts for
     * the hazard vocabulary without rescanning, and the suffix tree supplies the
     * longest phrase two reports have in common.
     */
    void stage2PhraseIndexing(InputHandler.Dataset dataset) throws IOException {
        out.section("Stage 2 - phrase indexing (M2: suffix array, SA-IS, Kasai, suffix tree, suffix automaton)");

        List<List<String>> rows = new ArrayList<>();
        for (InputHandler.Report report : dataset.reports()) {
            String body = report.body();
            SuffixArray array = SuffixArray.build(body);
            int[] naive = SuffixArray.naive(body);
            boolean agrees = array.sa().length == naive.length;
            if (agrees) {
                for (int i = 0; i < naive.length; i++) {
                    agrees &= naive[i] == array.sa()[i];
                }
            }
            int[] lcp = Kasai.compute(body, array.sa());
            List<LCPArray.Repeated> repeated = Kasai.repeatedSubstrings(body, 25);
            SuffixAutomaton automaton = SuffixAutomaton.of(body);
            String longestRepeat = repeated.isEmpty() ? "-" : repeated.get(0).substring(body);
            rows.add(List.of(report.reportId(), String.valueOf(body.length()), String.valueOf(array.sa().length),
                    agrees ? "yes" : "NO", String.valueOf(maxOf(lcp)),
                    String.valueOf(repeated.size()), trim(longestRepeat), String.valueOf(automaton.stateCount())));
        }
        out.printTable(List.of("report", "chars", "suffixes", "sa matches naive", "max LCP",
                "repeats >= 25", "longest repeat", "SAM states"), rows);

        out.line("  repeated boilerplate phrases of at least 25 characters:");
        List<String> artifact = new ArrayList<>(List.of("# report_id,phrase_length,occurrences,phrase"));
        for (InputHandler.Report report : dataset.reports()) {
            List<LCPArray.Repeated> repeated = Kasai.repeatedSubstrings(report.body(), 25);
            repeated.sort(Comparator.comparingInt(LCPArray.Repeated::length).reversed());
            for (LCPArray.Repeated r : repeated.subList(0, Math.min(3, repeated.size()))) {
                String phrase = r.substring(report.body()).replaceAll("\\s+", " ").strip();
                out.line("    " + report.reportId() + " x" + r.occurrences() + " (" + r.length() + " chars): " + trim(phrase));
                artifact.add(report.reportId() + "," + r.length() + "," + r.occurrences() + ",\"" + phrase + "\"");
            }
        }

        // Suffix tree: the longest phrase shared by each pair of reports.
        out.line("  longest common phrase between report pairs (suffix tree):");
        for (int i = 0; i < dataset.reports().size(); i++) {
            for (int j = i + 1; j < dataset.reports().size(); j++) {
                String a = dataset.reports().get(i).body();
                String shared = SuffixTree.build(a).longestCommonSubstring(dataset.reports().get(j).body());
                if (shared.length() >= 20) {
                    out.line("    " + dataset.reports().get(i).reportId() + " / "
                            + dataset.reports().get(j).reportId() + ": \"" + trim(shared) + "\"");
                    artifact.add("# pair " + dataset.reports().get(i).reportId() + "/" + dataset.reports().get(j).reportId()
                            + " shared=" + shared.length());
                }
            }
        }

        // Suffix automaton: occurrence counts for the registry vocabulary.
        out.line("  registry keyword frequencies inside report bodies (suffix automaton):");
        Map<String, Integer> vocabulary = new LinkedHashMap<>();
        for (InputHandler.Code code : dataset.codes()) {
            for (String keyword : code.keywords()) {
                vocabulary.merge(keyword.toLowerCase(Locale.ROOT), 0, (a, b) -> a);
            }
        }
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (InputHandler.Report report : dataset.reports()) {
            SuffixAutomaton automaton = SuffixAutomaton.of(report.body());
            for (String keyword : vocabulary.keySet()) {
                int occurrences = automaton.occurrencesOf(keyword);
                if (occurrences > 0) {
                    totals.merge(keyword, occurrences, Integer::sum);
                    vocabulary.merge(keyword, 1, (a, b) -> b + 1);
                }
            }
        }
        List<List<String>> freqRows = new ArrayList<>();
        totals.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(12)
                .forEach(e -> freqRows.add(List.of(e.getKey(), String.valueOf(e.getValue()),
                        String.valueOf(vocabulary.getOrDefault(e.getKey(), 0)))));
        out.printTable(List.of("keyword", "occurrences", "reports containing it"), freqRows);

        artifact.add("");
        artifact.add("# keyword,occurrences,reports");
        totals.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> artifact.add(e.getKey() + "," + e.getValue() + ","
                        + vocabulary.getOrDefault(e.getKey(), 0)));
        out.write("indexing_results/phrase_index.txt", artifact);
        out.detail("written", "indexing_results/phrase_index.txt");
    }

    private static String describeCycle(BrentAlgorithm.CycleResult cycle) {
        return cycle.found()
                ? "cycle length " + cycle.length() + " after index " + cycle.index() + " in " + cycle.steps()
                        + " steps"
                : "no cycle within the limit";
    }

    private static int maxOf(int[] values) {
        int max = 0;
        for (int value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    private static String trim(String text) {
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= 58 ? flat : flat.substring(0, 55) + "...";
    }

    // ---------------------------------------------------------------- stage 3

    /**
     * M3. Codes arriving over the radio are transcribed badly, so each one is
     * matched to the registry under both an unrestricted Damerau-Levenshtein
     * distance and a weighted model that treats field substitutions differently
     * from field gaps. When the two disagree the repair is reported as uncertain
     * rather than silently accepted, because a wrong code sends the wrong
     * response.
     */
    void stage3FuzzyResolution(InputHandler.Dataset dataset, Path dataDir) throws IOException {
        Path noisyCodeFeed = dataDir.resolve("incident_codes/noisy_codes.txt");
        if (!Files.isReadable(noisyCodeFeed)) {
            out.line("  no noisy code feed at " + noisyCodeFeed + ", skipping repair");
            return;
        }
        out.section("Stage 3 - fuzzy resolution (M3: Damerau-Levenshtein, weighted edit distance, bitmask DP)");

        List<String> registryCodes = dataset.codes().stream().map(InputHandler.Code::code).toList();
        List<String> descriptions = dataset.codes().stream().map(InputHandler.Code::description).toList();

        List<List<String>> rows = new ArrayList<>();
        int agreed = 0;
        int uncertain = 0;
        List<String> artifact = new ArrayList<>(List.of("# noisy_code,hazard_class,description,unrestricted,weighted,repair,verdict"));
        WeightedEditDistance.Costs costs = WeightedEditDistance.fieldTranscriptionCosts(4);

        for (String line : Files.readAllLines(noisyCodeFeed)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\\|", -1);
            String noisy = parts[0].strip();
            String declaredHazard = parts[1].strip();
            String messyText = parts.length > 2 ? parts[2].strip() : "";

            String nearestCode = DamerauLevenshtein.nearest(noisy, registryCodes, 4);
            // A second, independent read on the same field: the code text points at a
            // code, the free-text description of that transmission points at another.
            // They should agree, and when they do not the operator decides.
            String nearestByText = nearestByDescription(messyText, registryCodes, descriptions);
            int unrestricted = nearestCode == null ? -1 : DamerauLevenshtein.distance(noisy, nearestCode);
            int weighted = nearestCode == null ? -1
                    : WeightedEditDistance.distance(noisy, nearestCode, costs);
            int textDistance = nearestByText == null ? -1
                    : DamerauLevenshtein.distance(messyText, descriptions.get(indexOfCode(registryCodes, nearestByText)));
            boolean agrees = nearestCode != null && nearestCode.equals(nearestByText);
            if (agrees) {
                agreed++;
            } else {
                uncertain++;
            }
            // Two distinct failures are worth distinguishing: the code repair may be
            // right while the prose is too mangled to confirm it, and a real
            // disagreement between the two reads would mean the code is suspect.
            String verdict = agrees ? "resolved"
                    : nearestByText.equals(nearestCode)
                            ? "code repaired, description unreadable (closest " + nearestByText
                                    + " at distance " + textDistance + ")"
                            : "REVIEW (description suggests " + nearestByText + " at distance " + textDistance + ")";
            rows.add(List.of(noisy, declaredHazard, nearestCode == null ? "?" : nearestCode,
                    String.valueOf(unrestricted), String.valueOf(weighted), verdict));
            artifact.add(noisy + "," + declaredHazard + "," + (nearestCode == null ? "?" : nearestCode) + ","
                    + unrestricted + "," + weighted + "," + verdict);
        }
        out.printTable(List.of("noisy code", "hazard", "nearest code", "unrestricted", "weighted", "verdict"), rows);
        out.detail("codes resolved identically by both models", agreed);
        out.detail("codes needing review", uncertain);

        out.line("  alignment for the worst repair (unrestricted model):");
        int worstIndex = 0;
        int worstDistance = -1;
        List<String> noisyCodes = new ArrayList<>();
        for (String line : Files.readAllLines(noisyCodeFeed)) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                noisyCodes.add(trimmed.split("\\|", -1)[0].strip());
            }
        }
        for (int i = 0; i < noisyCodes.size(); i++) {
            String nearest = DamerauLevenshtein.nearest(noisyCodes.get(i), registryCodes, 4);
            int distance = nearest == null ? -1 : DamerauLevenshtein.distance(noisyCodes.get(i), nearest);
            if (distance > worstDistance) {
                worstDistance = distance;
                worstIndex = i;
            }
        }
        String worstNoisy = noisyCodes.get(worstIndex);
        String worstTarget = DamerauLevenshtein.nearest(worstNoisy, registryCodes, 4);
        out.detail("noisy", worstNoisy + " -> " + worstTarget + " (distance " + worstDistance + ")");
        for (Levenshtein.Op step : Levenshtein.alignment(worstNoisy, worstTarget)) {
            out.line("    " + step);
        }
        for (String step : WeightedEditDistance.alignment(worstNoisy, worstTarget, costs).subList(0,
                Math.min(6, WeightedEditDistance.alignment(worstNoisy, worstTarget, costs).size()))) {
            out.line("    weighted: " + step);
        }
        out.detail("similarity", String.format(Locale.ROOT, "%.3f", Levenshtein.similarity(worstNoisy, worstTarget)));

        // Bin packing: how many convoys of the largest corridor capacity are
        // needed to move the pooled demand of the high priority districts.
        int convoyCapacity = dataset.routes().stream().mapToInt(InputHandler.Route::capacity).max().orElse(0);
        int[] waterDemands = dataset.districts().stream().filter(d -> d.priority() >= 4)
                .mapToInt(InputHandler.District::needWater).toArray();
        int convoys = BitmaskDP.minimumBins(waterDemands, convoyCapacity);
        int naiveConvoys = 0;
        int pooled = 0;
        for (int demand : waterDemands) {
            pooled += demand;
            if (pooled > convoyCapacity) {
                naiveConvoys++;
                pooled = demand;
            }
        }
        if (pooled > 0) {
            naiveConvoys++;
        }
        out.detail("priority districts served", waterDemands.length);
        out.detail("convoy capacity (crates/day)", convoyCapacity);
        out.detail("optimal convoys for pooled water", convoys + " (next-fit uses " + naiveConvoys + ")");

        // Held-Karp over the district coordinates: a tour for the assessment team.
        List<InputHandler.District> tourDistricts = dataset.districts().stream()
                .filter(d -> d.priority() >= 5).toList();
        double[][] distance = new double[tourDistricts.size()][tourDistricts.size()];
        for (int i = 0; i < tourDistricts.size(); i++) {
            for (int j = 0; j < tourDistricts.size(); j++) {
                double dx = tourDistricts.get(i).x() - tourDistricts.get(j).x();
                double dy = tourDistricts.get(i).y() - tourDistricts.get(j).y();
                distance[i][j] = Math.hypot(dx, dy);
            }
        }
        BitmaskDP.Tour tour = BitmaskDP.shortestTour(distance);
        StringBuilder order = new StringBuilder();
        for (int index : tour.order()) {
            if (order.length() > 0) {
                order.append(" -> ");
            }
            order.append(tourDistricts.get(index).id());
        }
        out.detail("priority 5 districts", tourDistricts.size());
        out.detail("shortest assessment tour", tour.cost() + " km: " + order);

        out.write("search_results/code_repairs.csv", artifact);
        out.detail("written", "search_results/code_repairs.csv");
    }

    private static int indexOfCode(List<String> codes, String code) {
        return codes.indexOf(code);
    }

    /** The code whose registry description is closest to the messy transmission text. */
    private static String nearestByDescription(String text, List<String> codes, List<String> descriptions) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < descriptions.size(); i++) {
            if (text.isEmpty() || descriptions.get(i).isEmpty()) {
                continue;
            }
            int distance = DamerauLevenshtein.distance(text, descriptions.get(i));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = codes.get(i);
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- stage 4

    /**
     * M4. Three commodities are pushed from warehouses to districts over the road
     * corridors. Each commodity gets its own single-commodity max flow, and the
     * three flow algorithms are required to agree before the resulting minimum
     * cut is trusted, since a cut read off a non-maximal flow is not a cut at all.
     *
     * Corridor capacity is shared between commodities in reality, so these three
     * plans are individually feasible but not jointly feasible; that is stated
     * rather than hidden, and it is exactly the multi-commodity flow that module
     * M5 shows to be NP-hard.
     */
    void stage4ReliefLogistics(InputHandler.Dataset dataset) throws IOException {
        out.section("Stage 4 - relief logistics (M4: max flow, minimum cut, bipartite matching, Koenig)");

        Map<String, InputHandler.Warehouse> warehouses = dataset.warehousesById();
        Map<String, InputHandler.District> districts = dataset.districtsById();
        List<String> artifact = new ArrayList<>(List.of("# commodity,max_flow,delivered,demand,shortfall,cut_capacity"));
        List<String> cutArtifact = new ArrayList<>(List.of("# commodity,corridor,capacity"));

        for (Commodity commodity : Commodity.values()) {
            FlowNetwork network = new FlowNetwork();
            network.addNode(SOURCE);
            network.addNode(SINK);
            for (InputHandler.Warehouse warehouse : warehouses.values()) {
                network.addNode(warehouse.id());
                network.addEdge(SOURCE, warehouse.id(), commodity.stockOf(warehouse));
            }
            for (InputHandler.District district : districts.values()) {
                network.addNode(district.id());
                network.addEdge(district.id(), SINK, commodity.demandOf(district));
            }
            for (InputHandler.Route route : dataset.routes()) {
                // A blocked corridor contributes nothing. Adding it with zero capacity
                // keeps it visible in the cut, which is what an operations cell
                // wants to see: the road it already knows is closed.
                network.addEdge(route.from(), route.to(), route.blocked() ? 0 : route.capacity());
            }

            MaximumFlow.Comparison comparison = MaximumFlow.compare(network, SOURCE, SINK);
            Map<String, Object> report = MaximumFlow.flowAndCut(network, SOURCE, SINK);
            int flow = (int) report.get("flow");
            MinimumCut.Cut cut = (MinimumCut.Cut) report.get("cut");

            out.detail(commodity.label(), "max flow " + flow + " of demand " + commodity.totalDemand(dataset));
            for (MaximumFlow.Run run : comparison.runs()) {
                out.line(String.format(Locale.ROOT, "      %-14s value=%d augmentations=%d %.2f ms",
                        run.algorithm(), run.value(), run.augmentations(), run.nanos() / 1e6));
            }
            out.detail("  algorithms agree", comparison.agree() ? "yes" : "NO");
            out.detail("  shortest capacity across the cut", cut.capacity());
            for (String corridor : MinimumCut.asRoadClosures(cut)) {
                out.line("    would close " + corridor);
                cutArtifact.add(commodity.name() + "," + corridor.replace(" -> ", "->") + ","
                        + capacityOf(network, corridor));
            }

            Map<String, Integer> edgeFlows = edgeFlows(report);
            List<List<String>> rows = new ArrayList<>();
            int shortfall = 0;
            for (InputHandler.District district : districts.values()) {
                int delivered = edgeFlows.getOrDefault(district.id() + " -> " + SINK, 0);
                int demand = commodity.demandOf(district);
                shortfall += Math.max(0, demand - delivered);
                rows.add(List.of(district.id(), district.name(), String.valueOf(demand),
                        String.valueOf(delivered), demand - delivered <= 0 ? "met" : "short " + (demand - delivered)));
            }
            out.printTable(List.of("district", "name", "demand", "delivered", "status"), rows);
            out.detail("  total shortfall", shortfall);
            artifact.add(commodity.name() + "," + flow + "," + flow + "," + commodity.totalDemand(dataset)
                    + "," + shortfall + "," + cut.capacity());
        }

        // Team placement is bipartite: teams on one side, districts on the other.
        List<String> teamIds = dataset.teams().stream().map(InputHandler.Team::id).toList();
        List<String> districtIds = new ArrayList<>(districts.keySet());
        List<String[]> edges = new ArrayList<>();
        for (InputHandler.Team team : dataset.teams()) {
            for (String sector : team.sectors()) {
                if (districts.containsKey(sector)) {
                    edges.add(new String[] {team.id(), sector});
                }
            }
        }
        BipartiteMatching.Matching hopcroft = BipartiteMatching.hopcroftKarp(teamIds, districtIds, edges);
        BipartiteMatching.Matching viaFlow = BipartiteMatching.viaMaxFlow(teamIds, districtIds, edges);
        out.detail("placement edges", edges.size());
        out.detail("Hopcroft-Karp matching", hopcroft.size());
        out.detail("matching via max flow", viaFlow.size());
        out.detail("the two agree", hopcroft.size() == viaFlow.size() ? "yes" : "NO");

        List<List<String>> placementRows = new ArrayList<>();
        for (Map.Entry<String, String> pair : hopcroft.pairs().entrySet()) {
            InputHandler.Team team = dataset.teamsById().get(pair.getKey());
            InputHandler.District district = districts.get(pair.getValue());
            placementRows.add(List.of(pair.getKey(), team == null ? "?" : team.name(), pair.getValue(),
                    district == null ? "?" : district.name(), String.valueOf(district == null ? 0 : district.priority())));
        }
        out.printTable(List.of("team", "name", "district", "district name", "priority"), placementRows);
        out.detail("unplaced teams", teamIds.size() - hopcroft.size());

        KonigTheorem.Cover cover = KonigTheorem.minVertexCover(teamIds, districtIds, edges);
        out.detail("Koenig minimum vertex cover", cover.size());
        out.detail("cover equals matching size", cover.optimal() ? "yes (this is Koenig's theorem)" : "NO");
        List<String> allVertices = new ArrayList<>(teamIds);
        allVertices.addAll(districtIds);
        out.detail("cover is independent-set complement",
                KonigTheorem.isIndependent(edges, KonigTheorem.complementIndependentSet(allVertices, cover))
                        ? "yes" : "NO");

        artifact.add("");
        artifact.add("# team,district matched");
        for (Map.Entry<String, String> pair : hopcroft.pairs().entrySet()) {
            artifact.add(pair.getKey() + "," + pair.getValue());
        }
        out.write("optimization_results/relief_plan.csv", artifact);
        out.write("optimization_results/cut_corridors.csv", cutArtifact);
        out.detail("written", "optimization_results/relief_plan.csv, cut_corridors.csv");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> edgeFlows(Map<String, Object> report) {
        return (Map<String, Integer>) report.get("edgeFlows");
    }

    private static int capacityOf(FlowNetwork network, String corridor) {
        String[] ends = corridor.split(" -> ");
        if (ends.length != 2) {
            return 0;
        }
        int tail = network.node(ends[0]);
        for (int arc : network.arcsFrom(tail)) {
            if (network.name(network.head(arc)).equals(ends[1])) {
                return network.declaredCapacity(arc);
            }
        }
        return 0;
    }

    /** The three commodities the warehouses hold, and where each is needed. */
    private enum Commodity {
        WATER("water packets") {
            int stockOf(InputHandler.Warehouse warehouse) {
                return warehouse.waterPackets();
            }

            int demandOf(InputHandler.District district) {
                return district.needWater();
            }
        },
        MEDICAL("medical kits") {
            int stockOf(InputHandler.Warehouse warehouse) {
                return warehouse.medicalKits();
            }

            int demandOf(InputHandler.District district) {
                return district.needMedical();
            }
        },
        BLANKETS("blanket sets") {
            int stockOf(InputHandler.Warehouse warehouse) {
                return warehouse.blanketSets();
            }

            int demandOf(InputHandler.District district) {
                return district.needBlankets();
            }
        };

        private final String label;

        Commodity(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        abstract int stockOf(InputHandler.Warehouse warehouse);

        abstract int demandOf(InputHandler.District district);

        int totalDemand(InputHandler.Dataset dataset) {
            int total = 0;
            for (InputHandler.District district : dataset.districts()) {
                total += demandOf(district);
            }
            return total;
        }

        int totalStock(InputHandler.Dataset dataset) {
            int total = 0;
            for (InputHandler.Warehouse warehouse : dataset.warehouses()) {
                total += stockOf(warehouse);
            }
            return total;
        }
    }

    // ---------------------------------------------------------------- stage 5

    /**
     * M5. The team placement question from stage 4 is compiled to 3-SAT in the
     * standard way: one variable per candidate edge, at most one edge per team,
     * at most one per district, at least one per district. Maximising the number
     * of true variables then recovers a maximum matching, and it must land on the
     * same number Hopcroft-Karp found. The same formula is also decided through
     * the independent-set and vertex-cover readings of the reduction, which is a
     * third path to the same answer.
     *
     * Separately, the team conflict graph gets a genuine 2-approximation, which
     * is the honest answer to an NP-hard question at this size: no proof of
     * optimality is claimed unless the exact solver confirms it.
     */
    void stage5Hardness(InputHandler.Dataset dataset) throws IOException {
        out.section("Stage 5 - hardness (M5: SAT, reductions, independent set, 2-approximation)");

        List<String> teamIds = dataset.teams().stream().map(InputHandler.Team::id).toList();
        List<String> districtIds = dataset.districts().stream().map(InputHandler.District::id).toList();
        List<String[]> placementEdges = new ArrayList<>();
        for (InputHandler.Team team : dataset.teams()) {
            for (String sector : team.sectors()) {
                if (districtIds.contains(sector)) {
                    placementEdges.add(new String[] {team.id(), sector});
                }
            }
        }

        // One variable per edge; index i of the clause arrays means variable i.
        int variables = placementEdges.size();
        List<int[]> clauses = new ArrayList<>();
        Map<String, List<Integer>> edgesByTeam = new LinkedHashMap<>();
        Map<String, List<Integer>> edgesByDistrict = new LinkedHashMap<>();
        for (int i = 0; i < variables; i++) {
            edgesByTeam.computeIfAbsent(placementEdges.get(i)[0], k -> new ArrayList<>()).add(i);
            edgesByDistrict.computeIfAbsent(placementEdges.get(i)[1], k -> new ArrayList<>()).add(i);
        }
        for (Map.Entry<String, List<Integer>> entry : edgesByTeam.entrySet()) {
            addAtMostOne(clauses, entry.getValue());
        }
        for (Map.Entry<String, List<Integer>> entry : edgesByDistrict.entrySet()) {
            addAtMostOne(clauses, entry.getValue());
            clauses.add(toLiterals(entry.getValue()));
        }
        SAT.Formula formula = SAT.Formula.of(variables, clauses);

        out.detail("placement variables", variables);
        out.detail("clauses", clauses.size() + " (longest " + formula.longestClause() + " literals)");
        out.detail("canonical 3-CNF required", ThreeSAT.isThreeCNF(formula) ? "already 3-CNF" : "no, Tseitin will be applied");
        out.detail("reachable clauses in 3-CNF", ThreeSAT.toThreeCNF(formula).clauses().size());

        SAT.Solution solution = SAT.dpll(formula);
        out.detail("DPLL satisfiable", solution.satisfiable());
        out.detail("DPLL decisions", solution.decisions());
        out.detail("DPLL assignment size", solution.satisfiable() ? assignmentSize(solution) : 0);

        int bestBySat = maximiseSat(formula, variables);
        int bestByMatching = BipartiteMatching.hopcroftKarp(teamIds, districtIds, placementEdges).size();
        out.detail("largest assignment found by SAT", bestBySat);
        out.detail("maximum matching from stage 4", bestByMatching);
        out.detail("the two agree", bestBySat == bestByMatching ? "yes" : "NO");

        boolean viaIndependentSet = PolynomialReductions.solveViaIndependentSet(formula);
        boolean viaVertexCover = PolynomialReductions.solveViaVertexCover(formula);
        out.detail("decidable through the reduction", "independent set " + viaIndependentSet
                + ", vertex cover " + viaVertexCover);
        out.detail("reduction agrees with DPLL",
                viaIndependentSet == solution.satisfiable() && viaVertexCover == solution.satisfiable()
                        ? "yes" : "NO");

        PolynomialReductions.Reduction reduction = PolynomialReductions.threeSatToIndependentSet(formula);
        out.detail("reduction graph", reduction.toString());
        out.detail("independent set target", reduction.targetSize() + ", vertex cover target "
                + reduction.coverTargetSize());
        if (solution.satisfiable()) {
            IndependentSet.Independent independent =
                    IndependentSet.maximumIndependentSet(reduction.vertices(), reduction.edges());
            boolean[] assignment = PolynomialReductions.readAssignment(reduction, independent.vertices());
            out.detail("assignment recovered from the reduction", assignment != null);
        }

        // The NP-hard question the operations cell actually asks: which teams can
        // be stood down so the rest still cover every district.
        List<int[]> conflicts = teamConflicts(dataset);
        int vertices = teamIds.size();
        IndependentSet.Independent maximum = IndependentSet.maximumIndependentSet(vertices, conflicts);
        VertexCover.Cover greedy = VertexCover.greedyVertexCover(vertices, conflicts);
        VertexCover.Cover exact = VertexCover.minimumVertexCover(vertices, conflicts);
        TwoApproximation.Approximation approximation = TwoApproximation.cover(vertices, conflicts, exact.size());
        out.detail("team conflict graph", vertices + " vertices, " + conflicts.size() + " edges");
        out.detail("exact minimum cover", exact.size() + (exact.optimal() ? " (proven optimal)" : ""));
        out.detail("2-approximation size", approximation.size() + ", ratio "
                + String.format(Locale.ROOT, "%.2f", approximation.ratio()));
        out.detail("within a factor of two", approximation.withinFactorTwo() ? "yes" : "NO");
        out.detail("greedy cover size", greedy.size());
        out.detail("largest compatible team set", maximum.size()
                + (maximum.provenMaximum() ? " (proven maximum)" : ""));

        List<String> artifact = new ArrayList<>();
        artifact.add("# stage 5: 3-SAT encoding of team placement");
        artifact.add("variables=" + variables);
        artifact.add("clauses=" + clauses.size());
        artifact.add("dpll_satisfiable=" + solution.satisfiable());
        artifact.add("dpll_decisions=" + solution.decisions());
        artifact.add("max_assignment=" + bestBySat);
        artifact.add("max_matching=" + bestByMatching);
        artifact.add("reduction_vertices=" + reduction.vertices());
        artifact.add("independent_set_target=" + reduction.targetSize());
        artifact.add("vertex_cover_target=" + reduction.coverTargetSize());
        artifact.add("exact_cover=" + exact.size());
        artifact.add("approximation=" + approximation.size());
        artifact.add("approximation_ratio=" + String.format(Locale.ROOT, "%.3f", approximation.ratio()));
        artifact.add("");
        artifact.add("# team,stand_down");
        Set<Integer> standDown = new LinkedHashSet<>(greedy.vertices());
        for (int i = 0; i < vertices; i++) {
            artifact.add(teamIds.get(i) + "," + (standDown.contains(i) ? "yes" : "no"));
        }
        out.write("optimization_results/scheduling.txt", artifact);
        out.detail("written", "optimization_results/scheduling.txt");
    }

    /** Two teams conflict if they share a sector and their capabilities are disjoint. */
    private static List<int[]> teamConflicts(InputHandler.Dataset dataset) {
        List<int[]> conflicts = new ArrayList<>();
        List<InputHandler.Team> teams = dataset.teams();
        for (int i = 0; i < teams.size(); i++) {
            for (int j = i + 1; j < teams.size(); j++) {
                Set<String> shared = new LinkedHashSet<>(teams.get(i).sectors());
                shared.retainAll(teams.get(j).sectors());
                if (shared.isEmpty()) {
                    continue;
                }
                Set<String> capabilitiesI = new LinkedHashSet<>(teams.get(i).capabilities());
                capabilitiesI.retainAll(teams.get(j).capabilities());
                if (capabilitiesI.isEmpty()) {
                    conflicts.add(new int[] {i, j});
                }
            }
        }
        return conflicts;
    }

    private static void addAtMostOne(List<int[]> clauses, List<Integer> edgeIds) {
        for (int a = 0; a < edgeIds.size(); a++) {
            for (int b = a + 1; b < edgeIds.size(); b++) {
                clauses.add(new int[] {-(edgeIds.get(a) + 1), -(edgeIds.get(b) + 1)});
            }
        }
    }

    private static int[] toLiterals(List<Integer> edgeIds) {
        int[] literals = new int[edgeIds.size()];
        for (int i = 0; i < edgeIds.size(); i++) {
            literals[i] = edgeIds.get(i);
        }
        return literals;
    }

    private static int assignmentSize(SAT.Solution solution) {
        int size = 0;
        for (boolean value : solution.assignment()) {
            if (value) {
                size++;
            }
        }
        return size;
    }

    /**
     * Finds the largest satisfying assignment by repeatedly asking for one more
     * edge than the best found so far. Each round adds a clause over the
     * variables currently false, which is satisfiable only when some strictly
     * larger assignment exists, so the loop counts up and stops when it cannot.
     */
    private static int maximiseSat(SAT.Formula base, int variables) {
        int best = 0;
        List<int[]> clauses = new ArrayList<>(base.clauses());
        for (int round = 0; round < variables + 1; round++) {
            SAT.Solution solution = SAT.dpll(SAT.Formula.of(variables, clauses));
            if (!solution.satisfiable()) {
                break;
            }
            best = assignmentSize(solution);
            List<Integer> falseSoFar = new ArrayList<>();
            for (int v = 0; v < variables; v++) {
                if (!solution.assignment()[v]) {
                    falseSoFar.add(v);
                }
            }
            if (falseSoFar.isEmpty()) {
                break;
            }
            int[] clause = new int[falseSoFar.size()];
            for (int i = 0; i < falseSoFar.size(); i++) {
                clause[i] = falseSoFar.get(i);
            }
            clauses.add(clause);
        }
        return best;
    }

    // ---------------------------------------------------------------- stage 6

    /**
     * M6. The telemetry stage is where the parallel and randomized work belongs.
     * Sensor levels are scanned with Blelloch to find when each sensor crossed the
     * threshold the flood report mentions, the casualty totals are reduced over a
     * Fork/Join pool, the readings are sorted with a randomized quicksort whose
     * comparison count is reported rather than assumed, an audit sample is drawn
     * by both reservoir algorithms, depot keys are tested for primality, and Brent
     * finds the period of the legacy incident-code generator.
     */
    void stage6Telemetry(InputHandler.Dataset dataset) throws IOException {
        out.section("Stage 6 - telemetry (M6: Blelloch, Fork/Join, randomized quicksort, reservoir, Miller-Rabin, Brent)");

        Map<String, List<InputHandler.SensorReading>> bySensor = new LinkedHashMap<>();
        for (InputHandler.SensorReading reading : dataset.readings()) {
            bySensor.computeIfAbsent(reading.sensorId(), k -> new ArrayList<>()).add(reading);
        }
        final double floodThreshold = 9.5;

        List<List<String>> rows = new ArrayList<>();
        List<String> artifact = new ArrayList<>(List.of("# sensor,samples,peak,peak_at,threshold_crossing,closing,rank_of_peak"));
        for (Map.Entry<String, List<InputHandler.SensorReading>> entry : bySensor.entrySet()) {
            List<InputHandler.SensorReading> samples = entry.getValue();
            int[] levels = new int[samples.size()];
            for (int i = 0; i < samples.size(); i++) {
                levels[i] = (int) Math.round(samples.get(i).waterLevel() * 100);
            }
            BlellochScan.ScanResult scan = BlellochScan.scan(levels);
            int[] inclusive = BlellochScan.scanInclusive(levels);
            int[] ranks = BlellochScan.ranks(levels);

            int peakIndex = 0;
            for (int i = 1; i < levels.length; i++) {
                if (levels[i] > levels[peakIndex]) {
                    peakIndex = i;
                }
            }
            int crossing = -1;
            for (int i = 0; i < inclusive.length; i++) {
                if (inclusive[i] >= (int) (floodThreshold * 100)) {
                    crossing = i;
                    break;
                }
            }
            rows.add(List.of(entry.getKey(), String.valueOf(samples.size()),
                    String.format(Locale.ROOT, "%.2f", levels[peakIndex] / 100.0),
                    samples.get(peakIndex).timestamp(),
                    crossing < 0 ? "never" : samples.get(crossing).timestamp(),
                    String.format(Locale.ROOT, "%.2f", levels[levels.length - 1] / 100.0),
                    ranks[peakIndex] + "/" + levels.length));
            artifact.add(entry.getKey() + "," + samples.size() + "," + levels[peakIndex] + ","
                    + samples.get(peakIndex).timestamp() + "," + (crossing < 0 ? "never" : samples.get(crossing).timestamp())
                    + "," + levels[levels.length - 1] + "," + ranks[peakIndex]);
            out.line(String.format(Locale.ROOT,
                    "      %s: scan total %d over %d samples, %d work units, max depth %d, monotone=%b",
                    entry.getKey(), scan.total(), levels.length, scan.operations(), scan.maxDepth(), scan.monotone()));
        }
        out.detail("flood threshold", floodThreshold + " m (from RPT-2024-0001)");
        out.printTable(List.of("sensor", "samples", "peak", "peak at", "crossed threshold", "closing", "peak rank"), rows);

        int[] casualtyTotals = dataset.teamStatus().stream().mapToInt(InputHandler.TeamStatus::casualtiesHandled).toArray();
        ForkJoinPool pool = ForkJoinPool.commonPool();
        ParallelReduction.Result<Long> sum = ParallelReduction.sum(casualtyTotals);
        long sequentialTotal = ParallelReduction.sumSequential(casualtyTotals);
        ParallelReduction.Result<Integer> worst = ParallelReduction.max(casualtyTotals, pool);
        out.detail("casualties handled, total", sum.value() + " (sequential " + sequentialTotal + ")");
        out.detail("reduction correct", sum.value() == sequentialTotal ? "yes" : "NO");
        out.detail("reduction work / depth / tasks", sum.operations() + " / " + sum.maxDepth() + " / " + sum.tasks());
        if (sum.tasks() <= 1) {
            out.line("      (" + casualtyTotals.length + " entries is below the fork threshold, so this ran on"
                    + " one thread; the work count is the sequential figure for comparison)");
        }
        out.detail("peak single entry", worst.value() + " at depth " + worst.maxDepth());

        int[] levelsForSort = new int[casualtyTotals.length];
        System.arraycopy(casualtyTotals, 0, levelsForSort, 0, casualtyTotals.length);
        RandomizedQuickSort.Result sort = RandomizedQuickSort.sort(levelsForSort, 20240714L);
        out.detail("randomized quicksort", sort.toString());
        out.detail("sort is ordered", sort.sorted() ? "yes" : "NO");
        out.detail("guard or insertion cut-in", (sort.guardTriggered() ? "guard " : "")
                + (sort.insertionSortUsed() ? "insertion" : "no cut-in"));

        int auditSize = Math.min(3, dataset.reports().size());
        ReservoirSampling.Sample algorithmR = ReservoirSampling.algorithmR(dataset.reports(), auditSize, 99L);
        ReservoirSampling.Sample algorithmL = ReservoirSampling.algorithmL(dataset.reports(), auditSize, 99L);
        out.detail("audit sample size", auditSize + " of " + dataset.reports().size() + " reports");
        out.detail("algorithm R", algorithmR.values(dataset.reports()).stream()
                .map(InputHandler.Report::reportId).toList());
        out.detail("algorithm L", algorithmL.values(dataset.reports()).stream()
                .map(InputHandler.Report::reportId).toList());
        out.detail("sample rate", String.format(Locale.ROOT, "%.3f", algorithmR.rate()));

        List<String> primeDistricts = new ArrayList<>();
        for (InputHandler.District district : dataset.districts()) {
            if (MillerRabin.isPrime(district.population())) {
                primeDistricts.add(district.id() + "(" + district.population() + ")");
            }
        }
        out.detail("districts with prime population", primeDistricts.isEmpty() ? "none" : String.join(" ", primeDistricts));
        out.detail("primes below 40", MillerRabin.primesUpTo(40));

        // The legacy generator that produced the pre-2024 codes. Knowing its period
        // bounds how many distinct codes it can ever emit, which is why this is
        // worth computing rather than assuming.
        // The multiplier is coprime to the modulus, so the map is a permutation of
        // the residue class ring and every orbit is a pure cycle with no tail: the
        // cycle index is 0 by construction, and the length is the multiplicative
        // order of the multiplier.
        long modulus = 65_521;
        long multiplier = 75;
        long increment = 74;
        long seed = 12_345L;
        Function<Long, Long> next = state -> (multiplier * state + increment) % modulus;
        // Brent can need a few times mu + lambda evaluations before the checkpoint
        // window is wide enough, so the limit is a generous multiple of the state
        // space rather than the state space itself.
        long limit = 8 * modulus;
        BrentAlgorithm.CycleResult brent = BrentAlgorithm.brent(next, seed, limit);
        BrentAlgorithm.CycleResult floyd = BrentAlgorithm.floyd(next, seed, limit);
        BrentAlgorithm.CycleResult table = BrentAlgorithm.hashTable(next, seed, limit);
        out.detail("legacy LCG", "x -> (" + multiplier + "x + " + increment + ") mod " + modulus);
        out.detail("Brent", describeCycle(brent));
        out.detail("Floyd", describeCycle(floyd));
        out.detail("hash table", describeCycle(table));
        boolean allFound = brent.found() && floyd.found() && table.found();
        boolean allAgree = allFound && brent.length() == floyd.length() && floyd.length() == table.length()
                && brent.index() == floyd.index() && floyd.index() == table.index();
        out.detail("methods agree", allAgree ? "yes" : allFound ? "NO" : "NOT FOUND within " + limit + " steps");
        out.detail("Brent saved", floyd.found() && brent.found()
                ? (floyd.steps() - brent.steps()) + " evaluations against Floyd ("
                        + String.format(Locale.ROOT, "%.1fx", floyd.steps() / (double) Math.max(1, brent.steps()))
                        + ")"
                : "not comparable, at least one method found no cycle");

        artifact.add("");
        artifact.add("# casualties_total=" + sum.value());
        artifact.add("# quicksort_comparisons=" + sort.comparisons());
        artifact.add("# audit_r=" + algorithmR.positions());
        artifact.add("# audit_l=" + algorithmL.positions());
        artifact.add("# lcg_modulus=" + modulus);
        artifact.add("# lcg_cycle_length=" + brent.length());
        artifact.add("# lcg_cycle_index=" + brent.index());
        out.write("indexing_results/telemetry.txt", artifact);
        out.detail("written", "indexing_results/telemetry.txt");
    }
}
