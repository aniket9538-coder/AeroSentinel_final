package com.aerosentinel.admin;

import com.aerosentinel.action.ActionType;
import com.aerosentinel.action.AuthorityAction;
import com.aerosentinel.action.AuthorityActionRepository;
import com.aerosentinel.admin.dto.*;
import com.aerosentinel.air.AirObservation;
import com.aerosentinel.air.AirObservationRepository;
import com.aerosentinel.alert.AlertRepository;
import com.aerosentinel.alert.AlertStatus;
import com.aerosentinel.citizen.CitizenReportRepository;
import com.aerosentinel.city.CityRepository;
import com.aerosentinel.monitoring.MonitoringPriorityConfig;
import com.aerosentinel.sensor.MonitoringStationRepository;
import com.aerosentinel.weather.WeatherObservation;
import com.aerosentinel.weather.WeatherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class AdminObservabilityServiceTest {

    private DataSource dataSource;
    private AirObservationRepository airObservationRepository;
    private WeatherRepository weatherRepository;
    private MonitoringStationRepository monitoringStationRepository;
    private CityRepository cityRepository;
    private CitizenReportRepository citizenReportRepository;
    private AlertRepository alertRepository;
    private AuthorityActionRepository authorityActionRepository;
    private MonitoringPriorityConfig priorityConfig;

    private AdminObservabilityService service;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = Mockito.mock(DataSource.class);
        airObservationRepository = Mockito.mock(AirObservationRepository.class);
        weatherRepository = Mockito.mock(WeatherRepository.class);
        monitoringStationRepository = Mockito.mock(MonitoringStationRepository.class);
        cityRepository = Mockito.mock(CityRepository.class);
        citizenReportRepository = Mockito.mock(CitizenReportRepository.class);
        alertRepository = Mockito.mock(AlertRepository.class);
        authorityActionRepository = Mockito.mock(AuthorityActionRepository.class);
        priorityConfig = new MonitoringPriorityConfig();

        Connection mockConnection = Mockito.mock(Connection.class);
        when(mockConnection.isValid(2)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(mockConnection);

        service = new AdminObservabilityService(
                dataSource,
                airObservationRepository,
                weatherRepository,
                monitoringStationRepository,
                cityRepository,
                citizenReportRepository,
                alertRepository,
                authorityActionRepository,
                priorityConfig,
                "http://127.0.0.1:8000",
                3000,
                "https://api.openaq.org/v3",
                true,
                "https://api.cpcb.gov.in",
                "https://api.open-meteo.com/v1/forecast",
                "https://firms.modaps.eosdis.nasa.gov",
                "https://sachet.ndma.gov.in/rss.xml"
        );
    }

    @Test
    @DisplayName("Redaction: Sensitive bearer tokens, passwords, emails, and phone numbers are redacted")
    void testSensitiveRedaction() {
        String input = "Officer contacted user at john.doe@example.com or +919876543210 using Bearer eyJhbGciOiJIUzI1NiJ9. Password: secretPassword123";
        String redacted = AdminObservabilityService.redactSensitiveContent(input);

        assertThat(redacted).doesNotContain("john.doe@example.com");
        assertThat(redacted).doesNotContain("+919876543210");
        assertThat(redacted).doesNotContain("secretPassword123");
        assertThat(redacted).doesNotContain("Bearer eyJhbGciOiJIUzI1NiJ9");
        assertThat(redacted).contains("[REDACTED_EMAIL]");
        assertThat(redacted).contains("[REDACTED_PHONE]");
        assertThat(redacted).contains("[REDACTED_CREDENTIAL]");
    }

    @Test
    @DisplayName("Audit Events: Raw actions are mapped into safe redacted DTOs with PII stripped")
    void testSafeAuditEvents() {
        AuthorityAction action = new AuthorityAction(
                UUID.randomUUID(),
                "ACKNOWLEDGE",
                "Action details",
                "admin_officer",
                "OPEN",
                "ACKNOWLEDGED",
                "Investigated report for user citizen@gmail.com with phone 9876543210."
        );

        when(authorityActionRepository.findTop50ByOrderByPerformedAtDesc()).thenReturn(List.of(action));

        List<SafeAuditEventDto> events = service.getSafeAuditEvents();

        assertThat(events).hasSize(1);
        SafeAuditEventDto event = events.get(0);
        assertThat(event.actionType()).isEqualTo("ACKNOWLEDGE");
        assertThat(event.safeReason()).doesNotContain("citizen@gmail.com");
        assertThat(event.safeReason()).doesNotContain("9876543210");
        assertThat(event.safeReason()).contains("[REDACTED_EMAIL]");
        assertThat(event.safeReason()).contains("[REDACTED_PHONE]");
        assertThat(event.redacted()).isTrue();
    }

    @Test
    @DisplayName("Provider Observability: Telemetry records and freshness are computed truthfully")
    void testProviderObservability() {
        AirObservation airObs = new AirObservation();
        // Reflection or setter for observedAt
        Instant recentObs = Instant.now().minus(30, ChronoUnit.MINUTES);
        airObs.setObservedAt(recentObs);

        when(airObservationRepository.countBySource("OPENAQ")).thenReturn(250L);
        when(airObservationRepository.countBySource("CPCB")).thenReturn(120L);
        when(airObservationRepository.findFirstByOrderByObservedAtDesc()).thenReturn(Optional.of(airObs));
        when(weatherRepository.count()).thenReturn(500L);

        ProviderObservabilityDto dto = service.getProviderObservability();

        assertThat(dto).isNotNull();
        assertThat(dto.activeProvidersCount()).isGreaterThan(0);
        assertThat(dto.totalObservationsIngested()).isEqualTo(250L + 120L + 500L);

        ProviderObservabilityDto.ProviderItem openaq = dto.providers().stream()
                .filter(p -> "openaq".equals(p.providerId()))
                .findFirst()
                .orElse(null);

        assertThat(openaq).isNotNull();
        assertThat(openaq.recordCount()).isEqualTo(250L);
        assertThat(openaq.freshnessStatus()).isEqualTo("FRESH");
        assertThat(openaq.freshnessMinutes()).isNotNull();
    }

    @Test
    @DisplayName("Admin Overview: Aggregates record counts and generates diagnostic guidance")
    void testAdminOverview() {
        when(airObservationRepository.count()).thenReturn(1500L);
        when(weatherRepository.count()).thenReturn(2000L);
        when(monitoringStationRepository.count()).thenReturn(12L);
        when(cityRepository.count()).thenReturn(3L);
        when(citizenReportRepository.count()).thenReturn(45L);
        when(alertRepository.count()).thenReturn(8L);
        when(authorityActionRepository.count()).thenReturn(24L);

        AdminOverviewDto overview = service.getAdminOverview();

        assertThat(overview).isNotNull();
        assertThat(overview.recordCounts().totalAirObservations()).isEqualTo(1500L);
        assertThat(overview.recordCounts().totalWeatherObservations()).isEqualTo(2000L);
        assertThat(overview.recordCounts().activeStationsCount()).isEqualTo(12L);
        assertThat(overview.recordCounts().activeAlertsCount()).isEqualTo(8L);
        assertThat(overview.monitoringGapContext().gapThresholdKm()).isEqualTo(7.0);
        assertThat(overview.monitoringGapContext().coverageRadiusKm()).isEqualTo(5.0);
        assertThat(overview.diagnosticRecommendations()).isNotEmpty();
    }
}
