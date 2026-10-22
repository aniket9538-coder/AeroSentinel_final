package com.aerosentinel.evidence;

import com.aerosentinel.air.AirObservation;
import com.aerosentinel.air.AirObservationRepository;
import com.aerosentinel.alert.Alert;
import com.aerosentinel.alert.AlertRepository;
import com.aerosentinel.citizen.*;
import com.aerosentinel.city.City;
import com.aerosentinel.city.CityRepository;
import com.aerosentinel.event.PollutionEvent;
import com.aerosentinel.event.PollutionEventRepository;
import com.aerosentinel.grid.GridCell;
import com.aerosentinel.grid.GridService;
import com.aerosentinel.hotspot.HotspotContextService;
import com.aerosentinel.hotspot.HotspotSpatialContext;
import com.aerosentinel.model.GeminiAnalysis;
import com.aerosentinel.repository.GeminiAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit & Contract tests verifying Phase 7 Evidence Fusion Engine & the 12 Mandatory Safety Invariants.
 */
class EvidenceFusionServiceTest {

    private CitizenReportRepository citizenReportRepository;
    private CitizenReportDeduplicator citizenReportDeduplicator;
    private CitizenEventMatcher citizenEventMatcher;
    private GeminiAnalysisRepository geminiAnalysisRepository;
    private AirObservationRepository airObservationRepository;
    private HotspotContextService hotspotContextService;
    private PollutionEventRepository pollutionEventRepository;
    private EvidenceRepository evidenceRepository;
    private AlertRepository alertRepository;
    private CityRepository cityRepository;
    private GridService gridService;

    private EvidenceFusionService fusionService;

    private static final String H3_INDEX = "88608850e5fffff";
    private static final UUID PUNE_CITY_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    @BeforeEach
    void setUp() {
        citizenReportRepository = mock(CitizenReportRepository.class);
        citizenReportDeduplicator = new CitizenReportDeduplicator();
        citizenEventMatcher = new CitizenEventMatcher();
        geminiAnalysisRepository = mock(GeminiAnalysisRepository.class);
        airObservationRepository = mock(AirObservationRepository.class);
        hotspotContextService = mock(HotspotContextService.class);
        pollutionEventRepository = mock(PollutionEventRepository.class);
        evidenceRepository = mock(EvidenceRepository.class);
        alertRepository = mock(AlertRepository.class);
        cityRepository = mock(CityRepository.class);
        gridService = mock(GridService.class);

        City puneCity = new City();
        puneCity.setId(PUNE_CITY_ID);
        puneCity.setName("Pune");
        when(cityRepository.findByNameIgnoreCase("Pune")).thenReturn(Optional.of(puneCity));

        fusionService = new EvidenceFusionService(
                citizenReportRepository,
                citizenReportDeduplicator,
                citizenEventMatcher,
                geminiAnalysisRepository,
                airObservationRepository,
                null,
                hotspotContextService,
                pollutionEventRepository,
                evidenceRepository,
                alertRepository,
                cityRepository,
                gridService
        );
    }

    @Test
    @DisplayName("Safety Invariant 1, 2, 4 & 5: Citizen report alone yields PROVISIONAL_TRIAGE, never high severity or alert candidate")
    void citizenReportAlone_YieldsProvisionalTriage_NeverHighSeverityAlert() {
        CitizenReport cr = new CitizenReport();
        cr.setId(UUID.randomUUID());
        cr.setCityId(PUNE_CITY_ID);
        cr.setH3Index(H3_INDEX);
        cr.setCategory("SMOKE");
        cr.setDescription("Dense black plume rising from illegal trash fire");
        cr.setSubmittedAt(Instant.now());
        cr.setCreatedAt(Instant.now());

        when(citizenReportRepository.findByH3IndexOrderBySubmittedAtDesc(H3_INDEX))
                .thenReturn(List.of(cr));
        when(hotspotContextService.buildSpatialContextForH3(H3_INDEX))
                .thenReturn(Optional.empty());
        when(airObservationRepository.findFirstByH3IndexOrderByObservedAtDesc(H3_INDEX))
                .thenReturn(Optional.empty());

        TriageCandidateDto candidate = fusionService.evaluateTriageCandidate(H3_INDEX);

        assertThat(candidate.isProvisional()).isTrue();
        assertThat(candidate.triageStatus()).isEqualTo("PROVISIONAL_TRIAGE");
        assertThat(candidate.severity()).isEqualTo("MODERATE"); // Capped: Never HIGH or CRITICAL
        assertThat(candidate.independentSourceCount()).isEqualTo(1);
        assertThat(candidate.contributingEvidence()).hasSize(1);
        assertThat(candidate.contributingEvidence().get(0).evidenceClass())
                .isEqualTo(EvidenceClass.CITIZEN_SUBMITTED);
        assertThat(candidate.explanations())
                .anyMatch(s -> s.contains("Lacks independent physical ground sensor"));
    }

