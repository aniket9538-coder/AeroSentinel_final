import React, { useState, useEffect } from 'react';
import {
  ShieldAlert,
  Sparkles,
  RefreshCw,
  AlertTriangle,
  CheckCircle2,
  Clock,
  Layers,
  Info,
  ExternalLink,
  ChevronDown,
  ChevronUp,
  Activity,
  Wind,
  Camera,
  FileText,
  Radio,
  Eye,
  AlertOctagon,
} from 'lucide-react';
import { evidenceApi } from '../../services/evidenceApi';
import { TriageCandidateDto, NormalizedEvidenceItem, ExcludedEvidenceDto, EvidenceClass } from '../../types/evidence';

interface EvidenceFusionTriageCardProps {
  h3Index: string;
  onRefreshParent?: () => void;
}

export const EvidenceFusionTriageCard: React.FC<EvidenceFusionTriageCardProps> = ({
  h3Index,
  onRefreshParent,
}) => {
  const [candidate, setCandidate] = useState<TriageCandidateDto | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [fusing, setFusing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [showExcluded, setShowExcluded] = useState<boolean>(false);

  const fetchTriage = async () => {
    if (!h3Index) return;
    setLoading(true);
    setError(null);
    try {
      const data = await evidenceApi.getTriageCandidate(h3Index);
      setCandidate(data);
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Evidence triage candidate unavailable');
      setCandidate(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTriage();
  }, [h3Index]);

  const handleReevaluateFusion = async () => {
    if (!h3Index || fusing) return;
    setFusing(true);
    try {
      const updated = await evidenceApi.fuseAndPersist(h3Index);
      setCandidate(updated);
      if (onRefreshParent) onRefreshParent();
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Failed to fuse evidence');
    } finally {
      setFusing(false);
    }
  };

  const getEvidenceClassBadge = (evClass: EvidenceClass, isIndependent: boolean) => {
    switch (evClass) {
      case 'CITIZEN_SUBMITTED':
        return { label: 'Citizen Submission', bg: '#eff6ff', color: '#1d4ed8', border: '#bfdbfe', icon: <FileText size={11} /> };
      case 'CITIZEN_VISION':
        return { label: 'CitizenVision AI (Auxiliary)', bg: '#f5f3ff', color: '#7c3aed', border: '#ddd6fe', icon: <Eye size={11} /> };
      case 'ENVIRONMENTAL_MEASUREMENT':
        return { label: 'Physical Telemetry', bg: '#ecfdf5', color: '#047857', border: '#a7f3d0', icon: <Radio size={11} /> };
      case 'WEATHER_MODEL_CONTEXT':
        return { label: 'Weather Context', bg: '#f0fdfa', color: '#0f766e', border: '#99f6e4', icon: <Wind size={11} /> };
      case 'OFFICIAL_WARNING':
        return { label: 'Official Warning', bg: '#fef2f2', color: '#b91c1c', border: '#fecaca', icon: <AlertOctagon size={11} /> };
      case 'EXPERIMENTAL_ML_OUTPUT':
        return { label: 'Experimental ML', bg: '#fffbeb', color: '#b45309', border: '#fde68a', icon: <Sparkles size={11} /> };
      default:
        return { label: evClass, bg: '#f1f5f9', color: '#475569', border: '#cbd5e1', icon: <Layers size={11} /> };
    }
  };

  return (
    <div
      data-testid="evidence-fusion-triage-card"
      style={{
        background: '#ffffff',
        border: '1.5px solid #e0e7ff',
        borderRadius: '12px',
        padding: '1.15rem',
        boxShadow: '0 2px 6px rgba(99, 102, 241, 0.04)',
        display: 'flex',
        flexDirection: 'column',
        gap: '0.9rem',
      }}
    >
      {/* Header with Title and Re-evaluate Fusion Button */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <div
            style={{
              width: '28px',
              height: '28px',
              borderRadius: '6px',
              background: '#e0e7ff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#4f46e5',
            }}
          >
            <ShieldAlert size={16} />
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem' }}>
              <span style={{ fontSize: '0.85rem', fontWeight: 800, color: '#1e1b4b', letterSpacing: '0.02em', textTransform: 'uppercase' }}>
                F5 Evidence Fusion &amp; Triage
              </span>
              <span
                style={{
                  fontSize: '0.65rem',
                  padding: '0.1rem 0.4rem',
                  borderRadius: '4px',
                  background: '#f1f5f9',
                  color: '#475569',
                  fontFamily: 'var(--font-mono)',
                  fontWeight: 600,
                }}
              >
                {candidate?.policyVersion || 'f5_evidence_fusion_v1'}
              </span>
            </div>
            <div style={{ fontSize: '0.72rem', color: '#64748b' }}>
              Explainable Corroboration Engine &bull; H3 Res-8 ({h3Index})
            </div>
          </div>
        </div>

        <button
          type="button"
          data-testid="btn-reevaluate-fusion"
          onClick={handleReevaluateFusion}
          disabled={fusing || loading}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.35rem',
            padding: '0.35rem 0.75rem',
            borderRadius: '6px',
            fontSize: '0.75rem',
            fontWeight: 700,
            background: fusing ? '#e2e8f0' : '#4f46e5',
            color: fusing ? '#64748b' : '#ffffff',
            border: 'none',
            cursor: fusing || loading ? 'not-allowed' : 'pointer',
            transition: 'background 0.2s ease',
          }}
          title="Re-run idempotent evidence correlation and persistence"
        >
          <RefreshCw size={12} className={fusing ? 'animate-spin' : ''} />
          {fusing ? 'Fusing...' : 'Re-evaluate Fusion'}
        </button>
      </div>

      {loading && !candidate ? (
        <div style={{ padding: '1.25rem', textAlign: 'center', color: '#64748b', fontSize: '0.8rem' }}>
          <RefreshCw size={16} className="animate-spin" style={{ display: 'inline', marginRight: '0.4rem' }} />
          Correlating multi-source evidence across space and time...
        </div>
      ) : error ? (
        <div
          style={{
            padding: '0.75rem',
            borderRadius: '8px',
            background: '#fef2f2',
            border: '1px solid #fecaca',
            color: '#b91c1c',
            fontSize: '0.78rem',
            display: 'flex',
            alignItems: 'center',
            gap: '0.45rem',
          }}
        >
          <AlertTriangle size={14} />
          <span>{error}</span>
        </div>
      ) : candidate ? (
        <>
          {/* Provisional vs Corroborated Status Banner */}
          <div
            data-testid="triage-status-banner"
            style={{
              padding: '0.65rem 0.85rem',
              borderRadius: '8px',
              background: candidate.isProvisional ? '#fffbeb' : '#f0fdf4',
              border: `1.5px solid ${candidate.isProvisional ? '#fde68a' : '#bbf7d0'}`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: '0.5rem',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              {candidate.isProvisional ? (
                <AlertTriangle size={16} color="#d97706" style={{ flexShrink: 0 }} />
              ) : (
                <CheckCircle2 size={16} color="#16a34a" style={{ flexShrink: 0 }} />
              )}
              <div>
                <div style={{ fontSize: '0.8rem', fontWeight: 800, color: candidate.isProvisional ? '#92400e' : '#166534' }}>
                  {candidate.isProvisional
                    ? 'PROVISIONAL TRIAGE CANDIDATE — AUXILIARY OBSERVATION'
                    : 'CORROBORATED INCIDENT — MULTI-SOURCE EVIDENCE VERIFIED'}
                </div>
                <div style={{ fontSize: '0.72rem', color: candidate.isProvisional ? '#b45309' : '#15803d' }}>
                  {candidate.isProvisional
                    ? 'Originates solely from citizen reports/AI photo analysis. Invariant enforced: Never establishes confirmed violations or high operational alerts alone.'
                    : 'Corroborated by independent physical telemetry or authoritative environmental data.'}
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
              <span
                style={{
                  padding: '0.2rem 0.5rem',
                  borderRadius: '4px',
                  fontSize: '0.7rem',
                  fontWeight: 800,
                  textTransform: 'uppercase',
                  background: candidate.triageStatus === 'PROVISIONAL_TRIAGE' ? '#fef3c7' : '#dcfce7',
                  color: candidate.triageStatus === 'PROVISIONAL_TRIAGE' ? '#92400e' : '#15803d',
                  border: '1px solid currentColor',
                }}
              >
                {candidate.triageStatus}
              </span>
              <span
                style={{
                  padding: '0.2rem 0.5rem',
                  borderRadius: '4px',
                  fontSize: '0.7rem',
                  fontWeight: 800,
                  textTransform: 'uppercase',
                  background:
                    candidate.severity === 'CRITICAL'
                      ? '#fee2e2'
                      : candidate.severity === 'HIGH'
                      ? '#ffedd5'
                      : candidate.severity === 'MEDIUM'
                      ? '#fef3c7'
                      : '#e0f2fe',
                  color:
                    candidate.severity === 'CRITICAL'
                      ? '#dc2626'
                      : candidate.severity === 'HIGH'
                      ? '#c2410c'
                      : candidate.severity === 'MEDIUM'
                      ? '#b45309'
                      : '#0284c7',
                  border: '1px solid currentColor',
                }}
              >
                {candidate.severity} SEVERITY
              </span>
            </div>
          </div>

          {/* Metric Separation Grid (Evidence Score vs Risk Score vs Visual Confidence) */}
          <div
            data-testid="metric-separation-grid"
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
              gap: '0.65rem',
            }}
          >
            {/* 1. Evidence Score */}
            <div style={{ background: '#f8fafc', padding: '0.65rem 0.8rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
              <span style={{ color: '#64748b', display: 'block', fontSize: '0.68rem', fontWeight: 700, textTransform: 'uppercase' }}>
                F5 Evidence Score
              </span>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.3rem', marginTop: '0.15rem' }}>
                <span style={{ color: '#4f46e5', fontWeight: 900, fontFamily: 'var(--font-mono)', fontSize: '1.2rem' }}>
                  {candidate.evidenceScore != null ? candidate.evidenceScore.toFixed(3) : 'N/A'}
                </span>
                <span style={{ fontSize: '0.68rem', color: '#94a3b8' }}>/ 1.000</span>
              </div>
              <span style={{ fontSize: '0.65rem', color: '#64748b' }}>Multi-source strength</span>
            </div>

            {/* 2. F3 Risk Score */}
            <div style={{ background: '#f8fafc', padding: '0.65rem 0.8rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
              <span style={{ color: '#64748b', display: 'block', fontSize: '0.68rem', fontWeight: 700, textTransform: 'uppercase' }}>
                F3 ML Risk Score
              </span>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.3rem', marginTop: '0.15rem' }}>
                <span style={{ color: '#0284c7', fontWeight: 900, fontFamily: 'var(--font-mono)', fontSize: '1.2rem' }}>
                  {candidate.riskScore != null ? candidate.riskScore.toFixed(3) : 'N/A'}
                </span>
                <span style={{ fontSize: '0.68rem', color: '#94a3b8' }}>/ 1.000</span>
              </div>
              <span style={{ fontSize: '0.65rem', color: '#64748b' }}>Hotspot probability</span>
            </div>

            {/* 3. Visual AI Confidence */}
            <div style={{ background: '#f8fafc', padding: '0.65rem 0.8rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
              <span style={{ color: '#64748b', display: 'block', fontSize: '0.68rem', fontWeight: 700, textTransform: 'uppercase' }}>
                Visual AI Confidence
              </span>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.3rem', marginTop: '0.15rem' }}>
                <span style={{ color: '#7c3aed', fontWeight: 900, fontFamily: 'var(--font-mono)', fontSize: '1.2rem' }}>
                  {candidate.visualConfidence != null ? `${(candidate.visualConfidence * 100).toFixed(0)}%` : 'None'}
                </span>
              </div>
              <span style={{ fontSize: '0.65rem', color: '#64748b' }}>Auxiliary photo scan</span>
            </div>

            {/* 4. Independent Sources Count */}
            <div style={{ background: '#f8fafc', padding: '0.65rem 0.8rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
              <span style={{ color: '#64748b', display: 'block', fontSize: '0.68rem', fontWeight: 700, textTransform: 'uppercase' }}>
                Independent Sources
              </span>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.3rem', marginTop: '0.15rem' }}>
                <span style={{ color: '#0f172a', fontWeight: 900, fontFamily: 'var(--font-mono)', fontSize: '1.2rem' }}>
                  {candidate.independentSourceCount}
                </span>
                <span style={{ fontSize: '0.68rem', color: '#64748b' }}>
                  (+{candidate.relatedSourceCount} related)
                </span>
              </div>
              <span style={{ fontSize: '0.65rem', color: '#64748b' }}>Deduplicated clusters</span>
            </div>
          </div>

          {/* Source Independence Notice */}
          <div
            data-testid="source-independence-notice"
            style={{
              padding: '0.55rem 0.75rem',
              borderRadius: '6px',
              background: '#f8fafc',
              border: '1px solid #e2e8f0',
              fontSize: '0.72rem',
              color: '#475569',
              lineHeight: 1.45,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', fontWeight: 700, color: '#334155', marginBottom: '0.2rem' }}>
              <Info size={12} color="#4f46e5" />
              <span>Strict Source Independence Rule (Safety Invariant 3 &amp; 4)</span>
            </div>
            <div>
              A citizen report, its uploaded photograph, and the CitizenVision AI analysis of that same photo form <strong>1 single Citizen Cluster</strong> ({candidate.relatedSourceCount} auxiliary derived signals). They are <em>not</em> counted as 3 independent corroborating sources.
            </div>
          </div>

          {/* Contributing Evidence Breakdown */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <div style={{ fontSize: '0.75rem', fontWeight: 800, color: '#334155', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
              Contributing Evidence Items ({candidate.contributingEvidence?.length || 0})
            </div>

            {candidate.contributingEvidence && candidate.contributingEvidence.length > 0 ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.45rem' }}>
                {candidate.contributingEvidence.map((item, idx) => {
                  const badge = getEvidenceClassBadge(item.evidenceClass, item.isIndependent);
                  return (
                    <div
                      key={item.evidenceId || idx}
                      data-testid="evidence-item"
                      style={{
                        background: '#ffffff',
                        border: '1px solid #e2e8f0',
                        borderRadius: '8px',
                        padding: '0.65rem 0.8rem',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        flexWrap: 'wrap',
                        gap: '0.5rem',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        <span
                          style={{
                            padding: '0.2rem 0.45rem',
                            borderRadius: '4px',
                            fontSize: '0.68rem',
                            fontWeight: 700,
                            background: badge.bg,
                            color: badge.color,
                            border: `1px solid ${badge.border}`,
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '0.25rem',
                          }}
                        >
                          {badge.icon}
                          {badge.label}
                        </span>
                        <div>
                          <span style={{ fontSize: '0.78rem', fontWeight: 700, color: '#0f172a' }}>
                            {item.sourceType}
                          </span>
                          {item.sourceId && (
                            <span style={{ fontSize: '0.7rem', color: '#64748b', marginLeft: '0.35rem', fontFamily: 'var(--font-mono)' }}>
                              ({item.sourceId})
                            </span>
                          )}
                          <div style={{ fontSize: '0.68rem', color: '#64748b' }}>
                            Observed: {item.observedTimestamp ? new Date(item.observedTimestamp).toLocaleTimeString() : 'N/A'} &bull; Status: {item.freshnessStatus}
                          </div>
                        </div>
                      </div>

                      <div style={{ textAlign: 'right' }}>
                        {item.measurementValue != null && (
                          <div style={{ fontSize: '0.85rem', fontWeight: 800, color: '#0f172a', fontFamily: 'var(--font-mono)' }}>
                            {item.measurementValue.toFixed(1)} {item.measurementUnit || ''}
                          </div>
                        )}
                        <span
                          style={{
                            fontSize: '0.65rem',
                            fontWeight: 700,
                            padding: '0.1rem 0.35rem',
                            borderRadius: '3px',
                            background: item.isIndependent ? '#dcfce7' : '#ede9fe',
                            color: item.isIndependent ? '#15803d' : '#6d28d9',
                          }}
                        >
                          {item.isIndependent ? 'INDEPENDENT' : 'DERIVED / RELATED'}
                        </span>
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <div style={{ fontSize: '0.75rem', color: '#64748b', fontStyle: 'italic', padding: '0.4rem 0' }}>
                No active contributing evidence currently linked to this spatial cell.
              </div>
            )}
          </div>

          {/* Fusion Explanations */}
          {candidate.explanations && candidate.explanations.length > 0 && (
            <div
              style={{
                background: '#f8fafc',
                border: '1px solid #e2e8f0',
                borderRadius: '8px',
                padding: '0.75rem',
                display: 'flex',
                flexDirection: 'column',
                gap: '0.35rem',
              }}
            >
              <div style={{ fontSize: '0.72rem', fontWeight: 800, color: '#475569', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                Corroboration Rationale &bull; Explainability Log
              </div>
              <ul style={{ margin: 0, paddingLeft: '1.1rem', fontSize: '0.75rem', color: '#334155', lineHeight: 1.5 }}>
                {candidate.explanations.map((exp, i) => (
                  <li key={i}>{exp}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Excluded Evidence Collapsible Section */}
          {candidate.excludedEvidence && candidate.excludedEvidence.length > 0 && (
            <div>
              <button
                type="button"
                onClick={() => setShowExcluded(!showExcluded)}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: '#64748b',
                  fontSize: '0.72rem',
                  fontWeight: 700,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.3rem',
                  padding: '0.2rem 0',
                }}
              >
                {showExcluded ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
                <span>
                  Excluded Evidence ({candidate.excludedEvidence.length} items outside correlation window)
                </span>
              </button>

              {showExcluded && (
                <div style={{ marginTop: '0.4rem', display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
                  {candidate.excludedEvidence.map((ex, i) => (
                    <div
                      key={ex.evidenceId || i}
                      style={{
                        padding: '0.45rem 0.65rem',
                        background: '#fef2f2',
                        border: '1px solid #fecaca',
                        borderRadius: '6px',
                        fontSize: '0.72rem',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                      }}
                    >
                      <div>
                        <strong style={{ color: '#991b1b' }}>{ex.source}:</strong> {ex.description}
                      </div>
                      <span
                        style={{
                          fontSize: '0.65rem',
                          padding: '0.1rem 0.35rem',
                          borderRadius: '3px',
                          background: '#fee2e2',
                          color: '#dc2626',
                          fontWeight: 700,
                          fontFamily: 'var(--font-mono)',
                        }}
                      >
                        {ex.reasonCode}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </>
      ) : null}
    </div>
  );
};

export default EvidenceFusionTriageCard;
