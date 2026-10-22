import apiClient from './api';
import { EvidenceSummaryResponse, TriageCandidateDto } from '../types/evidence';

/**
 * Validates the raw API response structure before returning to the UI.
 * Ensures the response strictly conforms to the F5-P2/P3 authoritative contract.
 */
export function validateEvidenceResponse(data: any): EvidenceSummaryResponse {
  if (!data || typeof data !== 'object') {
    throw new Error('MALFORMED_RESPONSE: Evidence response must be a non-null object');
  }

  if (data.status !== 'SUCCESS') {
    throw new Error(`API_ERROR: Evidence request returned status '${data.status}'`);
  }

  if (!data.context || typeof data.context !== 'object') {
    throw new Error('MALFORMED_RESPONSE: Missing context block in evidence response');
  }

  if (!data.context.h3Index) {
    throw new Error('MALFORMED_RESPONSE: Missing context.h3Index');
  }

  if (!data.observedFacts || typeof data.observedFacts !== 'object') {
    throw new Error('MALFORMED_RESPONSE: Missing observedFacts block in evidence response');
  }

  if (!data.modelOutputs || typeof data.modelOutputs !== 'object') {
    throw new Error('MALFORMED_RESPONSE: Missing modelOutputs block in evidence response');
  }

  if (!data.modelOutputs.hotspot || typeof data.modelOutputs.hotspot !== 'object') {
    throw new Error('MALFORMED_RESPONSE: Missing modelOutputs.hotspot in evidence response');
  }

  if (!data.evidence || typeof data.evidence !== 'object') {
    throw new Error('MALFORMED_RESPONSE: Missing evidence block in evidence response');
  }

  if (typeof data.evidence.evidenceScore !== 'number' || isNaN(data.evidence.evidenceScore)) {
    throw new Error('MALFORMED_RESPONSE: Non-numeric evidenceScore');
  }

  return data as EvidenceSummaryResponse;
}

export const evidenceApi = {
  /**
   * Authoritative Phase 7: Retrieves explainable multi-source evidence triage candidate for an H3 cell.
   * Calls GET /api/v1/evidence/triage/h3/{h3Index}.
   */
  getTriageCandidate: async (h3Index: string, signal?: AbortSignal): Promise<TriageCandidateDto> => {
    if (!h3Index || !h3Index.trim()) {
      throw new Error('INVALID_INPUT: H3 index cannot be empty');
    }
    const trimmedH3 = h3Index.trim();
    const response = await apiClient.get<TriageCandidateDto>(
      `/evidence/triage/h3/${encodeURIComponent(trimmedH3)}`,
      { signal, timeout: 20000 }
    );
    return response.data;
  },

  /**
   * Authoritative Phase 7: Fuses and idempotently persists canonical event & alert candidate if verified.
   * Calls POST /api/v1/evidence/triage/fuse.
   */
  fuseAndPersist: async (h3Index: string): Promise<TriageCandidateDto> => {
    if (!h3Index || !h3Index.trim()) {
      throw new Error('INVALID_INPUT: H3 index cannot be empty');
    }
    const trimmedH3 = h3Index.trim();
    const response = await apiClient.post<TriageCandidateDto>(
      `/evidence/triage/fuse?h3Index=${encodeURIComponent(trimmedH3)}`,
      {},
      { timeout: 25000 }
    );
    return response.data;
  },

  /**
   * Retrieves authoritative, multi-source evidence and grounded Gemini reasoning for a single H3 hexagon.
   *
   * @param h3Index 15-character Uber H3 resolution 8 index
   * @param signal optional AbortSignal for rapid-selection request cancellation
   * @returns grounded EvidenceSummaryResponse
   */
  getEvidence: async (h3Index: string, signal?: AbortSignal): Promise<EvidenceSummaryResponse> => {
    if (!h3Index || !h3Index.trim()) {
      throw new Error('INVALID_INPUT: H3 index cannot be empty');
    }

    const trimmedH3 = h3Index.trim();
    const response = await apiClient.get<EvidenceSummaryResponse>(
      `/evidence/hotspot/${encodeURIComponent(trimmedH3)}`,
      { signal, timeout: 25000 }
    );

    return validateEvidenceResponse(response.data);
  },

  /**
   * Explicitly triggers fresh AI evidence scoring & Gemini narrative orchestration for an H3 cell.
   * Calls POST /api/v1/evidence/orchestrate.
   */
  orchestrateEvidence: async (h3Index: string): Promise<EvidenceSummaryResponse> => {
    if (!h3Index || !h3Index.trim()) {
      throw new Error('INVALID_INPUT: H3 index cannot be empty');
    }

    const trimmedH3 = h3Index.trim();
    const response = await apiClient.post<EvidenceSummaryResponse>(
      `/evidence/orchestrate?h3Index=${encodeURIComponent(trimmedH3)}`,
      {},
      { timeout: 30000 }
    );

    return validateEvidenceResponse(response.data);
  },
};

export default evidenceApi;

