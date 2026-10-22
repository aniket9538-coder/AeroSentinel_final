package com.aerosentinel.admin;

import com.aerosentinel.admin.dto.*;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminObservabilityController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
public class AdminObservabilitySecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminObservabilityService observabilityService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("Security: Anonymous requests to /api/v1/admin/observability/overview are rejected with 401")
    void testAnonymousAccessRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/observability/overview")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "citizen_user", roles = {"CITIZEN"})
    @DisplayName("Security: Citizen role is forbidden from admin observability with 403")
    void testCitizenAccessForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/observability/overview")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "analyst_user", roles = {"ANALYST"})
    @DisplayName("Security: Analyst role is forbidden from admin observability with 403")
    void testAnalystAccessForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/observability/overview")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "authority_officer", roles = {"AUTHORITY"})
    @DisplayName("Security: Authority role is decoupled and forbidden from admin observability with 403")
    void testAuthorityAccessForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/observability/overview")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    @DisplayName("Security: Admin role is authorized to access /overview with 200")
    void testAdminAccessAllowedToOverview() throws Exception {
        when(observabilityService.getAdminOverview()).thenReturn(new AdminOverviewDto(
                "HEALTHY",
                Instant.now(),
                Map.of("backend", "UP", "database", "UP", "aiService", "UP"),
                new AdminOverviewDto.RecordCounts(100, 50, 10, 5, 2, 3, 20),
                new AdminOverviewDto.MonitoringGapContext(5.0, 7.0, 10, "F8-P3-v1.0", "Notice"),
                Collections.emptyList()
        ));

        mockMvc.perform(get("/api/v1/admin/observability/overview")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    @DisplayName("Security: Admin role is authorized to access /services with 200")
    void testAdminAccessAllowedToServices() throws Exception {
        when(observabilityService.getServiceHealth()).thenReturn(new ServiceHealthDto(
                "HEALTHY",
                Instant.now(),
                120,
                new ServiceHealthDto.BackendDetails("UP", "backend", "21", 500, 1000, 2000, 15),
                new ServiceHealthDto.DatabaseDetails("UP", "PostgreSQL", true, 2, 8, 10, 0, Map.of()),
                new ServiceHealthDto.AiServiceDetails("UP", "http://localhost:8000", 25, true, List.of("hotspot"), null)
        ));

        mockMvc.perform(get("/api/v1/admin/observability/services")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    @DisplayName("Security: Admin role is authorized to access /providers with 200")
    void testAdminAccessAllowedToProviders() throws Exception {
        when(observabilityService.getProviderObservability()).thenReturn(new ProviderObservabilityDto(
                Instant.now(),
                3,
                5,
                1500,
                Collections.emptyList()
        ));

        mockMvc.perform(get("/api/v1/admin/observability/providers")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    @DisplayName("Security: Admin role is authorized to access /models with 200")
    void testAdminAccessAllowedToModels() throws Exception {
        when(observabilityService.getModelObservability()).thenReturn(new ModelObservabilityDto(
                Instant.now(),
                true,
                4,
                4,
                Collections.emptyList()
        ));

        mockMvc.perform(get("/api/v1/admin/observability/models")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    @DisplayName("Security: Admin role is authorized to access /audit-events with 200")
    void testAdminAccessAllowedToAuditEvents() throws Exception {
        when(observabilityService.getSafeAuditEvents()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/admin/observability/audit-events")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
