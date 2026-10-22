package com.aerosentinel.admin.dto;

import java.time.Instant;
import java.util.List;

/**
 * AI Model and capability availability contract for Admin Observability (Phase 9).
 * Distinguishes loaded model artifacts, configured cloud APIs, and actual inference readiness.
 */
public record ModelObservabilityDto(
        Instant evaluatedAt,
        boolean allModelsReady,
        int readyModelsCount,
        int totalModelsCount,
        List<ModelItem> models
) {
    public record ModelItem(
            String modelId,
            String modelName,
            String framework,
            String status,
            boolean ready,
            String artifactIdentifier,
            String operationalDetails,
            Instant lastVerifiedAt
    ) {}
}
