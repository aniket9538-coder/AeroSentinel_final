package com.aerosentinel.inspection;

import com.aerosentinel.alert.Alert;
import com.aerosentinel.alert.AlertRepository;
import com.aerosentinel.dto.inspection.*;
import com.aerosentinel.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Authoritative Service for Feature 5 Phase 6 (F5-P6):
 * Operational Field Team Assignment, Inspection Lifecycle, and Field Verification.
 */
@Service
public class InspectionService {

    private static final Logger log = LoggerFactory.getLogger(InspectionService.class);

    private static final Set<String> VALID_VERIFICATION_RESULTS = Set.of(
            "CONFIRMED", "REJECTED", "NEEDS_FOLLOW_UP"
    );

    private final InspectionRepository inspectionRepository;
    private final FieldTeamRepository fieldTeamRepository;
    private final FieldVerificationRepository fieldVerificationRepository;
    private final AlertRepository alertRepository;
    private final com.aerosentinel.event.PollutionEventRepository pollutionEventRepository;
    private final com.aerosentinel.action.ActionRepository actionRepository;

    public InspectionService(
            InspectionRepository inspectionRepository,
            FieldTeamRepository fieldTeamRepository,
            FieldVerificationRepository fieldVerificationRepository,
            AlertRepository alertRepository
    ) {
        this(inspectionRepository, fieldTeamRepository, fieldVerificationRepository, alertRepository, null, null);
    }

