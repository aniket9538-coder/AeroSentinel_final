package com.aerosentinel.citizen.preference;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public class SavedLocationDto {

    public record CreateRequest(
            @NotBlank(message = "Label is required (e.g. Home, Workplace)")
            String label,

            @NotBlank(message = "City name is required")
            String cityName,

            @NotNull(message = "Latitude is required")
            Double latitude,

            @NotNull(message = "Longitude is required")
            Double longitude,

            Boolean isDefault
    ) {}

    public record UpdateRequest(
            @NotBlank(message = "Label is required")
            String label,
            Boolean isDefault
    ) {}

    public record Response(
            UUID id,
            String userId,
            String label,
            String cityName,
            Double latitude,
            Double longitude,
            String h3Index,
            Boolean isDefault,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static Response fromEntity(CitizenSavedLocation entity) {
            return new Response(
                    entity.getId(),
                    entity.getUserId(),
                    entity.getLabel(),
                    entity.getCityName(),
                    entity.getLatitude(),
                    entity.getLongitude(),
                    entity.getH3Index(),
                    entity.getIsDefault(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt()
            );
        }
    }
}
