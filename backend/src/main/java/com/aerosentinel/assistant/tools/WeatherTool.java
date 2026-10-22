package com.aerosentinel.assistant.tools;

import com.aerosentinel.assistant.dto.LocationDto;
import com.aerosentinel.integration.weather.WeatherClient;
import com.aerosentinel.integration.weather.WeatherProviderResponse;
import com.aerosentinel.spatial.H3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WeatherTool {

    private static final Logger log = LoggerFactory.getLogger(WeatherTool.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Pattern HORIZON_HOURS_PATTERN = Pattern.compile("(?i)(\\d+)\\s*(h|hr|hrs|hour|hours)");
    private static final Pattern HORIZON_DAYS_PATTERN = Pattern.compile("(?i)(\\d+)\\s*(d|day|days)");

    public static final String OPEN_METEO_ATTRIBUTION = "Weather data by Open-Meteo.com (CC BY 4.0)";
    public static final int MAX_SUPPORTED_FORECAST_HOURS = 72;

    private final WeatherClient weatherClient;
    private final H3Service h3Service;

    public WeatherTool(WeatherClient weatherClient, H3Service h3Service) {
        this.weatherClient = weatherClient;
        this.h3Service = h3Service;
    }

    /**
     * Tool 1: getCurrentWeather(location)
     * Fetches current meteorological conditions derived from Open-Meteo NWP models.
     * Accurately labeled as numerical model reanalysis/estimate at 2m surface level,
     * NOT in-situ physical weather monitoring station telemetry.
     */
    public WeatherToolResult getCurrentWeather(LocationDto location) {
        if (location == null) {
            return WeatherToolResult.unavailable(
                    "Unknown",
                    "Open-Meteo NWP Model Analysis",
                    "Coordinates are required to retrieve local weather data."
            );
        }

        if (location.latitude() != null && location.longitude() != null &&
                (Double.isNaN(location.latitude()) || Double.isInfinite(location.latitude()) ||
                 Double.isNaN(location.longitude()) || Double.isInfinite(location.longitude()) ||
                 location.latitude() < -90.0 || location.latitude() > 90.0 ||
                 location.longitude() < -180.0 || location.longitude() > 180.0)) {
            return WeatherToolResult.unavailable(
                    location.cityName() != null ? location.cityName() : "Invalid Coordinates",
                    "Open-Meteo NWP Model Analysis",
                    "Coordinates out of bounds or non-finite: lat=" + location.latitude() + ", lon=" + location.longitude()
            );
        }

        if (!location.hasCoordinates()) {
            return WeatherToolResult.unavailable(
                    location.cityName() != null ? location.cityName() : "Unknown",
                    "Open-Meteo NWP Model Analysis",
                    "Coordinates are required to retrieve local weather data."
            );
        }

        double lat = location.latitude();
        double lon = location.longitude();

        String locName = (location.cityName() != null && !location.cityName().isBlank())
                ? location.cityName().trim() : String.format("%.2f, %.2f", lat, lon);

        Optional<WeatherProviderResponse> respOpt = weatherClient.fetchHourlyWeather(lat, lon, 1, 1);
        if (respOpt.isEmpty() || respOpt.get().getHourly() == null) {
            return WeatherToolResult.unavailable(locName, "Open-Meteo NWP Model Analysis", "Weather model service unavailable for coordinates.");
        }

        WeatherProviderResponse response = respOpt.get();
        WeatherProviderResponse.HourlyData hourly = response.getHourly();
        List<String> times = hourly.getTime();
        if (times == null || times.isEmpty()) {
            return WeatherToolResult.unavailable(locName, "Open-Meteo NWP Model Analysis", "No weather time series returned by provider.");
        }

        ZoneId zone = DEFAULT_ZONE;
        if (response.getTimezone() != null && !response.getTimezone().isBlank()) {
            try { zone = ZoneId.of(response.getTimezone().trim()); } catch (Exception ignored) {}
        }

        Instant now = Instant.now();
        int bestIdx = -1;
        Instant bestTime = null;

        // Find the latest timestamp that is <= now
        for (int i = 0; i < times.size(); i++) {
            try {
                Instant t = LocalDateTime.parse(times.get(i)).atZone(zone).toInstant();
                if (!t.isAfter(now)) {
                    if (bestTime == null || t.isAfter(bestTime)) {
                        bestTime = t;
                        bestIdx = i;
                    }
                }
            } catch (Exception ignored) {}
        }

        if (bestIdx == -1) {
            bestIdx = 0;
            bestTime = now;
        }

        Double temp = sanitize(hourly.getTemperature2m(), bestIdx);
        Double humidity = sanitize(hourly.getRelativeHumidity2m(), bestIdx);
        Double windSpeed = sanitize(hourly.getWindSpeed10m(), bestIdx);
        Double windDir = sanitize(hourly.getWindDirection10m(), bestIdx);
        Double precip = sanitize(hourly.getPrecipitation(), bestIdx);
        if (precip == null) precip = 0.0;
        Double pressure = sanitize(hourly.getSurfacePressure(), bestIdx);

        long ageMinutes = java.time.Duration.between(bestTime, now).toMinutes();
        String freshness = (ageMinutes <= 180) ? "LIVE" : "STALE";

        return new WeatherToolResult(
                false, // current observation/analysis, NOT forecast
                locName,
                lat,
                lon,
                temp,
                "°C",
                humidity,
                "%",
                windSpeed,
                "km/h",
                windDir,
                precip,
                "mm",
                pressure,
                bestTime.toString(),
                null,
                List.of(),
                "Open-Meteo NWP Model Analysis",
                freshness,
                "VALID",
                List.of(
                        "Surface meteorological analysis derived from Open-Meteo numerical weather prediction models (seamless GFS/ECMWF/ICON blend) at 2m surface level.",
                        "Notice: Model-based reanalysis/analysis, not an in-situ physical telemetry station.",
                        OPEN_METEO_ATTRIBUTION
                )
        );
    }

    /**
     * Tool 2: getWeatherForecast(location, timeRange)
     * Fetches genuine forward-looking weather forecast (strictly future hours).
     * Rejects unsupported forecast horizons (> 72 hours).
     */
    public WeatherToolResult getWeatherForecast(LocationDto location, String timeRange) {
        if (location == null) {
            return WeatherToolResult.unavailable(
                    "Unknown",
                    "Open-Meteo Numerical Forecast",
                    "Coordinates are required to generate weather forecast."
            );
        }

        if (location.latitude() != null && location.longitude() != null &&
                (Double.isNaN(location.latitude()) || Double.isInfinite(location.latitude()) ||
                 Double.isNaN(location.longitude()) || Double.isInfinite(location.longitude()) ||
                 location.latitude() < -90.0 || location.latitude() > 90.0 ||
                 location.longitude() < -180.0 || location.longitude() > 180.0)) {
            return WeatherToolResult.unavailable(
                    location.cityName() != null ? location.cityName() : "Invalid Coordinates",
                    "Open-Meteo Numerical Forecast",
                    "Coordinates out of bounds or non-finite: lat=" + location.latitude() + ", lon=" + location.longitude()
            );
        }

        if (!location.hasCoordinates()) {
            return WeatherToolResult.unavailable(
                    location.cityName() != null ? location.cityName() : "Unknown",
                    "Open-Meteo Numerical Forecast",
                    "Coordinates are required to generate weather forecast."
            );
        }

        double lat = location.latitude();
        double lon = location.longitude();

        String locName = (location.cityName() != null && !location.cityName().isBlank())
                ? location.cityName().trim() : String.format("%.2f, %.2f", lat, lon);

        // Parse requested horizon
        int requestedHours = parseHorizonHours(timeRange);
        if (requestedHours > MAX_SUPPORTED_FORECAST_HOURS) {
            return WeatherToolResult.unavailable(
                    locName,
                    "Open-Meteo Numerical Forecast",
                    "Requested forecast horizon (" + requestedHours + "h) exceeds maximum supported horizon of "
                            + MAX_SUPPORTED_FORECAST_HOURS + " hours (3 days)."
            );
        }

        int forecastDays = (int) Math.ceil((double) requestedHours / 24.0);
        forecastDays = Math.max(1, Math.min(3, forecastDays));

        // Request 0 past days and needed forecast days
        Optional<WeatherProviderResponse> respOpt = weatherClient.fetchHourlyWeather(lat, lon, 0, forecastDays);
        if (respOpt.isEmpty() || respOpt.get().getHourly() == null) {
            return WeatherToolResult.unavailable(locName, "Open-Meteo Numerical Forecast", "Forecast provider unavailable.");
        }

        WeatherProviderResponse response = respOpt.get();
        WeatherProviderResponse.HourlyData hourly = response.getHourly();
        List<String> times = hourly.getTime();
        if (times == null || times.isEmpty()) {
            return WeatherToolResult.unavailable(locName, "Open-Meteo Numerical Forecast", "Empty forecast series returned by provider.");
        }

        ZoneId zone = DEFAULT_ZONE;
        if (response.getTimezone() != null && !response.getTimezone().isBlank()) {
            try { zone = ZoneId.of(response.getTimezone().trim()); } catch (Exception ignored) {}
        }

        Instant now = Instant.now();
        List<WeatherToolResult.HourlyForecastEntry> futureEntries = new ArrayList<>();

        for (int i = 0; i < times.size(); i++) {
            try {
                Instant t = LocalDateTime.parse(times.get(i)).atZone(zone).toInstant();
                if (t.isAfter(now)) { // strictly future
                    Double temp = sanitize(hourly.getTemperature2m(), i);
                    Double hum = sanitize(hourly.getRelativeHumidity2m(), i);
                    Double prec = sanitize(hourly.getPrecipitation(), i);
                    if (prec == null) prec = 0.0;
                    Double wind = sanitize(hourly.getWindSpeed10m(), i);

                    futureEntries.add(new WeatherToolResult.HourlyForecastEntry(
                            t.toString(), temp, hum, prec, wind
                    ));

                    if (futureEntries.size() >= requestedHours) {
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }

        if (futureEntries.isEmpty()) {
            return WeatherToolResult.unavailable(locName, "Open-Meteo Numerical Forecast", "No future forecast hours available.");
        }

        // Summary metrics across next 6 hours (or available entries)
        int summaryWindow = Math.min(6, futureEntries.size());
        double avgTemp = futureEntries.stream().limit(summaryWindow)
                .filter(e -> e.temperature() != null)
                .mapToDouble(WeatherToolResult.HourlyForecastEntry::temperature)
                .average().orElse(0.0);
        double maxPrecip = futureEntries.stream().limit(summaryWindow)
                .filter(e -> e.precipitation() != null)
                .mapToDouble(WeatherToolResult.HourlyForecastEntry::precipitation)
                .max().orElse(0.0);
        double avgWind = futureEntries.stream().limit(summaryWindow)
                .filter(e -> e.windSpeed() != null)
                .mapToDouble(WeatherToolResult.HourlyForecastEntry::windSpeed)
                .average().orElse(0.0);

        String displayRange = (timeRange != null && !timeRange.isBlank())
                ? timeRange.trim() : ("Next " + futureEntries.size() + " Hours");

        return new WeatherToolResult(
                true, // FORECAST
                locName,
                lat,
                lon,
                Math.round(avgTemp * 10.0) / 10.0,
                "°C",
                null,
                "%",
                Math.round(avgWind * 10.0) / 10.0,
                "km/h",
                null,
                Math.round(maxPrecip * 10.0) / 10.0,
                "mm",
                null,
                null,
                displayRange,
                futureEntries,
                "Open-Meteo Numerical Forecast (ECMWF/GFS blend)",
                "LIVE",
                "VALID",
                List.of(
                        "Weather forecast data derived from Open-Meteo meteorological numerical models.",
                        "Genuinely forward-looking predictions strictly for future timestamps.",
                        "Clearly distinct from actual ground observations.",
                        OPEN_METEO_ATTRIBUTION
                )
        );
    }

    private int parseHorizonHours(String timeRange) {
        if (timeRange == null || timeRange.isBlank()) {
            return 24; // default 24h
        }
        Matcher mDays = HORIZON_DAYS_PATTERN.matcher(timeRange);
        if (mDays.find()) {
            try {
                int days = Integer.parseInt(mDays.group(1));
                return days * 24;
            } catch (Exception ignored) {}
        }
        Matcher mHours = HORIZON_HOURS_PATTERN.matcher(timeRange);
        if (mHours.find()) {
            try {
                return Integer.parseInt(mHours.group(1));
            } catch (Exception ignored) {}
        }
        if (timeRange.toLowerCase().contains("tomorrow")) {
            return 36;
        }
        if (timeRange.toLowerCase().contains("tonight") || timeRange.toLowerCase().contains("evening")) {
            return 12;
        }
        return 24;
    }

    private Double sanitize(List<Double> list, int idx) {
        if (list == null || idx < 0 || idx >= list.size()) {
            return null;
        }
        Double val = list.get(idx);
        if (val == null || Double.isNaN(val) || Double.isInfinite(val)) {
            return null;
        }
        return val;
    }
}
