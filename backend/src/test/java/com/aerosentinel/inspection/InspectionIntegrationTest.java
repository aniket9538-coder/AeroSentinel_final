package com.aerosentinel.inspection;

import com.aerosentinel.alert.Alert;
import com.aerosentinel.alert.AlertRepository;
import com.aerosentinel.city.City;
import com.aerosentinel.city.CityRepository;
import com.aerosentinel.dto.inspection.AssignTeamRequest;
import com.aerosentinel.dto.inspection.SubmitVerificationRequest;
import com.aerosentinel.event.PollutionEvent;
import com.aerosentinel.event.PollutionEventRepository;
import com.aerosentinel.grid.GridCell;
import com.aerosentinel.grid.GridService;
import com.aerosentinel.hotspot.HotspotPrediction;
import com.aerosentinel.hotspot.HotspotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "officer@pune.gov.in", roles = {"AUTHORITY"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("F5-P6: Field Team Assignment & Verification PostgreSQL Integration Tests")
class InspectionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private FieldTeamRepository fieldTeamRepository;

    @Autowired
    private InspectionRepository inspectionRepository;

    @Autowired
    private FieldVerificationRepository fieldVerificationRepository;

    @Autowired
    private PollutionEventRepository pollutionEventRepository;

    @Autowired
    private HotspotRepository hotspotRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private GridService gridService;

    private static final String TEST_H3 = "886196944dfffff";
    private static UUID testCityId;
    private static UUID testGridCellId;
    private static UUID testAlertId;
    private static UUID testEventId;
    private static UUID testPredictionId;
    private static UUID testTeamId;
    private static UUID createdInspectionId;

    @BeforeEach
    void setupFixtures() {
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

    private void prepareAlertCandidate() {
        if (testAlertId != null && alertRepository.existsById(testAlertId)) {
            return;
        }

        HotspotPrediction pred = new HotspotPrediction();
        pred.setCityId(testCityId);
        pred.setGridCellId(testGridCellId);
        pred.setH3Index(TEST_H3);
        pred.setPredictedAt(Instant.now());
        pred.setRiskScore(0.88);
        pred.setRiskLevel("CRITICAL");
        pred.setConfidence(0.94);
        pred.setModelVersion("hotspot_classifier_v1");
        pred.setCreatedAt(Instant.now());
        pred = hotspotRepository.save(pred);
        testPredictionId = pred.getId();

        PollutionEvent event = new PollutionEvent();
        event.setEventCode("EVT-P6-TEST-" + System.currentTimeMillis());
        event.setH3Index(TEST_H3);
        event.setPredictionId(testPredictionId);
        event.setGridCellId(testGridCellId);
        event.setSeverity("CRITICAL");
        event.setStatus("OPEN");
        event.setStartedAt(Instant.now());
        event.setCreatedAt(Instant.now());
        event = pollutionEventRepository.save(event);
        testEventId = event.getId();

        Alert alert = new Alert();
        alert.setEventId(testEventId);
        alert.setCityId(testCityId);
        alert.setGridCellId(testGridCellId);
        alert.setH3Index(TEST_H3);
        alert.setPredictionId(testPredictionId);
        alert.setEventCode(event.getEventCode());
        alert.setSeverity("CRITICAL");
        alert.setRiskScore(0.88);
        alert.setEvidenceScore(0.85);
        alert.setTriageState("ALERT_CANDIDATE");
        alert.setTitle("P6 Test Alert: Severe Particulate Inversion");
        alert.setMessage("Heavy industrial emissions detected in Pune corridor");
        alert.setForecastSummary("1h: 185 ug/m3 (CRITICAL), 3h: 210 ug/m3 (CRITICAL), 6h: 195 ug/m3 (CRITICAL)");
        alert.setRecommendedAction("Dispatch field verification squad to industrial sector.");
        alert.setStatus("OPEN");
        alert.setCreatedAt(Instant.now());
        alert.setUpdatedAt(Instant.now());
        alert = alertRepository.save(alert);
        testAlertId = alert.getId();

        // Get or seed test team
        List<FieldTeam> teams = fieldTeamRepository.findAll();
        if (teams.isEmpty()) {
            FieldTeam t = new FieldTeam();
            t.setTeamCode("TEAM-PUN-01");
            t.setTeamName("Pune Municipal Rapid Response Team A");
            t.setCityId(testCityId);
            t.setStatus("AVAILABLE");
            t = fieldTeamRepository.save(t);
            testTeamId = t.getId();
        } else {
            testTeamId = teams.get(0).getId();
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. Seeded field teams are returned by GET /api/v1/field-teams")
    void test1_seededFieldTeams_existAndLoadable() throws Exception {
        mockMvc.perform(get("/api/v1/field-teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].teamCode", notNullValue()))
                .andExpect(jsonPath("$[0].teamName", notNullValue()));
    }

    @Test
    @Order(2)
    @DisplayName("2. Cannot assign field team to OPEN alert (must be ACKNOWLEDGED)")
    void test2_assignToOpenAlert_rejected() throws Exception {
        prepareAlertCandidate();

        AssignTeamRequest req = new AssignTeamRequest(testTeamId, null, null, "Try assigning to OPEN alert");
        mockMvc.perform(post("/api/v1/alerts/" + testAlertId + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Alert must be ACKNOWLEDGED")));
    }

    @Test
    @Order(3)
    @DisplayName("3. Acknowledge alert, then assign field team -> persists in DB with complete lineage")
    void test3_assignFieldTeamToAcknowledgedAlert_persistsInPostgresWithLineage() throws Exception {
        prepareAlertCandidate();

        // Acknowledge alert first
        mockMvc.perform(patch("/api/v1/alerts/" + testAlertId + "/acknowledge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));

        AssignTeamRequest req = new AssignTeamRequest(testTeamId, null, Instant.now(), "Investigate factory boilers");

        String responseJson = mockMvc.perform(post("/api/v1/alerts/" + testAlertId + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.alertId").value(testAlertId.toString()))
                .andExpect(jsonPath("$.teamId").value(testTeamId.toString()))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.eventId").value(testEventId.toString()))
                .andExpect(jsonPath("$.h3Index").value(TEST_H3))
                .andExpect(jsonPath("$.predictionId").value(testPredictionId.toString()))
                .andReturn().getResponse().getContentAsString();

        createdInspectionId = UUID.fromString(objectMapper.readTree(responseJson).get("id").asText());

        // Verify in DB
        Inspection dbRecord = inspectionRepository.findById(createdInspectionId).orElse(null);
        assertThat(dbRecord).isNotNull();
        assertThat(dbRecord.getAlertId()).isEqualTo(testAlertId);
        assertThat(dbRecord.getEventId()).isEqualTo(testEventId);
        assertThat(dbRecord.getH3Index()).isEqualTo(TEST_H3);
        assertThat(dbRecord.getPredictionId()).isEqualTo(testPredictionId);
        assertThat(dbRecord.getStatus()).isEqualTo("ASSIGNED");
    }

    @Test
    @Order(4)
    @DisplayName("4. Duplicate assignment request for active alert is rejected with CONFLICT (409)")
    void test4_duplicateAssignmentToSameAlert_rejectedWithConflict() throws Exception {
        AssignTeamRequest req = new AssignTeamRequest(testTeamId, null, null, "Duplicate attempt");

        mockMvc.perform(post("/api/v1/alerts/" + testAlertId + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already has an active field assignment")));
    }

    @Test
    @Order(5)
    @DisplayName("5. Cannot submit verification if inspection is not IN_PROGRESS")
    void test5_submitVerificationWhenNotInProgress_rejected() throws Exception {
        SubmitVerificationRequest req = new SubmitVerificationRequest(
                "CONFIRMED",
                "Stack emission verified",
                null, null, null, null
        );

        mockMvc.perform(post("/api/v1/inspections/" + createdInspectionId + "/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Inspection must be IN_PROGRESS")));
    }

    @Test
    @Order(6)
    @DisplayName("6. Start inspection -> transitions status from ASSIGNED to IN_PROGRESS")
    void test6_startInspection_transitionsToInProgress() throws Exception {
        mockMvc.perform(patch("/api/v1/inspections/" + createdInspectionId + "/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdInspectionId.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.startedAt", notNullValue()));

        Inspection dbRecord = inspectionRepository.findById(createdInspectionId).orElseThrow();
        assertThat(dbRecord.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(dbRecord.getStartedAt()).isNotNull();
    }

    @Test
    @Order(7)
    @DisplayName("7. Submit verification -> creates field_verifications record, transitions inspection to COMPLETED")
    void test7_submitVerification_transitionsToCompletedAndPersistsFieldEvidence() throws Exception {
        SubmitVerificationRequest req = new SubmitVerificationRequest(
                "CONFIRMED",
                "Heavy black particulate plumes observed from unscrubbed brick kiln stack",
                "Violating CPCB emission norms; issued immediate stop-work notice",
                "REF-PHOTO-01, REF-PHOTO-02",
                "Officer R. Kulkarni",
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/inspections/" + createdInspectionId + "/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt", notNullValue()))
                .andExpect(jsonPath("$.latestVerification.verificationResult").value("CONFIRMED"))
                .andExpect(jsonPath("$.latestVerification.observedConditions", containsString("unscrubbed brick kiln stack")));

        // Verify verification in database
        List<FieldVerification> verifs = fieldVerificationRepository.findByInspectionId(createdInspectionId);
        assertThat(verifs).isNotEmpty();
        FieldVerification fv = verifs.get(0);
        assertThat(fv.getAlertId()).isEqualTo(testAlertId);
        assertThat(fv.getEventId()).isEqualTo(testEventId);
        assertThat(fv.getH3Index()).isEqualTo(TEST_H3);
        assertThat(fv.getPredictionId()).isEqualTo(testPredictionId);
        assertThat(fv.getVerificationResult()).isEqualTo("CONFIRMED");
        assertThat(fv.getObservedConditions()).contains("unscrubbed brick kiln stack");
    }

    @Test
    @Order(8)
    @DisplayName("8. Query verifications for alert returns the completed verification record")
    void test8_verificationsQuery_returnsLineageAndRecords() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/" + testAlertId + "/verifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].alertId").value(testAlertId.toString()))
                .andExpect(jsonPath("$[0].verificationResult").value("CONFIRMED"));

        mockMvc.perform(get("/api/v1/inspections/" + createdInspectionId + "/verifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].inspectionId").value(createdInspectionId.toString()));
    }

    @Test
    @Order(9)
    @DisplayName("9. Authority queue returns enriched assignment and verification metadata")
    void test9_authorityQueueEnrichedWithAssignmentAndVerification() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/authority"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.alertId == '" + testAlertId + "')].assignedTeamName", notNullValue()))
                .andExpect(jsonPath("$[?(@.alertId == '" + testAlertId + "')].verificationResult", hasItem("CONFIRMED")));
    }

    @Test
    @Order(10)
    @DisplayName("10. Cannot assign field team to RESOLVED alert")
    void test10_assignToResolvedAlert_rejected() throws Exception {
        // Resolve the alert
        mockMvc.perform(patch("/api/v1/alerts/" + testAlertId + "/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        AssignTeamRequest req = new AssignTeamRequest(testTeamId, null, null, "Try assigning to RESOLVED alert");
        mockMvc.perform(post("/api/v1/alerts/" + testAlertId + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Cannot assign field team to a RESOLVED alert")));
    }
}
