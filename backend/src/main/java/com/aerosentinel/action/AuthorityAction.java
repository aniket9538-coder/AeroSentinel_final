package com.aerosentinel.action;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity mapping to database table authority_actions (V1 / Feature 7 Phase 6).
 */
@Entity
@Table(name = "authority_actions")
public class AuthorityAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "alert_id")
    private UUID alertId;

    @Column(name = "action_type", nullable = false, length = 100)
    private String actionType;

    @Column(name = "action_details", nullable = false, columnDefinition = "TEXT")
    private String actionDetails;

    @Column(name = "performed_by", nullable = false, length = 150)
    private String performedBy;

    @Column(name = "performed_at")
    private Instant performedAt = Instant.now();

    @Column(name = "previous_status", length = 50)
    private String previousStatus;

    @Column(name = "new_status", length = 50)
    private String newStatus;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    public AuthorityAction() {}

    public AuthorityAction(UUID alertId, String actionType, String actionDetails, String performedBy) {
        this(alertId, actionType, actionDetails, performedBy, null, null, null);
    }

    public AuthorityAction(UUID alertId, String actionType, String actionDetails, String performedBy,
                           String previousStatus, String newStatus, String reason) {
        this.alertId = alertId;
        this.actionType = actionType;
        this.actionDetails = actionDetails;
        this.performedBy = performedBy;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.performedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public void setAlertId(UUID alertId) {
        this.alertId = alertId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getActionDetails() {
        return actionDetails;
    }

    public void setActionDetails(String actionDetails) {
        this.actionDetails = actionDetails;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }

    public Instant getPerformedAt() {
        return performedAt;
    }

    public void setPerformedAt(Instant performedAt) {
        this.performedAt = performedAt;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(String previousStatus) {
        this.previousStatus = previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getActionId() { return id != null ? id.toString() : null; }
    public void setActionId(String actionId) {}
    public String getEventId() { return alertId != null ? alertId.toString() : null; }
    public void setEventId(String eventId) {
        try { this.alertId = UUID.fromString(eventId); } catch (Exception ignored) {}
    }
    public String getNotes() { return actionDetails; }
    public void setNotes(String notes) { this.actionDetails = notes; }
    public String getResult() { return "COMPLETED"; }
    public void setResult(String result) {}
}
