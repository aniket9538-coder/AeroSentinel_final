package com.aerosentinel.assistant.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WeatherToolResult(
        boolean isForecast,
        String locationName,
        Double latitude,
        Double longitude,
        Double temperature,
        String temperatureUnit,
        Double humidity,
        String humidityUnit,
        Double windSpeed,
        String windSpeedUnit,
        Double windDirection,
        Double precipitation,
        String precipitationUnit,
        Double surfacePressure,
        String observationTime,
        String forecastTimeRange,
        List<HourlyForecastEntry> hourlyForecasts,
        String source,
        String freshness,
        String status,
        List<String> limitations
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HourlyForecastEntry(
            String timestamp,
            Double temperature,
            Double humidity,
            Double precipitation,
            Double windSpeed
    ) {}

    public static WeatherToolResult unavailable(String locationName, String source, String reason) {
        return new WeatherToolResult(
                false,
                locationName,
                null,
                null,
                null,
                "°C",
                null,
                "%",
                null,
                "km/h",
                null,
                null,
                "mm",
                null,
                null,
                null,
                List.of(),
                source != null ? source : "Open-Meteo",
                "SOURCE_UNAVAILABLE",
                "UNAVAILABLE",
                List.of(reason)
        );
    }
}
