package com.aerosentinel.dto.monitoring;

import java.time.Instant;
import java.util.UUID;

public record MonitoringPriorityResponse(
        String h3CellId,
        UUID cityId,
        Double latitude,
        Double longitude,
        Double riskScore,
        Double uncertainty,
        Double forecastConfidence,
        String nearestStationCode,
        String nearestStationName,
        Double nearestStationDistanceKm,
        Long nearbyStationCount,
        Long lastStationObservationAgeMinutes,
        String monitoringPriority,
        String recommendation,
        String recommendationReason,
        String activeEventId,
        Instant evaluatedAt
) {}