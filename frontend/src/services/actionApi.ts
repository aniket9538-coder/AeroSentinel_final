import apiClient from './api';

export interface AuthorityActionItem {
  id: string;
  actionId?: string;
  alertId: string;
  eventId?: string;
  actionType: string;
  actionDetails: string;
  performedBy: string;
  performedAt: string;
  eventStatus?: string;
  alertStatus?: string;
  previousStatus?: string;
  newStatus?: string;
  reason?: string;
}

export interface RecordActionRequest {
  alertId: string;
  actionType: string;
  notes: string;
  performedBy?: string;
}

export const actionApi = {
  /**
   * Retrieves all recorded actions for an alert.
   */
  getActions: async (alertId: string): Promise<AuthorityActionItem[]> => {
    if (!alertId || !alertId.trim()) {
      throw new Error('INVALID_INPUT: Alert ID cannot be empty');
    }
    const response = await apiClient.get<AuthorityActionItem[]>(
      `/actions?alertId=${encodeURIComponent(alertId.trim())}`
    );
    return response.data;
  },

  /**
   * Records a municipal authority action against an alert.
   */
  recordAction: async (request: RecordActionRequest): Promise<AuthorityActionItem> => {
    if (!request.alertId || !request.alertId.trim()) {
      throw new Error('INVALID_INPUT: Alert ID cannot be empty');
    }
    if (!request.actionType || !request.actionType.trim()) {
      throw new Error('INVALID_INPUT: Action type is required');
    }
    if (!request.notes || !request.notes.trim()) {
      throw new Error('INVALID_INPUT: Action notes/details are required');
    }

    const response = await apiClient.post<AuthorityActionItem>('/actions', request);
    return response.data;
  },
};

export default actionApi;
