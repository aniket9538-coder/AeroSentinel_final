package com.aerosentinel.monitoring;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain representation of monitoring coverage for a specific H3 spatial cell.
 * Evaluates observation adequacy based on model risk, uncertainty, and physical station distance.
 */
public class MonitoringCoverage {

    private final String h3CellId;
    private final UUID cityId;
    private final double latitude;
    private final double longitude;
    private final double riskScore;
    private final double uncertainty;
    private final Double forecastConfidence;
    private final String nearestStationCode;
    private final String nearestStationName;
    private final double nearestStationDistanceKm;
    private final long nearbyStationCount;
    private final Long lastStationObservationAgeMinutes;
    private final MonitoringPriority priority;
    private final RecommendationType recommendationType;
    private final String recommendationReason;
    private final String activeEventId;
    private final Instant evaluatedAt;

    public MonitoringCoverage(
            String h3CellId,
            UUID cityId,
            double latitude,
            double longitude,
            double riskScore,
            double uncertainty,
            Double forecastConfidence,
            String nearestStationCode,
            String nearestStationName,
            double nearestStationDistanceKm,
            long nearbyStationCount,
            Long lastStationObservationAgeMinutes,
            MonitoringPriority priority,
            RecommendationType recommendationType,
            String recommendationReason,
            String activeEventId,
            Instant evaluatedAt
    ) {
        this.h3CellId = h3CellId;
        this.cityId = cityId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.riskScore = riskScore;
        this.uncertainty = uncertainty;
        this.forecastConfidence = forecastConfidence;
        this.nearestStationCode = nearestStationCode;
        this.nearestStationName = nearestStationName;
        this.nearestStationDistanceKm = nearestStationDistanceKm;
        this.nearbyStationCount = nearbyStationCount;
        this.lastStationObservationAgeMinutes = lastStationObservationAgeMinutes;
        this.priority = priority;
        this.recommendationType = recommendationType;
        this.recommendationReason = recommendationReason;
        this.activeEventId = activeEventId;
        this.evaluatedAt = evaluatedAt != null ? evaluatedAt : Instant.now();
    }

    public String getH3CellId() { return h3CellId; }
    public UUID getCityId() { return cityId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getRiskScore() { return riskScore; }
    public double getUncertainty() { return uncertainty; }
    public Double getForecastConfidence() { return forecastConfidence; }
    public String getNearestStationCode() { return nearestStationCode; }
    public String getNearestStationName() { return nearestStationName; }
    public double getNearestStationDistanceKm() { return nearestStationDistanceKm; }
    public long getNearbyStationCount() { return nearbyStationCount; }
    public Long getLastStationObservationAgeMinutes() { return lastStationObservationAgeMinutes; }
    public MonitoringPriority getPriority() { return priority; }
    public RecommendationType getRecommendationType() { return recommendationType; }
    public String getRecommendationReason() { return recommendationReason; }
    public String getActiveEventId() { return activeEventId; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
}