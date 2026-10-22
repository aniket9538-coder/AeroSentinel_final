package com.aerosentinel.assistant.tools;

import com.aerosentinel.air.AirObservation;
import com.aerosentinel.air.AirObservationRepository;
import com.aerosentinel.air.AirService;
import com.aerosentinel.assistant.dto.LocationDto;
import com.aerosentinel.city.City;
import com.aerosentinel.city.CityRepository;
import com.aerosentinel.dto.air.AirQualityObservationResponse;
import com.aerosentinel.dto.air.LatestAirQualityResponse;
import com.aerosentinel.sensor.MonitoringStation;
import com.aerosentinel.sensor.SensorRepository;
import com.aerosentinel.spatial.H3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class AirQualityTool {

    private static final Logger log = LoggerFactory.getLogger(AirQualityTool.class);

    private final AirService airService;
    private final AirObservationRepository airObservationRepository;
    private final CityRepository cityRepository;
    private final SensorRepository sensorRepository;
    private final H3Service h3Service;

    public AirQualityTool(
            AirService airService,
            AirObservationRepository airObservationRepository,
            CityRepository cityRepository,
            SensorRepository sensorRepository,
            H3Service h3Service
    ) {
        this.airService = airService;
        this.airObservationRepository = airObservationRepository;
        this.cityRepository = cityRepository;
        this.sensorRepository = sensorRepository;
        this.h3Service = h3Service;
    }

    /**
     * Tool 4: getAirQuality(location)
     * Reuses the verified F1 real-data pipeline.
     */
    public AirQualityToolResult getAirQuality(LocationDto location) {
        if (location == null) {
            return AirQualityToolResult.noData("Unknown", "No location provided.");
        }

        Instant now = Instant.now();

        // 1. Try resolving by city name
        if (location.hasCityName()) {
            String queryCity = location.cityName().trim();
            Optional<City> cityOpt = cityRepository.findByNameIgnoreCase(queryCity);
            if (cityOpt.isPresent()) {
                City city = cityOpt.get();
                LatestAirQualityResponse latest = airService.getLatestAirQualityForCity(city.getId());
                if (latest != null && latest.getObservations() != null && !latest.getObservations().isEmpty()) {
                    // Pick the primary or most recent observation
                    AirQualityObservationResponse obs = latest.getObservations().get(0);
                    Instant obsTime = obs.getObservedAt();
                    long ageMinutes = (obsTime != null) ? Duration.between(obsTime, now).toMinutes() : 9999;
                    String freshness = (ageMinutes <= 120) ? "LIVE" : (ageMinutes <= 1440) ? "STALE" : "NO_DATA";

                    return new AirQualityToolResult(
                            city.getName(),
                            obs.getStationId(),
                            obs.getStationName(),
                            obs.getPm25(),
                            "ug/m3",
                            null, // Do not fabricate PM10 if not in latest DTO
                            "ug/m3",
                            obsTime != null ? obsTime.toString() : null,
                            now.toString(),
                            obs.getSource() != null ? obs.getSource() : "CPCB / OpenAQ",
                            freshness,
                            obs.getQuality() != null ? obs.getQuality() : "VALID",
                            "VALID",
                            List.of(
                                    "Direct ground telemetry from reference monitoring station.",
                                    "Freshness: " + freshness + " (" + ageMinutes + " minutes old)."
                            )
                    );
                } else {
                    return AirQualityToolResult.noData(city.getName(), "No active monitoring stations reported data for " + city.getName() + " within the last 24 hours.");
                }
            }
        }

        // 2. Try resolving by coordinates or H3
        Double targetLat = location.latitude();
        Double targetLon = location.longitude();

        if (targetLat == null && location.hasH3Index() && h3Service.validateH3Index(location.h3Index())) {
            try {
                com.uber.h3core.util.LatLng center = h3Service.h3ToCenter(location.h3Index());
                targetLat = center.lat;
                targetLon = center.lng;
            } catch (Exception ignored) {}
        }

        if (targetLat != null && targetLon != null) {
            List<MonitoringStation> allStations = sensorRepository.findAll();
            final double lat = targetLat;
            final double lon = targetLon;

            // Find closest station
            Optional<MonitoringStation> nearest = allStations.stream()
                    .min(Comparator.comparingDouble(s -> Math.hypot(s.getLatitude() - lat, s.getLongitude() - lon)));

            if (nearest.isPresent()) {
                MonitoringStation station = nearest.get();
                Optional<AirObservation> latestObsOpt = airObservationRepository
                        .findFirstByStationIdOrderByObservedAtDesc(station.getStationCode());

                if (latestObsOpt.isPresent()) {
                    AirObservation obs = latestObsOpt.get();
                    Instant obsTime = obs.getObservedAt();
                    long ageMinutes = (obsTime != null) ? Duration.between(obsTime, now).toMinutes() : 9999;
                    String freshness = (ageMinutes <= 120) ? "LIVE" : (ageMinutes <= 1440) ? "STALE" : "NO_DATA";

                    return new AirQualityToolResult(
                            station.getName(),
                            station.getStationCode(),
                            station.getName(),
                            obs.getPm25(),
                            "ug/m3",
                            obs.getPm10(),
                            "ug/m3",
                            obsTime != null ? obsTime.toString() : null,
                            now.toString(),
                            obs.getSource() != null ? obs.getSource() : "CPCB / OpenAQ",
                            freshness,
                            obs.getDataQuality() != null ? obs.getDataQuality() : "VALID",
                            "VALID",
                            List.of("Nearest reference monitoring station: " + station.getName() + " (" + station.getStationCode() + ").")
                    );
                }
            }
        }

        String displayName = location.hasCityName() ? location.cityName() : "Requested Area";
        return AirQualityToolResult.noData(displayName, "No monitoring station or telemetry found for location.");
    }
}
