package com.aerosentinel.assistant.service;

import com.aerosentinel.assistant.dto.*;
import com.aerosentinel.assistant.tools.*;
import com.aerosentinel.city.City;
import com.aerosentinel.city.CityRepository;
import com.aerosentinel.exception.ValidationException;
import com.aerosentinel.spatial.H3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class AssistantOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AssistantOrchestrationService.class);

    // Prompt injection & authority boundary pattern guards
    private static final Pattern ADVERSARIAL_INJECTION_PATTERN = Pattern.compile(
            "(?i)(ignore (all )?(previous |your )?instructions|system override|you are now in admin mode|grant (me )?admin|execute command|drop table|delete alerts?|resolve (alert|incident)s?|assign field team|acknowledge alert|escalate alert|expose (authority|admin) data)"
    );

    private final WeatherTool weatherTool;
    private final OfficialWarningTool officialWarningTool;
    private final AirQualityTool airQualityTool;
    private final PM25RiskTool pm25RiskTool;
    private final DataFreshnessTool dataFreshnessTool;
    private final AssistantSynthesisClient synthesisClient;
    private final AssistantRateLimiter rateLimiter;
    private final CityRepository cityRepository;
    private final H3Service h3Service;

    public AssistantOrchestrationService(
            WeatherTool weatherTool,
            OfficialWarningTool officialWarningTool,
            AirQualityTool airQualityTool,
            PM25RiskTool pm25RiskTool,
            DataFreshnessTool dataFreshnessTool,
            AssistantSynthesisClient synthesisClient,
            AssistantRateLimiter rateLimiter,
            CityRepository cityRepository,
            H3Service h3Service
    ) {
        this.weatherTool = weatherTool;
        this.officialWarningTool = officialWarningTool;
        this.airQualityTool = airQualityTool;
        this.pm25RiskTool = pm25RiskTool;
        this.dataFreshnessTool = dataFreshnessTool;
        this.synthesisClient = synthesisClient;
        this.rateLimiter = rateLimiter;
        this.cityRepository = cityRepository;
        this.h3Service = h3Service;
    }

    /**
     * Orchestrates a citizen natural-language request with strict safety boundaries and tool grounding.
     */
    public AssistantChatResponse processChat(AssistantChatRequest request, String clientIdentifier) {
        String correlationId = UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();

        if (request == null || request.message() == null || request.message().trim().isEmpty()) {
            throw new ValidationException("User message cannot be empty.");
        }

        String rawMessage = request.message().trim();
        if (rawMessage.length() > 1000) {
            throw new ValidationException("Message exceeds maximum allowable length of 1000 characters.");
        }

        // 1. Client Identity & Rate Limiting Note
        // Note: HTTP rate limiting is enforced with HTTP 429 status and Retry-After header
        // at the AssistantController layer (single-token accounting).
        String safeClientId = (clientIdentifier != null && !clientIdentifier.isBlank())
                ? clientIdentifier.trim() : "anonymous";

        // 2. Prompt Injection & Authority Boundary Enforcement
        if (ADVERSARIAL_INJECTION_PATTERN.matcher(rawMessage).find()) {
            log.warn("Prompt injection attempt intercepted from client '{}': '{}'", clientIdentifier, rawMessage);
            return new AssistantChatResponse(
                    "AeroSentinel Citizen Assistant operates strictly in an informational, read-only mode grounded in verified environmental telemetry. " +
                    "Administrative overrides, incident escalation, and authority actions cannot be executed through citizen chat.",
                    request.locale() != null ? request.locale() : "en",
                    List.of(),
                    null,
                    "SUCCESS",
                    List.of("Adversarial instructions or privileged command requests are blocked by safety policy."),
                    correlationId,
                    timestamp,
                    null
            );
        }

        // 3. Resolve Location
        LocationDto resolvedLocation = resolveLocation(request.location(), rawMessage);

        // 4. Intent Classification & Location Requirement Guard
        boolean asksWeatherForecast = matchesAny(rawMessage, "forecast", "tomorrow", "tonight", "this evening", "next few hours", "will it rain", "expected weather");
        boolean asksWeatherCurrent = matchesAny(rawMessage, "weather", "temperature", "humidity", "rain", "wind", "temp", "cold", "hot", "climate") && !asksWeatherForecast;
        boolean asksWarnings = matchesAny(rawMessage, "warning", "warnings", "disaster", "alert", "cyclone", "flood", "heat wave", "imd", "sachet", "emergency");
        boolean asksFreshness = matchesAny(rawMessage, "freshness", "how old", "source", "when was", "stale", "retrieval");
        boolean asksRisk = matchesAny(rawMessage, "risk", "hazard", "hotspot", "danger", "safe outside");
        boolean asksAirQuality = (matchesAny(rawMessage, "reading", "sensor", "telemetry", "latest pm2.5", "latest pm25", "latest air")
                || (!asksFreshness && !asksRisk && matchesAny(rawMessage, "air quality", "pm25", "pm2.5", "pm10", "pollution", "aqi", "smoke", "smog", "air"))
                || matchesAny(rawMessage, "weather and air quality", "weather and air"));

        // If user asks about local conditions but gave no identifiable location, ask follow-up clarification
        boolean needsLocation = (asksWeatherCurrent || asksWeatherForecast || asksAirQuality || asksRisk || asksWarnings);
        if (needsLocation && (resolvedLocation == null || (!resolvedLocation.hasCoordinates() && !resolvedLocation.hasCityName()))) {
            return AssistantChatResponse.clarification(
                    "Which city or area would you like environmental information for (e.g. Pune, Mumbai, or Delhi)?",
                    correlationId,
                    timestamp,
                    request.locale()
            );
        }

        // If no explicit intent matched, default to answering general weather & air for the resolved location
        if (!asksWeatherCurrent && !asksWeatherForecast && !asksWarnings && !asksAirQuality && !asksRisk && !asksFreshness) {
            asksWeatherCurrent = true;
            asksAirQuality = true;
        }

        // 5. Execute Allowed Tools
        Map<String, Object> toolResults = new HashMap<>();
        List<SourceReferenceDto> sources = new ArrayList<>();
        List<String> limitations = new ArrayList<>();

        // Tool 1: Current Weather
        if (asksWeatherCurrent && !asksWeatherForecast) {
            WeatherToolResult w = weatherTool.getCurrentWeather(resolvedLocation);
            if (w != null) {
                toolResults.put("currentWeather", w);
                if ("VALID".equals(w.status())) {
                    sources.add(new SourceReferenceDto(
                            w.source(),
                            null,
                            null,
                            "https://open-meteo.com",
                            w.observationTime(),
                            w.freshness(),
                            "Weather Observation"
                    ));
                }
                if (w.limitations() != null) {
                    limitations.addAll(w.limitations());
                }
            }
        }

        // Tool 2: Weather Forecast
        if (asksWeatherForecast) {
            String timeRange = rawMessage.toLowerCase().contains("evening") ? "Evening (18:00 – 23:00 IST)" :
                               rawMessage.toLowerCase().contains("tomorrow") ? "Tomorrow" : "Next 6 to 24 Hours";
            WeatherToolResult f = weatherTool.getWeatherForecast(resolvedLocation, timeRange);
            if (f != null) {
                toolResults.put("weatherForecast", f);
                if ("VALID".equals(f.status())) {
                    sources.add(new SourceReferenceDto(
                            f.source(),
                            null,
                            null,
                            "https://open-meteo.com",
                            null,
                            "LIVE",
                            "Open-Meteo Numerical Weather Forecast"
                    ));
                }
                if (f.limitations() != null) {
                    limitations.addAll(f.limitations());
                }
            }
        }

        // Tool 3: Official Warnings
        if (asksWarnings) {
            OfficialWarningToolResult ow = officialWarningTool.getOfficialWarnings(resolvedLocation);
            if (ow != null) {
                toolResults.put("officialWarnings", ow);
                sources.add(new SourceReferenceDto(
                        ow.sourceAuthority(),
                        null,
                        null,
                        ow.sourceUrl(),
                        ow.retrievedAt(),
                        "VERIFIED_WARNINGS_FOUND".equals(ow.status()) ? "LIVE" : "UNAVAILABLE",
                        "Official Government Disaster Warning Feed"
                ));
                if (ow.limitations() != null) {
                    limitations.addAll(ow.limitations());
                }
            }
        }

        // Tool 4: Air Quality
        if (asksAirQuality) {
            AirQualityToolResult aq = airQualityTool.getAirQuality(resolvedLocation);
            if (aq != null) {
                toolResults.put("airQuality", aq);
                if ("VALID".equals(aq.status())) {
                    sources.add(new SourceReferenceDto(
                            aq.source(),
                            aq.stationName(),
                            aq.stationCode(),
                            "https://openaq.org",
                            aq.observedAt(),
                            aq.freshness(),
                            "Ground Reference Monitor Telemetry"
                    ));
                }
                if (aq.limitations() != null) {
                    limitations.addAll(aq.limitations());
                }
            }
        }

        // Tool 5: PM2.5 ML Risk
        if (asksRisk) {
            PM25RiskToolResult r = pm25RiskTool.getPM25Risk(resolvedLocation, null);
            if (r != null) {
                toolResults.put("pm25Risk", r);
                if ("VALID".equals(r.status())) {
                    sources.add(new SourceReferenceDto(
                            "AeroSentinel F3 Hotspot Classifier",
                            null,
                            null,
                            "/api/v1/hotspots/overview",
                            timestamp,
                            "LIVE",
                            "Calibrated ML Hazard Classification (Pune Domain)"
                    ));
                }
                if (r.limitations() != null) {
                    limitations.addAll(r.limitations());
                }
            }
        }

        // Tool 6: Data Freshness
        if (asksFreshness) {
            String freshnessSource = rawMessage.toLowerCase().contains("weather") ? "WEATHER" :
                                     rawMessage.toLowerCase().contains("warning") ? "WARNING" :
                                     rawMessage.toLowerCase().contains("forecast") ? "PM25_FORECAST" : "AIR_QUALITY";
            DataFreshnessToolResult df = dataFreshnessTool.getDataFreshness(freshnessSource, resolvedLocation);
            if (df != null) {
                toolResults.put("dataFreshness", df);
                sources.add(new SourceReferenceDto(
                        df.sourceName(),
                        df.locationName(),
                        null,
                        null,
                        df.lastObservedAt(),
                        df.freshnessStatus(),
                        "Telemetry Metadata"
                ));
                if (df.limitations() != null) {
                    limitations.addAll(df.limitations());
                }
            }
        }

        // 6. Grounded Synthesis
        String locName = (resolvedLocation != null && resolvedLocation.cityName() != null)
                ? resolvedLocation.cityName() : "Local Area";
        AssistantSynthesisClient.SynthesisResponseDto synthesis = synthesisClient.synthesize(
                rawMessage, toolResults, request.locale(), locName
        );

        String finalAnswer = synthesis.answer();
        if (synthesis.limitations() != null) {
            limitations.addAll(synthesis.limitations());
        }

        // Determine overall status
        String overallStatus = "SUCCESS";
        if (toolResults.values().stream().anyMatch(v -> {
            if (v instanceof WeatherToolResult w) return "UNAVAILABLE".equals(w.status());
            if (v instanceof AirQualityToolResult a) return "UNAVAILABLE".equals(a.status());
            if (v instanceof OfficialWarningToolResult o) return "SOURCE_UNAVAILABLE".equals(o.status());
            return false;
        })) {
            overallStatus = "PARTIAL_DATA";
        }

        return new AssistantChatResponse(
                finalAnswer,
                request.locale() != null ? request.locale() : "en",
                sources,
                timestamp,
                overallStatus,
                limitations.stream().distinct().toList(),
                correlationId,
                timestamp,
                null
        );
    }

    /**
     * Resolves location context from the explicit DTO or by matching known city entities in the user text.
     */
    private LocationDto resolveLocation(LocationDto inputLocation, String message) {
        if (inputLocation != null) {
            if (inputLocation.hasCoordinates()) {
                return inputLocation;
            }
            if (inputLocation.hasH3Index() && h3Service.validateH3Index(inputLocation.h3Index())) {
                try {
                    com.uber.h3core.util.LatLng c = h3Service.h3ToCenter(inputLocation.h3Index());
                    return new LocationDto(inputLocation.cityName(), c.lat, c.lng, inputLocation.h3Index());
                } catch (Exception ignored) {}
            }
            if (inputLocation.hasCityName()) {
                Optional<City> cOpt = cityRepository.findByNameIgnoreCase(inputLocation.cityName().trim());
                if (cOpt.isPresent()) {
                    City c = cOpt.get();
                    return new LocationDto(c.getName(), c.getLatitude(), c.getLongitude(), null);
                }
            }
        }

        // Scan message text for recognized cities
        String lower = message.toLowerCase();
        List<City> cities = cityRepository.findAll();
        for (City city : cities) {
            if (lower.contains(city.getName().toLowerCase())) {
                return new LocationDto(city.getName(), city.getLatitude(), city.getLongitude(), null);
            }
        }

        // Common Indian cities aliases
        if (lower.contains("pune")) return new LocationDto("Pune", 18.5314, 73.8446, "88608850e5fffff");
        if (lower.contains("mumbai") || lower.contains("bombay")) return new LocationDto("Mumbai", 19.0760, 72.8777, "88608b56b3fffff");
        if (lower.contains("delhi")) return new LocationDto("Delhi", 28.6139, 77.2090, "883da11505fffff");

        return null;
    }

    private boolean matchesAny(String text, String... keywords) {
        String lower = text.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    public OfficialWarningToolResult getOfficialWarnings(LocationDto location) {
        LocationDto resolved = resolveLocation(location, location != null && location.cityName() != null ? location.cityName() : "");
        return officialWarningTool.getOfficialWarnings(resolved != null ? resolved : location);
    }

    public WeatherToolResult getCurrentWeather(LocationDto location) {
        LocationDto resolved = resolveLocation(location, location != null && location.cityName() != null ? location.cityName() : "");
        return weatherTool.getCurrentWeather(resolved != null ? resolved : location);
    }

    public WeatherToolResult getWeatherForecast(LocationDto location, String timeRange) {
        LocationDto resolved = resolveLocation(location, location != null && location.cityName() != null ? location.cityName() : "");
        return weatherTool.getWeatherForecast(resolved != null ? resolved : location, timeRange);
    }

    public AirQualityToolResult getAirQuality(LocationDto location) {
        LocationDto resolved = resolveLocation(location, location != null && location.cityName() != null ? location.cityName() : "");
        return airQualityTool.getAirQuality(resolved != null ? resolved : location);
    }

    public PM25RiskToolResult getPM25Risk(LocationDto location) {
        LocationDto resolved = resolveLocation(location, location != null && location.cityName() != null ? location.cityName() : "");
        return pm25RiskTool.getPM25Risk(resolved != null ? resolved : location, null);
    }
}