    @Test
    @DisplayName("Safety Invariant 3: CitizenVision output derived from photo is derivative, NOT an independent corroborator")
    void citizenVisionAnalysis_IsMarkedDerivative_NotIndependentCorroboration() {
        UUID reportId = UUID.randomUUID();
        CitizenReport cr = new CitizenReport();
        cr.setId(reportId);
        cr.setH3Index(H3_INDEX);
        cr.setCategory("BURNING");
        cr.setDescription("Plume observed");
        cr.setSubmittedAt(Instant.now());

        GeminiAnalysis ga = new GeminiAnalysis();
        ga.setId(UUID.randomUUID());
        ga.setCitizenReportId(reportId);
        ga.setDetectedCategory("BURNING");
        ga.setConfidence(0.85);
        ga.setNarrativeSummary("Dense smoke column detected in image");
        ga.setModelVersion("gemini-2.0-flash");

        when(citizenReportRepository.findByH3IndexOrderBySubmittedAtDesc(H3_INDEX))
                .thenReturn(List.of(cr));
        when(geminiAnalysisRepository.findTopByCitizenReportIdOrderByCreatedAtDesc(reportId))
                .thenReturn(Optional.of(ga));
        when(hotspotContextService.buildSpatialContextForH3(H3_INDEX))
                .thenReturn(Optional.empty());
        when(airObservationRepository.findFirstByH3IndexOrderByObservedAtDesc(H3_INDEX))
                .thenReturn(Optional.empty());

        TriageCandidateDto candidate = fusionService.evaluateTriageCandidate(H3_INDEX);

        // Crucial: 1 citizen report + 1 vision analysis = 1 independent source, 1 related source
        assertThat(candidate.independentSourceCount()).isEqualTo(1);
        assertThat(candidate.relatedSourceCount()).isEqualTo(1);

        NormalizedEvidenceItem visionItem = candidate.contributingEvidence().stream()
                .filter(i -> i.evidenceClass() == EvidenceClass.CITIZEN_VISION)
                .findFirst()
                .orElseThrow();

        assertThat(visionItem.isIndependent()).isFalse();
        assertThat(visionItem.relatedSourceId()).isEqualTo(reportId.toString());
        assertThat(candidate.sourceIndependenceBreakdown().get(visionItem.evidenceId()))
                .contains("DERIVATIVE_SOURCE");
    }

