package com.aerosentinel.forecast;

import com.aerosentinel.forecast.feature.ForecastFeatureAdapter.ModelReadyFeatureVector;
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
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Robust HTTP REST Client for F4-P3 Forecast Inference.
 *
 * Responsibilities:
 * 1. Executes HTTP POST to FastAPI AI Service (/api/v1/ml/forecast/predict).
 * 2. Feeds validated 36-feature vector and lineage context via strict JSON.
 * 3. Enforces bounded connection and response timeouts.
 * 4. Captures and parses strict JSON response into ForecastInferenceResultDto.
 * 5. Performs rigorous Java-side validation of model output before returning to domain service.
 * 6. Never fabricates values; provides explicit failure mapping.
 */
@Component
public class ForecastAiClient {

    private static final Logger log = LoggerFactory.getLogger(ForecastAiClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Set<Integer> REQUIRED_HORIZONS = Set.of(1, 3, 6);

    private final String serviceUrl;
    private final Duration timeout;
    private final HttpClient httpClient;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ForecastInferenceResultDto(
            String modelVersion,
            String generatedAt,
            String h3Index,
            String cityId,
            String parentPredictionId,
            String featureSnapshotId,
            String status,
            List<ForecastItemDto> forecasts,
            Double forecastConfidence
    ) {
        public record ForecastItemDto(
                Integer horizonHours,
                String targetTime,
                Double predictedPm25,
                Double lowerBound,
                Double upperBound,
                String unit
        ) {}
    }

    @Autowired
    public ForecastAiClient(
            @Value("${app.ai-service.url:http://localhost:8000}") String serviceUrl,
            @Value("${app.forecast.ai.timeout-ms:15000}") long timeoutMs
    ) {
        this(serviceUrl, timeoutMs, HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build());
    }

    public ForecastAiClient(String serviceUrl, long timeoutMs, HttpClient httpClient) {
        this.serviceUrl = serviceUrl;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = httpClient;
    }

    /**
     * Backward-compatibility constructor for tests migrating from process bridge.
     */
    @Deprecated
    public ForecastAiClient(String pythonCommand, String cliPath, long timeoutMs) {
        this("http://localhost:8000", timeoutMs);
    }

    /**
     * Executes standalone multi-horizon inference via HTTP REST API.
     */
    public ForecastInferenceResultDto predict(UUID parentPredictionId, ModelReadyFeatureVector featureVector) {
        return predict(parentPredictionId, featureVector != null ? featureVector.baseTimestamp() : null, featureVector);
    }

    /**
     * Executes standalone multi-horizon inference with explicit authoritative baseTimestamp (T0).
     */
    public ForecastInferenceResultDto predict(UUID parentPredictionId, Instant baseTimestamp, ModelReadyFeatureVector featureVector) {
        if (featureVector == null) {
            throw new ForecastException.ValidationFailed("ModelReadyFeatureVector must not be null");
        }
        if (featureVector.orderedValues() == null || featureVector.orderedValues().length != 36) {
            throw new ForecastException.ValidationFailed("Feature vector must contain exactly 36 values");
        }

        Instant effectiveBase = baseTimestamp != null ? baseTimestamp : (featureVector.baseTimestamp() != null ? featureVector.baseTimestamp() : Instant.now());
        Map<String, Object> payload = buildCliPayload(parentPredictionId, effectiveBase, featureVector);

        String inputJson;
        try {
            inputJson = MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new ForecastException.ValidationFailed("Failed to serialize forecast HTTP payload: " + e.getMessage());
        }

        return executeHttpInference(inputJson, parentPredictionId, featureVector);
    }

    private Map<String, Object> buildCliPayload(UUID parentPredictionId, Instant baseTimestamp, ModelReadyFeatureVector vector) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("h3Index", vector.h3Index());
        payload.put("cityId", vector.cityId() != null ? vector.cityId().toString() : null);
        payload.put("parentPredictionId", parentPredictionId != null ? parentPredictionId.toString() : null);
        payload.put("featureSnapshotId", vector.featureSnapshotId() != null ? vector.featureSnapshotId().toString() : null);
        payload.put("predictedAt", baseTimestamp != null ? baseTimestamp.toString() : Instant.now().toString());
        payload.put("features", vector.featureMap());
        return payload;
    }

    private ForecastInferenceResultDto executeHttpInference(
            String inputJson,
            UUID expectedParentId,
            ModelReadyFeatureVector vector
    ) {
        String endpoint = serviceUrl + "/api/v1/ml/forecast/predict";
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
            log.error("Forecast HTTP inference timed out after {} ms for H3: {}", timeout.toMillis(), vector.h3Index());
            throw new ForecastException.AiTimeout("Forecast HTTP inference timed out after " + timeout.toMillis() + " ms");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ForecastException.AiTimeout("Forecast HTTP inference interrupted: " + ie.getMessage());
        } catch (Exception ex) {
            log.error("Failed to connect to AI forecast service at {}: {}", endpoint, ex.getMessage());
            throw new ForecastException.AiUnavailable("AI forecast service unreachable: " + ex.getMessage(), ex);
        }

        int statusCode = response.statusCode();
        String body = response.body() != null ? response.body().trim() : "";

