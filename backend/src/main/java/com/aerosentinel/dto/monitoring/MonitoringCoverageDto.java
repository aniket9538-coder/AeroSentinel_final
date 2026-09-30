package com.aerosentinel.dto.monitoring;

import java.util.UUID;

public record MonitoringCoverageDto(
        String h3CellId,
        UUID cityId,
        Double latitude,
        Double longitude,
        Double riskScore,
        Double uncertainty,
        String nearestStationId,
        String nearestStationCode,
        String nearestStationName,
        Double nearestStationDistanceKm,
        Long nearbyStationCount
) {}