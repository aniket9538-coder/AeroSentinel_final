package com.aerosentinel.sensor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonitoringStationRepository extends JpaRepository<MonitoringStation, UUID> {

    Optional<MonitoringStation> findByStationCode(String stationCode);

    List<MonitoringStation> findByCityIdAndStatus(UUID cityId, String status);

    List<MonitoringStation> findByCityId(UUID cityId);

    List<MonitoringStation> findByStatus(String status);

    default List<MonitoringStation> findByCityIdAndIsActiveTrue(UUID cityId) {
        return findByCityIdAndStatus(cityId, "ACTIVE");
    }

    default List<MonitoringStation> findByIsActiveTrue() {
        return findByStatus("ACTIVE");
    }

    /**
     * Finds the nearest active monitoring station to the given point (lon, lat)
     * and computes geodesic distance in meters.
     */
    @Query(value = "SELECT s.id as station_id, s.station_code as station_code, s.name as station_name, " +
            "s.latitude as latitude, s.longitude as longitude, " +
            "ST_Distance(s.location::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography) as distance_meters " +
            "FROM monitoring_stations s " +
            "WHERE s.is_active = true " +
            "ORDER BY s.location::geography <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography " +
            "LIMIT 1", nativeQuery = true)
    NearestStationProjection findNearestActiveStation(
            @Param("lon") double lon,
            @Param("lat") double lat
    );

    /**
     * Counts active monitoring stations within a given radius in meters from the point.
     */
    @Query(value = "SELECT COUNT(s.id) FROM monitoring_stations s " +
            "WHERE s.is_active = true AND ST_DWithin(s.location::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radiusMeters)", nativeQuery = true)
    long countStationsWithinRadius(
            @Param("lon") double lon,
            @Param("lat") double lat,
            @Param("radiusMeters") double radiusMeters
    );

    /**
     * Retrieves the latest observation timestamp recorded by a station.
     */
    @Query(value = "SELECT MAX(o.observed_at) FROM air_quality_observations o WHERE o.station_id = :stationId", nativeQuery = true)
    Instant findLatestObservationTimeByStationId(@Param("stationId") UUID stationId);

    interface NearestStationProjection {
        UUID getStationId();
        String getStationCode();
        String getStationName();
        Double getLatitude();
        Double getLongitude();
        Double getDistanceMeters();
    }
}