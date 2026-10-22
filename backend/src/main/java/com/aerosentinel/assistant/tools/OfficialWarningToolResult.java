package com.aerosentinel.assistant.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfficialWarningToolResult(
        String status, // VERIFIED_WARNINGS_FOUND, NO_ACTIVE_WARNINGS, SOURCE_UNAVAILABLE, LOCATION_NOT_COVERED, UNCONFIGURED
        String locationName,
        List<OfficialWarningItem> warnings,
        String sourceAuthority,
        String sourceUrl,
        String retrievedAt,
        List<String> limitations
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OfficialWarningItem(
            String warningId,
            String issuingAuthority, // IMD, NDMA, CPCB
            String title,
            String warningType,      // HEAVY_RAIN, HEAT_WAVE, CYCLONE, AIR_EMERGENCY
            String severity,         // RED, ORANGE, YELLOW, ADVISORY
            String affectedArea,
            String issuedAt,
            String validFrom,
            String validUntil,
            String officialReferenceUrl
    ) {}

    public static OfficialWarningToolResult unavailable(String locationName, String reason) {
        return new OfficialWarningToolResult(
                "SOURCE_UNAVAILABLE",
                locationName,
                List.of(),
                "India Meteorological Department (IMD) / NDMA SACHET",
                "https://mausam.imd.gov.in",
                null,
                List.of(reason != null ? reason : "Official government warning feeds are unconfigured or currently unreachable.")
        );
    }

    public static OfficialWarningToolResult noWarnings(String locationName, String sourceUrl, String retrievedAt) {
        return new OfficialWarningToolResult(
                "NO_ACTIVE_WARNINGS",
                locationName,
                List.of(),
                "India Meteorological Department (IMD) / NDMA SACHET",
                sourceUrl != null ? sourceUrl : "https://mausam.imd.gov.in",
                retrievedAt,
                List.of("No official weather or disaster warnings active for this region at this time.")
        );
    }
}
