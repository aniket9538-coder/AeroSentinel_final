package com.aerosentinel.citizen.preference;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "citizen_alert_subscriptions")
public class CitizenAlertSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private CitizenSavedLocation savedLocation;

    @Column(name = "city_name", nullable = false, length = 100)
    private String cityName;

    @Column(name = "h3_index", length = 15)
    private String h3Index;

    @Column(name = "pollutant", nullable = false, length = 50)
    private String pollutant = "PM25";

    @Column(name = "threshold_value", nullable = false)
    private Double thresholdValue;

    @Column(name = "delivery_channel", nullable = false, length = 50)
    private String deliveryChannel = "IN_APP";

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public CitizenAlertSubscription() {
    }

    public CitizenAlertSubscription(String userId, CitizenSavedLocation savedLocation, String cityName, String h3Index, String pollutant, Double thresholdValue, String deliveryChannel) {
        this.userId = userId;
        this.savedLocation = savedLocation;
        this.cityName = cityName;
        this.h3Index = h3Index;
        this.pollutant = pollutant != null ? pollutant : "PM25";
        this.thresholdValue = thresholdValue;
        this.deliveryChannel = deliveryChannel != null ? deliveryChannel : "IN_APP";
        this.enabled = true;
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

    public CitizenSavedLocation getSavedLocation() {
        return savedLocation;
    }

    public void setSavedLocation(CitizenSavedLocation savedLocation) {
        this.savedLocation = savedLocation;
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public String getH3Index() {
        return h3Index;
    }

    public void setH3Index(String h3Index) {
        this.h3Index = h3Index;
    }

    public String getPollutant() {
        return pollutant;
    }

    public void setPollutant(String pollutant) {
        this.pollutant = pollutant;
    }

    public Double getThresholdValue() {
        return thresholdValue;
    }

    public void setThresholdValue(Double thresholdValue) {
        this.thresholdValue = thresholdValue;
    }

    public String getDeliveryChannel() {
        return deliveryChannel;
    }

    public void setDeliveryChannel(String deliveryChannel) {
        this.deliveryChannel = deliveryChannel;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getLastTriggeredAt() {
        return lastTriggeredAt;
    }

    public void setLastTriggeredAt(Instant lastTriggeredAt) {
        this.lastTriggeredAt = lastTriggeredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
