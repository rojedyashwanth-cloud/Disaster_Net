/*
 * Runs every module test in this directory and reports one summary.
 *
 * Each test class is launched in its own JVM. That costs a few seconds over
 * running them in one process, and it buys isolation: a test that corrupts a
 * static field or leaves a Fork/Join pool in a bad state cannot affect the ones
 * after it, so a failure always means something about that test.
 *
 * A test counts as failed if it exits non-zero or if its output contains a FAIL
 * or Exception line. Several tests report their results by printing rather than
 * by throwing, so the exit code alone would hide a failure.
 *
 * Usage: java TestRunner <classes-dir> [name-fragment ...]
 */
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class TestRunner {

    private static final List<String> FAILURE_MARKERS = List.of("FAIL", "Exception", "error:");

    private TestRunner() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            System.err.println("usage: TestRunner <classes-dir> [name-fragment ...]");
            System.exit(2);
        }
        Path classesDir = Path.of(args[0]);
        String classpath = System.getProperty("java.class.path");
        List<String> filters = Arrays.asList(args).subList(1, args.length);

        List<String> names;
        try (Stream<Path> files = Files.list(classesDir)) {
            names = files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith("Test.class"))
                    .map(n -> n.substring(0, n.length() - ".class".length()))
                    .filter(n -> filters.stream().allMatch(n::contains))
                    .sorted()
                    .toList();
        }
        if (names.isEmpty()) {
            System.err.println("no test classes found in " + classesDir
                    + (filters.isEmpty() ? "" : " matching " + filters));
            System.exit(2);
        }

        List<String> failed = new ArrayList<>();
        long totalMillis = 0;
        for (String name : names) {
            long start = System.nanoTime();
            ProcessBuilder builder = new ProcessBuilder("java", "-cp", classpath, name);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes());
            int status = process.waitFor();
            long millis = (System.nanoTime() - start) / 1_000_000;
            totalMillis += millis;

            List<String> offending = output.lines().filter(TestRunner::looksLikeFailure).toList();
            boolean ok = status == 0 && offending.isEmpty();
            if (!ok) {
                failed.add(name);
            }
            System.out.printf(Locale.ROOT, "%-6s %-38s %6d ms%n", ok ? "ok" : "FAILED", name, millis);
            if (!ok) {
                List<String> shown = offending.isEmpty()
                        ? output.lines().limit(10).toList()
                        : offending.stream().limit(10).toList();
                shown.forEach(line -> System.out.println("         " + line.strip()));
                if (offending.isEmpty()) {
                    System.out.println("         exit status " + status);
                }
            }
        }

        System.out.println();
        System.out.println((names.size() - failed.size()) + " of " + names.size() + " tests passed in "
                + totalMillis + " ms");
        if (!failed.isEmpty()) {
            System.out.println("failed: " + String.join(", ", failed));
            System.exit(1);
        }
    }

    private static boolean looksLikeFailure(String line) {
        for (String marker : FAILURE_MARKERS) {
            if (line.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
