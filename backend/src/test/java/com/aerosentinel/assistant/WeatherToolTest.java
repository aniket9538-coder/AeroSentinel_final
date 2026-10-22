package com.aerosentinel.assistant;

import com.aerosentinel.assistant.dto.LocationDto;
import com.aerosentinel.assistant.tools.WeatherTool;
import com.aerosentinel.assistant.tools.WeatherToolResult;
import com.aerosentinel.integration.weather.WeatherClient;
import com.aerosentinel.integration.weather.WeatherProviderResponse;
import com.aerosentinel.spatial.H3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class WeatherToolTest {

    private WeatherClient weatherClient;
    private H3Service h3Service;
    private WeatherTool weatherTool;

    @BeforeEach
    void setUp() {
        weatherClient = Mockito.mock(WeatherClient.class);
        h3Service = new H3Service(8);
        weatherTool = new WeatherTool(weatherClient, h3Service);
    }

    @Test
    @DisplayName("Tool 1: getCurrentWeather returns model-based reanalysis labeled correctly, not station telemetry")
    void testGetCurrentWeather_Success() {
        WeatherProviderResponse response = new WeatherProviderResponse();
        response.setTimezone("Asia/Kolkata");
        WeatherProviderResponse.HourlyData hourly = new WeatherProviderResponse.HourlyData();

        String nowIso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00")
                .withZone(ZoneId.of("Asia/Kolkata"))
                .format(Instant.now());

        hourly.setTime(List.of(nowIso));
        hourly.setTemperature2m(List.of(24.5));
        hourly.setRelativeHumidity2m(List.of(58.0));
        hourly.setWindSpeed10m(List.of(12.3));
        hourly.setWindDirection10m(List.of(180.0));
        hourly.setPrecipitation(List.of(0.0));
        hourly.setSurfacePressure(List.of(1012.0));
        response.setHourly(hourly);

        when(weatherClient.fetchHourlyWeather(eq(18.53), eq(73.84), anyInt(), anyInt()))
                .thenReturn(Optional.of(response));

        LocationDto loc = new LocationDto("Pune", 18.53, 73.84, null);
        WeatherToolResult result = weatherTool.getCurrentWeather(loc);

        assertNotNull(result);
        assertFalse(result.isForecast(), "Current weather must be an observation/analysis, not a forecast");
        assertEquals("VALID", result.status());
        assertEquals("Pune", result.locationName());
        assertEquals(24.5, result.temperature());
        assertEquals("°C", result.temperatureUnit());
        assertEquals(58.0, result.humidity());
        assertEquals("%", result.humidityUnit());
        assertEquals(12.3, result.windSpeed());
        assertEquals("km/h", result.windSpeedUnit());
        assertEquals("Open-Meteo NWP Model Analysis", result.source());
        assertTrue(result.limitations().stream().anyMatch(l -> l.contains("numerical weather prediction models")));
        assertTrue(result.limitations().stream().anyMatch(l -> l.contains("not an in-situ physical telemetry station")));
        assertNotNull(result.freshness());
    }

    @Test
    @DisplayName("Tool 1: getCurrentWeather rejects non-finite or invalid coordinates")
    void testGetCurrentWeather_InvalidCoordinates() {
        LocationDto loc = new LocationDto("BadLoc", Double.NaN, 73.84, null);
        WeatherToolResult result = weatherTool.getCurrentWeather(loc);

        assertNotNull(result);
        assertEquals("UNAVAILABLE", result.status());
        assertTrue(result.limitations().get(0).contains("non-finite"));
    }

    @Test
    @DisplayName("Tool 2: getWeatherForecast returns strictly forward-looking numerical simulation with attribution")
    void testGetWeatherForecast_Success() {
        WeatherProviderResponse response = new WeatherProviderResponse();
        response.setTimezone("Asia/Kolkata");
        WeatherProviderResponse.HourlyData hourly = new WeatherProviderResponse.HourlyData();

        Instant future1 = Instant.now().plusSeconds(3600);
        Instant future2 = Instant.now().plusSeconds(7200);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00").withZone(ZoneId.of("Asia/Kolkata"));

        hourly.setTime(List.of(fmt.format(future1), fmt.format(future2)));
        hourly.setTemperature2m(List.of(26.0, 25.0));
        hourly.setRelativeHumidity2m(List.of(60.0, 65.0));
        hourly.setWindSpeed10m(List.of(10.0, 8.0));
        hourly.setPrecipitation(List.of(1.5, 0.2));
        response.setHourly(hourly);

        when(weatherClient.fetchHourlyWeather(eq(18.53), eq(73.84), anyInt(), anyInt()))
                .thenReturn(Optional.of(response));

        LocationDto loc = new LocationDto("Pune", 18.53, 73.84, null);
        WeatherToolResult result = weatherTool.getWeatherForecast(loc, "Evening");

        assertNotNull(result);
        assertTrue(result.isForecast(), "Weather forecast must be flagged as forecast");
        assertEquals("VALID", result.status());
        assertEquals("Evening", result.forecastTimeRange());
        assertEquals(25.5, result.temperature(), 0.1);
        assertEquals(1.5, result.precipitation());
        assertEquals("Open-Meteo Numerical Forecast (ECMWF/GFS blend)", result.source());
        assertTrue(result.limitations().stream().anyMatch(l -> l.contains("Open-Meteo.com")));
        assertFalse(result.hourlyForecasts().isEmpty());
    }

    @Test
    @DisplayName("Tool 2: getWeatherForecast rejects unsupported horizons (> 72 hours)")
    void testGetWeatherForecast_UnsupportedHorizon() {
        LocationDto loc = new LocationDto("Pune", 18.53, 73.84, null);
        WeatherToolResult result = weatherTool.getWeatherForecast(loc, "7 days");

        assertNotNull(result);
        assertEquals("UNAVAILABLE", result.status());
        assertTrue(result.limitations().get(0).contains("exceeds maximum supported horizon of 72 hours"));
    }
}
