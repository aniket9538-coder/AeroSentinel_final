import React, { useState } from 'react';
import { Card } from '../common/Card';
import { Badge } from '../common/Badge';
import { Button } from '../common/Button';
import { RoundDetail, RoundResponse } from '../../types/federated';
import { getRoundStatusConfig, formatRelativeTime, formatMetric } from '../../utils/federatedUtils';
import { RoundQuorumProgress } from './RoundQuorumProgress';
import { RefreshCw, Play, CheckCircle, AlertTriangle, Layers, Clock, Cpu, BarChart3, ChevronDown, ChevronUp, ShieldCheck } from 'lucide-react';
import { federatedService } from '../../services/federated.service';

interface RoundLifecycleManagerProps {
  rounds: RoundResponse[];
  selectedRoundDetail?: RoundDetail | null;
  isLoadingDetail?: boolean;
  onSelectRound: (roundId: string) => void;
  onInitiateRoundClick: () => void;
  onTriggerAggregation: (roundId: string) => Promise<void>;
  onSimulateRound?: (roundId: string) => Promise<void>;
}

export const RoundLifecycleManager: React.FC<RoundLifecycleManagerProps> = ({
  rounds,
  selectedRoundDetail,
  isLoadingDetail,
  onSelectRound,
  onInitiateRoundClick,
  onTriggerAggregation,
  onSimulateRound,
}) => {
  const [isAggregating, setIsAggregating] = useState(false);
  const [isSimulating, setIsSimulating] = useState(false);
  const [showEvaluation, setShowEvaluation] = useState(false);
  const [evaluationData, setEvaluationData] = useState<any>(null);
  const [loadingEvaluation, setLoadingEvaluation] = useState(false);

  const activeRound = selectedRoundDetail || (rounds.length > 0 ? (rounds[0] as any) : null);
  const statusCfg = getRoundStatusConfig(activeRound?.status);

  const canAggregate =
    activeRound &&
    activeRound.status !== 'COMPLETED' &&
    activeRound.status !== 'FAILED' &&
    (activeRound.receivedUpdatesCount ?? 0) >= (activeRound.minQuorum ?? 2);

  const handleAggregate = async () => {
    if (!activeRound || isAggregating) return;
    setIsAggregating(true);
    try {
      await onTriggerAggregation(activeRound.roundId);
    } finally {
      setIsAggregating(false);
    }
  };

  const handleSimulate = async () => {
    if (!activeRound || isSimulating || !onSimulateRound) return;
    setIsSimulating(true);
    try {
      await onSimulateRound(activeRound.roundId);
    } finally {
      setIsSimulating(false);
    }
  };

  const handleLoadEvaluation = async () => {
    if (!activeRound) return;
    const version = activeRound.targetModelVersion || activeRound.baseModelVersion || 'global-v2';
    setLoadingEvaluation(true);
    try {
      const data = await federatedService.getEvaluation(version);
      setEvaluationData(data);
      setShowEvaluation(true);
    } catch (e: any) {
      console.error('Failed to load evaluation:', e);
    } finally {
      setLoadingEvaluation(false);
    }
  };

  return (
    <Card className="round-lifecycle-manager">
      {/* Header and Controls */}
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: '1rem',
          borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
          paddingBottom: '1rem',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
          <Layers size={20} color="#a855f7" />
          <h3 style={{ fontSize: '1.05rem', fontWeight: 600, color: '#f3f4f6', margin: 0 }}>
            Federated Training Round Lifecycle
          </h3>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
          {rounds.length > 1 && (
            <select
              value={activeRound?.roundId || ''}
              onChange={(e) => onSelectRound(e.target.value)}
              style={{
                background: 'rgba(255, 255, 255, 0.06)',
                border: '1px solid rgba(255, 255, 255, 0.15)',
                color: '#f3f4f6',
                borderRadius: '6px',
                padding: '0.4rem 0.6rem',
                fontSize: '0.8rem',
                outline: 'none',
              }}
            >
              {rounds.map((r) => (
                <option key={r.roundId} value={r.roundId} style={{ background: '#1e293b' }}>
                  {r.roundId} ({r.status})
                </option>
              ))}
            </select>
          )}

          <Button variant="outline" size="sm" onClick={onInitiateRoundClick}>
            <Play size={13} style={{ marginRight: '0.35rem' }} />
            New Round
          </Button>

          {activeRound &&
            activeRound.status !== 'COMPLETED' &&
            activeRound.status !== 'FAILED' &&
            onSimulateRound && (
              <Button
                variant="secondary"
                size="sm"
                onClick={handleSimulate}
                isLoading={isSimulating}
                title="Executes genuine local Ridge training on local datasets for participating nodes and submits updates"
              >
                <Cpu size={13} style={{ marginRight: '0.35rem' }} />
                Simulate Node Training
              </Button>
            )}

          <Button
            variant="primary"
            size="sm"
            onClick={handleAggregate}
            isLoading={isAggregating}
            disabled={!canAggregate}
          >
            <RefreshCw size={13} style={{ marginRight: '0.35rem' }} />
            Trigger FedAvg Aggregation
          </Button>
        </div>
      </div>

      {isLoadingDetail ? (
        <div style={{ padding: '2rem', textAlign: 'center', color: '#9ca3af' }}>
          Loading round details...
        </div>
      ) : !activeRound ? (
        <div style={{ padding: '2rem', textAlign: 'center', color: '#9ca3af' }}>
          No federated training rounds recorded. Click "New Round" to start.
        </div>
      ) : (
        <div style={{ marginTop: '1rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {/* Active Round Info Bar */}
          <div
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              justifyContent: 'space-between',
              alignItems: 'center',
              background: 'rgba(255, 255, 255, 0.02)',
              padding: '0.75rem 1rem',
              borderRadius: '8px',
              gap: '0.75rem',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              <span style={{ fontSize: '1.1rem', fontWeight: 700, color: '#f3f4f6' }}>
                {activeRound.roundId}
              </span>
              <Badge variant={statusCfg.variant}>{statusCfg.label}</Badge>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', fontSize: '0.8rem', color: '#9ca3af' }}>
              <span>
                Base: <strong style={{ color: '#e5e7eb' }}>{activeRound.baseModelVersion}</strong>
              </span>
              {activeRound.targetModelVersion && (
                <span>
                  Target: <strong style={{ color: '#38bdf8' }}>{activeRound.targetModelVersion}</strong>
                </span>
              )}
              <span style={{ display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
                <Clock size={13} />
                Started: {formatRelativeTime(activeRound.createdAt)}
              </span>
            </div>

            <div style={{ marginTop: '0.4rem', fontSize: '0.72rem', color: '#9ca3af', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <span style={{ fontFamily: 'monospace', background: 'rgba(255,255,255,0.05)', padding: '0.1rem 0.4rem', borderRadius: '4px' }}>
                FedAvg Ridge (37 params: 36 f3-features-v1 weights + intercept, α=1.0)
              </span>
            </div>
          </div>

          {/* Quorum Progress Meter */}
          <RoundQuorumProgress
            receivedCount={activeRound.receivedUpdatesCount ?? (activeRound.updates?.length || 0)}
            minQuorum={activeRound.minQuorum ?? 2}
          />

          {/* Failure Alert Banner if Failed */}
          {activeRound.status === 'FAILED' && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem',
                padding: '0.75rem 1rem',
                borderRadius: '8px',
                background: 'rgba(239, 68, 68, 0.12)',
                border: '1px solid rgba(239, 68, 68, 0.3)',
                color: '#ef4444',
                fontSize: '0.85rem',
              }}
            >
              <AlertTriangle size={18} />
              <span>
                <strong>Round Failed:</strong>{' '}
                {activeRound.failureReason || 'Minimum quorum consensus was not achieved.'}
              </span>
            </div>
          )}

          {/* Updates Table */}
          <div>
            <div
              style={{
                fontSize: '0.85rem',
                fontWeight: 600,
                color: '#9ca3af',
                marginBottom: '0.5rem',
                display: 'flex',
                justifyContent: 'space-between',
              }}
            >
              <span>Contributed Municipal Parameter Updates</span>
              <span>
                Total Samples Contributed:{' '}
                <strong style={{ color: '#f3f4f6' }}>
                  {activeRound.totalSamples?.toLocaleString() ||
                    activeRound.updates?.reduce((sum: number, u: any) => sum + (u.sampleCount || 0), 0)?.toLocaleString() ||
                    0}
                </strong>
              </span>
            </div>

            {activeRound.updates && activeRound.updates.length > 0 ? (
              <div
                style={{
                  overflowX: 'auto',
                  borderRadius: '8px',
                  border: '1px solid rgba(255, 255, 255, 0.08)',
                }}
              >
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.8rem', textAlign: 'left' }}>
                  <thead>
                    <tr style={{ background: 'rgba(255, 255, 255, 0.04)', color: '#9ca3af' }}>
                      <th style={{ padding: '0.6rem 0.8rem' }}>City Node</th>
                      <th style={{ padding: '0.6rem 0.8rem' }}>Sample Contribution</th>
                      <th style={{ padding: '0.6rem 0.8rem' }}>Validation Status</th>
                      <th style={{ padding: '0.6rem 0.8rem' }}>Submission Timestamp</th>
                    </tr>
                  </thead>
                  <tbody>
                    {activeRound.updates.map((u: any) => (
                      <tr
                        key={u.updateId || `${u.roundId}-${u.cityName}`}
                        style={{ borderTop: '1px solid rgba(255, 255, 255, 0.05)' }}
                      >
                        <td style={{ padding: '0.6rem 0.8rem', fontWeight: 600, color: '#f3f4f6' }}>
                          {u.cityName}
                        </td>
                        <td style={{ padding: '0.6rem 0.8rem', color: '#38bdf8' }}>
                          {u.sampleCount?.toLocaleString()} samples
                        </td>
                        <td style={{ padding: '0.6rem 0.8rem' }}>
                          <Badge
                            variant={u.status === 'ACCEPTED' ? 'success' : 'warning'}
                            size="sm"
                          >
                            {u.status}
                          </Badge>
                        </td>
                        <td style={{ padding: '0.6rem 0.8rem', color: '#6b7280' }}>
                          {formatRelativeTime(u.submittedAt)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div
                style={{
                  padding: '1.25rem',
                  textAlign: 'center',
                  background: 'rgba(255, 255, 255, 0.02)',
                  borderRadius: '8px',
                  color: '#6b7280',
                  fontSize: '0.8rem',
                }}
              >
                No municipal parameter updates submitted yet for this round.
              </div>
            )}
          </div>

          {/* Aggregated Consensus Metrics if Completed */}
          {activeRound.aggregatedMetrics && (
            <div
              style={{
                marginTop: '0.5rem',
                padding: '0.75rem 1rem',
                borderRadius: '8px',
                background: 'rgba(16, 185, 129, 0.06)',
                border: '1px solid rgba(16, 185, 129, 0.2)',
                display: 'flex',
                flexWrap: 'wrap',
                justifyContent: 'space-between',
                alignItems: 'center',
                gap: '1rem',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#10b981', fontSize: '0.85rem', fontWeight: 600 }}>
                <CheckCircle size={16} />
                <span>Round Aggregation Consensus Achieved</span>
              </div>
              <div style={{ display: 'flex', gap: '1rem', fontSize: '0.8rem', color: '#9ca3af' }}>
                <span>MAE: <strong style={{ color: '#f3f4f6' }}>{formatMetric(activeRound.aggregatedMetrics.mae)}</strong></span>
                <span>RMSE: <strong style={{ color: '#f3f4f6' }}>{formatMetric(activeRound.aggregatedMetrics.rmse)}</strong></span>
                <span>ROC-AUC: <strong style={{ color: '#38bdf8' }}>{formatMetric(activeRound.aggregatedMetrics.rocAuc)}</strong></span>
                <span>Brier: <strong style={{ color: '#10b981' }}>{formatMetric(activeRound.aggregatedMetrics.brierScore)}</strong></span>
              </div>
            </div>
          )}

          {/* Held-Out Cross-City Baseline Evaluation Toggle & Panel */}
          {activeRound.status === 'COMPLETED' && (
            <div style={{ marginTop: '0.75rem', border: '1px solid rgba(255, 255, 255, 0.08)', borderRadius: '8px', overflow: 'hidden' }}>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  padding: '0.75rem 1rem',
                  background: 'rgba(255, 255, 255, 0.03)',
                  cursor: 'pointer',
                }}
                onClick={() => {
                  if (!showEvaluation && !evaluationData) {
                    handleLoadEvaluation();
                  } else {
                    setShowEvaluation(!showEvaluation);
                  }
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.85rem', fontWeight: 600, color: '#38bdf8' }}>
                  <BarChart3 size={16} />
                  <span>Held-Out Cross-City Evaluation & Baseline Comparison</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  {loadingEvaluation && <span style={{ fontSize: '0.75rem', color: '#9ca3af' }}>Loading metrics...</span>}
                  <Button variant="ghost" size="sm" style={{ padding: '0.2rem' }}>
                    {showEvaluation ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                  </Button>
                </div>
              </div>

              {showEvaluation && evaluationData && (
                <div style={{ padding: '1rem', background: 'rgba(0, 0, 0, 0.2)', borderTop: '1px solid rgba(255, 255, 255, 0.05)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
                    <span style={{ fontSize: '0.8rem', color: '#9ca3af' }}>
                      Master Held-Out Validation: <strong style={{ color: '#f3f4f6' }}>{evaluationData.testSampleCount || evaluationData.testSampleSize} samples</strong> (cross-city test split, seed 999)
                    </span>
                    <span style={{ fontSize: '0.75rem', color: '#6b7280' }}>
                      Evaluated: {formatRelativeTime(evaluationData.evaluatedAt || evaluationData.evaluationTimestamp)}
                    </span>
                  </div>

                  <div style={{ overflowX: 'auto', borderRadius: '6px', border: '1px solid rgba(255, 255, 255, 0.08)' }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.8rem', textAlign: 'left' }}>
                      <thead>
                        <tr style={{ background: 'rgba(255, 255, 255, 0.04)', color: '#9ca3af' }}>
                          <th style={{ padding: '0.5rem 0.8rem' }}>Model / Baseline</th>
                          <th style={{ padding: '0.5rem 0.8rem' }}>MAE (µg/m³)</th>
                          <th style={{ padding: '0.5rem 0.8rem' }}>RMSE</th>
                          <th style={{ padding: '0.5rem 0.8rem' }}>ROC-AUC</th>
                          <th style={{ padding: '0.5rem 0.8rem' }}>Brier Score</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr style={{ borderTop: '1px solid rgba(255, 255, 255, 0.05)', background: 'rgba(56, 189, 248, 0.05)' }}>
                          <td style={{ padding: '0.5rem 0.8rem', fontWeight: 600, color: '#38bdf8' }}>
                            Consensus Global Model ({evaluationData.modelVersion})
                          </td>
                          <td style={{ padding: '0.5rem 0.8rem', color: '#f3f4f6' }}>{formatMetric(evaluationData.globalMetrics?.mae)}</td>
                          <td style={{ padding: '0.5rem 0.8rem', color: '#f3f4f6' }}>{formatMetric(evaluationData.globalMetrics?.rmse)}</td>
                          <td style={{ padding: '0.5rem 0.8rem', color: '#10b981', fontWeight: 600 }}>{formatMetric(evaluationData.globalMetrics?.rocAuc)}</td>
                          <td style={{ padding: '0.5rem 0.8rem', color: '#f3f4f6' }}>{formatMetric(evaluationData.globalMetrics?.brierScore)}</td>
                        </tr>
                        <tr style={{ borderTop: '1px solid rgba(255, 255, 255, 0.05)' }}>
                          <td style={{ padding: '0.5rem 0.8rem', fontWeight: 500, color: '#e5e7eb' }}>
                            Pune Local Baseline (Local Only)
                          </td>
                          <td style={{ padding: '0.5rem 0.8rem', color: '#9ca3af' }}>{formatMetric((evaluationData.localBaselineMetrics || evaluationData.puneLocalMetrics)?.mae)}</td>
                          <td style={{ padding: '0.5rem 0.8rem', color: '#9ca3af' }}>{formatMetric((evaluationData.localBaselineMetrics || evaluationData.puneLocalMetrics)?.rmse)}</td>
                          <td style={{ padding: '0.5rem 0.8rem', color: '#9ca3af' }}>{formatMetric((evaluationData.localBaselineMetrics || evaluationData.puneLocalMetrics)?.rocAuc)}</td>
                          <td style={{ padding: '0.5rem 0.8rem', color: '#9ca3af' }}>{formatMetric((evaluationData.localBaselineMetrics || evaluationData.puneLocalMetrics)?.brierScore)}</td>
                        </tr>
                        <tr style={{ borderTop: '1px solid rgba(255, 255, 255, 0.05)' }}>
                          <td style={{ padding: '0.5rem 0.8rem', fontWeight: 500, color: '#9ca3af' }}>
                            Naive Mean Baseline (Constant)
                          </td>
                          <td style={{ padding: '0.5rem 0.8rem', color: '#6b7280' }}>{formatMetric(evaluationData.naiveBaselineMetrics?.mae)}</td>
                          <td style={{ padding: '0.5rem 0.8rem', color: '#6b7280' }}>{formatMetric(evaluationData.naiveBaselineMetrics?.rmse)}</td>
                          <td style={{ padding: '0.5rem 0.8rem', color: '#6b7280' }}>{formatMetric(evaluationData.naiveBaselineMetrics?.rocAuc)}</td>
                          <td style={{ padding: '0.5rem 0.8rem', color: '#6b7280' }}>{formatMetric(evaluationData.naiveBaselineMetrics?.brierScore)}</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>

                  <div
                    style={{
                      marginTop: '0.75rem',
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: '0.5rem',
                      padding: '0.6rem 0.8rem',
                      borderRadius: '6px',
                      background: 'rgba(245, 158, 11, 0.08)',
                      border: '1px solid rgba(245, 158, 11, 0.25)',
                      color: '#fbbf24',
                      fontSize: '0.75rem',
                      lineHeight: 1.4,
                    }}
                  >
                    <ShieldCheck size={16} style={{ flexShrink: 0, marginTop: '0.1rem' }} />
                    <span>
                      <strong>Scientific Domain Limitation:</strong> {evaluationData.geographicValidityNotice || evaluationData.domainLimitationNote}
                    </span>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </Card>
  );
};

export default RoundLifecycleManager;
