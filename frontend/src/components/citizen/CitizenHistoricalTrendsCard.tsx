import React, { useState, useEffect } from 'react';
import {
  TrendingUp,
  TrendingDown,
  Minus,
  RefreshCw,
  Clock,
  Radio,
  AlertCircle,
  Activity,
} from 'lucide-react';
import { AirQualityTrendResponse } from '../../types/citizenPreferences';
import { fetchAirQualityTrends } from '../../services/citizenPreferencesApi';
import { useApp } from '../../store/AppContext';
import { getTranslation } from '../../i18n/citizenTranslations';

interface CitizenHistoricalTrendsCardProps {
  currentLocale?: string;
  cityId?: string;
}

export const CitizenHistoricalTrendsCard: React.FC<CitizenHistoricalTrendsCardProps> = ({
  currentLocale = 'en',
  cityId,
}) => {
  const t = getTranslation(currentLocale);
  const { selectedCity } = useApp();
  const effectiveCityId = cityId || selectedCity?.id || '550e8400-e29b-41d4-a716-446655440001';

  const [trends, setTrends] = useState<AirQualityTrendResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedStation, setSelectedStation] = useState<string>('');
  const [cachedAt, setCachedAt] = useState<Date | null>(null);

  const loadTrends = async (station?: string) => {
    try {
      setError(null);
      const data = await fetchAirQualityTrends(effectiveCityId, station);
      setTrends(data);
      setCachedAt(new Date());
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to fetch historical air trends.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    setLoading(true);
    loadTrends(selectedStation);
  }, [effectiveCityId, selectedStation]);

  const handleRefresh = () => {
    setRefreshing(true);
    loadTrends(selectedStation);
  };

  const renderTrendBadge = (direction?: string) => {
    switch (direction) {
      case 'IMPROVING':
        return (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.25rem', padding: '0.2rem 0.5rem', borderRadius: '9999px', background: '#dcfce7', color: '#166534', fontSize: '0.75rem', fontWeight: 700 }}>
            <TrendingDown size={12} /> IMPROVING
          </span>
        );
      case 'DETERIORATING':
        return (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.25rem', padding: '0.2rem 0.5rem', borderRadius: '9999px', background: '#fee2e2', color: '#991b1b', fontSize: '0.75rem', fontWeight: 700 }}>
            <TrendingUp size={12} /> ELEVATING
          </span>
        );
      case 'STABLE':
        return (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.25rem', padding: '0.2rem 0.5rem', borderRadius: '9999px', background: '#f1f5f9', color: '#475569', fontSize: '0.75rem', fontWeight: 700 }}>
            <Minus size={12} /> STABLE
          </span>
        );
      default:
        return (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.25rem', padding: '0.2rem 0.5rem', borderRadius: '9999px', background: '#f1f5f9', color: '#94a3b8', fontSize: '0.75rem', fontWeight: 600 }}>
            NO DATA
          </span>
        );
    }
  };

  return (
    <div
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border-medium)',
        borderRadius: '1rem',
        padding: '1.5rem',
        boxShadow: 'var(--shadow-sm)',
        display: 'flex',
        flexDirection: 'column',
        gap: '1rem',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
        <div>
          <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 700, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Activity size={18} style={{ color: 'var(--brand-primary)' }} />
            {t.historicalTrendsTitle}
          </h3>
          <p style={{ margin: '0.2rem 0 0', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            {t.historicalTrendsSubtitle}
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <select
            value={selectedStation}
            onChange={(e) => setSelectedStation(e.target.value)}
            style={{
              padding: '0.4rem 0.65rem',
              borderRadius: '0.375rem',
              border: '1px solid var(--border-medium)',
              background: 'var(--bg-surface-elevated)',
              color: 'var(--text-primary)',
              fontSize: '0.8rem',
            }}
          >
            <option value="">All City Stations</option>
            <option value="PUN-001">Shivajinagar (PUN-001)</option>
            <option value="PUN-002">Katraj (PUN-002)</option>
            <option value="PUN-003">Hadapsar Industrial (PUN-003)</option>
          </select>

          <button
            onClick={handleRefresh}
            disabled={refreshing}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.35rem',
              padding: '0.45rem 0.75rem',
              borderRadius: '0.5rem',
              background: 'transparent',
              border: '1px solid var(--border-medium)',
              color: 'var(--text-secondary)',
              fontSize: '0.78rem',
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            <RefreshCw size={13} className={refreshing ? 'spin' : ''} />
            {refreshing ? 'Updating...' : t.refreshBtn}
          </button>
        </div>
      </div>

      {cachedAt && (
        <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
          <Clock size={12} />
          <span>Last synchronized: {cachedAt.toLocaleTimeString()} (truthful cache with zero synthetic fill)</span>
        </div>
      )}

      {error && (
        <div style={{ padding: '0.65rem 0.85rem', borderRadius: '0.5rem', background: '#fee2e2', border: '1px solid #fca5a5', color: '#b91c1c', fontSize: '0.8rem' }}>
          {error}
        </div>
      )}

      {loading ? (
        <div style={{ textAlign: 'center', padding: '1.5rem', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
          Loading historical telemetry series...
        </div>
      ) : trends && trends.observations.length > 0 ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {/* Summary Stat Cards */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: '0.75rem' }}>
            <div style={{ padding: '0.75rem', borderRadius: '0.5rem', background: 'var(--bg-surface-elevated)', border: '1px solid var(--border-subtle)' }}>
              <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', fontWeight: 600 }}>LATEST PM2.5</div>
              <div style={{ fontSize: '1.25rem', fontWeight: 800, color: 'var(--text-primary)', marginTop: '0.2rem' }}>
                {trends.summary.latestPm25?.toFixed(1) ?? '—'} <span style={{ fontSize: '0.75rem', fontWeight: 500 }}>µg/m³</span>
              </div>
            </div>

            <div style={{ padding: '0.75rem', borderRadius: '0.5rem', background: 'var(--bg-surface-elevated)', border: '1px solid var(--border-subtle)' }}>
              <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', fontWeight: 600 }}>AVERAGE PM2.5</div>
              <div style={{ fontSize: '1.25rem', fontWeight: 800, color: 'var(--text-primary)', marginTop: '0.2rem' }}>
                {trends.summary.avgPm25?.toFixed(1) ?? '—'} <span style={{ fontSize: '0.75rem', fontWeight: 500 }}>µg/m³</span>
              </div>
            </div>

            <div style={{ padding: '0.75rem', borderRadius: '0.5rem', background: 'var(--bg-surface-elevated)', border: '1px solid var(--border-subtle)' }}>
              <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', fontWeight: 600 }}>MIN - MAX RANGE</div>
              <div style={{ fontSize: '1rem', fontWeight: 800, color: 'var(--text-primary)', marginTop: '0.35rem' }}>
                {trends.summary.minPm25?.toFixed(0) ?? '—'} - {trends.summary.maxPm25?.toFixed(0) ?? '—'} <span style={{ fontSize: '0.75rem', fontWeight: 500 }}>µg/m³</span>
              </div>
            </div>

            <div style={{ padding: '0.75rem', borderRadius: '0.5rem', background: 'var(--bg-surface-elevated)', border: '1px solid var(--border-subtle)', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
              <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', fontWeight: 600, marginBottom: '0.25rem' }}>TREND STATUS</div>
              <div>{renderTrendBadge(trends.summary.trendDirection)}</div>
            </div>
          </div>

          {/* Simple Visual Time-Series Bar */}
          <div style={{ padding: '1rem', borderRadius: '0.75rem', background: 'var(--bg-surface-elevated)', border: '1px solid var(--border-subtle)' }}>
            <div style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '0.75rem' }}>
              Chronological Observation Timeline (Most Recent {Math.min(trends.observations.length, 12)} Readings)
            </div>

            <div style={{ display: 'flex', alignItems: 'flex-end', gap: '0.5rem', height: '100px', paddingBottom: '0.5rem', borderBottom: '1px solid var(--border-medium)', overflowX: 'auto' }}>
              {trends.observations.slice(-12).map((pt, idx) => {
                const heightPct = pt.pm25 ? Math.min(Math.max((pt.pm25 / 120) * 100, 10), 100) : 10;
                const barColor = pt.pm25 && pt.pm25 > 90 ? '#ef4444' : pt.pm25 && pt.pm25 > 60 ? '#f59e0b' : '#10b981';
                const timeStr = new Date(pt.observedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

                return (
                  <div key={idx} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.25rem', minWidth: '42px', flex: 1 }}>
                    <div style={{ fontSize: '0.65rem', fontWeight: 700, color: 'var(--text-secondary)' }}>
                      {pt.pm25?.toFixed(0)}
                    </div>
                    <div
                      style={{
                        width: '100%',
                        maxWidth: '28px',
                        height: `${heightPct}px`,
                        background: barColor,
                        borderRadius: '4px 4px 0 0',
                        transition: 'height 0.3s ease',
                      }}
                      title={`${pt.pm25} µg/m³ (${pt.source}) at ${new Date(pt.observedAt).toLocaleString()}`}
                    />
                    <div style={{ fontSize: '0.62rem', color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>
                      {timeStr}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Transparent Scientific Disclaimer */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.75rem', color: 'var(--text-muted)', fontStyle: 'italic' }}>
            <AlertCircle size={14} style={{ color: 'var(--brand-primary)', flexShrink: 0 }} />
            <span>{trends.honestMissingDataNote}</span>
          </div>
        </div>
      ) : (
        <div style={{ textAlign: 'center', padding: '1.5rem', background: 'var(--bg-surface-elevated)', borderRadius: '0.75rem', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
          No historical air quality observations recorded for this location yet. Check back as periodic CAAQMS stations report telemetry.
        </div>
      )}
    </div>
  );
};
