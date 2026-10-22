import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useApp } from '../../store/AppContext';
import { LocationDto, WeatherToolResult, OfficialWarningToolResult, AirQualityToolResult, PM25RiskToolResult } from '../../types/assistant';
import { AirQualityObservationResponse } from '../../types/airQuality';
import { assistantService } from '../../services/assistant.service';
import { airQualityApi } from '../../services/airQualityApi';
import { CitizenLocationSelector } from '../../components/citizen/CitizenLocationSelector';
import { CitizenWeatherCard } from '../../components/citizen/CitizenWeatherCard';
import { CitizenForecastTimeline } from '../../components/citizen/CitizenForecastTimeline';
import { CitizenWarningsCard } from '../../components/citizen/CitizenWarningsCard';
import { CitizenAirQualityCard } from '../../components/citizen/CitizenAirQualityCard';
import { CitizenPM25RiskCard } from '../../components/citizen/CitizenPM25RiskCard';
import { CitizenQuickAssistant } from '../../components/citizen/CitizenQuickAssistant';
import { CitizenSavedLocationsCard } from '../../components/citizen/CitizenSavedLocationsCard';
import { CitizenAlertSubscriptionsCard } from '../../components/citizen/CitizenAlertSubscriptionsCard';
import { CitizenHistoricalTrendsCard } from '../../components/citizen/CitizenHistoricalTrendsCard';

export interface CitizenOverviewProps {
  currentLocale?: string;
}

