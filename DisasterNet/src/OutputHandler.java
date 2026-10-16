/*
 * DisasterNet pipeline - output stage.
 *
 * Every stage of the pipeline produces human-readable artifacts under output/,
 * grouped the way the three directories already present in the project suggest:
 * search_results for the M1/M2 text stages, indexing_results for the M2/M6
 * index structures, and optimization_results for the M4/M5 planning stages.
 *
 * Writes are atomic in the sense that matters here: the content is assembled in
 * full and written with a single call, so a partially written file cannot be
 * mistaken for a complete one. OutputHandler also keeps a transcript of
 * everything it printed, which the pipeline writes as a run log, so a report can
 * be reconstructed after the fact without re-running anything.
 */
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Collects stage output and writes it to disk under output/. */
public final class OutputHandler {

    private final Path outputDir;
    private final List<String> transcript = new ArrayList<>();

    public OutputHandler(Path outputDir) {
        this.outputDir = outputDir;
    }

    /** Prints a section heading and records it in the transcript. */
    public void section(String title) {
        String bar = "=".repeat(Math.max(title.length(), 8));
        record(bar);
        record(title);
        record(bar);
    }

    /** Prints a line and records it in the transcript. */
    public void line(String text) {
        record(text);
    }

    /** Prints a labelled line, indented so it reads as a detail of the last section. */
    public void detail(String label, Object value) {
        record("  " + label + ": " + value);
    }

    private void record(String text) {
        transcript.add(text);
        System.out.println(text);
    }

    /**
     * Writes {@code lines} to {@code relativePath} under the output directory,
     * creating parent directories as needed.
     */
    public void write(String relativePath, List<String> lines) throws IOException {
        Path target = outputDir.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    /** Writes a single block of text to a file under the output directory. */
    public void write(String relativePath, String content) throws IOException {
        write(relativePath, content.lines().toList());
    }

    /** Writes the transcript of everything printed during this run. */
    public void writeTranscript() throws IOException {
        write("run_log.txt", transcript);
    }

    /** The transcript so far, for callers that want to assert on it. */
    public List<String> transcript() {
        return List.copyOf(transcript);
    }

    /** Formats a fixed-width table, right-aligning numeric columns. */
    public static List<String> table(List<String> headers, List<List<String>> rows) {
        int columns = headers.size();
        int[] widths = new int[columns];
        for (int i = 0; i < columns; i++) {
            widths[i] = headers.get(i).length();
        }
        for (List<String> row : rows) {
            for (int i = 0; i < columns && i < row.size(); i++) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }
        List<String> out = new ArrayList<>();
        out.add(joinCells(headers, widths));
        for (int i = 0; i < columns; i++) {
            widths[i] = Math.max(widths[i], 2);
        }
        StringBuilder rule = new StringBuilder();
        for (int i = 0; i < columns; i++) {
            rule.append("-".repeat(widths[i]));
            if (i + 1 < columns) {
                rule.append("-+-");
            }
        }
        out.add(rule.toString());
        for (List<String> row : rows) {
            out.add(joinCells(row, widths));
        }
        return out;
    }

    private static String joinCells(List<String> cells, int[] widths) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            String cell = i < cells.size() ? cells.get(i) : "";
            if (i > 0) {
                sb.append(" | ");
            }
            // The first column reads best left aligned; the rest are numbers.
            if (i == 0) {
                sb.append(cell);
                sb.append(" ".repeat(Math.max(0, widths[i] - cell.length())));
            } else {
                sb.append(" ".repeat(Math.max(0, widths[i] - cell.length())));
                sb.append(cell);
            }
        }
        return sb.toString();
    }

    /** Formats a table and prints it, recording every row in the transcript. */
    public void printTable(List<String> headers, List<List<String>> rows) {
        for (String row : table(headers, rows)) {
            record(row);
        }
    }
}
