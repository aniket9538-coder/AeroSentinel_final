package com.aerosentinel.evidence;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceAiClientHttpTest {

    private HttpServer server;
    private String serverUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("F5 HTTP: Valid 200 response deserializes into EvidenceAiOutputDto")
    void validResponse_succeeds() {
        String jsonResponse = """
                {
                    "status": "SUCCESS",
                    "h3Index": "8860145a31fffff",
                    "cityId": "pune",
                    "predictionId": "pred-001",
                    "eventId": "evt-001",
                    "canonicalEventId": "evt-001",
                    "clusterH3Cells": ["8860145a31fffff"],
                    "evidenceScore": 0.85,
                    "evidenceCompleteness": 0.90,
                    "consistency": "HIGH",
                    "triageState": "ALERT_CANDIDATE",
                    "scoreBreakdown": {
                        "observation_strength": 0.85,
                        "ml_forecast_support": 0.80,
                        "multi_source_agreement": 0.90,
                        "spatial_consistency": 0.85,
                        "temporal_persistence": 0.80,
                        "recency_factor": 1.0,
                        "conflict_penalty": 0.0,
                        "evidence_completeness": 0.90,
                        "final_evidence_score": 0.85
                    },
                    "sourceMatrix": {"CAAQMS": "CONFIRMED"},
                    "unavailableSources": [],
                    "conflictingNotes": [],
                    "supportingSignals": ["CAAQMS"],
                    "signals": [],
                    "aiInterpretation": {
                        "summaryPublic": "High PM2.5 levels detected",
                        "summaryAnalyst": "Ground sensor corroborates elevated PM2.5",
                        "detectedCondition": "INDUSTRIAL_EMISSION",
                        "supportingSignals": ["CAAQMS"],
                        "forecastTrajectory": "Stable",
                        "uncertaintyStatement": "Low uncertainty",
                        "unsupportedConclusions": [],
                        "causalClaimSupported": true,
                        "isGrounded": true,
                        "modelVersion": "gemini-2.0-flash",
                        "promptVersion": "v1.0"
                    },
                    "recommendedVerification": {
                        "action": "Dispatch mobile unit",
                        "priority": "URGENT",
                        "guidelines": ["Verify sensor reading"]
                    },
                    "provenance": {
                        "h3Index": "8860145a31fffff",
                        "cityId": "pune",
                        "f3PredictionId": "pred-001",
                        "f3ModelVersion": "hotspot_classifier_v1",
                        "f4ModelVersion": "forecast_regressors_v1",
                        "f5ScoringVersion": "f5_v1",
                        "geminiModelVersion": "gemini-2.0-flash",
                        "geminiPromptVersion": "v1.0",
                        "evaluatedAt": "2026-10-18T10:00:00Z"
                    }
                }
                """;

        server.createContext("/api/v1/ml/evidence/orchestrate", exchange -> {
            byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        EvidenceAiClient client = new EvidenceAiClient(serverUrl, 5000);
        EvidenceAiClient.EvidenceAiOutputDto output = client.evaluate(Map.of("h3Index", "8860145a31fffff"));

        assertThat(output).isNotNull();
        assertThat(output.status()).isEqualTo("SUCCESS");
        assertThat(output.evidenceScore()).isEqualTo(0.85);
        assertThat(output.triageState()).isEqualTo("ALERT_CANDIDATE");
    }

    @Test
    @DisplayName("F5 HTTP: 500 error from AI service throws RuntimeException without fabricating evidence")
    void serverError500_throwsRuntimeException() {
        server.createContext("/api/v1/ml/evidence/orchestrate", exchange -> {
            String err = "{\"detail\":\"EVIDENCE_ORCHESTRATION_FAILED: Internal error\"}";
            byte[] bytes = err.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        EvidenceAiClient client = new EvidenceAiClient(serverUrl, 5000);
        assertThatThrownBy(() -> client.evaluate(Map.of("h3Index", "8860145a31fffff")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Evidence service returned HTTP 500");
    }

    @Test
    @DisplayName("F5 HTTP: Connection refused throws RuntimeException without fabricating evidence")
    void connectionRefused_throwsRuntimeException() {
        server.stop(0);

        EvidenceAiClient client = new EvidenceAiClient(serverUrl, 2000);
        assertThatThrownBy(() -> client.evaluate(Map.of("h3Index", "8860145a31fffff")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("AI evidence service unreachable");
    }
}
