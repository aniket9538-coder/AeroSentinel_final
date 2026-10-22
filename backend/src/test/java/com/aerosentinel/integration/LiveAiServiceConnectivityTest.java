package com.aerosentinel.integration;

import com.aerosentinel.citizen.CitizenVisionAiClient;
import com.aerosentinel.citizen.CitizenVisionAiClient.CitizenVisionResultDto;
import com.aerosentinel.evidence.EvidenceAiClient;
import com.aerosentinel.evidence.EvidenceAiClient.EvidenceAiOutputDto;
import com.aerosentinel.forecast.ForecastAiClient;
import com.aerosentinel.forecast.ForecastAiClient.ForecastInferenceResultDto;
import com.aerosentinel.forecast.feature.ForecastFeatureAdapter.ModelReadyFeatureVector;
import com.aerosentinel.hotspot.AiServiceHotspotClient;
import com.aerosentinel.hotspot.AiServiceHotspotClient.MlInferencePayload;
import com.aerosentinel.hotspot.AiServiceHotspotClient.MlInferenceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Local Integration Test: Verifies end-to-end HTTP REST communication between the Java backend
 * AI clients and the local persistent FastAPI AI service (running on http://127.0.0.1:8000).
 *
 * NOTE: This test targets the local service with real loaded serialized ML model artifacts
 * (forecast_regressors_v1.joblib [125MB] and hotspot_classifier_v1.joblib [20MB]).
 * It does NOT target the historical Vercel deployment (`aero-sentinel-final-rzx6.vercel.app`),
 * which returned `models_ready: false` due to Vercel Serverless Function limits.
 */
class LiveAiServiceConnectivityTest {

    private static final String AI_SERVICE_URL = "http://127.0.0.1:8000";

    private boolean isAiServiceAvailable() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(AI_SERVICE_URL + "/health"))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().contains("\"models_ready\":true");
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("Live End-to-End: F4 ForecastAiClient connects to live FastAPI over HTTP and receives valid multi-horizon inference")
    void testLiveF4ForecastInference() {
        if (!isAiServiceAvailable()) {
            System.out.println("[SKIP] Live AI service not available on port 8000; skipping live test");
            return;
        }

        ForecastAiClient client = new ForecastAiClient(AI_SERVICE_URL, 15000);

        double[] vals = new double[36];
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            vals[i] = 25.0;
            map.put("feature_" + i, 25.0);
        }

        ModelReadyFeatureVector vector = new ModelReadyFeatureVector(
                "8860145a31fffff",
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                "VALID",
                Collections.emptyList(),
                vals,
                map,
                true
        );

        ForecastInferenceResultDto result = client.predict(UUID.randomUUID(), vector);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.modelVersion()).isEqualTo("forecast_regressors_v1");
        assertThat(result.forecastConfidence()).isNull();
        assertThat(result.forecasts()).hasSize(3);

        for (var item : result.forecasts()) {
            assertThat(item.predictedPm25()).isGreaterThan(0.0);
            assertThat(item.lowerBound()).isNotNull();
            assertThat(item.upperBound()).isNotNull();
            assertThat(item.lowerBound()).isLessThanOrEqualTo(item.upperBound());
        }
    }

    @Test
    @DisplayName("Live End-to-End: F5 EvidenceAiClient connects to live FastAPI over HTTP and receives structured triage")
    void testLiveF5EvidenceOrchestration() {
        if (!isAiServiceAvailable()) {
            System.out.println("[SKIP] Live AI service not available on port 8000; skipping live test");
            return;
        }

        EvidenceAiClient client = new EvidenceAiClient(AI_SERVICE_URL, 60000);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("h3Index", "8860145a31fffff");
        payload.put("cityId", UUID.randomUUID().toString());
        payload.put("predictedAt", Instant.now().toString());

        Map<String, Double> features = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            features.put("feature_" + i, 25.0);
        }
        payload.put("features", features);
        payload.put("observations", Collections.emptyList());
        payload.put("citizenReports", Collections.emptyList());

        EvidenceAiOutputDto result = client.evaluate(payload);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.h3Index()).isEqualTo("8860145a31fffff");
        assertThat(result.triageState()).isIn("INSUFFICIENT_EVIDENCE", "MONITOR", "ALERT_CANDIDATE");
        assertThat(result.provenance()).isNotNull();
    }

    @Test
    @DisplayName("Live End-to-End: F6 CitizenVisionAiClient connects to live FastAPI over HTTP and receives analysis")
    void testLiveF6VisionAnalysis() {
        if (!isAiServiceAvailable()) {
            System.out.println("[SKIP] Live AI service not available on port 8000; skipping live test");
            return;
        }

        CitizenVisionAiClient client = new CitizenVisionAiClient(AI_SERVICE_URL, 15000);

        // Test with fallback when non-existent image path is provided
        CitizenVisionResultDto result = client.analyzeImage(
                Path.of("non_existent_image.jpg"),
                UUID.randomUUID(),
                "8860145a31fffff"
        );

        assertThat(result).isNotNull();
        assertThat(result.modelVersion()).isNotNull();
    }

    @Test
    @DisplayName("Live End-to-End: F3 AiServiceHotspotClient connects to live FastAPI over HTTP and receives calibrated prediction")
    void testLiveF3HotspotInference() {
        if (!isAiServiceAvailable()) {
            System.out.println("[SKIP] Live AI service not available on port 8000; skipping live test");
            return;
        }

        AiServiceHotspotClient client = new AiServiceHotspotClient(AI_SERVICE_URL, 10000);

        List<Double> features = new ArrayList<>();
        Map<String, Double> featureMap = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            features.add(25.0);
            featureMap.put("f" + i, 25.0);
        }

        MlInferencePayload payload = new MlInferencePayload(
                "8860145a31fffff",
                "Pune",
                UUID.randomUUID().toString(),
                features,
                featureMap
        );

        MlInferenceResult result = client.predict(payload);

        assertThat(result).isNotNull();
        assertThat(result.h3Index()).isEqualTo("8860145a31fffff");
        assertThat(result.riskScore()).isGreaterThanOrEqualTo(0.0);
        assertThat(result.riskLevel()).isNotNull();
        assertThat(result.modelVersion()).isEqualTo("hotspot_classifier_v1");
    }
}
