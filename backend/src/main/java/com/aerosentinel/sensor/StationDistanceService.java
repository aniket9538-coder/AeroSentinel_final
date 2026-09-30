package com.aerosentinel.sensor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class StationDistanceService {

    private static final Logger log = LoggerFactory.getLogger(StationDistanceService.class);
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final MonitoringStationRepository stationRepository;

    public StationDistanceService(MonitoringStationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    public record NearestStationResult(
            UUID stationId,
            String stationCode,
            String stationName,
            Double latitude,
            Double longitude,
            Double distanceKm,
            long nearbyStationCount,
            Long observationAgeMinutes
    ) {}

    @Transactional(readOnly = true)
    public NearestStationResult findNearestStation(double latitude, double longitude) {
        var projection = stationRepository.findNearestActiveStation(longitude, latitude);

        if (projection == null) {
            log.warn("No active monitoring station found near lat={}, lon={}", latitude, longitude);
            return new NearestStationResult(null, null, "None Available", null, null, Double.MAX_VALUE, 0, null);
        }

        double distanceKm = projection.getDistanceMeters() != null
                ? projection.getDistanceMeters() / 1000.0
                : Double.MAX_VALUE;

        long countNearby = stationRepository.countStationsWithinRadius(longitude, latitude, 10000.0);

        Long ageMinutes = resolveObservationAge(projection.getStationId());

        return new NearestStationResult(
                projection.getStationId(),
                projection.getStationCode(),
                projection.getStationName(),
                projection.getLatitude(),
                projection.getLongitude(),
                Math.round(distanceKm * 100.0) / 100.0,
                countNearby,
                ageMinutes
        );
    }

    /**
     * Solves N+1 query problem by resolving nearest station in-memory against pre-loaded active stations for the city.
     */
    public NearestStationResult findNearestStationFromList(double latitude, double longitude, List<MonitoringStation> stations, Map<UUID, Long> observationAges) {
        if (stations == null || stations.isEmpty()) {
            return new NearestStationResult(null, null, "None Available", null, null, Double.MAX_VALUE, 0, null);
        }

        MonitoringStation nearest = null;
        double minDistance = Double.MAX_VALUE;
        long nearbyCount = 0;

        for (MonitoringStation s : stations) {
            if (s.getLatitude() == null || s.getLongitude() == null) continue;
            double dist = haversineDistance(latitude, longitude, s.getLatitude(), s.getLongitude());
            if (dist < minDistance) {
                minDistance = dist;
                nearest = s;
            }
            if (dist <= 10.0) { // within 10 km
                nearbyCount++;
            }
        }

        if (nearest == null) {
            return new NearestStationResult(null, null, "None Available", null, null, Double.MAX_VALUE, 0, null);
        }

        Long age = nearest.getId() != null ? observationAges.get(nearest.getId()) : null;

        return new NearestStationResult(
                nearest.getId(),
                nearest.getStationCode(),
                nearest.getName(),
                nearest.getLatitude(),
                nearest.getLongitude(),
                Math.round(minDistance * 100.0) / 100.0,
                nearbyCount,
                age
        );
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> loadObservationAges(List<MonitoringStation> stations) {
        Map<UUID, Long> map = new HashMap<>();
        Instant now = Instant.now();
        for (MonitoringStation s : stations) {
            try {
                Instant latest = stationRepository.findLatestObservationTimeByStationId(s.getId());
                if (latest != null) {
                    map.put(s.getId(), Duration.between(latest, now).toMinutes());
                }
            } catch (Exception ignored) {}
        }
        return map;
    }

    private Long resolveObservationAge(UUID stationId) {
        if (stationId == null) return null;
        try {
            Instant latestObs = stationRepository.findLatestObservationTimeByStationId(stationId);
            if (latestObs != null) {
                return Duration.between(latestObs, Instant.now()).toMinutes();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}