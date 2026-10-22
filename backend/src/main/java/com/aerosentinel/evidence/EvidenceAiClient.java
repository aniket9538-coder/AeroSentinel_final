package com.aerosentinel.evidence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Robust HTTP REST Client for F5 Evidence Orchestration.
 *
 * Responsibilities:
 * 1. Executes HTTP POST to FastAPI AI Service (/api/v1/ml/evidence/orchestrate).
 * 2. Feeds structured evidence context via strict JSON.
 * 3. Enforces bounded execution timeout with automatic cleanup.
 * 4. Captures and parses strict JSON response into EvidenceAiOutputDto.
 * 5. Never fabricates values; provides graceful controlled fallback on failure.
 */
@Component
public class EvidenceAiClient {

    private static final Logger log = LoggerFactory.getLogger(EvidenceAiClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final String serviceUrl;
    private final Duration timeout;
    private final HttpClient httpClient;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvidenceAiOutputDto(
            String status,
            String h3Index,
            String cityId,
            String predictionId,
            String eventId,
            String canonicalEventId,
            List<String> clusterH3Cells,
            Double evidenceScore,
            Double evidenceCompleteness,
            String consistency,
            String triageState,
            ScoreBreakdownDto scoreBreakdown,
            Map<String, String> sourceMatrix,
            List<String> unavailableSources,
            List<String> conflictingNotes,
            List<String> supportingSignals,
            List<SignalItemDto> signals,
            AiInterpretationDto aiInterpretation,
            RecommendedVerificationDto recommendedVerification,
            ProvenanceDto provenance
    ) {
        public record ScoreBreakdownDto(
                Double observation_strength,
                Double ml_forecast_support,
                Double multi_source_agreement,
                Double spatial_consistency,
                Double temporal_persistence,
                Double recency_factor,
                Double conflict_penalty,
                Double evidence_completeness,
                Double final_evidence_score
        ) {}

        public record SignalItemDto(
                String signalId,
                String sourceType,
                String description,
                String timestamp,
                Double confidenceScore,
                String dataSource,
                String relevanceTier,
                String sourceRef
        ) {}

        public record AiInterpretationDto(
                String summaryPublic,
                String summaryAnalyst,
                String detectedCondition,
                List<String> supportingSignals,
                String forecastTrajectory,
                String uncertaintyStatement,
                List<String> unsupportedConclusions,
                Boolean causalClaimSupported,
                Boolean isGrounded,
                String modelVersion,
                String promptVersion
        ) {}

        public record RecommendedVerificationDto(
                String action,
                String priority,
                List<String> guidelines
        ) {}

        public record ProvenanceDto(
                String h3Index,
                String cityId,
                String f3PredictionId,
                String f3ModelVersion,
                String f4ModelVersion,
                String f5ScoringVersion,
                String geminiModelVersion,
                String geminiPromptVersion,
                String evaluatedAt
        ) {}
    }

    @Autowired
    public EvidenceAiClient(
            @Value("${app.ai-service.url:http://localhost:8000}") String serviceUrl,
            @Value("${app.evidence.ai.timeout-ms:15000}") long timeoutMs
    ) {
        this(serviceUrl, timeoutMs, HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build());
    }

    public EvidenceAiClient(String serviceUrl, long timeoutMs, HttpClient httpClient) {
        this.serviceUrl = serviceUrl;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = httpClient;
    }

    /**
     * Backward-compatibility constructor for tests migrating from process bridge.
     */
    @Deprecated
    public EvidenceAiClient(String pythonCommand, String cliPath, long timeoutMs) {
        this("http://localhost:8000", timeoutMs);
    }

    public EvidenceAiOutputDto evaluate(Map<String, Object> inputPayload) {
        String inputJson;
        try {
            inputJson = MAPPER.writeValueAsString(inputPayload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize evidence HTTP input payload: " + e.getMessage(), e);
        }

        return executeHttpOrchestration(inputJson);
    }

    private EvidenceAiOutputDto executeHttpOrchestration(String inputJson) {
        String endpoint = serviceUrl + "/api/v1/ml/evidence/orchestrate";
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
            log.error("Evidence HTTP orchestration timed out after {} ms", timeout.toMillis());
            throw new RuntimeException("Evidence HTTP orchestration timed out after " + timeout.toMillis() + " ms", te);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Evidence HTTP orchestration interrupted", ie);
        } catch (Exception ex) {
            log.error("Failed to connect to AI evidence service at {}: {}", endpoint, ex.getMessage());
            throw new RuntimeException("AI evidence service unreachable: " + ex.getMessage(), ex);
        }

        int statusCode = response.statusCode();
        String body = response.body() != null ? response.body().trim() : "";

        if (statusCode != 200) {
            log.error("Evidence service returned HTTP status {}: {}", statusCode, body);
            throw new RuntimeException("Evidence service returned HTTP " + statusCode + ": " + body);
        }

        try {
            return MAPPER.readValue(body, EvidenceAiOutputDto.class);
        } catch (Exception e) {
            log.error("Failed to parse evidence service JSON: '{}', Error: {}", body, e.getMessage());
            throw new RuntimeException("Failed to parse evidence service output: " + e.getMessage(), e);
        }
    }
}
