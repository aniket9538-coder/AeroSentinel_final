package com.aerosentinel.dto.action;

import java.time.Instant;
import java.util.UUID;

public record ActionResponseDto(
        UUID id,
        UUID alertId,
        UUID eventId,
        String actionType,
        String actionDetails,
        String performedBy,
        Instant performedAt,
        String eventStatus,
        String alertStatus,
        String previousStatus,
        String newStatus,
        String reason
) {
    public ActionResponseDto(
            UUID id,
            UUID alertId,
            UUID eventId,
            String actionType,
            String actionDetails,
            String performedBy,
            Instant performedAt,
            String eventStatus,
            String alertStatus
    ) {
        this(id, alertId, eventId, actionType, actionDetails, performedBy, performedAt, eventStatus, alertStatus, null, null, null);
    }
}
