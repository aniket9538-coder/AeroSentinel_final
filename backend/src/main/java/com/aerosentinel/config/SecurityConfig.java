package com.aerosentinel.config;

import com.aerosentinel.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {})
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required: " +
                            authException.getMessage() + "\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied: " +
                            accessDeniedException.getMessage() + "\"}");
                })
            )
            .authorizeHttpRequests(auth -> auth
                // 1. Health & Authentication
                .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()

                // 2. Public Read-Only Environmental & Geospatial Data
                .requestMatchers(HttpMethod.GET, "/api/v1/cities/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stations/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/sensors/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/air/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/weather/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/fires/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/satellite/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/grid", "/api/v1/grid/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/hotspots/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/forecast/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/events", "/api/v1/events/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/monitoring/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/evidence/hotspot/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/federated/nodes", "/api/v1/federated/nodes/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/federated/models/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/federated/rounds", "/api/v1/federated/rounds/**").permitAll()

                // 3. Citizen Environmental Report Submissions, Status Queries, Preferences & Subscriptions
                .requestMatchers(HttpMethod.POST, "/api/v1/citizen/reports").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/citizen/reports/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/citizen/photos/**").permitAll()
                .requestMatchers("/api/v1/citizen/preferences/**").permitAll()
                .requestMatchers("/api/v1/citizen/subscriptions/**").permitAll()
                .requestMatchers("/api/v1/citizen/notifications/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/assistant/chat").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/assistant/**").permitAll()

                // 4. Privileged Authority Operations (Authority, Admin & Analyst Roles Required)
                .requestMatchers("/api/v1/evidence/triage/**").hasAnyRole("AUTHORITY", "ADMIN", "ANALYST")
                .requestMatchers("/api/v1/evidence/**").hasAnyRole("AUTHORITY", "ADMIN", "ANALYST")
                .requestMatchers(HttpMethod.GET, "/api/v1/alerts/authority", "/api/v1/alerts/authority/**").hasAnyRole("AUTHORITY", "ADMIN", "ANALYST")
                .requestMatchers(HttpMethod.GET, "/api/v1/alerts", "/api/v1/alerts/**").hasAnyRole("AUTHORITY", "ADMIN", "ANALYST")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/alerts/**").hasAnyRole("AUTHORITY", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/alerts/**").hasAnyRole("AUTHORITY", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/alerts").hasAnyRole("AUTHORITY", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/actions/**").hasAnyRole("AUTHORITY", "ADMIN", "ANALYST")
                .requestMatchers("/api/v1/actions/**").hasAnyRole("AUTHORITY", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/inspections/**").hasAnyRole("AUTHORITY", "ADMIN", "ANALYST")
                .requestMatchers("/api/v1/inspections/**").hasAnyRole("AUTHORITY", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/field-teams/**").hasAnyRole("AUTHORITY", "ADMIN", "ANALYST")
                .requestMatchers("/api/v1/field-teams/**").hasAnyRole("AUTHORITY", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/events/*/actions").hasAnyRole("AUTHORITY", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/events/*/resolve").hasAnyRole("AUTHORITY", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/federated/rounds/**").hasAnyRole("AUTHORITY", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/federated/nodes/**").hasAnyRole("AUTHORITY", "ADMIN")

                // 5. Admin-Only Operations
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/actuator/**").hasRole("ADMIN")

                // 6. Fail-closed: All other routes require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
