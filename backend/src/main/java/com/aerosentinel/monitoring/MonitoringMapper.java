package com.aerosentinel.monitoring;

import com.aerosentinel.dto.monitoring.MonitoringPriorityResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class MonitoringMapper {

    public MonitoringPriorityResponse toResponse(
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
            Long lastObservationAgeMinutes,
            MonitoringPriority priority,
            RecommendationType recommendationType,
            String recommendationReason,
            String activeEventId
    ) {
        return new MonitoringPriorityResponse(
                h3CellId,
                cityId,
                latitude,
                longitude,
                riskScore,
                uncertainty,
                forecastConfidence,
                nearestStationCode,
                nearestStationName,
                nearestStationDistanceKm,
                nearbyStationCount,
                lastObservationAgeMinutes,
                priority != null ? priority.name() : MonitoringPriority.LOW.name(),
                recommendationType != null ? recommendationType.name() : RecommendationType.NONE.name(),
                recommendationReason,
                activeEventId,
                Instant.now()
        );
    }
}