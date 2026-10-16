/*
 * DisasterNet interactive console menu.
 *
 * The pipeline in Main proves the modules work end to end; this menu is the
 * operator-facing shell around it. Options 1 to 6 run the individual algorithm
 * modules, option 7 browses and searches the CSV data, option 8 appends new
 * incidents, resources, teams and observations to the data directory, and
 * option 9 summarises the relief inventory against district demand.
 */
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Menu {

    private static final String BAR = "=".repeat(40);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final Path dataDir;
    private final Path outputDir;
    private final OutputHandler out;
    private final Main main;
    private final BufferedReader in =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    private InputHandler.Dataset dataset;
    private boolean eof;

    Menu(Path dataDir, Path outputDir) throws IOException {
        this.dataDir = dataDir;
        this.outputDir = outputDir;
        this.out = new OutputHandler(outputDir);
        this.main = new Main(out);
        this.dataset = InputHandler.load(dataDir);
    }

    void run() throws IOException {
        reportDataset();
        while (true) {
            printBanner();
            int choice = askInt("Select an option: ", -1);
            if (eof) {
                out.line("");
                out.line("Input closed, exiting.");
                return;
            }
            if (choice == 0) {
                out.line("");
                out.line("Goodbye.");
                return;
            }
            try {
                dispatch(choice);
            } catch (IOException e) {
                out.line("Error: " + e.getMessage());
            }
            pause();
        }
    }

    private void dispatch(int choice) throws IOException {
        switch (choice) {
            case 1 -> main.stage1TextTriage(dataset);
            case 2 -> main.stage2PhraseIndexing(dataset);
            case 3 -> main.stage3FuzzyResolution(dataset, dataDir);
            case 4 -> main.stage4ReliefLogistics(dataset);
            case 5 -> main.stage5Hardness(dataset);
            case 6 -> main.stage6Telemetry(dataset);
            case 7 -> browse();
            case 8 -> addRecord();
            case 9 -> inventory();
            default -> out.line("Unknown option: " + choice);
        }
    }

    private void printBanner() {
        out.line("");
        out.line(BAR + "     DisasterNet");
        out.line(BAR);
        out.line("");
        out.line("1. String Searching");
        out.line("2. Suffix Structures");
        out.line("3. Data Cleaning & Optimization");
        out.line("4. Network Flow & Team Matching");
        out.line("5. Constraints & Approximation");
        out.line("6. Randomized / Parallel Analytics");
        out.line("7. Browse/Search CSV Data");
        out.line("8. Add Incident / Resource / Team / Observation");
        out.line("9. View Available Resource Inventory");
        out.line("0. Exit");
        out.line("");
    }

    private void reportDataset() {
        out.section("DisasterNet");
        out.detail("input", dataDir);
        out.detail("loaded", InputHandler.describe(dataset));
        if (dataset.warnings().isEmpty()) {
            out.detail("data quality", "no warnings");
        } else {
            out.detail("data quality", dataset.warnings().size() + " warning(s)");
        }
    }

    // ------------------------------------------------------------------ option 7

    private void browse() throws IOException {
        while (true) {
            out.line("");
            out.line("Browse/Search CSV Data");
            out.line("  a. Districts         e. Sensor readings");
            out.line("  b. Warehouses        f. Team status");
            out.line("  c. Routes            g. Incident codes");
            out.line("  d. Rescue teams      h. Reports");
            out.line("  s. Search all        0. Back");
            String pick = ask("Choose: ").strip().toLowerCase(Locale.ROOT);

            switch (pick) {
                case "a" -> out.printTable(
                        List.of("id", "name", "population", "water", "medical", "blankets", "priority"),
                        districts());
                case "b" -> out.printTable(
                        List.of("id", "name", "water", "medical", "blankets", "road cap"),
                        warehouses());
                case "c" -> out.printTable(
                        List.of("from", "to", "capacity", "status"),
                        routes());
                case "d" -> out.printTable(
                        List.of("id", "name", "capabilities", "sectors", "shift"),
                        teams());
                case "e" -> out.printTable(
                        List.of("timestamp", "sensor", "water", "wind", "smoke", "status", "severity"),
                        readings());
                case "f" -> out.printTable(
                        List.of("timestamp", "team", "mission", "casualties", "status", "severity"),
                        teamStatus());
                case "g" -> out.printTable(
                        List.of("code", "hazard", "priority", "description"),
                        codes());
                case "h" -> out.printTable(
                        List.of("file", "report id", "code", "hazard", "severity", "district"),
                        reports());
                case "s" -> search();
                case "0", "" -> {
                    return;
                }
                default -> out.line("Unknown choice: " + pick);
            }
        }
    }

    private void search() {
        String term = ask("Search for: ").strip();
        if (term.isEmpty()) {
            out.line("Nothing to search for.");
            return;
        }
        String needle = term.toLowerCase(Locale.ROOT);
        int hits = 0;
        for (List<String> row : new ArrayList<>(allRows())) {
            if (row.get(1).toLowerCase(Locale.ROOT).contains(needle)) {
                out.line(row.get(0) + " | " + row.get(1));
                hits++;
            }
        }
        out.detail("matches", hits);
    }

    private List<List<String>> allRows() {
        List<List<String>> all = new ArrayList<>();
        for (List<String> row : districts()) {
            all.add(List.of("district", String.join(" ", row)));
        }
        for (List<String> row : warehouses()) {
            all.add(List.of("warehouse", String.join(" ", row)));
        }
        for (List<String> row : routes()) {
            all.add(List.of("route", String.join(" ", row)));
        }
        for (List<String> row : teams()) {
            all.add(List.of("team", String.join(" ", row)));
        }
        for (List<String> row : readings()) {
            all.add(List.of("reading", String.join(" ", row)));
        }
        for (List<String> row : teamStatus()) {
            all.add(List.of("status", String.join(" ", row)));
        }
        for (List<String> row : codes()) {
            all.add(List.of("code", String.join(" ", row)));
        }
        for (List<String> row : reports()) {
            all.add(List.of("report", String.join(" ", row)));
        }
        return all;
    }

    private List<List<String>> districts() {
        List<List<String>> rows = new ArrayList<>();
        for (InputHandler.District d : dataset.districts()) {
            rows.add(List.of(d.id(), d.name(), String.valueOf(d.population()),
                    String.valueOf(d.needWater()), String.valueOf(d.needMedical()),
                    String.valueOf(d.needBlankets()), String.valueOf(d.priority())));
        }
        return rows;
    }

    private List<List<String>> warehouses() {
        List<List<String>> rows = new ArrayList<>();
        for (InputHandler.Warehouse w : dataset.warehouses()) {
            rows.add(List.of(w.id(), w.name(), String.valueOf(w.waterPackets()),
                    String.valueOf(w.medicalKits()), String.valueOf(w.blanketSets()),
                    String.valueOf(w.roadCapacityPerDay())));
        }
        return rows;
    }

    private List<List<String>> routes() {
        List<List<String>> rows = new ArrayList<>();
        for (InputHandler.Route r : dataset.routes()) {
            rows.add(List.of(r.from(), r.to(), String.valueOf(r.capacity()),
                    r.blocked() ? "BLOCKED" : "OPEN"));
        }
        return rows;
    }

    private List<List<String>> teams() {
        List<List<String>> rows = new ArrayList<>();
        for (InputHandler.Team t : dataset.teams()) {
            rows.add(List.of(t.id(), t.name(), String.join(",", t.capabilities()),
                    String.join(";", t.sectors()), String.valueOf(t.shiftHours())));
        }
        return rows;
    }

    private List<List<String>> readings() {
        List<List<String>> rows = new ArrayList<>();
        for (InputHandler.SensorReading r : dataset.readings()) {
            rows.add(List.of(r.timestamp(), r.sensorId(), String.valueOf(r.waterLevel()),
                    String.valueOf(r.windKmh()), String.valueOf(r.smokeIndex()),
                    r.status(), String.valueOf(r.severity())));
        }
        return rows;
    }

    private List<List<String>> teamStatus() {
        List<List<String>> rows = new ArrayList<>();
        for (InputHandler.TeamStatus s : dataset.teamStatus()) {
            rows.add(List.of(s.timestamp(), s.teamId(), s.mission(),
                    String.valueOf(s.casualtiesHandled()), s.status(),
                    String.valueOf(s.severity())));
        }
        return rows;
    }

    private List<List<String>> codes() {
        List<List<String>> rows = new ArrayList<>();
        for (InputHandler.Code c : dataset.codes()) {
            rows.add(List.of(c.code(), c.hazardClass(), String.valueOf(c.priority()), c.description()));
        }
        return rows;
    }

    private List<List<String>> reports() {
        List<List<String>> rows = new ArrayList<>();
        for (InputHandler.Report r : dataset.reports()) {
            rows.add(List.of(r.fileName(), r.reportId(), r.incidentCode(),
                    r.hazardClass(), r.severity(), r.district()));
        }
        return rows;
    }

    // ------------------------------------------------------------------ option 8

    private void addRecord() throws IOException {
        out.line("");
        out.line("Add Incident / Resource / Team / Observation");
        out.line("  a. Incident report   d. Rescue team");
        out.line("  b. District          e. Sensor reading");
        out.line("  c. Warehouse         f. Team status");
        out.line("  g. Route             0. Back");
        String pick = ask("Choose: ").strip().toLowerCase(Locale.ROOT);

        switch (pick) {
            case "a" -> addIncident();
            case "b" -> append(resourceData("districts.csv"), districtLine());
            case "c" -> append(resourceData("warehouses.csv"), warehouseLine());
            case "d" -> append(resourceData("rescue_teams.csv"), teamLine());
            case "e" -> append(fieldData("sensor_readings.csv"), readingLine());
            case "f" -> append(fieldData("team_status.csv"), statusLine());
            case "g" -> append(resourceData("routes.csv"), routeLine());
            case "0", "" -> {
                return;
            }
            default -> out.line("Unknown choice: " + pick);
        }
    }

    private void addIncident() throws IOException {
        String id = ask("Report ID: ").strip();
        String code = ask("Incident code: ").strip();
        String hazard = ask("Hazard class: ").strip();
        String severity = ask("Severity: ").strip();
        String declared = ask("Declared (default now): ").strip();
        String district = ask("District: ").strip();
        String originator = ask("Originator: ").strip();
        String narrative = ask("Narrative: ").strip();

        if (id.isEmpty() || code.isEmpty()) {
            out.line("Report ID and incident code are required.");
            return;
        }
        if (declared.isEmpty()) {
            declared = LocalDateTime.now().format(STAMP);
        }

        StringBuilder text = new StringBuilder();
        text.append("DISASTER INCIDENT REPORT\n");
        text.append("========================\n");
        text.append("Report ID      : ").append(id).append('\n');
        text.append("Incident Code  : ").append(code).append('\n');
        text.append("Hazard Class   : ").append(hazard.isEmpty() ? "?" : hazard).append('\n');
        text.append("Severity       : ").append(severity.isEmpty() ? "?" : severity).append('\n');
        text.append("Declared       : ").append(declared).append('\n');
        text.append("District       : ").append(district.isEmpty() ? "?" : district).append('\n');
        text.append("Originator     : ").append(originator.isEmpty() ? "?" : originator).append('\n');
        text.append("\nNARRATIVE\n");
        text.append(narrative.isEmpty() ? "(no narrative supplied)\n" : narrative + "\n");

        Path reportsDir = dataDir.resolve("emergency_reports");
        Files.createDirectories(reportsDir);
        String slug = id.replaceAll("[^A-Za-z0-9_-]", "_");
        Path target = reportsDir.resolve(slug + ".txt");
        Files.writeString(target, text.toString(), StandardCharsets.UTF_8);
        out.detail("written", target);
        reload();
    }

    private String districtLine() {
        return join(
                ask("district id: "),
                ask("name: "),
                ask("population: "),
                ask("need water: "),
                ask("need medical: "),
                ask("need blankets: "),
                ask("priority: "),
                ask("x km: "),
                ask("y km: "));
    }

    private String warehouseLine() {
        return join(
                ask("warehouse id: "),
                ask("name: "),
                ask("water packets: "),
                ask("medical kits: "),
                ask("blanket sets: "),
                ask("road capacity/day: "));
    }

    private String teamLine() {
        String id = ask("team id: ");
        String name = ask("name: ");
        String caps = ask("capabilities (comma separated): ");
        String sectors = ask("sectors (semicolon separated): ");
        String shift = ask("shift hours: ");
        return join(id, name, caps, sectors, shift);
    }

    private String readingLine() {
        String stamp = ask("timestamp (default now): ").strip();
        if (stamp.isEmpty()) {
            stamp = LocalDateTime.now().format(STAMP);
        }
        return join(
                stamp,
                ask("sensor id: "),
                ask("water level m: "),
                ask("wind kmh: "),
                ask("smoke index: "),
                ask("status: "),
                ask("severity: "));
    }

    private String statusLine() {
        String stamp = ask("timestamp (default now): ").strip();
        if (stamp.isEmpty()) {
            stamp = LocalDateTime.now().format(STAMP);
        }
        return join(
                stamp,
                ask("team id: "),
                ask("mission: "),
                ask("casualties handled: "),
                ask("status: "),
                ask("severity: "));
    }

    private String routeLine() {
        return join(
                ask("from node: "),
                ask("to node: "),
                ask("capacity: "),
                ask("status (OPEN/BLOCKED): "));
    }

    private void append(Path file, String line) throws IOException {
        Files.createDirectories(file.getParent());
        String existing = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        String prefix = existing.isEmpty() || existing.endsWith("\n") ? "" : "\n";
        Files.writeString(file, prefix + line + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        out.detail("appended", file.getFileName());
        reload();
    }

    private Path resourceData(String name) {
        return dataDir.resolve("resource_data").resolve(name);
    }

    private Path fieldData(String name) {
        return dataDir.resolve("field_observations").resolve(name);
    }

    // ------------------------------------------------------------------ option 9

    private void inventory() {
        out.line("");
        out.line("Relief resource inventory, by warehouse");

        int water = 0;
        int medical = 0;
        int blankets = 0;
        List<List<String>> rows = new ArrayList<>();
        for (InputHandler.Warehouse w : dataset.warehouses()) {
            water += w.waterPackets();
            medical += w.medicalKits();
            blankets += w.blanketSets();
            rows.add(List.of(w.id(), w.name(), String.valueOf(w.waterPackets()),
                    String.valueOf(w.medicalKits()), String.valueOf(w.blanketSets())));
        }
        rows.add(List.of("TOTAL", "", String.valueOf(water), String.valueOf(medical), String.valueOf(blankets)));
        out.printTable(List.of("id", "name", "water", "medical", "blankets"), rows);

        int needWater = 0;
        int needMedical = 0;
        int needBlankets = 0;
        for (InputHandler.District d : dataset.districts()) {
            needWater += d.needWater();
            needMedical += d.needMedical();
            needBlankets += d.needBlankets();
        }

        out.line("");
        out.line("Demand versus supply");
        List<List<String>> balance = new ArrayList<>();
        balance.add(balanceRow("water", water, needWater));
        balance.add(balanceRow("medical", medical, needMedical));
        balance.add(balanceRow("blankets", blankets, needBlankets));
        out.printTable(List.of("commodity", "supply", "demand", "balance"), balance);
        out.detail("teams on roster", dataset.teams().size());
        out.detail("districts", dataset.districts().size());
    }

    private List<String> balanceRow(String commodity, int supply, int demand) {
        int diff = supply - demand;
        String note = diff >= 0 ? "+" + diff + " surplus" : diff + " shortfall";
        return List.of(commodity, String.valueOf(supply), String.valueOf(demand), note);
    }

    // ------------------------------------------------------------------ helpers

    private void reload() throws IOException {
        dataset = InputHandler.load(dataDir);
    }

    private static String join(String... parts) {
        List<String> clean = new ArrayList<>();
        for (String part : parts) {
            clean.add(part == null ? "" : part.strip());
        }
        return String.join(",", clean);
    }

    private String ask(String prompt) {
        System.out.print(prompt);
        System.out.flush();
        try {
            String line = in.readLine();
            if (line == null) {
                eof = true;
                return "";
            }
            return line;
        } catch (IOException e) {
            eof = true;
            return "";
        }
    }

    private int askInt(String prompt, int fallback) {
        String raw = ask(prompt).strip();
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void pause() {
        ask("\nPress Enter to return to the menu...");
    }
}
