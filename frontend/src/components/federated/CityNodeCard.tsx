import React, { useState } from 'react';
import { Card } from '../common/Card';
import { Badge } from '../common/Badge';
import { Button } from '../common/Button';
import { FederatedNode } from '../../types/federated';
import { getNodeStatusConfig, formatRelativeTime } from '../../utils/federatedUtils';
import { Server, Activity, Radio, Cpu, ExternalLink } from 'lucide-react';

interface CityNodeCardProps {
  node: FederatedNode;
  onHeartbeat?: (nodeId: string) => Promise<void> | void;
}

export const CityNodeCard: React.FC<CityNodeCardProps> = ({ node, onHeartbeat }) => {
  const [isPinging, setIsPinging] = useState(false);
  const statusCfg = getNodeStatusConfig(node.status);
  const nodeId = node.nodeId || node.id || 'NODE';
  const lastTime = node.lastSeenAt || node.lastUpdateAt;

  const handlePing = async () => {
    if (!onHeartbeat) return;
    setIsPinging(true);
    try {
      await onHeartbeat(nodeId);
    } finally {
      setIsPinging(false);
    }
  };

  return (
    <Card className="federated-city-node-card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <div
            style={{
              padding: '0.6rem',
              borderRadius: '10px',
              background: statusCfg.bg,
              color: statusCfg.color,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Server size={22} />
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <h4 style={{ fontSize: '1rem', fontWeight: 600, color: '#f3f4f6', margin: 0 }}>
                {node.nodeName}
              </h4>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', marginTop: '0.2rem' }}>
              <span
                style={{
                  fontSize: '0.7rem',
                  fontFamily: 'monospace',
                  background: 'rgba(255, 255, 255, 0.06)',
                  padding: '0.15rem 0.4rem',
                  borderRadius: '4px',
                  color: '#9ca3af',
                }}
              >
                {nodeId}
              </span>
              <span style={{ fontSize: '0.75rem', color: '#6b7280' }}>•</span>
              <span style={{ fontSize: '0.75rem', color: '#9ca3af', display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                <Cpu size={12} />
                {node.modelVersion || 'None'}
              </span>
            </div>
            <div style={{ marginTop: '0.35rem' }}>
              <span
                style={{
                  fontSize: '0.62rem',
                  fontWeight: 600,
                  letterSpacing: '0.04em',
                  padding: '0.12rem 0.4rem',
                  borderRadius: '4px',
                  background: nodeId === 'PUNE' ? 'rgba(59, 130, 246, 0.15)' : 'rgba(245, 158, 11, 0.15)',
                  color: nodeId === 'PUNE' ? '#60a5fa' : '#fbbf24',
                  border: `1px solid ${nodeId === 'PUNE' ? 'rgba(59, 130, 246, 0.3)' : 'rgba(245, 158, 11, 0.3)'}`,
                }}
              >
                {nodeId === 'PUNE' ? 'LOCAL TEST FIXTURE' : (nodeId === 'MUMBAI' || nodeId === 'DELHI') ? 'SIMULATED - TEST FIXTURE' : 'REGISTERED NODE'}
              </span>
            </div>
          </div>
        </div>

        <Badge variant={statusCfg.variant} size="sm" pulse={statusCfg.pulse}>
          {statusCfg.label}
        </Badge>
      </div>

      <div style={{ marginTop: '0.6rem', fontSize: '0.72rem', color: '#9ca3af', lineHeight: 1.35, background: 'rgba(255,255,255,0.02)', padding: '0.4rem 0.6rem', borderRadius: '6px' }}>
        {nodeId === 'PUNE'
          ? 'Pune Municipal Node: Local test fixture. Authorized F3 feature domain (1,200 local samples).'
          : (nodeId === 'MUMBAI' || nodeId === 'DELHI')
            ? `${node.nodeName}: Synthetic municipal test fixture. No remote connection; simulated local updates.`
            : 'Municipal participant node.'}
      </div>

      <div
        style={{
          marginTop: '1rem',
          paddingTop: '0.75rem',
          borderTop: '1px solid rgba(255, 255, 255, 0.06)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}
      >
        <div style={{ fontSize: '0.75rem', color: '#6b7280', display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
          <Radio size={12} color="#10b981" />
          <span>Last Seen: {formatRelativeTime(lastTime)}</span>
        </div>

        {onHeartbeat && (
          <Button
            variant="outline"
            size="sm"
            onClick={handlePing}
            isLoading={isPinging}
            style={{ fontSize: '0.75rem', padding: '0.3rem 0.6rem' }}
          >
            <Activity size={12} style={{ marginRight: '0.3rem' }} />
            Send Ping
          </Button>
        )}
      </div>

      {node.endpointUrl && (
        <div
          style={{
            marginTop: '0.5rem',
            fontSize: '0.7rem',
            color: '#6b7280',
            fontFamily: 'monospace',
            display: 'flex',
            alignItems: 'center',
            gap: '0.3rem',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          <ExternalLink size={10} />
          {node.endpointUrl}
        </div>
      )}
    </Card>
  );
};

export default CityNodeCard;
