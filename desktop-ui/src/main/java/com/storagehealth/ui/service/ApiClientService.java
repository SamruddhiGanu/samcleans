package com.storagehealth.ui.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.storagehealth.ui.explorer.DuplicateGroupRow;
import com.storagehealth.ui.panels.RecommendationRow;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/**
 * Lightweight HTTP client that connects the JavaFX UI to the
 * Spring Boot backend REST API running on {@code localhost:8080}.
 *
 * <p>Uses Java 11+ {@link HttpClient} with a 30-second timeout.
 * All methods are synchronous — callers must invoke them on a background thread.
 */
public class ApiClientService {

    private static final String BASE_URL = "http://localhost:8080";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .build();

    private final ObjectMapper json = new ObjectMapper()
        .findAndRegisterModules(); // picks up jackson-datatype-jsr310 for LocalDateTime

    // ---------------------------------------------------------------
    // Scan API
    // ---------------------------------------------------------------

    /** Starts a new scan and returns the assigned session ID. */
    public Long startScan(String path, String name) throws Exception {
        String body = String.format("{\"path\":\"%s\",\"name\":\"%s\"}",
            escapeJson(path), escapeJson(name));
        String response = post("/api/scan/start", body);
        JsonNode node = json.readTree(response);
        return node.get("id").asLong();
    }

    /** Returns the current status string of a scan session (e.g. "COMPLETED"). */
    public String getScanStatus(Long sessionId) throws Exception {
        String response = get("/api/scan/" + sessionId);
        JsonNode node = json.readTree(response);
        return node.get("status").asText();
    }

    /** Requests cancellation of the running scan for the given session. */
    public void cancelScan(Long sessionId) {
        try { post("/api/scan/cancel/" + sessionId, ""); } catch (Exception ignored) {}
    }

    // ---------------------------------------------------------------
    // Health API
    // ---------------------------------------------------------------

    /**
     * Returns health scores as a double[4]:
     * [0]=overall, [1]=duplicateWaste, [2]=clutter, [3]=organisation
     */
    public double[] getHealthScores(Long sessionId) throws Exception {
        String response = get("/api/health/score/" + sessionId);
        JsonNode node = json.readTree(response);
        return new double[]{
            node.path("overallScore").asDouble(0),
            node.path("duplicateWasteScore").asDouble(0),
            node.path("clutterScore").asDouble(0),
            node.path("organizationScore").asDouble(0)
        };
    }

    // ---------------------------------------------------------------
    // Duplicate API
    // ---------------------------------------------------------------

    /** Triggers duplicate detection and returns the resulting groups as UI rows. */
    public List<DuplicateGroupRow> detectDuplicates(Long sessionId) throws Exception {
        String response = post("/api/duplicates/detect/" + sessionId, "");
        JsonNode root = json.readTree(response);
        List<DuplicateGroupRow> rows = new ArrayList<>();
        for (JsonNode g : root.path("groups")) {
            rows.add(new DuplicateGroupRow(
                g.path("hashValue").asText("").substring(0, Math.min(16, g.path("hashValue").asText("").length())),
                g.path("fileCount").asInt(0),
                g.path("totalSize").asLong(0),
                g.path("recoverableSpace").asLong(0)
            ));
        }
        return rows;
    }

    // ---------------------------------------------------------------
    // Recommendation API
    // ---------------------------------------------------------------

    /** Triggers recommendation generation for a session. */
    public void generateRecommendations(Long sessionId) throws Exception {
        post("/api/recommendations/generate/" + sessionId, "");
    }

    /**
     * Fetches recommendations for a session, optionally filtered by type.
     * @param type null or empty for all types
     */
    public List<RecommendationRow> getRecommendations(Long sessionId, String type) throws Exception {
        String url = "/api/recommendations/list?page=0&size=200"
            + (type != null && !type.isBlank() ? "&type=" + type : "");
        String response = get(url);
        JsonNode root = json.readTree(response);
        List<RecommendationRow> rows = new ArrayList<>();
        for (JsonNode r : root.path("content")) {
            rows.add(new RecommendationRow(
                r.path("id").asLong(),
                r.path("type").asText(),
                r.path("fileName").asText(),
                r.path("filePath").asText(),
                String.format("%.0f%%", r.path("confidenceScore").asDouble(0) * 100),
                r.path("recoverableSpace").asLong(0),
                r.path("explanation").asText()
            ));
        }
        return rows;
    }

    // ---------------------------------------------------------------
    // HTTP helpers
    // ---------------------------------------------------------------

    private String get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + path))
            .timeout(TIMEOUT)
            .GET()
            .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) {
            throw new RuntimeException("API error " + res.statusCode() + ": " + res.body());
        }
        return res.body();
    }

    private String post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + path))
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) {
            throw new RuntimeException("API error " + res.statusCode() + ": " + res.body());
        }
        return res.body();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
