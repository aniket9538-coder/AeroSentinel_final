import React from 'react';
import { AlertTriangle, ShieldCheck, ExternalLink, AlertOctagon, Info, RefreshCw } from 'lucide-react';
import { OfficialWarningToolResult, OfficialWarningItem } from '../../types/assistant';

export interface CitizenWarningsCardProps {
  warningsResult: OfficialWarningToolResult | null;
  loading: boolean;
  error: string | null;
  onRefresh?: () => void;
}

export const CitizenWarningsCard: React.FC<CitizenWarningsCardProps> = ({
  warningsResult,
  loading,
  error,
  onRefresh,
}) => {
  if (loading && !warningsResult) {
    return (
      <div className="citizen-card" aria-busy="true">
        <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
          Checking official government disaster warning feeds (NDMA / IMD)...
        </div>
      </div>
    );
  }

  // Handle status scenarios
  const status = warningsResult?.status || 'UNCONFIGURED';
  const hasActiveWarnings = status === 'VERIFIED_WARNINGS_FOUND' && (warningsResult?.warnings?.length ?? 0) > 0;
  const isNoActiveWarnings = status === 'NO_ACTIVE_WARNINGS';
  const isSourceUnavailable = status === 'SOURCE_UNAVAILABLE' || status === 'UNCONFIGURED' || error != null;

  return (
    <div className="citizen-card" role="region" aria-label="Official Government Disaster Warnings">
      {/* Card Header */}
      <div className="citizen-card-header">
        <div className="citizen-card-title-group">
          <div
            className="citizen-card-icon"
            style={{
              background: hasActiveWarnings ? 'rgba(239, 68, 68, 0.15)' : 'var(--brand-surface)',
              color: hasActiveWarnings ? '#ef4444' : 'var(--brand-primary)',
            }}
          >
            {hasActiveWarnings ? <AlertOctagon size={18} /> : <AlertTriangle size={18} />}
          </div>
          <div>
            <h2 className="citizen-card-title">Official Government Warnings</h2>
            <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
              IMD &amp; NDMA SACHET Common Alerting Protocol (CAP RSS 2.0)
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span
            className={`citizen-card-badge ${
              hasActiveWarnings
                ? 'badge-unavailable'
                : isNoActiveWarnings
                ? 'badge-live'
                : 'badge-stale'
            }`}
          >
            {hasActiveWarnings
              ? `${warningsResult?.warnings.length} ACTIVE`
              : isNoActiveWarnings
              ? 'NO ACTIVE WARNINGS'
              : 'FEED_UNAVAILABLE'}
          </span>
          {onRefresh && (
            <button
              onClick={onRefresh}
              title="Re-check government warning feed"
              aria-label="Re-check government warning feed"
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

      {/* Case 1: Verified Active Warnings Found */}
      {hasActiveWarnings && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          {warningsResult?.warnings.map((warning, idx) => {
            const severityClass =
              warning.severity === 'ORANGE'
                ? 'severity-orange'
                : warning.severity === 'YELLOW'
                ? 'severity-yellow'
                : 'severity-red';

            return (
              <div key={warning.warningId || idx} className={`citizen-warning-item ${severityClass}`}>
                <div className="citizen-warning-header">
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                    <span
                      style={{
                        padding: '0.15rem 0.45rem',
                        borderRadius: '4px',
                        background: '#ef4444',
                        color: '#ffffff',
                        fontSize: '0.7rem',
                        fontWeight: 800,
                      }}
                    >
                      {warning.severity || 'WARNING'}
                    </span>
                    <span className="citizen-warning-title">{warning.title}</span>
                  </div>
                  <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)', fontWeight: 600 }}>
                    {warning.issuingAuthority || 'NDMA SACHET'}
                  </span>
                </div>

                <div style={{ fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
                  <strong>Affected Area:</strong> {warning.affectedArea || 'Local District'}
                </div>

                {(warning.validFrom || warning.validUntil) && (
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                    Validity: {warning.validFrom ? new Date(warning.validFrom).toLocaleString() : 'Immediate'} —{' '}
                    {warning.validUntil ? new Date(warning.validUntil).toLocaleString() : 'Until Further Notice'}
                  </div>
                )}

                {warning.officialReferenceUrl && (
                  <div style={{ alignSelf: 'flex-start', marginTop: '0.25rem' }}>
                    <a
                      href={warning.officialReferenceUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="citizen-source-link"
                    >
                      <span>View Official Government Bulletin</span>
                      <ExternalLink size={11} />
                    </a>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Case 2: Successful Lookup, No Active Warnings */}
      {isNoActiveWarnings && (
        <div
          style={{
            padding: '1rem',
            borderRadius: '10px',
            background: 'rgba(16, 185, 129, 0.08)',
            border: '1px solid rgba(16, 185, 129, 0.25)',
            display: 'flex',
            alignItems: 'center',
            gap: '0.75rem',
          }}
        >
          <ShieldCheck size={24} style={{ color: '#10b981', flexShrink: 0 }} />
          <div>
            <div style={{ fontSize: '0.9rem', fontWeight: 700, color: '#10b981' }}>
              No Active Official Disaster Warnings
            </div>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', marginTop: '0.2rem' }}>
              Government warning feeds (IMD &amp; NDMA SACHET) report no active severe weather or disaster alerts for {warningsResult?.locationName || 'this region'}.
            </div>
          </div>
        </div>
      )}

      {/* Case 3: Source Unavailable or Unconfigured */}
      {isSourceUnavailable && (
        <div
          style={{
            padding: '1rem',
            borderRadius: '10px',
            background: 'rgba(245, 158, 11, 0.08)',
            border: '1px solid rgba(245, 158, 11, 0.25)',
            display: 'flex',
            alignItems: 'flex-start',
            gap: '0.75rem',
          }}
        >
          <Info size={20} style={{ color: '#f59e0b', flexShrink: 0, marginTop: '2px' }} />
          <div>
            <div style={{ fontSize: '0.88rem', fontWeight: 700, color: '#f59e0b' }}>
              Official Warning Feed Unavailable
            </div>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', marginTop: '0.25rem', lineHeight: 1.4 }}>
              Unable to reach government disaster portal feeds (NDMA SACHET / IMD CAP). An absent or unreachable feed does not guarantee that conditions are clear. Please check regional state emergency portals.
            </div>
          </div>
        </div>
      )}

      {/* Strict Anti-Fabrication Disclaimer */}
      <div
        style={{
          fontSize: '0.72rem',
          color: 'var(--text-muted)',
          lineHeight: 1.4,
          padding: '0.5rem 0.65rem',
          background: 'var(--bg-surface)',
          borderRadius: '6px',
        }}
      >
        <strong>Safety Policy:</strong> AeroSentinel predictive ML risk scores and sensor hotspots are internal analytical indicators and are strictly distinguished from official statutory government warnings.
      </div>

      {/* Source Provenance Footer */}
      <div className="citizen-source-provenance">
        <span>Issuing Authority: <strong>{warningsResult?.sourceAuthority || 'NDMA SACHET / IMD'}</strong></span>
        {warningsResult?.sourceUrl && (
          <a
            href={warningsResult.sourceUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="citizen-source-link"
          >
            <span>Visit Official Warning Portal</span>
            <ExternalLink size={11} />
          </a>
        )}
      </div>
    </div>
  );
};
