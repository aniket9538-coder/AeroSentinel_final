import React from 'react';
import { Cpu, AlertTriangle, Info, CheckCircle2, ShieldAlert } from 'lucide-react';
import { PM25RiskToolResult } from '../../types/assistant';

export interface CitizenPM25RiskCardProps {
  riskResult: PM25RiskToolResult | null;
  selectedCityName?: string;
  loading: boolean;
  error: string | null;
}

export const CitizenPM25RiskCard: React.FC<CitizenPM25RiskCardProps> = ({
  riskResult,
  selectedCityName = 'Pune',
  loading,
  error,
}) => {
  if (loading && !riskResult) {
    return (
      <div className="citizen-card" aria-busy="true">
        <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
          Evaluating calibrated ML hazard models...
        </div>
      </div>
    );
  }

  const isPune = selectedCityName.toLowerCase().includes('pune');
  const isDomainUnsupported = !isPune || riskResult?.status === 'MODEL_DOMAIN_UNSUPPORTED';
  const isAvailable = !isDomainUnsupported && riskResult && riskResult.status === 'VALID';

  const hazardLevel = riskResult?.hazardLevel || 'LOW';
  const getHazardColor = (lvl: string) => {
    switch (lvl) {
      case 'CRITICAL': return '#ef4444';
      case 'HIGH': return '#f97316';
      case 'MODERATE': return '#f59e0b';
      case 'LOW':
      default: return '#10b981';
    }
  };

  return (
    <div className="citizen-card" role="region" aria-label="Experimental PM2.5 Machine Learning Risk">
      {/* Header with EXPERIMENTAL_ML Badge */}
      <div className="citizen-card-header">
        <div className="citizen-card-title-group">
          <div className="citizen-card-icon" style={{ background: 'rgba(168, 85, 247, 0.15)', color: '#a855f7' }}>
            <Cpu size={18} />
          </div>
          <div>
            <h2 className="citizen-card-title">Emerging PM2.5 Hazard Risk</h2>
            <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
              AeroSentinel F3 Calibrated ML Classifier
            </div>
          </div>
        </div>

        <span className="citizen-card-badge badge-experimental">
          EXPERIMENTAL_ML
        </span>
      </div>

      {/* Case 1: Outside Pune Domain */}
      {isDomainUnsupported ? (
        <div
          style={{
            padding: '1.1rem',
            borderRadius: '10px',
            background: 'var(--bg-surface)',
            border: '1px solid var(--border-subtle)',
            display: 'flex',
            flexDirection: 'column',
            gap: '0.5rem',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#f59e0b', fontWeight: 700, fontSize: '0.88rem' }}>
            <Info size={16} />
            <span>Capability Unavailable: Geographic Domain Boundary</span>
          </div>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', lineHeight: 1.45 }}>
            AeroSentinel predictive ML hotspot models are calibrated strictly within the <strong>Pune Metropolitan Region</strong>. Operational spatial regressors are not yet validated for {selectedCityName}.
          </div>
          <div style={{ fontSize: '0.74rem', color: 'var(--text-muted)' }}>
            To prevent misinformation, numerical risk projections are withheld outside the calibrated boundary.
          </div>
        </div>
      ) : isAvailable ? (
        /* Case 2: Pune Domain Valid Prediction */
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.75rem' }}>
            <div>
              <div style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                Predicted Hazard Tier
              </div>
              <div style={{ fontSize: '1.75rem', fontWeight: 800, color: getHazardColor(hazardLevel), fontFamily: 'var(--font-heading)' }}>
                {hazardLevel} RISK
              </div>
            </div>

            <div style={{ textAlign: 'right', fontSize: '0.78rem', color: 'var(--text-secondary)' }}>
              <div>Domain: <strong>Pune Metropolitan</strong></div>
              <div>Spatial Index: <code style={{ fontSize: '0.74rem', background: 'var(--bg-surface)', padding: '0.1rem 0.3rem', borderRadius: '4px' }}>{riskResult.h3Index || '88608850e5fffff'}</code></div>
              <div style={{ color: 'var(--text-muted)', fontSize: '0.72rem' }}>Confidence: <em>Null (Preserved Contract)</em></div>
            </div>
          </div>

          {/* Benchmark Disclosure */}
          <div
            style={{
              padding: '0.65rem 0.85rem',
              borderRadius: '8px',
              background: 'rgba(168, 85, 247, 0.08)',
              border: '1px solid rgba(168, 85, 247, 0.25)',
              fontSize: '0.75rem',
              color: 'var(--text-secondary)',
              lineHeight: 1.45,
            }}
          >
            <strong>Transparent Benchmark Disclosure:</strong> In Phase 3 reported holdout comparisons, the Random Forest model performed worse than standard persistence (MAE 11.2 vs 8.4 µg/m³). Predictions are presented for analytical review under active research supervision.
          </div>
        </div>
      ) : (
        /* Case 3: Error or Data Unavailable */
        <div style={{ padding: '1rem 0', color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#f59e0b' }}>
            <AlertTriangle size={15} />
            <span>Hazard model inputs currently incomplete for this cell.</span>
          </div>
        </div>
      )}

      {/* Card Footer */}
      <div className="citizen-source-provenance">
        <span>Model Version: <strong>rf_hotspot_v1 (Resolution 8)</strong></span>
        <span>Validation Status: <strong>Holdout Monitored</strong></span>
      </div>
    </div>
  );
};
