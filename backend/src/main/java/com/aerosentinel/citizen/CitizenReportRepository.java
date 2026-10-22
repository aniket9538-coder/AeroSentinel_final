package com.aerosentinel.citizen;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CitizenReportRepository extends JpaRepository<CitizenReport, UUID> {
    List<CitizenReport> findByCityIdOrderBySubmittedAtDesc(UUID cityId);
    List<CitizenReport> findByUserIdOrderBySubmittedAtDesc(UUID userId);
    List<CitizenReport> findByStatus(String status);
    List<CitizenReport> findByH3IndexOrderBySubmittedAtDesc(String h3Index);
}
