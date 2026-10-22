package com.aerosentinel.citizen.preference;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CitizenInAppNotificationRepository extends JpaRepository<CitizenInAppNotification, UUID> {
    List<CitizenInAppNotification> findByUserIdOrderByCreatedAtDesc(String userId);
    List<CitizenInAppNotification> findByUserIdAndReadStatusFalseOrderByCreatedAtDesc(String userId);
    Optional<CitizenInAppNotification> findByIdAndUserId(UUID id, String userId);
    long countByUserIdAndReadStatusFalse(String userId);
}
