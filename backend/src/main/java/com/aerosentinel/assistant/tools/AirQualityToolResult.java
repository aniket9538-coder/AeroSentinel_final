package com.aerosentinel.assistant.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AirQualityToolResult(
        String locationName,
        String stationCode,
        String stationName,
        Double pm25,
        String pm25Unit,
        Double pm10,
        String pm10Unit,
        String observedAt,
        String retrievedAt,
        String source,
        String freshness, // LIVE, STALE, NO_DATA, SOURCE_UNAVAILABLE
        String dataQuality,
        String status,    // VALID, NO_DATA, UNAVAILABLE
        List<String> limitations
) {
    public static AirQualityToolResult noData(String locationName, String reason) {
        return new AirQualityToolResult(
                locationName,
                null,
                null,
                null,
                "ug/m3",
                null,
                "ug/m3",
                null,
                null,
                "CPCB / OpenAQ",
                "NO_DATA",
                "UNKNOWN",
                "NO_DATA",
                List.of(reason != null ? reason : "No recent ground monitoring measurements available for this location.")
        );
    }

    public static AirQualityToolResult unavailable(String locationName, String reason) {
        return new AirQualityToolResult(
                locationName,
                null,
                null,
                null,
                "ug/m3",
                null,
                "ug/m3",
                null,
                null,
                "CPCB / OpenAQ",
                "SOURCE_UNAVAILABLE",
                "UNAVAILABLE",
                "UNAVAILABLE",
                List.of(reason != null ? reason : "Air quality telemetry service is currently unavailable.")
        );
    }
}
