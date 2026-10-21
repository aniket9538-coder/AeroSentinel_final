package com.aerosentinel.citizen.preference;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CitizenSavedLocationRepository extends JpaRepository<CitizenSavedLocation, UUID> {
    List<CitizenSavedLocation> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<CitizenSavedLocation> findByIdAndUserId(UUID id, String userId);
    Optional<CitizenSavedLocation> findByUserIdAndIsDefaultTrue(String userId);
    List<CitizenSavedLocation> findByUserIdAndCityNameIgnoreCase(String userId, String cityName);
}
