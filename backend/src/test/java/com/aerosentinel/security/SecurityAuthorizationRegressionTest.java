package com.aerosentinel.security;

import com.aerosentinel.action.ActionController;
import com.aerosentinel.action.ActionService;
import com.aerosentinel.alert.Alert;
import com.aerosentinel.alert.AlertController;
import com.aerosentinel.alert.AlertService;
import com.aerosentinel.citizen.CitizenReportController;
import com.aerosentinel.citizen.CitizenReportResponseDto;
import com.aerosentinel.citizen.CitizenReportService;
import com.aerosentinel.citizen.PhotoStorageService;
import com.aerosentinel.citizen.CitizenLookupRateLimiter;
import com.aerosentinel.assistant.service.ClientIpResolver;
import com.aerosentinel.config.SecurityConfig;
import com.aerosentinel.dto.action.ActionResponseDto;
import com.aerosentinel.dto.action.RecordActionRequest;
import com.aerosentinel.dto.action.ResolveAlertRequest;
import com.aerosentinel.dto.alert.AuthorityQueueItemDto;
import com.aerosentinel.dto.inspection.AssignTeamRequest;
import com.aerosentinel.dto.inspection.InspectionResponseDto;
import com.aerosentinel.dto.inspection.SubmitVerificationRequest;
import com.aerosentinel.health.HealthController;
import com.aerosentinel.inspection.InspectionController;
import com.aerosentinel.inspection.InspectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AlertController.class,
        ActionController.class,
        InspectionController.class,
        HealthController.class,
        CitizenReportController.class
})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, com.aerosentinel.exception.GlobalExceptionHandler.class})
class SecurityAuthorizationRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private AlertService alertService;

    @MockBean
    private ActionService actionService;

    @MockBean
    private InspectionService inspectionService;

    @MockBean
    private CitizenReportService citizenReportService;

    @MockBean
    private PhotoStorageService photoStorageService;

    @MockBean
    private CitizenLookupRateLimiter citizenLookupRateLimiter;

    @MockBean
    private ClientIpResolver clientIpResolver;

    private static final UUID ALERT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CITY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GRID_CELL_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    // =========================================================================
    // 1. ANONYMOUS REQUESTS ARE DENIED (401 Unauthorized)
    // =========================================================================

    @Test
    @DisplayName("P0 Security: Anonymous request to resolve alert is denied with 401")
    void anonymous_resolveAlert_denied() throws Exception {
        ResolveAlertRequest req = new ResolveAlertRequest("Test resolution notes", UUID.randomUUID());

        mockMvc.perform(patch("/api/v1/alerts/" + ALERT_ID + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("P0 Security: Anonymous request to dismiss alert is denied with 401")
    void anonymous_dismissAlert_denied() throws Exception {
        mockMvc.perform(patch("/api/v1/alerts/" + ALERT_ID + "/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dismissalReason\":\"Test dismissal\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("P0 Security: Anonymous request to record action is denied with 401")
    void anonymous_recordAction_denied() throws Exception {
        RecordActionRequest req = new RecordActionRequest(ALERT_ID, "COMMUNITY_NOTICE", "Public advisory", "Field Officer");

        mockMvc.perform(post("/api/v1/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("P0 Security: Anonymous direct alert creation is denied with 401")
    void anonymous_createAlert_denied() throws Exception {
        Alert alert = new Alert();
        alert.setCityId(CITY_ID);
        alert.setGridCellId(GRID_CELL_ID);
        alert.setTitle("Unauthorized Alert");
        alert.setMessage("This alert must be rejected");
        alert.setSeverity("HIGH");

        mockMvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alert)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("P0 Security: Anonymous request to start inspection is denied with 401")
    void anonymous_startInspection_denied() throws Exception {
        mockMvc.perform(patch("/api/v1/inspections/" + UUID.randomUUID() + "/start"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("P0 Security: Anonymous request to submit verification is denied with 401")
    void anonymous_submitVerification_denied() throws Exception {
        SubmitVerificationRequest req = new SubmitVerificationRequest(
                "VERIFIED_UNHEALTHY", "Smoke plume observed", "Inspector notes", "REF-001", "Officer Smith", Instant.now()
        );

        mockMvc.perform(post("/api/v1/inspections/" + UUID.randomUUID() + "/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("P0 Security: Anonymous request to assign inspection is denied with 401")
    void anonymous_assignInspection_denied() throws Exception {
        AssignTeamRequest req = new AssignTeamRequest(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), "Immediate dispatch");

        mockMvc.perform(post("/api/v1/inspections/alert/" + ALERT_ID + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 2. CITIZEN ROLE CANNOT PERFORM PRIVILEGED ACTIONS (403 Forbidden)
    // =========================================================================

    @Test
    @WithMockUser(username = "citizen@example.com", roles = {"CITIZEN"})
    @DisplayName("P0 Security: Authenticated citizen attempting alert resolution is denied with 403")
    void citizen_resolveAlert_forbidden() throws Exception {
        ResolveAlertRequest req = new ResolveAlertRequest("Citizen trying to resolve", UUID.randomUUID());

        mockMvc.perform(patch("/api/v1/alerts/" + ALERT_ID + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @WithMockUser(username = "citizen@example.com", roles = {"CITIZEN"})
    @DisplayName("P0 Security: Authenticated citizen attempting direct alert creation is denied with 403")
    void citizen_createAlert_forbidden() throws Exception {
        Alert alert = new Alert();
        alert.setCityId(CITY_ID);
        alert.setGridCellId(GRID_CELL_ID);
        alert.setTitle("Citizen Fabricated Alert");
        alert.setMessage("Fake alert injection attempt");
        alert.setSeverity("CRITICAL");

        mockMvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alert)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(username = "citizen@example.com", roles = {"CITIZEN"})
    @DisplayName("P0 Security: Authenticated citizen attempting to record action is denied with 403")
    void citizen_recordAction_forbidden() throws Exception {
        RecordActionRequest req = new RecordActionRequest(ALERT_ID, "COMMUNITY_NOTICE", "Citizen action", "Citizen User");

        mockMvc.perform(post("/api/v1/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // =========================================================================
    // 3. PRIVILEGED ROLES SUCCEED & INPUT VALIDATION IS ENFORCED
    // =========================================================================

    @Test
    @WithMockUser(username = "officer@pune.gov.in", roles = {"AUTHORITY"})
    @DisplayName("P0 Security: Authenticated authority creating valid alert succeeds with 201 Created")
    void authority_createAlert_valid_succeeds() throws Exception {
        Alert alert = new Alert();
        alert.setCityId(CITY_ID);
        alert.setGridCellId(GRID_CELL_ID);
        alert.setTitle("Valid Authority Alert");
        alert.setMessage("Elevated PM2.5 detected near industrial corridor");
        alert.setSeverity("HIGH");

        Alert saved = new Alert();
        saved.setId(UUID.randomUUID());
        saved.setCityId(CITY_ID);
        saved.setGridCellId(GRID_CELL_ID);
        saved.setTitle("Valid Authority Alert");
        saved.setMessage("Elevated PM2.5 detected near industrial corridor");
        saved.setSeverity("HIGH");
        saved.setStatus("OPEN");

        when(alertService.createAlert(any(Alert.class))).thenReturn(saved);

        mockMvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alert)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Valid Authority Alert"))
                .andExpect(jsonPath("$.severity").value("HIGH"));
    }

    @Test
    @WithMockUser(username = "officer@pune.gov.in", roles = {"AUTHORITY"})
    @DisplayName("P0 Security: Alert creation with invalid severity is rejected with 400 Bad Request")
    void authority_createAlert_invalidSeverity_rejected() throws Exception {
        Alert alert = new Alert();
        alert.setCityId(CITY_ID);
        alert.setGridCellId(GRID_CELL_ID);
        alert.setTitle("Invalid Severity Alert");
        alert.setMessage("Testing invalid severity validation");
        alert.setSeverity("SUPER_CRITICAL_INVALID");

        mockMvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alert)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "officer@pune.gov.in", roles = {"AUTHORITY"})
    @DisplayName("P0 Security: Alert creation with missing required fields is rejected with 400 Bad Request")
    void authority_createAlert_missingFields_rejected() throws Exception {
        Alert alert = new Alert();
        // Missing cityId, gridCellId, title, message, severity

        mockMvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alert)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 4. PUBLIC CITIZEN & READ ENDPOINTS REMAIN ACCESSIBLE
    // =========================================================================

    @Test
    @DisplayName("Public Access: Health check endpoint remains accessible without credentials")
    void public_healthEndpoint_accessible() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Public Access: Citizen report submission remains accessible without credentials")
    void public_citizenReportSubmission_accessible() throws Exception {
        com.aerosentinel.citizen.CitizenReport report = new com.aerosentinel.citizen.CitizenReport();
        report.setId(UUID.randomUUID());
        report.setCityId(CITY_ID);
        report.setCategory("SMOKE");
        report.setDescription("Visible smoke emissions");
        report.setLatitude(18.5204);
        report.setLongitude(73.8567);
        report.setStatus("RECEIVED");
        report.setSubmittedAt(Instant.now());

        CitizenReportResponseDto responseDto = CitizenReportResponseDto.fromEntity(report, null);

        when(citizenReportService.submitReport(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(responseDto);

        mockMvc.perform(multipart("/api/v1/citizen/reports")
                        .param("cityId", CITY_ID.toString())
                        .param("latitude", "18.5204")
                        .param("longitude", "73.8567")
                        .param("category", "SMOKE")
                        .param("description", "Visible smoke emissions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("SMOKE"));
    }

    // =========================================================================
    // 5. ANALYST ROLE CANNOT MUTATE AUTHORITY LIFECYCLE (403 Forbidden)
    // =========================================================================

    @Test
    @WithMockUser(username = "analyst@example.com", roles = {"ANALYST"})
    @DisplayName("P0 Security: Authenticated analyst attempting alert acknowledge is denied with 403")
    void analyst_acknowledgeAlert_forbidden() throws Exception {
        mockMvc.perform(patch("/api/v1/alerts/" + ALERT_ID + "/acknowledge"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @WithMockUser(username = "analyst@example.com", roles = {"ANALYST"})
    @DisplayName("P0 Security: Authenticated analyst attempting alert resolution is denied with 403")
    void analyst_resolveAlert_forbidden() throws Exception {
        ResolveAlertRequest req = new ResolveAlertRequest("Analyst resolution attempt", UUID.randomUUID());

        mockMvc.perform(patch("/api/v1/alerts/" + ALERT_ID + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @WithMockUser(username = "analyst@example.com", roles = {"ANALYST"})
    @DisplayName("P0 Security: Authenticated analyst attempting alert dismissal is denied with 403")
    void analyst_dismissAlert_forbidden() throws Exception {
        mockMvc.perform(patch("/api/v1/alerts/" + ALERT_ID + "/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dismissalReason\":\"Analyst dismissal attempt\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @WithMockUser(username = "analyst@example.com", roles = {"ANALYST"})
    @DisplayName("P0 Security: Authenticated analyst attempting team assignment is denied with 403")
    void analyst_assignFieldTeam_forbidden() throws Exception {
        AssignTeamRequest req = new AssignTeamRequest(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), "Immediate dispatch");

        mockMvc.perform(post("/api/v1/alerts/" + ALERT_ID + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @WithMockUser(username = "analyst@example.com", roles = {"ANALYST"})
    @DisplayName("P0 Security: Authenticated analyst attempting team reassignment is denied with 403")
    void analyst_reassignFieldTeam_forbidden() throws Exception {
        com.aerosentinel.dto.inspection.ReassignTeamRequest req =
                new com.aerosentinel.dto.inspection.ReassignTeamRequest(UUID.randomUUID(), "Rapid escalation", "Notes");

        mockMvc.perform(post("/api/v1/alerts/" + ALERT_ID + "/reassign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    // =========================================================================
    // 6. AUTHORITY TEAM REASSIGNMENT WORKFLOW
    // =========================================================================

    @Test
    @WithMockUser(username = "officer@pune.gov.in", roles = {"AUTHORITY"})
    @DisplayName("P0 Security: Authenticated authority reassigning field team succeeds with 200 OK")
    void authority_reassignFieldTeam_succeeds() throws Exception {
        UUID newTeamId = UUID.randomUUID();
        com.aerosentinel.dto.inspection.ReassignTeamRequest req =
                new com.aerosentinel.dto.inspection.ReassignTeamRequest(newTeamId, "Vehicle breakdown", "Dispatch team 2");

        InspectionResponseDto mockResp = new InspectionResponseDto(
                UUID.randomUUID(), ALERT_ID, newTeamId, "TEAM-02", "Team Beta",
                UUID.randomUUID(), "88608850e5fffff", UUID.randomUUID(),
                UUID.randomUUID(), Instant.now(), Instant.now(), null, null,
                null, "Reassigned: Vehicle breakdown", "ASSIGNED",
                Instant.now(), Instant.now(), null
        );

        when(inspectionService.reassignFieldTeam(eq(ALERT_ID), any(), any())).thenReturn(mockResp);

        mockMvc.perform(post("/api/v1/alerts/" + ALERT_ID + "/reassign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamName").value("Team Beta"))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    @WithMockUser(username = "officer@pune.gov.in", roles = {"AUTHORITY"})
    @DisplayName("P0 Security: Reassignment without reason is rejected with 400 Bad Request")
    void authority_reassignFieldTeam_missingReason_rejected() throws Exception {
        com.aerosentinel.dto.inspection.ReassignTeamRequest req =
                new com.aerosentinel.dto.inspection.ReassignTeamRequest(UUID.randomUUID(), "", "Notes");

        mockMvc.perform(post("/api/v1/alerts/" + ALERT_ID + "/reassign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
