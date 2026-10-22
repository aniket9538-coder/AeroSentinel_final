import React, { useState, useEffect, useRef } from 'react';
import { useSearchParams, useLocation, Link } from 'react-router-dom';
import { PageContainer } from '../../components/layout/PageContainer';
import { Card } from '../../components/common/Card';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { EmptyState } from '../../components/common/EmptyState';
import { ErrorState } from '../../components/common/ErrorState';
import { Loading } from '../../components/common/Loading';
import { ForecastSummary } from '../../components/forecasting/ForecastSummary';
import { ForecastTimeline } from '../../components/forecasting/ForecastTimeline';
import { ForecastChart } from '../../components/charts/ForecastChart';
import { useApp } from '../../store/AppContext';
import { useForecast } from '../../hooks/useForecast';
import {
  TrendingUp,
  Brain,
  Clock,
  RefreshCw,
  Layers,
  Info,
  Calendar,
  Sparkles,
  AlertTriangle,
  Hexagon,
  MapPin,
  ShieldCheck,
  CheckCircle2,
} from 'lucide-react';

// Authoritative monitoring locations with known H3 cell associations
const PRESET_CELLS = [
  {
    h3Index: '88608850e5fffff',
    cityName: 'Pune',
    areaName: 'Shivajinagar',
    stationName: 'Shivajinagar CAAQMS (PUN-001)',
    stationId: 'PUN-001',
    isPrimary: true,
  },
  {
    h3Index: '88608852c1fffff',
    cityName: 'Pune',
    areaName: 'Katraj',
    stationName: 'Katraj Air Station (PUN-002)',
    stationId: 'PUN-002',
    isPrimary: false,
  },
  {
    h3Index: '8860885357fffff',
    cityName: 'Pune',
    areaName: 'Hadapsar',
    stationName: 'Hadapsar Industrial Zone (PUN-003)',
    stationId: 'PUN-003',
    isPrimary: false,
  },
  {
    h3Index: '88608b56b3fffff',
    cityName: 'Mumbai',
    areaName: 'Kurla',
    stationName: 'Kurla Station (MUM-001)',
    stationId: 'MUM-001',
    isPrimary: false,
  },
  {
    h3Index: '883da11505fffff',
    cityName: 'Delhi',
    areaName: 'R K Puram',
    stationName: 'R K Puram Station (DEL-001)',
    stationId: 'DEL-001',
    isPrimary: false,
  },
];

