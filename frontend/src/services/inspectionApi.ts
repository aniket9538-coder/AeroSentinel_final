import apiClient from './api';
import {
  FieldTeam,
  FieldVerification,
  InspectionResponse,
  AssignTeamRequest,
  ReassignTeamRequest,
  SubmitVerificationRequest,
} from '../types/inspection';

export const inspectionApi = {
  /**
   * Lists municipal field teams, optionally filtered by city or availability status.
   */
  getFieldTeams: async (params?: { cityId?: string; status?: string }): Promise<FieldTeam[]> => {
    const query = new URLSearchParams();
    if (params?.cityId) query.append('cityId', params.cityId);
    if (params?.status) query.append('status', params.status);

    const qs = query.toString() ? `?${query.toString()}` : '';
    const response = await apiClient.get<FieldTeam[]>(`/field-teams${qs}`);
    return response.data;
  },

  /**
   * Retrieves single field team details by ID.
   */
  getFieldTeamById: async (teamId: string): Promise<FieldTeam> => {
    if (!teamId || !teamId.trim()) {
      throw new Error('INVALID_INPUT: Field Team ID cannot be empty');
    }
    const response = await apiClient.get<FieldTeam>(`/field-teams/${encodeURIComponent(teamId)}`);
    return response.data;
  },

  /**
   * Assigns a field team to an acknowledged alert candidate.
   */
  assignFieldTeam: async (alertId: string, request: AssignTeamRequest): Promise<InspectionResponse> => {
    if (!alertId || !alertId.trim()) {
      throw new Error('INVALID_INPUT: Alert ID cannot be empty');
    }
    if (!request.teamId || !request.teamId.trim()) {
      throw new Error('INVALID_INPUT: Team ID is required');
    }
    const response = await apiClient.post<InspectionResponse>(
      `/alerts/${encodeURIComponent(alertId)}/assign`,
      request
    );
    return response.data;
  },

  /**
   * Reassigns an active field assignment to a different eligible municipal team (Feature 7 / Phase 8).
   */
  reassignFieldTeam: async (alertId: string, request: ReassignTeamRequest): Promise<InspectionResponse> => {
    if (!alertId || !alertId.trim()) {
      throw new Error('INVALID_INPUT: Alert ID cannot be empty');
    }
    if (!request.newTeamId || !request.newTeamId.trim()) {
      throw new Error('INVALID_INPUT: New Team ID is required');
    }
    if (!request.reason || !request.reason.trim()) {
      throw new Error('VALIDATION_ERROR: Reassignment reason is mandatory');
    }
    const response = await apiClient.post<InspectionResponse>(
      `/alerts/${encodeURIComponent(alertId)}/reassign`,
      request
    );
    return response.data;
  },

  /**
   * Retrieves active or latest field assignment for an alert.
   */
  getAlertAssignment: async (alertId: string): Promise<InspectionResponse | null> => {
    if (!alertId || !alertId.trim()) {
      throw new Error('INVALID_INPUT: Alert ID cannot be empty');
    }
    try {
      const response = await apiClient.get<InspectionResponse>(
        `/alerts/${encodeURIComponent(alertId)}/assignment`
      );
      return response.data;
    } catch (err: any) {
      if (err?.response?.status === 404) {
        return null;
      }
      throw err;
    }
  },

  /**
   * Retrieves inspection details by inspection ID.
   */
  getInspectionById: async (inspectionId: string): Promise<InspectionResponse> => {
    if (!inspectionId || !inspectionId.trim()) {
      throw new Error('INVALID_INPUT: Inspection ID cannot be empty');
    }
    const response = await apiClient.get<InspectionResponse>(
      `/inspections/${encodeURIComponent(inspectionId)}`
    );
    return response.data;
  },

  /**
   * Starts an assigned inspection, transitioning it to IN_PROGRESS.
   */
  startInspection: async (inspectionId: string): Promise<InspectionResponse> => {
    if (!inspectionId || !inspectionId.trim()) {
      throw new Error('INVALID_INPUT: Inspection ID cannot be empty');
    }
    const response = await apiClient.patch<InspectionResponse>(
      `/inspections/${encodeURIComponent(inspectionId)}/start`
    );
    return response.data;
  },

  /**
   * Submits an observational field verification report for an IN_PROGRESS inspection.
   */
  submitVerification: async (
    inspectionId: string,
    request: SubmitVerificationRequest
  ): Promise<InspectionResponse> => {
    if (!inspectionId || !inspectionId.trim()) {
      throw new Error('INVALID_INPUT: Inspection ID cannot be empty');
    }
    if (!request.observedConditions || !request.observedConditions.trim()) {
      throw new Error('VALIDATION_ERROR: Observed conditions (OBSERVED FIELD EVIDENCE) are required');
    }
    if (!['CONFIRMED', 'REJECTED', 'NEEDS_FOLLOW_UP'].includes(request.verificationResult)) {
      throw new Error('VALIDATION_ERROR: Invalid verification result');
    }
    const response = await apiClient.post<InspectionResponse>(
      `/inspections/${encodeURIComponent(inspectionId)}/verification`,
      request
    );
    return response.data;
  },

  /**
   * Retrieves all verification records for an alert.
   */
  getAlertVerifications: async (alertId: string): Promise<FieldVerification[]> => {
    if (!alertId || !alertId.trim()) {
      throw new Error('INVALID_INPUT: Alert ID cannot be empty');
    }
    const response = await apiClient.get<FieldVerification[]>(
      `/alerts/${encodeURIComponent(alertId)}/verifications`
    );
    return response.data;
  },

  /**
   * Retrieves all verification records for a specific inspection.
   */
  getInspectionVerifications: async (inspectionId: string): Promise<FieldVerification[]> => {
    if (!inspectionId || !inspectionId.trim()) {
      throw new Error('INVALID_INPUT: Inspection ID cannot be empty');
    }
    const response = await apiClient.get<FieldVerification[]>(
      `/inspections/${encodeURIComponent(inspectionId)}/verifications`
    );
    return response.data;
  },
};

export default inspectionApi;
