package com.aerosentinel.citizen;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Clean, safe client-facing DTO representing a citizen report and its attached Gemini Vision analysis.
 * Never exposes raw internal database secrets or unrestricted filesystem paths.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CitizenReportResponseDto(
        UUID id,
        String reportId,
        UUID cityId,
        Double latitude,
        Double longitude,
        String h3Index,
        String category,
        String description,
        String imageUrl,
        List<String> photoUrls,
        Instant submittedAt,
        String status,
        String verificationStatus,
        Instant createdAt,
        VisionAnalysisSummaryDto visionAnalysis
) {
    public record VisionAnalysisSummaryDto(
            UUID analysisId,
            String analysisStatus,
            String detectedCategory,
            Double confidence,
            List<String> observations,
            List<String> uncertainty,
            String modelVersion,
            String promptVersion,
            Instant analyzedAt
    ) {}

    public static CitizenReportResponseDto fromEntity(CitizenReport report, VisionAnalysisSummaryDto vision) {
        String repId = report.getId() != null ? "CR-" + report.getId().toString().substring(0, 8).toUpperCase() : null;
        List<String> urls = java.util.Collections.emptyList();
        String primaryImageUrl = report.getImageUrl();
        if (primaryImageUrl != null && !primaryImageUrl.isBlank()) {
            urls = java.util.Arrays.stream(primaryImageUrl.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            if (!urls.isEmpty()) {
                primaryImageUrl = urls.get(0);
            }
        }
        return new CitizenReportResponseDto(
                report.getId(),
                repId,
                report.getCityId(),
                report.getLatitude(),
                report.getLongitude(),
                report.getH3Index(),
                report.getCategory(),
                report.getDescription(),
                primaryImageUrl,
                urls,
                report.getSubmittedAt(),
                report.getStatus(),
                report.getVerificationStatus(),
                report.getCreatedAt(),
                vision
        );
    }

    public static CitizenReportResponseDto publicReceipt(CitizenReport report, String analysisStatus) {
        String repId = report.getId() != null ? "CR-" + report.getId().toString().substring(0, 8).toUpperCase() : null;
        VisionAnalysisSummaryDto vision = null;
        if (analysisStatus != null && !analysisStatus.isBlank()) {
            vision = new VisionAnalysisSummaryDto(
                    null,
                    analysisStatus,
                    report.getCategory(),
                    null,
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    null,
                    null,
                    report.getCreatedAt()
            );
        }
        return new CitizenReportResponseDto(
                null, // id masked: internal primary key UUID not exposed to public
                repId,
                null, // cityId masked: internal city UUID not exposed to public
                null, // latitude masked: protects precise citizen location
                null, // longitude masked: protects precise citizen location
                report.getH3Index(), // coarse spatial cell (Resolution 8 ~460m hex) preserved
                report.getCategory(),
                report.getDescription() != null && !report.getDescription().isBlank()
                        ? "[Citizen ground observation registered for auxiliary spatial evaluation]"
                        : null, // private qualitative narrative masked for public lookup
                null, // imageUrl masked: no private storage key or photo URL leakage
                java.util.Collections.emptyList(), // photoUrls masked: no private storage key leakage
                report.getSubmittedAt(),
                report.getStatus(),
                report.getVerificationStatus(),
                report.getCreatedAt(),
                vision
        );
    }
}
