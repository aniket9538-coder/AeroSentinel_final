package com.aerosentinel.air;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * F1 Phase 5 Contract Hardening and Regression Verification Test Suite.
 * Covers canonical contracts, validation error responses, entity non-exposure,
 * security boundaries, and database-to-API consistency proofs.
 */
@SpringBootTest
@AutoConfigureMockMvc
class F1ApiContractHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AirObservationRepository airObservationRepository;

    private static final String PUNE_ID = "550e8400-e29b-41d4-a716-446655440001";
    private static final String NON_EXISTENT_UUID = "00000000-0000-0000-0000-000000000000";

    @Test
    @DisplayName("Contract 1: GET /api/v1/cities/{id} -> HTTP 200 with stable CityResponse DTO and no JPA internals")
    void testGetCityDetailContract() throws Exception {
        mockMvc.perform(get("/api/v1/cities/" + PUNE_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(PUNE_ID)))
                .andExpect(jsonPath("$.name", is("Pune")))
                .andExpect(jsonPath("$.state", is("Maharashtra")))
                .andExpect(jsonPath("$.country", is("India")))
                .andExpect(jsonPath("$.timezone", is("Asia/Kolkata")))
                .andExpect(jsonPath("$.active", is(true)))
                .andExpect(jsonPath("$.latitude", is(18.5204)))
                .andExpect(jsonPath("$.longitude", is(73.8567)))
                // Verify no JPA entity leakage (e.g., Hibernate proxies, internal persistence fields)
                .andExpect(jsonPath("$.hibernateLazyInitializer").doesNotExist())
                .andExpect(jsonPath("$.handler").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("Contract 2: GET /api/v1/cities/{id} with non-existent UUID -> HTTP 404 with standard error contract")
    void testGetCityNotFoundContract() throws Exception {
        mockMvc.perform(get("/api/v1/cities/" + NON_EXISTENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("NOT_FOUND")))
                .andExpect(jsonPath("$.message", containsString("City not found with id: " + NON_EXISTENT_UUID)))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Contract 3: GET /api/v1/cities/{id} with malformed UUID -> HTTP 400 BAD_REQUEST error contract")
    void testGetCityMalformedUuidContract() throws Exception {
        mockMvc.perform(get("/api/v1/cities/not-a-valid-uuid")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")))
                .andExpect(jsonPath("$.message", containsString("Invalid parameter value for 'id'")))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Contract 4: GET /api/v1/stations/{stationId}/air-quality with malformed 'to' date -> HTTP 400 VALIDATION_ERROR")
    void testMalformedToDateReturns400Contract() throws Exception {
        mockMvc.perform(get("/api/v1/stations/PUN-001/air-quality")
                        .param("from", "2026-09-24T00:00:00Z")
                        .param("to", "malformed-to-date")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.message", containsString("Invalid 'to' timestamp format")))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Contract 5: Security regression - Canonical endpoints public, unmapped protected paths reject unauthenticated")
    void testSecurityBoundariesContract() throws Exception {
        // Public canonical endpoints accessible without authentication
        mockMvc.perform(get("/api/v1/cities"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/cities/" + PUNE_ID))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/cities/" + PUNE_ID + "/air-quality/latest"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/stations/PUN-001/air-quality")
                        .param("from", "2026-09-24T00:00:00Z")
                        .param("to", "2026-09-24T23:59:59Z"))
                .andExpect(status().isOk());

        // Unpermitted / unmapped endpoints require authentication and return 401 Unauthorized
        mockMvc.perform(get("/api/v1/unpermitted-secured-endpoint"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Contract 6: Database to API consistency - Observation in PostgreSQL matches canonical API response exactly")
    void testDatabaseToApiExactConsistency() throws Exception {
        // Query known seeded observation directly from PostgreSQL
        List<AirObservation> dbObsList = airObservationRepository.findByStationIdOrderByObservedAtDesc("PUN-001");
        assertThat(dbObsList).isNotEmpty();

        AirObservation firstDbObs = dbObsList.get(0);
        String obsTimestamp = firstDbObs.getObservedAt().toString();

        mockMvc.perform(get("/api/v1/stations/PUN-001/air-quality")
                        .param("from", obsTimestamp)
                        .param("to", obsTimestamp)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationId", is("PUN-001")))
                .andExpect(jsonPath("$.observations", hasSize(1)))
                .andExpect(jsonPath("$.observations[0].pm25", is(firstDbObs.getPm25())))
                .andExpect(jsonPath("$.observations[0].observedAt", is(obsTimestamp)))
                .andExpect(jsonPath("$.observations[0].source", is(firstDbObs.getSource())))
                .andExpect(jsonPath("$.observations[0].quality", is(firstDbObs.getDataQuality())));
    }
}
