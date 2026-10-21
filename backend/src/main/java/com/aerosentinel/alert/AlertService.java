package com.aerosentinel.alert;

import com.aerosentinel.city.City;
import com.aerosentinel.city.CityRepository;
import com.aerosentinel.dto.alert.AuthorityQueueItemDto;
import com.aerosentinel.dto.alert.CitizenEvidenceItemDto;
import com.aerosentinel.citizen.CitizenReport;
import com.aerosentinel.citizen.CitizenReportRepository;
import com.aerosentinel.evidence.EventEvidence;
import com.aerosentinel.evidence.EvidenceRepository;
import com.aerosentinel.event.PollutionEvent;
import com.aerosentinel.evidence.EvidenceAiClient.EvidenceAiOutputDto;
import com.aerosentinel.exception.ResourceNotFoundException;
import com.aerosentinel.exception.ValidationException;
import com.aerosentinel.forecast.ForecastResponse;
import com.aerosentinel.forecast.ForecastResponse.ForecastItem;
import com.aerosentinel.hotspot.HotspotSpatialContext;
import com.aerosentinel.model.GeminiAnalysis;
import com.aerosentinel.repository.GeminiAnalysisRepository;
import com.aerosentinel.inspection.FieldVerification;
import com.aerosentinel.inspection.FieldVerificationRepository;
import com.aerosentinel.inspection.Inspection;
import com.aerosentinel.inspection.InspectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Authoritative Alert Service for Feature 5 (F5-P5) and Feature 7 (F7-P2).
 * Enforces:
 * 1. ONLY events with F5 triageState == 'ALERT_CANDIDATE' create actionable alerts.
 * 2. Idempotent alert creation: multiple orchestrations for the same event do not duplicate alerts.
 * 3. Race-safe concurrency handling via database uniqueness constraint.
 * 4. Strict lifecycle state machine: OPEN -> ACKNOWLEDGED -> RESOLVED (invalid transitions rejected).
 * 5. Full audit lineage linking Alert -> PollutionEvent -> H3 -> HotspotPrediction -> GeminiAnalysis -> Citizen Evidence.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alertRepository;
    private final CityRepository cityRepository;
    private final GeminiAnalysisRepository geminiAnalysisRepository;
    private final InspectionRepository inspectionRepository;
    private final FieldVerificationRepository fieldVerificationRepository;
    private final EvidenceRepository evidenceRepository;
    private final CitizenReportRepository citizenReportRepository;
    private final com.aerosentinel.event.PollutionEventRepository pollutionEventRepository;
    private final com.aerosentinel.action.ActionRepository actionRepository;

    public AlertService(
            AlertRepository alertRepository,
            CityRepository cityRepository,
            GeminiAnalysisRepository geminiAnalysisRepository
    ) {
        this(alertRepository, cityRepository, geminiAnalysisRepository, null, null, null, null, null, null);
    }

    public AlertService(
            AlertRepository alertRepository,
            CityRepository cityRepository,
            GeminiAnalysisRepository geminiAnalysisRepository,
            InspectionRepository inspectionRepository,
            FieldVerificationRepository fieldVerificationRepository
    ) {
        this(alertRepository, cityRepository, geminiAnalysisRepository, inspectionRepository, fieldVerificationRepository, null, null, null, null);
    }

    public AlertService(
            AlertRepository alertRepository,
            CityRepository cityRepository,
            GeminiAnalysisRepository geminiAnalysisRepository,
            InspectionRepository inspectionRepository,
            FieldVerificationRepository fieldVerificationRepository,
            EvidenceRepository evidenceRepository,
            CitizenReportRepository citizenReportRepository
    ) {
        this(alertRepository, cityRepository, geminiAnalysisRepository, inspectionRepository, fieldVerificationRepository, evidenceRepository, citizenReportRepository, null, null);
    }

    @Autowired
    public AlertService(
            AlertRepository alertRepository,
            CityRepository cityRepository,
            GeminiAnalysisRepository geminiAnalysisRepository,
            @Autowired(required = false) InspectionRepository inspectionRepository,
            @Autowired(required = false) FieldVerificationRepository fieldVerificationRepository,
            @Autowired(required = false) EvidenceRepository evidenceRepository,
            @Autowired(required = false) CitizenReportRepository citizenReportRepository,
            @Autowired(required = false) com.aerosentinel.event.PollutionEventRepository pollutionEventRepository,
            @Autowired(required = false) com.aerosentinel.action.ActionRepository actionRepository
    ) {
        this.alertRepository = alertRepository;
        this.cityRepository = cityRepository;
        this.geminiAnalysisRepository = geminiAnalysisRepository;
        this.inspectionRepository = inspectionRepository;
        this.fieldVerificationRepository = fieldVerificationRepository;
        this.evidenceRepository = evidenceRepository;
        this.citizenReportRepository = citizenReportRepository;
        this.pollutionEventRepository = pollutionEventRepository;
        this.actionRepository = actionRepository;
    }

    /**
     * Evaluates F5 evidence scoring output and creates an Alert candidate IF AND ONLY IF
     * the triage state is ALERT_CANDIDATE.
     *
     * @param event authoritative PollutionEvent
     * @param spatialContext F3 spatial hotspot context
     * @param forecast optional F4 forecast trajectory
     * @param aiResult F5 evidence AI bridge output
     * @return Optional containing created or existing Alert candidate, or empty if triage is not ALERT_CANDIDATE
     */
    @Transactional
    public Optional<Alert> createOrUpdateAlertCandidate(
            PollutionEvent event,
            HotspotSpatialContext spatialContext,
            ForecastResponse forecast,
            EvidenceAiOutputDto aiResult
    ) {
        if (event == null || spatialContext == null || aiResult == null) {
            log.warn("Cannot evaluate alert candidate: required event, context, or AI result is null");
            return Optional.empty();
        }

        String triage = aiResult.triageState() != null ? aiResult.triageState().trim() : "";

        // STRICT REQUIREMENT (Section 3): Only ALERT_CANDIDATE may enter the authority queue workflow
        if (!"ALERT_CANDIDATE".equalsIgnoreCase(triage)) {
            log.info("F5 Triage state is '{}' for event id={} code={}; no authority alert candidate created",
                    triage, event.getId(), event.getEventCode());
            return Optional.empty();
        }

        // Idempotency check: reuse existing active alert if already created for this event
        if (event.getId() != null) {
            Optional<Alert> existingOpt = alertRepository.findByEventId(event.getId());
            if (existingOpt.isPresent()) {
                Alert existing = existingOpt.get();
                log.info("Reusing existing Alert candidate id={} for event id={}", existing.getId(), event.getId());
                return Optional.of(existing);
            }
        }

        // Build new Alert candidate
        Alert alert = new Alert();
        alert.setEventId(event.getId());
        alert.setEventCode(event.getEventCode());
        alert.setH3Index(event.getH3Index() != null ? event.getH3Index() : spatialContext.h3Index());
        alert.setPredictionId(spatialContext.predictionId());
        alert.setCityId(spatialContext.cityId());
        alert.setGridCellId(event.getGridCellId());
        alert.setSeverity(spatialContext.riskLevel() != null ? spatialContext.riskLevel() : "HIGH");
        alert.setRiskScore(spatialContext.riskScore());
        alert.setEvidenceScore(aiResult.evidenceScore() != null ? aiResult.evidenceScore() : 0.0);
        alert.setTriageState("ALERT_CANDIDATE");
        alert.setConsistency(aiResult.consistency());
        alert.setStatus("OPEN");
        alert.setGeneratedBy("F5_EVIDENCE_ENGINE");

        // Title and description from AI interpretation or fallback
        String title = "Elevated Pollution Risk: " + (spatialContext.cityName() != null ? spatialContext.cityName() : "Sector")
                + " (" + alert.getH3Index().substring(0, Math.min(10, alert.getH3Index().length())) + "...)";
        if (aiResult.aiInterpretation() != null && aiResult.aiInterpretation().detectedCondition() != null) {
            title = aiResult.aiInterpretation().detectedCondition();
        }
        alert.setTitle(title);

        String message = "F3 Hotspot probability " + String.format("%.2f", spatialContext.riskScore())
                + " corroborated by evidence score " + String.format("%.3f", alert.getEvidenceScore()) + ".";
        if (aiResult.aiInterpretation() != null && aiResult.aiInterpretation().summaryPublic() != null) {
            message = aiResult.aiInterpretation().summaryPublic();
        }
        alert.setMessage(message);

        if (aiResult.recommendedVerification() != null) {
            alert.setRecommendedAction(aiResult.recommendedVerification().action());
        }

        // Forecast summary
        if (forecast != null && forecast.forecasts() != null && !forecast.forecasts().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ForecastItem item : forecast.forecasts()) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append("+").append(item.horizonHours()).append("h: ")
                  .append(String.format("%.1f", item.predictedPm25())).append(" ug/m3");
            }
            alert.setForecastSummary(sb.toString());
        } else if (aiResult.aiInterpretation() != null && aiResult.aiInterpretation().forecastTrajectory() != null) {
            alert.setForecastSummary(aiResult.aiInterpretation().forecastTrajectory());
        }

        alert.setCreatedAt(Instant.now());
        alert.setUpdatedAt(Instant.now());

        try {
            Alert saved = alertRepository.save(alert);
            log.info("Created authoritative Alert candidate id={} for event id={} code={}",
                    saved.getId(), event.getId(), event.getEventCode());
            return Optional.of(saved);
        } catch (DataIntegrityViolationException ex) {
            // Concurrency race: Another thread saved alert for same event_id
            log.info("Concurrent insert caught by unique constraint for event id={}; fetching existing", event.getId());
            return alertRepository.findByEventId(event.getId());
        }
    }

    /**
     * Acknowledges an alert candidate, transitioning status from OPEN to ACKNOWLEDGED.
     */
    @Transactional
    public Alert acknowledgeAlert(UUID alertId, UUID acknowledgedBy) {
        return acknowledgeAlert(alertId, acknowledgedBy, null);
    }

    @Transactional
    public Alert acknowledgeAlert(UUID alertId, UUID acknowledgedBy, String actorName) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + alertId));

        if ("RESOLVED".equalsIgnoreCase(alert.getStatus())) {
            throw new ValidationException("Cannot acknowledge an already resolved alert: " + alertId);
        }

        if ("DISMISSED".equalsIgnoreCase(alert.getStatus())) {
            throw new ValidationException("Cannot acknowledge a dismissed alert: " + alertId);
        }

        if ("ACKNOWLEDGED".equalsIgnoreCase(alert.getStatus())) {
            return alert; // Idempotent
        }

        String prevStatus = alert.getStatus() != null ? alert.getStatus().trim().toUpperCase() : "OPEN";
        AlertStatus.validateTransition(prevStatus, "ACKNOWLEDGED");

        alert.setStatus("ACKNOWLEDGED");
        alert.setAcknowledgedAt(Instant.now());
        alert.setAcknowledgedBy(acknowledgedBy);
        alert.setUpdatedAt(Instant.now());

        String actor = (actorName != null && !actorName.isBlank())
                ? actorName
                : (acknowledgedBy != null ? acknowledgedBy.toString() : "Municipal Authority");

        // Record audit trail in authority_actions
        if (actionRepository != null) {
            com.aerosentinel.action.AuthorityAction action = new com.aerosentinel.action.AuthorityAction(
                    alert.getId(),
                    "ACKNOWLEDGEMENT",
                    "Alert candidate acknowledged by municipal authority",
                    actor,
                    prevStatus,
                    "ACKNOWLEDGED",
                    "Triage candidate accepted for operational response"
            );
            actionRepository.save(action);
        }

        log.info("Alert id={} transitioned from {} to ACKNOWLEDGED by {}", alertId, prevStatus, actor);
        return alertRepository.save(alert);
    }

    public Optional<Alert> acknowledgeAlert(UUID alertId) {
        return Optional.of(acknowledgeAlert(alertId, null, null));
    }

    /**
     * Resolves an alert candidate, transitioning status to RESOLVED.
     */
    @Transactional
    public Alert resolveAlert(UUID alertId, UUID resolvedBy) {
        return resolveAlert(alertId, resolvedBy, "Alert and Event resolved by municipal authority", false, null);
    }

    @Transactional
    public Alert resolveAlert(UUID alertId, UUID resolvedBy, String notes, boolean strictValidation) {
        return resolveAlert(alertId, resolvedBy, notes, strictValidation, null);
    }

    @Transactional
    public Alert resolveAlert(UUID alertId, UUID resolvedBy, String notes, boolean strictValidation, String actorName) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + alertId));

        if ("RESOLVED".equalsIgnoreCase(alert.getStatus())) {
            return alert; // Idempotent
        }

        if ("DISMISSED".equalsIgnoreCase(alert.getStatus())) {
            throw new IllegalStateException("Cannot resolve a DISMISSED alert: " + alertId);
        }

        if (strictValidation && (notes == null || notes.isBlank())) {
            throw new com.aerosentinel.exception.ValidationException("Resolution notes are required to resolve alert");
        }

        String curAlertStatus = alert.getStatus() != null ? alert.getStatus().trim().toUpperCase() : "OPEN";
        if (strictValidation) {
            if ("OPEN".equals(curAlertStatus) || "ACKNOWLEDGED".equals(curAlertStatus)) {
                throw new IllegalStateException("Cannot resolve alert directly from " + curAlertStatus + " state. Field inspection and action taken are required first.");
            }
            if ("ASSIGNED".equals(curAlertStatus)) {
                throw new IllegalStateException("Cannot resolve alert while in ASSIGNED state. Inspection and action taken are required first.");
            }
            if ("IN_INSPECTION".equals(curAlertStatus)) {
                throw new IllegalStateException("Cannot resolve alert while in IN_INSPECTION state. Action must be recorded first.");
            }
        }

        // Synchronize parent event status: ACTION_TAKEN -> RESOLVED
        if (pollutionEventRepository != null && alert.getEventId() != null) {
            pollutionEventRepository.findById(alert.getEventId()).ifPresent(event -> {
                String evStatus = event.getStatus() != null ? event.getStatus().trim().toUpperCase() : "OPEN";
                if (strictValidation) {
                    if ("OPEN".equals(evStatus)) {
                        throw new IllegalStateException("Cannot resolve event directly from OPEN state. Field inspection and action taken are required first.");
                    }
                    if ("ASSIGNED".equals(evStatus)) {
                        throw new IllegalStateException("Cannot resolve event while in ASSIGNED state. Inspection and action taken are required first.");
                    }
                    if ("IN_INSPECTION".equals(evStatus)) {
                        throw new IllegalStateException("Cannot resolve event while in IN_INSPECTION state. Action must be recorded first.");
                    }
                    if ("DISMISSED".equals(evStatus)) {
                        throw new IllegalStateException("Cannot resolve a DISMISSED event.");
                    }
                    com.aerosentinel.event.PollutionEventStatus.validateTransition(evStatus, "RESOLVED");
                }
                event.setStatus("RESOLVED");
                event.setResolvedAt(Instant.now());
                pollutionEventRepository.save(event);
                log.info("Synchronized PollutionEvent id={} status to RESOLVED", event.getId());
            });
        }

        alert.setStatus("RESOLVED");
        alert.setResolvedAt(Instant.now());
        alert.setResolvedBy(resolvedBy);
        alert.setUpdatedAt(Instant.now());

        String actor = (actorName != null && !actorName.isBlank())
                ? actorName
                : (resolvedBy != null ? resolvedBy.toString() : "Municipal Authority");

        // Audit record in authority_actions
        if (actionRepository != null) {
            com.aerosentinel.action.AuthorityAction action = new com.aerosentinel.action.AuthorityAction(
                    alert.getId(),
                    "RESOLUTION",
                    (notes != null && !notes.isBlank()) ? notes.trim() : "Alert resolved by municipal authority",
                    actor,
                    curAlertStatus,
                    "RESOLVED",
                    (notes != null && !notes.isBlank()) ? notes.trim() : "Case resolved"
            );
            actionRepository.save(action);
        }

        log.info("Alert id={} transitioned from {} to RESOLVED by {}", alertId, curAlertStatus, actor);
        return alertRepository.save(alert);
    }

    /**
     * Dismisses an alert candidate, transitioning status to DISMISSED and synchronizing the parent event.
     */
    @Transactional
    public Alert dismissAlert(UUID alertId, UUID dismissedBy, String reason) {
        return dismissAlert(alertId, dismissedBy, reason, null);
    }

    @Transactional
    public Alert dismissAlert(UUID alertId, UUID dismissedBy, String reason, String actorName) {
        if (alertId == null) {
            throw new IllegalArgumentException("Alert ID must not be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new com.aerosentinel.exception.ValidationException("Dismissal reason is required");
        }

        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + alertId));

        if ("DISMISSED".equalsIgnoreCase(alert.getStatus())) {
            return alert; // Idempotent
        }
        if ("RESOLVED".equalsIgnoreCase(alert.getStatus())) {
            throw new IllegalStateException("Cannot dismiss an already RESOLVED alert: " + alertId);
        }

        String curAlertStatus = alert.getStatus() != null ? alert.getStatus().trim().toUpperCase() : "OPEN";
        if ("IN_INSPECTION".equals(curAlertStatus) || "ACTION_TAKEN".equals(curAlertStatus)) {
            throw new IllegalStateException("Cannot dismiss alert after inspection has started. Current status: " + curAlertStatus);
        }

        // Synchronize parent event status: OPEN -> DISMISSED
        if (pollutionEventRepository != null && alert.getEventId() != null) {
            pollutionEventRepository.findById(alert.getEventId()).ifPresent(event -> {
                String evStatus = event.getStatus() != null ? event.getStatus().trim().toUpperCase() : "OPEN";
                if ("RESOLVED".equals(evStatus)) {
                    throw new IllegalStateException("Cannot dismiss an already RESOLVED event.");
                }
                if ("IN_INSPECTION".equals(evStatus) || "ACTION_TAKEN".equals(evStatus)) {
                    throw new IllegalStateException("Cannot dismiss event after inspection has started. Current status: " + evStatus);
                }
                com.aerosentinel.event.PollutionEventStatus.validateTransition(evStatus, "DISMISSED");
                event.setStatus("DISMISSED");
                event.setResolvedAt(Instant.now());
                pollutionEventRepository.save(event);
                log.info("Synchronized PollutionEvent id={} status to DISMISSED", event.getId());
            });
        }

        alert.setStatus("DISMISSED");
        alert.setResolvedAt(Instant.now());
        alert.setResolvedBy(dismissedBy);
        alert.setUpdatedAt(Instant.now());

        String actor = (actorName != null && !actorName.isBlank())
                ? actorName
                : (dismissedBy != null ? dismissedBy.toString() : "Municipal Authority");

        // Audit record in authority_actions
        if (actionRepository != null) {
            com.aerosentinel.action.AuthorityAction action = new com.aerosentinel.action.AuthorityAction(
                    alert.getId(),
                    "DISMISSAL",
                    reason.trim(),
                    actor,
                    curAlertStatus,
                    "DISMISSED",
                    reason.trim()
            );
            actionRepository.save(action);
        }

        log.info("Alert id={} transitioned from {} to DISMISSED by {}", alertId, curAlertStatus, actor);
        return alertRepository.save(alert);
    }

    /**
     * Retrieves the authoritative municipal Authority Queue with full lineage and diagnostic metadata.
     */
    @Transactional(readOnly = true)
    public List<AuthorityQueueItemDto> getAuthorityQueue(String status, UUID cityId) {
        List<Alert> alerts;

        if (cityId != null && status != null && !status.equalsIgnoreCase("ALL")) {
            alerts = alertRepository.findByCityIdAndStatusOrderByCreatedAtDesc(cityId, status.toUpperCase());
        } else if (cityId != null) {
            alerts = alertRepository.findByCityIdOrderByCreatedAtDesc(cityId);
        } else if (status != null && !status.equalsIgnoreCase("ALL")) {
            alerts = alertRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
        } else {
            alerts = alertRepository.findAllByOrderByCreatedAtDesc();
        }

        return alerts.stream()
                .map(this::toAuthorityQueueItemDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a single authoritative Alert candidate by UUID.
     */
    @Transactional(readOnly = true)
    public Optional<AuthorityQueueItemDto> getAuthorityAlertById(UUID alertId) {
        return alertRepository.findById(alertId).map(this::toAuthorityQueueItemDto);
    }

    /**
     * Maps an Alert entity to the rich AuthorityQueueItemDto.
     */
    public AuthorityQueueItemDto toAuthorityQueueItemDto(Alert alert) {
        String cityName = null;
        if (alert.getCityId() != null) {
            cityName = cityRepository.findById(alert.getCityId())
                    .map(City::getName)
                    .orElse(null);
        }

        boolean hasGemini = false;
        String geminiSummary = null;
        if (alert.getEventId() != null) {
            List<GeminiAnalysis> analyses = geminiAnalysisRepository.findByEventIdOrderByCreatedAtDesc(alert.getEventId());
            if (!analyses.isEmpty()) {
                hasGemini = true;
                geminiSummary = analyses.get(0).getEventSummaryPublic();
            }
        }

        UUID activeAssignmentId = null;
        UUID assignedTeamId = null;
        String assignedTeamName = null;
        String assignmentStatus = null;
        Instant assignedAt = null;
        String verificationResult = null;
        String verificationStatus = null;
        Instant inspectedAt = null;

        if (inspectionRepository != null && alert.getId() != null) {
            Optional<Inspection> activeOpt = inspectionRepository.findActiveByAlertId(alert.getId());
            Inspection insp = activeOpt.orElseGet(() -> {
                List<Inspection> list = inspectionRepository.findByAlertIdOrderByAssignedAtDesc(alert.getId());
                return list.isEmpty() ? null : list.get(0);
            });
            if (insp != null) {
                activeAssignmentId = insp.getId();
                assignedTeamId = insp.getTeamId();
                assignedTeamName = insp.getAssignedTeam();
                assignmentStatus = insp.getStatus();
                assignedAt = insp.getAssignedAt();

                if (fieldVerificationRepository != null) {
                    List<FieldVerification> verifs = fieldVerificationRepository.findByInspectionId(insp.getId());
                    if (!verifs.isEmpty()) {
                        FieldVerification fv = verifs.get(0);
                        verificationResult = fv.getVerificationResult();
                        verificationStatus = fv.getVerificationStatus();
                        inspectedAt = fv.getInspectedAt();
                    }
                }
            }
        }

        List<CitizenEvidenceItemDto> citizenEvidenceList = new ArrayList<>();
        if (alert.getEventId() != null && evidenceRepository != null) {
            List<EventEvidence> allEv = evidenceRepository.findByEventId(alert.getEventId());
            for (EventEvidence ev : allEv) {
                if ("CITIZEN".equalsIgnoreCase(ev.getDataSource()) && ev.getSourceRef() != null) {
                    try {
                        UUID repId = UUID.fromString(ev.getSourceRef());
                        CitizenReport report = citizenReportRepository != null
                                ? citizenReportRepository.findById(repId).orElse(null)
                                : null;

                        Optional<GeminiAnalysis> analysisOpt = geminiAnalysisRepository.findTopByCitizenReportIdOrderByCreatedAtDesc(repId);
                        GeminiAnalysis ga = analysisOpt.orElse(null);

                        String repRef = "CR-" + repId.toString().substring(0, Math.min(8, repId.toString().length())).toUpperCase();
                        String category = report != null ? report.getCategory() : "UNKNOWN";
                        String description = report != null ? report.getDescription() : ev.getEvidenceValue();
                        Instant obsAt = report != null && report.getSubmittedAt() != null ? report.getSubmittedAt() : ev.getObservedAt();
                        String photoUrl = report != null ? report.getImageUrl() : null;
                        String visibleCond = ga != null ? ga.getDetectedCategory() : null;
                        Double visConf = ga != null && ga.getConfidence() != null ? ga.getConfidence() : ev.getConfidenceScore();
                        List<String> obs = (ga != null && ga.getNarrativeSummary() != null && !ga.getNarrativeSummary().isBlank())
                                ? List.of(ga.getNarrativeSummary())
                                : Collections.emptyList();
                        List<String> uncert = (ga != null && ga.getUncertaintyStatement() != null && !ga.getUncertaintyStatement().isBlank())
                                ? List.of(ga.getUncertaintyStatement())
                                : Collections.emptyList();

                        citizenEvidenceList.add(new CitizenEvidenceItemDto(
                                repId,
                                repRef,
                                alert.getH3Index(),
                                category,
                                description,
                                obsAt,
                                visibleCond,
                                visConf,
                                obs,
                                uncert,
                                photoUrl,
                                "CITIZEN",
                                "AUXILIARY",
                                alert.getEventId(),
                                ev.getEvidenceKey()
                        ));
                    } catch (Exception ex) {
                        log.debug("Could not resolve citizen report for sourceRef {}: {}", ev.getSourceRef(), ex.getMessage());
                    }
                }
            }
        }

        return new AuthorityQueueItemDto(
                alert.getId(),
                alert.getEventId(),
                alert.getEventCode(),
                alert.getH3Index(),
                alert.getPredictionId(),
                alert.getCityId(),
                cityName,
                alert.getStatus(),
                alert.getSeverity(),
                alert.getRiskScore(),
                alert.getEvidenceScore(),
                alert.getTriageState() != null ? alert.getTriageState() : "ALERT_CANDIDATE",
                alert.getConsistency(),
                alert.getTitle(),
                alert.getMessage(),
                alert.getForecastSummary(),
                alert.getRecommendedAction(),
                hasGemini,
                geminiSummary,
                alert.getCreatedAt(),
                alert.getUpdatedAt(),
                alert.getAcknowledgedAt(),
                alert.getResolvedAt(),
                activeAssignmentId,
                assignedTeamId,
                assignedTeamName,
                assignmentStatus,
                assignedAt,
                verificationResult,
                verificationStatus,
                inspectedAt,
                citizenEvidenceList.isEmpty() ? null : citizenEvidenceList
        );
    }

    // =========================================================================
    // Legacy compatibility methods
    // =========================================================================
    public List<Alert> getAlertsByCity(UUID cityId, String status) {
        return alertRepository.findByCityIdAndStatusOrderByCreatedAtDesc(cityId, status);
    }

    public List<Alert> getAlertsByStatus(String status) {
        return alertRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public Alert createAlert(Alert alert) {
        return alertRepository.save(alert);
    }
}
