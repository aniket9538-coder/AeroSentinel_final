package com.aerosentinel.assistant;

import com.aerosentinel.air.AirObservation;
import com.aerosentinel.air.AirObservationRepository;
import com.aerosentinel.air.AirService;
import com.aerosentinel.assistant.dto.LocationDto;
import com.aerosentinel.assistant.tools.AirQualityTool;
import com.aerosentinel.assistant.tools.AirQualityToolResult;
import com.aerosentinel.city.City;
import com.aerosentinel.city.CityRepository;
import com.aerosentinel.dto.air.AirQualityObservationResponse;
import com.aerosentinel.dto.air.LatestAirQualityResponse;
import com.aerosentinel.sensor.MonitoringStation;
import com.aerosentinel.sensor.SensorRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AirQualityToolTest {

    private AirService airService;
    private AirObservationRepository airObservationRepository;
    private CityRepository cityRepository;
    private SensorRepository sensorRepository;
    private H3Service h3Service;
    private AirQualityTool airQualityTool;

    @BeforeEach
    void setUp() {
        airService = Mockito.mock(AirService.class);
        airObservationRepository = Mockito.mock(AirObservationRepository.class);
        cityRepository = Mockito.mock(CityRepository.class);
        sensorRepository = Mockito.mock(SensorRepository.class);
        h3Service = new H3Service(8);
        airQualityTool = new AirQualityTool(airService, airObservationRepository, cityRepository, sensorRepository, h3Service);
    }

    @Test
    @DisplayName("Tool 4: Retrieves genuine ground PM2.5 observation and computes LIVE freshness")
    void testGetAirQuality_LiveSuccess() {
        UUID cityId = UUID.randomUUID();
        City city = new City("Pune", "Maharashtra", 18.5314, 73.8446);
        city.setId(cityId);
        when(cityRepository.findByNameIgnoreCase("Pune")).thenReturn(Optional.of(city));

        Instant recent = Instant.now().minusSeconds(1800); // 30 minutes ago
        AirQualityObservationResponse obs = new AirQualityObservationResponse(
                "PUN-001",
                "Shivajinagar CAAQMS",
                52.4,
                recent,
                "CPCB",
                "VALID"
        );
        when(airService.getLatestAirQualityForCity(cityId))
                .thenReturn(new LatestAirQualityResponse(cityId, "Pune", List.of(obs)));

        LocationDto loc = new LocationDto("Pune", null, null, null);
        AirQualityToolResult result = airQualityTool.getAirQuality(loc);

        assertNotNull(result);
        assertEquals("VALID", result.status());
        assertEquals("LIVE", result.freshness());
        assertEquals(52.4, result.pm25());
        assertEquals("ug/m3", result.pm25Unit());
        assertEquals("PUN-001", result.stationCode());
        assertEquals("CPCB", result.source());
    }

    @Test
    @DisplayName("Tool 4: Outdated observation (> 2 hours old) is labeled STALE with real timestamp")
    void testGetAirQuality_StaleStatus() {
        UUID cityId = UUID.randomUUID();
        City city = new City("Pune", "Maharashtra", 18.5314, 73.8446);
        city.setId(cityId);
        when(cityRepository.findByNameIgnoreCase("Pune")).thenReturn(Optional.of(city));

        Instant staleTime = Instant.now().minusSeconds(10800); // 3 hours ago
        AirQualityObservationResponse obs = new AirQualityObservationResponse(
                "PUN-001",
                "Shivajinagar CAAQMS",
                68.0,
                staleTime,
                "CPCB",
                "VALID"
        );
        when(airService.getLatestAirQualityForCity(cityId))
                .thenReturn(new LatestAirQualityResponse(cityId, "Pune", List.of(obs)));

        LocationDto loc = new LocationDto("Pune", null, null, null);
        AirQualityToolResult result = airQualityTool.getAirQuality(loc);

        assertNotNull(result);
        assertEquals("STALE", result.freshness());
        assertEquals(68.0, result.pm25());
        assertEquals(staleTime.toString(), result.observedAt());
    }

    @Test
    @DisplayName("Tool 4: Returns NO_DATA when city has no reported measurements")
    void testGetAirQuality_NoData() {
        UUID cityId = UUID.randomUUID();
        City city = new City("Pune", "Maharashtra", 18.5314, 73.8446);
        city.setId(cityId);
        when(cityRepository.findByNameIgnoreCase("Pune")).thenReturn(Optional.of(city));
        when(airService.getLatestAirQualityForCity(cityId))
                .thenReturn(new LatestAirQualityResponse(cityId, "Pune", List.of()));

        LocationDto loc = new LocationDto("Pune", null, null, null);
        AirQualityToolResult result = airQualityTool.getAirQuality(loc);

        assertNotNull(result);
        assertEquals("NO_DATA", result.status());
        assertEquals("NO_DATA", result.freshness());
        assertNull(result.pm25());
    }
}
