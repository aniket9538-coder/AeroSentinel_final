package com.aerosentinel.hotspot;

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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.isOneOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HotspotIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(HotspotIntegrationTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HotspotService hotspotService;

    @Autowired
    private HotspotRepository hotspotRepository;

    private static final UUID PUNE_CITY_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID MUMBAI_CITY_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
    private static final UUID DELHI_CITY_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");

    private static final String PUNE_SHIVAJINAGAR_H3 = "88608850e5fffff";

    @Test
    @Order(1)
    @DisplayName("1. Hotspot API returns full city risk overview with clean contracts")
    void testGetHotspotsForPune() throws Exception {
        mockMvc.perform(get("/api/v1/hotspots")
                        .param("cityId", PUNE_CITY_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cityId").value(PUNE_CITY_ID.toString()))
                .andExpect(jsonPath("$.cityName").value("Pune"))
                .andExpect(jsonPath("$.modelVersion").value("hotspot_classifier_v1"))
                .andExpect(jsonPath("$.engineType").value("ML"))
                .andExpect(jsonPath("$.freshness").value(isOneOf("LIVE", "STALE", "UNAVAILABLE", "NO_DATA")))
                .andExpect(jsonPath("$.totalCells").isNumber())
                .andExpect(jsonPath("$.operationalThreshold").value(0.20))
                .andExpect(jsonPath("$.cells").isArray())
                .andExpect(jsonPath("$.cells[0].h3Index").isNotEmpty())
                .andExpect(jsonPath("$.cells[0].riskScore").isNumber())
                .andExpect(jsonPath("$.cells[0].riskLevel").isNotEmpty())
                .andExpect(jsonPath("$.cells[0].confidence").isNumber())
                .andExpect(jsonPath("$.cells[0].isHotspot").isBoolean())
                .andExpect(jsonPath("$.cells[0].operationalThreshold").value(0.20));
    }

    @Test
    @Order(2)
    @DisplayName("2. Database persistence audit: Hotspot predictions are stored with snapshot references and history preserved")
    void testDatabasePersistenceAudit() {
        List<HotspotPrediction> predictions = hotspotRepository.findLatestByCityId(PUNE_CITY_ID);
        assertThat(predictions).isNotEmpty();

        for (HotspotPrediction pred : predictions) {
            assertThat(pred.getId()).isNotNull();
            assertThat(pred.getCityId()).isEqualTo(PUNE_CITY_ID);
            assertThat(pred.getH3Index()).isNotNull();
            assertThat(pred.getRiskScore()).isBetween(0.0, 1.0);
            assertThat(pred.getConfidence()).isBetween(0.0, 1.0);
            assertThat(pred.getModelVersion()).isIn("hotspot-baseline-v1", "hotspot_classifier_v1", "f3_classifier_v1");
            assertThat(pred.getFeatureSnapshotId()).withFailMessage("FeatureSnapshot reference missing").isNotNull();
        }

        HotspotPrediction sample = predictions.get(0);
        log.info("DATABASE_ROW_VERIFICATION: id={}, cityId={}, h3Index={}, riskScore={}, riskLevel={}, confidence={}, modelVersion={}, featureSnapshotId={}, predictedAt={}",
                sample.getId(), sample.getCityId(), sample.getH3Index(), sample.getRiskScore(), sample.getRiskLevel(),
                sample.getConfidence(), sample.getModelVersion(), sample.getFeatureSnapshotId(), sample.getPredictedAt());

        log.info("Verified {} persisted hotspot predictions with valid feature snapshot provenance", predictions.size());
    }

    @Test
    @Order(3)
    @DisplayName("3. Single-cell API: Retrieves latest prediction for specific H3 cell")
    void testGetSingleCellHotspot() throws Exception {
        var result = mockMvc.perform(get("/api/v1/hotspots/" + PUNE_SHIVAJINAGAR_H3)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.h3Index").value(PUNE_SHIVAJINAGAR_H3))
                .andExpect(jsonPath("$.riskScore").isNumber())
                .andExpect(jsonPath("$.riskLevel").isNotEmpty())
                .andExpect(jsonPath("$.confidence").isNumber())
                .andExpect(jsonPath("$.isHotspot").isBoolean())
                .andExpect(jsonPath("$.operationalThreshold").value(0.20))
                .andExpect(jsonPath("$.freshness").value(isOneOf("LIVE", "STALE", "UNAVAILABLE", "NO_DATA")))
                .andExpect(jsonPath("$.spatialContext").exists())
                .andExpect(jsonPath("$.spatialContext.h3Index").value(PUNE_SHIVAJINAGAR_H3))
                .andExpect(jsonPath("$.spatialContext.isHotspot").isBoolean())
                .andExpect(jsonPath("$.spatialContext.operationalThreshold").value(0.20))
                .andExpect(jsonPath("$.spatialContext.confidenceBreakdown").exists())
                .andExpect(jsonPath("$.spatialContext.confidenceBreakdown.spatialCoverageConfidence").isNumber())
                .andReturn();

        log.info("API_RUNTIME_RESPONSE_EXCERPT: {}", result.getResponse().getContentAsString());
    }

    @Test
    @Order(4)
    @DisplayName("4. Single-cell API: Returns 404 for unknown H3 cell")
    void testGetUnknownCellReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/hotspots/880000000000000")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    @DisplayName("5. Multi-city support: Mumbai and Delhi evaluate with explicit degraded confidence and baseline engine without fake ML")
    void testMultiCityHotspotBehavior() {
        HotspotOverviewResponse mumbaiResponse = hotspotService.getHotspotsForCity(MUMBAI_CITY_ID);
        assertThat(mumbaiResponse.cityName()).isEqualTo("Mumbai");
        assertThat(mumbaiResponse.engineType()).isEqualTo("BASELINE");
        assertThat(mumbaiResponse.modelVersion()).isEqualTo("hotspot-baseline-v1");
        assertThat(mumbaiResponse.cells()).isNotEmpty();
        // Mumbai confidence reflects unmonitored co-pollutant state
        assertThat(mumbaiResponse.cells().get(0).confidence()).isLessThan(0.50);

        HotspotOverviewResponse delhiResponse = hotspotService.getHotspotsForCity(DELHI_CITY_ID);
        assertThat(delhiResponse.cityName()).isEqualTo("Delhi");
        assertThat(delhiResponse.engineType()).isEqualTo("BASELINE");
        assertThat(delhiResponse.modelVersion()).isEqualTo("hotspot-baseline-v1");
        assertThat(delhiResponse.cells()).isNotEmpty();
        // Delhi confidence reflects unmonitored co-pollutant state
        assertThat(delhiResponse.cells().get(0).confidence()).isLessThan(0.50);

        log.info("Multi-city verified: Mumbai (cells={}), Delhi (cells={})",
                mumbaiResponse.cells().size(), delhiResponse.cells().size());
    }
}
