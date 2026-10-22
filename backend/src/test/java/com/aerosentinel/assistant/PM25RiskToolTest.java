package com.aerosentinel.assistant;

import com.aerosentinel.assistant.dto.LocationDto;
import com.aerosentinel.assistant.tools.PM25RiskTool;
import com.aerosentinel.assistant.tools.PM25RiskToolResult;
import com.aerosentinel.city.City;
import com.aerosentinel.city.CityRepository;
import com.aerosentinel.forecast.Forecast;
import com.aerosentinel.forecast.ForecastRepository;
import com.aerosentinel.hotspot.HotspotPrediction;
import com.aerosentinel.hotspot.HotspotPredictionRepository;
import com.aerosentinel.spatial.H3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class PM25RiskToolTest {

    private H3Service h3Service;
    private CityRepository cityRepository;
    private HotspotPredictionRepository hotspotRepository;
    private ForecastRepository forecastRepository;
    private PM25RiskTool pm25RiskTool;

    @BeforeEach
    void setUp() {
        h3Service = new H3Service(8);
        cityRepository = Mockito.mock(CityRepository.class);
        hotspotRepository = Mockito.mock(HotspotPredictionRepository.class);
        forecastRepository = Mockito.mock(ForecastRepository.class);
        pm25RiskTool = new PM25RiskTool(h3Service, cityRepository, hotspotRepository, forecastRepository);
    }

    @Test
    @DisplayName("Tool 5: Pune location returns valid hotspot risk, experimental ML forecaster, and null confidence")
    void testGetPM25Risk_PuneDomainSuccess() {
        City pune = new City("Pune", "Maharashtra", 18.5314, 73.8446);
        pune.setId(UUID.randomUUID());
        when(cityRepository.findByNameIgnoreCase("Pune")).thenReturn(Optional.of(pune));

        HotspotPrediction pred = new HotspotPrediction();
        pred.setH3Index(PM25RiskTool.DEFAULT_PUNE_H3);
        pred.setRiskScore(0.42);
        pred.setRiskLevel("MODERATE");
        pred.setModelVersion("hotspot_classifier_v1");
        pred.setConfidence(0.85);

        when(hotspotRepository.findTopByH3IndexOrderByPredictedAtDesc(anyString()))
                .thenReturn(Optional.of(pred));

        Forecast f1 = new Forecast();
        f1.setHorizonHours(1);
        f1.setPredictedPm25(48.2);
        f1.setLowerBound(45.1);
        f1.setUpperBound(51.3);
        f1.setTargetTime(Instant.now().plusSeconds(3600));

        when(forecastRepository.findLatestByH3Index(anyString())).thenReturn(List.of(f1));

        LocationDto loc = new LocationDto("Pune", 18.5314, 73.8446, null);
        PM25RiskToolResult result = pm25RiskTool.getPM25Risk(loc, null);

        assertNotNull(result);
        assertEquals("VALID", result.status());
        assertTrue(result.domainSupported());
        assertEquals("MODERATE", result.riskLevel());
        assertEquals(0.42, result.riskScore());
        assertEquals("hotspot_classifier_v1", result.modelVersion());
        assertEquals(0.20, result.operationalThreshold());
        assertNull(result.forecastConfidence(), "Confidence must strictly be null per Phase 3 contract");

        assertFalse(result.forecastHorizons().isEmpty());
        assertEquals("EXPERIMENTAL_ML", result.forecastHorizons().get(0).modelStatus());
    }

    @Test
    @DisplayName("Tool 5: Outside Pune location (e.g. Mumbai) strictly returns MODEL_DOMAIN_UNSUPPORTED")
    void testGetPM25Risk_UnsupportedDomain() {
        City mumbai = new City("Mumbai", "Maharashtra", 19.0760, 72.8777);
        mumbai.setId(UUID.randomUUID());
        when(cityRepository.findByNameIgnoreCase("Mumbai")).thenReturn(Optional.of(mumbai));

        LocationDto loc = new LocationDto("Mumbai", 19.0760, 72.8777, null);
        PM25RiskToolResult result = pm25RiskTool.getPM25Risk(loc, null);

        assertNotNull(result);
        assertEquals("MODEL_DOMAIN_UNSUPPORTED", result.status());
        assertFalse(result.domainSupported());
        assertNull(result.riskScore());
        assertTrue(result.limitations().get(0).contains("Pune Metropolitan Region"));
    }

    @Test
    @DisplayName("Tool 5: Unsupported forecast horizon (24h) returns limitation explaining F4 calibrated only for 1h, 3h, 6h")
    void testGetPM25Risk_UnsupportedHorizon() {
        City pune = new City("Pune", "Maharashtra", 18.5314, 73.8446);
        pune.setId(UUID.randomUUID());
        when(cityRepository.findByNameIgnoreCase("Pune")).thenReturn(Optional.of(pune));

        LocationDto loc = new LocationDto("Pune", 18.5314, 73.8446, null);
        PM25RiskToolResult result = pm25RiskTool.getPM25Risk(loc, 24);

        assertNotNull(result);
        assertEquals("VALID", result.status());
        assertTrue(result.limitations().stream().anyMatch(l -> l.contains("Requested horizon (24h) is not supported")));
    }

    @Test
    @DisplayName("Tool 5: Rejects non-Pune city (Delhi) regardless of missing coordinates")
    void testGetPM25Risk_StrictCityRejection() {
        LocationDto loc = new LocationDto("Delhi", null, null, null);
        PM25RiskToolResult result = pm25RiskTool.getPM25Risk(loc, null);

        assertNotNull(result);
        assertEquals("MODEL_DOMAIN_UNSUPPORTED", result.status());
        assertFalse(result.domainSupported());
    }
}
