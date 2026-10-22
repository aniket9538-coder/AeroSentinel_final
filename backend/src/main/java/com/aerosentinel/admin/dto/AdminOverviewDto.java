package com.aerosentinel.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Top-level Admin Observability Overview Contract (Phase 9).
 * Provides aggregated system status, vital database record counts, F8 gap context,
 * and prioritized diagnostic recommendations for administrative investigation.
 */
public record AdminOverviewDto(
        String systemHealth,
        Instant generatedAt,
        Map<String, String> serviceStatusSummary,
        RecordCounts recordCounts,
        MonitoringGapContext monitoringGapContext,
        List<DiagnosticRecommendation> diagnosticRecommendations
) {
    public record RecordCounts(
            long totalAirObservations,
            long totalWeatherObservations,
            long activeStationsCount,
            long activeAlertsCount,
            long citizenReportsCount,
            long totalCitiesCount,
            long totalAuthorityActions
    ) {}

    public record MonitoringGapContext(
            double coverageRadiusKm,
            double gapThresholdKm,
            long totalActiveStations,
            String policyVersion,
            String notice
    ) {}

    public record DiagnosticRecommendation(
            String id,
            String severity, // INFO, WARNING, CRITICAL
            String component,
            String title,
            String details,
            String recommendedAction
    ) {}
}
