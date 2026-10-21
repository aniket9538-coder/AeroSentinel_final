package com.aerosentinel.action;

import com.aerosentinel.alert.Alert;
import com.aerosentinel.alert.AlertRepository;
import com.aerosentinel.alert.AlertService;
import com.aerosentinel.dto.action.DismissAlertRequest;
import com.aerosentinel.dto.action.RecordActionRequest;
import com.aerosentinel.dto.action.ResolveAlertRequest;
import com.aerosentinel.dto.inspection.AssignTeamRequest;
import com.aerosentinel.dto.inspection.SubmitVerificationRequest;
import com.aerosentinel.event.PollutionEvent;
import com.aerosentinel.event.PollutionEventRepository;
import com.aerosentinel.event.PollutionEventStatus;
import com.aerosentinel.exception.ValidationException;
import com.aerosentinel.inspection.FieldTeam;
import com.aerosentinel.inspection.FieldTeamRepository;
import com.aerosentinel.inspection.Inspection;
import com.aerosentinel.inspection.InspectionRepository;
import com.aerosentinel.inspection.InspectionService;
import com.aerosentinel.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OperationalWorkflowLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AlertService alertService;

    @Autowired
    private PollutionEventRepository pollutionEventRepository;

    @Autowired
    private FieldTeamRepository fieldTeamRepository;

    @Autowired
    private InspectionRepository inspectionRepository;

    @Autowired
    private InspectionService inspectionService;

    @Autowired
    private ActionRepository actionRepository;

    @Autowired
    private ActionService actionService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.aerosentinel.city.CityRepository cityRepository;

    @Autowired
    private com.aerosentinel.grid.GridService gridService;

    private static final String H3_TEST = "88608850e5fffff";

    private UUID testCityId;
    private UUID testGridCellId;
    private String authorityToken;
    private String citizenToken;
    private String analystToken;

    @BeforeEach
    void setUpTokens() {
        var city = cityRepository.findByNameIgnoreCase("Pune").orElseGet(() -> {
            var c = new com.aerosentinel.city.City();
            c.setName("Pune");
            c.setState("Maharashtra");
            c.setCountry("India");
            c.setActive(true);
            return cityRepository.save(c);
        });
        testCityId = city.getId();
        var gridCell = gridService.getOrCreateGridCell(H3_TEST, testCityId);
        testGridCellId = gridCell.getId();

        authorityToken = "Bearer " + jwtService.generateToken("authority-user", "AUTHORITY");
        citizenToken = "Bearer " + jwtService.generateToken("citizen-user", "CITIZEN");
        analystToken = "Bearer " + jwtService.generateToken("analyst-user", "ANALYST");
    }

    private FieldTeam getOrCreateFieldTeam() {
        return fieldTeamRepository.findAll().stream().findFirst().orElseGet(() -> {
            FieldTeam ft = new FieldTeam();
            ft.setTeamCode("TEAM-TEST-01");
            ft.setTeamName("Test Operational Team");
            ft.setStatus("AVAILABLE");
            return fieldTeamRepository.save(ft);
        });
    }

    private PollutionEvent createTestEvent(String initialStatus) {
        PollutionEvent event = new PollutionEvent();
        event.setGridCellId(testGridCellId);
        event.setH3Index(H3_TEST);
        event.setEventCode("EVT-TEST-" + UUID.randomUUID().toString().substring(0, 8));
        event.setSeverity("HIGH");
        event.setStatus(initialStatus);
        event.setStartedAt(Instant.now());
        return pollutionEventRepository.save(event);
    }

    private Alert createTestAlert(PollutionEvent event, String initialStatus) {
        Alert alert = new Alert();
        alert.setEventId(event.getId());
        alert.setCityId(testCityId);
        alert.setGridCellId(event.getGridCellId());
        alert.setH3Index(event.getH3Index());
        alert.setEventCode(event.getEventCode());
        alert.setSeverity(event.getSeverity());
        alert.setRiskScore(0.7998);
        alert.setEvidenceScore(0.6500);
        alert.setTriageState("ALERT_CANDIDATE");
        alert.setTitle("Potential Pollution Incident");
        alert.setMessage("Test multi-source event");
        alert.setStatus(initialStatus);
        return alertRepository.save(alert);
    }

    @Test
    @Order(1)
    @DisplayName("P6-01: Canonical step 1: Alert ACKNOWLEDGE keeps event in OPEN")
    void testAcknowledgeAlertKeepsEventOpen() {
        PollutionEvent event = createTestEvent("OPEN");
        Alert alert = createTestAlert(event, "OPEN");

        Alert acked = alertService.acknowledgeAlert(alert.getId(), null);
        assertThat(acked.getStatus()).isEqualTo("ACKNOWLEDGED");

        PollutionEvent evInDb = pollutionEventRepository.findById(event.getId()).orElseThrow();
        assertThat(evInDb.getStatus()).isEqualTo("OPEN");
    }

    @Test
    @Order(2)
    @DisplayName("P6-02: Canonical step 2: OPEN -> ASSIGNED upon field team assignment")
    void testAssignFieldTeamTransitionsEventToAssigned() {
        PollutionEvent event = createTestEvent("OPEN");
        Alert alert = createTestAlert(event, "ACKNOWLEDGED");
        FieldTeam team = getOrCreateFieldTeam();

        inspectionService.assignFieldTeam(alert.getId(), new AssignTeamRequest(team.getId(), null, null, "Assign team A"));

        PollutionEvent evInDb = pollutionEventRepository.findById(event.getId()).orElseThrow();
        assertThat(evInDb.getStatus()).isEqualTo("ASSIGNED");

        Alert alertInDb = alertRepository.findById(alert.getId()).orElseThrow();
        assertThat(alertInDb.getStatus()).isEqualTo("ASSIGNED");
    }

    @Test
    @Order(3)
    @DisplayName("P6-03: Canonical step 3: ASSIGNED -> IN_INSPECTION when inspection starts")
    void testStartInspectionTransitionsEventToInInspection() {
        PollutionEvent event = createTestEvent("OPEN");
        Alert alert = createTestAlert(event, "ACKNOWLEDGED");
        FieldTeam team = getOrCreateFieldTeam();

        var assignResp = inspectionService.assignFieldTeam(alert.getId(), new AssignTeamRequest(team.getId(), null, null, "Dispatch team"));
        assertThat(pollutionEventRepository.findById(event.getId()).orElseThrow().getStatus()).isEqualTo("ASSIGNED");

        inspectionService.startInspection(assignResp.id());

        PollutionEvent evInDb = pollutionEventRepository.findById(event.getId()).orElseThrow();
        assertThat(evInDb.getStatus()).isEqualTo("IN_INSPECTION");

        Alert alertInDb = alertRepository.findById(alert.getId()).orElseThrow();
        assertThat(alertInDb.getStatus()).isEqualTo("IN_INSPECTION");
    }

    @Test
    @Order(4)
    @DisplayName("P6-04: Canonical step 4: IN_INSPECTION -> ACTION_TAKEN when AuthorityAction is recorded")
    void testRecordActionTransitionsEventToActionTaken() {
        PollutionEvent event = createTestEvent("IN_INSPECTION");
        Alert alert = createTestAlert(event, "IN_INSPECTION");

        var actionResp = actionService.recordAction(
                new RecordActionRequest(alert.getId(), "FIELD_VERIFICATION", "On-site assessment conducted; source identified", "Officer A"),
                "Officer A"
        );

        assertThat(actionResp.actionType()).isEqualTo("FIELD_VERIFICATION");
        assertThat(actionResp.eventStatus()).isEqualTo("ACTION_TAKEN");
        assertThat(actionResp.alertStatus()).isEqualTo("ACTION_TAKEN");

        PollutionEvent evInDb = pollutionEventRepository.findById(event.getId()).orElseThrow();
        assertThat(evInDb.getStatus()).isEqualTo("ACTION_TAKEN");

        Alert alertInDb = alertRepository.findById(alert.getId()).orElseThrow();
        assertThat(alertInDb.getStatus()).isEqualTo("ACTION_TAKEN");

        List<AuthorityAction> actions = actionRepository.findByAlertIdOrderByPerformedAtDesc(alert.getId());
        assertThat(actions).isNotEmpty();
        assertThat(actions.get(0).getActionType()).isEqualTo("FIELD_VERIFICATION");
    }

    @Test
    @Order(5)
    @DisplayName("P6-05: Canonical step 5: ACTION_TAKEN -> RESOLVED when resolution is completed")
    void testResolveAlertTransitionsEventToResolved() {
        PollutionEvent event = createTestEvent("ACTION_TAKEN");
        Alert alert = createTestAlert(event, "ACTION_TAKEN");

        Alert resolved = alertService.resolveAlert(alert.getId(), null, "Factory scrubber repaired and verified.", true);
        assertThat(resolved.getStatus()).isEqualTo("RESOLVED");
        assertThat(resolved.getResolvedAt()).isNotNull();

        PollutionEvent evInDb = pollutionEventRepository.findById(event.getId()).orElseThrow();
        assertThat(evInDb.getStatus()).isEqualTo("RESOLVED");
        assertThat(evInDb.getResolvedAt()).isNotNull();

        List<AuthorityAction> actions = actionRepository.findByAlertIdOrderByPerformedAtDesc(alert.getId());
        assertThat(actions).anyMatch(a -> "RESOLUTION".equals(a.getActionType()));
    }

    @Test
    @Order(6)
    @DisplayName("P6-06: Authorized dismissal path: OPEN -> DISMISSED with required notes")
    void testDismissAlertTransitionsEventToDismissed() {
        PollutionEvent event = createTestEvent("OPEN");
        Alert alert = createTestAlert(event, "OPEN");

        Alert dismissed = alertService.dismissAlert(alert.getId(), null, "Permitted controlled agricultural burn verified with local fire dept");
        assertThat(dismissed.getStatus()).isEqualTo("DISMISSED");

        PollutionEvent evInDb = pollutionEventRepository.findById(event.getId()).orElseThrow();
        assertThat(evInDb.getStatus()).isEqualTo("DISMISSED");

        List<AuthorityAction> actions = actionRepository.findByAlertIdOrderByPerformedAtDesc(alert.getId());
        assertThat(actions).anyMatch(a -> "DISMISSAL".equals(a.getActionType()));
    }

    @Test
    @Order(7)
    @DisplayName("P6-07: Invalid transitions rejected by state machine")
    void testInvalidTransitionsRejected() {
        // RESOLVED -> ASSIGNED
        assertThatThrownBy(() -> PollutionEventStatus.validateTransition("RESOLVED", "ASSIGNED"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition from terminal state RESOLVED");

        // RESOLVED -> IN_INSPECTION
        assertThatThrownBy(() -> PollutionEventStatus.validateTransition("RESOLVED", "IN_INSPECTION"))
                .isInstanceOf(IllegalStateException.class);

        // DISMISSED -> ASSIGNED
        assertThatThrownBy(() -> PollutionEventStatus.validateTransition("DISMISSED", "ASSIGNED"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition from terminal state DISMISSED");

        // IN_INSPECTION -> ASSIGNED
        assertThatThrownBy(() -> PollutionEventStatus.validateTransition("IN_INSPECTION", "ASSIGNED"))
                .isInstanceOf(IllegalStateException.class);

        // OPEN -> ACTION_TAKEN (skipping inspection)
        assertThatThrownBy(() -> PollutionEventStatus.validateTransition("OPEN", "ACTION_TAKEN"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Order(8)
    @DisplayName("P6-08: Resolution requires notes (rejects blank notes)")
    void testResolutionRequiresNotes() {
        PollutionEvent event = createTestEvent("ACTION_TAKEN");
        Alert alert = createTestAlert(event, "ACTION_TAKEN");

        assertThatThrownBy(() -> alertService.resolveAlert(alert.getId(), null, "   ", true))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Resolution notes are required");
    }

    @Test
    @Order(9)
    @DisplayName("P6-09: Direct resolution from OPEN rejected in strict operational flow")
    void testDirectResolutionFromOpenRejectedInOperationalFlow() {
        PollutionEvent event = createTestEvent("OPEN");
        Alert alert = createTestAlert(event, "OPEN");

        assertThatThrownBy(() -> alertService.resolveAlert(alert.getId(), null, "Resolved without inspection", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("directly from OPEN state");
    }

    @Test
    @Order(10)
    @DisplayName("P6-10: Action recording rejected when event is still OPEN")
    void testRecordActionRejectedWhenEventIsOpen() {
        PollutionEvent event = createTestEvent("OPEN");
        Alert alert = createTestAlert(event, "OPEN");

        assertThatThrownBy(() -> actionService.recordAction(
                new RecordActionRequest(alert.getId(), "SITE_CHECK", "Attempting action on OPEN event", "Officer"),
                "Officer"
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("ACTION_TAKEN");
    }

    @Test
    @Order(11)
    @DisplayName("P6-11: Dismissal rejected after inspection has started")
    void testDismissalRejectedAfterInspectionStarted() {
        PollutionEvent event = createTestEvent("IN_INSPECTION");
        Alert alert = createTestAlert(event, "IN_INSPECTION");

        assertThatThrownBy(() -> alertService.dismissAlert(alert.getId(), null, "Attempt dismissal in inspection"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot dismiss alert after inspection has started");
    }

    @Test
    @Order(12)
    @DisplayName("P6-12: POST /api/v1/actions rejects CITIZEN role with 403 Forbidden")
    void testActionEndpointRejectsCitizenWith403() throws Exception {
        PollutionEvent event = createTestEvent("IN_INSPECTION");
        Alert alert = createTestAlert(event, "IN_INSPECTION");

        RecordActionRequest req = new RecordActionRequest(alert.getId(), "SITE_CHECK", "Citizen trying authority action", "Citizen");

        mockMvc.perform(post("/api/v1/actions")
                        .header("Authorization", citizenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(13)
    @DisplayName("P6-13: POST /api/v1/actions rejects ANALYST role with 403 Forbidden")
    void testActionEndpointRejectsAnalystWith403() throws Exception {
        PollutionEvent event = createTestEvent("IN_INSPECTION");
        Alert alert = createTestAlert(event, "IN_INSPECTION");

        RecordActionRequest req = new RecordActionRequest(alert.getId(), "SITE_CHECK", "Analyst trying authority action", "Analyst");

        mockMvc.perform(post("/api/v1/actions")
                        .header("Authorization", analystToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(14)
    @DisplayName("P6-14: POST /api/v1/actions succeeds for AUTHORITY role")
    void testActionEndpointSucceedsForAuthorityRole() throws Exception {
        PollutionEvent event = createTestEvent("IN_INSPECTION");
        Alert alert = createTestAlert(event, "IN_INSPECTION");

        RecordActionRequest req = new RecordActionRequest(alert.getId(), "MOBILE_SENSOR_DEPLOYED", "Mobile particulate monitor deployed at street corner", "Inspector Patil");

        mockMvc.perform(post("/api/v1/actions")
                        .header("Authorization", authorityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actionType").value("MOBILE_SENSOR_DEPLOYED"))
                .andExpect(jsonPath("$.eventStatus").value("ACTION_TAKEN"));

        PollutionEvent evInDb = pollutionEventRepository.findById(event.getId()).orElseThrow();
        assertThat(evInDb.getStatus()).isEqualTo("ACTION_TAKEN");
    }

    @Test
    @Order(15)
    @DisplayName("P6-15: GET /api/v1/actions returns list of actions with lineage")
    void testGetActionsReturnsList() throws Exception {
        PollutionEvent event = createTestEvent("IN_INSPECTION");
        Alert alert = createTestAlert(event, "IN_INSPECTION");

        actionService.recordAction(new RecordActionRequest(alert.getId(), "ADVISORY_ISSUED", "Air quality health advisory issued to industrial unit", "Authority"), "Authority");

        mockMvc.perform(get("/api/v1/actions?alertId=" + alert.getId())
                        .header("Authorization", authorityToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].actionType").value("ADVISORY_ISSUED"))
                .andExpect(jsonPath("$[0].actionDetails").value("Air quality health advisory issued to industrial unit"));
    }

    @Test
    @Order(16)
    @DisplayName("P6-16: End-to-End Operational Lifecycle: ACKNOWLEDGE -> ASSIGN -> START -> VERIFY -> ACTION -> RESOLVE")
    void testEndToEndOperationalLifecycleFlow() throws Exception {
        // 1. Initial State: Event OPEN, Alert OPEN
        PollutionEvent event = createTestEvent("OPEN");
        Alert alert = createTestAlert(event, "OPEN");
        FieldTeam team = getOrCreateFieldTeam();

        // 2. Acknowledge
        mockMvc.perform(patch("/api/v1/alerts/" + alert.getId() + "/acknowledge")
                        .header("Authorization", authorityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));

        assertThat(pollutionEventRepository.findById(event.getId()).orElseThrow().getStatus()).isEqualTo("OPEN");

        // 3. Assign
        AssignTeamRequest assignReq = new AssignTeamRequest(team.getId(), null, null, "Dispatch rapid response unit");
        var assignResult = mockMvc.perform(post("/api/v1/alerts/" + alert.getId() + "/assign")
                        .header("Authorization", authorityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andReturn();

        String assignBody = assignResult.getResponse().getContentAsString();
        UUID inspectionId = UUID.fromString(objectMapper.readTree(assignBody).get("id").asText());

        assertThat(pollutionEventRepository.findById(event.getId()).orElseThrow().getStatus()).isEqualTo("ASSIGNED");

        // 4. Start Inspection
        mockMvc.perform(patch("/api/v1/inspections/" + inspectionId + "/start")
                        .header("Authorization", authorityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        assertThat(pollutionEventRepository.findById(event.getId()).orElseThrow().getStatus()).isEqualTo("IN_INSPECTION");

        // 5. Submit Field Verification
        SubmitVerificationRequest verifyReq = new SubmitVerificationRequest(
                "CONFIRMED",
                "Visible high particulate density near manufacturing plant stack",
                "Secondary filter malfunctioning",
                "Sensor PM2.5 = 185 ug/m3",
                "Team Lead Deshmukh",
                null
        );
        mockMvc.perform(post("/api/v1/inspections/" + inspectionId + "/verify")
                        .header("Authorization", authorityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Event remains in IN_INSPECTION awaiting authority action
        assertThat(pollutionEventRepository.findById(event.getId()).orElseThrow().getStatus()).isEqualTo("IN_INSPECTION");

        // 6. Record Authority Action
        RecordActionRequest actionReq = new RecordActionRequest(
                alert.getId(),
                "FIELD_VERIFICATION",
                "Formal regulatory notice served to facility manager requiring immediate scrubber maintenance",
                "Inspector A. Deshmukh"
        );
        mockMvc.perform(post("/api/v1/actions")
                        .header("Authorization", authorityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actionReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actionType").value("FIELD_VERIFICATION"))
                .andExpect(jsonPath("$.eventStatus").value("ACTION_TAKEN"));

        assertThat(pollutionEventRepository.findById(event.getId()).orElseThrow().getStatus()).isEqualTo("ACTION_TAKEN");

        // 7. Resolve Alert and Event
        ResolveAlertRequest resolveReq = new ResolveAlertRequest(
                "Scrubber repaired, stack emissions returned to within permitted NAAQS thresholds. Incident resolved.",
                null
        );
        mockMvc.perform(patch("/api/v1/alerts/" + alert.getId() + "/resolve")
                        .header("Authorization", authorityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        PollutionEvent finalEvent = pollutionEventRepository.findById(event.getId()).orElseThrow();
        assertThat(finalEvent.getStatus()).isEqualTo("RESOLVED");
        assertThat(finalEvent.getResolvedAt()).isNotNull();

        Alert finalAlert = alertRepository.findById(alert.getId()).orElseThrow();
        assertThat(finalAlert.getStatus()).isEqualTo("RESOLVED");
        assertThat(finalAlert.getResolvedAt()).isNotNull();
    }
}
