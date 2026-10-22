package com.aerosentinel.evidence;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Normalized evidence representation preserving source contracts and provenance.
 *
 * Implements F5 requirement:
 * 1. Distinguishes measurement time, submission time, and forecast valid time.
 * 2. Identifies source independence (e.g., photo analysis is marked isIndependent=false).
 * 3. Preserves physical units and spatial precision.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NormalizedEvidenceItem(
        String evidenceId,
        EvidenceClass evidenceClass,
        String category,
        String provider,
        String sourceIdentifier,
        Instant observedAt,
        Instant submittedAt,
        Instant validAt,
        String h3Index,
        String spatialPrecision,
        String freshnessStatus,
        String validityStatus,
        Double measurementValue,
        String measurementUnit,
        String originId,
        boolean isIndependent,
        String relatedSourceId,
        Double confidence,
        String summary
) {
    /**
     * Factory for independent physical telemetry.
     */
    public static NormalizedEvidenceItem physicalMeasurement(
            String id,
            String category,
            String provider,
            String stationId,
            Instant observedAt,
            String h3Index,
            String freshness,
            Double value,
            String unit,
            String summary
    ) {
        return new NormalizedEvidenceItem(
                id,
                EvidenceClass.ENVIRONMENTAL_MEASUREMENT,
                category,
                provider,
                stationId,
                observedAt,
                null,
                null,
                h3Index,
                "EXACT_STATION",
                freshness,
                "VALID",
                value,
                unit,
                stationId,
                true, // independent source
                null,
                1.0,
                summary
        );
    }

    /**
     * Factory for citizen-submitted evidence.
     */
    public static NormalizedEvidenceItem citizenSubmitted(
            String id,
            String category,
            String reportReference,
            Instant observedAt,
            Instant submittedAt,
            String h3Index,
            String originId,
            String summary
    ) {
        return new NormalizedEvidenceItem(
                id,
                EvidenceClass.CITIZEN_SUBMITTED,
                category,
                "CITIZEN",
                reportReference,
                observedAt,
                submittedAt,
                null,
                h3Index,
                "H3_RESOLUTION_8",
                "FRESH",
                "PROVISIONAL",
                null,
                null,
                originId,
                true, // primary citizen source
                null,
                0.75,
                summary
        );
    }

    /**
     * Factory for derivative AI vision assessment.
     * Crucial: isIndependent is strictly FALSE and relatedSourceId links to parent citizen report.
     */
    public static NormalizedEvidenceItem citizenVisionAssessment(
            String id,
            String category,
            String modelVersion,
            Instant analyzedAt,
            String h3Index,
            String parentReportId,
            Double confidence,
            String summary
    ) {
        return new NormalizedEvidenceItem(
                id,
                EvidenceClass.CITIZEN_VISION,
                category,
                "GEMINI_VISION",
                modelVersion,
                analyzedAt,
                null,
                null,
                h3Index,
                "H3_RESOLUTION_8",
                "DERIVATIVE",
                "MODEL_GENERATED",
                null,
                null,
                parentReportId,
                false, // NOT independent from citizen photo
                parentReportId,
                confidence,
                summary
        );
    }

    /**
     * Factory for weather model dispersion context.
     */
    public static NormalizedEvidenceItem weatherModelContext(
            String id,
            String metric,
            Instant validAt,
            String h3Index,
            Double value,
            String unit,
            String summary
    ) {
        return new NormalizedEvidenceItem(
                id,
                EvidenceClass.WEATHER_MODEL_CONTEXT,
                metric,
                "OPEN_METEO_NWP",
                "NWP_MODEL_ANALYSIS",
                validAt,
                null,
                validAt,
                h3Index,
                "GRID_INTERPOLATED",
                "LIVE",
                "CONTEXTUAL_ONLY",
                value,
                unit,
                null,
                true,
                null,
                0.85,
                summary
        );
    }

    /**
     * Factory for official government warning bulletin.
     */
    public static NormalizedEvidenceItem officialWarning(
            String id,
            String severity,
            String sourceAuthority,
            Instant issuedAt,
            Instant validUntil,
            String h3Index,
            String warningText
    ) {
        return new NormalizedEvidenceItem(
                id,
                EvidenceClass.OFFICIAL_WARNING,
                "STATUTORY_BULLETIN",
                sourceAuthority,
                id,
                issuedAt,
                null,
                validUntil,
                h3Index,
                "MUNICIPAL_BOUNDARY",
                "OFFICIAL",
                "STATUTORY",
                null,
                null,
                null,
                true,
                null,
                1.0,
                warningText
        );
    }

    /**
     * Factory for experimental ML output.
     */
    public static NormalizedEvidenceItem experimentalMl(
            String id,
            String modelType,
            String modelVersion,
            Instant predictedAt,
            String h3Index,
            Double riskScore,
            Double confidence,
            String summary
    ) {
        return new NormalizedEvidenceItem(
                id,
                EvidenceClass.EXPERIMENTAL_ML_OUTPUT,
                modelType,
                "AEROSENTINEL_ML",
                modelVersion,
                predictedAt,
                null,
                null,
                h3Index,
                "H3_RESOLUTION_8",
                "MODEL_OUTPUT",
                "EXPERIMENTAL",
                riskScore,
                "PROBABILITY",
                id,
                true,
                null,
                confidence,
                summary
        );
    }
}