    public InspectionService(
            InspectionRepository inspectionRepository,
            FieldTeamRepository fieldTeamRepository,
            FieldVerificationRepository fieldVerificationRepository,
            AlertRepository alertRepository,
            com.aerosentinel.event.PollutionEventRepository pollutionEventRepository
    ) {
        this(inspectionRepository, fieldTeamRepository, fieldVerificationRepository, alertRepository, pollutionEventRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public InspectionService(
            InspectionRepository inspectionRepository,
            FieldTeamRepository fieldTeamRepository,
            FieldVerificationRepository fieldVerificationRepository,
            AlertRepository alertRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.aerosentinel.event.PollutionEventRepository pollutionEventRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.aerosentinel.action.ActionRepository actionRepository
    ) {
        this.inspectionRepository = inspectionRepository;
        this.fieldTeamRepository = fieldTeamRepository;
        this.fieldVerificationRepository = fieldVerificationRepository;
        this.alertRepository = alertRepository;
        this.pollutionEventRepository = pollutionEventRepository;
        this.actionRepository = actionRepository;
    }

    // =========================================================================
    // FIELD TEAM DIRECTORY
    // =========================================================================

    @Transactional(readOnly = true)
    public List<FieldTeamDto> listFieldTeams(UUID cityId, String status) {
        List<FieldTeam> teams;
        if (cityId != null && status != null && !status.isBlank()) {
            teams = fieldTeamRepository.findByCityIdAndStatus(cityId, status.toUpperCase().trim());
        } else if (cityId != null) {
            teams = fieldTeamRepository.findByCityId(cityId);
        } else if (status != null && !status.isBlank()) {
            teams = fieldTeamRepository.findByStatus(status.toUpperCase().trim());
        } else {
            teams = fieldTeamRepository.findAll();
        }

        return teams.stream()
                .map(this::toFieldTeamDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FieldTeamDto getFieldTeamById(UUID teamId) {
        FieldTeam team = fieldTeamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Field team not found with id: " + teamId));
        return toFieldTeamDto(team);
    }

    // =========================================================================
    // FIELD TEAM ASSIGNMENT
    // =========================================================================

    /**
     * Assigns a field team to an acknowledged alert candidate.
     * Enforces:
     * - Alert exists
     * - Alert is ACKNOWLEDGED (rejects OPEN and RESOLVED)
     * - Field team exists
     * - No existing active assignment for this alert (idempotency & concurrency safe)
     * - Full lineage preservation (Alert -> Event -> H3 -> Prediction)
     */
    @Transactional
    public InspectionResponseDto assignFieldTeam(UUID alertId, AssignTeamRequest request) {
        return assignFieldTeam(alertId, request, null);
    }

    @Transactional
    public InspectionResponseDto assignFieldTeam(UUID alertId, AssignTeamRequest request, String actorName) {
        if (alertId == null) {
            throw new IllegalArgumentException("Alert ID must not be null");
        }
        if (request == null || request.teamId() == null) {
            throw new IllegalArgumentException("Team ID must not be null");
        }

        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + alertId));

        // Idempotency: verify no active assignment already exists for this alert
        Optional<Inspection> activeOpt = inspectionRepository.findActiveByAlertId(alertId);
        if (activeOpt.isPresent()) {
            throw new IllegalStateException("Alert already has an active field assignment: " + activeOpt.get().getId());
        }

        // Lifecycle validation
        String alertStatus = alert.getStatus() != null ? alert.getStatus().toUpperCase() : "OPEN";
        if ("RESOLVED".equals(alertStatus) || "DISMISSED".equals(alertStatus)) {
            throw new IllegalStateException("Cannot assign field team to a " + alertStatus + " alert: " + alertId);
        }
        if (!"ACKNOWLEDGED".equals(alertStatus)) {
            throw new IllegalStateException("Alert must be ACKNOWLEDGED before assigning a field team. Current status: " + alertStatus);
        }

        FieldTeam team = fieldTeamRepository.findById(request.teamId())
                .orElseThrow(() -> new ResourceNotFoundException("Field team not found with id: " + request.teamId()));

        Inspection inspection = new Inspection();
        inspection.setAlertId(alert.getId());
        inspection.setTeamId(team.getId());
        inspection.setAssignedTeam(team.getTeamName());
        inspection.setEventId(alert.getEventId());
        inspection.setH3Index(alert.getH3Index());
        inspection.setPredictionId(alert.getPredictionId());
        inspection.setAssignedBy(request.assignedBy());
        inspection.setAssignedAt(Instant.now());
        inspection.setScheduledAt(request.scheduledAt() != null ? request.scheduledAt() : Instant.now());
        inspection.setNotes(request.notes());
        inspection.setStatus("ASSIGNED");

        try {
            inspection = inspectionRepository.save(inspection);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Database partial unique constraint caught duplicate active assignment for alert: {}", alertId);
            throw new IllegalStateException("Concurrent assignment conflict: Alert already has an active field assignment", ex);
        }

        // Update team status to DISPATCHED
        team.setStatus("DISPATCHED");
        team.setUpdatedAt(Instant.now());
        fieldTeamRepository.save(team);

        // Synchronize alert status to ASSIGNED
        String prevAlertStatus = alert.getStatus();
        alert.setStatus("ASSIGNED");
        alert.setUpdatedAt(Instant.now());
        alertRepository.save(alert);

        // Synchronize parent event status: OPEN -> ASSIGNED
        if (pollutionEventRepository != null && alert.getEventId() != null) {
            pollutionEventRepository.findById(alert.getEventId()).ifPresent(event -> {
                String evStatus = event.getStatus() != null ? event.getStatus().trim().toUpperCase() : "OPEN";
                if ("RESOLVED".equals(evStatus) || "DISMISSED".equals(evStatus)) {
                    throw new IllegalStateException("Cannot assign field team to an event that is " + evStatus);
                }
                com.aerosentinel.event.PollutionEventStatus.validateTransition(evStatus, "ASSIGNED");
                event.setStatus("ASSIGNED");
                pollutionEventRepository.save(event);
                log.info("Synchronized PollutionEvent id={} status to ASSIGNED", event.getId());
            });
        }

        // Audit log in authority_actions
        if (actionRepository != null) {
            String actor = (actorName != null && !actorName.isBlank())
                    ? actorName
                    : (request.assignedBy() != null ? request.assignedBy().toString() : "Municipal Authority");
            com.aerosentinel.action.AuthorityAction action = new com.aerosentinel.action.AuthorityAction(
                    alert.getId(),
                    "ASSIGNMENT",
                    "Field team assigned: " + team.getTeamName() + (request.notes() != null && !request.notes().isBlank() ? " | Notes: " + request.notes().trim() : ""),
                    actor,
                    prevAlertStatus,
                    "ASSIGNED",
                    request.notes()
            );
            actionRepository.save(action);
        }

        log.info("Assigned field team {} ({}) to alert {} [Event: {}, H3: {}]",
                team.getTeamCode(), team.getTeamName(), alertId, alert.getEventId(), alert.getH3Index());

        return toInspectionResponseDto(inspection, team, null);
    }

    /**
     * Reassigns an active alert assignment to a different eligible municipal field team (F7-P8).
     * Enforces mandatory reason, releases old team to AVAILABLE, marks new team DISPATCHED,
     * and records an auditable REASSIGNMENT action in authority_actions.
     */
    @Transactional
    public InspectionResponseDto reassignFieldTeam(UUID alertId, ReassignTeamRequest request, String actorName) {
        if (alertId == null) {
            throw new IllegalArgumentException("Alert ID must not be null");
        }
        if (request == null || request.newTeamId() == null) {
            throw new IllegalArgumentException("New Team ID must not be null");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new com.aerosentinel.exception.ValidationException("Reassignment reason is mandatory");
        }

        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + alertId));

        String alertStatus = alert.getStatus() != null ? alert.getStatus().toUpperCase() : "OPEN";
        if ("RESOLVED".equals(alertStatus) || "DISMISSED".equals(alertStatus)) {
            throw new IllegalStateException("Cannot reassign field team for a " + alertStatus + " alert: " + alertId);
        }

        FieldTeam newTeam = fieldTeamRepository.findById(request.newTeamId())
                .orElseThrow(() -> new ResourceNotFoundException("Field team not found with id: " + request.newTeamId()));

        Inspection activeInspection = inspectionRepository.findActiveByAlertId(alertId)
                .orElseThrow(() -> new IllegalStateException("No active assignment found to reassign for alert: " + alertId));

        String oldTeamName = activeInspection.getAssignedTeam();
        if (activeInspection.getTeamId() != null) {
            fieldTeamRepository.findById(activeInspection.getTeamId()).ifPresent(oldTeam -> {
                oldTeam.setStatus("AVAILABLE");
                oldTeam.setUpdatedAt(Instant.now());
                fieldTeamRepository.save(oldTeam);
            });
        }

        activeInspection.setStatus("REASSIGNED");
        activeInspection.setFindings("Reassigned to " + newTeam.getTeamName() + " | Reason: " + request.reason().trim());
        activeInspection.setUpdatedAt(Instant.now());
        inspectionRepository.saveAndFlush(activeInspection);

        Inspection newInspection = new Inspection();
        newInspection.setAlertId(alert.getId());
        newInspection.setTeamId(newTeam.getId());
        newInspection.setAssignedTeam(newTeam.getTeamName());
        newInspection.setEventId(alert.getEventId());
        newInspection.setH3Index(alert.getH3Index());
        newInspection.setPredictionId(alert.getPredictionId());
        newInspection.setAssignedAt(Instant.now());
        newInspection.setScheduledAt(request.scheduledAt() != null ? request.scheduledAt() : Instant.now());
        newInspection.setNotes(request.notes() != null && !request.notes().isBlank() ? request.notes().trim() : ("Reassigned: " + request.reason().trim()));
        newInspection.setStatus("ASSIGNED");

        newInspection = inspectionRepository.save(newInspection);

        newTeam.setStatus("DISPATCHED");
        newTeam.setUpdatedAt(Instant.now());
        fieldTeamRepository.save(newTeam);

        alert.setStatus("ASSIGNED");
        alert.setUpdatedAt(Instant.now());
        alertRepository.save(alert);

        String actor = (actorName != null && !actorName.isBlank()) ? actorName : "Municipal Authority";
        if (actionRepository != null) {
            com.aerosentinel.action.AuthorityAction action = new com.aerosentinel.action.AuthorityAction(
                    alert.getId(),
                    "REASSIGNMENT",
                    "Reassigned from " + oldTeamName + " to " + newTeam.getTeamName(),
                    actor,
                    "ASSIGNED",
                    "ASSIGNED",
                    request.reason().trim()
            );
            actionRepository.save(action);
        }

        log.info("Reassigned alert {} from {} to {} [Reason: {}]", alertId, oldTeamName, newTeam.getTeamName(), request.reason());
        return toInspectionResponseDto(newInspection, newTeam, null);
    }