        if (statusCode == 503 || body.contains("FORECAST_MODEL_UNAVAILABLE")) {
            throw new ForecastException.AiUnavailable("Forecast model unavailable: " + body);
        }
        if (statusCode == 400 || body.contains("INVALID_INPUT") || body.contains("FORECAST_MODEL_CONTRACT_INVALID")) {
            throw new ForecastException.ValidationFailed("Forecast input contract invalid: " + body);
        }
        if (statusCode != 200) {
            throw new ForecastException.AiUnavailable("Forecast inference failed with HTTP status " + statusCode + ": " + body);
        }
        if (body.isEmpty()) {
            throw new ForecastException.AiUnavailable("Forecast service returned empty response body");
        }

        ForecastInferenceResultDto result;
        try {
            result = MAPPER.readValue(body, ForecastInferenceResultDto.class);
        } catch (Exception e) {
            log.error("Failed to parse forecast response JSON: '{}'. Error: {}", body, e.getMessage());
            throw new ForecastException.AiUnavailable("Invalid JSON from forecast service: " + e.getMessage(), e);
        }

        validateInferenceResult(result, expectedParentId, vector);
        return result;
    }

    /**
     * Validates the Python inference output contract against all P3/P4 constraints.
     */
    public void validateInferenceResult(
            ForecastInferenceResultDto result,
            UUID expectedParentId,
            ModelReadyFeatureVector vector
    ) {
        if (result == null) {
            throw new ForecastException.ValidationFailed("Inference result must not be null");
        }
        if (!"SUCCESS".equalsIgnoreCase(result.status())) {
            throw new ForecastException.ValidationFailed("Forecast inference status was not SUCCESS: " + result.status());
        }
        if (!"forecast_regressors_v1".equals(result.modelVersion())) {
            throw new ForecastException.ValidationFailed("Unexpected modelVersion: " + result.modelVersion());
        }
        if (result.forecastConfidence() != null) {
            throw new ForecastException.ValidationFailed("Forecast confidence must be strictly null");
        }
        if (result.forecasts() == null || result.forecasts().size() != 3) {
            throw new ForecastException.ValidationFailed("Forecast result must contain exactly 3 horizons, got: " +
                    (result.forecasts() == null ? 0 : result.forecasts().size()));
        }

        // Validate lineage
        if (expectedParentId != null && result.parentPredictionId() != null) {
            if (!expectedParentId.toString().equalsIgnoreCase(result.parentPredictionId())) {
                throw new ForecastException.ValidationFailed("Lineage mismatch: expected parentId " + expectedParentId +
                        ", got " + result.parentPredictionId());
            }
        }
        if (vector.h3Index() != null && !vector.h3Index().equalsIgnoreCase(result.h3Index())) {
            throw new ForecastException.ValidationFailed("Lineage mismatch: expected H3 " + vector.h3Index() +
                    ", got " + result.h3Index());
        }

        // Validate horizons and bounds
        Set<Integer> seenHorizons = new HashSet<>();
        Instant previousTarget = null;

        for (ForecastInferenceResultDto.ForecastItemDto item : result.forecasts()) {
            if (item.horizonHours() == null || !REQUIRED_HORIZONS.contains(item.horizonHours())) {
                throw new ForecastException.ValidationFailed("Unsupported horizon: " + item.horizonHours());
            }
            if (!seenHorizons.add(item.horizonHours())) {
                throw new ForecastException.ValidationFailed("Duplicate horizon in forecast output: " + item.horizonHours());
            }
            if (item.predictedPm25() == null || Double.isNaN(item.predictedPm25()) || Double.isInfinite(item.predictedPm25())) {
                throw new ForecastException.ValidationFailed("predictedPm25 must be finite numeric");
            }
            if (item.lowerBound() == null || Double.isNaN(item.lowerBound()) || Double.isInfinite(item.lowerBound())) {
                throw new ForecastException.ValidationFailed("lowerBound must be finite numeric");
            }
            if (item.upperBound() == null || Double.isNaN(item.upperBound()) || Double.isInfinite(item.upperBound())) {
                throw new ForecastException.ValidationFailed("upperBound must be finite numeric");
            }
            if (item.lowerBound() < 0.0) {
                throw new ForecastException.ValidationFailed("Physical lower bound violated: lowerBound < 0 (" + item.lowerBound() + ")");
            }
            if (item.lowerBound() > item.predictedPm25()) {
                throw new ForecastException.ValidationFailed("Interval violated: lowerBound > predictedPm25 (" +
                        item.lowerBound() + " > " + item.predictedPm25() + ")");
            }
            if (item.predictedPm25() > item.upperBound()) {
                throw new ForecastException.ValidationFailed("Interval violated: predictedPm25 > upperBound (" +
                        item.predictedPm25() + " > " + item.upperBound() + ")");
            }
            if (!"ug/m3".equalsIgnoreCase(item.unit())) {
                throw new ForecastException.ValidationFailed("Invalid unit: expected 'ug/m3', got " + item.unit());
            }

            // Target time validation
            Instant target;
            try {
                target = Instant.parse(item.targetTime());
            } catch (DateTimeParseException e) {
                throw new ForecastException.ValidationFailed("Invalid targetTime ISO format: " + item.targetTime());
            }

            if (previousTarget != null && !target.isAfter(previousTarget)) {
                throw new ForecastException.ValidationFailed("Target timestamps not in ascending order: " +
                        previousTarget + " then " + target);
            }
            previousTarget = target;
        }

        if (!seenHorizons.equals(REQUIRED_HORIZONS)) {
            throw new ForecastException.ValidationFailed("Forecasts must cover exact horizons [1, 3, 6], found: " + seenHorizons);
        }
    }
}
