/**
 * AeroSentinel Federated City Network (F9) Canonical Types
 * Matches Spring Boot Federated Control Plane DTOs & PostgreSQL Schemas
 */

export type NodeStatus = 'ONLINE' | 'OFFLINE' | 'TRAINING' | 'STALE';

export type RoundStatus =
  | 'CREATED'
  | 'MODEL_DISTRIBUTED'
  | 'TRAINING'
  | 'UPDATES_COLLECTING'
  | 'AGGREGATING'
  | 'COMPLETED'
  | 'FAILED';

export type UpdateStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'INVALID';

export interface FederatedNode {
  nodeId: string;
  nodeName: string;
  cityId: string;
  status: NodeStatus | string;
  modelVersion: string;
  lastSeenAt?: string;
  endpointUrl?: string | null;
  // Backward compatibility aliases
  id?: string;
  lastUpdateAt?: string;
}

export interface EvaluationMetrics {
  mae: number;
  rmse: number;
  rocAuc: number;
  brierScore: number;
}

export interface RoundResponse {
  roundId: string;
  baseModelVersion: string;
  status: RoundStatus | string;
  minQuorum: number;
  participatingNodes: string[];
  createdAt: string;
}

export interface RoundUpdateItem {
  updateId: string;
  roundId: string;
  cityName: string;
  status: UpdateStatus | string;
  sampleCount: number;
  submittedAt: string;
}

export interface RoundDetail extends RoundResponse {
  targetModelVersion?: string;
  receivedUpdatesCount: number;
  totalSamples?: number | null;
  aggregatedMetrics?: EvaluationMetrics | null;
  failureReason?: string | null;
  completedAt?: string | null;
  updates: RoundUpdateItem[];
}

export interface GlobalModel {
  modelVersion: string;
  baseModelVersion?: string | null;
  roundId?: string | null;
  isActive: boolean;
  participatingNodes: string[];
  totalSamples: number;
  metrics: {
    mae?: number;
    rmse?: number;
    rocAuc?: number;
    brierScore?: number;
    [key: string]: number | undefined;
  };
  artifactPath?: string;
  createdAt: string;
}

export interface CreateRoundRequest {
  roundId?: string;
  baseModelVersion: string;
  minQuorum?: number;
  participatingNodes?: string[];
}

export interface NodeHeartbeatRequest {
  status?: NodeStatus;
  modelVersion?: string;
}

export interface AggregationResponse {
  roundId: string;
  status: string;
  globalModelVersion: string;
  participatingNodesCount: number;
  totalSamplesAggregated: number;
}

export interface FederatedOverviewStats {
  activeModelVersion: string;
  totalRounds: number;
  onlineNodesCount: number;
  totalNodesCount: number;
  totalTelemetrySamples: number;
  systemHealth: 'HEALTHY' | 'DEGRADED' | 'CRITICAL';
}

export interface EvaluationComparison {
  modelVersion: string;
  baselineName?: string;
  baseModelVersion?: string;
  featureSchemaVersion?: string;
  parameterCount?: number;
  testSampleCount: number;
  testSampleSize?: number;
  globalMetrics: EvaluationMetrics;
  localBaselineMetrics: EvaluationMetrics;
  puneLocalMetrics?: EvaluationMetrics;
  naiveBaselineMetrics: EvaluationMetrics;
  generalizationDeltaMae?: number;
  generalizationDeltaRmse?: number;
  evaluationDatasetProvenance?: string;
  geographicValidityNotice: string;
  domainLimitationNote?: string;
  evaluatedAt: string;
  evaluationTimestamp?: string;
}
