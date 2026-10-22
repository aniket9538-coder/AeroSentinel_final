import React, { useState, useEffect, useCallback } from 'react';
import { PageContainer } from '../../components/layout/PageContainer';
import { Button } from '../../components/common/Button';
import { FederatedStatus } from '../../components/federated/FederatedStatus';
import { NodeStatusGrid } from '../../components/federated/NodeStatusGrid';
import { GlobalModelHero } from '../../components/federated/GlobalModelHero';
import { RoundLifecycleManager } from '../../components/federated/RoundLifecycleManager';
import { ModelCatalogTable } from '../../components/federated/ModelCatalogTable';
import { InitiateRoundModal } from '../../components/federated/InitiateRoundModal';
import { federatedService } from '../../services/federated.service';
import {
  FederatedNode,
  RoundResponse,
  RoundDetail,
  GlobalModel,
  CreateRoundRequest,
} from '../../types/federated';
import { RefreshCw, Play, CheckCircle2, AlertCircle, Sparkles } from 'lucide-react';

export const FederatedNetwork: React.FC = () => {
  const [nodes, setNodes] = useState<FederatedNode[]>([]);
  const [rounds, setRounds] = useState<RoundResponse[]>([]);
  const [selectedRoundDetail, setSelectedRoundDetail] = useState<RoundDetail | null>(null);
  const [activeModel, setActiveModel] = useState<GlobalModel | null>(null);
  const [modelCatalog, setModelCatalog] = useState<GlobalModel[]>([]);

  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isLoadingRoundDetail, setIsLoadingRoundDetail] = useState(false);
  const [isInitiateModalOpen, setIsInitiateModalOpen] = useState(false);

  const [toastMessage, setToastMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(
    null
  );

  const showToast = (text: string, type: 'success' | 'error' = 'success') => {
    setToastMessage({ type, text });
    setTimeout(() => setToastMessage(null), 5000);
  };

  // Load round detail by roundId
  const loadRoundDetail = useCallback(async (roundId: string) => {
    setIsLoadingRoundDetail(true);
    try {
      const detail = await federatedService.getRound(roundId);
      setSelectedRoundDetail(detail);
    } catch (err: any) {
      console.error('Failed to load round details:', err);
    } finally {
      setIsLoadingRoundDetail(false);
    }
  }, []);

  // Fetch all primary console data
  const fetchData = useCallback(
    async (isManualRefresh = false) => {
      if (isManualRefresh) setIsRefreshing(true);
      try {
        const [nodesRes, roundsRes, activeModelRes, catalogRes] = await Promise.allSettled([
          federatedService.getNodes(),
          federatedService.getRounds(),
          federatedService.getActiveModel(),
          federatedService.getModelCatalog(),
        ]);

        if (nodesRes.status === 'fulfilled') {
          setNodes(nodesRes.value || []);
        }

        let loadedRounds: RoundResponse[] = [];
        if (roundsRes.status === 'fulfilled') {
          loadedRounds = roundsRes.value || [];
          setRounds(loadedRounds);
        }

        if (activeModelRes.status === 'fulfilled') {
          setActiveModel(activeModelRes.value);
        }

        if (catalogRes.status === 'fulfilled') {
          setModelCatalog(catalogRes.value || []);
        }

        // Pick latest round for detailed view
        if (loadedRounds.length > 0) {
          const targetRoundId = selectedRoundDetail?.roundId || loadedRounds[0].roundId;
          await loadRoundDetail(targetRoundId);
        }
      } catch (err) {
        console.error('Error fetching federated console data:', err);
      } finally {
        setIsLoading(false);
        setIsRefreshing(false);
      }
    },
    [loadRoundDetail, selectedRoundDetail?.roundId]
  );

  useEffect(() => {
    fetchData();
  }, []);

  // Heartbeat ping handler
  const handleHeartbeat = async (nodeId: string) => {
    try {
      const updatedNode = await federatedService.sendHeartbeat(nodeId, {
        status: 'ONLINE',
        modelVersion: activeModel?.modelVersion || 'global-v3',
      });
      setNodes((prev) =>
        prev.map((n) => ((n.nodeId || n.id) === nodeId ? { ...n, ...updatedNode } : n))
      );
      showToast(`Heartbeat signal acknowledged from ${updatedNode.nodeName || nodeId}`);
    } catch (err: any) {
      showToast(`Heartbeat ping failed: ${err.message}`, 'error');
    }
  };

  // Initiate training round handler
  const handleInitiateRound = async (payload: CreateRoundRequest) => {
    try {
      const created = await federatedService.createRound(payload);
      showToast(`Federated training round ${created.roundId} initiated successfully!`);
      await fetchData(true);
      await loadRoundDetail(created.roundId);
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || 'Failed to initiate round';
      showToast(msg, 'error');
      throw err;
    }
  };

  // Trigger FedAvg aggregation handler
  const handleTriggerAggregation = async (roundId: string) => {
    try {
      const agg = await federatedService.triggerAggregation(roundId);
      showToast(
        `FedAvg Consensus Complete! Published new consensus model: ${agg.globalModelVersion} (${agg.totalSamplesAggregated} samples aggregated)`
      );
      await fetchData(true);
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || 'Aggregation failed';
      showToast(msg, 'error');
      throw err;
    }
  };

  // Simulate local training execution handler
  const handleSimulateRound = async (roundId: string) => {
    try {
      await federatedService.simulateRound(roundId);
      showToast(
        'Genuine local Ridge training executed across municipal nodes! Parameter updates submitted.'
      );
      await fetchData(true);
      await loadRoundDetail(roundId);
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || 'Local training execution failed';
      showToast(msg, 'error');
      throw err;
    }
  };

  const onlineNodesCount = nodes.filter(
    (n) => (n.status || '').toUpperCase() === 'ONLINE' || (n.status || '').toUpperCase() === 'TRAINING'
  ).length;

  const totalCumulativeSamples = modelCatalog.length > 0
    ? modelCatalog.reduce((acc, m) => acc + (m.totalSamples || 0), 0)
    : (activeModel?.totalSamples || 0);

  return (
    <PageContainer
      title="Multi-City Federated Network Console"
      subtitle="Decentralized collaborative model learning across Pune, Mumbai, and Delhi without centralizing raw telemetry"
      action={
        <div style={{ display: 'flex', gap: '0.6rem' }}>
          <Button
            variant="outline"
            size="md"
            onClick={() => fetchData(true)}
            isLoading={isRefreshing}
          >
            <RefreshCw size={14} style={{ marginRight: '0.4rem' }} />
            Sync Network
          </Button>

          <Button
            variant="primary"
            size="md"
            onClick={() => setIsInitiateModalOpen(true)}
          >
            <Play size={14} style={{ marginRight: '0.4rem' }} />
            New Training Round
          </Button>
        </div>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
        {/* Toast Notification */}
        {toastMessage && (
          <div
            style={{
              padding: '0.85rem 1.25rem',
              borderRadius: '10px',
              background:
                toastMessage.type === 'success'
                  ? 'linear-gradient(135deg, rgba(16, 185, 129, 0.15), rgba(5, 150, 105, 0.25))'
                  : 'linear-gradient(135deg, rgba(239, 68, 68, 0.15), rgba(185, 28, 28, 0.25))',
              border: `1px solid ${toastMessage.type === 'success' ? '#10b981' : '#ef4444'}55`,
              color: toastMessage.type === 'success' ? '#10b981' : '#ef4444',
              display: 'flex',
              alignItems: 'center',
              gap: '0.6rem',
              fontSize: '0.85rem',
              boxShadow: '0 4px 12px rgba(0, 0, 0, 0.2)',
              animation: 'fadeIn 0.3s ease-in-out',
            }}
          >
            {toastMessage.type === 'success' ? (
              <CheckCircle2 size={18} />
            ) : (
              <AlertCircle size={18} />
            )}
            <span style={{ fontWeight: 500 }}>{toastMessage.text}</span>
          </div>
        )}

        {/* Engineering Simulation Disclaimer Banner */}
        <div
          style={{
            padding: '0.75rem 1rem',
            borderRadius: '8px',
            background: 'rgba(245, 158, 11, 0.08)',
            border: '1px solid rgba(245, 158, 11, 0.25)',
            color: '#fbbf24',
            display: 'flex',
            alignItems: 'center',
            gap: '0.6rem',
            fontSize: '0.8rem',
            lineHeight: 1.4,
          }}
        >
          <AlertCircle size={18} style={{ flexShrink: 0 }} />
          <span>
            <strong>Auditable Engineering Demonstration:</strong> Participating municipal nodes operate in local test fixture (Pune) and deterministic synthetic simulation (Mumbai, Delhi) modes. Real-world municipal networks and inter-city data permissions are currently unattached. Operational forecasts remain strictly limited to the Pune geographical domain.
          </span>
        </div>

        {/* 1. Orchestration Status & Overview Banner */}
        <FederatedStatus
          currentRound={rounds.length}
          totalNodes={nodes.length}
          activeNodes={onlineNodesCount}
          status={nodes.length > 0 && onlineNodesCount > 0 ? "SYNCHRONIZED" : "STANDBY"}
          activeModelVersion={activeModel?.modelVersion || (modelCatalog.length > 0 ? modelCatalog[0].modelVersion : 'None')}
          totalSamples={totalCumulativeSamples}
        />

        {/* 2. Municipal Federated Nodes Grid */}
        <NodeStatusGrid nodes={nodes} onHeartbeat={handleHeartbeat} />

        {/* 3. Active Global Model Hero Card */}
        <GlobalModelHero model={activeModel} isLoading={isLoading} />

        {/* 4. Round Lifecycle & Consensus Aggregator */}
        <RoundLifecycleManager
          rounds={rounds}
          selectedRoundDetail={selectedRoundDetail}
          isLoadingDetail={isLoadingRoundDetail}
          onSelectRound={(roundId) => loadRoundDetail(roundId)}
          onInitiateRoundClick={() => setIsInitiateModalOpen(true)}
          onTriggerAggregation={handleTriggerAggregation}
          onSimulateRound={handleSimulateRound}
        />

        {/* 5. Consensus Model Lineage & Catalog */}
        <ModelCatalogTable models={modelCatalog} isLoading={isLoading} />
      </div>

      {/* Initiate Round Modal */}
      <InitiateRoundModal
        isOpen={isInitiateModalOpen}
        onClose={() => setIsInitiateModalOpen(false)}
        activeModelVersion={activeModel?.modelVersion || (modelCatalog.length > 0 ? modelCatalog[0].modelVersion : 'global-v1')}
        onSubmit={handleInitiateRound}
      />
    </PageContainer>
  );
};

export default FederatedNetwork;
