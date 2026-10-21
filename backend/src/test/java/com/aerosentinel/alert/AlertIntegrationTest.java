package com.aerosentinel.alert;

import com.aerosentinel.city.City;
import com.aerosentinel.city.CityRepository;
import com.aerosentinel.event.PollutionEvent;
import com.aerosentinel.event.PollutionEventRepository;
import com.aerosentinel.evidence.EvidenceAiClient.EvidenceAiOutputDto;
import com.aerosentinel.forecast.ForecastResponse;
import com.aerosentinel.forecast.ForecastResponse.ForecastItem;
import com.aerosentinel.grid.GridCell;
import com.aerosentinel.grid.GridService;
import com.aerosentinel.hotspot.HotspotPrediction;
import com.aerosentinel.hotspot.HotspotRepository;
import com.aerosentinel.hotspot.HotspotSpatialContext;
import com.aerosentinel.hotspot.HotspotSpatialContext.*;
import com.aerosentinel.model.GeminiAnalysis;
import com.aerosentinel.repository.GeminiAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import com.aerosentinel.grid.GridRepository;

import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "officer@pune.gov.in", roles = {"AUTHORITY"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AlertIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AlertIntegrationTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AlertService alertService;

    @Autowired
    private PollutionEventRepository pollutionEventRepository;

    @Autowired
    private HotspotRepository hotspotRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private GridService gridService;

    @Autowired
    private GridRepository gridRepository;

    @Autowired
    private GeminiAnalysisRepository geminiAnalysisRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private com.aerosentinel.feature.FeatureSnapshotRepository featureSnapshotRepository;

    private static final String TEST_H3 = "886196944dfffff";
    private static UUID testCityId;
    private static UUID testGridCellId;
    private static UUID candidateAlertId;
    private static UUID candidateEventId;

    @AfterAll
    void cleanupTestArtifacts() {
        jdbcTemplate.execute("DELETE FROM alerts WHERE h3_index = '" + TEST_H3 + "'");
        jdbcTemplate.execute("DELETE FROM event_evidence WHERE event_id IN (SELECT id FROM pollution_events WHERE h3_index = '" + TEST_H3 + "')");
        jdbcTemplate.execute("DELETE FROM pollution_events WHERE h3_index = '" + TEST_H3 + "'");
        jdbcTemplate.execute("DELETE FROM hotspot_predictions WHERE h3_index = '" + TEST_H3 + "'");
        jdbcTemplate.execute("DELETE FROM grid_cells WHERE h3_index = '" + TEST_H3 + "'");
    }

    @BeforeEach
    void ensurePrerequisites() {
        City city = cityRepository.findByNameIgnoreCase("Pune").orElseGet(() -> {
            City c = new City();
            c.setName("Pune");
            c.setState("Maharashtra");
            c.setCountry("India");
            c.setActive(true);
            return cityRepository.save(c);
        });
        testCityId = city.getId();

        GridCell gridCell = gridService.getOrCreateGridCell(TEST_H3, testCityId);
        testGridCellId = gridCell.getId();
    }

    private HotspotPrediction createTestHotspotPrediction() {
        UUID snapshotId = featureSnapshotRepository.findAll().stream().findFirst()
                .map(com.aerosentinel.feature.FeatureSnapshot::getId).orElse(null);
        HotspotPrediction pred = new HotspotPrediction();
        pred.setCityId(testCityId);
        pred.setGridCellId(testGridCellId);
        pred.setH3Index(TEST_H3);
        pred.setPredictedAt(Instant.now());
        pred.setRiskScore(0.85);
        pred.setRiskLevel("CRITICAL");
        pred.setConfidence(0.92);
        pred.setModelVersion("hotspot_classifier_v1");
        pred.setFeatureSnapshotId(snapshotId);
        pred.setCreatedAt(Instant.now());
        return hotspotRepository.save(pred);
    }

    private PollutionEvent createTestEvent(String code) {
        HotspotPrediction pred = createTestHotspotPrediction();
        PollutionEvent event = new PollutionEvent();
        event.setEventCode(code);
        event.setH3Index(TEST_H3);
        event.setPredictionId(pred.getId());
        event.setGridCellId(testGridCellId);
        event.setSeverity("HIGH");
        event.setStatus("OPEN");
        event.setStartedAt(Instant.now());
        event.setCreatedAt(Instant.now());
        return pollutionEventRepository.save(event);
    }

    private HotspotSpatialContext createSpatialContext(UUID predictionId) {
        AirContext air = new AirContext("VALID", 158.0, 210.0, 45.0, 18.0, 1.2, 30.0, Instant.now(), "PUN-IND-01", 158.0);
        WeatherContext weather = new WeatherContext("VALID", 24.5, 88.0, 3.2, 0.89, 210.0, 950.0, 0.0, Instant.now());
        MonitoringCoverageContext cov = new MonitoringCoverageContext("VALID", 0.5, 3, 0, 0.95);
        SpatialDispersionContext disp = new SpatialDispersionContext("VALID", 145.0, 2.1, 0.5);
        EnvironmentalGisContext gis = new EnvironmentalGisContext("VALID", 0.8, 0.2, 5, 1, 2, 8.4, 0.0, 60.0, 0.0, 0.0);
        ConfidenceBreakdown conf = new ConfidenceBreakdown(0.92, 0.90, 0.95, 0.90, 0.5, 0);

        return new HotspotSpatialContext(
                predictionId,
                TEST_H3,
                testCityId,
                "Pune",
                UUID.randomUUID(),
                Instant.now(),
                0.85,
                "CRITICAL",
                0.92,
                "ML",
                "hotspot_classifier_v1",
                "LIVE",
                air,
                weather,
                cov,
                disp,
                gis,
                true,
                0.20,
                conf
        );
    }

    private ForecastResponse createForecast(UUID predictionId) {
        Instant t0 = Instant.now();
        List<ForecastItem> horizons = List.of(
                new ForecastItem(1, t0.plusSeconds(3600), 162.5, 155.0, 170.0, "ug/m3"),
                new ForecastItem(3, t0.plusSeconds(10800), 175.2, 160.0, 185.0, "ug/m3"),
                new ForecastItem(6, t0.plusSeconds(21600), 180.0, 162.0, 195.0, "ug/m3")
        );
        return new ForecastResponse(
                TEST_H3,
                testCityId,
                t0,
                t0.plusSeconds(60),
                "forecast_regressors_v1",
                predictionId,
                UUID.randomUUID(),
                "SUCCESS",
                "LIVE",
                horizons,
                null
        );
    }

    private EvidenceAiOutputDto createAiOutput(String triageState, double score, String eventCode) {
        EvidenceAiOutputDto.ScoreBreakdownDto sb = new EvidenceAiOutputDto.ScoreBreakdownDto(
                0.35, 0.40, 0.33, 0.95, 0.67, 1.0, 0.0, score, score
        );
        EvidenceAiOutputDto.AiInterpretationDto ai = new EvidenceAiOutputDto.AiInterpretationDto(
                "Industrial emission cluster detected in sector " + TEST_H3,
                "Ground sensors report PM2.5 of 158 ug/m3 with multi-source FIRMS correlation",
                "Severe industrial emission event",
                List.of("PM2.5 concentration at 158.0 ug/m3", "Industrial zone distance 0.8 km"),
                "Forecast trajectory projects peak ~180 ug/m3 across 6h",
                "CAAQMS within 500m provides high spatial confidence",
                List.of("Stack emission volume requires field verification"),
                false,
                true,
                "gemini-2.0-flash",
                "structured_event_explanation_v001"
        );
        EvidenceAiOutputDto.RecommendedVerificationDto rec = new EvidenceAiOutputDto.RecommendedVerificationDto(
                "Deploy environmental enforcement team to industrial sector",
                "CRITICAL",
                List.of("Inspect combustion permits", "Collect stack samples")
        );
        EvidenceAiOutputDto.ProvenanceDto prov = new EvidenceAiOutputDto.ProvenanceDto(
                TEST_H3,
                testCityId.toString(),
                UUID.randomUUID().toString(),
                "hotspot_classifier_v1",
                "forecast_regressors_v1",
                "v1.0.0",
                "gemini-2.0-flash",
                "structured_event_explanation_v001",
                Instant.now().toString()
        );
        return new EvidenceAiOutputDto(
                "SUCCESS",
                TEST_H3,
                testCityId.toString(),
                UUID.randomUUID().toString(),
                eventCode,
                eventCode,
                List.of(TEST_H3),
                score,
                0.667,
                "consistent",
                triageState,
                sb,
                Map.of("ground_sensor", "supported"),
                Collections.emptyList(),
                Collections.emptyList(),
                List.of("Ground PM2.5 measurement exceeds threshold"),
                Collections.emptyList(),
                ai,
                rec,
                prov
        );
    }

    @Test
    @Order(1)
    @DisplayName("P5-INT-1: ALERT_CANDIDATE creates persistent alert in PostgreSQL with lineage")
    void testAlertCandidatePersistenceInPostgres() {
        String eventCode = "EVT-TEST-ALERT-" + System.currentTimeMillis();
        PollutionEvent event = createTestEvent(eventCode);
        candidateEventId = event.getId();
        HotspotSpatialContext ctx = createSpatialContext(event.getPredictionId());
        ForecastResponse fc = createForecast(event.getPredictionId());
        EvidenceAiOutputDto ai = createAiOutput("ALERT_CANDIDATE", 0.667, eventCode);

        // Also persist a GeminiAnalysis to test rich enrichment
        GeminiAnalysis ga = new GeminiAnalysis();
        ga.setEventId(event.getId());
        ga.setPredictionId(event.getPredictionId());
        ga.setH3Index(TEST_H3);
        ga.setModelVersion("gemini-2.0-flash");
        ga.setPromptVersion("structured_event_explanation_v001");
        ga.setEventSummaryPublic("Industrial emission cluster detected in sector " + TEST_H3);
        ga.setDetectedCondition("Severe industrial emission event");
        ga.setIsGrounded(true);
        geminiAnalysisRepository.save(ga);

        Optional<Alert> createdOpt = alertService.createOrUpdateAlertCandidate(event, ctx, fc, ai);

        assertThat(createdOpt).isPresent();
        Alert alert = createdOpt.get();
        candidateAlertId = alert.getId();

        // Verify in database via repository
        Optional<Alert> inDb = alertRepository.findById(candidateAlertId);
        assertThat(inDb).isPresent();
        assertThat(inDb.get().getEventId()).isEqualTo(event.getId());
        assertThat(inDb.get().getEventCode()).isEqualTo(eventCode);
        assertThat(inDb.get().getH3Index()).isEqualTo(TEST_H3);
        assertThat(inDb.get().getPredictionId()).isEqualTo(event.getPredictionId());
        assertThat(inDb.get().getTriageState()).isEqualTo("ALERT_CANDIDATE");
        assertThat(inDb.get().getStatus()).isEqualTo("OPEN");
        assertThat(inDb.get().getEvidenceScore()).isEqualTo(0.667);
        assertThat(inDb.get().getForecastSummary()).contains("+1h");

        log.info("Verified PostgreSQL alert candidate persistence: alertId={}, eventId={}, h3={}, triage={}",
                alert.getId(), alert.getEventId(), alert.getH3Index(), alert.getTriageState());
    }

    @Test
    @Order(2)
    @DisplayName("P5-INT-2: INSUFFICIENT_EVIDENCE does not create alert in PostgreSQL")
    void testInsufficientEvidenceDoesNotPersistAlert() {
        String eventCode = "EVT-TEST-INSUFF-" + System.currentTimeMillis();
        PollutionEvent event = createTestEvent(eventCode);
        HotspotSpatialContext ctx = createSpatialContext(event.getPredictionId());
        ForecastResponse fc = createForecast(event.getPredictionId());
        EvidenceAiOutputDto ai = createAiOutput("INSUFFICIENT_EVIDENCE", 0.157, eventCode);

        Optional<Alert> result = alertService.createOrUpdateAlertCandidate(event, ctx, fc, ai);

        assertThat(result).isEmpty();
        Optional<Alert> inDb = alertRepository.findByEventId(event.getId());
        assertThat(inDb).isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("P5-INT-3: MONITOR does not create alert in PostgreSQL")
    void testMonitorDoesNotPersistAlert() {
        String eventCode = "EVT-TEST-MONITOR-" + System.currentTimeMillis();
        PollutionEvent event = createTestEvent(eventCode);
        HotspotSpatialContext ctx = createSpatialContext(event.getPredictionId());
        ForecastResponse fc = createForecast(event.getPredictionId());
        EvidenceAiOutputDto ai = createAiOutput("MONITOR", 0.420, eventCode);

        Optional<Alert> result = alertService.createOrUpdateAlertCandidate(event, ctx, fc, ai);

        assertThat(result).isEmpty();
        Optional<Alert> inDb = alertRepository.findByEventId(event.getId());
        assertThat(inDb).isEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("P5-INT-4: Idempotency - repeated creation returns existing alert and does not duplicate")
    void testAlertCreationIdempotency() {
        PollutionEvent event = pollutionEventRepository.findById(candidateEventId).orElseThrow();
        HotspotSpatialContext ctx = createSpatialContext(event.getPredictionId());
        ForecastResponse fc = createForecast(event.getPredictionId());
        EvidenceAiOutputDto ai = createAiOutput("ALERT_CANDIDATE", 0.667, event.getEventCode());

        long countBefore = alertRepository.count();
        Optional<Alert> repeated = alertService.createOrUpdateAlertCandidate(event, ctx, fc, ai);

        assertThat(repeated).isPresent();
        assertThat(repeated.get().getId()).isEqualTo(candidateAlertId);
        long countAfter = alertRepository.count();
        assertThat(countAfter).isEqualTo(countBefore);
    }

    @Test
    @Order(5)
    @DisplayName("P5-INT-5: GET /api/v1/alerts/authority returns rich enriched alert items")
    void testGetAuthorityQueue() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/authority")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[?(@.alertId == '" + candidateAlertId + "')].eventCode").isNotEmpty())
                .andExpect(jsonPath("$[?(@.alertId == '" + candidateAlertId + "')].h3Index").value(TEST_H3))
                .andExpect(jsonPath("$[?(@.alertId == '" + candidateAlertId + "')].triageState").value("ALERT_CANDIDATE"))
                .andExpect(jsonPath("$[?(@.alertId == '" + candidateAlertId + "')].status").value("OPEN"))
                .andExpect(jsonPath("$[?(@.alertId == '" + candidateAlertId + "')].evidenceScore").value(0.667))
                .andExpect(jsonPath("$[?(@.alertId == '" + candidateAlertId + "')].hasGeminiAnalysis").value(true));
    }

    @Test
    @Order(6)
    @DisplayName("P5-INT-6: GET /api/v1/alerts/{alertId} returns single candidate")
    void testGetAlertById() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/" + candidateAlertId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertId").value(candidateAlertId.toString()))
                .andExpect(jsonPath("$.h3Index").value(TEST_H3))
                .andExpect(jsonPath("$.triageState").value("ALERT_CANDIDATE"))
                .andExpect(jsonPath("$.forecastSummary", containsString("+1h")));
    }

    @Test
    @Order(7)
    @DisplayName("P5-INT-7: PATCH /api/v1/alerts/{alertId}/acknowledge updates status to ACKNOWLEDGED")
    void testAcknowledgeAlert() throws Exception {
        mockMvc.perform(patch("/api/v1/alerts/" + candidateAlertId + "/acknowledge")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedAt").isNotEmpty());

        Alert inDb = alertRepository.findById(candidateAlertId).orElseThrow();
        assertThat(inDb.getStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(inDb.getAcknowledgedAt()).isNotNull();
    }

    @Test
    @Order(8)
    @DisplayName("P5-INT-8: PATCH /api/v1/alerts/{alertId}/resolve updates status to RESOLVED")
    void testResolveAlert() throws Exception {
        mockMvc.perform(patch("/api/v1/alerts/" + candidateAlertId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());

        Alert inDb = alertRepository.findById(candidateAlertId).orElseThrow();
        assertThat(inDb.getStatus()).isEqualTo("RESOLVED");
        assertThat(inDb.getResolvedAt()).isNotNull();
    }

    @Autowired
    private com.aerosentinel.evidence.EvidenceRepository evidenceRepository;

    @Autowired
    private com.aerosentinel.citizen.CitizenReportRepository citizenReportRepository;

    @Test
    @Order(9)
    @DisplayName("P5-INT-9: Invalid transition: acknowledging already resolved alert returns 400 Bad Request")
    void testInvalidTransitionRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/alerts/" + candidateAlertId + "/acknowledge")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Cannot acknowledge an already resolved alert")));
    }

    @Test
    @Order(10)
    @DisplayName("F7-P2-INT-10: Security fix: GET /api/v1/events and /api/v1/events/{id} return 200 OK")
    void testEventsApiSecurityFix() throws Exception {
        mockMvc.perform(get("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        if (candidateEventId != null) {
            mockMvc.perform(get("/api/v1/events/" + candidateEventId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(candidateEventId.toString()))
                    .andExpect(jsonPath("$.prediction").exists())
                    .andExpect(jsonPath("$.prediction.h3Index").value(TEST_H3))
                    .andExpect(jsonPath("$.forecast").exists())
                    .andExpect(jsonPath("$.evidence").exists())
                    .andExpect(jsonPath("$.alert").exists());
        }
    }

    @Test
    @Order(12)
    @DisplayName("F7-P3-INT-12: Event context API: non-existent returns 404")
    void testEventContextApiNonExistent() throws Exception {
        mockMvc.perform(get("/api/v1/events/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(11)
    @DisplayName("F7-P2-INT-11: GET /api/v1/alerts/{alertId} exposes attached citizen evidence with safe photo URL")
    void testAlertExposesAttachedCitizenEvidence() throws Exception {
        com.aerosentinel.citizen.CitizenReport report = new com.aerosentinel.citizen.CitizenReport();
        report.setCityId(testCityId);
        report.setLatitude(18.5308);
        report.setLongitude(73.8475);
        report.setH3Index(TEST_H3);
        report.setCategory("INDUSTRIAL_EMISSION");
        report.setDescription("Dense black plume observed from factory stack");
        report.setImageUrl("/api/v1/citizen/photos/" + UUID.randomUUID() + ".jpg");
        report.setSubmittedAt(Instant.now());
        report.setStatus("ANALYZED");
        report.setVerificationStatus("UNVERIFIED");
        report = citizenReportRepository.save(report);

        com.aerosentinel.evidence.EventEvidence ev = new com.aerosentinel.evidence.EventEvidence();
        ev.setEventId(candidateEventId);
        ev.setDataSource("CITIZEN");
        ev.setSourceType("CITIZEN_OBSERVATION");
        ev.setRelevanceTier("AUXILIARY");
        ev.setSourceRef(report.getId().toString());
        ev.setEvidenceKey("citizen-report-" + report.getId());
        ev.setEvidenceValue("Citizen observation [SMOKE_LIKE]: Dense black plume observed from factory stack");
        ev.setConfidenceScore(0.95);
        ev.setWeight(1.0);
        ev.setObservedAt(Instant.now());
        evidenceRepository.save(ev);

        mockMvc.perform(get("/api/v1/alerts/" + candidateAlertId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertId").value(candidateAlertId.toString()))
                .andExpect(jsonPath("$.citizenEvidence", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.citizenEvidence[0].reportReference", startsWith("CR-")))
                .andExpect(jsonPath("$.citizenEvidence[0].dataSource").value("CITIZEN"))
                .andExpect(jsonPath("$.citizenEvidence[0].relevanceTier").value("AUXILIARY"))
                .andExpect(jsonPath("$.citizenEvidence[0].photoUrl", startsWith("/api/v1/citizen/photos/")));
    }
}
