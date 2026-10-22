package com.aerosentinel.evidence;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Structured, transparent, explainable triage candidate for authority & analyst review.
 *
 * Implements F5 requirements:
 * 1. Independent source count vs related source count.
 * 2. Explicit separation between evidenceScore (evidence strength) and riskScore (F3 ML probability).
 * 3. Transparent listing of contributing, related, and excluded evidence with reason codes.
 * 4. Clear provisional flag when lacking physical sensor corroboration.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TriageCandidateDto(
        String candidateId,
        String h3Index,
        UUID cityId,
        String cityName,
        String category,
        String triageStatus,
        String severity,
        Double evidenceScore,
        Double riskScore,
        Double visualConfidence,
        int independentSourceCount,
        int relatedSourceCount,
        boolean isProvisional,
        List<NormalizedEvidenceItem> contributingEvidence,
        List<ExcludedEvidenceDto> excludedEvidence,
        List<String> explanations,
        Map<String, String> sourceIndependenceBreakdown,
        String policyVersion,
        Instant createdAt
) {
    public record ExcludedEvidenceDto(
            String evidenceId,
            String source,
            String reasonCode,
            String description
    ) {}
}
