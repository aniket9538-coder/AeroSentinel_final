package com.aerosentinel.citizen;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity(name = "CitizenGeminiAnalysis")
@Table(name = "gemini_analyses")
public class GeminiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "citizen_report_id", nullable = false)
    private UUID citizenReportId;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "detected_category", length = 50)
    private String detectedCategory;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "narrative_summary", columnDefinition = "TEXT")
    private String narrativeSummary;

    @Column(name = "verification_required")
    private Boolean verificationRequired = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", nullable = false, length = 30)
    private GeminiAnalysisStatus analysisStatus = GeminiAnalysisStatus.COMPLETED;

    @Column(name = "prompt_version", length = 20)
    private String promptVersion = "v1.0";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb")
    private String rawResponse;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt = Instant.now();

    public GeminiAnalysis() {
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCitizenReportId() {
        return citizenReportId;
    }

    public void setCitizenReportId(UUID citizenReportId) {
        this.citizenReportId = citizenReportId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getDetectedCategory() {
        return detectedCategory;
    }

    public void setDetectedCategory(String detectedCategory) {
        this.detectedCategory = detectedCategory;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getNarrativeSummary() {
        return narrativeSummary;
    }

    public void setNarrativeSummary(String narrativeSummary) {
        this.narrativeSummary = narrativeSummary;
    }

    public Boolean getVerificationRequired() {
        return verificationRequired;
    }

    public void setVerificationRequired(Boolean verificationRequired) {
        this.verificationRequired = verificationRequired;
    }

    public GeminiAnalysisStatus getAnalysisStatus() {
        return analysisStatus;
    }

    public void setAnalysisStatus(GeminiAnalysisStatus analysisStatus) {
        this.analysisStatus = analysisStatus;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(Instant analyzedAt) {
        this.analyzedAt = analyzedAt;
    }
}