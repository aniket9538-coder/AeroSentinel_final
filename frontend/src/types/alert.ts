export interface CitizenEvidenceItem {
  reportId: string;
  reportReference: string;
  h3Index: string;
  category: string;
  description: string;
  observedAt: string;
  visibleCondition?: string | null;
  visualConfidence?: number | null;
  visualObservations?: string[] | null;
  visualUncertainty?: string[] | null;
  photoUrl?: string | null;
  dataSource: string;
  relevanceTier: string;
  eventId?: string | null;
  evidenceKey: string;
}

export interface AuthorityQueueItem {
  alertId: string;
  eventId: string;
  eventCode: string;
  h3Index: string;
  predictionId: string;
  cityId: string;
  cityName?: string | null;
  status: 'OPEN' | 'ACKNOWLEDGED' | 'ASSIGNED' | 'IN_INSPECTION' | 'ACTION_TAKEN' | 'RESOLVED' | 'CLOSED' | 'DISMISSED';
  severity: string;
  riskScore: number;
  evidenceScore: number;
  triageState: 'ALERT_CANDIDATE' | 'MONITOR' | 'INSUFFICIENT_EVIDENCE';
  consistency: string;
  title: string;
  message: string;
  forecastSummary?: string | null;
  recommendedAction?: string | null;
  hasGeminiAnalysis: boolean;
  geminiSummary?: string | null;
  createdAt: string;
  updatedAt?: string;
  acknowledgedAt?: string | null;
  resolvedAt?: string | null;
  dismissedAt?: string | null;
  resolutionNotes?: string | null;
  dismissalReason?: string | null;

  // F5-P6 Field Team Assignment & Verification Fields
  activeAssignmentId?: string | null;
  assignedTeamId?: string | null;
  assignedTeamName?: string | null;
  assignmentStatus?: string | null;
  assignedAt?: string | null;
  verificationResult?: 'CONFIRMED' | 'REJECTED' | 'NEEDS_FOLLOW_UP' | null;
  verificationStatus?: string | null;
  inspectedAt?: string | null;

  // F7-P2 Citizen Evidence Bridge
  citizenEvidence?: CitizenEvidenceItem[] | null;
}
