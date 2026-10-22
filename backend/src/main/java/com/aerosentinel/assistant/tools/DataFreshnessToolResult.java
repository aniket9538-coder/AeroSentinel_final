package com.aerosentinel.assistant.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataFreshnessToolResult(
        String sourceName,
        String locationName,
        String lastObservedAt,
        String retrievedAt,
        Long ageMinutes,
        String freshnessStatus, // LIVE, STALE, NO_DATA, SOURCE_UNAVAILABLE
        String explanation,
        List<String> limitations
) {}
