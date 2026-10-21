package com.aerosentinel.admin.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe operational audit log event for Admin Observability (Phase 9).
 * All sensitive citizen PII, confidential internal notes, and credentials are strictly redacted.
 */
public record SafeAuditEventDto(
        UUID actionId,
        UUID alertId,
        String actionType,
        String previousStatus,
        String newStatus,
        String actorUsername,
        String actorRole,
        String safeReason,
        Instant performedAt,
        boolean redacted
) {}