export const Forecast: React.FC = () => {
  const { selectedCity, stations } = useApp();
  const [searchParams, setSearchParams] = useSearchParams();
  const location = useLocation();

  // Resolve initial H3 with strict F3 continuity priority:
  // 1. URL search parameter ?h3=...
  // 2. React Router location state: location.state?.h3Index
  // 3. Session storage preserved from F3 selection
  // 4. Default to Pune Shivajinagar baseline cell
  const getInitialH3 = (): string => {
    const urlH3 = searchParams.get('h3');
    if (urlH3 && /^88[0-9a-f]{13}$/i.test(urlH3.trim())) {
      return urlH3.trim();
    }
    const stateH3 = (location.state as any)?.h3Index;
    if (stateH3 && /^88[0-9a-f]{13}$/i.test(stateH3.trim())) {
      return stateH3.trim();
    }
    const storedH3 =
      typeof sessionStorage !== 'undefined'
        ? sessionStorage.getItem('aerosentinel_selected_h3')
        : null;
    if (storedH3 && /^88[0-9a-f]{13}$/i.test(storedH3.trim())) {
      return storedH3.trim();
    }
    return '88608850e5fffff';
  };

  const [selectedH3, setSelectedH3] = useState<string>(getInitialH3);
  const [customH3Input, setCustomH3Input] = useState<string>('');

  // Synchronize when URL ?h3=... parameter changes externally
  const urlH3 = searchParams.get('h3');
  useEffect(() => {
    if (urlH3 && /^88[0-9a-f]{13}$/i.test(urlH3.trim())) {
      setSelectedH3(urlH3.trim());
      sessionStorage.setItem('aerosentinel_selected_h3', urlH3.trim());
    }
  }, [urlH3]);

  // Persist selectedH3 across session
  useEffect(() => {
    if (selectedH3) {
      sessionStorage.setItem('aerosentinel_selected_h3', selectedH3);
    }
  }, [selectedH3]);

  // Fetch forecast using our typed hook
  const { forecast, loading, error, refresh } = useForecast(selectedH3);

  // Sync default cell ONLY when user genuinely switches city in the Navbar dropdown
  const previousCityIdRef = useRef<string | undefined>(selectedCity?.id);
  useEffect(() => {
    if (!selectedCity?.id) return;
    if (previousCityIdRef.current && previousCityIdRef.current !== selectedCity.id) {
      const cityPreset = PRESET_CELLS.find((c) =>
        c.cityName.toLowerCase() === selectedCity.name.toLowerCase()
      );
      if (cityPreset) {
        setSelectedH3(cityPreset.h3Index);
        setSearchParams({ h3: cityPreset.h3Index }, { replace: true });
      }
    }
    previousCityIdRef.current = selectedCity.id;
  }, [selectedCity, setSearchParams]);

  // Active preset metadata resolution
  const activePreset = PRESET_CELLS.find((c) => c.h3Index === selectedH3);

  // Derive observed PM2.5 for the selected cell from verified station telemetry only (never fabricate fallback defaults)
  const matchingStation = activePreset
    ? stations.find((s) => s.stationId === activePreset.stationId)
    : undefined;

  const observedPm25 = matchingStation?.pm25;

  const handleSelectCell = (h3: string) => {
    setSelectedH3(h3);
    setSearchParams({ h3 }, { replace: true });
    setCustomH3Input('');
  };

  const handleCustomH3Submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (customH3Input.trim()) {
      const trimmed = customH3Input.trim();
      setSelectedH3(trimmed);
      setSearchParams({ h3: trimmed }, { replace: true });
    }
  };

  // Determine freshness badge variant and label
  const freshness = forecast?.freshness || 'NO_DATA';
  const freshnessBadgeVariant =
    freshness === 'LIVE' ? 'success' : freshness === 'STALE' ? 'warning' : 'neutral';

  // Dynamically resolve an available forecast cell from known presets (excluding current cell)
  const availablePreset =
    PRESET_CELLS.find((cell) => cell.isPrimary && cell.h3Index !== selectedH3) ||
    PRESET_CELLS.find((cell) => cell.h3Index !== selectedH3);

  return (
    <PageContainer
      title="Short-Term PM2.5 Forecast"
      subtitle="Predicted PM2.5 for the next 1, 3, and 6 hours"
      actions={
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          <Button variant="outline" size="sm" onClick={() => refresh()} disabled={loading}>
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} style={{ marginRight: '0.4rem' }} />
            <span>Refresh</span>
          </Button>
          <Link to={`/analyst/evidence?h3=${encodeURIComponent(selectedH3)}`} style={{ textDecoration: 'none' }}>
            <Button variant="outline" size="sm">
              <Sparkles size={14} style={{ marginRight: '0.4rem' }} />
              <span>Evidence & WHY</span>
            </Button>
          </Link>
          <Badge variant={freshnessBadgeVariant} size="sm">
            {loading ? 'FETCHING...' : freshness}
          </Badge>
        </div>
      }
    >
      {/* H3 Spatial Cell Selector Bar */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0.85rem 1.15rem',
          borderRadius: '12px',
          background: 'var(--bg-surface-elevated)',
          border: '1px solid var(--border-subtle)',
          flexWrap: 'wrap',
          gap: '1rem',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', color: 'var(--text-secondary)', fontSize: '0.825rem', fontWeight: 600 }}>
            <Hexagon size={16} color="var(--brand-primary)" />
            <span>Location:</span>
          </div>
          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            {PRESET_CELLS.map((cell) => {
              const isSelected = selectedH3 === cell.h3Index;
              return (
                <button
                  key={cell.h3Index}
                  type="button"
                  data-testid={`cell-btn-${cell.h3Index}`}
                  title={`Uber H3: ${cell.h3Index} (${cell.stationName})`}
                  onClick={() => handleSelectCell(cell.h3Index)}
                  style={{
                    padding: '0.35rem 0.75rem',
                    borderRadius: '6px',
                    fontSize: '0.78rem',
                    cursor: 'pointer',
                    transition: 'all 0.15s ease',
                    background: isSelected ? 'var(--brand-primary)' : 'var(--bg-surface)',
                    color: isSelected ? '#ffffff' : 'var(--text-secondary)',
                    border: isSelected ? '1px solid var(--brand-primary)' : '1px solid var(--border-subtle)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.4rem',
                  }}
                >
                  <span style={{ fontWeight: isSelected ? 700 : 600 }}>
                    {cell.cityName} · {cell.areaName}
                  </span>
                  <span
                    style={{
                      fontSize: '0.7rem',
                      opacity: isSelected ? 0.9 : 0.65,
                      fontFamily: 'var(--font-mono)',
                    }}
                  >
                    H3 {cell.h3Index.slice(0, 8)}…
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Manual H3 Cell Query Form */}
        <form onSubmit={handleCustomH3Submit} style={{ display: 'flex', gap: '0.5rem' }}>
          <input
            type="text"
            placeholder="Custom H3 Index..."
            value={customH3Input}
            onChange={(e) => setCustomH3Input(e.target.value)}
            style={{
              padding: '0.35rem 0.65rem',
              borderRadius: '6px',
              fontSize: '0.78rem',
              background: 'var(--bg-surface)',
              border: '1px solid var(--border-subtle)',
              color: 'var(--text-primary)',
              fontFamily: 'var(--font-mono)',
              width: '160px',
            }}
          />
          <Button size="sm" variant="secondary" type="submit">
            Query
          </Button>
        </form>
      </div>

      {/* Main Content Conditional States */}
      {loading ? (
        <div style={{ padding: '3rem 0' }}>
          <Loading message="Retrieving real multi-horizon forecast from Spring Boot..." />
        </div>
      ) : error ? (
        <div style={{ maxWidth: '520px', margin: '0.5rem auto' }}>
          <ErrorState
            title="Forecast Service Unavailable"
            message={error}
            onRetry={refresh}
          />
        </div>
      ) : !forecast || forecast.status === 'NO_DATA' || freshness === 'NO_DATA' || !forecast.forecasts || forecast.forecasts.length === 0 ? (
        <div style={{ width: '100%', display: 'flex', justifyContent: 'center' }}>
          <EmptyState
            title="No forecast available for this cell"
            message="No forecast has been generated for this H3 cell yet."
            actionLabel={availablePreset ? 'View available forecast' : undefined}
            onAction={availablePreset ? () => handleSelectCell(availablePreset.h3Index) : undefined}
            style={{ margin: '0.5rem auto 1.5rem auto', maxWidth: '460px', padding: '2rem 1.5rem' }}
          />
        </div>
      ) : (forecast.status === ('UNAVAILABLE' as any) && (!forecast.forecasts || forecast.forecasts.length === 0)) ? (
        <div style={{ maxWidth: '520px', margin: '0.5rem auto' }}>
          <ErrorState
            title="Forecast Telemetry Unavailable"
            message="No current forecast is available for this H3 cell."
            onRetry={refresh}
            retryLabel="Refresh Forecast"
          />
        </div>
      ) : (
        <>
          {/* Archived Telemetry Warning Banner if freshness is UNAVAILABLE (>24h) */}
          {freshness === 'UNAVAILABLE' && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '0.6rem',
                padding: '0.65rem 1rem',
                borderRadius: '8px',
                background: 'rgba(239, 68, 68, 0.1)',
                border: '1px solid rgba(239, 68, 68, 0.35)',
                color: 'var(--accent-rose, #f87171)',
                fontSize: '0.825rem',
                marginBottom: '1.25rem',
              }}
            >
              <Clock size={16} />
              <span>
                <strong>Archived Forecast Telemetry (&gt;24h):</strong> Displaying persisted forecast generated on{' '}
                {new Date(forecast.generatedAt).toLocaleDateString()} at {new Date(forecast.generatedAt).toLocaleTimeString()}. Real-time conditions may have evolved.
              </span>
            </div>
          )}

          {/* Stale Warning Banner if applicable */}
          {freshness === 'STALE' && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '0.6rem',
                padding: '0.65rem 1rem',
                borderRadius: '8px',
                background: 'rgba(245, 158, 11, 0.1)',
                border: '1px solid rgba(245, 158, 11, 0.35)',
                color: 'var(--accent-amber)',
                fontSize: '0.825rem',
                marginBottom: '1.25rem',
              }}
            >
              <AlertTriangle size={16} />
              <span>
                <strong>Forecast is Stale:</strong> The latest persisted forecast is older than 2 hours. Telemetry conditions may have changed.
              </span>
            </div>
          )}

          {/* Top 4 Summary KPI Cards */}
          <div style={{ marginBottom: '1.75rem' }}>
            <ForecastSummary
              forecast={forecast}
              currentPm25={observedPm25}
              cityName={activePreset?.cityName || selectedCity?.name || 'Pune'}
            />
          </div>

          {/* Forecast Trajectory Chart */}
          <div style={{ marginBottom: '1.75rem' }}>
            <Card
              title={
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                  <TrendingUp size={20} color="var(--accent-amber)" />
                  <span>PM2.5 Forecast Trajectory</span>
                </div>
              }
              subtitle="Observed base (T0) and predicted PM2.5 for +1h, +3h, and +6h lead times"
              badge={
                <div style={{ display: 'flex', gap: '0.5rem' }}>
                  <Badge variant="info">H3: {forecast.h3Index.slice(0, 10)}…</Badge>
                  <Badge variant="neutral">{forecast.modelVersion}</Badge>
                </div>
              }
            >
              <ForecastChart
                forecasts={forecast.forecasts}
                currentPm25={observedPm25}
                baseTimestamp={forecast.baseTimestamp}
                height={340}
              />
            </Card>
          </div>

          {/* Forecast by Horizon Section */}
          <div style={{ marginBottom: '1.75rem' }}>
            <Card
              title="Forecast by Horizon"
              subtitle="Predicted PM2.5 and prediction range"
            >
              <ForecastTimeline
                forecasts={forecast.forecasts}
                currentPm25={observedPm25}
              />
            </Card>
          </div>

          {/* Lineage Summary & Methodology Details */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '1.25rem' }}>
            {/* User-facing Provenance Summary */}
            <Card title="Forecast Provenance">
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.55rem', fontSize: '0.8rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.45rem 0.65rem', background: 'var(--bg-surface-elevated)', borderRadius: '6px' }}>
                  <span style={{ color: 'var(--text-muted)' }}>Location:</span>
                  <strong style={{ color: 'var(--text-primary)' }}>
                    {activePreset
                      ? `${activePreset.cityName} · ${activePreset.areaName} (${activePreset.stationName})`
                      : `H3 Cell: ${selectedH3}`}
                  </strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.45rem 0.65rem', background: 'var(--bg-surface-elevated)', borderRadius: '6px' }}>
                  <span style={{ color: 'var(--text-muted)' }}>Based on observation:</span>
                  <strong style={{ color: 'var(--text-primary)' }}>
                    {forecast.baseTimestamp ? new Date(forecast.baseTimestamp).toLocaleString() : '—'}
                  </strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.45rem 0.65rem', background: 'var(--bg-surface-elevated)', borderRadius: '6px' }}>
                  <span style={{ color: 'var(--text-muted)' }}>Forecast generated:</span>
                  <strong style={{ color: 'var(--text-primary)' }}>
                    {new Date(forecast.generatedAt).toLocaleString()}
                  </strong>
                </div>

                {/* Compact Technical Identifiers */}
                <div style={{ marginTop: '0.35rem', paddingTop: '0.45rem', borderTop: '1px solid var(--border-subtle)', display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.72rem' }}>
                    <span style={{ color: 'var(--text-muted)' }}>F3 Parent Prediction ID:</span>
                    <span style={{ color: 'var(--text-secondary)', fontFamily: 'var(--font-mono)' }}>
                      {forecast.parentPredictionId || '—'}
                    </span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.72rem' }}>
                    <span style={{ color: 'var(--text-muted)' }}>Feature Snapshot ID:</span>
                    <span style={{ color: 'var(--text-secondary)', fontFamily: 'var(--font-mono)' }}>
                      {forecast.featureSnapshotId || '—'}
                    </span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.72rem' }}>
                    <span style={{ color: 'var(--text-muted)' }}>Spatial Hex Index:</span>
                    <span style={{ color: 'var(--text-secondary)', fontFamily: 'var(--font-mono)' }}>
                      {forecast.h3Index}
                    </span>
                  </div>
                </div>
              </div>
            </Card>

            {/* Methodology Section */}
            <Card title="How this forecast is generated">
              <div style={{ fontSize: '0.825rem', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                <p style={{ marginBottom: '0.75rem', color: 'var(--text-primary)' }}>
                  Random Forest multi-horizon models use weather, spatial, monitoring and GIS features.
                </p>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.45rem', fontSize: '0.8rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.45rem 0.65rem', background: 'var(--bg-surface-elevated)', borderRadius: '6px' }}>
                    <span style={{ color: 'var(--text-muted)' }}>Spatial grid:</span>
                    <strong style={{ color: 'var(--text-primary)' }}>H3 Resolution 8 (~0.73 km²)</strong>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.45rem 0.65rem', background: 'var(--bg-surface-elevated)', borderRadius: '6px' }}>
                    <span style={{ color: 'var(--text-muted)' }}>Horizons:</span>
                    <strong style={{ color: 'var(--text-primary)' }}>1h · 3h · 6h</strong>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.45rem 0.65rem', background: 'var(--bg-surface-elevated)', borderRadius: '6px' }}>
                    <span style={{ color: 'var(--text-muted)' }}>Prediction range:</span>
                    <strong style={{ color: 'var(--brand-primary)' }}>Empirical P10–P90</strong>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.45rem 0.65rem', background: 'var(--bg-surface-elevated)', borderRadius: '6px' }}>
                    <span style={{ color: 'var(--text-muted)' }}>Bounds safeguard:</span>
                    <strong style={{ color: 'var(--brand-primary)' }}>Physical lower bound (≥ 0.0 µg/m³)</strong>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.45rem 0.65rem', background: 'var(--bg-surface-elevated)', borderRadius: '6px' }}>
                    <span style={{ color: 'var(--text-muted)' }}>Model Status:</span>
                    <strong style={{ color: 'var(--accent-amber)' }}>Experimental ML (Baseline Comparison Disclosed)</strong>
                  </div>
                </div>
                <div style={{ marginTop: '0.85rem', padding: '0.65rem', background: 'var(--bg-surface-elevated)', borderRadius: '6px', fontSize: '0.75rem', border: '1px solid var(--border-subtle)' }}>
                  <strong style={{ color: 'var(--text-primary)' }}>Audit Benchmark Disclosure:</strong>
                  <p style={{ margin: '0.25rem 0 0 0', color: 'var(--text-muted)' }}>
                    In offline testing across &gt;9,000 holdout rows, a naive persistence baseline (repeating T0) outperformed the Random Forest regressor on short horizons (T+1h MAE 1.20 vs 2.97 µg/m³; T+3h MAE 3.62 vs 5.17 µg/m³; T+6h MAE 5.64 vs 6.71 µg/m³). These predictions represent experimental ML outputs for research and analysis, not certified authoritative guidance.
                  </p>
                </div>
              </div>
            </Card>
          </div>
        </>
      )}
    </PageContainer>
  );
};

export default Forecast;
