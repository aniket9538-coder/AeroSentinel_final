package com.aerosentinel.action;

import com.aerosentinel.alert.Alert;
import com.aerosentinel.alert.AlertRepository;
import com.aerosentinel.alert.AlertStatus;
import com.aerosentinel.dto.action.ActionResponseDto;
import com.aerosentinel.dto.action.RecordActionRequest;
import com.aerosentinel.event.PollutionEvent;
import com.aerosentinel.event.PollutionEventRepository;
import com.aerosentinel.event.PollutionEventStatus;
import com.aerosentinel.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Authoritative Service for Municipal Authority Actions (Feature 7 Phase 6).
 *
 * Implements strict transaction boundaries and canonical event state synchronization:
 *   IN_INSPECTION -> ACTION_TAKEN
 */
@Service
public class ActionService {

    private static final Logger log = LoggerFactory.getLogger(ActionService.class);

    private final ActionRepository actionRepository;
    private final AlertRepository alertRepository;
    private final PollutionEventRepository pollutionEventRepository;

    public ActionService(
            ActionRepository actionRepository,
            AlertRepository alertRepository,
            PollutionEventRepository pollutionEventRepository
    ) {
        this.actionRepository = actionRepository;
        this.alertRepository = alertRepository;
        this.pollutionEventRepository = pollutionEventRepository;
    }

    /**
     * Records an authoritative action against an alert and synchronizes the parent event lifecycle.
     */
    @Transactional
    public ActionResponseDto recordAction(RecordActionRequest request, String defaultOfficer) {
        if (request == null) {
            throw new IllegalArgumentException("RecordActionRequest must not be null");
        }
        if (request.alertId() == null) {
            throw new IllegalArgumentException("Alert ID must not be null");
        }
        if (request.actionType() == null || request.actionType().isBlank()) {
            throw new IllegalArgumentException("Action type must not be blank");
        }
        if (!AuthorityActionType.isValid(request.actionType())) {
            throw new IllegalArgumentException("Invalid action type: " + request.actionType());
        }
        if (request.notes() == null || request.notes().isBlank()) {
            throw new IllegalArgumentException("Action notes/details must not be blank");
        }

        Alert alert = alertRepository.findById(request.alertId())
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + request.alertId()));

        String alertPrevStatus = alert.getStatus() != null ? alert.getStatus().trim().toUpperCase() : "OPEN";
        AlertStatus.validateTransition(alertPrevStatus, "ACTION_TAKEN");
        alert.setStatus("ACTION_TAKEN");
        alert.setUpdatedAt(Instant.now());
        alertRepository.save(alert);
        log.info("Synchronized Alert id={} status to ACTION_TAKEN after action", alert.getId());

        PollutionEvent event = null;
        if (alert.getEventId() != null) {
            event = pollutionEventRepository.findById(alert.getEventId()).orElse(null);
        }

        if (event != null) {
            String currentStatus = event.getStatus() != null ? event.getStatus().trim().toUpperCase() : "OPEN";

            if ("OPEN".equals(currentStatus)) {
                throw new IllegalStateException("Cannot record action directly on OPEN event. Field inspection must be conducted first.");
            }
            if ("ASSIGNED".equals(currentStatus)) {
                throw new IllegalStateException("Cannot record action on ASSIGNED event before inspection has started.");
            }
            if ("RESOLVED".equals(currentStatus)) {
                throw new IllegalStateException("Cannot record action on an already RESOLVED event.");
            }
            if ("DISMISSED".equals(currentStatus)) {
                throw new IllegalStateException("Cannot record action on a DISMISSED event.");
            }

            // Canonical transition: IN_INSPECTION -> ACTION_TAKEN (or additional action in ACTION_TAKEN)
            PollutionEventStatus.validateTransition(currentStatus, "ACTION_TAKEN");
            event.setStatus("ACTION_TAKEN");
            pollutionEventRepository.save(event);
            log.info("Synchronized PollutionEvent id={} status to ACTION_TAKEN after action", event.getId());
        }

        String officer = (request.performedBy() != null && !request.performedBy().isBlank())
                ? request.performedBy().trim()
                : (defaultOfficer != null && !defaultOfficer.isBlank() ? defaultOfficer : "Municipal Authority");

        AuthorityAction action = new AuthorityAction(
                alert.getId(),
                request.actionType().trim().toUpperCase(),
                request.notes().trim(),
                officer,
                alertPrevStatus,
                "ACTION_TAKEN",
                request.notes().trim()
        );

        action = actionRepository.save(action);
        log.info("Recorded AuthorityAction id={} type={} for alert id={}", action.getId(), action.getActionType(), alert.getId());

        String eventStatus = event != null ? event.getStatus() : null;
        String alertStatus = alert.getStatus();

        return new ActionResponseDto(
                action.getId(),
                action.getAlertId(),
                event != null ? event.getId() : null,
                action.getActionType(),
                action.getActionDetails(),
                action.getPerformedBy(),
                action.getPerformedAt(),
                eventStatus,
                alertStatus,
                action.getPreviousStatus(),
                action.getNewStatus(),
                action.getReason()
        );
    }

    /**
     * Lists all actions recorded for a given alert in descending chronological order.
     */
    @Transactional(readOnly = true)
    public List<ActionResponseDto> getActionsForAlert(UUID alertId) {
        if (alertId == null) {
            throw new IllegalArgumentException("Alert ID must not be null");
        }

        Alert alert = alertRepository.findById(alertId).orElse(null);
        PollutionEvent event = (alert != null && alert.getEventId() != null)
                ? pollutionEventRepository.findById(alert.getEventId()).orElse(null)
                : null;

        String eventStatus = event != null ? event.getStatus() : null;
        String alertStatus = alert != null ? alert.getStatus() : null;

        return actionRepository.findByAlertIdOrderByPerformedAtDesc(alertId).stream()
                .map(a -> new ActionResponseDto(
                        a.getId(),
                        a.getAlertId(),
                        event != null ? event.getId() : null,
                        a.getActionType(),
                        a.getActionDetails(),
                        a.getPerformedBy(),
                        a.getPerformedAt(),
                        eventStatus,
                        alertStatus,
                        a.getPreviousStatus(),
                        a.getNewStatus(),
                        a.getReason()
                ))
                .collect(Collectors.toList());
    }
}
