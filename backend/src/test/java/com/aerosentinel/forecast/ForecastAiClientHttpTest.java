package com.aerosentinel.forecast;

import com.aerosentinel.forecast.feature.ForecastFeatureAdapter.ModelReadyFeatureVector;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForecastAiClientHttpTest {

    private HttpServer server;
    private int port;
    private String serverUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        serverUrl = "http://127.0.0.1:" + port;
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private ModelReadyFeatureVector createValidFeatureVector() {
        double[] vals = new double[36];
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            vals[i] = 25.0;
            map.put("feature_" + i, 25.0);
        }
        return new ModelReadyFeatureVector(
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
    }

    @Test
    @DisplayName("F4 HTTP: Valid 200 response deserializes and passes all Java validations")
    void validResponse_succeeds() {
        String jsonResponse = """
                {
                    "modelVersion": "forecast_regressors_v1",
                    "generatedAt": "2026-10-18T10:00:00Z",
                    "h3Index": "8860145a31fffff",
                    "cityId": "pune",
                    "parentPredictionId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
                    "featureSnapshotId": null,
                    "status": "SUCCESS",
                    "forecasts": [
                        {"horizonHours": 1, "targetTime": "2026-10-18T11:00:00Z", "predictedPm25": 42.5, "lowerBound": 35.0, "upperBound": 50.0, "unit": "ug/m3"},
                        {"horizonHours": 3, "targetTime": "2026-10-18T13:00:00Z", "predictedPm25": 48.0, "lowerBound": 38.0, "upperBound": 58.0, "unit": "ug/m3"},
                        {"horizonHours": 6, "targetTime": "2026-10-18T16:00:00Z", "predictedPm25": 55.2, "lowerBound": 42.0, "upperBound": 68.0, "unit": "ug/m3"}
                    ],
                    "forecastConfidence": null
                }
                """;

        server.createContext("/api/v1/ml/forecast/predict", exchange -> {
            byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        ForecastAiClient client = new ForecastAiClient(serverUrl, 5000);
        UUID parentId = UUID.fromString("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d");
        ForecastAiClient.ForecastInferenceResultDto result = client.predict(parentId, createValidFeatureVector());

        assertThat(result).isNotNull();
        assertThat(result.modelVersion()).isEqualTo("forecast_regressors_v1");
        assertThat(result.forecastConfidence()).isNull();
        assertThat(result.forecasts()).hasSize(3);
        assertThat(result.forecasts().get(0).predictedPm25()).isEqualTo(42.5);
    }

    @Test
    @DisplayName("F4 HTTP: 503 Service Unavailable throws ForecastException.AiUnavailable without fallback")
    void serviceUnavailable503_throwsAiUnavailable() {
        server.createContext("/api/v1/ml/forecast/predict", exchange -> {
            String err = "{\"detail\":\"FORECAST_MODEL_UNAVAILABLE: Artifact not found\"}";
            byte[] bytes = err.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(503, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        ForecastAiClient client = new ForecastAiClient(serverUrl, 5000);
        assertThatThrownBy(() -> client.predict(UUID.randomUUID(), createValidFeatureVector()))
                .isInstanceOf(ForecastException.AiUnavailable.class)
                .hasMessageContaining("Forecast model unavailable");
    }

    @Test
    @DisplayName("F4 HTTP: 400 Bad Request throws ForecastException.ValidationFailed")
    void badRequest400_throwsValidationFailed() {
        server.createContext("/api/v1/ml/forecast/predict", exchange -> {
            String err = "{\"detail\":\"INVALID_INPUT: Feature input contains NaN\"}";
            byte[] bytes = err.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        ForecastAiClient client = new ForecastAiClient(serverUrl, 5000);
        assertThatThrownBy(() -> client.predict(UUID.randomUUID(), createValidFeatureVector()))
                .isInstanceOf(ForecastException.ValidationFailed.class)
                .hasMessageContaining("Forecast input contract invalid");
    }

    @Test
    @DisplayName("F4 HTTP: Service timeout throws ForecastException.AiTimeout")
    void timeout_throwsAiTimeout() {
        server.createContext("/api/v1/ml/forecast/predict", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {}
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });

        ForecastAiClient client = new ForecastAiClient(serverUrl, 300); // 300ms timeout
        assertThatThrownBy(() -> client.predict(UUID.randomUUID(), createValidFeatureVector()))
                .isInstanceOf(ForecastException.AiTimeout.class)
                .hasMessageContaining("timed out");
    }

    @Test
    @DisplayName("F4 HTTP: Connection refused throws ForecastException.AiUnavailable")
    void connectionRefused_throwsAiUnavailable() {
        server.stop(0); // Stop server immediately

        ForecastAiClient client = new ForecastAiClient(serverUrl, 2000);
        assertThatThrownBy(() -> client.predict(UUID.randomUUID(), createValidFeatureVector()))
                .isInstanceOf(ForecastException.AiUnavailable.class)
                .hasMessageContaining("AI forecast service unreachable");
    }
}