export const CitizenOverview: React.FC<CitizenOverviewProps> = ({
  currentLocale = 'en',
}) => {
  const { selectedCity } = useApp();

  // Active citizen location state
  const [currentLocation, setCurrentLocation] = useState<LocationDto>(() => ({
    cityName: selectedCity?.name || 'Pune',
    latitude: selectedCity?.latitude || 18.5314,
    longitude: selectedCity?.longitude || 73.8446,
  }));

  // Data states
  const [weather, setWeather] = useState<WeatherToolResult | null>(null);
  const [forecast, setForecast] = useState<WeatherToolResult | null>(null);
  const [warnings, setWarnings] = useState<OfficialWarningToolResult | null>(null);
  const [airQuality, setAirQuality] = useState<AirQualityToolResult | null>(null);
  const [pm25Risk, setPm25Risk] = useState<PM25RiskToolResult | null>(null);
  const [stations, setStations] = useState<AirQualityObservationResponse[]>([]);

  // Loading & error states
  const [loading, setLoading] = useState(true);
  const [weatherError, setWeatherError] = useState<string | null>(null);
  const [forecastError, setForecastError] = useState<string | null>(null);
  const [warningsError, setWarningsError] = useState<string | null>(null);
  const [airError, setAirError] = useState<string | null>(null);
  const [riskError, setRiskError] = useState<string | null>(null);

  // Request race guard
  const reqCountRef = useRef(0);

  // Keep currentLocation aligned when selectedCity changes in the global app context
  useEffect(() => {
    if (selectedCity && selectedCity.name !== currentLocation.cityName) {
      setCurrentLocation({
        cityName: selectedCity.name,
        latitude: selectedCity.latitude,
        longitude: selectedCity.longitude,
      });
    }
  }, [selectedCity]);

  // Load all environmental data for current location
  const loadEnvironmentalData = useCallback(async (loc: LocationDto) => {
    const curReq = ++reqCountRef.current;
    setLoading(true);

    // 1. Current Weather
    assistantService.getCurrentWeather(loc)
      .then((data) => {
        if (curReq === reqCountRef.current) {
          setWeather(data);
          setWeatherError(null);
        }
      })
      .catch((err) => {
        if (curReq === reqCountRef.current) {
          setWeatherError(err.message || 'Weather unavailable');
        }
      });

    // 2. Weather Forecast (Next 72 Hours)
    assistantService.getWeatherForecast(loc, 'Next 72 Hours')
      .then((data) => {
        if (curReq === reqCountRef.current) {
          setForecast(data);
          setForecastError(null);
        }
      })
      .catch((err) => {
        if (curReq === reqCountRef.current) {
          setForecastError(err.message || 'Forecast unavailable');
        }
      });

    // 3. Official Warnings
    assistantService.getWarnings(loc)
      .then((data) => {
        if (curReq === reqCountRef.current) {
          setWarnings(data);
          setWarningsError(null);
        }
      })
      .catch((err) => {
        if (curReq === reqCountRef.current) {
          setWarningsError(err.message || 'Warnings unavailable');
        }
      });

    // 4. Ground Air Quality
    assistantService.getAirQuality(loc)
      .then((data) => {
        if (curReq === reqCountRef.current) {
          setAirQuality(data);
          setAirError(null);
        }
      })
      .catch((err) => {
        if (curReq === reqCountRef.current) {
          setAirError(err.message || 'Air quality unavailable');
        }
      });

    // 5. PM2.5 ML Risk
    assistantService.getPM25Risk(loc)
      .then((data) => {
        if (curReq === reqCountRef.current) {
          setPm25Risk(data);
          setRiskError(null);
        }
      })
      .catch((err) => {
        if (curReq === reqCountRef.current) {
          setRiskError(err.message || 'Risk model unavailable');
        }
      });

    // 6. City Station observations if selectedCity ID available
    if (selectedCity?.id) {
      airQualityApi.getLatestAirQuality(selectedCity.id)
        .then((resp) => {
          if (curReq === reqCountRef.current && resp?.observations) {
            setStations(resp.observations);
          }
        })
        .catch(() => {
          // Non-blocking fallback
        });
    }

    setLoading(false);
  }, [selectedCity]);

  // Trigger data fetch on location change
  useEffect(() => {
    loadEnvironmentalData(currentLocation);
  }, [currentLocation, loadEnvironmentalData]);

  const handleLocationChange = (newLoc: LocationDto) => {
    setCurrentLocation(newLoc);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      {/* Hero Banner */}
      <section className="citizen-hero-banner" aria-label="Portal Introduction">
        <div className="citizen-hero-eyebrow">
          <span>Hyperlocal Environmental Intelligence</span>
        </div>
        <h1 className="citizen-hero-title">
          Citizen Environmental Overview
        </h1>
        <p className="citizen-hero-sub">
          Monitor real-time numerical weather analysis, ground-level PM2.5 observations, forward-looking 72-hour forecasts, and official statutory disaster bulletins for your community.
        </p>
      </section>

      {/* Location Selector Bar */}
      <CitizenLocationSelector
        currentLocation={currentLocation}
        onLocationChange={handleLocationChange}
      />

      {/* Quick "Ask AeroSentinel" Prompt Assistant */}
      <CitizenQuickAssistant
        location={currentLocation}
        locale={currentLocale}
      />

      {/* Main Grid: Weather, Forecast, Warnings, Air Quality, PM2.5 Risk */}
      <div className="citizen-grid-container">
        {/* Left Column: Current Weather (Col 6) */}
        <div className="citizen-col-6">
          <CitizenWeatherCard
            weather={weather}
            loading={loading && !weather}
            error={weatherError}
            onRefresh={() => loadEnvironmentalData(currentLocation)}
          />
        </div>

        {/* Right Column: Air Quality (Col 6) */}
        <div className="citizen-col-6">
          <CitizenAirQualityCard
            airQuality={airQuality}
            stationList={stations}
            loading={loading && !airQuality}
            error={airError}
            onRefresh={() => loadEnvironmentalData(currentLocation)}
          />
        </div>

        {/* Full Width: 72h Forecast Timeline (Col 12) */}
        <div className="citizen-col-12">
          <CitizenForecastTimeline
            forecast={forecast}
            loading={loading && !forecast}
            error={forecastError}
          />
        </div>

        {/* Left Bottom: Official Government Warnings (Col 7) */}
        <div className="citizen-col-7">
          <CitizenWarningsCard
            warningsResult={warnings}
            loading={loading && !warnings}
            error={warningsError}
            onRefresh={() => loadEnvironmentalData(currentLocation)}
          />
        </div>

        {/* Right Bottom: PM2.5 Experimental ML Risk (Col 5) */}
        <div className="citizen-col-5">
          <CitizenPM25RiskCard
            riskResult={pm25Risk}
            selectedCityName={currentLocation.cityName}
            loading={loading && !pm25Risk}
            error={riskError}
          />
        </div>

        {/* --- PHASE 10: ADVANCED INTELLIGENCE & CITIZEN PREFERENCES --- */}
        {/* Left: Saved Locations & Multi-Location Preferences */}
        <div className="citizen-col-6">
          <CitizenSavedLocationsCard
            currentLocale={currentLocale}
            onSelectLocation={(lat, lon, cityName) => {
              handleLocationChange({
                cityName,
                latitude: lat,
                longitude: lon,
              });
            }}
          />
        </div>

        {/* Right: Opt-In Threshold Alerts & In-App Notification Center */}
        <div className="citizen-col-6">
          <CitizenAlertSubscriptionsCard
            currentLocale={currentLocale}
            currentCityName={currentLocation.cityName}
          />
        </div>

        {/* Full Width: Verified Historical Environmental Trends */}
        <div className="citizen-col-12">
          <CitizenHistoricalTrendsCard
            currentLocale={currentLocale}
            cityId={selectedCity?.id}
          />
        </div>
      </div>
    </div>
  );
};
