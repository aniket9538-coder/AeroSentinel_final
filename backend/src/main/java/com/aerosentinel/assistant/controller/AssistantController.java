package com.aerosentinel.assistant.controller;

import com.aerosentinel.assistant.dto.AssistantChatRequest;
import com.aerosentinel.assistant.dto.AssistantChatResponse;
import com.aerosentinel.assistant.dto.LocationDto;
import com.aerosentinel.assistant.service.AssistantOrchestrationService;
import com.aerosentinel.assistant.service.AssistantRateLimiter;
import com.aerosentinel.assistant.service.ClientIpResolver;
import com.aerosentinel.assistant.tools.AirQualityToolResult;
import com.aerosentinel.assistant.tools.OfficialWarningToolResult;
import com.aerosentinel.assistant.tools.PM25RiskToolResult;
import com.aerosentinel.assistant.tools.WeatherToolResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final AssistantOrchestrationService orchestrationService;
    private final AssistantRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    public AssistantController(
            AssistantOrchestrationService orchestrationService,
            AssistantRateLimiter rateLimiter,
            ClientIpResolver clientIpResolver
    ) {
        this.orchestrationService = orchestrationService;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    /**
     * Public / Citizen Conversational Assistant endpoint.
     * Answers weather, air quality, forecast, warning, and freshness questions grounded strictly in validated tools.
     * Protected by sliding-window rate limiting with explicit HTTP 429 status and Retry-After header.
     */
    @PostMapping("/chat")
    public ResponseEntity<AssistantChatResponse> chat(
            @Valid @RequestBody AssistantChatRequest request,
            HttpServletRequest servletRequest
    ) {
        String clientIdentifier = clientIpResolver.resolveClientIp(servletRequest);

        // Enforce rate limiting with HTTP 429
        if (!rateLimiter.tryAcquire(clientIdentifier)) {
            AssistantChatResponse rateLimitResp = new AssistantChatResponse(
                    "You have sent too many requests in a short period. Please wait a minute before asking another question.",
                    request.locale() != null ? request.locale() : "en",
                    List.of(),
                    null,
                    "UNAVAILABLE",
                    List.of("Rate limit exceeded (maximum " + rateLimiter.getMaxRequestsPerMinute() + " requests per minute)."),
                    UUID.randomUUID().toString(),
                    Instant.now().toString(),
                    null
            );
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "60")
                    .body(rateLimitResp);
        }

        AssistantChatResponse response = orchestrationService.processChat(request, clientIdentifier);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "AeroSentinel Conversational Assistant",
                "mode", "Grounded Tool Orchestration"
        ));
    }

    @GetMapping("/warnings")
    public ResponseEntity<OfficialWarningToolResult> getWarnings(
            @RequestParam(required = false) String cityName,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        LocationDto loc = new LocationDto(cityName, latitude, longitude, null);
        return ResponseEntity.ok(orchestrationService.getOfficialWarnings(loc));
    }

    @GetMapping("/weather/current")
    public ResponseEntity<WeatherToolResult> getCurrentWeather(
            @RequestParam(required = false) String cityName,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        LocationDto loc = new LocationDto(cityName, latitude, longitude, null);
        return ResponseEntity.ok(orchestrationService.getCurrentWeather(loc));
    }

    @GetMapping("/weather/forecast")
    public ResponseEntity<WeatherToolResult> getWeatherForecast(
            @RequestParam(required = false) String cityName,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false, defaultValue = "Next 72 Hours") String timeRange
    ) {
        LocationDto loc = new LocationDto(cityName, latitude, longitude, null);
        return ResponseEntity.ok(orchestrationService.getWeatherForecast(loc, timeRange));
    }

    @GetMapping("/air-quality")
    public ResponseEntity<AirQualityToolResult> getAirQuality(
            @RequestParam(required = false) String cityName,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        LocationDto loc = new LocationDto(cityName, latitude, longitude, null);
        return ResponseEntity.ok(orchestrationService.getAirQuality(loc));
    }

    @GetMapping("/pm25-risk")
    public ResponseEntity<PM25RiskToolResult> getPM25Risk(
            @RequestParam(required = false) String cityName,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        LocationDto loc = new LocationDto(cityName, latitude, longitude, null);
        return ResponseEntity.ok(orchestrationService.getPM25Risk(loc));
    }
}