    // =========================================================================
    // FIELD INSPECTION WORKFLOW
    // =========================================================================

    /**
     * Transitions an assignment from SCHEDULED/ASSIGNED to IN_PROGRESS.
     */
    @Transactional
    public InspectionResponseDto startInspection(UUID inspectionId) {
        return startInspection(inspectionId, null);
    }

    @Transactional
    public InspectionResponseDto startInspection(UUID inspectionId, String actorName) {
        Inspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Inspection not found with id: " + inspectionId));

        String currentStatus = inspection.getStatus() != null ? inspection.getStatus().toUpperCase() : "";
        if (!"ASSIGNED".equals(currentStatus) && !"SCHEDULED".equals(currentStatus)) {
            throw new IllegalStateException("Cannot start inspection in status: " + currentStatus + ". Must be ASSIGNED or SCHEDULED.");
        }

        inspection.setStatus("IN_PROGRESS");
        inspection.setStartedAt(Instant.now());
        inspection.setUpdatedAt(Instant.now());
        Inspection savedInspection = inspectionRepository.save(inspection);
        if (savedInspection != null) {
            inspection = savedInspection;
        }

        // Synchronize parent alert status: ASSIGNED -> IN_INSPECTION
        if (inspection.getAlertId() != null) {
            alertRepository.findById(inspection.getAlertId()).ifPresent(alert -> {
                alert.setStatus("IN_INSPECTION");
                alert.setUpdatedAt(Instant.now());
                alertRepository.save(alert);
            });
        }

        // Synchronize parent event status: ASSIGNED -> IN_INSPECTION
        if (pollutionEventRepository != null && inspection.getEventId() != null) {
            pollutionEventRepository.findById(inspection.getEventId()).ifPresent(event -> {
                String evStatus = event.getStatus() != null ? event.getStatus().trim().toUpperCase() : "OPEN";
                if ("RESOLVED".equals(evStatus) || "DISMISSED".equals(evStatus)) {
                    throw new IllegalStateException("Cannot start inspection for an event that is " + evStatus);
                }
                com.aerosentinel.event.PollutionEventStatus.validateTransition(evStatus, "IN_INSPECTION");
                event.setStatus("IN_INSPECTION");
                pollutionEventRepository.save(event);
                log.info("Synchronized PollutionEvent id={} status to IN_INSPECTION", event.getId());
            });
        }

        if (actionRepository != null && inspection.getAlertId() != null) {
            String actor = (actorName != null && !actorName.isBlank())
                    ? actorName
                    : (inspection.getAssignedTeam() != null ? inspection.getAssignedTeam() : "Municipal Authority");
            com.aerosentinel.action.AuthorityAction action = new com.aerosentinel.action.AuthorityAction(
                    inspection.getAlertId(),
                    "FIELD_INSPECTION_STARTED",
                    "Field inspection initiated by team " + inspection.getAssignedTeam(),
                    actor,
                    "ASSIGNED",
                    "IN_INSPECTION",
                    inspection.getNotes()
            );
            actionRepository.save(action);
        }

        log.info("Started inspection {} for alert {}", inspectionId, inspection.getAlertId());

        FieldTeam team = inspection.getTeamId() != null
                ? fieldTeamRepository.findById(inspection.getTeamId()).orElse(null)
                : null;

        FieldVerification latestVerification = fieldVerificationRepository
                .findByInspectionId(inspectionId).stream()
                .findFirst().orElse(null);

        return toInspectionResponseDto(
                inspection,
                team,
                latestVerification != null ? toVerificationDto(latestVerification) : null
        );
    }

