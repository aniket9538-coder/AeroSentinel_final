package com.aerosentinel.evidence;

import com.aerosentinel.config.SecurityConfig;
import com.aerosentinel.exception.GlobalExceptionHandler;
import com.aerosentinel.security.CustomUserDetailsService;
import com.aerosentinel.security.JwtAuthenticationFilter;
import com.aerosentinel.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EvidenceController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class EvidenceTriageSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private EvidenceService evidenceService;

    @MockBean
    private EvidenceOrchestrationService orchestrationService;

    @MockBean
    private EvidenceFusionService evidenceFusionService;

    private static final String PUNE_H3 = "88608850e5fffff";

    private TriageCandidateDto createDummyCandidate() {
        return new TriageCandidateDto(
                "cand-1234",
                PUNE_H3,
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                "Pune",
                "AIR_POLLUTION",
                "PROVISIONAL_TRIAGE",
                "LOW",
                0.35,
                0.25,
                null,
                1,
                0,
                true,
                List.of(),
                List.of(),
                List.of("Provisional citizen observation"),
                java.util.Map.of("CITIZEN_SUBMITTED", "1 citizen observation"),
                "f5_evidence_fusion_v1",
                Instant.now()
        );
    }

    @Test
    @DisplayName("F5 Security: Anonymous request to evidence triage candidate is rejected with 401 Unauthorized")
    void testAnonymousTriageAccessDenied() throws Exception {
        mockMvc.perform(get("/api/v1/evidence/triage/h3/" + PUNE_H3)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("F5 Security: Citizen role is forbidden (403) from accessing evidence triage dossiers")
    @WithMockUser(username = "citizen1", roles = {"CITIZEN"})
    void testCitizenRoleTriageAccessForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/evidence/triage/h3/" + PUNE_H3)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("F5 Security: Citizen role is forbidden (403) from triggering evidence fusion & persistence")
    @WithMockUser(username = "citizen1", roles = {"CITIZEN"})
    void testCitizenRoleFuseForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/evidence/triage/fuse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"h3Index\":\"" + PUNE_H3 + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("F5 Security: Authority role can access explainable evidence triage candidates")
    @WithMockUser(username = "authority_officer", roles = {"AUTHORITY"})
    void testAuthorityRoleTriageAccessGranted() throws Exception {
        when(evidenceFusionService.evaluateTriageCandidate(anyString())).thenReturn(createDummyCandidate());

        mockMvc.perform(get("/api/v1/evidence/triage/h3/" + PUNE_H3)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.h3Index").value(PUNE_H3))
                .andExpect(jsonPath("$.triageStatus").value("PROVISIONAL_TRIAGE"))
                .andExpect(jsonPath("$.isProvisional").value(true));
    }

    @Test
    @DisplayName("F5 Security: Analyst role can access explainable evidence triage candidates")
    @WithMockUser(username = "analyst1", roles = {"ANALYST"})
    void testAnalystRoleTriageAccessGranted() throws Exception {
        when(evidenceFusionService.evaluateTriageCandidate(anyString())).thenReturn(createDummyCandidate());

        mockMvc.perform(get("/api/v1/evidence/triage/h3/" + PUNE_H3)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.h3Index").value(PUNE_H3))
                .andExpect(jsonPath("$.policyVersion").value("f5_evidence_fusion_v1"));
    }

    @Test
    @DisplayName("F5 Security: Authority role can trigger evidence fusion & persistence")
    @WithMockUser(username = "authority_officer", roles = {"AUTHORITY"})
    void testAuthorityRoleFuseGranted() throws Exception {
        when(evidenceFusionService.fuseAndPersist(anyString())).thenReturn(createDummyCandidate());

        mockMvc.perform(post("/api/v1/evidence/triage/fuse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"h3Index\":\"" + PUNE_H3 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateId").value("cand-1234"));
    }
}
