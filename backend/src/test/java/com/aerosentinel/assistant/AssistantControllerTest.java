package com.aerosentinel.assistant;

import com.aerosentinel.assistant.controller.AssistantController;
import com.aerosentinel.assistant.dto.AssistantChatRequest;
import com.aerosentinel.assistant.dto.AssistantChatResponse;
import com.aerosentinel.assistant.dto.LocationDto;
import com.aerosentinel.assistant.service.AssistantOrchestrationService;
import com.aerosentinel.assistant.service.AssistantRateLimiter;
import com.aerosentinel.assistant.service.ClientIpResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssistantControllerTest {

    private AssistantOrchestrationService orchestrationService;
    private AssistantRateLimiter rateLimiter;
    private ClientIpResolver clientIpResolver;
    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        orchestrationService = Mockito.mock(AssistantOrchestrationService.class);
        rateLimiter = new AssistantRateLimiter(5);
        clientIpResolver = new ClientIpResolver(""); // Default strict direct mode (no trusted proxies)
        mockMvc = MockMvcBuilders.standaloneSetup(new AssistantController(orchestrationService, rateLimiter, clientIpResolver)).build();
    }

    @Test
    @DisplayName("Assistant Controller: Valid chat request returns 200 OK with AssistantChatResponse")
    void testChatEndpoint_Success() throws Exception {
        AssistantChatResponse response = new AssistantChatResponse(
                "Current temperature in Pune is 25.0°C.",
                "en",
                List.of(),
                "2026-10-18T12:00:00Z",
                "SUCCESS",
                List.of(),
                "test-corr-id",
                "2026-10-18T12:00:00Z",
                null
        );
        when(orchestrationService.processChat(any(), anyString())).thenReturn(response);

        AssistantChatRequest request = new AssistantChatRequest("Weather in Pune", new LocationDto("Pune", 18.53, 73.84, null), "en");

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.answer").value("Current temperature in Pune is 25.0°C."))
                .andExpect(jsonPath("$.correlationId").value("test-corr-id"));
    }

    @Test
    @DisplayName("Assistant Controller: Rate limit exceeded returns HTTP 429 and Retry-After header")
    void testChatEndpoint_RateLimitExceeded() throws Exception {
        AssistantChatRequest request = new AssistantChatRequest("Weather in Pune", new LocationDto("Pune", 18.53, 73.84, null), "en");

        // Send 5 permitted requests from client
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/assistant/chat")
                            .with(req -> {
                                req.setRemoteAddr("198.51.100.2");
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        // 6th request from same IP must receive HTTP 429 Too Many Requests
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .with(req -> {
                            req.setRemoteAddr("198.51.100.2");
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"));
    }

    @Test
    @DisplayName("Security: Attacker rotating X-Forwarded-For headers is strictly rate limited by remoteAddr")
    void testChatEndpoint_RotatingXForwardedForCannotBypassRateLimit() throws Exception {
        AssistantChatRequest request = new AssistantChatRequest("Weather in Pune", new LocationDto("Pune", 18.53, 73.84, null), "en");

        // Attacker sends 5 requests from same direct IP (198.51.100.5) but rotates X-Forwarded-For to different IPs
        String[] forgedHops = {"1.1.1.1", "2.2.2.2", "3.3.3.3", "4.4.4.4", "5.5.5.5"};
        for (String forgedIp : forgedHops) {
            mockMvc.perform(post("/api/v1/assistant/chat")
                            .with(req -> {
                                req.setRemoteAddr("198.51.100.5");
                                return req;
                            })
                            .header("X-Forwarded-For", forgedIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        // 6th request with yet another forged IP MUST be rejected because server-observed remoteAddr is used
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .with(req -> {
                            req.setRemoteAddr("198.51.100.5");
                            return req;
                        })
                        .header("X-Forwarded-For", "6.6.6.6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"));
    }

    @Test
    @DisplayName("Trusted Proxy: Legitimate forwarded client IP is tracked when proxy is trusted")
    void testChatEndpoint_TrustedProxyEnforcesRateLimitOnForwardedClient() throws Exception {
        AssistantRateLimiter proxyLimiter = new AssistantRateLimiter(5);
        ClientIpResolver trustedResolver = new ClientIpResolver("10.0.0.1");
        MockMvc proxyMockMvc = MockMvcBuilders.standaloneSetup(
                new AssistantController(orchestrationService, proxyLimiter, trustedResolver)
        ).build();

        AssistantChatRequest request = new AssistantChatRequest("Weather in Pune", new LocationDto("Pune", 18.53, 73.84, null), "en");

        // 5 requests from client 203.0.113.195 through trusted proxy 10.0.0.1
        for (int i = 0; i < 5; i++) {
            proxyMockMvc.perform(post("/api/v1/assistant/chat")
                            .with(req -> {
                                req.setRemoteAddr("10.0.0.1");
                                return req;
                            })
                            .header("X-Forwarded-For", "203.0.113.195")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        // 6th request from 203.0.113.195 must be rate limited
        proxyMockMvc.perform(post("/api/v1/assistant/chat")
                        .with(req -> {
                            req.setRemoteAddr("10.0.0.1");
                            return req;
                        })
                        .header("X-Forwarded-For", "203.0.113.195")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));

        // A different client 203.0.113.200 through the same proxy must still be allowed
        proxyMockMvc.perform(post("/api/v1/assistant/chat")
                        .with(req -> {
                            req.setRemoteAddr("10.0.0.1");
                            return req;
                        })
                        .header("X-Forwarded-For", "203.0.113.200")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Assistant Controller: Empty message returns 400 Bad Request")
    void testChatEndpoint_EmptyMessageBadRequest() throws Exception {
        AssistantChatRequest request = new AssistantChatRequest("", null, "en");

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Assistant Controller: Health endpoint returns 200 UP")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/assistant/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
