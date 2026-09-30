package com.aerosentinel.citizen;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository("citizenGeminiAnalysisRepository")
public interface GeminiAnalysisRepository extends JpaRepository<GeminiAnalysis, UUID> {

    Optional<GeminiAnalysis> findTopByCitizenReportIdOrderByAnalyzedAtDesc(UUID citizenReportId);
}