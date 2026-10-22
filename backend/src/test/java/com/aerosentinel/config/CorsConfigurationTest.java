package com.aerosentinel.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigurationTest {

    @Test
    @DisplayName("CORS: Exact production origin https://aero-sentinel-final.vercel.app is allowed")
    void exactProductionOrigin_isAllowed() {
        CorsConfig corsConfig = new CorsConfig();
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cities");
        request.addHeader("Origin", "https://aero-sentinel-final.vercel.app");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertThat(config).isNotNull();
        assertThat(config.checkOrigin("https://aero-sentinel-final.vercel.app")).isEqualTo("https://aero-sentinel-final.vercel.app");
        assertThat(config.getAllowCredentials()).isTrue();
    }

    @Test
    @DisplayName("CORS: Local development origins are allowed")
    void localhostOrigins_areAllowed() {
        CorsConfig corsConfig = new CorsConfig();
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cities");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertThat(config).isNotNull();
        assertThat(config.checkOrigin("http://localhost:3000")).isEqualTo("http://localhost:3000");
        assertThat(config.checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173");
        assertThat(config.checkOrigin("http://127.0.0.1:3000")).isEqualTo("http://127.0.0.1:3000");
    }

    @Test
    @DisplayName("CORS: Arbitrary or malicious origins are rejected")
    void unapprovedOrigin_isDenied() {
        CorsConfig corsConfig = new CorsConfig();
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cities");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertThat(config).isNotNull();
        assertThat(config.checkOrigin("https://malicious-site.example.com")).isNull();
        assertThat(config.checkOrigin("https://random-tenant.vercel.app")).isNull();
        assertThat(config.checkOrigin("http://evil-localhost.attacker.com")).isNull();
    }

    @Test
    @DisplayName("CORS: Configured preview origin via app.cors.allowed-origins is allowed")
    void configuredPreviewOrigin_isAllowed() {
        CorsConfig corsConfig = new CorsConfig();
        ReflectionTestUtils.setField(corsConfig, "configuredAllowedOrigins", "https://preview-pr-42.vercel.app, https://staging.aerosentinel.org");
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cities");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertThat(config).isNotNull();
        assertThat(config.checkOrigin("https://preview-pr-42.vercel.app")).isEqualTo("https://preview-pr-42.vercel.app");
        assertThat(config.checkOrigin("https://staging.aerosentinel.org")).isEqualTo("https://staging.aerosentinel.org");
        assertThat(config.checkOrigin("https://other-unapproved.vercel.app")).isNull();
    }

    @Test
    @DisplayName("CORS: Preflight configuration exposes required headers and methods")
    void preflightConfiguration_hasAllowedMethodsAndHeaders() {
        CorsConfig corsConfig = new CorsConfig();
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/cities");
        request.addHeader("Origin", "https://aero-sentinel-final.vercel.app");
        request.addHeader("Access-Control-Request-Method", "POST");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertThat(config).isNotNull();
        assertThat(config.getAllowedMethods()).contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(config.getAllowedHeaders()).contains("Authorization", "Content-Type", "Accept", "Origin");
        assertThat(config.getAllowCredentials()).isTrue();
    }
}
