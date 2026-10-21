package com.aerosentinel.citizen;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CitizenVisionAiClientHttpTest {

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
    @DisplayName("F6 HTTP: Valid 200 response deserializes into CitizenVisionResultDto")
    void validResponse_succeeds(@TempDir Path tempDir) throws IOException {
        Path imageFile = tempDir.resolve("test_photo.jpg");
        Files.write(imageFile, new byte[]{1, 2, 3, 4});

        String jsonResponse = """
                {
                    "status": "SUCCESS",
                    "reportId": "rep-001",
                    "h3Index": "8860145a31fffff",
                    "category": "SMOKE_LIKE",
                    "confidence": 0.88,
                    "observations": ["Dense smoke plume visible"],
                    "uncertainty": ["Minor lighting distortion"],
                    "indicators": {"smoke_visible": true, "fire_visible": false},
                    "privacyFlags": ["PII scan cleared"],
                    "modelVersion": "gemini-2.0-flash",
                    "promptVersion": "vision_analysis_v001"
                }
                """;

        server.createContext("/api/v1/ml/vision/analyze", exchange -> {
            byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        CitizenVisionAiClient client = new CitizenVisionAiClient(serverUrl, 5000);
        CitizenVisionAiClient.CitizenVisionResultDto result = client.analyzeImage(imageFile, UUID.randomUUID(), "8860145a31fffff");

        assertThat(result).isNotNull();
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.category()).isEqualTo("SMOKE_LIKE");
        assertThat(result.confidence()).isEqualTo(0.88);
    }

    @Test
    @DisplayName("F6 HTTP: Service error returns deterministic fallback so report is not rolled back")
    void serviceError_returnsFallback(@TempDir Path tempDir) throws IOException {
        Path imageFile = tempDir.resolve("test_photo.jpg");
        Files.write(imageFile, new byte[]{1, 2, 3, 4});

        server.createContext("/api/v1/ml/vision/analyze", exchange -> {
            exchange.sendResponseHeaders(500, 0);
            exchange.close();
        });

        CitizenVisionAiClient client = new CitizenVisionAiClient(serverUrl, 5000);
        CitizenVisionAiClient.CitizenVisionResultDto result = client.analyzeImage(imageFile, UUID.randomUUID(), "8860145a31fffff");

        assertThat(result).isNotNull();
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.status()).isEqualTo("FALLBACK");
        assertThat(result.category()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("F6 HTTP: Connection refused returns deterministic fallback")
    void connectionRefused_returnsFallback(@TempDir Path tempDir) throws IOException {
        Path imageFile = tempDir.resolve("test_photo.jpg");
        Files.write(imageFile, new byte[]{1, 2, 3, 4});

        server.stop(0);

        CitizenVisionAiClient client = new CitizenVisionAiClient(serverUrl, 2000);
        CitizenVisionAiClient.CitizenVisionResultDto result = client.analyzeImage(imageFile, UUID.randomUUID(), "8860145a31fffff");

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("FALLBACK");
    }

    @Test
    @DisplayName("F6 HTTP: Missing image file returns fallback without calling HTTP")
    void missingFile_returnsFallback() {
        CitizenVisionAiClient client = new CitizenVisionAiClient(serverUrl, 5000);
        CitizenVisionAiClient.CitizenVisionResultDto result = client.analyzeImage(Path.of("non_existent_file.jpg"), UUID.randomUUID(), "8860145a31fffff");

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("FALLBACK");
    }
}
