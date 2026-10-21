import React, { useState, useEffect } from 'react';
import { CloudSun, RefreshCw } from 'lucide-react';
import { useApp } from '../../store/AppContext';
import { LocationDto, WeatherToolResult } from '../../types/assistant';
import { assistantService } from '../../services/assistant.service';
import { CitizenLocationSelector } from '../../components/citizen/CitizenLocationSelector';
import { CitizenWeatherCard } from '../../components/citizen/CitizenWeatherCard';
import { CitizenForecastTimeline } from '../../components/citizen/CitizenForecastTimeline';

export interface CitizenWeatherPageProps {
  currentLocale?: string;
}

export const CitizenWeatherPage: React.FC<CitizenWeatherPageProps> = () => {
  const { selectedCity } = useApp();

  const [location, setLocation] = useState<LocationDto>(() => ({
    cityName: selectedCity?.name || 'Pune',
    latitude: selectedCity?.latitude || 18.5314,
    longitude: selectedCity?.longitude || 73.8446,
  }));

  const [weather, setWeather] = useState<WeatherToolResult | null>(null);
  const [forecast, setForecast] = useState<WeatherToolResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [weatherError, setWeatherError] = useState<string | null>(null);
  const [forecastError, setForecastError] = useState<string | null>(null);

  const fetchWeatherData = async (loc: LocationDto) => {
    setLoading(true);
    try {
      const [w, f] = await Promise.all([
        assistantService.getCurrentWeather(loc),
        assistantService.getWeatherForecast(loc, 'Next 72 Hours'),
      ]);
      setWeather(w);
      setForecast(f);
      setWeatherError(null);
      setForecastError(null);
    } catch (err: any) {
      setWeatherError(err.message || 'Failed to fetch weather');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchWeatherData(location);
  }, [location]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      <section className="citizen-hero-banner">
        <div className="citizen-hero-eyebrow">
          <CloudSun size={13} />
          <span>Numerical Meteorological Analysis</span>
        </div>
        <h1 className="citizen-hero-title">Weather &amp; Forecast</h1>
        <p className="citizen-hero-sub">
          Continuous atmospheric model reanalysis and forward-looking 72-hour forecasts powered by Open-Meteo numerical weather prediction models (seamless GFS/ECMWF/ICON blend).
        </p>
      </section>

      <CitizenLocationSelector
        currentLocation={location}
        onLocationChange={(newLoc) => setLocation(newLoc)}
      />

      <div className="citizen-grid-container">
        <div className="citizen-col-12">
          <CitizenWeatherCard
            weather={weather}
            loading={loading && !weather}
            error={weatherError}
            onRefresh={() => fetchWeatherData(location)}
          />
        </div>

        <div className="citizen-col-12">
          <CitizenForecastTimeline
            forecast={forecast}
            loading={loading && !forecast}
            error={forecastError}
          />
        </div>
      </div>
    </div>
  );
};
