package com.aerosentinel.admin.dto;

import java.time.Instant;
import java.util.List;

/**
 * Data provider freshness, health, and ingestion metrics for Admin Observability (Phase 9).
 * Contains real counts and timestamps; metrics not tracked are honestly reported as null or untracked.
 */
public record ProviderObservabilityDto(
        Instant evaluatedAt,
        int activeProvidersCount,
        int totalProvidersCount,
        long totalObservationsIngested,
        List<ProviderItem> providers
) {
    public record ProviderItem(
            String providerId,
            String displayName,
            String category,
            boolean configured,
            String endpoint,
            String status,
            Instant lastIngestionAt,
            Instant latestObservationAt,
            long recordCount,
            Long freshnessMinutes,
            String freshnessStatus,
            String safeNotes
    ) {}
}
