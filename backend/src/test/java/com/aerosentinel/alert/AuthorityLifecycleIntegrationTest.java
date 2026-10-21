package com.aerosentinel.alert;

import com.aerosentinel.action.ActionRepository;
import com.aerosentinel.action.ActionService;
import com.aerosentinel.action.AuthorityAction;
import com.aerosentinel.dto.action.DismissAlertRequest;
import com.aerosentinel.dto.action.RecordActionRequest;
import com.aerosentinel.dto.action.ResolveAlertRequest;
import com.aerosentinel.dto.inspection.AssignTeamRequest;
import com.aerosentinel.dto.inspection.ReassignTeamRequest;
import com.aerosentinel.dto.inspection.SubmitVerificationRequest;
import com.aerosentinel.event.PollutionEvent;
import com.aerosentinel.event.PollutionEventRepository;
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

/**
 * Phase 8 (Feature 7) Authoritative Integration Test Suite for Municipal Lifecycle,
 * Incident Assignment, Reassignment, and Resolution.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthorityLifecycleIntegrationTest {

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

    private static final String H3_INDEX = "88608850e5fffff";

    private UUID cityId;
    private UUID gridCellId;
    private String authorityToken;
    private String citizenToken;
    private String analystToken;

    @BeforeEach
    void setUp() {
        var city = cityRepository.findByNameIgnoreCase("Pune").orElseGet(() -> {
            var c = new com.aerosentinel.city.City();
            c.setName("Pune");
            c.setState("Maharashtra");
            c.setCountry("India");
            c.setActive(true);
            return cityRepository.save(c);
        });
        cityId = city.getId();
        var gridCell = gridService.getOrCreateGridCell(H3_INDEX, cityId);
        gridCellId = gridCell.getId();

        authorityToken = "Bearer " + jwtService.generateToken("authority-officer-88", "AUTHORITY");
        citizenToken = "Bearer " + jwtService.generateToken("citizen-user-12", "CITIZEN");
        analystToken = "Bearer " + jwtService.generateToken("analyst-user-34", "ANALYST");
    }

    private FieldTeam createOrGetFieldTeam(String code, String name) {
        return fieldTeamRepository.findAll().stream()
                .filter(t -> code.equalsIgnoreCase(t.getTeamCode()))
                .findFirst()
                .orElseGet(() -> {
                    FieldTeam ft = new FieldTeam();
                    ft.setTeamCode(code);
                    ft.setTeamName(name);
                    ft.setStatus("AVAILABLE");
                    ft.setCityId(cityId);
                    return fieldTeamRepository.save(ft);
                });
    }

    private PollutionEvent createEvent(String status) {
        PollutionEvent event = new PollutionEvent();
        event.setGridCellId(gridCellId);
        event.setH3Index(H3_INDEX);
        event.setEventCode("EVT-F8-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        event.setSeverity("HIGH");
        event.setStatus(status);
        event.setStartedAt(Instant.now());
        return pollutionEventRepository.save(event);
    }

    private Alert createAlert(PollutionEvent event, String status) {
        Alert alert = new Alert();
        alert.setEventId(event.getId());
        alert.setCityId(cityId);
        alert.setGridCellId(event.getGridCellId());
        alert.setH3Index(event.getH3Index());
        alert.setEventCode(event.getEventCode());
        alert.setSeverity(event.getSeverity());
        alert.setRiskScore(0.85);
        alert.setEvidenceScore(0.72);
        alert.setTriageState("ALERT_CANDIDATE");
        alert.setTitle("F8 Industrial Emission Alert");
        alert.setMessage("Corroborated sensor and citizen evidence candidate");
        alert.setStatus(status);
        alert.setCreatedAt(Instant.now());
        alert.setUpdatedAt(Instant.now());
        return alertRepository.save(alert);
    }

    @Test
    @Order(1)
    @DisplayName("F8-01: Canonical lifecycle flow OPEN -> ACK -> ASSIGNED -> IN_INSPECTION -> ACTION_TAKEN -> RESOLVED")
    void testCanonicalLifecycleEndToEnd() throws Exception {
        PollutionEvent event = createEvent("OPEN");
        Alert alert = createAlert(event, "OPEN");
        FieldTeam team = createOrGetFieldTeam("TEAM-F8-01", "Rapid Response Squad Alpha");

        // 1. Acknowledge alert
        mockMvc.perform(patch("/api/v1/alerts/" + alert.getId() + "/acknowledge")
                        .header("Authorization", authorityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));

        Alert ackedAlert = alertRepository.findById(alert.getId()).orElseThrow();
        assertThat(ackedAlert.getStatus()).isEqualTo("ACKNOWLEDGED");

        // 2. Assign team
        AssignTeamRequest assignReq = new AssignTeamRequest(team.getId(), null, null, "Immediate dispatch for verification");
        var assignRes = mockMvc.perform(post("/api/v1/alerts/" + alert.getId() + "/assign")
                        .header("Authorization", authorityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andReturn();

        UUID inspectionId = UUID.fromString(objectMapper.readTree(assignRes.getResponse().getContentAsString()).get("id").asText());

        Alert assignedAlert = alertRepository.findById(alert.getId()).orElseThrow();
        assertThat(assignedAlert.getStatus()).isEqualTo("ASSIGNED");

        // 3. Start inspection
        mockMvc.perform(patch("/api/v1/inspections/" + inspectionId + "/start")
                        .header("Authorization", authorityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        Alert inInspAlert = alertRepository.findById(alert.getId()).orElseThrow();
        assertThat(inInspAlert.getStatus()).isEqualTo("IN_INSPECTION");

        // 4. Submit verification
        SubmitVerificationRequest verifyReq = new SubmitVerificationRequest(
                "CONFIRMED", "Observed heavy boiler fugitive emissions", "Scrubber pressure gauge failure", "EV-REF-01", "Inspector Patil", Instant.now()
        );
        mockMvc.perform(post("/api/v1/inspections/" + inspectionId + "/verify")
                        .header("Authorization", authorityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 5. Record authority action
        RecordActionRequest actionReq = new RecordActionRequest(
                alert.getId(), "MITIGATION_ORDERED", "Order issued under Section 31A Air Act to replace failed seal", "Officer Patil"
        );
        mockMvc.perform(post("/api/v1/actions")
                        .header("Authorization", authorityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actionReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actionType").value("MITIGATION_ORDERED"))
                .andExpect(jsonPath("$.alertStatus").value("ACTION_TAKEN"));

        Alert actionAlert = alertRepository.findById(alert.getId()).orElseThrow();
        assertThat(actionAlert.getStatus()).isEqualTo("ACTION_TAKEN");

        // 6. Resolve alert
        ResolveAlertRequest resolveReq = new ResolveAlertRequest(
                "Boiler seal replaced and certified; emissions normalized to 32 ug/m3", null
        );
        mockMvc.perform(patch("/api/v1/alerts/" + alert.getId() + "/resolve")
                        .header("Authorization", authorityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        Alert resolvedAlert = alertRepository.findById(alert.getId()).orElseThrow();
        assertThat(resolvedAlert.getStatus()).isEqualTo("RESOLVED");
        assertThat(resolvedAlert.getResolvedAt()).isNotNull();

        // 7. Verify complete audit trail in authority_actions
        List<AuthorityAction> auditLog = actionRepository.findByAlertIdOrderByPerformedAtDesc(alert.getId());
        assertThat(auditLog).isNotEmpty();
        assertThat(auditLog.stream().map(AuthorityAction::getActionType))
                .contains("ACKNOWLEDGEMENT", "ASSIGNMENT", "FIELD_INSPECTION_STARTED", "FIELD_VERIFICATION", "MITIGATION_ORDERED", "RESOLUTION");

        // Verify actor provenance
        assertThat(auditLog.stream().allMatch(a -> a.getPerformedBy() != null && !a.getPerformedBy().isBlank())).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("F8-02: Reassignment workflow with mandatory reason and team release")
    void testReassignmentWorkflow() throws Exception {
        PollutionEvent event = createEvent("OPEN");
        Alert alert = createAlert(event, "ACKNOWLEDGED");
        FieldTeam teamAlpha = createOrGetFieldTeam("TEAM-F8-02A", "Alpha Team");
        FieldTeam teamBeta = createOrGetFieldTeam("TEAM-F8-02B", "Beta Team");

        // Initial assignment to Alpha
        inspectionService.assignFieldTeam(alert.getId(), new AssignTeamRequest(teamAlpha.getId(), null, null, "First response"), "Lead Officer");

        FieldTeam alphaAfterAssign = fieldTeamRepository.findById(teamAlpha.getId()).orElseThrow();
        assertThat(alphaAfterAssign.getStatus()).isEqualTo("DISPATCHED");

        // Reassign to Beta with mandatory reason
        ReassignTeamRequest reassignReq = new ReassignTeamRequest(teamBeta.getId(), "Vehicle breakdown en-route", "Urgent reroute");
        mockMvc.perform(post("/api/v1/alerts/" + alert.getId() + "/reassign")
                        .header("Authorization", authorityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reassignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamName").value("Beta Team"))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        // Verify old team released to AVAILABLE
        FieldTeam alphaAfterReassign = fieldTeamRepository.findById(teamAlpha.getId()).orElseThrow();
        assertThat(alphaAfterReassign.getStatus()).isEqualTo("AVAILABLE");

        // Verify new team marked DISPATCHED
        FieldTeam betaAfterReassign = fieldTeamRepository.findById(teamBeta.getId()).orElseThrow();
        assertThat(betaAfterReassign.getStatus()).isEqualTo("DISPATCHED");

        // Verify audit log has REASSIGNMENT action with reason
        List<AuthorityAction> actions = actionRepository.findByAlertIdOrderByPerformedAtDesc(alert.getId());
        assertThat(actions).anyMatch(a -> "REASSIGNMENT".equals(a.getActionType())
                && a.getReason() != null && a.getReason().contains("Vehicle breakdown en-route"));
    }

    @Test
    @Order(3)
    @DisplayName("F8-03: Reassignment without reason is rejected with 400 Bad Request")
    void testReassignmentMissingReasonRejected() throws Exception {
        PollutionEvent event = createEvent("OPEN");
        Alert alert = createAlert(event, "ACKNOWLEDGED");
        FieldTeam team = createOrGetFieldTeam("TEAM-F8-03", "Gamma Team");

        inspectionService.assignFieldTeam(alert.getId(), new AssignTeamRequest(team.getId(), null, null, "Initial"), "Lead Officer");

        ReassignTeamRequest blankReq = new ReassignTeamRequest(team.getId(), "   ", "Notes");
        mockMvc.perform(post("/api/v1/alerts/" + alert.getId() + "/reassign")
                        .header("Authorization", authorityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    @DisplayName("F8-04: Dismissal path: OPEN -> DISMISSED with mandatory reason")
    void testDismissalWorkflow() throws Exception {
        PollutionEvent event = createEvent("OPEN");
        Alert alert = createAlert(event, "OPEN");

        DismissAlertRequest dismissReq = new DismissAlertRequest("Permitted flare event verified with petrochemical plant safety desk", null);
        mockMvc.perform(patch("/api/v1/alerts/" + alert.getId() + "/dismiss")
                        .header("Authorization", authorityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dismissReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));

        Alert dismissedAlert = alertRepository.findById(alert.getId()).orElseThrow();
        assertThat(dismissedAlert.getStatus()).isEqualTo("DISMISSED");

        List<AuthorityAction> actions = actionRepository.findByAlertIdOrderByPerformedAtDesc(alert.getId());
        assertThat(actions).anyMatch(a -> "DISMISSAL".equals(a.getActionType())
                && a.getReason().contains("Permitted flare event"));
    }

    @Test
    @Order(5)
    @DisplayName("F8-05: Illegal state machine skips rejected: OPEN -> RESOLVED (skipping inspection)")
    void testIllegalSkipRejected() throws Exception {
        PollutionEvent event = createEvent("OPEN");
        Alert alert = createAlert(event, "OPEN");

        ResolveAlertRequest resolveReq = new ResolveAlertRequest("Trying to close open alert directly", null);
        mockMvc.perform(patch("/api/v1/alerts/" + alert.getId() + "/resolve")
                        .header("Authorization", authorityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveReq)))
                .andExpect(status().isConflict()); // IllegalStateException mapped to 409 CONFLICT
    }

    @Test
    @Order(6)
    @DisplayName("F8-06: Terminal state immutability: mutating RESOLVED alert is rejected")
    void testTerminalStateImmutability() {
        PollutionEvent event = createEvent("RESOLVED");
        Alert alert = createAlert(event, "RESOLVED");

        assertThatThrownBy(() -> alertService.acknowledgeAlert(alert.getId()))
                .isInstanceOf(com.aerosentinel.exception.ValidationException.class)
                .hasMessageContaining("Cannot acknowledge an already resolved alert");

        assertThatThrownBy(() -> alertService.dismissAlert(alert.getId(), null, "Trying to dismiss resolved alert"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot dismiss an already RESOLVED alert");
    }

    @Test
    @Order(7)
    @DisplayName("F8-07: Authorization matrix: Anonymous (401), Citizen (403), Analyst mutation (403)")
    void testAuthorizationMatrix() throws Exception {
        PollutionEvent event = createEvent("OPEN");
        Alert alert = createAlert(event, "OPEN");

        // Anonymous attempt -> 401
        mockMvc.perform(patch("/api/v1/alerts/" + alert.getId() + "/acknowledge"))
                .andExpect(status().isUnauthorized());

        // Citizen attempt -> 403
        mockMvc.perform(patch("/api/v1/alerts/" + alert.getId() + "/acknowledge")
                        .header("Authorization", citizenToken))
                .andExpect(status().isForbidden());

        // Analyst attempt to acknowledge -> 403
        mockMvc.perform(patch("/api/v1/alerts/" + alert.getId() + "/acknowledge")
                        .header("Authorization", analystToken))
                .andExpect(status().isForbidden());

        // Analyst attempt to assign team -> 403
        mockMvc.perform(post("/api/v1/alerts/" + alert.getId() + "/assign")
                        .header("Authorization", analystToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());

        // Analyst can read the authority queue -> 200 OK
        mockMvc.perform(get("/api/v1/alerts/authority")
                        .header("Authorization", analystToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(8)
    @DisplayName("F8-08: F5 Evidence links and citizen evidence references remain preserved on alert")
    void testF5EvidenceLineagePreserved() {
        PollutionEvent event = createEvent("OPEN");
        Alert alert = createAlert(event, "OPEN");

        var queueItem = alertService.toAuthorityQueueItemDto(alert);
        assertThat(queueItem.eventId()).isEqualTo(event.getId());
        assertThat(queueItem.h3Index()).isEqualTo(H3_INDEX);
        assertThat(queueItem.evidenceScore()).isEqualTo(0.72);
        assertThat(queueItem.triageState()).isEqualTo("ALERT_CANDIDATE");
    }
}
