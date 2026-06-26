package com.yzzhang.weeklyreport;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class WeeklyReportServer {
    private static final Pattern WEEK_PATTERN = Pattern.compile("\\d{4}-W\\d{2}");
    private static final String JSON = "application/json; charset=utf-8";
    private static final String TEXT = "text/plain; charset=utf-8";
    private static final ExecutorService JOB_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final List<JobRecord> JOBS = new ArrayList<>();
    private static volatile boolean jobRunning = false;

    private final Path projectRoot;
    private final Path outputRoot;
    private final Path frontendDist;

    public WeeklyReportServer(Path projectRoot) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.outputRoot = this.projectRoot.resolve("output");
        this.frontendDist = this.projectRoot.resolve("web").resolve("frontend").resolve("dist");
    }

    public static void main(String[] args) throws Exception {
        Path root = resolveProjectRoot(args);
        int port = Integer.parseInt(System.getenv().getOrDefault("WEEKLY_REPORT_PORT", "8088"));
        WeeklyReportServer app = new WeeklyReportServer(root);
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", app::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Weekly report web server started on http://0.0.0.0:" + port);
        System.out.println("Project root: " + root.toAbsolutePath().normalize());
    }

    private static Path resolveProjectRoot(String[] args) {
        if (args.length > 0 && !args[0].isBlank()) {
            return Path.of(args[0]);
        }
        String property = System.getProperty("weekly.projectRoot", "");
        if (!property.isBlank()) {
            return Path.of(property);
        }
        String env = System.getenv().getOrDefault("WEEKLY_REPORT_ROOT", "");
        if (!env.isBlank()) {
            return Path.of(env);
        }
        return Path.of(".");
    }

    private void handle(HttpExchange exchange) throws IOException {
        addCommonHeaders(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                handleApi(exchange, path);
            } else {
                serveStatic(exchange, path);
            }
        } catch (NotFoundException exc) {
            sendJson(exchange, 404, Map.of("error", exc.getMessage()));
        } catch (IllegalArgumentException exc) {
            sendJson(exchange, 400, Map.of("error", exc.getMessage()));
        } catch (Exception exc) {
            exc.printStackTrace();
            sendJson(exchange, 500, Map.of("error", exc.getMessage() == null ? exc.getClass().getName() : exc.getMessage()));
        } finally {
            exchange.close();
        }
    }

    private void handleApi(HttpExchange exchange, String path) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        if (method.equals("GET") && path.equals("/api/health")) {
            sendJson(exchange, 200, Map.of("status", "ok", "projectRoot", projectRoot.toString()));
            return;
        }
        if (method.equals("GET") && path.equals("/api/weeks")) {
            sendJson(exchange, 200, listWeeks());
            return;
        }
        if (method.equals("GET") && path.equals("/api/weeks/latest")) {
            List<Map<String, Object>> weeks = listWeeks();
            sendJson(exchange, 200, weeks.isEmpty() ? Map.of() : weeks.get(0));
            return;
        }
        if (method.equals("GET") && path.matches("/api/weeks/[^/]+/submission-status")) {
            sendJson(exchange, 200, readSubmissionRows(segment(path, 3)));
            return;
        }
        if (method.equals("GET") && path.matches("/api/weeks/[^/]+/summary")) {
            sendJson(exchange, 200, readSummary(segment(path, 3)));
            return;
        }
        if (method.equals("GET") && path.matches("/api/weeks/[^/]+/analysis")) {
            sendJson(exchange, 200, readAnalysis(segment(path, 3)));
            return;
        }
        if (method.equals("GET") && path.matches("/api/files/[^/]+/submission-status/download")) {
            String week = segment(path, 3);
            sendFile(exchange, weekDir(week).resolve("exports").resolve("submission_status.csv"), "text/csv; charset=utf-8", "submission_status_" + week + ".csv");
            return;
        }
        if (method.equals("POST") && path.equals("/api/jobs/run")) {
            startJob(exchange);
            return;
        }
        if (method.equals("GET") && path.equals("/api/jobs/latest")) {
            sendJson(exchange, 200, latestJob().map(JobRecord::toMap).orElseGet(Map::of));
            return;
        }
        if (method.equals("GET") && path.equals("/api/jobs")) {
            List<Map<String, Object>> records = new ArrayList<>();
            synchronized (JOBS) {
                JOBS.stream().sorted(Comparator.comparing(JobRecord::startedAt).reversed()).forEach(job -> records.add(job.toMap()));
            }
            sendJson(exchange, 200, records);
            return;
        }
        throw new NotFoundException("Unknown API: " + path);
    }

    private void startJob(HttpExchange exchange) throws IOException {
        if (jobRunning) {
            sendJson(exchange, 409, Map.of("error", "A weekly report job is already running."));
            return;
        }
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String week = query.getOrDefault("week", "previous");
        if (!week.equals("previous") && !week.equals("current")) {
            throw new IllegalArgumentException("week must be previous or current");
        }
        JobRecord job = new JobRecord(UUID.randomUUID().toString(), week);
        synchronized (JOBS) {
            JOBS.add(job);
        }
        jobRunning = true;
        JOB_EXECUTOR.submit(() -> runJob(job));
        sendJson(exchange, 202, job.toMap());
    }

    private void runJob(JobRecord job) {
        try {
            List<String> command = pythonCommand();
            command.add("scripts/run_weekly.py");
            command.add("--week");
            command.add(job.weekMode);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(projectRoot.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = readLimited(process.getInputStream(), 20000);
            int exit = process.waitFor();
            job.stdout = output;
            job.finishedAt = Instant.now();
            if (exit == 0) {
                job.status = "SUCCESS";
                job.weekLabel = detectWeekFromOutput(output).orElse(guessLatestWeek());
            } else {
                job.status = "FAILED";
                job.errorMessage = "run_weekly.py exited with code " + exit;
            }
            writeJobLog(job);
        } catch (Exception exc) {
            job.status = "FAILED";
            job.errorMessage = exc.getMessage() == null ? exc.getClass().getName() : exc.getMessage();
            job.finishedAt = Instant.now();
            writeJobLog(job);
        } finally {
            jobRunning = false;
        }
    }

    private List<Map<String, Object>> listWeeks() throws IOException {
        if (!Files.isDirectory(outputRoot)) {
            return List.of();
        }
        List<Map<String, Object>> weeks = new ArrayList<>();
        try (Stream<Path> stream = Files.list(outputRoot)) {
            stream.filter(Files::isDirectory)
                .filter(path -> WEEK_PATTERN.matcher(path.getFileName().toString()).matches())
                .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                .forEach(path -> weeks.add(weekOverview(path.getFileName().toString())));
        }
        return weeks;
    }

    private Map<String, Object> weekOverview(String week) {
        List<Map<String, String>> rows = readSubmissionRowsQuietly(week);
        long submitted = rows.stream().filter(row -> "已提交".equals(row.get("提交状态"))).count();
        long missing = rows.stream().filter(row -> "未提交".equals(row.get("提交状态"))).count();
        long leaders = rows.stream().filter(row -> "是".equals(row.get("是否负责人候选"))).count();
        Path dir = weekDir(week);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("week", week);
        result.put("expectedCount", rows.size());
        result.put("submittedCount", submitted);
        result.put("missingCount", missing);
        result.put("leaderCandidateCount", leaders);
        result.put("hasManagerReport", Files.exists(dir.resolve("summary").resolve("manager_report.md")));
        result.put("generatedAt", latestModified(dir).map(Instant::toString).orElse(""));
        return result;
    }

    private Map<String, Object> readSummary(String week) throws IOException {
        Path dir = weekDir(week);
        Map<String, Object> result = weekOverview(week);
        result.put("submissionSummary", readFileIfExists(dir.resolve("summary").resolve("submission_check.md")));
        result.put("managerReport", readFileIfExists(dir.resolve("summary").resolve("manager_report.md")));
        return result;
    }

    private Map<String, Object> readAnalysis(String week) throws IOException {
        Path dir = weekDir(week);
        Path manager = dir.resolve("summary").resolve("manager_report.md");
        Path analysis = dir.resolve("analysis").resolve("analysis_input.md");
        Path source = Files.exists(manager) ? manager : analysis;
        return Map.of(
            "week", week,
            "source", projectRoot.relativize(source).toString().replace('\\', '/'),
            "content", readFileIfExists(source),
            "isManagerReport", Files.exists(manager)
        );
    }

    private List<Map<String, String>> readSubmissionRows(String week) throws IOException {
        Path csv = weekDir(week).resolve("exports").resolve("submission_status.csv");
        if (!Files.exists(csv)) {
            throw new NotFoundException("submission_status.csv not found for " + week);
        }
        return parseCsv(csv);
    }

    private List<Map<String, String>> readSubmissionRowsQuietly(String week) {
        try {
            return readSubmissionRows(week);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Path weekDir(String week) {
        if (!WEEK_PATTERN.matcher(week).matches()) {
            throw new IllegalArgumentException("Invalid week label: " + week);
        }
        return outputRoot.resolve(week).normalize();
    }

    private List<Map<String, String>> parseCsv(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return List.of();
        }
        List<String> headers = parseCsvLine(stripBom(lines.get(0)));
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            List<String> values = parseCsvLine(lines.get(i));
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                row.put(headers.get(j), j < values.size() ? values.get(j) : "");
            }
            rows.add(row);
        }
        return rows;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == ',') {
                values.add(current.toString());
                current.setLength(0);
            } else if (c == '"') {
                quoted = true;
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        return values;
    }

    private Optional<Instant> latestModified(Path dir) {
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                .map(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toInstant();
                    } catch (IOException exc) {
                        return Instant.EPOCH;
                    }
                })
                .max(Comparator.naturalOrder());
        } catch (IOException exc) {
            return Optional.empty();
        }
    }

    private Optional<String> detectWeekFromOutput(String output) {
        java.util.regex.Matcher matcher = WEEK_PATTERN.matcher(output);
        String last = null;
        while (matcher.find()) {
            last = matcher.group();
        }
        return Optional.ofNullable(last);
    }

    private String guessLatestWeek() {
        try {
            List<Map<String, Object>> weeks = listWeeks();
            if (!weeks.isEmpty()) {
                return String.valueOf(weeks.get(0).get("week"));
            }
        } catch (IOException ignored) {
        }
        return "";
    }

    private Optional<JobRecord> latestJob() {
        synchronized (JOBS) {
            return JOBS.stream().max(Comparator.comparing(JobRecord::startedAt));
        }
    }

    private void writeJobLog(JobRecord job) {
        try {
            Path logDir = projectRoot.resolve("logs");
            Files.createDirectories(logDir);
            Path log = logDir.resolve("jobs.jsonl");
            Files.writeString(log, toJson(job.toMap()) + System.lineSeparator(), StandardCharsets.UTF_8,
                Files.exists(log) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
        } catch (IOException ignored) {
        }
    }

    private void serveStatic(HttpExchange exchange, String path) throws IOException {
        if (!Files.isDirectory(frontendDist)) {
            sendText(exchange, 200, "Vue frontend is not built yet. Run: cd web/frontend && npm install && npm run build");
            return;
        }
        String cleaned = path.equals("/") ? "/index.html" : path;
        Path file = frontendDist.resolve(cleaned.substring(1)).normalize();
        if (!file.startsWith(frontendDist) || !Files.exists(file) || Files.isDirectory(file)) {
            file = frontendDist.resolve("index.html");
        }
        sendFile(exchange, file, contentType(file), null);
    }

    private void sendFile(HttpExchange exchange, Path file, String contentType, String downloadName) throws IOException {
        if (!Files.exists(file)) {
            throw new NotFoundException(file + " not found");
        }
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        if (downloadName != null) {
            headers.set("Content-Disposition", "attachment; filename=\"" + downloadName + "\"");
        }
        byte[] data = Files.readAllBytes(file);
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] data = toJson(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", JSON);
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private void sendText(HttpExchange exchange, int status, String value) throws IOException {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", TEXT);
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private void addCommonHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type,X-Access-Token");
        headers.set("Cache-Control", "no-store");
    }

    private static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String str) {
            return quote(str);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(quote(String.valueOf(entry.getKey()))).append(':').append(toJson(entry.getValue()));
            }
            return sb.append('}').toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : iterable) {
                if (!first) sb.append(',');
                first = false;
                sb.append(toJson(item));
            }
            return sb.append(']').toString();
        }
        return quote(String.valueOf(value));
    }

    private static String quote(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return map;
        }
        for (String part : raw.split("&")) {
            String[] pieces = part.split("=", 2);
            String key = decode(pieces[0]);
            String value = pieces.length > 1 ? decode(pieces[1]) : "";
            map.put(key, value);
        }
        return map;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String segment(String path, int index) {
        String[] parts = path.split("/");
        if (index >= parts.length) {
            throw new IllegalArgumentException("Missing path segment");
        }
        return parts[index];
    }

    private static String readFileIfExists(Path path) throws IOException {
        return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
    }

    private static String stripBom(String value) {
        return value != null && value.startsWith("\ufeff") ? value.substring(1) : value;
    }

    private static String readLimited(InputStream input, int maxChars) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() < maxChars) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static List<String> pythonCommand() {
        String configured = System.getenv().getOrDefault("PYTHON_BIN", "").trim();
        if (!configured.isEmpty()) {
            return new ArrayList<>(List.of(configured));
        }
        if (isWindows()) {
            return new ArrayList<>(List.of("py", "-3"));
        }
        return new ArrayList<>(List.of("python3"));
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".json")) return JSON;
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    private static final class JobRecord {
        private final String id;
        private final String weekMode;
        private final Instant startedAt = Instant.now();
        private Instant finishedAt;
        private String status = "RUNNING";
        private String stdout = "";
        private String errorMessage = "";
        private String weekLabel = "";

        private JobRecord(String id, String weekMode) {
            this.id = id;
            this.weekMode = weekMode;
        }

        public Instant startedAt() {
            return startedAt;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("weekMode", weekMode);
            map.put("weekLabel", weekLabel);
            map.put("status", status);
            map.put("startedAt", startedAt.toString());
            map.put("finishedAt", finishedAt == null ? "" : finishedAt.toString());
            map.put("stdout", stdout);
            map.put("errorMessage", errorMessage);
            return map;
        }
    }

    private static final class NotFoundException extends RuntimeException {
        private NotFoundException(String message) {
            super(message);
        }
    }
}
