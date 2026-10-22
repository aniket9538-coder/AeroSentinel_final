package com.aerosentinel.monitoring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MonitoringControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.aerosentinel.hotspot.HotspotRepository hotspotRepository;

    @Autowired
    private com.aerosentinel.forecast.ForecastRepository forecastRepository;

    @Autowired
    private com.aerosentinel.grid.GridService gridService;

    @Autowired
    private com.aerosentinel.feature.FeatureSnapshotRepository featureSnapshotRepository;

    private static final UUID PUNE_CITY_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID MUMBAI_CITY_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
    private static final UUID UNMONITORED_CITY_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440999");

    // Real Pune seeded H3 cell around Shivajinagar
    private static final String PUNE_SHIVAJINAGAR_H3 = "88608850e5fffff";
    // Real Pune cell around Katraj
    private static final String PUNE_KATRAJ_H3 = "88608852c1fffff";
    // Real Pune cell around Hadapsar
    private static final String PUNE_HADAPSAR_H3 = "8860885357fffff";
    // Distant valid H3 in Pune region (> 7 km from all 3 central stations)
    private static final String PUNE_DISTANT_H3 = "8860884119fffff";

    private static boolean seeded = false;

    @org.junit.jupiter.api.BeforeEach
    void setUpData() {
        if (seeded) return;
        seeded = true;

        var cellShivajinagar = gridService.getOrCreateGridCell(PUNE_SHIVAJINAGAR_H3, PUNE_CITY_ID);
        var cellKatraj = gridService.getOrCreateGridCell(PUNE_KATRAJ_H3, PUNE_CITY_ID);
        var cellDistant = gridService.getOrCreateGridCell(PUNE_DISTANT_H3, PUNE_CITY_ID);

        java.time.Instant future = java.time.Instant.now().plus(java.time.Duration.ofDays(30));

        var snap = new com.aerosentinel.feature.FeatureSnapshot();
        snap.setCityId(PUNE_CITY_ID);
        snap.setH3Index(PUNE_SHIVAJINAGAR_H3);
        snap.setObservedAt(future);
        snap.setFeatures("{}");
        snap = featureSnapshotRepository.save(snap);
        UUID snapId = snap.getId();

        // Shivajinagar
        var pA = new com.aerosentinel.hotspot.HotspotPrediction();
        pA.setCityId(PUNE_CITY_ID);
        pA.setH3Index(PUNE_SHIVAJINAGAR_H3);
        pA.setGridCellId(cellShivajinagar.getId());
        pA.setFeatureSnapshotId(snapId);
        pA.setRiskScore(0.15);
        pA.setRiskLevel("LOW");
        pA.setConfidence(0.85);
        pA.setModelVersion("hotspot_classifier_v1");
        pA.setExplanationStatus("COMPLETED");
        pA.setPredictedAt(future);
        pA = hotspotRepository.save(pA);

        var fA = new com.aerosentinel.forecast.Forecast(
                pA.getId(), PUNE_CITY_ID, PUNE_SHIVAJINAGAR_H3, cellShivajinagar.getId(), snapId,
                future, future.plus(1, java.time.temporal.ChronoUnit.HOURS), 1,
                28.0, 23.0, 33.0, null, "forecast_regressors_v1", "ug/m3", "SUCCESS"
        );
        forecastRepository.save(fA);

        // Katraj
        var pB = new com.aerosentinel.hotspot.HotspotPrediction();
        pB.setCityId(PUNE_CITY_ID);
        pB.setH3Index(PUNE_KATRAJ_H3);
        pB.setGridCellId(cellKatraj.getId());
        pB.setFeatureSnapshotId(snapId);
        pB.setRiskScore(0.58);
        pB.setRiskLevel("MEDIUM");
        pB.setConfidence(0.70);
        pB.setModelVersion("hotspot_classifier_v1");
        pB.setExplanationStatus("COMPLETED");
        pB.setPredictedAt(future);
        pB = hotspotRepository.save(pB);

        var fB = new com.aerosentinel.forecast.Forecast(
                pB.getId(), PUNE_CITY_ID, PUNE_KATRAJ_H3, cellKatraj.getId(), snapId,
                future, future.plus(1, java.time.temporal.ChronoUnit.HOURS), 1,
                68.0, 60.5, 75.5, null, "forecast_regressors_v1", "ug/m3", "SUCCESS"
        );
        forecastRepository.save(fB);

        // Distant
        var pC = new com.aerosentinel.hotspot.HotspotPrediction();
        pC.setCityId(PUNE_CITY_ID);
        pC.setH3Index(PUNE_DISTANT_H3);
        pC.setGridCellId(cellDistant.getId());
        pC.setFeatureSnapshotId(snapId);
        pC.setRiskScore(0.88);
        pC.setRiskLevel("HIGH");
        pC.setConfidence(0.90);
        pC.setModelVersion("hotspot_classifier_v1");
        pC.setExplanationStatus("COMPLETED");
        pC.setPredictedAt(future);
        pC = hotspotRepository.save(pC);

        var fC = new com.aerosentinel.forecast.Forecast(
                pC.getId(), PUNE_CITY_ID, PUNE_DISTANT_H3, cellDistant.getId(), snapId,
                future, future.plus(1, java.time.temporal.ChronoUnit.HOURS), 1,
                135.0, 125.0, 145.0, null, "forecast_regressors_v1", "ug/m3", "SUCCESS"
        );
        forecastRepository.save(fC);
    }

    @Test
    @Order(1)
    @DisplayName("1. Real Pune cell returns Shivajinagar station with valid distance and no coverage gap")
    void testGetCoverageForShivajinagar() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/coverage/{h3Index}", PUNE_SHIVAJINAGAR_H3)
                        .param("cityId", PUNE_CITY_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.h3Index").value(PUNE_SHIVAJINAGAR_H3))
                .andExpect(jsonPath("$.latitude").isNumber())
                .andExpect(jsonPath("$.longitude").isNumber())
                .andExpect(jsonPath("$.nearestStationCode").value("PUN-001"))
                .andExpect(jsonPath("$.nearestStationName").value("Shivajinagar CAAQMS"))
                .andExpect(jsonPath("$.nearestStationDistanceKm").value(closeTo(0.27, 0.05)))
                .andExpect(jsonPath("$.stationsWithin5kmCount").value(1))
                .andExpect(jsonPath("$.monitoringCoverageGapFlag").value(0));
    }

    @Test
    @Order(2)
    @DisplayName("2. Omitting cityId defaults to Pune and successfully returns nearest station")
    void testGetCoverageDefaultCityId() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/coverage/{h3Index}", PUNE_SHIVAJINAGAR_H3)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.h3Index").value(PUNE_SHIVAJINAGAR_H3))
                .andExpect(jsonPath("$.nearestStationCode").value("PUN-001"));
    }

    @Test
    @Order(3)
    @DisplayName("3. Real Pune Katraj cell resolves Katraj station as nearest")
    void testGetCoverageForKatraj() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/coverage/{h3Index}", PUNE_KATRAJ_H3)
                        .param("cityId", PUNE_CITY_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.h3Index").value(PUNE_KATRAJ_H3))
                .andExpect(jsonPath("$.nearestStationCode").value("PUN-002"))
                .andExpect(jsonPath("$.nearestStationName").value("Katraj Air Station"))
                .andExpect(jsonPath("$.nearestStationDistanceKm").value(closeTo(0.45, 0.05)))
                .andExpect(jsonPath("$.monitoringCoverageGapFlag").value(0));
    }

    @Test
    @Order(4)
    @DisplayName("4. Real Pune Hadapsar cell resolves Hadapsar station as nearest")
    void testGetCoverageForHadapsar() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/coverage/{h3Index}", PUNE_HADAPSAR_H3)
                        .param("cityId", PUNE_CITY_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.h3Index").value(PUNE_HADAPSAR_H3))
                .andExpect(jsonPath("$.nearestStationCode").value("PUN-003"))
                .andExpect(jsonPath("$.nearestStationName").value("Hadapsar Industrial Zone"))
                .andExpect(jsonPath("$.nearestStationDistanceKm").value(closeTo(0.40, 0.05)))
                .andExpect(jsonPath("$.monitoringCoverageGapFlag").value(0));
    }

    @Test
    @Order(5)
    @DisplayName("5. Distant H3 cell (> 7km from stations) correctly triggers coverage gap flag = 1")
    void testGetCoverageForDistantCell() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/coverage/{h3Index}", PUNE_DISTANT_H3)
                        .param("cityId", PUNE_CITY_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.h3Index").value(PUNE_DISTANT_H3))
                .andExpect(jsonPath("$.nearestStationDistanceKm").value(greaterThan(7.0)))
                .andExpect(jsonPath("$.stationsWithin5kmCount").value(0))
                .andExpect(jsonPath("$.monitoringCoverageGapFlag").value(1));
    }

    @Test
    @Order(6)
    @DisplayName("6. City with no active stations (Mumbai) returns safe no-coverage response without fake station or zero distance")
    void testGetCoverageCityWithNoStations() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/coverage/{h3Index}", PUNE_SHIVAJINAGAR_H3)
                        .param("cityId", UNMONITORED_CITY_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.h3Index").value(PUNE_SHIVAJINAGAR_H3))
                .andExpect(jsonPath("$.nearestStationId").doesNotExist())
                .andExpect(jsonPath("$.nearestStationCode").doesNotExist())
                .andExpect(jsonPath("$.nearestStationDistanceKm").doesNotExist())
                .andExpect(jsonPath("$.stationsWithin5kmCount").value(0))
                .andExpect(jsonPath("$.monitoringCoverageGapFlag").value(1));
    }

    @Test
    @Order(7)
    @DisplayName("7. Invalid H3 index returns 400 Bad Request with standard validation error")
    void testInvalidH3ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/coverage/{h3Index}", "invalid-h3-index")
                        .param("cityId", PUNE_CITY_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("Invalid H3 index")));
    }

    @Test
    @Order(8)
    @DisplayName("8. Priority endpoint returns 400 Bad Request for invalid H3 index")
    void testPriorityInvalidH3ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/priority/{h3Index}", "invalid-h3-index")
                        .param("cityId", PUNE_CITY_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("Invalid H3 index")));
    }

    @Test
    @Order(9)
    @DisplayName("9. Priority endpoint returns 404 Not Found when no F3 prediction exists for H3 cell")
    void testPriorityNotFoundWhenNoPrediction() throws Exception {
        // Valid H3 cell without F3 prediction in DB
        String unpopulatedH3 = "8860884111fffff";
        mockMvc.perform(get("/api/v1/monitoring/priority/{h3Index}", unpopulatedH3)
                        .param("cityId", PUNE_CITY_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(containsString("No hotspot prediction found")));
    }

    @Test
    @Order(10)
    @DisplayName("10. Priority endpoint CASE A (Shivajinagar): returns LOW priority for well-covered low risk cell")
    void testPriorityCaseA() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/priority/{h3Index}", PUNE_SHIVAJINAGAR_H3)
                        .param("cityId", PUNE_CITY_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.h3Index").value(PUNE_SHIVAJINAGAR_H3))
                .andExpect(jsonPath("$.riskScore").value(0.15))
                .andExpect(jsonPath("$.predictedPm25").isNumber())
                .andExpect(jsonPath("$.uncertaintyIntervalWidth").isNumber())
                .andExpect(jsonPath("$.forecastHorizonHours").value(1))
                .andExpect(jsonPath("$.nearestStationCode").value("PUN-001"))
                .andExpect(jsonPath("$.nearestStationDistanceKm").value(closeTo(0.27, 0.05)))
                .andExpect(jsonPath("$.priorityLevel").value("LOW"))
                .andExpect(jsonPath("$.priorityScorePercent").value(lessThan(40)));
    }

    @Test
    @Order(11)
    @DisplayName("11. Priority endpoint CASE B (Katraj): returns MEDIUM priority for moderate risk and uncertainty")
    void testPriorityCaseB() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/priority/{h3Index}", PUNE_KATRAJ_H3)
                        .param("cityId", PUNE_CITY_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.h3Index").value(PUNE_KATRAJ_H3))
                .andExpect(jsonPath("$.riskScore").value(0.58))
                .andExpect(jsonPath("$.predictedPm25").value(68.0))
                .andExpect(jsonPath("$.uncertaintyIntervalWidth").value(15.0))
                .andExpect(jsonPath("$.forecastHorizonHours").value(1))
                .andExpect(jsonPath("$.nearestStationCode").value("PUN-002"))
                .andExpect(jsonPath("$.priorityLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.priorityScorePercent").value(both(greaterThanOrEqualTo(40)).and(lessThanOrEqualTo(69))));
    }

    @Test
    @Order(12)
    @DisplayName("12. Priority endpoint CASE C (Distant Cell): returns HIGH priority for high risk, high uncertainty, coverage gap")
    void testPriorityCaseC() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/priority/{h3Index}", PUNE_DISTANT_H3)
                        .param("cityId", PUNE_CITY_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.h3Index").value(PUNE_DISTANT_H3))
                .andExpect(jsonPath("$.riskScore").value(0.88))
                .andExpect(jsonPath("$.predictedPm25").value(135.0))
                .andExpect(jsonPath("$.uncertaintyIntervalWidth").value(20.0))
                .andExpect(jsonPath("$.forecastHorizonHours").value(1))
                .andExpect(jsonPath("$.nearestStationDistanceKm").value(greaterThan(7.0)))
                .andExpect(jsonPath("$.monitoringCoverageGapFlag").value(1))
                .andExpect(jsonPath("$.priorityLevel").value("HIGH"))
                .andExpect(jsonPath("$.priorityScorePercent").value(greaterThanOrEqualTo(70)));
    }

    @Test
    @Order(13)
    @DisplayName("13. Recommendations endpoint returns city recommendations sorted by priorityScorePercent descending")
    void testGetCityRecommendations() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/recommendations")
                        .param("cityId", PUNE_CITY_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))))
                // Verify all required MonitoringRecommendation fields are present
                .andExpect(jsonPath("$[0].h3Index").isString())
                .andExpect(jsonPath("$[0].latitude").isNumber())
                .andExpect(jsonPath("$[0].longitude").isNumber())
                .andExpect(jsonPath("$[0].riskScore").isNumber())
                .andExpect(jsonPath("$[0].riskLevel").isString())
                .andExpect(jsonPath("$[0].forecastHorizonHours").value(1))
                .andExpect(jsonPath("$[0].predictedPm25").isNumber())
                .andExpect(jsonPath("$[0].uncertaintyIntervalWidth").isNumber())
                .andExpect(jsonPath("$[0].normalizedUncertainty").isNumber())
                .andExpect(jsonPath("$[0].priorityScore").isNumber())
                .andExpect(jsonPath("$[0].priorityScorePercent").isNumber())
                .andExpect(jsonPath("$[0].priorityLevel").isString())
                .andExpect(jsonPath("$[0].recommendationType").isString())
                .andExpect(jsonPath("$[0].recommendation").isString())
                .andExpect(jsonPath("$[0].rationale").isString())
                // Verify frontend backward-compatibility aliases
                .andExpect(jsonPath("$[0].uncertainty").isNumber())
                // Verify descending priority order
                .andExpect(jsonPath("$[0].priorityScorePercent").value(greaterThanOrEqualTo(70)))
                .andExpect(jsonPath("$[0].priorityLevel").value("HIGH"));
    }

    @Test
    @Order(14)
    @DisplayName("14. Recommendations endpoint CASE A (Shivajinagar): returns ROUTINE_MONITORING for low priority cell")
    void testRecommendationCaseA() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/recommendations/{h3Index}", PUNE_SHIVAJINAGAR_H3)
                        .param("cityId", PUNE_CITY_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.h3Index").value(PUNE_SHIVAJINAGAR_H3))
                .andExpect(jsonPath("$.priorityLevel").value("LOW"))
                .andExpect(jsonPath("$.recommendationType").value("ROUTINE_MONITORING"))
                .andExpect(jsonPath("$.recommendation").value("Continue routine monitoring for this H3 cell."))
                .andExpect(jsonPath("$.rationale").value(containsString("Priority: LOW")))
                .andExpect(jsonPath("$.rationale").value(containsString("Routine observation sufficient")))
                // Frontend compatibility checks
                .andExpect(jsonPath("$.stationDistanceKm").value(closeTo(0.27, 0.05)))
                .andExpect(jsonPath("$.uncertainty").isNumber());
    }

    @Test
    @Order(15)
    @DisplayName("15. Recommendations endpoint CASE B (Katraj): returns TARGETED_MONITORING for medium priority cell")
    void testRecommendationCaseB() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/recommendations/{h3Index}", PUNE_KATRAJ_H3)
                        .param("cityId", PUNE_CITY_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.h3Index").value(PUNE_KATRAJ_H3))
                .andExpect(jsonPath("$.priorityLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.recommendationType").value("TARGETED_MONITORING"))
                .andExpect(jsonPath("$.recommendation").value("Prioritize targeted monitoring and closer observation for this H3 cell."))
                .andExpect(jsonPath("$.rationale").value(containsString("Priority: MEDIUM")))
                .andExpect(jsonPath("$.rationale").value(containsString("Closer observation recommended")));
    }

    @Test
    @Order(16)
    @DisplayName("16. Recommendations endpoint CASE C (Distant Cell): returns MOBILE_SENSOR_RECOMMENDED for high priority with gap")
    void testRecommendationCaseC() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/recommendations/{h3Index}", PUNE_DISTANT_H3)
                        .param("cityId", PUNE_CITY_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.h3Index").value(PUNE_DISTANT_H3))
                .andExpect(jsonPath("$.priorityLevel").value("HIGH"))
                .andExpect(jsonPath("$.monitoringCoverageGapFlag").value(1))
                .andExpect(jsonPath("$.recommendationType").value("MOBILE_SENSOR_RECOMMENDED"))
                .andExpect(jsonPath("$.recommendation").value("Consider deploying additional mobile monitoring in this unobserved H3 cell."))
                .andExpect(jsonPath("$.rationale").value(containsString("Priority: HIGH")))
                .andExpect(jsonPath("$.rationale").value(containsString("Additional mobile sensor deployment recommended")));
    }

    @Test
    @Order(17)
    @DisplayName("17. Recommendations endpoint defaults cityId to Pune when omitted")
    void testRecommendationDefaultCityId() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/recommendations/{h3Index}", PUNE_SHIVAJINAGAR_H3)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.h3Index").value(PUNE_SHIVAJINAGAR_H3))
                .andExpect(jsonPath("$.recommendationType").value("ROUTINE_MONITORING"));
    }

    @Test
    @Order(18)
    @DisplayName("18. Recommendations endpoint returns 400 Bad Request for invalid H3 index")
    void testRecommendationInvalidH3() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/recommendations/{h3Index}", "not-an-h3")
                        .param("cityId", PUNE_CITY_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("Invalid H3 index")));
    }

    @Test
    @Order(19)
    @DisplayName("19. Recommendations endpoint returns 404 Not Found when H3 has no prediction")
    void testRecommendationNotFoundWhenNoPrediction() throws Exception {
        String unpopulatedH3 = "8860884111fffff";
        mockMvc.perform(get("/api/v1/monitoring/recommendations/{h3Index}", unpopulatedH3)
                        .param("cityId", PUNE_CITY_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(containsString("No hotspot prediction found")));
    }
}
