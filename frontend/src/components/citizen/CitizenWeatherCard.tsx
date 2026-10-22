import React from 'react';
import { CloudSun, Wind, Droplets, Gauge, AlertCircle, ExternalLink, RefreshCw } from 'lucide-react';
import { WeatherToolResult } from '../../types/assistant';

export interface CitizenWeatherCardProps {
  weather: WeatherToolResult | null;
  loading: boolean;
  error: string | null;
  onRefresh?: () => void;
}

export const CitizenWeatherCard: React.FC<CitizenWeatherCardProps> = ({
  weather,
  loading,
  error,
  onRefresh,
}) => {
  if (loading && !weather) {
    return (
      <div className="citizen-card" aria-busy="true">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '2.5rem', gap: '0.65rem', color: 'var(--text-secondary)' }}>
          <RefreshCw size={18} className="animate-spin" />
          <span>Retrieving meteorological analysis...</span>
        </div>
      </div>
    );
  }

  if (error || !weather || weather.status === 'UNAVAILABLE' || weather.status === 'ERROR') {
    return (
      <div className="citizen-card" role="region" aria-label="Current Weather Conditions">
        <div className="citizen-card-header">
          <div className="citizen-card-title-group">
            <div className="citizen-card-icon">
              <CloudSun size={18} />
            </div>
            <h2 className="citizen-card-title">Current Weather Conditions</h2>
          </div>
          <span className="citizen-card-badge badge-unavailable">SOURCE_UNAVAILABLE</span>
        </div>
        <div style={{ padding: '1rem 0', color: 'var(--text-secondary)', fontSize: '0.88rem', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#ef4444' }}>
            <AlertCircle size={16} />
            <span>Weather telemetry currently unavailable for this location.</span>
          </div>
          {weather?.limitations && weather.limitations.length > 0 && (
            <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
              {weather.limitations.join(' ')}
            </div>
          )}
          {onRefresh && (
            <button
              onClick={onRefresh}
              type="button"
              style={{
                alignSelf: 'flex-start',
                marginTop: '0.5rem',
                padding: '0.35rem 0.75rem',
                borderRadius: '8px',
                border: '1px solid var(--border-medium)',
                background: 'var(--bg-surface)',
                color: 'var(--text-primary)',
                fontSize: '0.8rem',
                cursor: 'pointer',
              }}
            >
              Retry
            </button>
          )}
        </div>
      </div>
    );
  }

  const isLive = weather.freshness === 'LIVE';
  const badgeClass = isLive ? 'badge-live' : weather.freshness === 'STALE' ? 'badge-stale' : 'badge-unavailable';

  return (
    <div className="citizen-card" role="region" aria-label="Current Weather Conditions">
      {/* Header */}
      <div className="citizen-card-header">
        <div className="citizen-card-title-group">
          <div className="citizen-card-icon">
            <CloudSun size={18} />
          </div>
          <div>
            <h2 className="citizen-card-title">Weather Conditions</h2>
            <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
              Open-Meteo NWP Numerical Analysis (2m surface level)
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span className={`citizen-card-badge ${badgeClass}`}>
            {weather.freshness || 'LIVE'}
          </span>
          {onRefresh && (
            <button
              onClick={onRefresh}
              title="Refresh weather data"
              aria-label="Refresh weather data"
              style={{
                background: 'transparent',
                border: 'none',
                color: 'var(--text-muted)',
                cursor: 'pointer',
                padding: '0.2rem',
              }}
            >
              <RefreshCw size={14} />
            </button>
          )}
        </div>
      </div>

      {/* Main Temperature & Location Display */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
        <div className="citizen-metric-primary">
          <span className="citizen-metric-val">
            {weather.temperature != null ? weather.temperature.toFixed(1) : '--'}
          </span>
          <span className="citizen-metric-unit">
            {weather.temperatureUnit || '°C'}
          </span>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', fontSize: '0.78rem', color: 'var(--text-secondary)' }}>
          <span><strong>Location:</strong> {weather.locationName}</span>
          {weather.observationTime && (
            <span><strong>Model Analysis Time:</strong> {new Date(weather.observationTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
          )}
        </div>
      </div>

      {/* Metric Grid (Humidity, Wind Speed, Precipitation, Pressure) */}
      <div className="citizen-metric-grid">
        <div className="citizen-metric-item">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', color: 'var(--text-muted)' }}>
            <Droplets size={13} />
            <span className="citizen-metric-item-label">Humidity</span>
          </div>
          <span className="citizen-metric-item-value">
            {weather.humidity != null ? `${weather.humidity.toFixed(0)}${weather.humidityUnit || '%'}` : 'N/A'}
          </span>
        </div>

        <div className="citizen-metric-item">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', color: 'var(--text-muted)' }}>
            <Wind size={13} />
            <span className="citizen-metric-item-label">Wind Speed</span>
          </div>
          <span className="citizen-metric-item-value">
            {weather.windSpeed != null ? `${weather.windSpeed.toFixed(1)} ${weather.windSpeedUnit || 'km/h'}` : 'N/A'}
          </span>
        </div>

        <div className="citizen-metric-item">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', color: 'var(--text-muted)' }}>
            <CloudSun size={13} />
            <span className="citizen-metric-item-label">Precipitation</span>
          </div>
          <span className="citizen-metric-item-value">
            {weather.precipitation != null ? `${weather.precipitation.toFixed(1)} ${weather.precipitationUnit || 'mm'}` : '0.0 mm'}
          </span>
        </div>

        <div className="citizen-metric-item">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', color: 'var(--text-muted)' }}>
            <Gauge size={13} />
            <span className="citizen-metric-item-label">Pressure</span>
          </div>
          <span className="citizen-metric-item-value">
            {weather.surfacePressure != null ? `${weather.surfacePressure.toFixed(0)} hPa` : 'N/A'}
          </span>
        </div>
      </div>

      {/* Transparent Semantics Disclosure Alert */}
      <div
        style={{
          padding: '0.55rem 0.75rem',
          borderRadius: '8px',
          background: 'var(--bg-surface)',
          border: '1px solid var(--border-subtle)',
          fontSize: '0.74rem',
          color: 'var(--text-secondary)',
          lineHeight: 1.4,
        }}
      >
        <strong>Notice:</strong> Surface meteorological analysis derived from Open-Meteo numerical weather prediction models (seamless GFS/ECMWF/ICON blend) at 2m surface level. Not an in-situ physical telemetry station measurement.
      </div>

      {/* Source Provenance Footer */}
      <div className="citizen-source-provenance">
        <span>Source: <strong>{weather.source || 'Open-Meteo NWP Model Analysis'}</strong></span>
        <a
          href="https://open-meteo.com"
          target="_blank"
          rel="noopener noreferrer"
          className="citizen-source-link"
        >
          <span>Weather data by Open-Meteo.com (CC BY 4.0)</span>
          <ExternalLink size={11} />
        </a>
      </div>
    </div>
  );
};
