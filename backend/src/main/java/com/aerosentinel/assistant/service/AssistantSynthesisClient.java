package com.aerosentinel.assistant.service;

import com.aerosentinel.assistant.tools.*;
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

@Component
public class AssistantSynthesisClient {

    private static final Logger log = LoggerFactory.getLogger(AssistantSynthesisClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final String aiServiceUrl;
    private final Duration timeout;
    private final HttpClient httpClient;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SynthesisResponseDto(
            String answer,
            String language,
            String status,
            String generator, // "gemini-2.0-flash" or "deterministic_synthesis"
            List<Double> preservedNumericalValues,
            List<String> limitations
    ) {}

    @Autowired
    public AssistantSynthesisClient(
            @Value("${app.ai-service.url:http://localhost:8000}") String aiServiceUrl,
            @Value("${app.assistant.timeout-ms:10000}") long timeoutMs
    ) {
        this.aiServiceUrl = (aiServiceUrl != null && !aiServiceUrl.isBlank()) ? aiServiceUrl.trim() : "http://localhost:8000";
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    public AssistantSynthesisClient(String aiServiceUrl, long timeoutMs, HttpClient httpClient) {
        this.aiServiceUrl = aiServiceUrl;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = httpClient;
    }

    /**
     * Synthesizes a citizen response grounded in verified tool results.
     * Invokes FastAPI /api/v1/assistant/synthesize with numerical safeguard verification,
     * falling back cleanly to deterministic synthesis upon failure, timeout, or numerical hallucination.
     */
    public SynthesisResponseDto synthesize(
            String userMessage,
            Map<String, Object> toolResults,
            String locale,
            String locationName
    ) {
        String lang = (locale != null && !locale.isBlank()) ? locale.trim().toLowerCase() : "en";
        String loc = (locationName != null && !locationName.isBlank()) ? locationName.trim() : "Local Area";

        try {
            Map<String, Object> requestPayload = new HashMap<>();
            requestPayload.put("user_message", userMessage);
            requestPayload.put("tool_results", toolResults);
            requestPayload.put("locale", lang);
            requestPayload.put("location_name", loc);

            String jsonBody = MAPPER.writeValueAsString(requestPayload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.aiServiceUrl + "/api/v1/assistant/synthesize"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(this.timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
                SynthesisResponseDto dto = MAPPER.readValue(response.body(), SynthesisResponseDto.class);
                if (dto != null && dto.answer() != null && !dto.answer().isBlank()) {
                    // Numerical preservation safeguard verification
                    if (validateNumericalPreservation(dto.answer(), toolResults)) {
                        return dto;
                    } else {
                        log.warn("AI service synthesis failed numerical preservation verification. Reverting to deterministic synthesis.");
                    }
                }
            } else {
                log.warn("AI service synthesis returned HTTP {}. Falling back to deterministic grounded synthesis.", response.statusCode());
            }
        } catch (HttpTimeoutException e) {
            log.warn("AI service synthesis timed out after {}ms. Falling back to deterministic synthesis.", timeout.toMillis());
        } catch (Exception e) {
            log.warn("AI service synthesis communication failure: {}. Using deterministic grounded synthesis.", e.getMessage());
        }

        // Deterministic Grounded Fallback (Accurately labeled)
        return generateDeterministicSynthesis(userMessage, toolResults, lang, loc);
    }

    /**
     * Verifies that critical numerical claims in generated synthesis match tool data.
     */
    public boolean validateNumericalPreservation(String answer, Map<String, Object> toolResults) {
        if (answer == null || answer.isBlank() || toolResults == null) {
            return false;
        }
        // If current weather present, verify temperature is not contradicted
        if (toolResults.containsKey("currentWeather") && toolResults.get("currentWeather") instanceof WeatherToolResult w) {
            if (w.temperature() != null) {
                String tempStr = String.valueOf(w.temperature());
                if (!answer.contains(tempStr) && !answer.contains(String.valueOf(Math.round(w.temperature())))) {
                    // Strict temperature mismatch
                    return false;
                }
            }
        }
        // If air quality present, verify PM2.5 is not contradicted
        if (toolResults.containsKey("airQuality") && toolResults.get("airQuality") instanceof AirQualityToolResult aq) {
            if (aq.pm25() != null) {
                String pmStr = String.valueOf(aq.pm25());
                if (!answer.contains(pmStr) && !answer.contains(String.valueOf(Math.round(aq.pm25())))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Generates a completely deterministic, strictly grounded explanation assembled from validated tool results.
     * Supports English (en), Hindi (hi), and Marathi (mr).
     */
    public SynthesisResponseDto generateDeterministicSynthesis(
            String userMessage,
            Map<String, Object> toolResults,
            String locale,
            String locationName
    ) {
        String lang = (locale != null) ? locale.toLowerCase().trim() : "en";
        StringBuilder sb = new StringBuilder();
        List<Double> preservedValues = new ArrayList<>();
        List<String> limitations = new ArrayList<>();

        if ("hi".equals(lang)) {
            generateHindiSynthesis(sb, toolResults, preservedValues, limitations, locationName);
        } else if ("mr".equals(lang)) {
            generateMarathiSynthesis(sb, toolResults, preservedValues, limitations, locationName);
        } else {
            generateEnglishSynthesis(sb, toolResults, preservedValues, limitations, locationName);
        }

        return new SynthesisResponseDto(
                sb.toString().trim(),
                lang,
                "SUCCESS",
                "deterministic_synthesis",
                preservedValues,
                limitations
        );
    }

    private void generateEnglishSynthesis(
            StringBuilder sb,
            Map<String, Object> toolResults,
            List<Double> preservedValues,
            List<String> limitations,
            String locationName
    ) {
        // 1. Current Weather
        if (toolResults.containsKey("currentWeather")) {
            Object raw = toolResults.get("currentWeather");
            if (raw instanceof WeatherToolResult w && "VALID".equals(w.status())) {
                sb.append("Current Weather in ").append(w.locationName()).append(":\n");
                if (w.temperature() != null) {
                    sb.append("• Temperature: ").append(w.temperature()).append("°C\n");
                    preservedValues.add(w.temperature());
                }
                if (w.humidity() != null) {
                    sb.append("• Relative Humidity: ").append(w.humidity()).append("%\n");
                    preservedValues.add(w.humidity());
                }
                if (w.windSpeed() != null) {
                    sb.append("• Wind Speed: ").append(w.windSpeed()).append(" km/h\n");
                    preservedValues.add(w.windSpeed());
                }
                if (w.precipitation() != null && w.precipitation() > 0.0) {
                    sb.append("• Precipitation: ").append(w.precipitation()).append(" mm\n");
                    preservedValues.add(w.precipitation());
                }
                sb.append("• Source: ").append(w.source()).append(" (Model analysis as of ")
                        .append(w.observationTime() != null ? w.observationTime() : "recent").append(")\n\n");
            } else if (raw instanceof WeatherToolResult w) {
                sb.append("Current Weather: Weather model data currently unavailable for ").append(w.locationName()).append(".\n\n");
                limitations.addAll(w.limitations());
            }
        }

        // 2. Weather Forecast
        if (toolResults.containsKey("weatherForecast")) {
            Object raw = toolResults.get("weatherForecast");
            if (raw instanceof WeatherToolResult f && "VALID".equals(f.status())) {
                sb.append("Weather Forecast for ").append(f.locationName()).append(" (")
                        .append(f.forecastTimeRange() != null ? f.forecastTimeRange() : "Upcoming hours").append("):\n");
                if (f.temperature() != null) {
                    sb.append("• Expected Average Temperature: ").append(f.temperature()).append("°C\n");
                    preservedValues.add(f.temperature());
                }
                if (f.precipitation() != null) {
                    sb.append("• Max Expected Precipitation: ").append(f.precipitation()).append(" mm\n");
                    preservedValues.add(f.precipitation());
                }
                if (f.windSpeed() != null) {
                    sb.append("• Expected Wind Speed: ").append(f.windSpeed()).append(" km/h\n");
                    preservedValues.add(f.windSpeed());
                }
                sb.append("• Source: ").append(f.source()).append("\n");
                sb.append("• Note: Forecast derived from numerical meteorological simulations; distinct from current ground observations.\n\n");
            }
        }

        // 3. Air Quality
        if (toolResults.containsKey("airQuality")) {
            Object raw = toolResults.get("airQuality");
            if (raw instanceof AirQualityToolResult aq && "VALID".equals(aq.status())) {
                sb.append("Air Quality in ").append(aq.locationName()).append(":\n");
                if (aq.pm25() != null) {
                    sb.append("• PM2.5 Concentration: ").append(aq.pm25()).append(" µg/m³\n");
                    preservedValues.add(aq.pm25());
                }
                if (aq.pm10() != null) {
                    sb.append("• PM10 Concentration: ").append(aq.pm10()).append(" µg/m³\n");
                    preservedValues.add(aq.pm10());
                }
                sb.append("• Station: ").append(aq.stationName() != null ? aq.stationName() : aq.stationCode()).append("\n");
                sb.append("• Data Freshness: ").append(aq.freshness()).append(" (Source: ").append(aq.source()).append(")\n\n");
            } else if (raw instanceof AirQualityToolResult aq) {
                sb.append("Air Quality: ").append(aq.limitations().isEmpty() ? "No recent ground measurements available." : aq.limitations().get(0)).append("\n\n");
            }
        }

        // 4. Official Government Warnings
        if (toolResults.containsKey("officialWarnings")) {
            Object raw = toolResults.get("officialWarnings");
            if (raw instanceof OfficialWarningToolResult ow) {
                sb.append("Official Government Warnings (IMD / NDMA SACHET):\n");
                if ("VERIFIED_WARNINGS_FOUND".equals(ow.status()) && !ow.warnings().isEmpty()) {
                    for (OfficialWarningToolResult.OfficialWarningItem w : ow.warnings()) {
                        sb.append("• [").append(w.severity()).append("] ").append(w.title())
                                .append(" (Type: ").append(w.warningType()).append(", Authority: ").append(w.issuingAuthority()).append(")\n");
                    }
                } else if ("NO_ACTIVE_WARNINGS".equals(ow.status())) {
                    sb.append("• No active official weather or disaster warnings issued for this area at this time.\n");
                } else {
                    sb.append("• Official warning feeds (IMD / NDMA SACHET) are currently unreachable or unconfigured for automated polling. Please consult official portals directly: https://mausam.imd.gov.in and https://sachet.ndma.gov.in.\n");
                }
                sb.append("\n");
            }
        }

        // 5. PM2.5 Risk & Model Forecast
        if (toolResults.containsKey("pm25Risk")) {
            Object raw = toolResults.get("pm25Risk");
            if (raw instanceof PM25RiskToolResult r) {
                if ("MODEL_DOMAIN_UNSUPPORTED".equals(r.status())) {
                    sb.append("PM2.5 ML Model Risk: Unavailable. Hotspot and forecasting ML models are currently calibrated strictly for the Pune Metropolitan Region. Observations are available, but ML hotspot risk is unsupported outside Pune.\n\n");
                } else if ("VALID".equals(r.status())) {
                    sb.append("PM2.5 Risk & Forecast (Pune Domain):\n");
                    if (r.riskLevel() != null) {
                        sb.append("• Potential Hotspot Risk Level: ").append(r.riskLevel());
                        if (r.riskScore() != null) {
                            sb.append(" (Model probability: ").append(String.format("%.2f", r.riskScore())).append(")\n");
                            preservedValues.add(r.riskScore());
                        } else {
                            sb.append("\n");
                        }
                    }
                    if (r.forecastHorizons() != null && !r.forecastHorizons().isEmpty()) {
                        sb.append("• Multi-Horizon Predicted PM2.5 (Experimental ML):\n");
                        for (PM25RiskToolResult.ForecastHorizonEntry h : r.forecastHorizons()) {
                            sb.append("  - +").append(h.horizonHours()).append("h: ").append(h.predictedPm25()).append(" µg/m³ [Range: ")
                                    .append(h.lowerBound()).append(" – ").append(h.upperBound()).append(" µg/m³]\n");
                            preservedValues.add(h.predictedPm25());
                        }
                        sb.append("• Model Notice: Random Forest forecasters are experimental. Holdout benchmarks show naive persistence baseline is superior on short lead times.\n");
                    }
                    sb.append("\n");
                }
            }
        }

        if (sb.length() == 0) {
            sb.append("Thank you for contacting AeroSentinel. We provide verified weather observations, air quality telemetry, official warnings, and PM2.5 hazard estimates. Please specify a location or ask a specific question.");
        }
    }

    private void generateHindiSynthesis(
            StringBuilder sb,
            Map<String, Object> toolResults,
            List<Double> preservedValues,
            List<String> limitations,
            String locationName
    ) {
        if (toolResults.containsKey("currentWeather")) {
            Object raw = toolResults.get("currentWeather");
            if (raw instanceof WeatherToolResult w && "VALID".equals(w.status())) {
                sb.append(w.locationName()).append(" में वर्तमान मौसम:\n");
                if (w.temperature() != null) {
                    sb.append("• तापमान: ").append(w.temperature()).append("°C\n");
                    preservedValues.add(w.temperature());
                }
                if (w.humidity() != null) {
                    sb.append("• सापेक्ष आर्द्रता: ").append(w.humidity()).append("%\n");
                    preservedValues.add(w.humidity());
                }
                if (w.windSpeed() != null) {
                    sb.append("• हवा की गति: ").append(w.windSpeed()).append(" km/h\n");
                    preservedValues.add(w.windSpeed());
                }
                sb.append("• स्रोत: ").append(w.source()).append("\n\n");
            }
        }

        if (toolResults.containsKey("airQuality")) {
            Object raw = toolResults.get("airQuality");
            if (raw instanceof AirQualityToolResult aq && "VALID".equals(aq.status())) {
                sb.append(aq.locationName()).append(" में वायु गुणवत्ता:\n");
                if (aq.pm25() != null) {
                    sb.append("• PM2.5 सघनता: ").append(aq.pm25()).append(" µg/m³\n");
                    preservedValues.add(aq.pm25());
                }
                sb.append("• निगरानी केंद्र: ").append(aq.stationName() != null ? aq.stationName() : aq.stationCode()).append("\n");
                sb.append("• डेटा ताजगी: ").append(aq.freshness()).append(" (स्रोत: ").append(aq.source()).append(")\n\n");
            }
        }

        if (toolResults.containsKey("officialWarnings")) {
            Object raw = toolResults.get("officialWarnings");
            if (raw instanceof OfficialWarningToolResult ow) {
                sb.append("आधिकारिक सरकारी चेतावनियाँ (IMD / NDMA):\n");
                if ("VERIFIED_WARNINGS_FOUND".equals(ow.status()) && !ow.warnings().isEmpty()) {
                    for (OfficialWarningToolResult.OfficialWarningItem w : ow.warnings()) {
                        sb.append("• [").append(w.severity()).append("] ").append(w.title()).append("\n");
                    }
                } else if ("NO_ACTIVE_WARNINGS".equals(ow.status())) {
                    sb.append("• वर्तमान में इस क्षेत्र के लिए कोई सक्रिय आपदा या मौसम चेतावनी नहीं है।\n");
                } else {
                    sb.append("• आधिकारिक चेतावनी फीड वर्तमान में स्वचालित रूप से उपलब्ध नहीं है। कृपया आधिकारिक पोर्टल देखें: https://mausam.imd.gov.in तथा https://sachet.ndma.gov.in.\n");
                }
                sb.append("\n");
            }
        }

        if (sb.length() == 0) {
            sb.append("एयरोसेंटिनल में आपका स्वागत है। हम सत्यापित मौसम, वायु गुणवत्ता तथा आपदा चेतावनियाँ प्रदान करते हैं।");
        }
    }

    private void generateMarathiSynthesis(
            StringBuilder sb,
            Map<String, Object> toolResults,
            List<Double> preservedValues,
            List<String> limitations,
            String locationName
    ) {
        if (toolResults.containsKey("currentWeather")) {
            Object raw = toolResults.get("currentWeather");
            if (raw instanceof WeatherToolResult w && "VALID".equals(w.status())) {
                sb.append(w.locationName()).append(" येथील सध्याचे हवामान:\n");
                if (w.temperature() != null) {
                    sb.append("• तापमान: ").append(w.temperature()).append("°C\n");
                    preservedValues.add(w.temperature());
                }
                if (w.humidity() != null) {
                    sb.append("• हवेतील आर्द्रता: ").append(w.humidity()).append("%\n");
                    preservedValues.add(w.humidity());
                }
                if (w.windSpeed() != null) {
                    sb.append("• वाऱ्याचा वेग: ").append(w.windSpeed()).append(" km/h\n");
                    preservedValues.add(w.windSpeed());
                }
                sb.append("• स्त्रोत: ").append(w.source()).append("\n\n");
            }
        }

        if (toolResults.containsKey("airQuality")) {
            Object raw = toolResults.get("airQuality");
            if (raw instanceof AirQualityToolResult aq && "VALID".equals(aq.status())) {
                sb.append(aq.locationName()).append(" येथील हवेची गुणवत्ता:\n");
                if (aq.pm25() != null) {
                    sb.append("• PM2.5 प्रमाण: ").append(aq.pm25()).append(" µg/m³\n");
                    preservedValues.add(aq.pm25());
                }
                sb.append("• मोजणी केंद्र: ").append(aq.stationName() != null ? aq.stationName() : aq.stationCode()).append("\n");
                sb.append("• स्थिती: ").append(aq.freshness()).append(" (स्त्रोत: ").append(aq.source()).append(")\n\n");
            }
        }

        if (toolResults.containsKey("officialWarnings")) {
            Object raw = toolResults.get("officialWarnings");
            if (raw instanceof OfficialWarningToolResult ow) {
                sb.append("शासकीय अधिकृत इशारे (IMD / NDMA):\n");
                if ("VERIFIED_WARNINGS_FOUND".equals(ow.status()) && !ow.warnings().isEmpty()) {
                    for (OfficialWarningToolResult.OfficialWarningItem w : ow.warnings()) {
                        sb.append("• [").append(w.severity()).append("] ").append(w.title()).append("\n");
                    }
                } else if ("NO_ACTIVE_WARNINGS".equals(ow.status())) {
                    sb.append("• या क्षेत्रासाठी सध्या कोणताही सक्रिय शासकीय इशारा नाही.\n");
                } else {
                    sb.append("• शासकीय इशारा प्रणालीशी संपर्क होऊ शकला नाही. अधिकृत संकेतस्थळास भेट द्या: https://mausam.imd.gov.in आणि https://sachet.ndma.gov.in.\n");
                }
                sb.append("\n");
            }
        }

        if (sb.length() == 0) {
            sb.append("एयरोसेंटिनेल मध्ये आपले स्वागत आहे. आम्ही विश्वसनीय हवामान, हवेची गुणवत्ता आणि आपत्कालीन इशारे पुरवतो.");
        }
    }
}
