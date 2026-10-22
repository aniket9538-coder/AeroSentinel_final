package com.aerosentinel.air;

import com.aerosentinel.city.City;
import com.aerosentinel.city.CityRepository;
import com.aerosentinel.dto.air.AirQualityTrendResponse;
import com.aerosentinel.sensor.MonitoringStation;
import com.aerosentinel.sensor.SensorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AirQualityTrendsTest {

    @Mock
    private AirObservationRepository airObservationRepository;

    @Mock
    private CityRepository cityRepository;

    @Mock
    private SensorRepository sensorRepository;

    private AirService airService;

    private final UUID puneCityId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    @BeforeEach
    void setUp() {
        airService = new AirService(airObservationRepository, cityRepository, sensorRepository);
    }

    @Test
    @DisplayName("getAirQualityTrends calculates valid statistical summary and chronological sorting")
    void getAirQualityTrends_calculatesSummary() {
        City pune = new City();
        pune.setId(puneCityId);
        pune.setName("Pune");
        when(cityRepository.findById(puneCityId)).thenReturn(Optional.of(pune));

        Instant t0 = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant t1 = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant t2 = Instant.now();

        AirObservation obs1 = new AirObservation();
        obs1.setStationId("PUN-001");
        obs1.setPm25(40.0);
        obs1.setObservedAt(t0);
        obs1.setSource("CPCB");

        AirObservation obs2 = new AirObservation();
        obs2.setStationId("PUN-001");
        obs2.setPm25(60.0);
        obs2.setObservedAt(t1);
        obs2.setSource("CPCB");

        AirObservation obs3 = new AirObservation();
        obs3.setStationId("PUN-001");
        obs3.setPm25(80.0);
        obs3.setObservedAt(t2);
        obs3.setSource("CPCB");

        when(airObservationRepository.findTop100ByStationIdOrderByObservedAtDesc("PUN-001"))
                .thenReturn(List.of(obs3, obs2, obs1)); // reverse order from repo

        MonitoringStation station = new MonitoringStation();
        station.setStationCode("PUN-001");
        station.setName("Shivajinagar CAAQMS");
        when(sensorRepository.findByStationCode("PUN-001")).thenReturn(Optional.of(station));

        AirQualityTrendResponse response = airService.getAirQualityTrends(puneCityId, "PUN-001", 50);

        assertThat(response).isNotNull();
        assertThat(response.stationCode()).isEqualTo("PUN-001");
        assertThat(response.stationName()).isEqualTo("Shivajinagar CAAQMS");
        assertThat(response.observations()).hasSize(3);
        // Chronologically sorted: t0, t1, t2
        assertThat(response.observations().get(0).observedAt()).isEqualTo(t0);
        assertThat(response.observations().get(2).observedAt()).isEqualTo(t2);

        // Stats
        assertThat(response.summary().minPm25()).isEqualTo(40.0);
        assertThat(response.summary().maxPm25()).isEqualTo(80.0);
        assertThat(response.summary().avgPm25()).isEqualTo(60.0);
        assertThat(response.summary().latestPm25()).isEqualTo(80.0);
        assertThat(response.summary().trendDirection()).isEqualTo("DETERIORATING");
        assertThat(response.honestMissingDataNote()).contains("strictly from actual station telemetry");
    }

    @Test
    @DisplayName("getAirQualityTrends returns honest missing data indicator when no records exist")
    void getAirQualityTrends_emptyRecords() {
        City pune = new City();
        pune.setId(puneCityId);
        pune.setName("Pune");
        when(cityRepository.findById(puneCityId)).thenReturn(Optional.of(pune));
        when(airObservationRepository.findTop100ByCityIdOrderByObservedAtDesc(puneCityId)).thenReturn(List.of());

        AirQualityTrendResponse response = airService.getAirQualityTrends(puneCityId, null, 50);

        assertThat(response.observations()).isEmpty();
        assertThat(response.summary().trendDirection()).isEqualTo("NO_DATA");
        assertThat(response.honestMissingDataNote()).contains("No historical air quality observations recorded");
    }
}
