package com.aerosentinel.hotspot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

/**
 * Robust Client for the Python AI Service / ML Inference Model.
 *
 * Primary Channel: REST API (FastAPI /api/v1/ml/hotspot/predict)
 * Resilient Secondary Channel: ProcessBuilder to predict_cli.py (for offline/test execution)
 */
@Component
public class AiServiceHotspotClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceHotspotClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String serviceUrl;
    private final Duration timeout;
    private final HttpClient httpClient;

    public record MlInferencePayload(
            String h3Index,
            String cityName,
            String cityId,
            List<Double> features,
            Map<String, Double> featureMap
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MlInferenceResult(
            String h3Index,
            double riskScore,
            String riskLevel,
            double confidence,
            boolean isHotspot,
            double operationalThreshold,
            String modelVersion,
            String engineType,
            String status,
            String message,
            Map<String, Object> metadata
    ) {}

    public AiServiceHotspotClient(
            @Value("${app.ai-service.url:http://localhost:8000}") String serviceUrl,
            @Value("${app.ai-service.timeout-ms:10000}") long timeoutMs
    ) {
        this.serviceUrl = serviceUrl;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(this.timeout)
                .build();
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    /**
     * Executes inference on the trained ML model for a 36-feature snapshot.
     *
     * @param payload ordered feature vector and metadata
     * @return MlInferenceResult with calibrated probability, risk level, and confidence
     */
    public MlInferenceResult predict(MlInferencePayload payload) {
        // 1. Try REST call to FastAPI AI Service
        try {
            return callRestInference(payload);
        } catch (Exception restEx) {
            log.debug("AI REST service unreachable at {} ({}). Invoking direct process bridge fallback...",
                    serviceUrl, restEx.getMessage());

            // 2. Fallback to direct Python runner (handles standalone unit tests & dev environments)
            try {
                return callProcessInference(payload);
            } catch (Exception procEx) {
                log.error("Both REST and direct Python model bridge failed for cell {}: REST: {}, Process: {}",
                        payload.h3Index(), restEx.getMessage(), procEx.getMessage());

                if (procEx.getMessage() != null && procEx.getMessage().contains("MODEL_UNAVAILABLE")) {
                    throw new IllegalStateException("MODEL_UNAVAILABLE: ML artifact hotspot_classifier_v1.joblib not found on disk");
                }
                throw new RuntimeException("MODEL_INFERENCE_FAILED: " + procEx.getMessage(), procEx);
            }
        }
    }

    private MlInferenceResult callRestInference(MlInferencePayload payload) throws Exception {
        String endpoint = serviceUrl + "/api/v1/ml/hotspot/predict";
        String requestJson = MAPPER.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() == 503) {
            throw new IllegalStateException("MODEL_UNAVAILABLE: AI service reports hotspot classifier is not loaded");
        }
        if (response.statusCode() == 400) {
            throw new IllegalArgumentException("INSUFFICIENT_DATA: " + response.body());
        }
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + " from AI service: " + response.body());
        }

        return MAPPER.readValue(response.body(), MlInferenceResult.class);
    }

    private MlInferenceResult callProcessInference(MlInferencePayload payload) throws Exception {
        // Resolve path to ai-service/ml/inference/predict_cli.py
        Path currentDir = Paths.get(".").toAbsolutePath().normalize();
        Path cliScript = currentDir.resolve("ai-service/ml/inference/predict_cli.py");
        if (!Files.exists(cliScript)) {
            cliScript = currentDir.resolve("../ai-service/ml/inference/predict_cli.py").normalize();
        }

        if (!Files.exists(cliScript)) {
            throw new FileNotFoundException("MODEL_LOAD_FAILED: CLI script not found at " + cliScript);
        }

        // Verify physical joblib exists
        Path artifactPath = cliScript.getParent().getParent().resolve("models/artifacts/hotspot_classifier_v1.joblib").normalize();
        if (!Files.exists(artifactPath)) {
            artifactPath = cliScript.getParent().getParent().getParent().resolve("models/artifacts/hotspot_classifier_v1.joblib").normalize();
        }
        if (!Files.exists(artifactPath)) {
            throw new FileNotFoundException("MODEL_UNAVAILABLE: Physical joblib artifact missing at " + artifactPath);
        }

        String inputJson = MAPPER.writeValueAsString(payload);

        ProcessBuilder pb = new ProcessBuilder("python", cliScript.toString());
        pb.redirectErrorStream(false);
        Process process = pb.start();

        // Write input to STDIN
        try (OutputStream os = process.getOutputStream();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
            writer.write(inputJson);
            writer.flush();
        }

        // Read STDOUT
        StringBuilder stdout = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stdout.append(line);
            }
        }

        // Read STDERR (for debug logging)
        StringBuilder stderr = new StringBuilder();
        try (BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = errReader.readLine()) != null) {
                stderr.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        String outputStr = stdout.toString().trim();

        if (exitCode != 0) {
            log.warn("predict_cli exited with code {}. Stderr: {}", exitCode, stderr);
            if (outputStr.contains("MODEL_UNAVAILABLE")) {
                throw new IllegalStateException("MODEL_UNAVAILABLE: " + outputStr);
            }
            if (outputStr.contains("INSUFFICIENT_DATA")) {
                throw new IllegalArgumentException("INSUFFICIENT_DATA: " + outputStr);
            }
            throw new RuntimeException("Process failed with code " + exitCode + ": " + outputStr);
        }

        return MAPPER.readValue(outputStr, MlInferenceResult.class);
    }
}