    /**
     * Submits a field verification report for an active inspection.
     * Enforces:
     * - Inspection must be IN_PROGRESS
     * - Verification result must be CONFIRMED, REJECTED, or NEEDS_FOLLOW_UP
     * - Observed conditions must not be empty (OBSERVED FIELD EVIDENCE)
     * - Preserves complete lineage (Verification -> Inspection -> Alert -> Event -> H3 -> Prediction)
     * - Transitions inspection to COMPLETED
     */
    @Transactional
    public InspectionResponseDto submitVerification(UUID inspectionId, SubmitVerificationRequest request) {
        return submitVerification(inspectionId, request, null);
    }

    @Transactional
    public InspectionResponseDto submitVerification(UUID inspectionId, SubmitVerificationRequest request, String actorName) {
        if (inspectionId == null) {
            throw new IllegalArgumentException("Inspection ID must not be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("SubmitVerificationRequest must not be null");
        }

        Inspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Inspection not found with id: " + inspectionId));

        String currentStatus = inspection.getStatus() != null ? inspection.getStatus().toUpperCase() : "";
        if (!"IN_PROGRESS".equals(currentStatus)) {
            throw new IllegalStateException("Inspection must be IN_PROGRESS to submit verification. Current status: " + currentStatus);
        }

        if (request.verificationResult() == null || request.verificationResult().isBlank()) {
            throw new IllegalArgumentException("Verification result must be provided");
        }
        String result = request.verificationResult().toUpperCase().trim();
        if (!VALID_VERIFICATION_RESULTS.contains(result)) {
            throw new IllegalArgumentException("Invalid verification result: " + request.verificationResult() +
                    ". Must be one of: " + VALID_VERIFICATION_RESULTS);
        }

        if (request.observedConditions() == null || request.observedConditions().isBlank()) {
            throw new IllegalArgumentException("Observed conditions (OBSERVED FIELD EVIDENCE) must not be blank");
        }

        // Create authoritative FieldVerification record
        FieldVerification verification = new FieldVerification();
        verification.setInspectionId(inspection.getId());
        verification.setAlertId(inspection.getAlertId());
        verification.setEventId(inspection.getEventId());
        verification.setH3Index(inspection.getH3Index());
        verification.setPredictionId(inspection.getPredictionId());
        verification.setVerificationStatus(result);
        verification.setVerificationResult(result);
        verification.setObservedConditions(request.observedConditions().trim());
        verification.setInspectorNotes(request.inspectorNotes());
        verification.setEvidenceReferences(request.evidenceReferences());
        verification.setVerifiedBy(request.verifiedBy() != null ? request.verifiedBy() : inspection.getAssignedTeam());
        verification.setInspectedAt(request.inspectedAt() != null ? request.inspectedAt() : Instant.now());
        verification.setCreatedAt(Instant.now());
        verification.setUpdatedAt(Instant.now());

        FieldVerification savedVerification = fieldVerificationRepository.save(verification);
        if (savedVerification != null) {
            verification = savedVerification;
        }

        // Transition inspection to COMPLETED
        inspection.setStatus("COMPLETED");
        inspection.setCompletedAt(Instant.now());
        inspection.setFindings("Result: " + result + " | Observations: " + request.observedConditions().trim());
        inspection.setUpdatedAt(Instant.now());
        Inspection savedInspection = inspectionRepository.save(inspection);
        if (savedInspection != null) {
            inspection = savedInspection;
        }

        // Reset field team availability
        if (inspection.getTeamId() != null) {
            fieldTeamRepository.findById(inspection.getTeamId()).ifPresent(team -> {
                team.setStatus("AVAILABLE");
                team.setUpdatedAt(Instant.now());
                fieldTeamRepository.save(team);
            });
        }

        if (actionRepository != null && inspection.getAlertId() != null) {
            String actor = (actorName != null && !actorName.isBlank())
                    ? actorName
                    : (request.verifiedBy() != null ? request.verifiedBy() : inspection.getAssignedTeam());
            com.aerosentinel.action.AuthorityAction action = new com.aerosentinel.action.AuthorityAction(
                    inspection.getAlertId(),
                    "FIELD_VERIFICATION",
                    "Field verification recorded: " + result + " | Observed: " + request.observedConditions().trim(),
                    actor,
                    "IN_INSPECTION",
                    "IN_INSPECTION",
                    request.inspectorNotes()
            );
            actionRepository.save(action);
        }

        log.info("Completed field verification {} for inspection {} [Result: {}, Alert: {}, H3: {}]",
                verification.getId(), inspectionId, result, inspection.getAlertId(), inspection.getH3Index());


        FieldTeam team = inspection.getTeamId() != null
                ? fieldTeamRepository.findById(inspection.getTeamId()).orElse(null)
                : null;

        return toInspectionResponseDto(inspection, team, toVerificationDto(verification));
    }

    // =========================================================================
    // QUERY ENDPOINTS
    // =========================================================================

    @Transactional(readOnly = true)
    public InspectionResponseDto getInspectionById(UUID inspectionId) {
        Inspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Inspection not found with id: " + inspectionId));

        FieldTeam team = inspection.getTeamId() != null
                ? fieldTeamRepository.findById(inspection.getTeamId()).orElse(null)
                : null;

        FieldVerification latestVerification = fieldVerificationRepository
                .findByInspectionId(inspectionId).stream()
                .findFirst().orElse(null);

        return toInspectionResponseDto(
                inspection,
                team,
                latestVerification != null ? toVerificationDto(latestVerification) : null
        );
    }

    @Transactional(readOnly = true)
    public Optional<InspectionResponseDto> getActiveAssignmentForAlert(UUID alertId) {
        Optional<Inspection> activeOpt = inspectionRepository.findActiveByAlertId(alertId);
        if (activeOpt.isPresent()) {
            Inspection inspection = activeOpt.get();
            FieldTeam team = inspection.getTeamId() != null
                    ? fieldTeamRepository.findById(inspection.getTeamId()).orElse(null)
                    : null;
            FieldVerification latestVerification = fieldVerificationRepository
                    .findByInspectionId(inspection.getId()).stream()
                    .findFirst().orElse(null);
            return Optional.of(toInspectionResponseDto(
                    inspection,
                    team,
                    latestVerification != null ? toVerificationDto(latestVerification) : null
            ));
        }

        // If no active, return latest completed assignment if any
        List<Inspection> all = inspectionRepository.findByAlertIdOrderByAssignedAtDesc(alertId);
        if (!all.isEmpty()) {
            Inspection inspection = all.get(0);
            FieldTeam team = inspection.getTeamId() != null
                    ? fieldTeamRepository.findById(inspection.getTeamId()).orElse(null)
                    : null;
            FieldVerification latestVerification = fieldVerificationRepository
                    .findByInspectionId(inspection.getId()).stream()
                    .findFirst().orElse(null);
            return Optional.of(toInspectionResponseDto(
                    inspection,
                    team,
                    latestVerification != null ? toVerificationDto(latestVerification) : null
            ));
        }

        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public List<FieldVerificationDto> getVerificationsForAlert(UUID alertId) {
        return fieldVerificationRepository.findByAlertIdOrderByInspectedAtDesc(alertId).stream()
                .map(this::toVerificationDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FieldVerificationDto> getVerificationsForInspection(UUID inspectionId) {
        return fieldVerificationRepository.findByInspectionId(inspectionId).stream()
                .map(this::toVerificationDto)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // DTO MAPPERS
    // =========================================================================

    public FieldTeamDto toFieldTeamDto(FieldTeam team) {
        return new FieldTeamDto(
                team.getId(),
                team.getTeamCode(),
                team.getTeamName(),
                team.getCityId(),
                team.getStatus(),
                team.getContactNumber(),
                team.getLeaderName(),
                team.getCreatedAt()
        );
    }

    public FieldVerificationDto toVerificationDto(FieldVerification v) {
        return new FieldVerificationDto(
                v.getId(),
                v.getInspectionId(),
                v.getAlertId(),
                v.getEventId(),
                v.getH3Index(),
                v.getPredictionId(),
                v.getVerificationStatus(),
                v.getVerificationResult(),
                v.getObservedConditions(),
                v.getInspectorNotes(),
                v.getEvidenceReferences(),
                v.getVerifiedBy(),
                v.getInspectedAt(),
                v.getCreatedAt()
        );
    }

    public InspectionResponseDto toInspectionResponseDto(
            Inspection inspection,
            FieldTeam team,
            FieldVerificationDto latestVerification
    ) {
        return new InspectionResponseDto(
                inspection.getId(),
                inspection.getAlertId(),
                inspection.getTeamId(),
                team != null ? team.getTeamCode() : null,
                team != null ? team.getTeamName() : inspection.getAssignedTeam(),
                inspection.getEventId(),
                inspection.getH3Index(),
                inspection.getPredictionId(),
                inspection.getAssignedBy(),
                inspection.getAssignedAt(),
                inspection.getScheduledAt(),
                inspection.getStartedAt(),
                inspection.getCompletedAt(),
                inspection.getFindings(),
                inspection.getNotes(),
                inspection.getStatus(),
                inspection.getCreatedAt(),
                inspection.getUpdatedAt(),
                latestVerification
        );
    }
}
