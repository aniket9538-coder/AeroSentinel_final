package com.aerosentinel.citizen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/**
 * Spring Boot HTTP REST Client for Gemini Vision Analysis of citizen observation photographs.
 * Calls FastAPI AI Service (/api/v1/ml/vision/analyze) via HTTP REST.
 * Fully resilient: never throws fatal errors that would cause citizen reports to be rolled back.
 */
@Component
public class CitizenVisionAiClient {

    private static final Logger log = LoggerFactory.getLogger(CitizenVisionAiClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final String serviceUrl;
    private final Duration timeout;
    private final HttpClient httpClient;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CitizenVisionResultDto(
            String status,
            String reportId,
            String h3Index,
            String category,
            Double confidence,
            List<String> observations,
            List<String> uncertainty,
            Map<String, Boolean> indicators,
            List<String> privacyFlags,
            String modelVersion,
            String promptVersion
    ) {
        public boolean isSuccessful() {
            return "SUCCESS".equalsIgnoreCase(status);
        }

        public static CitizenVisionResultDto createFallback(String reportId, String h3Index, String reason) {
            return new CitizenVisionResultDto(
                    "FALLBACK",
                    reportId,
                    h3Index,
                    "UNKNOWN",
                    0.50,
                    List.of("Visual observation unconfirmed: " + reason),
                    List.of("Image analysis offline or fallback activated", reason),
                    Collections.emptyMap(),
                    List.of("PII scan cleared"),
                    "deterministic-fallback",
                    "vision_analysis_v001"
            );
        }
    }

    @Autowired
    public CitizenVisionAiClient(
            @Value("${app.ai-service.url:http://localhost:8000}") String serviceUrl,
            @Value("${app.citizen.vision.timeout-ms:30000}") long timeoutMs
    ) {
        this(serviceUrl, timeoutMs, HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build());
    }

    public CitizenVisionAiClient(String serviceUrl, long timeoutMs, HttpClient httpClient) {
        this.serviceUrl = serviceUrl;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = httpClient;
    }

    /**
     * Backward-compatibility constructor for tests migrating from process bridge.
     */
    @Deprecated
    public CitizenVisionAiClient(String pythonCommand, String cliPath, long timeoutMs) {
        this("http://localhost:8000", timeoutMs);
    }

    /**
     * Executes vision analysis on the stored citizen image with bounded timeouts.
     *
     * @param imagePath absolute local path to stored photo
     * @param reportId  citizen report UUID
     * @param h3Index   Uber H3 resolution 8 cell
     * @return structured vision analysis result or deterministic fallback
     */
    public CitizenVisionResultDto analyzeImage(Path imagePath, UUID reportId, String h3Index) {
        String repIdStr = reportId != null ? reportId.toString() : "unknown";
        if (imagePath == null || !Files.exists(imagePath)) {
            log.warn("Citizen image file not found for report {}: {}", repIdStr, imagePath);
            return CitizenVisionResultDto.createFallback(repIdStr, h3Index, "Photo file not found on disk");
        }

        Map<String, Object> inputPayload = new LinkedHashMap<>();
        inputPayload.put("imagePath", imagePath.toAbsolutePath().toString());
        inputPayload.put("reportId", repIdStr);
        inputPayload.put("h3Index", h3Index != null ? h3Index : "unknown");

        try {
            byte[] bytes = Files.readAllBytes(imagePath);
            inputPayload.put("imageBase64", Base64.getEncoder().encodeToString(bytes));
        } catch (IOException e) {
            log.warn("Could not read image bytes for report {}: {}", repIdStr, e.getMessage());
        }

        String inputJson;
        try {
            inputJson = MAPPER.writeValueAsString(inputPayload);
        } catch (Exception e) {
            log.error("Failed to serialize vision input payload: {}", e.getMessage());
            return CitizenVisionResultDto.createFallback(repIdStr, h3Index, "Payload serialization failure: " + e.getMessage());
        }

        return executeHttpVision(inputJson, repIdStr, h3Index);
    }

    private CitizenVisionResultDto executeHttpVision(String inputJson, String reportId, String h3Index) {
        String endpoint = serviceUrl + "/api/v1/ml/vision/analyze";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(inputJson, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException te) {
            log.warn("Vision HTTP request timed out after {} ms for report {}", timeout.toMillis(), reportId);
            return CitizenVisionResultDto.createFallback(reportId, h3Index, "Vision service timed out after " + timeout.toMillis() + " ms");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Vision HTTP request interrupted for report {}", reportId);
            return CitizenVisionResultDto.createFallback(reportId, h3Index, "Execution thread interrupted");
        } catch (Exception ex) {
            log.warn("Failed to connect to AI vision service at {}: {}", endpoint, ex.getMessage());
            return CitizenVisionResultDto.createFallback(reportId, h3Index, "AI vision service unreachable: " + ex.getMessage());
        }

        int statusCode = response.statusCode();
        String body = response.body() != null ? response.body().trim() : "";

        if (statusCode != 200) {
            log.warn("Vision service returned HTTP status {}: {}", statusCode, body);
            return CitizenVisionResultDto.createFallback(reportId, h3Index, "Vision service returned HTTP " + statusCode + ": " + body);
        }

        try {
            return MAPPER.readValue(body, CitizenVisionResultDto.class);
        } catch (Exception e) {
            log.warn("Failed to parse vision service JSON. Output: '{}', Error: {}", body, e.getMessage());
            return CitizenVisionResultDto.createFallback(reportId, h3Index, "JSON parse failure: " + e.getMessage());
        }
    }
}
