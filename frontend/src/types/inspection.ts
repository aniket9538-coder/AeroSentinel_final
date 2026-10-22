export interface FieldTeam {
  id: string;
  teamCode: string;
  teamName: string;
  cityId?: string | null;
  status: 'AVAILABLE' | 'DISPATCHED' | 'OFF_DUTY';
  contactNumber?: string | null;
  leaderName?: string | null;
  createdAt: string;
}

export type VerificationResult = 'CONFIRMED' | 'REJECTED' | 'NEEDS_FOLLOW_UP';

export interface FieldVerification {
  id: string;
  inspectionId: string;
  alertId: string;
  eventId?: string | null;
  h3Index: string;
  predictionId?: string | null;
  verificationStatus: string;
  verificationResult: VerificationResult;
  observedConditions: string;
  inspectorNotes?: string | null;
  evidenceReferences?: string | null;
  verifiedBy?: string | null;
  inspectedAt: string;
  createdAt: string;
}

export interface InspectionResponse {
  id: string;
  alertId: string;
  teamId?: string | null;
  teamCode?: string | null;
  teamName?: string | null;
  eventId?: string | null;
  h3Index?: string | null;
  predictionId?: string | null;
  assignedBy?: string | null;
  assignedAt?: string | null;
  scheduledAt?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  findings?: string | null;
  notes?: string | null;
  status: 'SCHEDULED' | 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
  createdAt: string;
  updatedAt?: string | null;
  latestVerification?: FieldVerification | null;
}

export interface AssignTeamRequest {
  teamId: string;
  assignedBy?: string | null;
  scheduledAt?: string | null;
  notes?: string | null;
}

export interface SubmitVerificationRequest {
  verificationResult: VerificationResult;
  observedConditions: string;
  inspectorNotes?: string | null;
  evidenceReferences?: string | null;
  verifiedBy?: string | null;
  inspectedAt?: string | null;
}

export interface ReassignTeamRequest {
  newTeamId: string;
  reason: string;
  scheduledAt?: string | null;
  notes?: string | null;
}
