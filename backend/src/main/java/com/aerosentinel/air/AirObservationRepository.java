package com.aerosentinel.air;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AirObservationRepository extends JpaRepository<AirObservation, UUID> {
    List<AirObservation> findByCityIdOrderByObservedAtDesc(UUID cityId);
    List<AirObservation> findByStationIdOrderByObservedAtDesc(String stationId);
    Optional<AirObservation> findFirstByStationIdOrderByObservedAtDesc(String stationId);
    List<AirObservation> findByStationIdAndObservedAtBetweenOrderByObservedAtAsc(String stationId, Instant from, Instant to);
    boolean existsByStationIdAndObservedAt(String stationId, Instant observedAt);
    long countByH3IndexIsNull();
    List<AirObservation> findByH3IndexIsNull();
    List<AirObservation> findByH3IndexOrderByObservedAtDesc(String h3Index);
    Optional<AirObservation> findFirstByH3IndexOrderByObservedAtDesc(String h3Index);
    List<AirObservation> findByH3IndexOrderByObservedAtAsc(String h3Index);
    List<AirObservation> findByCityIdAndObservedAt(UUID cityId, Instant observedAt);
    Optional<AirObservation> findFirstByOrderByObservedAtDesc();
    long countBySource(String source);
    List<AirObservation> findTop100ByCityIdOrderByObservedAtDesc(UUID cityId);
    List<AirObservation> findTop100ByStationIdOrderByObservedAtDesc(String stationId);
    List<AirObservation> findByCityIdAndObservedAtBetweenOrderByObservedAtAsc(UUID cityId, Instant from, Instant to);
}
