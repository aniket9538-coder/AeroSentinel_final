package com.aerosentinel.citizen;

import com.aerosentinel.exception.ResourceNotFoundException;
import com.aerosentinel.exception.ValidationException;
import com.aerosentinel.model.GeminiAnalysis;
import com.aerosentinel.repository.GeminiAnalysisRepository;
import com.aerosentinel.util.H3Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;

/**
 * Authoritative Citizen Report Service orchestrating ingestion, spatial resolution 8 indexing,
 * photo storage, Gemini Vision analysis, and persistence.
 */
@Service
public class CitizenReportService {

    private static final Logger log = LoggerFactory.getLogger(CitizenReportService.class);

    private static final Set<String> VALID_CATEGORIES = Set.of(
            "SMOKE",
            "DUST",
            "BURNING",
            "ODOR",
            "ODOUR",
            "GARBAGE_BURNING",
            "INDUSTRIAL",
            "TRAFFIC",
            "OTHER"
    );

    private final CitizenReportRepository citizenReportRepository;
    private final GeminiAnalysisRepository geminiAnalysisRepository;
    private final PhotoStorageService photoStorageService;
    private final CitizenVisionAiClient visionAiClient;
    private final com.aerosentinel.event.PollutionEventRepository pollutionEventRepository;
    private final com.aerosentinel.evidence.EvidenceRepository evidenceRepository;
    private final CitizenEventMatcher citizenEventMatcher;

