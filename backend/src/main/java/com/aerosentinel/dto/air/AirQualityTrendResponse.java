package com.aerosentinel.dto.air;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AirQualityTrendResponse(
        UUID cityId,
        String cityName,
        String stationCode,
        String stationName,
        List<TrendPoint> observations,
        TrendSummary summary,
        String honestMissingDataNote
) {
    public record TrendPoint(
            Instant observedAt,
            Double pm25,
            Double pm10,
            Double aqi,
            String source,
            String dataQuality
    ) {}

    public record TrendSummary(
            Double minPm25,
            Double maxPm25,
            Double avgPm25,
            Double latestPm25,
            int observationCount,
            String trendDirection
    ) {}
}
