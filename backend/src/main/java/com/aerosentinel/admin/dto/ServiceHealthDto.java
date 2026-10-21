package com.aerosentinel.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Detailed service health and dependency liveness/readiness contract for Admin Observability (Phase 9).
 */
public record ServiceHealthDto(
        String overallStatus,
        Instant evaluatedAt,
        long uptimeSeconds,
        BackendDetails backend,
        DatabaseDetails database,
        AiServiceDetails aiService
) {
    public record BackendDetails(
            String status,
            String serviceName,
            String javaVersion,
            long freeMemoryMb,
            long totalMemoryMb,
            long maxMemoryMb,
            int activeThreads
    ) {}

    public record DatabaseDetails(
            String status,
            String databaseProduct,
            boolean connectionValid,
            int activeConnections,
            int idleConnections,
            int totalConnections,
            int threadsAwaitingConnection,
            Map<String, Long> tableRowCounts
    ) {}

    public record AiServiceDetails(
            String status,
            String endpoint,
            long latencyMs,
            boolean modelsReady,
            List<String> loadedArtifacts,
            String errorMessage
    ) {}
}
