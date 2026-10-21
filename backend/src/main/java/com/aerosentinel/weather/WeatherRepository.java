package com.aerosentinel.weather;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WeatherRepository extends JpaRepository<WeatherObservation, UUID> {

    List<WeatherObservation> findByCityIdOrderByObservedAtDesc(UUID cityId);

    Optional<WeatherObservation> findFirstByCityIdOrderByObservedAtDesc(UUID cityId);

    List<WeatherObservation> findByH3Index(String h3Index);

    List<WeatherObservation> findByH3IndexOrderByObservedAtDesc(String h3Index);

    List<WeatherObservation> findByH3IndexOrderByObservedAtAsc(String h3Index);

    Optional<WeatherObservation> findFirstByH3IndexOrderByObservedAtDesc(String h3Index);

    boolean existsByCityIdAndObservedAt(UUID cityId, Instant observedAt);

    boolean existsByH3IndexAndObservedAt(String h3Index, Instant observedAt);

    long countByCityId(UUID cityId);

    long countBySource(String source);

    long countByH3Index(String h3Index);
    Optional<WeatherObservation> findFirstByOrderByObservedAtDesc();
    List<WeatherObservation> findTop50ByCityIdOrderByObservedAtDesc(UUID cityId);
}
