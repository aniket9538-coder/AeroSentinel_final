package com.aerosentinel.weather;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/current")
    public ResponseEntity<WeatherObservation> getCurrentWeather(@RequestParam UUID cityId) {
        return weatherService.getCurrentWeather(cityId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/history")
    public ResponseEntity<java.util.List<WeatherObservation>> getWeatherHistory(
            @RequestParam UUID cityId,
            @RequestParam(required = false, defaultValue = "50") Integer limit
    ) {
        return ResponseEntity.ok(weatherService.getWeatherHistory(cityId, limit));
    }
}
