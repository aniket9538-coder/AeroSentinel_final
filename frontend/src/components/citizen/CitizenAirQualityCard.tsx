import React from 'react';
import { Wind, Activity, Clock, MapPin, AlertCircle, RefreshCw, ExternalLink } from 'lucide-react';
import { AirQualityToolResult } from '../../types/assistant';
import { AirQualityObservationResponse } from '../../types/airQuality';

export interface CitizenAirQualityCardProps {
  airQuality: AirQualityToolResult | null;
  stationList?: AirQualityObservationResponse[];
  loading: boolean;
  error: string | null;
  onRefresh?: () => void;
}

export const CitizenAirQualityCard: React.FC<CitizenAirQualityCardProps> = ({
  airQuality,
  stationList = [],
  loading,
  error,
  onRefresh,
}) => {
  if (loading && !airQuality) {
    return (
      <div className="citizen-card" aria-busy="true">
        <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
          Loading ground telemetry observations (CPCB / OpenAQ)...
        </div>
      </div>
    );
  }

  const isUnavailable = error != null || !airQuality || airQuality.status === 'NO_DATA' || airQuality.status === 'UNAVAILABLE';
  const freshness = airQuality?.freshness || 'NO_DATA';
  const isLive = freshness === 'LIVE';
  const badgeClass = isLive ? 'badge-live' : freshness === 'STALE' ? 'badge-stale' : 'badge-unavailable';

  // Get AQI color tier based on verified PM2.5 value (NAAQS standards: 0-30 Good, 31-60 Satisfactory, 61-90 Moderate, 91-120 Poor, 121-250 Very Poor, >250 Severe)
  const getPm25Color = (val: number | null | undefined): string => {
    if (val == null) return 'var(--text-secondary)';
    if (val <= 30) return 'var(--aqi-good)';
    if (val <= 60) return '#84cc16';
    if (val <= 90) return 'var(--aqi-moderate)';
    if (val <= 120) return 'var(--aqi-poor)';
    if (val <= 250) return 'var(--aqi-very-poor)';
    return 'var(--aqi-severe)';
  };

  const getPm25Category = (val: number | null | undefined): string => {
    if (val == null) return 'Unobserved';
    if (val <= 30) return 'Good';
    if (val <= 60) return 'Satisfactory';
    if (val <= 90) return 'Moderate';
    if (val <= 120) return 'Poor';
    if (val <= 250) return 'Very Poor';
    return 'Severe';
  };

  const pm25Val = airQuality?.pm25;

  return (
    <div className="citizen-card" role="region" aria-label="Ground Air Quality Telemetry">
      {/* Header */}
      <div className="citizen-card-header">
        <div className="citizen-card-title-group">
          <div className="citizen-card-icon">
            <Wind size={18} />
          </div>
          <div>
            <h2 className="citizen-card-title">Air Quality Observation</h2>
            <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
              In-situ ground reference monitor telemetry
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span className={`citizen-card-badge ${badgeClass}`}>
            {freshness}
          </span>
          {onRefresh && (
            <button
              onClick={onRefresh}
              title="Refresh telemetry"
              aria-label="Refresh telemetry"
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

      {isUnavailable ? (
        <div style={{ padding: '1rem 0', color: 'var(--text-secondary)', fontSize: '0.88rem', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#f59e0b' }}>
            <AlertCircle size={16} />
            <span>No ground monitor observations available for the selected area.</span>
          </div>
          <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
            Missing sensor telemetry is not substituted with zeroes or synthetic estimates.
          </div>
        </div>
      ) : (
        <>
          {/* Main PM2.5 Observation */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
            <div>
              <div style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                PM2.5 Concentration
              </div>
              <div className="citizen-metric-primary">
                <span className="citizen-metric-val" style={{ color: getPm25Color(pm25Val) }}>
                  {pm25Val != null ? pm25Val.toFixed(1) : '--'}
                </span>
                <span className="citizen-metric-unit">
                  {airQuality?.pm25Unit || 'µg/m³'}
                </span>
              </div>
              <div style={{ fontSize: '0.85rem', fontWeight: 700, color: getPm25Color(pm25Val), marginTop: '0.2rem' }}>
                Category: {getPm25Category(pm25Val)}
              </div>
            </div>

            {/* Station Details */}
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', fontSize: '0.78rem', color: 'var(--text-secondary)' }}>
              <span>
                <strong>Station:</strong> {airQuality?.stationName || 'Primary Regional Monitor'}
              </span>
              {airQuality?.stationCode && (
                <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                  Code: {airQuality.stationCode}
                </span>
              )}
              {airQuality?.observedAt && (
                <span>
                  <strong>Measured:</strong>{' '}
                  {new Date(airQuality.observedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </span>
              )}
            </div>
          </div>

          {/* Active Station Multi-Monitor List if available */}
          {stationList.length > 1 && (
            <div style={{ marginTop: '0.5rem' }}>
              <div style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-muted)', marginBottom: '0.4rem', textTransform: 'uppercase' }}>
                Active City Monitors ({stationList.length})
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem', maxHeight: '130px', overflowY: 'auto' }}>
                {stationList.slice(0, 4).map((st) => (
                  <div
                    key={st.stationId}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '0.35rem 0.55rem',
                      background: 'var(--bg-surface)',
                      borderRadius: '6px',
                      fontSize: '0.78rem',
                      border: '1px solid var(--border-subtle)',
                    }}
                  >
                    <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{st.stationName}</span>
                    <span style={{ fontWeight: 700, color: getPm25Color(st.pm25) }}>
                      {st.pm25.toFixed(1)} µg/m³
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Freshness Disclaimer */}
          {freshness === 'STALE' && (
            <div
              style={{
                padding: '0.5rem 0.65rem',
                borderRadius: '6px',
                background: 'rgba(245, 158, 11, 0.1)',
                border: '1px solid rgba(245, 158, 11, 0.25)',
                fontSize: '0.74rem',
                color: '#f59e0b',
              }}
            >
              <strong>Notice:</strong> Ground observation is older than 2 hours. Telemetry update frequency depends on CPCB/OpenAQ synchronization.
            </div>
          )}
        </>
      )}

      {/* Source Provenance Footer */}
      <div className="citizen-source-provenance">
        <span>Network: <strong>{airQuality?.source || 'CPCB / OpenAQ Ground Reference'}</strong></span>
        <a
          href="https://openaq.org"
          target="_blank"
          rel="noopener noreferrer"
          className="citizen-source-link"
        >
          <span>OpenAQ Telemetry Portal</span>
          <ExternalLink size={11} />
        </a>
      </div>
    </div>
  );
};