    @Test
    @DisplayName("Deduplication: Duplicate citizen reports within 60 minutes are coalesced without inflating corroboration")
    void duplicateCitizenReports_Within60Minutes_AreCoalescedWithoutInflatingCounts() {
        Instant baseTime = Instant.now().minus(Duration.ofMinutes(15));

        CitizenReport cr1 = new CitizenReport();
        cr1.setId(UUID.randomUUID());
        cr1.setH3Index(H3_INDEX);
        cr1.setCategory("SMOKE");
        cr1.setSubmittedAt(baseTime);

        CitizenReport cr2 = new CitizenReport(); // Duplicate (5 mins later, same cell)
        cr2.setId(UUID.randomUUID());
        cr2.setH3Index(H3_INDEX);
        cr2.setCategory("SMOKE");
        cr2.setSubmittedAt(baseTime.plus(Duration.ofMinutes(5)));

        CitizenReport cr3 = new CitizenReport(); // Duplicate (10 mins later, same cell)
        cr3.setId(UUID.randomUUID());
        cr3.setH3Index(H3_INDEX);
        cr3.setCategory("SMOKE");
        cr3.setSubmittedAt(baseTime.plus(Duration.ofMinutes(10)));

        when(citizenReportRepository.findByH3IndexOrderBySubmittedAtDesc(H3_INDEX))
                .thenReturn(List.of(cr1, cr2, cr3));

        TriageCandidateDto candidate = fusionService.evaluateTriageCandidate(H3_INDEX);

        // 1 contributing primary report, 2 excluded duplicates
        assertThat(candidate.contributingEvidence().stream()
                .filter(i -> i.evidenceClass() == EvidenceClass.CITIZEN_SUBMITTED).count())
                .isEqualTo(1);
        assertThat(candidate.excludedEvidence()).hasSize(2);
        assertThat(candidate.excludedEvidence().get(0).reasonCode())
                .isEqualTo("DUPLICATE_CITIZEN_REPORT");
        assertThat(candidate.independentSourceCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Safety Invariant 6: Independent physical sensor telemetry corroborates candidate and allows ALERT_CANDIDATE")
    void independentPhysicalTelemetry_ElevatesCorroboration_AndEnablesAlertCandidate() {
        CitizenReport cr = new CitizenReport();
        cr.setId(UUID.randomUUID());
        cr.setH3Index(H3_INDEX);
        cr.setCategory("BURNING");
        cr.setSubmittedAt(Instant.now().minus(Duration.ofMinutes(20)));

        // Fresh physical ground observation
        AirObservation obs = new AirObservation();
        obs.setId(UUID.randomUUID());
        obs.setStationId("PUN-001");
        obs.setH3Index(H3_INDEX);
        obs.setPm25(165.0); // Severe PM2.5 spike
        obs.setObservedAt(Instant.now().minus(Duration.ofMinutes(15)));

        // F3 Hotspot model output
        HotspotSpatialContext ctx = mock(HotspotSpatialContext.class);
        when(ctx.cityId()).thenReturn(PUNE_CITY_ID);
        when(ctx.cityName()).thenReturn("Pune");
        when(ctx.riskScore()).thenReturn(0.85);
        when(ctx.operationalThreshold()).thenReturn(0.20);
        when(ctx.isHotspot()).thenReturn(true);
        when(ctx.confidence()).thenReturn(0.92);
        when(ctx.modelVersion()).thenReturn("hotspot_classifier_v1");
        when(ctx.predictedAt()).thenReturn(Instant.now());

        when(citizenReportRepository.findByH3IndexOrderBySubmittedAtDesc(H3_INDEX))
                .thenReturn(List.of(cr));
        when(airObservationRepository.findFirstByH3IndexOrderByObservedAtDesc(H3_INDEX))
                .thenReturn(Optional.of(obs));
        when(hotspotContextService.buildSpatialContextForH3(H3_INDEX))
                .thenReturn(Optional.of(ctx));

        TriageCandidateDto candidate = fusionService.evaluateTriageCandidate(H3_INDEX);

        assertThat(candidate.isProvisional()).isFalse();
        assertThat(candidate.triageStatus()).isEqualTo("ALERT_CANDIDATE");
        assertThat(candidate.independentSourceCount()).isEqualTo(3); // Citizen, Sensor, ML
        assertThat(candidate.severity()).isEqualTo("HIGH");
        assertThat(candidate.explanations())
                .anyMatch(s -> s.contains("Corroborated Triage: Physical CAAQMS sensor telemetry"));
    }

    @Test
    @DisplayName("Safety Invariant 7: Stale sensor telemetry (> 60m old) is rejected from current corroboration")
    void staleTelemetry_OlderThan60Minutes_IsExcludedFromCorroboration() {
        CitizenReport cr = new CitizenReport();
        cr.setId(UUID.randomUUID());
        cr.setH3Index(H3_INDEX);
        cr.setCategory("SMOKE");
        cr.setSubmittedAt(Instant.now());

        // Stale ground observation (110 minutes old)
        AirObservation staleObs = new AirObservation();
        staleObs.setId(UUID.randomUUID());
        staleObs.setStationId("PUN-001");
        staleObs.setH3Index(H3_INDEX);
        staleObs.setPm25(190.0);
        staleObs.setObservedAt(Instant.now().minus(Duration.ofMinutes(110)));

        when(citizenReportRepository.findByH3IndexOrderBySubmittedAtDesc(H3_INDEX))
                .thenReturn(List.of(cr));
        when(airObservationRepository.findFirstByH3IndexOrderByObservedAtDesc(H3_INDEX))
                .thenReturn(Optional.of(staleObs));

        TriageCandidateDto candidate = fusionService.evaluateTriageCandidate(H3_INDEX);

        // Stale observation is excluded
        assertThat(candidate.excludedEvidence())
                .anyMatch(e -> "STALE_TELEMETRY".equals(e.reasonCode()));
        // Invariant 7 holds: Remains provisional because only citizen evidence is fresh
        assertThat(candidate.isProvisional()).isTrue();
        assertThat(candidate.triageStatus()).isEqualTo("PROVISIONAL_TRIAGE");
    }

    @Test
    @DisplayName("Safety Invariant 8: Missing sensor data is not treated as negative measurement")
    void missingData_IsNotTreatedAsNegativeEvidence() {
        when(citizenReportRepository.findByH3IndexOrderBySubmittedAtDesc(H3_INDEX))
                .thenReturn(Collections.emptyList());
        when(airObservationRepository.findFirstByH3IndexOrderByObservedAtDesc(H3_INDEX))
                .thenReturn(Optional.empty());

        TriageCandidateDto candidate = fusionService.evaluateTriageCandidate(H3_INDEX);

        assertThat(candidate.explanations())
                .anyMatch(s -> s.contains("missing physical data is not treated as negative measurement"));
    }

    @Test
    @DisplayName("Metric Separation: F3 Risk Score != F5 Evidence Score != Visual Confidence")
    void metricSeparation_RiskScoreNotEqualToEvidenceScore() {
        CitizenReport cr = new CitizenReport();
        cr.setId(UUID.randomUUID());
        cr.setH3Index(H3_INDEX);
        cr.setCategory("SMOKE");
        cr.setSubmittedAt(Instant.now());

        HotspotSpatialContext ctx = mock(HotspotSpatialContext.class);
        when(ctx.cityId()).thenReturn(PUNE_CITY_ID);
        when(ctx.cityName()).thenReturn("Pune");
        when(ctx.riskScore()).thenReturn(0.7998); // High F3 risk score
        when(ctx.operationalThreshold()).thenReturn(0.20);
        when(ctx.isHotspot()).thenReturn(true);
        when(ctx.confidence()).thenReturn(0.85);

        when(citizenReportRepository.findByH3IndexOrderBySubmittedAtDesc(H3_INDEX))
                .thenReturn(List.of(cr));
        when(hotspotContextService.buildSpatialContextForH3(H3_INDEX))
                .thenReturn(Optional.of(ctx));

        TriageCandidateDto candidate = fusionService.evaluateTriageCandidate(H3_INDEX);

        // Strict Separation Verification
        assertThat(candidate.riskScore()).isEqualTo(0.7998);
        assertThat(candidate.evidenceScore()).isNotEqualTo(candidate.riskScore());
        assertThat(candidate.explanations())
                .anyMatch(s -> s.contains("Authoritative Metric Separation: F3 ML Risk Score (0.7998) != F5 Multi-Source Evidence Score"));
    }

    @Test
    @DisplayName("Idempotency: Repeated evaluation does not duplicate canonical records or inflate counts")
    void reprocessing_IsIdempotent_DoesNotDuplicateCandidatesOrCounts() {
        CitizenReport cr = new CitizenReport();
        cr.setId(UUID.randomUUID());
        cr.setH3Index(H3_INDEX);
        cr.setCategory("SMOKE");
        cr.setSubmittedAt(Instant.now());

        when(citizenReportRepository.findByH3IndexOrderBySubmittedAtDesc(H3_INDEX))
                .thenReturn(List.of(cr));

        PollutionEvent event = new PollutionEvent();
        event.setId(UUID.randomUUID());
        event.setH3Index(H3_INDEX);
        event.setEventCode("EVT-88608850");
        when(pollutionEventRepository.findByEventCode(any())).thenReturn(Optional.of(event));

        TriageCandidateDto run1 = fusionService.fuseAndPersist(H3_INDEX);
        TriageCandidateDto run2 = fusionService.fuseAndPersist(H3_INDEX);

        assertThat(run1.independentSourceCount()).isEqualTo(run2.independentSourceCount());
        assertThat(run1.evidenceScore()).isEqualTo(run2.evidenceScore());
        assertThat(run1.triageStatus()).isEqualTo(run2.triageStatus());
    }
}
