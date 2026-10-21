import React from 'react';
import { Card } from '../common/Card';
import { Badge } from '../common/Badge';
import { ShieldCheck, Activity, Database, Cpu } from 'lucide-react';

interface FederatedStatusProps {
  currentRound?: number;
  totalNodes?: number;
  activeNodes?: number;
  status?: string;
  activeModelVersion?: string;
  totalSamples?: number;
}

export const FederatedStatus: React.FC<FederatedStatusProps> = ({
  currentRound = 0,
  totalNodes = 0,
  activeNodes = 0,
  status = 'SYNCHRONIZED',
  activeModelVersion = 'N/A',
  totalSamples = 0,
}) => {
  return (
    <Card
      title="Federated Orchestration Status"
      subtitle="Decentralized municipal ML coordination without raw telemetry centralization"
      className="federated-status-card"
    >
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
          gap: '1rem',
          marginTop: '0.75rem',
        }}
      >
        <div style={{ padding: '0.75rem 1rem', background: 'rgba(255, 255, 255, 0.03)', borderRadius: '8px' }}>
          <div style={{ fontSize: '0.75rem', color: '#9ca3af', display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
            <Activity size={13} color="#38bdf8" />
            <span>Coordinator State</span>
          </div>
          <div style={{ marginTop: '0.35rem' }}>
            <Badge variant="success" pulse size="sm">
              {status}
            </Badge>
          </div>
        </div>

        <div style={{ padding: '0.75rem 1rem', background: 'rgba(255, 255, 255, 0.03)', borderRadius: '8px' }}>
          <div style={{ fontSize: '0.75rem', color: '#9ca3af', display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
            <ShieldCheck size={13} color="#10b981" />
            <span>Participating Municipalities</span>
          </div>
          <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#f3f4f6', marginTop: '0.15rem' }}>
            {activeNodes} / {totalNodes}
            <span style={{ fontSize: '0.75rem', color: '#10b981', fontWeight: 500, marginLeft: '0.4rem' }}>
              Online
            </span>
          </div>
        </div>

        <div style={{ padding: '0.75rem 1rem', background: 'rgba(255, 255, 255, 0.03)', borderRadius: '8px' }}>
          <div style={{ fontSize: '0.75rem', color: '#9ca3af', display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
            <Cpu size={13} color="#a855f7" />
            <span>Active Model</span>
          </div>
          <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#a855f7', marginTop: '0.15rem' }}>
            {activeModelVersion}
          </div>
        </div>

        <div style={{ padding: '0.75rem 1rem', background: 'rgba(255, 255, 255, 0.03)', borderRadius: '8px' }}>
          <div style={{ fontSize: '0.75rem', color: '#9ca3af', display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
            <Database size={13} color="#f59e0b" />
            <span>Cumulative Samples</span>
          </div>
          <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#f59e0b', marginTop: '0.15rem' }}>
            {totalSamples?.toLocaleString()}
          </div>
        </div>
      </div>
    </Card>
  );
};

export default FederatedStatus;