    @org.springframework.beans.factory.annotation.Autowired
    public CitizenReportService(
            CitizenReportRepository citizenReportRepository,
            GeminiAnalysisRepository geminiAnalysisRepository,
            PhotoStorageService photoStorageService,
            CitizenVisionAiClient visionAiClient,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.aerosentinel.event.PollutionEventRepository pollutionEventRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.aerosentinel.evidence.EvidenceRepository evidenceRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) CitizenEventMatcher citizenEventMatcher
    ) {
        this.citizenReportRepository = citizenReportRepository;
        this.geminiAnalysisRepository = geminiAnalysisRepository;
        this.photoStorageService = photoStorageService;
        this.visionAiClient = visionAiClient;
        this.pollutionEventRepository = pollutionEventRepository;
        this.evidenceRepository = evidenceRepository;
        this.citizenEventMatcher = citizenEventMatcher;
    }

    public CitizenReportService(
            CitizenReportRepository citizenReportRepository,
            GeminiAnalysisRepository geminiAnalysisRepository,
            PhotoStorageService photoStorageService,
            CitizenVisionAiClient visionAiClient
    ) {
        this(citizenReportRepository, geminiAnalysisRepository, photoStorageService, visionAiClient, null, null, null);
    }

    /**
     * Ingests a citizen observation with mandatory spatial coordinates and optional single photo evidence.
     */
    @Transactional
    public CitizenReportResponseDto submitReport(
            UUID cityId,
            Double latitude,
            Double longitude,
            String category,
            String description,
            MultipartFile photo,
            Instant observedAt
    ) {
        List<MultipartFile> photos = (photo != null && !photo.isEmpty()) ? List.of(photo) : Collections.emptyList();
        return submitReport(cityId, latitude, longitude, category, description, photos, observedAt, null);
    }

    /**
     * Ingests a citizen observation with mandatory spatial coordinates, optional multi-photo evidence,
     * authenticated user attribution, and transactional cleanup.
     */
    @Transactional
    public CitizenReportResponseDto submitReport(
            UUID cityId,
            Double latitude,
            Double longitude,
            String category,
            String description,
            List<MultipartFile> photos,
            Instant observedAt,
            UUID userId
    ) {
        // 1. Validate mandatory location
        if (latitude == null || longitude == null) {
            throw new ValidationException("Geographic coordinates (latitude and longitude) are mandatory");
        }
        H3Utils.validateCoordinates(latitude, longitude);

        if (cityId == null) {
            throw new ValidationException("Target municipal cityId is mandatory");
        }

        // 2. Validate category
        String normalizedCategory = normalizeCategory(category);

        // 3. Validate description
        if (description != null && description.trim().length() > 1000) {
            throw new ValidationException("Description exceeds maximum permitted length of 1000 characters");
        }
        String cleanDescription = description != null ? description.trim() : "";

        // 4. Validate observedAt (cannot be in the far future)
        Instant finalObservedAt = observedAt != null ? observedAt : Instant.now();
        if (finalObservedAt.isAfter(Instant.now().plusSeconds(300))) {
            throw new ValidationException("Incident time cannot be in the future");
        }

        // 5. Compute Authoritative Uber H3 Resolution 8
        String h3Index = H3Utils.coordinatesToH3(latitude, longitude, H3Utils.NEIGHBORHOOD_RESOLUTION);

        // 6. Store Photos if present with rollback cleanup capability
        List<PhotoStorageService.StoredPhoto> storedPhotos = Collections.emptyList();
        if (photos != null && !photos.isEmpty()) {
            if (photos.size() == 1) {
                PhotoStorageService.StoredPhoto sp = photoStorageService.storePhoto(photos.get(0));
                if (sp != null) {
                    storedPhotos = List.of(sp);
                }
            } else {
                storedPhotos = photoStorageService.storePhotos(photos);
            }
        }

        CitizenReport savedReport;
        try {
            CitizenReport report = new CitizenReport();
            report.setUserId(userId);
            report.setCityId(cityId);
            report.setLatitude(latitude);
            report.setLongitude(longitude);
            report.setH3Index(h3Index);
            report.setCategory(normalizedCategory);
            report.setDescription(cleanDescription);
            if (!storedPhotos.isEmpty()) {
                String joinedUrls = String.join(",", storedPhotos.stream().map(PhotoStorageService.StoredPhoto::accessUrl).toList());
                report.setImageUrl(joinedUrls);
            } else {
                report.setImageUrl(null);
            }
            report.setSubmittedAt(finalObservedAt);
            report.setStatus("PENDING");
            report.setVerificationStatus("UNVERIFIED");
            report.setCreatedAt(Instant.now());

            savedReport = citizenReportRepository.save(report);
            log.info("Persisted CitizenReport id={} for H3 cell {} and city {} (photos: {})",
                    savedReport.getId(), h3Index, cityId, storedPhotos.size());
        } catch (Exception e) {
            // Transactional compensation cleanup: delete stored photos from disk if persistence fails
            for (PhotoStorageService.StoredPhoto sp : storedPhotos) {
                photoStorageService.deletePhoto(sp.storageKey());
            }
            throw e;
        }

        // 7. Invoke Gemini Vision if photo exists (run on primary photo)
        CitizenReportResponseDto.VisionAnalysisSummaryDto visionSummary = null;
        GeminiAnalysis savedAnalysis = null;
        if (!storedPhotos.isEmpty()) {
            PhotoStorageService.StoredPhoto primaryPhoto = storedPhotos.get(0);
            try {
                CitizenVisionAiClient.CitizenVisionResultDto visionResult = visionAiClient.analyzeImage(
                        primaryPhoto.absolutePath(),
                        savedReport.getId(),
                        h3Index
                );

                if (visionResult != null) {
                    GeminiAnalysis analysis = new GeminiAnalysis();
                    analysis.setCitizenReportId(savedReport.getId());
                    analysis.setH3Index(h3Index);
                    analysis.setDetectedCategory(visionResult.category());
                    analysis.setConfidence(visionResult.confidence());
                    analysis.setModelName(visionResult.modelVersion());
                    analysis.setModelVersion(visionResult.modelVersion());
                    analysis.setPromptVersion(visionResult.promptVersion());
                    analysis.setNarrativeSummary(String.join(". ", visionResult.observations()));
                    analysis.setEventSummaryAnalyst(String.join(". ", visionResult.observations()));
                    analysis.setUncertaintyStatement(String.join(". ", visionResult.uncertainty()));
                    analysis.setIsGrounded(true);
                    analysis.setCreatedAt(Instant.now());

                    savedAnalysis = geminiAnalysisRepository.save(analysis);
                    log.info("Persisted GeminiAnalysis id={} for citizen report {}", savedAnalysis.getId(), savedReport.getId());

                    savedReport.setStatus("ANALYZED");
                    savedReport = citizenReportRepository.save(savedReport);

                    visionSummary = new CitizenReportResponseDto.VisionAnalysisSummaryDto(
                            savedAnalysis.getId(),
                            visionResult.isSuccessful() ? "ANALYZED" : "FALLBACK",
                            visionResult.category(),
                            visionResult.confidence(),
                            visionResult.observations(),
                            visionResult.uncertainty(),
                            visionResult.modelVersion(),
                            visionResult.promptVersion(),
                            savedAnalysis.getCreatedAt()
                    );
                }
            } catch (Exception e) {
                log.warn("Gemini vision analysis failed for report {}: {}. Citizen report is preserved.",
                        savedReport.getId(), e.getMessage());
                visionSummary = new CitizenReportResponseDto.VisionAnalysisSummaryDto(
                        null,
                        "UNAVAILABLE",
                        "UNKNOWN",
                        0.50,
                        List.of("AI visual analysis unavailable — citizen report is still stored"),
                        List.of("Analysis failed: " + e.getMessage()),
                        "deterministic-fallback",
                        "vision_analysis_v001",
                        Instant.now()
                );
            }
        }

        // 8. F6-P3: Attach to existing PollutionEvent if spatially and temporally matching
        attachToEventIfMatching(savedReport, savedAnalysis);

        return CitizenReportResponseDto.fromEntity(savedReport, visionSummary);
    }

    /**
     * Attaches an analyzed citizen report to an existing pollution event if spatially and temporally matched.
     * Enforces the locked invariant: citizen evidence alone never fabricates a new event.
     */
    private void attachToEventIfMatching(CitizenReport report, GeminiAnalysis analysis) {
        if (pollutionEventRepository == null || evidenceRepository == null || citizenEventMatcher == null) {
            return;
        }

        try {
            Optional<com.aerosentinel.event.PollutionEvent> eventOpt =
                    pollutionEventRepository.findTopByH3IndexOrderByStartedAtDesc(report.getH3Index());

            if (eventOpt.isPresent()) {
                com.aerosentinel.event.PollutionEvent event = eventOpt.get();
                if (citizenEventMatcher.isMatch(report, event)) {
                    String evidenceKey = "citizen-report-" + report.getId();
                    if (!evidenceRepository.existsByEventIdAndEvidenceKey(event.getId(), evidenceKey)) {
                        double conf = analysis != null && analysis.getConfidence() != null ? analysis.getConfidence() : 0.75;
                        String detected = analysis != null && analysis.getDetectedCategory() != null
                                ? analysis.getDetectedCategory()
                                : report.getCategory();

                        com.aerosentinel.evidence.EventEvidence ev = new com.aerosentinel.evidence.EventEvidence();
                        ev.setEventId(event.getId());
                        ev.setSourceType("CITIZEN_OBSERVATION");
                        ev.setEvidenceKey(evidenceKey);
                        String desc = report.getDescription() != null && !report.getDescription().isBlank()
                                ? report.getDescription()
                                : "Citizen visual observation";
                        ev.setEvidenceValue("Citizen observation [" + detected + "]: " + desc);
                        ev.setWeight(conf);
                        ev.setConfidenceScore(conf);
                        ev.setSignalId("sig-citizen-" + report.getId().toString().substring(0, Math.min(8, report.getId().toString().length())));
                        ev.setDataSource("CITIZEN");
                        ev.setRelevanceTier("AUXILIARY");
                        ev.setSourceRef(report.getId().toString());
                        ev.setObservedAt(report.getSubmittedAt() != null ? report.getSubmittedAt() : Instant.now());
                        ev.setCreatedAt(Instant.now());

                        evidenceRepository.save(ev);
                        log.info("Attached citizen report id={} to existing PollutionEvent id={} code={} as AUXILIARY evidence",
                                report.getId(), event.getId(), event.getEventCode());
                    } else {
                        log.debug("EventEvidence for citizen report {} already exists on event {}; skipping duplicate",
                                report.getId(), event.getId());
                    }

                    if (analysis != null && analysis.getEventId() == null) {
                        analysis.setEventId(event.getId());
                        geminiAnalysisRepository.save(analysis);
                    }
                } else {
                    log.info("Citizen report id={} in H3 {} does not match event code={} temporally (delta outside window)",
                            report.getId(), report.getH3Index(), event.getEventCode());
                }
            } else {
                log.info("No active PollutionEvent found in H3 {} for citizen report id={}. Report stored unattached (no event fabricated).",
                        report.getH3Index(), report.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to check/attach citizen report {} to event: {}", report.getId(), e.getMessage());
        }
    }

    /**
     * Retrieves an individual citizen report with its latest vision analysis by ID.
     */
    @Transactional(readOnly = true)
    public CitizenReportResponseDto getReportById(UUID reportId) {
        return getReportById(reportId, null, true);
    }

    /**
     * Retrieves a report enforcing IDOR protection: if user is authenticated and not privileged,
     * they cannot access other users' private reports.
     */
    @Transactional(readOnly = true)
    public CitizenReportResponseDto getReportById(UUID reportId, UUID currentUserId, boolean isPrivileged) {
        CitizenReport report = citizenReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Citizen report not found with id: " + reportId));

        if (report.getUserId() != null && !isPrivileged) {
            if (currentUserId == null || !report.getUserId().equals(currentUserId)) {
                throw new org.springframework.security.access.AccessDeniedException("Access denied: You are not authorized to view this citizen report.");
            }
        }

        Optional<GeminiAnalysis> analysisOpt = geminiAnalysisRepository.findTopByCitizenReportIdOrderByCreatedAtDesc(reportId);
        CitizenReportResponseDto.VisionAnalysisSummaryDto visionSummary = analysisOpt.map(a ->
                new CitizenReportResponseDto.VisionAnalysisSummaryDto(
                        a.getId(),
                        "ANALYZED",
                        a.getDetectedCategory() != null ? a.getDetectedCategory() : "UNKNOWN",
                        a.getConfidence() != null ? a.getConfidence() : 0.75,
                        a.getNarrativeSummary() != null ? List.of(a.getNarrativeSummary()) : Collections.emptyList(),
                        a.getUncertaintyStatement() != null ? List.of(a.getUncertaintyStatement()) : Collections.emptyList(),
                        a.getModelVersion(),
                        a.getPromptVersion(),
                        a.getCreatedAt()
                )
        ).orElse(null);

        return CitizenReportResponseDto.fromEntity(report, visionSummary);
    }

    /**
     * Retrieves an individual report by public reference code (e.g. CR-XXXXXXXX) or UUID.
     * Enforces privacy-preserving data minimization for unauthenticated inquiries.
     */
    @Transactional(readOnly = true)
    public CitizenReportResponseDto getReportByReference(String reference) {
        return getReportByReference(reference, null, false);
    }

    /**
     * Retrieves report by reference code, disclosing full coordinates and photo evidence only to
     * authenticated report owners or privileged authorities.
     */
    @Transactional(readOnly = true)
    public CitizenReportResponseDto getReportByReference(String reference, UUID currentUserId, boolean isPrivileged) {
        if (reference == null || reference.isBlank()) {
            throw new ValidationException("Report reference or receipt code is required");
        }
        String clean = reference.trim();
        UUID directId = null;
        try {
            String candidate = clean.toUpperCase().startsWith("CR-") ? clean.substring(3).trim() : clean;
            if (candidate.length() == 36 && candidate.contains("-")) {
                directId = UUID.fromString(candidate);
            }
        } catch (Exception ignored) {}

        CitizenReport matchedReport = null;
        if (directId != null) {
            matchedReport = citizenReportRepository.findById(directId).orElse(null);
        } else {
            String search = clean.toUpperCase().startsWith("CR-") ? clean.substring(3).trim().toLowerCase() : clean.toLowerCase();
            List<CitizenReport> all = citizenReportRepository.findAll();
            for (CitizenReport r : all) {
                String shortId = r.getId().toString().substring(0, Math.min(8, r.getId().toString().length())).toLowerCase();
                if (shortId.equals(search) || r.getId().toString().toLowerCase().equals(search)) {
                    matchedReport = r;
                    break;
                }
            }
        }

        if (matchedReport == null) {
            throw new ResourceNotFoundException("No citizen report found matching reference: " + reference);
        }

        boolean isOwner = matchedReport.getUserId() != null && currentUserId != null && matchedReport.getUserId().equals(currentUserId);
        if (isPrivileged || isOwner) {
            Optional<GeminiAnalysis> analysisOpt = geminiAnalysisRepository.findTopByCitizenReportIdOrderByCreatedAtDesc(matchedReport.getId());
            CitizenReportResponseDto.VisionAnalysisSummaryDto visionSummary = analysisOpt.map(a ->
                    new CitizenReportResponseDto.VisionAnalysisSummaryDto(
                            a.getId(),
                            "ANALYZED",
                            a.getDetectedCategory() != null ? a.getDetectedCategory() : "UNKNOWN",
                            a.getConfidence() != null ? a.getConfidence() : 0.75,
                            a.getNarrativeSummary() != null ? List.of(a.getNarrativeSummary()) : Collections.emptyList(),
                            a.getUncertaintyStatement() != null ? List.of(a.getUncertaintyStatement()) : Collections.emptyList(),
                            a.getModelVersion(),
                            a.getPromptVersion(),
                            a.getCreatedAt()
                    )
            ).orElse(null);
            return CitizenReportResponseDto.fromEntity(matchedReport, visionSummary);
        }

        // Data-Minimized Public Receipt for unauthenticated or non-owner tracking
        return CitizenReportResponseDto.publicReceipt(matchedReport, matchedReport.getStatus());
    }

    /**
     * Finds the citizen report containing the given photo storage key.
     */
    @Transactional(readOnly = true)
    public Optional<CitizenReport> findReportByPhotoKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return Optional.empty();
        }
        return citizenReportRepository.findAll().stream()
                .filter(r -> r.getImageUrl() != null && r.getImageUrl().contains(storageKey))
                .findFirst();
    }

    /**
     * Retrieves all citizen reports submitted by a specific authenticated citizen user.
     */
    @Transactional(readOnly = true)
    public List<CitizenReportResponseDto> getReportsByUser(UUID userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<CitizenReport> reports = citizenReportRepository.findByUserIdOrderBySubmittedAtDesc(userId);
        List<CitizenReportResponseDto> responseList = new ArrayList<>(reports.size());
        for (CitizenReport r : reports) {
            Optional<GeminiAnalysis> analysisOpt = geminiAnalysisRepository.findTopByCitizenReportIdOrderByCreatedAtDesc(r.getId());
            CitizenReportResponseDto.VisionAnalysisSummaryDto visionSummary = analysisOpt.map(a ->
                    new CitizenReportResponseDto.VisionAnalysisSummaryDto(
                            a.getId(),
                            "ANALYZED",
                            a.getDetectedCategory(),
                            a.getConfidence(),
                            a.getNarrativeSummary() != null ? List.of(a.getNarrativeSummary()) : Collections.emptyList(),
                            a.getUncertaintyStatement() != null ? List.of(a.getUncertaintyStatement()) : Collections.emptyList(),
                            a.getModelVersion(),
                            a.getPromptVersion(),
                            a.getCreatedAt()
                    )
            ).orElse(null);
            responseList.add(CitizenReportResponseDto.fromEntity(r, visionSummary));
        }
        return responseList;
    }

    /**
     * Lists reports for a city ordered by submission time.
     */
    @Transactional(readOnly = true)
    public List<CitizenReportResponseDto> getReportsByCity(UUID cityId) {
        List<CitizenReport> reports = citizenReportRepository.findByCityIdOrderBySubmittedAtDesc(cityId);
        List<CitizenReportResponseDto> responseList = new ArrayList<>(reports.size());
        for (CitizenReport r : reports) {
            Optional<GeminiAnalysis> analysisOpt = geminiAnalysisRepository.findTopByCitizenReportIdOrderByCreatedAtDesc(r.getId());
            CitizenReportResponseDto.VisionAnalysisSummaryDto visionSummary = analysisOpt.map(a ->
                    new CitizenReportResponseDto.VisionAnalysisSummaryDto(
                            a.getId(),
                            "ANALYZED",
                            a.getDetectedCategory(),
                            a.getConfidence(),
                            a.getNarrativeSummary() != null ? List.of(a.getNarrativeSummary()) : Collections.emptyList(),
                            a.getUncertaintyStatement() != null ? List.of(a.getUncertaintyStatement()) : Collections.emptyList(),
                            a.getModelVersion(),
                            a.getPromptVersion(),
                            a.getCreatedAt()
                    )
            ).orElse(null);
            responseList.add(CitizenReportResponseDto.fromEntity(r, visionSummary));
        }
        return responseList;
    }

    /**
     * Preserved legacy entity creation for compatibility with raw JSON submissions.
     */
    @Transactional
    public CitizenReport createReport(CitizenReport report) {
        if (report.getLatitude() != null && report.getLongitude() != null) {
            H3Utils.validateCoordinates(report.getLatitude(), report.getLongitude());
            if (report.getH3Index() == null || report.getH3Index().isBlank()) {
                report.setH3Index(H3Utils.coordinatesToH3(report.getLatitude(), report.getLongitude(), H3Utils.NEIGHBORHOOD_RESOLUTION));
            }
        }
        if (report.getCategory() != null) {
            report.setCategory(normalizeCategory(report.getCategory()));
        }
        return citizenReportRepository.save(report);
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "OTHER";
        }
        String upper = category.trim().toUpperCase().replace(" ", "_");
        if (upper.equals("ODOUR")) {
            return "ODOR";
        }
        if (VALID_CATEGORIES.contains(upper)) {
            return upper;
        }
        return "OTHER";
    }
}
