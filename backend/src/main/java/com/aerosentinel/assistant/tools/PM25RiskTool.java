package com.aerosentinel.assistant.tools;

import com.aerosentinel.assistant.dto.LocationDto;
import com.aerosentinel.city.City;
import com.aerosentinel.city.CityRepository;
import com.aerosentinel.forecast.Forecast;
import com.aerosentinel.forecast.ForecastRepository;
import com.aerosentinel.hotspot.HotspotPrediction;
import com.aerosentinel.hotspot.HotspotPredictionRepository;
import com.aerosentinel.spatial.H3Service;
import com.uber.h3core.util.LatLng;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class PM25RiskTool {

    private static final Logger log = LoggerFactory.getLogger(PM25RiskTool.class);

    // Pune Metropolitan Region Bounding Box (aligned with MLHotspotDetectionEngine)
    // Note: This bounding box is a coarse geographical boundary. Models were calibrated exclusively
    // on monitoring stations and weather cells within the Pune Metropolitan Region.
    public static final double PUNE_MIN_LAT = 18.0;
    public static final double PUNE_MAX_LAT = 19.2;
    public static final double PUNE_MIN_LON = 73.3;
    public static final double PUNE_MAX_LON = 74.4;
    public static final String DEFAULT_PUNE_H3 = "88608850e5fffff"; // Shivajinagar CAAQMS
    public static final Set<Integer> SUPPORTED_F4_HORIZONS = Set.of(1, 3, 6);

    private final H3Service h3Service;
    private final CityRepository cityRepository;
    private final HotspotPredictionRepository hotspotRepository;
    private final ForecastRepository forecastRepository;

    public PM25RiskTool(
            H3Service h3Service,
            CityRepository cityRepository,
            HotspotPredictionRepository hotspotRepository,
            ForecastRepository forecastRepository
    ) {
        this.h3Service = h3Service;
        this.cityRepository = cityRepository;
        this.hotspotRepository = hotspotRepository;
        this.forecastRepository = forecastRepository;
    }

    /**
     * Tool 5: getPM25Risk(location, horizon)
     * Retrieves validated Pune hotspot model risk and experimental multi-horizon forecasts.
     * Strictly enforces Pune domain restriction, server-side coordinate validation,
     * supported forecast horizon bounds, and transparent baseline benchmark disclosure.
     */
    public PM25RiskToolResult getPM25Risk(LocationDto location, Integer horizonHours) {
        String locName = (location != null && location.hasCityName()) ? location.cityName().trim() : "Pune";

        // 1. Strict unsupported-city rejection
        if (location != null && location.hasCityName() && !"Pune".equalsIgnoreCase(locName)) {
            return PM25RiskToolResult.domainUnsupported(locName, null);
        }

        String resolvedH3 = null;
        Double lat = null;
        Double lon = null;

        // 2. Resolve H3 and coordinates with server-side validation
        if (location != null && location.hasH3Index()) {
            String candidateH3 = location.h3Index().trim();
            if (!h3Service.validateH3Index(candidateH3) || h3Service.getCellResolution(candidateH3) != 8) {
                return PM25RiskToolResult.domainUnsupported(locName, candidateH3);
            }
            resolvedH3 = candidateH3;
            try {
                LatLng center = h3Service.h3ToCenter(resolvedH3);
                lat = center.lat;
                lon = center.lng;
            } catch (Exception ignored) {}
        } else if (location != null && location.hasCoordinates()) {
            lat = location.latitude();
            lon = location.longitude();
            if (lat == null || lon == null || Double.isNaN(lat) || Double.isInfinite(lat)
                    || Double.isNaN(lon) || Double.isInfinite(lon)
                    || lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
                return PM25RiskToolResult.domainUnsupported(locName, null);
            }
            try {
                resolvedH3 = h3Service.coordinatesToH3(lat, lon);
            } catch (Exception ignored) {}
        } else if (location != null && location.hasCityName()) {
            Optional<City> cityOpt = cityRepository.findByNameIgnoreCase(locName);
            if (cityOpt.isPresent()) {
                City city = cityOpt.get();
                lat = city.getLatitude();
                lon = city.getLongitude();
                resolvedH3 = DEFAULT_PUNE_H3;
            }
        } else {
            // Default to Pune primary cell if location omitted
            resolvedH3 = DEFAULT_PUNE_H3;
            lat = 18.5314;
            lon = 73.8446;
        }

        // 3. Check if coordinates belong to Pune Metropolitan Region
        boolean inPuneDomain = false;
        if (lat != null && lon != null) {
            inPuneDomain = (lat >= PUNE_MIN_LAT && lat <= PUNE_MAX_LAT && lon >= PUNE_MIN_LON && lon <= PUNE_MAX_LON);
        } else if ("Pune".equalsIgnoreCase(locName)) {
            inPuneDomain = true;
        }

        if (!inPuneDomain) {
            return PM25RiskToolResult.domainUnsupported(locName, resolvedH3);
        }

        if (resolvedH3 == null) {
            resolvedH3 = DEFAULT_PUNE_H3;
        }

        // 4. Query latest F3 Hotspot Prediction
        Optional<HotspotPrediction> predOpt = hotspotRepository.findTopByH3IndexOrderByPredictedAtDesc(resolvedH3);
        Boolean isHotspot = null;
        Double riskScore = null;
        String riskLevel = "UNKNOWN";
        String modelVersion = "hotspot_classifier_v1";

        if (predOpt.isPresent()) {
            HotspotPrediction p = predOpt.get();
            riskScore = p.getRiskScore();
            riskLevel = p.getRiskLevel();
            isHotspot = (riskScore != null && riskScore >= 0.20);
            modelVersion = p.getModelVersion();
        }

        // 5. Query latest F4 PM2.5 multi-horizon forecasts
        List<Forecast> forecasts = forecastRepository.findLatestByH3Index(resolvedH3);
        List<PM25RiskToolResult.ForecastHorizonEntry> horizonEntries = new ArrayList<>();

        if (forecasts != null) {
            for (Forecast f : forecasts) {
                if (horizonHours == null || horizonHours.equals(f.getHorizonHours())) {
                    horizonEntries.add(new PM25RiskToolResult.ForecastHorizonEntry(
                            f.getHorizonHours(),
                            f.getTargetTime() != null ? f.getTargetTime().toString() : null,
                            f.getPredictedPm25(),
                            f.getLowerBound(),
                            f.getUpperBound(),
                            "ug/m3",
                            "EXPERIMENTAL_ML"
                    ));
                }
            }
        }

        List<String> limitations = new ArrayList<>();
        limitations.add("Model Scope: Calibrated exclusively on Pune Metropolitan Region ground and meteorological data. Bounding box [18.0..19.2, 73.3..74.4] represents coarse spatial bounds.");
        limitations.add("F3 Decision Threshold: 0.20 (optimized for high hazard detection recall: 97.1%).");
        limitations.add("F4 Forecaster Status: Experimental ML. On test holdout evaluation, naive persistence baseline outperformed the Random Forest regressor on short lead times.");

        if (horizonHours != null && !SUPPORTED_F4_HORIZONS.contains(horizonHours)) {
            limitations.add("Requested horizon (" + horizonHours + "h) is not supported. F4 forecaster models are calibrated only for 1h, 3h, and 6h lead times.");
        }

        return new PM25RiskToolResult(
                resolvedH3,
                locName,
                isHotspot,
                riskScore,
                riskLevel,
                modelVersion,
                0.20,
                horizonEntries,
                null, // Strictly null confidence
                true,
                "VALID",
                limitations
        );
    }
}
