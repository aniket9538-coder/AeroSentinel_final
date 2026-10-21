package com.aerosentinel.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LocationDto(
        String cityName,
        Double latitude,
        Double longitude,
        String h3Index
) {
    public boolean hasCoordinates() {
        return latitude != null && longitude != null
                && !latitude.isNaN() && !longitude.isNaN()
                && !latitude.isInfinite() && !longitude.isInfinite();
    }

    public boolean hasCityName() {
        return cityName != null && !cityName.trim().isEmpty();
    }

    public boolean hasH3Index() {
        return h3Index != null && !h3Index.trim().isEmpty();
    }
}
