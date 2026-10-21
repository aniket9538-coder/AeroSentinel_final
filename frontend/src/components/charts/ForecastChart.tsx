import React from 'react';
import {
  ResponsiveContainer,
  ComposedChart,
  Line,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  ReferenceLine,
} from 'recharts';
import { ForecastItem } from '../../types/forecast';
import { useApp } from '../../store/AppContext';

export interface ForecastChartProps {
  forecasts?: ForecastItem[];
  currentPm25?: number | null;
  baseTimestamp?: string | null;
  data?: { hour: string; predicted: number; lower?: number; upper?: number }[];
  height?: number;
}

export const ForecastChart: React.FC<ForecastChartProps> = ({
  forecasts = [],
  currentPm25,
  baseTimestamp,
  data,
  height = 340,
}) => {
  const { theme } = useApp();
  const isDark = theme === 'dark';

  // Format base timestamp label
  const baseLabel = baseTimestamp
    ? `T0 (${new Date(baseTimestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })})`
    : 'T0 (Observed)';

  const hasBaseObs = currentPm25 !== undefined && currentPm25 !== null && !isNaN(currentPm25);

  const horizonPoints = ([...forecasts])
    .sort((a, b) => a.horizonHours - b.horizonHours)
    .map((f) => {
      const timeFormatted = f.targetTime
        ? new Date(f.targetTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        : `+${f.horizonHours}h`;
      return {
        timeLabel: `+${f.horizonHours}h (${timeFormatted})`,
        horizonHours: f.horizonHours,
        observedPm25: undefined,
        forecastPm25: f.predictedPm25,
        lowerBound: f.lowerBound,
        upperBound: f.upperBound,
        isBase: false,
      };
    });

  // Build chart data: support both new forecasts array and optional legacy dashboard data
  const chartData = data
    ? data.map((d) => ({
        timeLabel: d.hour,
        horizonHours: 0,
        observedPm25: d.hour.toUpperCase().includes('NOW') ? d.predicted : undefined,
        forecastPm25: d.predicted,
        lowerBound: d.lower ?? d.predicted,
        upperBound: d.upper ?? d.predicted,
        isBase: d.hour.toUpperCase().includes('NOW'),
      }))
    : hasBaseObs
    ? [
        {
          timeLabel: baseLabel,
          horizonHours: 0,
          observedPm25: currentPm25,
          forecastPm25: currentPm25,
          lowerBound: currentPm25,
          upperBound: currentPm25,
          isBase: true,
        },
        ...horizonPoints,
      ]
    : horizonPoints;

  const gridColor = isDark ? 'rgba(255, 255, 255, 0.07)' : 'rgba(15, 23, 42, 0.08)';
  const textColor = isDark ? '#94a3b8' : '#64748b';
  const tooltipBg = isDark ? '#0f172a' : '#ffffff';
  const tooltipBorder = isDark ? 'rgba(255, 255, 255, 0.15)' : '#cbd5e1';

  return (
    <div style={{ width: '100%', height }}>
      {/* Legend Header (Clean & Uncluttered) */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          fontSize: '0.78rem',
          marginBottom: '0.75rem',
          color: 'var(--text-secondary)',
          flexWrap: 'wrap',
          gap: '0.75rem',
        }}
      >
        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 500 }}>
          PM2.5 Concentration (µg/m³) vs Horizon
        </span>
        <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
            <span
              style={{
                width: '12px',
                height: '12px',
                borderRadius: '50%',
                background: 'var(--brand-primary)',
                border: '2px solid rgba(255, 255, 255, 0.8)',
              }}
            />
            <span style={{ fontWeight: 600, color: 'var(--brand-primary)' }}>Observed</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
            <span
              style={{
                width: '14px',
                height: '3px',
                background: 'var(--accent-amber)',
                borderRadius: '2px',
              }}
            />
            <span style={{ fontWeight: 600, color: 'var(--accent-amber)' }}>Forecast</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
            <span
              style={{
                width: '12px',
                height: '10px',
                background: 'rgba(245, 158, 11, 0.22)',
                borderRadius: '2px',
                border: '1px solid rgba(245, 158, 11, 0.5)',
              }}
            />
            <span>Prediction range</span>
            <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>(P10–P90 empirical range)</span>
          </div>
        </div>
      </div>

      <ResponsiveContainer width="100%" height={height - 40}>
        <ComposedChart data={chartData} margin={{ top: 15, right: 35, left: -5, bottom: 15 }}>
          <defs>
            <linearGradient id="forecastUncertaintyBand" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="var(--accent-amber)" stopOpacity={0.28} />
              <stop offset="95%" stopColor="var(--accent-amber)" stopOpacity={0.04} />
            </linearGradient>
          </defs>

          <CartesianGrid strokeDasharray="3 3" stroke={gridColor} vertical={false} />

          <XAxis
            dataKey="timeLabel"
            stroke={textColor}
            fontSize={12}
            tickLine={false}
            dy={8}
          />

          <YAxis
            stroke={textColor}
            fontSize={12}
            tickLine={false}
            tickFormatter={(val) => `${val}`}
            domain={['auto', 'auto']}
          />

          <Tooltip
            content={({ active, payload }) => {
              if (!active || !payload || !payload.length) return null;
              const point: any = payload[0].payload;

              return (
                <div
                  style={{
                    background: tooltipBg,
                    border: `1px solid ${tooltipBorder}`,
                    borderRadius: '8px',
                    padding: '0.65rem 0.85rem',
                    boxShadow: '0 4px 12px rgba(0,0,0,0.2)',
                    fontSize: '0.8rem',
                    color: isDark ? '#f8fafc' : '#0f172a',
                  }}
                >
                  <div style={{ fontWeight: 700, marginBottom: '0.35rem', color: 'var(--text-primary)' }}>
                    {point.timeLabel}
                  </div>
                  {point.isBase ? (
                    <div style={{ color: 'var(--brand-primary)', fontWeight: 600 }}>
                      Observed: {point.observedPm25} µg/m³
                    </div>
                  ) : (
                    <>
                      <div style={{ color: 'var(--accent-amber)', fontWeight: 600 }}>
                        Forecast: {point.forecastPm25?.toFixed(2)} µg/m³
                      </div>
                      <div style={{ color: 'var(--text-secondary)', fontSize: '0.75rem', marginTop: '0.2rem' }}>
                        Prediction range: {point.lowerBound?.toFixed(2)} – {point.upperBound?.toFixed(2)} µg/m³
                      </div>
                    </>
                  )}
                </div>
              );
            }}
          />

          {/* Reference line at 60 ug/m3 (NAAQS Standard threshold) - positioned inside to prevent right-edge clipping */}
          <ReferenceLine
            y={60}
            stroke="rgba(239, 68, 68, 0.45)"
            strokeDasharray="4 4"
            label={{
              value: 'NAAQS Standard: 60 µg/m³',
              fill: 'var(--text-muted)',
              fontSize: 10,
              position: 'insideTopLeft',
            }}
          />

          {/* Uncertainty Envelope Band (Area from lower to upper bound) */}
          <Area
            type="monotone"
            dataKey="upperBound"
            stroke="none"
            fill="url(#forecastUncertaintyBand)"
          />
          <Area
            type="monotone"
            dataKey="lowerBound"
            stroke="none"
            fill={isDark ? '#0b1120' : '#ffffff'}
          />

          {/* Lower bound line */}
          <Line
            type="monotone"
            dataKey="lowerBound"
            stroke="rgba(245, 158, 11, 0.4)"
            strokeDasharray="3 3"
            dot={false}
            isAnimationActive={false}
          />

          {/* Upper bound line */}
          <Line
            type="monotone"
            dataKey="upperBound"
            stroke="rgba(245, 158, 11, 0.4)"
            strokeDasharray="3 3"
            dot={false}
            isAnimationActive={false}
          />

          {/* Main Forecast Regressor Line */}
          <Line
            type="monotone"
            dataKey="forecastPm25"
            stroke="var(--accent-amber)"
            strokeWidth={2.5}
            dot={(props: any) => {
              // Avoid duplicate dot at T0 base observation point
              if (props.payload?.isBase) return <svg key={props.key} />;
              return (
                <circle
                  key={props.key}
                  cx={props.cx}
                  cy={props.cy}
                  r={5}
                  fill="var(--accent-amber)"
                  stroke={isDark ? '#0f172a' : '#fff'}
                  strokeWidth={2}
                />
              );
            }}
            activeDot={{ r: 7 }}
          />

          {/* Observed Base Point Marker */}
          <Line
            type="monotone"
            dataKey="observedPm25"
            stroke="var(--brand-primary)"
            strokeWidth={0}
            dot={{ r: 6.5, fill: 'var(--brand-primary)', stroke: '#fff', strokeWidth: 2 }}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
};

export default ForecastChart;
