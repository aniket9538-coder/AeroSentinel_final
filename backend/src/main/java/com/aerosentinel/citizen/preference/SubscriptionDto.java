package com.aerosentinel.citizen.preference;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public class SubscriptionDto {

    public record CreateRequest(
            UUID locationId,

            @NotBlank(message = "City name is required")
            String cityName,

            String h3Index,

            @NotBlank(message = "Pollutant is required (e.g. PM25, AQI)")
            String pollutant,

            @NotNull(message = "Threshold value is required")
            Double thresholdValue,

            String deliveryChannel
    ) {}

    public record Response(
            UUID id,
            String userId,
            UUID locationId,
            String locationLabel,
            String cityName,
            String h3Index,
            String pollutant,
            Double thresholdValue,
            String deliveryChannel,
            Boolean enabled,
            Instant lastTriggeredAt,
            Instant createdAt,
            String channelStatusNote
    ) {
        public static Response fromEntity(CitizenAlertSubscription entity) {
            String label = entity.getSavedLocation() != null ? entity.getSavedLocation().getLabel() : null;
            String channelNote = "IN_APP".equalsIgnoreCase(entity.getDeliveryChannel())
                    ? "Active in-app delivery supported"
                    : "Channel unconfigured in local deployment (in-app fallback active)";

            return new Response(
                    entity.getId(),
                    entity.getUserId(),
                    entity.getSavedLocation() != null ? entity.getSavedLocation().getId() : null,
                    label,
                    entity.getCityName(),
                    entity.getH3Index(),
                    entity.getPollutant(),
                    entity.getThresholdValue(),
                    entity.getDeliveryChannel(),
                    entity.getEnabled(),
                    entity.getLastTriggeredAt(),
                    entity.getCreatedAt(),
                    channelNote
            );
        }
    }

    public record NotificationResponse(
            UUID id,
            String userId,
            UUID subscriptionId,
            String title,
            String message,
            String pollutant,
            Double observedValue,
            Double thresholdValue,
            String triggeringSource,
            Boolean readStatus,
            Instant createdAt
    ) {
        public static NotificationResponse fromEntity(CitizenInAppNotification entity) {
            return new NotificationResponse(
                    entity.getId(),
                    entity.getUserId(),
                    entity.getSubscriptionId(),
                    entity.getTitle(),
                    entity.getMessage(),
                    entity.getPollutant(),
                    entity.getObservedValue(),
                    entity.getThresholdValue(),
                    entity.getTriggeringSource(),
                    entity.getReadStatus(),
                    entity.getCreatedAt()
            );
        }
    }

    public record EvaluationResult(
            int subscriptionsEvaluated,
            int notificationsTriggered,
            String evaluatedAt,
            String notice
    ) {}
}
