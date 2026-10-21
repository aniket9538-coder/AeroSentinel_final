import React from 'react';
import { Card } from '../common/Card';
import { Badge } from '../common/Badge';
import { ForecastResponse } from '../../types/forecast';
import { TrendingUp, Clock, BrainCircuit, ShieldAlert, Sparkles } from 'lucide-react';

interface ForecastSummaryProps {
  forecast?: ForecastResponse | null;
  currentPm25?: number | null;
  cityName?: string;
}

export const ForecastSummary: React.FC<ForecastSummaryProps> = ({
  forecast,
  currentPm25,
  cityName = 'Pune',
}) => {
  const items = forecast?.forecasts || [];

  // Find peak forecast among the 1h, 3h, 6h horizons
  const peak = items.length > 0
    ? items.reduce((max, cur) => (cur.predictedPm25 > max.predictedPm25 ? cur : max), items[0])
    : null;

  const hasCurrentPm25 = currentPm25 !== undefined && currentPm25 !== null && !isNaN(currentPm25);
  const delta = peak && hasCurrentPm25 ? Math.round(peak.predictedPm25 - currentPm25) : null;
  const pctDelta = peak && hasCurrentPm25 && currentPm25 > 0 ? Math.round(((peak.predictedPm25 - currentPm25) / currentPm25) * 100) : null;

  return (
    <div>
      {/* Model Overview Banner (Clean & Non-overbearing) */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0.65rem 1.15rem',
          borderRadius: '10px',
          background: 'var(--bg-surface-elevated)',
          border: '1px solid var(--border-subtle)',
          marginBottom: '1.25rem',
          fontSize: '0.825rem',
          color: 'var(--text-secondary)',
          flexWrap: 'wrap',
          gap: '0.75rem',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem', flexWrap: 'wrap' }}>
          <div>
            <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', marginRight: '0.35rem' }}>Forecast model:</span>
            <strong style={{ color: 'var(--text-primary)' }}>Experimental ML (Random Forest)</strong>
          </div>
          <span style={{ color: 'var(--border-subtle)' }}>|</span>
          <div>
            <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', marginRight: '0.35rem' }}>Horizons:</span>
            <strong style={{ color: 'var(--text-primary)' }}>1h · 3h · 6h</strong>
          </div>
          <span style={{ color: 'var(--border-subtle)' }}>|</span>
          <div>
            <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem', marginRight: '0.35rem' }}>Baseline Note:</span>
            <strong style={{ color: 'var(--accent-amber)' }}>Persistence superior on holdout</strong>
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          {forecast?.modelVersion && (
            <Badge variant="neutral">{forecast.modelVersion}</Badge>
          )}
        </div>
      </div>

      {/* 4 Summary KPI Cards */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: '1rem',
        }}
      >
        {/* KPI 1: Base Observed PM2.5 */}
        <Card>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>
              Observed PM2.5
            </span>
            <span
              style={{ width: '8px', height: '8px', borderRadius: '50%', background: hasCurrentPm25 ? 'var(--brand-primary)' : 'var(--text-muted)' }}
              className={hasCurrentPm25 ? 'live-indicator-dot' : ''}
            />
          </div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.4rem', marginTop: '0.4rem' }}>
            <span style={{ fontSize: hasCurrentPm25 ? '2rem' : '1.5rem', fontWeight: 800, color: hasCurrentPm25 ? 'var(--brand-primary)' : 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
              {hasCurrentPm25 ? currentPm25 : '—'}
            </span>
            {hasCurrentPm25 && <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>µg/m³</span>}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.35rem' }}>
            {hasCurrentPm25 ? `Observed at T0 · ${cityName}` : `Observation unavailable at T0 · ${cityName}`}
          </div>
        </Card>

        {/* KPI 2: Highest Forecast Across Horizons */}
        <Card>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>
              Highest Forecast
            </span>
            <TrendingUp size={16} color="var(--accent-rose)" />
          </div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.4rem', marginTop: '0.4rem' }}>
            <span style={{ fontSize: '2rem', fontWeight: 800, color: 'var(--accent-rose)', fontFamily: 'var(--font-mono)' }}>
              {peak ? peak.predictedPm25.toFixed(1) : '—'}
            </span>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>µg/m³</span>
          </div>
          <div style={{ fontSize: '0.75rem', color: delta != null && delta >= 0 ? 'var(--accent-rose)' : delta != null ? 'var(--accent-teal)' : 'var(--text-muted)', marginTop: '0.35rem', fontWeight: 600 }}>
            {peak && pctDelta != null ? `${pctDelta >= 0 ? '+' : '−'}${Math.abs(pctDelta)}% from observed` : peak ? 'No T0 observation to compare' : 'No active forecast'}
          </div>
        </Card>

        {/* KPI 3: Forecast Horizon */}
        <Card>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>
              Forecast Horizon
            </span>
            <Clock size={16} color="var(--accent-amber)" />
          </div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.4rem', marginTop: '0.4rem' }}>
            <span style={{ fontSize: '2rem', fontWeight: 800, color: 'var(--accent-amber)', fontFamily: 'var(--font-mono)' }}>
              {peak ? `+${peak.horizonHours}h` : '—'}
            </span>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
              {peak?.targetTime ? new Date(peak.targetTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
            </span>
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.35rem' }}>
            Highest predicted PM2.5
          </div>
        </Card>

        {/* KPI 4: Forecast Confidence (Strictly Honest Null Handling) */}
        <Card>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>
              Forecast Confidence
            </span>
            <ShieldAlert size={16} color="var(--text-muted)" />
          </div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.4rem', marginTop: '0.4rem' }}>
            <span style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--text-secondary)', fontFamily: 'var(--font-sans)' }}>
              {forecast?.forecastConfidence != null
                ? `${Math.round(forecast.forecastConfidence * 100)}%`
                : 'Not available'}
            </span>
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.35rem' }}>
            Prediction ranges are provided instead.
          </div>
        </Card>
      </div>
    </div>
  );
};

export default ForecastSummary;
