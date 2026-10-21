package com.aerosentinel.forecast;

import com.aerosentinel.hotspot.HotspotPrediction;
import com.aerosentinel.hotspot.HotspotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "officer@pune.gov.in", roles = {"AUTHORITY"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ForecastIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ForecastIntegrationTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ForecastService forecastService;

    @Autowired
    private ForecastRepository forecastRepository;

    @Autowired
    private HotspotRepository hotspotRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID PUNE_CITY_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final String PUNE_SHIVAJINAGAR_H3 = "88608850e5fffff";

    private static UUID realParentPredictionId;

    @Test
    @Order(1)
    @DisplayName("1. Repository: Save, query, idempotency, and foreign key constraint checks for horizons [1, 3, 6]")
    void testRepositoryPersistenceAndConstraints() {
        Optional<HotspotPrediction> parentOpt = hotspotRepository.findTopByH3IndexOrderByPredictedAtDesc(PUNE_SHIVAJINAGAR_H3);
        assertThat(parentOpt).isPresent();
        HotspotPrediction parent = parentOpt.get();
        UUID parentId = parent.getId();
        UUID snapshotId = parent.getFeatureSnapshotId() != null ? parent.getFeatureSnapshotId() : UUID.randomUUID();
        realParentPredictionId = parentId;

        Instant now = Instant.now();

        // 1. Verify foreign key rejection on invalid parent prediction ID
        UUID fakeParentId = UUID.randomUUID();
        Forecast fakeForecast = new Forecast(fakeParentId, PUNE_CITY_ID, PUNE_SHIVAJINAGAR_H3, null, snapshotId,
                now, now.plus(1, ChronoUnit.HOURS), 1, 71.90, 70.06, 73.76, null, "forecast_regressors_v1", "ug/m3", "SUCCESS");
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> {
            forecastRepository.saveAndFlush(fakeForecast);
        });

        // 2. Verify rejection when featureSnapshotId is null for SUCCESS forecast
        Forecast missingSnapshotForecast = new Forecast(parentId, PUNE_CITY_ID, PUNE_SHIVAJINAGAR_H3, null, null,
                now, now.plus(1, ChronoUnit.HOURS), 1, 71.90, 70.06, 73.76, null, "forecast_regressors_v1", "ug/m3", "SUCCESS");
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
            forecastRepository.saveAndFlush(missingSnapshotForecast);
        });

        // 3. Verify valid save with real parent prediction and non-null snapshotId
        forecastRepository.deleteByParentPredictionId(parentId);
        forecastRepository.flush();

        Forecast f1 = new Forecast(parentId, PUNE_CITY_ID, PUNE_SHIVAJINAGAR_H3, null, snapshotId,
                now, now.plus(1, ChronoUnit.HOURS), 1, 71.90, 70.06, 73.76, null, "forecast_regressors_v1", "ug/m3", "SUCCESS");
        Forecast f3 = new Forecast(parentId, PUNE_CITY_ID, PUNE_SHIVAJINAGAR_H3, null, snapshotId,
                now, now.plus(3, ChronoUnit.HOURS), 3, 70.43, 66.53, 73.48, null, "forecast_regressors_v1", "ug/m3", "SUCCESS");
        Forecast f6 = new Forecast(parentId, PUNE_CITY_ID, PUNE_SHIVAJINAGAR_H3, null, snapshotId,
                now, now.plus(6, ChronoUnit.HOURS), 6, 70.55, 65.03, 75.98, null, "forecast_regressors_v1", "ug/m3", "SUCCESS");

        forecastRepository.saveAll(List.of(f1, f3, f6));

        List<Forecast> retrieved = forecastRepository.findByParentPredictionIdOrderByHorizonHoursAsc(parentId);
        assertThat(retrieved).hasSize(3);
        assertThat(retrieved.get(0).getHorizonHours()).isEqualTo(1);
        assertThat(retrieved.get(1).getHorizonHours()).isEqualTo(3);
        assertThat(retrieved.get(2).getHorizonHours()).isEqualTo(6);

        for (Forecast f : retrieved) {
            assertThat(f.getFeatureSnapshotId()).isNotNull();
            assertThat(f.getForecastConfidence()).isNull();
            assertThat(f.getUnit()).isEqualTo("ug/m3");
            assertThat(f.getStatus()).isEqualTo("SUCCESS");
            assertThat(f.getLowerBound()).isGreaterThanOrEqualTo(0.0);
            assertThat(f.getLowerBound()).isLessThanOrEqualTo(f.getPredictedPm25());
            assertThat(f.getPredictedPm25()).isLessThanOrEqualTo(f.getUpperBound());
        }
    }

    @Test
    @Order(2)
    @DisplayName("2. End-to-End: Real Pune F3 parent -> P2 feature builder -> P3 Python CLI -> DB persistence")
    void testRealPuneEndToEndForecastGeneration() {
        // Resolve real F3 parent prediction in Pune
        Optional<HotspotPrediction> parentOpt = hotspotRepository.findTopByH3IndexOrderByPredictedAtDesc(PUNE_SHIVAJINAGAR_H3);
        assertThat(parentOpt).isPresent();
        HotspotPrediction parent = parentOpt.get();
        realParentPredictionId = parent.getId();

        log.info("Found real F3 parent prediction for Pune: id={}, h3Index={}, snapshotId={}, predictedAt={}",
                parent.getId(), parent.getH3Index(), parent.getFeatureSnapshotId(), parent.getPredictedAt());

        ForecastGenerateRequest request = new ForecastGenerateRequest(
                parent.getId(),
                parent.getCityId(),
                parent.getH3Index()
        );

        // Execute orchestration
        ForecastResponse response = forecastService.generateForecast(request);

        assertThat(response).isNotNull();
        assertThat(response.h3Index()).isEqualTo(PUNE_SHIVAJINAGAR_H3);
        assertThat(response.cityId()).isEqualTo(PUNE_CITY_ID);
        assertThat(response.parentPredictionId()).isEqualTo(parent.getId());
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.modelVersion()).isEqualTo("forecast_regressors_v1");
        assertThat(response.forecastConfidence()).isNull();
        assertThat(response.freshness()).isIn("LIVE", "STALE");

        assertThat(response.forecasts()).hasSize(3);
        ForecastResponse.ForecastItem h1 = response.forecasts().get(0);
        ForecastResponse.ForecastItem h3 = response.forecasts().get(1);
        ForecastResponse.ForecastItem h6 = response.forecasts().get(2);

        assertThat(h1.horizonHours()).isEqualTo(1);
        assertThat(h3.horizonHours()).isEqualTo(3);
        assertThat(h6.horizonHours()).isEqualTo(6);

        assertThat(h1.lowerBound()).isGreaterThanOrEqualTo(0.0);
        assertThat(h1.lowerBound()).isLessThanOrEqualTo(h1.predictedPm25());
        assertThat(h1.predictedPm25()).isLessThanOrEqualTo(h1.upperBound());

        assertThat(h3.lowerBound()).isGreaterThanOrEqualTo(0.0);
        assertThat(h3.lowerBound()).isLessThanOrEqualTo(h3.predictedPm25());
        assertThat(h3.predictedPm25()).isLessThanOrEqualTo(h3.upperBound());

        assertThat(h6.lowerBound()).isGreaterThanOrEqualTo(0.0);
        assertThat(h6.lowerBound()).isLessThanOrEqualTo(h6.predictedPm25());
        assertThat(h6.predictedPm25()).isLessThanOrEqualTo(h6.upperBound());

        log.info("==================================================");
        log.info("REAL_BACKEND_FORECAST_RUNTIME_PROOF");
        log.info("parentPredictionId = {}", response.parentPredictionId());
        log.info("cityId             = {}", response.cityId());
        log.info("h3Index            = {}", response.h3Index());
        log.info("featureSnapshotId  = {}", response.featureSnapshotId());
        log.info("generatedAt        = {}", response.generatedAt());
        log.info("modelVersion       = {}", response.modelVersion());
        log.info("T+1h: pred={} [{}, {}] target={}", h1.predictedPm25(), h1.lowerBound(), h1.upperBound(), h1.targetTime());
        log.info("T+3h: pred={} [{}, {}] target={}", h3.predictedPm25(), h3.lowerBound(), h3.upperBound(), h3.targetTime());
        log.info("T+6h: pred={} [{}, {}] target={}", h6.predictedPm25(), h6.lowerBound(), h6.upperBound(), h6.targetTime());
        log.info("forecastConfidence = {}", response.forecastConfidence());
        log.info("freshness          = {}", response.freshness());
        log.info("==================================================");
    }

    @Test
    @Order(3)
    @DisplayName("3. Database Proof: Verify persisted forecast rows for real parent prediction")
    void testDatabaseProofPersistedRows() {
        assertThat(realParentPredictionId).isNotNull();

        List<Forecast> persisted = forecastRepository.findByParentPredictionIdOrderByHorizonHoursAsc(realParentPredictionId);
        assertThat(persisted).hasSize(3);

        for (Forecast row : persisted) {
            assertThat(row.getId()).isNotNull();
            assertThat(row.getParentPredictionId()).isEqualTo(realParentPredictionId);
            assertThat(row.getCityId()).isEqualTo(PUNE_CITY_ID);
            assertThat(row.getH3Index()).isEqualTo(PUNE_SHIVAJINAGAR_H3);
            assertThat(row.getModelVersion()).isEqualTo("forecast_regressors_v1");
            assertThat(row.getUnit()).isEqualTo("ug/m3");
            assertThat(row.getStatus()).isEqualTo("SUCCESS");
            assertThat(row.getForecastConfidence()).isNull();
            assertThat(row.getPredictedPm25()).isFinite();
            assertThat(row.getLowerBound()).isGreaterThanOrEqualTo(0.0);
            assertThat(row.getLowerBound()).isLessThanOrEqualTo(row.getPredictedPm25());
            assertThat(row.getPredictedPm25()).isLessThanOrEqualTo(row.getUpperBound());

            log.info("DATABASE_ROW_VERIFICATION: id={}, parentId={}, h3={}, horizon={}h, pred={}, bounds=[{}, {}], confidence={}, generatedAt={}",
                    row.getId(), row.getParentPredictionId(), row.getH3Index(), row.getHorizonHours(),
                    row.getPredictedPm25(), row.getLowerBound(), row.getUpperBound(), row.getForecastConfidence(), row.getGeneratedAt());
        }
    }

    @Test
    @Order(4)
    @DisplayName("4. REST API: GET /api/v1/forecast/{h3Index} returns canonical contract")
    void testGetForecastByH3Api() throws Exception {
        var mvcResult = mockMvc.perform(get("/api/v1/forecast/" + PUNE_SHIVAJINAGAR_H3)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.h3Index").value(PUNE_SHIVAJINAGAR_H3))
                .andExpect(jsonPath("$.cityId").value(PUNE_CITY_ID.toString()))
                .andExpect(jsonPath("$.modelVersion").value("forecast_regressors_v1"))
                .andExpect(jsonPath("$.parentPredictionId").value(realParentPredictionId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.freshness").value(isOneOf("LIVE", "STALE")))
                .andExpect(jsonPath("$.forecastConfidence").value(nullValue()))
                .andExpect(jsonPath("$.forecasts").isArray())
                .andExpect(jsonPath("$.forecasts.length()").value(3))
                .andExpect(jsonPath("$.forecasts[0].horizonHours").value(1))
                .andExpect(jsonPath("$.forecasts[0].predictedPm25").isNumber())
                .andExpect(jsonPath("$.forecasts[0].lowerBound").isNumber())
                .andExpect(jsonPath("$.forecasts[0].upperBound").isNumber())
                .andExpect(jsonPath("$.forecasts[0].unit").value("ug/m3"))
                .andExpect(jsonPath("$.forecasts[1].horizonHours").value(3))
                .andExpect(jsonPath("$.forecasts[2].horizonHours").value(6))
                .andReturn();

        log.info("API_RUNTIME_RESPONSE_EXCERPT: {}", mvcResult.getResponse().getContentAsString());
    }

    @Test
    @Order(5)
    @DisplayName("5. REST API: GET /api/v1/forecast/{h3Index} returns 404 NO_DATA for unknown cell")
    void testGetForecastNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/forecast/886088500000000")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("NO_DATA"))
                .andExpect(jsonPath("$.message").value(containsString("No forecast data found")));
    }

    @Test
    @Order(6)
    @DisplayName("6. REST API: POST /api/v1/forecast/generate with invalid parent returns 404")
    void testPostGenerateNotFound() throws Exception {
        UUID nonExistentParentId = UUID.randomUUID();
        ForecastGenerateRequest request = new ForecastGenerateRequest(nonExistentParentId, PUNE_CITY_ID, PUNE_SHIVAJINAGAR_H3);

        mockMvc.perform(post("/api/v1/forecast/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("FORECAST_PARENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(containsString("Parent prediction not found")));
    }

    @Test
    @Order(7)
    @DisplayName("7. REST API: GET /api/v1/forecast/parent/{parentId} returns persisted forecast")
    void testGetForecastByParentId() throws Exception {
        assertThat(realParentPredictionId).isNotNull();

        mockMvc.perform(get("/api/v1/forecast/parent/" + realParentPredictionId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentPredictionId").value(realParentPredictionId.toString()))
                .andExpect(jsonPath("$.h3Index").value(PUNE_SHIVAJINAGAR_H3))
                .andExpect(jsonPath("$.forecasts.length()").value(3));
    }

    @Test
    @Order(8)
    @DisplayName("8. Idempotency: Repeated generation updates existing records without duplicate key collision")
    void testIdempotencyRepeatedGeneration() {
        assertThat(realParentPredictionId).isNotNull();

        ForecastGenerateRequest request = new ForecastGenerateRequest(
                realParentPredictionId,
                PUNE_CITY_ID,
                PUNE_SHIVAJINAGAR_H3
        );

        // Run second generation
        ForecastResponse response2 = forecastService.generateForecast(request);
        assertThat(response2).isNotNull();
        assertThat(response2.parentPredictionId()).isEqualTo(realParentPredictionId);

        // Verify still exactly 3 rows in database
        List<Forecast> rows = forecastRepository.findByParentPredictionIdOrderByHorizonHoursAsc(realParentPredictionId);
        assertThat(rows).hasSize(3);
    }

    @Test
    @Order(9)
    @DisplayName("9. Lineage Validation: POST /api/v1/forecast/generate with mismatched cityId is rejected with 422 FORECAST_PARENT_CONTEXT_MISMATCH")
    void testParentCityMismatchRejectedWith422() throws Exception {
        assertThat(realParentPredictionId).isNotNull();

        UUID wrongCityId = UUID.randomUUID();
        ForecastGenerateRequest request = new ForecastGenerateRequest(
                realParentPredictionId,
                wrongCityId,
                PUNE_SHIVAJINAGAR_H3
        );

        mockMvc.perform(post("/api/v1/forecast/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("FORECAST_PARENT_CONTEXT_MISMATCH"))
                .andExpect(jsonPath("$.message").value(containsString("does not match authoritative F3 parent prediction cityId")));
    }

    @Test
    @Order(10)
    @DisplayName("10. Lineage Validation: POST /api/v1/forecast/generate with mismatched h3Index is rejected with 422 FORECAST_PARENT_CONTEXT_MISMATCH")
    void testParentH3MismatchRejectedWith422() throws Exception {
        assertThat(realParentPredictionId).isNotNull();

        String wrongH3 = "886088500000000";
        ForecastGenerateRequest request = new ForecastGenerateRequest(
                realParentPredictionId,
                PUNE_CITY_ID,
                wrongH3
        );

        mockMvc.perform(post("/api/v1/forecast/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("FORECAST_PARENT_CONTEXT_MISMATCH"))
                .andExpect(jsonPath("$.message").value(containsString("does not match authoritative F3 parent prediction h3Index")));
    }

    @Test
    @Order(11)
    @DisplayName("11. Numerical Parity: Exact interval preservation from P3 artifact residuals without independent recalculation")
    void testNumericalParityWithP3ArtifactResiduals() {
        assertThat(realParentPredictionId).isNotNull();

        List<Forecast> rows = forecastRepository.findByParentPredictionIdOrderByHorizonHoursAsc(realParentPredictionId);
        assertThat(rows).hasSize(3);

        Forecast h1 = rows.get(0);
        Forecast h3 = rows.get(1);
        Forecast h6 = rows.get(2);

        // H1 residual: P10 = -1.84, P90 = +1.86
        double expectedLowerH1 = Math.max(0.0, Math.round((h1.getPredictedPm25() - 1.8424755) * 100.0) / 100.0);
        double expectedUpperH1 = Math.round((h1.getPredictedPm25() + 1.8628680) * 100.0) / 100.0;
        assertThat(h1.getLowerBound()).isEqualTo(expectedLowerH1);
        assertThat(h1.getUpperBound()).isEqualTo(expectedUpperH1);

        // H3 residual: P10 = -3.90, P90 = +3.05
        double expectedLowerH3 = Math.max(0.0, Math.round((h3.getPredictedPm25() - 3.9013645) * 100.0) / 100.0);
        double expectedUpperH3 = Math.round((h3.getPredictedPm25() + 3.0473582) * 100.0) / 100.0;
        assertThat(h3.getLowerBound()).isEqualTo(expectedLowerH3);
        assertThat(h3.getUpperBound()).isEqualTo(expectedUpperH3);

        // H6 residual: P10 = -5.52, P90 = +5.42
        double expectedLowerH6 = Math.max(0.0, Math.round((h6.getPredictedPm25() - 5.5207627) * 100.0) / 100.0);
        double expectedUpperH6 = Math.round((h6.getPredictedPm25() + 5.4248675) * 100.0) / 100.0;
        assertThat(h6.getLowerBound()).isEqualTo(expectedLowerH6);
        assertThat(h6.getUpperBound()).isEqualTo(expectedUpperH6);
    }
}
