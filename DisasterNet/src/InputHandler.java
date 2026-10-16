/*
 * DisasterNet pipeline - Input stage
 *
 * Purpose:
 * - Read all files under data/ into strongly typed Java records
 * - Collect data-quality warnings instead of silently dropping bad rows
 * - Validate cross-file references (districts, teams, corridors)
 *
 * Notes on parsing:
 * - rescue_teams.csv has an unquoted comma inside the capabilities field
 *   (e.g. "water-rescue,medical"). A naive split(",") would break it.
 *   We anchor on: first field = team_id, last field = shift_hours,
 *   and the field containing ';' = sectors list. Everything between
 *   name and sectors is treated as capabilities.
 * - Lines starting with '#' are treated as comments
 * - Blank lines are ignored
 */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class InputHandler {

    // ------------------------------------------------------------------------
    // Data records
    // ------------------------------------------------------------------------

    /**
     * Incident code from the registry (codes.txt)
     */
    public record Code(
            String code,
            String hazardClass,
            int priority,
            String description,
            List<String> keywords
    ) {
        @Override
        public String toString() {
            return code + " (" + hazardClass + ", priority " + priority + ")";
        }
    }

    /**
     * Emergency report loaded from emergency_reports/*.txt
     */
    public record Report(
            String fileName,
            String reportId,
            String incidentCode,
            String hazardClass,
            String severity,
            String declared,
            String district,
            String originator,
            String text
    ) {
        /**
         * Returns the narrative+actions portion for phrase-level analysis
         */
        public String body() {
            int narrativeIndex = text.indexOf("NARRATIVE");

            if (narrativeIndex < 0) {
                return text;
            }

            return text.substring(narrativeIndex);
        }
    }

    /**
     * District with demand and coordinates
     */
    public record District(
            String id,
            String name,
            int population,
            int needWater,
            int needMedical,
            int needBlankets,
            int priority,
            double x,
            double y
    ) {
        public int totalNeed() {
            return needWater + needMedical + needBlankets;
        }
    }

    /**
     * Relief warehouse
     */
    public record Warehouse(
            String id,
            String name,
            int waterPackets,
            int medicalKits,
            int blanketSets,
            int roadCapacityPerDay
    ) {
        // Intentionally minimal
    }

    /**
     * Road corridor between nodes
     */
    public record Route(
            String from,
            String to,
            int capacity,
            boolean blocked
    ) {
        // Intentionally minimal
    }

    /**
     * Rescue team
     */
    public record Team(
            String id,
            String name,
            List<String> capabilities,
            List<String> sectors,
            int shiftHours
    ) {
        // Intentionally minimal
    }

    /**
     * Sensor telemetry reading
     */
    public record SensorReading(
            String timestamp,
            String sensorId,
            double waterLevel,
            int windKmh,
            int smokeIndex,
            String status,
            int severity
    ) {
        // Intentionally minimal
    }

    /**
     * Team status feed entry
     */
    public record TeamStatus(
            String timestamp,
            String teamId,
            String mission,
            int casualtiesHandled,
            String status,
            int severity
    ) {
        // Intentionally minimal
    }

    /**
     * Complete dataset loaded from data/
     */
    public record Dataset(
            List<Code> codes,
            List<Report> reports,
            List<District> districts,
            List<Warehouse> warehouses,
            List<Route> routes,
            List<Team> teams,
            List<SensorReading> readings,
            List<TeamStatus> teamStatus,
            List<String> warnings
    ) {

        public Map<String, Code> codesByCode() {
            Map<String, Code> map = new LinkedHashMap<>();

            for (Code code : codes) {
                map.put(code.code(), code);
            }

            return map;
        }

        public Map<String, District> districtsById() {
            Map<String, District> map = new LinkedHashMap<>();

            for (District district : districts) {
                map.put(district.id(), district);
            }

            return map;
        }

        public Map<String, Warehouse> warehousesById() {
            Map<String, Warehouse> map = new LinkedHashMap<>();

            for (Warehouse warehouse : warehouses) {
                map.put(warehouse.id(), warehouse);
            }

            return map;
        }

        public Map<String, Team> teamsById() {
            Map<String, Team> map = new LinkedHashMap<>();

            for (Team team : teams) {
                map.put(team.id(), team);
            }

            return map;
        }
    }

    // ------------------------------------------------------------------------
    // Fields & helpers
    // ------------------------------------------------------------------------

    private final List<String> warnings = new ArrayList<>();

    private InputHandler() {
        // Utility class
    }

    // ------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------

    public static Dataset load(Path dataDir) throws IOException {
        InputHandler handler = new InputHandler();

        // Load all inputs
        Path codesPath = dataDir.resolve("incident_codes/codes.txt");
        Path reportsDir = dataDir.resolve("emergency_reports");
        Path districtsPath = dataDir.resolve("resource_data/districts.csv");
        Path warehousesPath = dataDir.resolve("resource_data/warehouses.csv");
        Path routesPath = dataDir.resolve("resource_data/routes.csv");
        Path teamsPath = dataDir.resolve("resource_data/rescue_teams.csv");
        Path readingsPath = dataDir.resolve("field_observations/sensor_readings.csv");
        Path statusPath = dataDir.resolve("field_observations/team_status.csv");

        List<Code> codes = handler.readCodes(codesPath);
        List<Report> reports = handler.readReports(reportsDir);
        List<District> districts = handler.readDistricts(districtsPath);
        List<Warehouse> warehouses = handler.readWarehouses(warehousesPath);
        List<Route> routes = handler.readRoutes(routesPath);
        List<Team> teams = handler.readTeams(teamsPath);
        List<SensorReading> readings = handler.readReadings(readingsPath);
        List<TeamStatus> teamStatusList = handler.readTeamStatus(statusPath);

        Dataset dataset = new Dataset(
                codes,
                reports,
                districts,
                warehouses,
                routes,
                teams,
                readings,
                teamStatusList,
                List.copyOf(handler.warnings)
        );

        // Cross-file validation
        handler.validate(dataset);

        return dataset;
    }

    public static Dataset loadOrFail(String dataDir) {
        try {
            return load(Path.of(dataDir));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "cannot read data from " + dataDir + ": " + e.getMessage(),
                    e
            );
        }
    }

    public static String describe(Dataset dataset) {
        return String.join(" | ",
                dataset.codes().size() + " codes",
                dataset.reports().size() + " reports",
                dataset.districts().size() + " districts",
                dataset.warehouses().size() + " warehouses",
                dataset.routes().size() + " corridors",
                dataset.teams().size() + " teams",
                dataset.readings().size() + " readings",
                dataset.teamStatus().size() + " status entries"
        );
    }

    // ------------------------------------------------------------------------
    // Low-level file helpers
    // ------------------------------------------------------------------------

    private static List<String> dataLines(Path path) throws IOException {
        List<String> result = new ArrayList<>();
        List<String> allLines = Files.readAllLines(path, StandardCharsets.UTF_8);

        for (String line : allLines) {
            String trimmed = line.strip();

            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.startsWith("#")) {
                continue;
            }

            result.add(trimmed);
        }

        return result;
    }

    private static String[] splitCsv(String line) {
        return line.split(",", -1);
    }

    // ------------------------------------------------------------------------
    // Field parsers (track warnings)
    // ------------------------------------------------------------------------

    private int intField(String raw, String where, int fallback) {
        String stripped = raw.strip();

        try {
            return Integer.parseInt(stripped);
        } catch (NumberFormatException e) {
            warnings.add(where + ": expected an integer but found '" + raw + "'");
            return fallback;
        }
    }

    private double doubleField(String raw, String where, double fallback) {
        String stripped = raw.strip();

        try {
            return Double.parseDouble(stripped);
        } catch (NumberFormatException e) {
            warnings.add(where + ": expected a number but found '" + raw + "'");
            return fallback;
        }
    }

    // ------------------------------------------------------------------------
    // Readers per file
    // ------------------------------------------------------------------------

    private List<Code> readCodes(Path path) throws IOException {
        List<Code> codes = new ArrayList<>();
        List<String> lines = dataLines(path);

        for (String line : lines) {
            // codes.txt is pipe-delimited: CODE | HAZARD | PRIORITY | DESCRIPTION | KEYWORDS
            String[] parts = line.split("\\|", -1);

            if (parts.length < 5) {
                warnings.add("codes.txt: expected 5 pipe separated fields, found "
                        + parts.length + " in '" + line + "'");
                continue;
            }

            String codeStr = parts[0].strip();
            String hazardStr = parts[1].strip();
            int priorityVal = intField(parts[2], "codes.txt:" + codeStr, 1);
            String descStr = parts[3].strip();

            // Keywords are comma-separated
            List<String> keywordsList = new ArrayList<>();
            String keywordsField = parts[4];

            if (!keywordsField.isEmpty()) {
                String[] keywordTokens = keywordsField.split(",");

                for (String token : keywordTokens) {
                    String kw = token.strip();

                    if (!kw.isEmpty()) {
                        keywordsList.add(kw);
                    }
                }
            }

            codes.add(new Code(
                    codeStr,
                    hazardStr,
                    priorityVal,
                    descStr,
                    List.copyOf(keywordsList)
            ));
        }

        return codes;
    }

    private List<Report> readReports(Path dir) throws IOException {
        List<Report> reports = new ArrayList<>();

        if (!Files.exists(dir)) {
            warnings.add("emergency_reports: directory not found: " + dir);
            return reports;
        }

        try (var stream = Files.list(dir)) {
            List<Path> paths = stream
                    .filter(p -> p.toString().endsWith(".txt"))
                    .sorted()
                    .toList();

            for (Path path : paths) {
                reports.add(readReport(path));
            }
        }

        return reports;
    }

    private Report readReport(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        Map<String, String> fields = new LinkedHashMap<>();

        // Parse header lines: KEY: VALUE
        String[] lines = text.split("\\R");

        for (String line : lines) {
            int colonIndex = line.indexOf(':');

            if (colonIndex <= 0) {
                continue;
            }

            String left = line.substring(0, colonIndex).strip();
            String right = line.substring(colonIndex + 1).strip();

            if (left.isEmpty()) {
                continue;
            }

            // Only treat lines that look like headers as fields
            if (!Character.isLetterOrDigit(left.charAt(0))) {
                continue;
            }

            // putIfAbsent to avoid overwriting real header with status line
            if (!fields.containsKey(left)) {
                fields.put(left, right);
            }
        }

        String fileName = path.getFileName().toString();
        String reportId = fields.getOrDefault("Report ID", "?");
        String incidentCode = fields.getOrDefault("Incident Code", "?");
        String hazardClass = fields.getOrDefault("Hazard Class", "?");
        String severity = fields.getOrDefault("Severity", "?");
        String declared = fields.getOrDefault("Declared", "?");
        String district = fields.getOrDefault("District", "?");
        String originator = fields.getOrDefault("Originator", "?");

        return new Report(
                fileName,
                reportId,
                incidentCode,
                hazardClass,
                severity,
                declared,
                district,
                originator,
                text
        );
    }

    private List<District> readDistricts(Path path) throws IOException {
        List<District> districts = new ArrayList<>();
        List<String> lines = dataLines(path);

        for (String line : lines) {
            String[] p = splitCsv(line);

            if (p.length < 9) {
                warnings.add("districts.csv: expected 9 fields, found "
                        + p.length + " in '" + line + "'");
                continue;
            }

            String id = p[0].strip();
            String name = p[1].strip();
            int population = intField(p[2], "districts.csv:" + id, 0);
            int needWater = intField(p[3], "districts.csv:" + id, 0);
            int needMedical = intField(p[4], "districts.csv:" + id, 0);
            int needBlankets = intField(p[5], "districts.csv:" + id, 0);
            int priority = intField(p[6], "districts.csv:" + id, 1);
            double x = doubleField(p[7], "districts.csv:" + id, 0.0);
            double y = doubleField(p[8], "districts.csv:" + id, 0.0);

            districts.add(new District(
                    id,
                    name,
                    population,
                    needWater,
                    needMedical,
                    needBlankets,
                    priority,
                    x,
                    y
            ));
        }

        return districts;
    }

    private List<Warehouse> readWarehouses(Path path) throws IOException {
        List<Warehouse> warehouses = new ArrayList<>();
        List<String> lines = dataLines(path);

        for (String line : lines) {
            String[] p = splitCsv(line);

            if (p.length < 6) {
                warnings.add("warehouses.csv: expected 6 fields, found "
                        + p.length + " in '" + line + "'");
                continue;
            }

            String id = p[0].strip();
            String name = p[1].strip();
            int waterPackets = intField(p[2], "warehouses.csv:" + id, 0);
            int medicalKits = intField(p[3], "warehouses.csv:" + id, 0);
            int blanketSets = intField(p[4], "warehouses.csv:" + id, 0);
            int roadCap = intField(p[5], "warehouses.csv:" + id, 0);

            warehouses.add(new Warehouse(
                    id,
                    name,
                    waterPackets,
                    medicalKits,
                    blanketSets,
                    roadCap
            ));
        }

        return warehouses;
    }

    private List<Route> readRoutes(Path path) throws IOException {
        List<Route> routes = new ArrayList<>();
        List<String> lines = dataLines(path);

        for (String line : lines) {
            String[] p = splitCsv(line);

            if (p.length < 4) {
                warnings.add("routes.csv: expected 4 fields, found "
                        + p.length + " in '" + line + "'");
                continue;
            }

            String from = p[0].strip();
            String to = p[1].strip();
            int capacity = intField(p[2], "routes.csv:" + from, 0);
            String status = p[3].strip();

            boolean blocked = !"OPEN".equalsIgnoreCase(status);

            if (blocked) {
                warnings.add("routes.csv: corridor " + from + " -> " + to
                        + " has status " + status + " and will be modelled as zero capacity");
            }

            routes.add(new Route(from, to, capacity, blocked));
        }

        return routes;
    }

    // Special handling for rescue_teams.csv due to unquoted comma in capabilities
    private List<Team> readTeams(Path path) throws IOException {
        List<Team> teams = new ArrayList<>();
        List<String> lines = dataLines(path);

        for (String line : lines) {
            String[] p = splitCsv(line);

            // Find sector list (contains ';')
            int sectorAt = -1;
            for (int i = 1; i < p.length; i++) {
                if (p[i].contains(";")) {
                    sectorAt = i;
                    break;
                }
            }

            int shiftAt = p.length - 1;

            if (sectorAt < 0 || shiftAt <= sectorAt) {
                warnings.add("rescue_teams.csv: cannot locate the sector list in '" + line + "'");
                continue;
            }

            String id = p[0].strip();
            String name = p[1].strip();

            // Capabilities are everything between name and sectors
            List<String> capabilities = new ArrayList<>();
            for (int i = 2; i < sectorAt; i++) {
                String cap = p[i].strip();
                if (!cap.isEmpty()) {
                    capabilities.add(cap);
                }
            }

            // Sectors are semicolon-split
            List<String> sectors = new ArrayList<>();
            String sectorsField = p[sectorAt];
            String[] sectorTokens = sectorsField.split(";");

            for (String token : sectorTokens) {
                String s = token.strip();
                if (!s.isEmpty()) {
                    sectors.add(s);
                }
            }

            int shiftHours = intField(p[shiftAt], "rescue_teams.csv:" + id, 0);

            teams.add(new Team(
                    id,
                    name,
                    List.copyOf(capabilities),
                    List.copyOf(sectors),
                    shiftHours
            ));
        }

        return teams;
    }

    private List<SensorReading> readReadings(Path path) throws IOException {
        List<SensorReading> readings = new ArrayList<>();
        List<String> lines = dataLines(path);

        for (String line : lines) {
            String[] p = splitCsv(line);

            if (p.length < 7) {
                warnings.add("sensor_readings.csv: expected 7 fields, found "
                        + p.length + " in '" + line + "'");
                continue;
            }

            String timestamp = p[0].strip();
            String sensorId = p[1].strip();
            double waterLevel = doubleField(p[2], "sensor_readings.csv:" + timestamp, 0.0);
            int windKmh = intField(p[3], "sensor_readings.csv:" + timestamp, 0);
            int smokeIndex = intField(p[4], "sensor_readings.csv:" + timestamp, 0);
            String status = p[5].strip();
            int severity = intField(p[6], "sensor_readings.csv:" + timestamp, 1);

            readings.add(new SensorReading(
                    timestamp,
                    sensorId,
                    waterLevel,
                    windKmh,
                    smokeIndex,
                    status,
                    severity
            ));
        }

        return readings;
    }

    private List<TeamStatus> readTeamStatus(Path path) throws IOException {
        List<TeamStatus> statusList = new ArrayList<>();
        List<String> lines = dataLines(path);

        for (String line : lines) {
            String[] p = splitCsv(line);

            if (p.length < 6) {
                warnings.add("team_status.csv: expected 6 fields, found "
                        + p.length + " in '" + line + "'");
                continue;
            }

            String timestamp = p[0].strip();
            String teamId = p[1].strip();
            String mission = p[2].strip();
            int casualtiesHandled = intField(p[3], "team_status.csv:" + timestamp, 0);
            String status = p[4].strip();
            int severity = intField(p[5], "team_status.csv:" + timestamp, 1);

            statusList.add(new TeamStatus(
                    timestamp,
                    teamId,
                    mission,
                    casualtiesHandled,
                    status,
                    severity
            ));
        }

        return statusList;
    }

    // ------------------------------------------------------------------------
    // Cross-file validation
    // ------------------------------------------------------------------------

    private void validate(Dataset dataset) {
        // District ids
        Set<String> districtIds = new LinkedHashSet<>();
        for (District d : dataset.districts()) {
            if (!districtIds.add(d.id())) {
                warnings.add("districts.csv: duplicate district id " + d.id());
            }
        }

        // Warehouse ids
        Set<String> warehouseIds = new LinkedHashSet<>();
        for (Warehouse w : dataset.warehouses()) {
            if (!warehouseIds.add(w.id())) {
                warnings.add("warehouses.csv: duplicate warehouse id " + w.id());
            }
        }

        // Team ids + sector coverage
        Set<String> teamIds = new LinkedHashSet<>();
        for (Team t : dataset.teams()) {
            if (!teamIds.add(t.id())) {
                warnings.add("rescue_teams.csv: duplicate team id " + t.id());
            }

            for (String sector : t.sectors()) {
                if (!districtIds.contains(sector)) {
                    warnings.add("rescue_teams.csv: team " + t.id()
                            + " covers unknown sector " + sector);
                }
            }
        }

        // Known nodes for routes
        Set<String> knownNodes = new LinkedHashSet<>(warehouseIds);
        knownNodes.addAll(districtIds);

        for (Route r : dataset.routes()) {
            if (!knownNodes.contains(r.from())) {
                warnings.add("routes.csv: corridor starts at unknown node " + r.from());
            }
            if (!knownNodes.contains(r.to())) {
                warnings.add("routes.csv: corridor ends at unknown node " + r.to());
            }
        }

        // Sensor readings
        for (SensorReading reading : dataset.readings()) {
            int sev = reading.severity();
            if (sev < 1 || sev > 5) {
                warnings.add("sensor_readings.csv: severity " + sev
                        + " outside 1..5 at " + reading.timestamp());
            }
        }

        // Team status
        for (TeamStatus s : dataset.teamStatus()) {
            if (!teamIds.contains(s.teamId())) {
                warnings.add("team_status.csv: unknown team " + s.teamId()
                        + " at " + s.timestamp());
            }
        }

        // Codes
        for (Code code : dataset.codes()) {
            for (String keyword : code.keywords()) {
                if (keyword.isEmpty()) {
                    warnings.add("codes.txt: empty keyword in " + code.code());
                }
            }
        }
    }
}
