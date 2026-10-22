import apiClient from './api';
import {
  FederatedNode,
  RoundResponse,
  RoundDetail,
  GlobalModel,
  CreateRoundRequest,
  NodeHeartbeatRequest,
  AggregationResponse,
  EvaluationComparison,
} from '../types';

export const federatedService = {
  /**
   * Retrieves all registered municipal federated nodes (Pune, Mumbai, Delhi).
   */
  getNodes: async (): Promise<FederatedNode[]> => {
    const response = await apiClient.get<FederatedNode[]>('/federated/nodes');
    return response.data;
  },

  /**
   * Retrieves health, model version, and endpoint for a specific municipal node.
   */
  getNode: async (nodeId: string): Promise<FederatedNode> => {
    const response = await apiClient.get<FederatedNode>(`/federated/nodes/${nodeId}`);
    return response.data;
  },

  /**
   * Records a heartbeat for a municipal node, updating status and model version.
   */
  sendHeartbeat: async (
    nodeId: string,
    payload?: NodeHeartbeatRequest
  ): Promise<FederatedNode> => {
    const response = await apiClient.post<FederatedNode>(
      `/federated/nodes/${nodeId}/heartbeat`,
      payload || {}
    );
    return response.data;
  },

  /**
   * Lists all federated training rounds.
   */
  getRounds: async (): Promise<RoundResponse[]> => {
    const response = await apiClient.get<RoundResponse[]>('/federated/rounds');
    return response.data;
  },

  /**
   * Retrieves round lifecycle details, quorum status, and submitted updates.
   */
  getRound: async (roundId: string): Promise<RoundDetail> => {
    const response = await apiClient.get<RoundDetail>(`/federated/rounds/${roundId}`);
    return response.data;
  },

  /**
   * Initiates a new federated training round with base model version and minimum quorum.
   */
  createRound: async (payload: CreateRoundRequest): Promise<RoundResponse> => {
    const response = await apiClient.post<RoundResponse>('/federated/rounds', payload);
    return response.data;
  },

  /**
   * Triggers FedAvg aggregation for a round whose quorum has been satisfied.
   */
  triggerAggregation: async (roundId: string): Promise<AggregationResponse> => {
    const response = await apiClient.post<AggregationResponse>(
      `/federated/rounds/${roundId}/aggregate`
    );
    return response.data;
  },

  /**
   * Retrieves the currently active consensus global model and its validation metrics.
   */
  getActiveModel: async (): Promise<GlobalModel> => {
    const response = await apiClient.get<GlobalModel>('/federated/models/active');
    return response.data;
  },

  /**
   * Retrieves historical consensus global models catalog.
   */
  getModelCatalog: async (): Promise<GlobalModel[]> => {
    const response = await apiClient.get<GlobalModel[]>('/federated/models/catalog');
    return response.data;
  },

  /**
   * Triggers genuine local training execution for participating municipal nodes in a round.
   */
  simulateRound: async (roundId: string): Promise<Record<string, any>> => {
    const response = await apiClient.post<Record<string, any>>(
      `/federated/rounds/${roundId}/simulate-round`
    );
    return response.data;
  },

  /**
   * Retrieves held-out baseline cross-city evaluation metrics for a global model version.
   */
  getEvaluation: async (version: string): Promise<EvaluationComparison> => {
    const response = await apiClient.get<EvaluationComparison>(
      `/federated/models/${version}/evaluation`
    );
    return response.data;
  },
};

export default federatedService;
