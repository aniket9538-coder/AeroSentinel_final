import React, { useState } from 'react';
import { Calendar, Clock, Droplets, Wind, AlertCircle } from 'lucide-react';
import { WeatherToolResult, HourlyForecastEntry } from '../../types/assistant';

export interface CitizenForecastTimelineProps {
  forecast: WeatherToolResult | null;
  loading: boolean;
  error: string | null;
}

export const CitizenForecastTimeline: React.FC<CitizenForecastTimelineProps> = ({
  forecast,
  loading,
  error,
}) => {
  const [horizonFilter, setHorizonFilter] = useState<24 | 48 | 72>(24);

  if (loading && !forecast) {
    return (
      <div className="citizen-card" aria-busy="true">
        <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
          Loading 72-hour numerical forecast timeline...
        </div>
      </div>
    );
  }

  const entries: HourlyForecastEntry[] = forecast?.hourlyForecasts || [];

  if (error || !forecast || forecast.status === 'UNAVAILABLE' || entries.length === 0) {
    return (
      <div className="citizen-card" role="region" aria-label="Weather Forecast Timeline">
        <div className="citizen-card-header">
          <div className="citizen-card-title-group">
            <div className="citizen-card-icon">
              <Calendar size={18} />
            </div>
            <h2 className="citizen-card-title">Hourly Forecast Timeline</h2>
          </div>
          <span className="citizen-card-badge badge-unavailable">NO_FORECAST_DATA</span>
        </div>
        <div style={{ padding: '1rem 0', color: 'var(--text-secondary)', fontSize: '0.88rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <AlertCircle size={16} color="#f59e0b" />
          <span>Forward-looking hourly forecast currently unavailable from numerical provider.</span>
        </div>
      </div>
    );
  }

  // Slice based on selected horizon filter
  const displayedEntries = entries.slice(0, horizonFilter);

  return (
    <div className="citizen-card" role="region" aria-label="Hourly Forecast Timeline">
      {/* Header with Horizon Selector */}
      <div className="citizen-card-header">
        <div className="citizen-card-title-group">
          <div className="citizen-card-icon">
            <Calendar size={18} />
          </div>
          <div>
            <h2 className="citizen-card-title">Hourly Forecast Timeline</h2>
            <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
              Forward-looking timestamps (t &gt; now) • Horizon: {horizonFilter} Hours
            </div>
          </div>
        </div>

        {/* Horizon Filter Pills */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
          {[24, 48, 72].map((h) => (
            <button
              key={h}
              type="button"
              onClick={() => setHorizonFilter(h as 24 | 48 | 72)}
              style={{
                padding: '0.2rem 0.55rem',
                borderRadius: '6px',
                fontSize: '0.74rem',
                fontWeight: 700,
                border: horizonFilter === h ? '1px solid var(--brand-primary)' : '1px solid var(--border-subtle)',
                background: horizonFilter === h ? 'var(--brand-surface)' : 'transparent',
                color: horizonFilter === h ? 'var(--brand-primary)' : 'var(--text-muted)',
                cursor: 'pointer',
              }}
            >
              {h}h
            </button>
          ))}
        </div>
      </div>

      {/* Hourly Scrollable Timeline Strip */}
      <div className="citizen-forecast-scroll" tabIndex={0} aria-label="Scrollable hourly forecast cards">
        {displayedEntries.map((item, idx) => {
          const date = new Date(item.timestamp);
          const timeStr = isNaN(date.getTime())
            ? item.timestamp
            : date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
          const dayStr = isNaN(date.getTime())
            ? ''
            : date.toLocaleDateString([], { weekday: 'short' });

          return (
            <div key={`${item.timestamp}-${idx}`} className="citizen-forecast-hour-card">
              <span className="citizen-forecast-hour-time">{timeStr}</span>
              <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                {dayStr}
              </span>
              <span className="citizen-forecast-hour-temp">
                {item.temperature != null ? `${Math.round(item.temperature)}°` : '--'}
              </span>
              <div className="citizen-forecast-hour-rain">
                <Droplets size={10} />
                <span>{item.precipitation != null ? `${item.precipitation.toFixed(1)}mm` : '0mm'}</span>
              </div>
              {item.windSpeed != null && (
                <div style={{ fontSize: '0.65rem', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '0.2rem' }}>
                  <Wind size={10} />
                  <span>{item.windSpeed.toFixed(0)}kph</span>
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Source Provenance Footer */}
      <div className="citizen-source-provenance">
        <span>Model: <strong>Open-Meteo Numerical Forecast (ECMWF/GFS blend)</strong></span>
        <span>Valid timezone: <strong>Asia/Kolkata (IST)</strong></span>
      </div>
    </div>
  );
};
