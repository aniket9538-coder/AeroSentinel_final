package com.aerosentinel.citizen.preference;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "citizen_in_app_notifications")
public class CitizenInAppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "pollutant", nullable = false, length = 50)
    private String pollutant;

    @Column(name = "observed_value", nullable = false)
    private Double observedValue;

    @Column(name = "threshold_value", nullable = false)
    private Double thresholdValue;

    @Column(name = "triggering_source", nullable = false, length = 100)
    private String triggeringSource;

    @Column(name = "read_status", nullable = false)
    private Boolean readStatus = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public CitizenInAppNotification() {
    }

    public CitizenInAppNotification(String userId, UUID subscriptionId, String title, String message, String pollutant, Double observedValue, Double thresholdValue, String triggeringSource) {
        this.userId = userId;
        this.subscriptionId = subscriptionId;
        this.title = title;
        this.message = message;
        this.pollutant = pollutant;
        this.observedValue = observedValue;
        this.thresholdValue = thresholdValue;
        this.triggeringSource = triggeringSource;
        this.readStatus = false;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(UUID subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPollutant() {
        return pollutant;
    }

    public void setPollutant(String pollutant) {
        this.pollutant = pollutant;
    }

    public Double getObservedValue() {
        return observedValue;
    }

    public void setObservedValue(Double observedValue) {
        this.observedValue = observedValue;
    }

    public Double getThresholdValue() {
        return thresholdValue;
    }

    public void setThresholdValue(Double thresholdValue) {
        this.thresholdValue = thresholdValue;
    }

    public String getTriggeringSource() {
        return triggeringSource;
    }

    public void setTriggeringSource(String triggeringSource) {
        this.triggeringSource = triggeringSource;
    }

    public Boolean getReadStatus() {
        return readStatus;
    }

    public void setReadStatus(Boolean readStatus) {
        this.readStatus = readStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
