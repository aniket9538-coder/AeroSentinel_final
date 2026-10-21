package com.aerosentinel.citizen.preference;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CitizenAlertSubscriptionRepository extends JpaRepository<CitizenAlertSubscription, UUID> {
    List<CitizenAlertSubscription> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<CitizenAlertSubscription> findByIdAndUserId(UUID id, String userId);
    List<CitizenAlertSubscription> findByEnabledTrue();
    List<CitizenAlertSubscription> findByEnabledTrueAndCityNameIgnoreCase(String cityName);
}
