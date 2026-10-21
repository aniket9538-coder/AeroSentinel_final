package com.aerosentinel.weather;

import com.aerosentinel.city.City;
import com.aerosentinel.city.CityRepository;
import com.aerosentinel.dto.weather.WeatherLatestResponse;
import com.aerosentinel.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WeatherService {

    private final WeatherRepository weatherRepository;
    private final CityRepository cityRepository;

    public WeatherService(WeatherRepository weatherRepository, CityRepository cityRepository) {
        this.weatherRepository = weatherRepository;
        this.cityRepository = cityRepository;
    }

    /**
     * Canonical F2 Endpoint Service: Latest real weather for a city.
     * Returns the latest persisted weather observation ordered by observed_at DESC.
     * If city exists but has no weather observations, returns controlled empty response.
     */
    public WeatherLatestResponse getLatestWeatherForCity(UUID cityId) {
        City city = cityRepository.findById(cityId)
                .orElseThrow(() -> new ResourceNotFoundException("City not found with id: " + cityId));

        return weatherRepository.findFirstByCityIdOrderByObservedAtDesc(cityId)
                .map(obs -> new WeatherLatestResponse(
                        city.getId(),
                        city.getName(),
                        obs.getTemperature(),
                        obs.getHumidity(),
                        obs.getWindSpeed(),
                        obs.getWindDirection(),
                        obs.getRainfall(),
                        obs.getObservedAt(),
                        obs.getSource(),
                        obs.getH3Index()
                ))
                .orElseGet(() -> new WeatherLatestResponse(city.getId(), city.getName()));
    }

    public Optional<WeatherObservation> getCurrentWeather(UUID cityId) {
        return weatherRepository.findFirstByCityIdOrderByObservedAtDesc(cityId);
    }

    public List<WeatherObservation> getWeatherHistory(UUID cityId) {
        return weatherRepository.findTop50ByCityIdOrderByObservedAtDesc(cityId);
    }

    public List<WeatherObservation> getWeatherHistory(UUID cityId, Integer limit) {
        return weatherRepository.findTop50ByCityIdOrderByObservedAtDesc(cityId);
    }

    public Optional<WeatherObservation> getCurrentWeatherByH3(String h3Index) {
        return weatherRepository.findFirstByH3IndexOrderByObservedAtDesc(h3Index);
    }

    public List<WeatherObservation> getWeatherHistoryByH3(String h3Index) {
        return weatherRepository.findByH3IndexOrderByObservedAtDesc(h3Index);
    }

    public WeatherObservation saveWeather(WeatherObservation weather) {
        return weatherRepository.save(weather);
    }

    public List<WeatherObservation> saveAllWeather(List<WeatherObservation> list) {
        return weatherRepository.saveAll(list);
    }
}
