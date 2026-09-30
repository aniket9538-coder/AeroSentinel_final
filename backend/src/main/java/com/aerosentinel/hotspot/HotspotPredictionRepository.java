package com.aerosentinel.hotspot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HotspotPredictionRepository extends JpaRepository<HotspotPrediction, UUID> {

    Optional<HotspotPrediction> findTopByH3IndexOrderByPredictedAtDesc(String h3Index);

    List<HotspotPrediction> findByCityIdOrderByPredictedAtDesc(UUID cityId);

    @Query("SELECT p FROM HotspotPrediction p WHERE p.h3Index IN :h3Indices " +
           "AND p.predictedAt = (SELECT MAX(p2.predictedAt) FROM HotspotPrediction p2 WHERE p2.h3Index = p.h3Index)")
    List<HotspotPrediction> findLatestByH3IndexIn(@Param("h3Indices") Collection<String> h3Indices);
}