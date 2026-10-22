package com.aerosentinel.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceReferenceDto(
        String provider,
        String stationName,
        String stationCode,
        String url,
        String timestamp,
        String freshness,
        String dataType
) {}
