import React from 'react';
import { ForecastItem } from '../../types/forecast';
import { Clock, TrendingUp, TrendingDown, Minus } from 'lucide-react';

interface ForecastTimelineProps {
  forecasts?: ForecastItem[];
  currentPm25?: number | null;
}

export const ForecastTimeline: React.FC<ForecastTimelineProps> = ({
  forecasts = [],
  currentPm25,
}) => {
  if (!forecasts || forecasts.length === 0) {
    return (
      <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.875rem' }}>
        No horizon forecast intervals available for this spatial cell.
      </div>
    );
  }

  const hasCurrentPm25 = currentPm25 !== undefined && currentPm25 !== null && !isNaN(currentPm25);

  // Sort strictly by horizonHours ascending [1, 3, 6]
  const sorted = [...forecasts].sort((a, b) => a.horizonHours - b.horizonHours);

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
        gap: '1.25rem',
      }}
    >
      {sorted.map((f) => {
        const delta = hasCurrentPm25 ? Math.round(f.predictedPm25 - currentPm25) : null;
        const isElevated = f.predictedPm25 >= 100;
        const isModerate = f.predictedPm25 >= 60 && f.predictedPm25 < 100;

        const targetFormatted = f.targetTime
          ? new Date(f.targetTime).toLocaleTimeString([], {
              hour: '2-digit',
              minute: '2-digit',
              hour12: true,
            })
          : `+${f.horizonHours}h`;

        const targetDateFormatted = f.targetTime
          ? new Date(f.targetTime).toLocaleDateString([], {
              month: 'short',
              day: 'numeric',
            })
          : '';

        const isPast = f.targetTime ? new Date(f.targetTime).getTime() < Date.now() : false;

        return (
          <div
            key={f.horizonHours}
            data-testid={`forecast-card-${f.horizonHours}h`}
            style={{
              padding: '1.25rem 1.15rem',
              borderRadius: '12px',
              background: isElevated
                ? 'rgba(239, 68, 68, 0.07)'
                : isModerate
                ? 'rgba(245, 158, 11, 0.05)'
                : 'var(--bg-surface-elevated)',
              border: isElevated
                ? '1px solid rgba(239, 68, 68, 0.3)'
                : isModerate
                ? '1px solid rgba(245, 158, 11, 0.25)'
                : '1px solid var(--border-subtle)',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'space-between',
              boxShadow: 'var(--shadow-sm)',
              position: 'relative',
              overflow: 'hidden',
            }}
          >
            {/* Top Row: Horizon + Target Timestamp */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', color: 'var(--text-primary)', fontWeight: 700, fontSize: '0.95rem' }}>
                <Clock size={15} color={isPast ? 'var(--text-muted)' : 'var(--accent-amber)'} />
                <span>+{f.horizonHours} Hour Horizon</span>
              </div>
              <span
                style={{
                  fontSize: '0.72rem',
                  fontWeight: 600,
                  padding: '0.2rem 0.5rem',
                  borderRadius: '5px',
                  background: isPast ? 'rgba(239, 68, 68, 0.12)' : 'var(--bg-glass)',
                  color: isPast ? '#ef4444' : 'var(--text-muted)',
                  border: isPast ? '1px solid rgba(239, 68, 68, 0.3)' : '1px solid var(--border-subtle)',
                }}
              >
                {targetDateFormatted} {targetFormatted} {isPast ? '(Archived)' : ''}
              </span>
            </div>

            {/* PM2.5 Prediction Value */}
            <div style={{ marginBottom: '0.85rem' }}>
              <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase', marginBottom: '0.25rem' }}>
                {isPast ? 'Historical Predicted PM2.5 (Archived)' : 'Predicted PM2.5'}
              </div>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.4rem' }}>
                <span
                  style={{
                    fontSize: '2.25rem',
                    fontWeight: 800,
                    fontFamily: 'var(--font-mono)',
                    color: isElevated ? 'var(--accent-rose)' : isModerate ? 'var(--accent-amber)' : 'var(--text-primary)',
                  }}
                >
                  {f.predictedPm25.toFixed(2)}
                </span>
                <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                  {f.unit === 'ug/m3' ? 'µg/m³' : f.unit}
                </span>
              </div>

              {/* Delta vs T0 */}
              {hasCurrentPm25 && delta != null ? (
                <div
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '0.25rem',
                    fontSize: '0.75rem',
                    color: delta > 0 ? 'var(--accent-rose)' : delta < 0 ? 'var(--accent-teal)' : 'var(--text-muted)',
                    marginTop: '0.35rem',
                    fontWeight: 600,
                  }}
                >
                  {delta > 0 ? <TrendingUp size={13} /> : delta < 0 ? <TrendingDown size={13} /> : <Minus size={13} />}
                  <span>
                    {delta > 0 ? `+${delta}` : delta} {f.unit === 'ug/m3' ? 'µg/m³' : f.unit} vs observed T0
                  </span>
                </div>
              ) : (
                <div
                  style={{
                    fontSize: '0.75rem',
                    color: 'var(--text-muted)',
                    marginTop: '0.35rem',
                  }}
                >
                  No T0 observation to compare
                </div>
              )}
            </div>

            {/* Prediction Range */}
            <div
              style={{
                paddingTop: '0.75rem',
                borderTop: '1px solid var(--border-subtle)',
                fontSize: '0.78rem',
                color: 'var(--text-secondary)',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              }}
            >
              <span style={{ color: 'var(--text-muted)' }}>Prediction range:</span>
              <strong style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-mono)' }}>
                {f.lowerBound.toFixed(2)} – {f.upperBound.toFixed(2)} µg/m³
              </strong>
            </div>
          </div>
        );
      })}
    </div>
  );
};

export default ForecastTimeline;
