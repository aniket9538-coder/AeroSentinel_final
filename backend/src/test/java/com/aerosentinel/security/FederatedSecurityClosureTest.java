package com.aerosentinel.security;

import com.aerosentinel.auth.AuthController;
import com.aerosentinel.auth.AuthResponse;
import com.aerosentinel.auth.AuthService;
import com.aerosentinel.auth.LoginRequest;
import com.aerosentinel.config.SecurityConfig;
import com.aerosentinel.dto.federated.CreateRoundRequest;
import com.aerosentinel.dto.federated.FederatedNodeResponse;
import com.aerosentinel.dto.federated.GlobalModelResponse;
import com.aerosentinel.dto.federated.RegisterNodeRequest;
import com.aerosentinel.exception.GlobalExceptionHandler;
import com.aerosentinel.federated.FederatedNodeController;
import com.aerosentinel.federated.FederatedNodeService;
import com.aerosentinel.federated.FederatedRoundController;
import com.aerosentinel.federated.FederatedRoundService;
import com.aerosentinel.federated.FederatedTrainingService;
import com.aerosentinel.federated.GlobalModelController;
import com.aerosentinel.federated.GlobalModelService;
import com.aerosentinel.federated.ModelUpdateController;
import com.aerosentinel.federated.ModelUpdateService;
import com.aerosentinel.federated.dto.RoundResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        FederatedRoundController.class,
        FederatedNodeController.class,
        GlobalModelController.class,
        ModelUpdateController.class,
        AuthController.class
})
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class FederatedSecurityClosureTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private FederatedRoundService roundService;

    @MockBean
    private FederatedNodeService nodeService;

    @MockBean
    private GlobalModelService globalModelService;

    @MockBean
    private ModelUpdateService modelUpdateService;

    @MockBean
    private FederatedTrainingService federatedTrainingService;

    @MockBean
    private AuthService authService;

    // =========================================================================
    // 1. PUBLIC READ-ONLY FEDERATED ENDPOINTS (PermitAll)
    // =========================================================================

    @Test
    @DisplayName("Public Access: Anonymous request to GET /api/v1/federated/nodes is 200 OK")
    void anonymous_getNodes_permitted() throws Exception {
        when(nodeService.getAllNodes()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/federated/nodes"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Public Access: Anonymous request to GET /api/v1/federated/models/active is 200 OK")
    void anonymous_getActiveModel_permitted() throws Exception {
        GlobalModelResponse model = new GlobalModelResponse(
                "global-v1", null, null, true, List.of("PUNE"), 0, Collections.emptyMap(), "path", Instant.now()
        );
        when(globalModelService.getActiveModel()).thenReturn(model);

        mockMvc.perform(get("/api/v1/federated/models/active"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Public Access: Anonymous request to GET /api/v1/federated/rounds is 200 OK")
    void anonymous_getRounds_permitted() throws Exception {
        when(roundService.listRounds()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/federated/rounds"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 2. STATE-MUTATING FEDERATED ACTIONS ARE DENIED TO ANONYMOUS USERS (401)
    // =========================================================================

    @Test
    @DisplayName("Security: Anonymous create training round is denied with 401")
    void anonymous_createRound_denied() throws Exception {
        CreateRoundRequest req = new CreateRoundRequest("R1", "global-v1", List.of("PUNE"), 1);

        mockMvc.perform(post("/api/v1/federated/rounds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Security: Anonymous trigger aggregation is denied with 401")
    void anonymous_aggregateRound_denied() throws Exception {
        mockMvc.perform(post("/api/v1/federated/rounds/R1/aggregate"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Security: Anonymous node registration is denied with 401")
    void anonymous_registerNode_denied() throws Exception {
        RegisterNodeRequest req = new RegisterNodeRequest("PUNE", UUID.randomUUID(), "Pune Node", "http://pune");

        mockMvc.perform(post("/api/v1/federated/nodes/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // =========================================================================
    // 3. CITIZEN ROLE CANNOT MUTATE FEDERATED TRAINING (403 Forbidden)
    // =========================================================================

    @Test
    @WithMockUser(username = "citizen@example.com", roles = {"CITIZEN"})
    @DisplayName("Security: Citizen attempting to create training round is denied with 403")
    void citizen_createRound_forbidden() throws Exception {
        CreateRoundRequest req = new CreateRoundRequest("R1", "global-v1", List.of("PUNE"), 1);

        mockMvc.perform(post("/api/v1/federated/rounds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(username = "citizen@example.com", roles = {"CITIZEN"})
    @DisplayName("Security: Citizen attempting to trigger model aggregation is denied with 403")
    void citizen_aggregateRound_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/federated/rounds/R1/aggregate"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // =========================================================================
    // 4. AUTHORITY ROLE SUCCEEDS FOR PRIVILEGED FEDERATED OPERATIONS
    // =========================================================================

    @Test
    @WithMockUser(username = "authority@aerosentinel.gov", roles = {"AUTHORITY"})
    @DisplayName("Security: Authority officer creating training round succeeds with 201")
    void authority_createRound_succeeds() throws Exception {
        RoundResponse res = new RoundResponse("R1", "global-v1", "CREATED", 1, List.of("PUNE"), Instant.now());
        when(roundService.startRound(any(), any(), any(), any())).thenReturn(res);

        CreateRoundRequest req = new CreateRoundRequest("R1", "global-v1", List.of("PUNE"), 1);

        mockMvc.perform(post("/api/v1/federated/rounds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roundId").value("R1"));
    }

    // =========================================================================
    // 5. AUTHENTICATION INTEGRITY: VALID VS INVALID CREDENTIALS
    // =========================================================================

    @Test
    @DisplayName("Auth Integrity: Correct credentials yield 200 OK with valid JWT")
    void auth_validCredentials_succeeds() throws Exception {
        AuthResponse authRes = AuthResponse.of("jwt.test.token", "authority@aerosentinel.gov", "ROLE_AUTHORITY");
        when(authService.login(any(LoginRequest.class))).thenReturn(authRes);

        LoginRequest req = new LoginRequest("authority@aerosentinel.gov", "password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt.test.token"))
                .andExpect(jsonPath("$.role").value("AUTHORITY"));
    }

    @Test
    @DisplayName("Auth Integrity: Wrong password yields 401 Unauthorized")
    void auth_invalidPassword_denied() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid username or password"));

        LoginRequest req = new LoginRequest("authority@aerosentinel.gov", "wrong_password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }
}
