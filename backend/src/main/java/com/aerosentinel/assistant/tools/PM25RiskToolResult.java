package com.aerosentinel.assistant.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PM25RiskToolResult(
        String h3Index,
        String locationName,
        Boolean isHotspot,
        Double riskScore,
        String riskLevel,
        String modelVersion,
        Double operationalThreshold,
        List<ForecastHorizonEntry> forecastHorizons,
        Double forecastConfidence, // Strictly null
        boolean domainSupported,
        String status, // VALID, MODEL_DOMAIN_UNSUPPORTED, MODEL_UNAVAILABLE
        List<String> limitations
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ForecastHorizonEntry(
            Integer horizonHours,
            String targetTime,
            Double predictedPm25,
            Double lowerBound,
            Double upperBound,
            String unit,
            String modelStatus // "EXPERIMENTAL_ML"
    ) {}

    public static PM25RiskToolResult domainUnsupported(String locationName, String h3Index) {
        return new PM25RiskToolResult(
                h3Index,
                locationName,
                null,
                null,
                null,
                "hotspot_classifier_v1",
                0.20,
                List.of(),
                null,
                false,
                "MODEL_DOMAIN_UNSUPPORTED",
                List.of("PM2.5 ML risk model is currently calibrated and validated strictly for the Pune Metropolitan Region. Observations are available, but ML hotspot risk is unsupported outside Pune.")
        );
    }

    public static PM25RiskToolResult unavailable(String locationName, String h3Index, String reason) {
        return new PM25RiskToolResult(
                h3Index,
                locationName,
                null,
                null,
                null,
                "hotspot_classifier_v1",
                0.20,
                List.of(),
                null,
                true,
                "MODEL_UNAVAILABLE",
                List.of(reason != null ? reason : "PM2.5 ML risk service is currently unreachable.")
        );
    }
}
