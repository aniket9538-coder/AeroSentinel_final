package com.aerosentinel.action;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthorityActionRepository extends JpaRepository<AuthorityAction, UUID> {

    default Optional<AuthorityAction> findByActionId(String actionId) {
        try {
            return findById(UUID.fromString(actionId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Query("SELECT a FROM AuthorityAction a WHERE a.alertId = :alertId ORDER BY a.performedAt DESC")
    List<AuthorityAction> findByAlertIdOrderByPerformedAtDesc(@Param("alertId") UUID alertId);

    List<AuthorityAction> findTop50ByOrderByPerformedAtDesc();

    default List<AuthorityAction> findByEventIdOrderByPerformedAtDesc(String eventId) {
        try {
            return findByAlertIdOrderByPerformedAtDesc(UUID.fromString(eventId));
        } catch (Exception e) {
            return List.of();
        }
    }
}