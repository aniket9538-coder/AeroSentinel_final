export type UserRole = 'CITIZEN' | 'ANALYST' | 'AUTHORITY' | 'ADMIN';

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export type EventStatus = 'OPEN' | 'ASSIGNED' | 'IN_INSPECTION' | 'FIELD_INSPECTION' | 'ACTION_TAKEN' | 'RESOLVED' | 'DISMISSED';

export type AlertSeverity = 'INFO' | 'WARNING' | 'CRITICAL';

export interface City {
  id: string;
  name: string;
  state: string;
  country: string;
  latitude: number;
  longitude: number;
  active: boolean;
}

export interface AirObservation {
  id?: string;
  stationId: string;
  stationName: string;
  latitude: number;
  longitude: number;
  observedAt: string;
  pm25: number;
  pm10?: number;
  no2?: number;
  so2?: number;
  co?: number;
  o3?: number;
  aqi?: number;
  source: string;
}

export interface WeatherObservation {
  id?: string;
  cityId: string;
  latitude: number;
  longitude: number;
  observedAt: string;
  temperature: number;
  humidity: number;
  windSpeed: number;
  windDirection: number;
  rainfall: number;
  source: string;
}

export interface FireEvent {
  id: string;
  cityId: string;
  latitude: number;
  longitude: number;
  detectedAt: string;
  confidence: number;
  frp?: number;
  satellite: string;
}

export interface SatelliteObservation {
  id: string;
  h3Index: string;
  observedAt: string;
  no2Value?: number;
  so2Value?: number;
  aerosolIndicator?: number;
  sourceProduct: string;
}

export interface HotspotPrediction {
  id: string;
  h3Index: string;
  cityId: string;
  predictedAt: string;
  riskScore: number;
  riskLevel: RiskLevel;
  confidence: number;
  primaryFactors?: string[];
  modelVersion: string;
}

export interface HourlyForecast {
  targetHour: number;
  predictedPm25: number;
  lowerBound?: number;
  upperBound?: number;
  confidence: number;
}

export interface CellForecast {
  h3Index: string;
  generatedAt: string;
  unit: string;
  forecast: HourlyForecast[];
}

export interface VisionAnalysisSummary {
  analysisId?: string;
  analysisStatus: 'ANALYZED' | 'FALLBACK' | 'UNAVAILABLE' | string;
  detectedCategory: string;
  confidence: number;
  observations: string[];
  uncertainty: string[];
  visualIndicators?: Record<string, boolean>;
  model?: string;
  narrativeSummary?: string;
  modelVersion?: string;
  promptVersion?: string;
  analyzedAt?: string;
}

export interface CitizenReport {
  id: string;
  reportId?: string;
  cityId?: string;
  userId?: string;
  latitude: number;
  longitude: number;
  h3Index?: string;
  category: 'SMOKE' | 'DUST' | 'BURNING' | 'ODOR' | 'OTHER' | string;
  description: string;
  imageUrl?: string;
  photoUrl?: string;
  photoUrls?: string[];
  storageKey?: string;
  submittedAt: string;
  observedAt?: string;
  status: 'PENDING' | 'ANALYZED' | 'VERIFIED' | 'DISMISSED' | string;
  verificationStatus?: string;
  createdAt?: string;
  visionAnalysis?: VisionAnalysisSummary;
}

export type CitizenReportResponse = CitizenReport;

export interface GeminiAnalysis {
  id: string;
  citizenReportId: string;
  detectedCategory: string;
  confidence: number;
  narrativeSummary: string;
  evidenceCorroborated: boolean;
}

export interface PollutionEvent {
  id: string;
  h3Index: string;
  eventCode: string;
  severity: RiskLevel;
  status: EventStatus;
  startedAt: string;
  resolvedAt?: string;
  evidenceList?: EventEvidence[];
}

export interface EventEvidence {
  id: string;
  sourceType: string;
  evidenceKey: string;
  evidenceValue: string;
  weight: number;
}

export interface Alert {
  id: string;
  eventId?: string;
  h3Index: string;
  severity: AlertSeverity;
  title: string;
  message: string;
  status: 'OPEN' | 'ACKNOWLEDGED' | 'ASSIGNED' | 'IN_INSPECTION' | 'ACTION_TAKEN' | 'RESOLVED' | 'CLOSED' | 'DISMISSED';
  createdAt: string;
  acknowledgedAt?: string;
}

export interface AuthorityAction {
  id: string;
  alertId: string;
  actionType: string;
  actionDetails: string;
  performedBy: string;
  performedAt: string;
}

export interface Inspection {
  id: string;
  alertId: string;
  assignedTeam: string;
  scheduledAt: string;
  findings?: string;
  status: 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED';
}

// Canonical F9 Federated Network Types
export * from './federated';

// Canonical F8 Monitoring Gap & Recommendation Types
export * from './monitoring';

// Canonical F1 Phase 5/6 Types
export * from './city';
export * from './airQuality';

// Canonical F2 Phase 5/6 Types
export * from './weather';
export * from './grid';

// Canonical F3 Phase 4 Types
export * from './hotspot';

// Canonical F4 Types
export * from './forecast';

// Canonical F5 Evidence & WHY Types
export * from './evidence';

// Canonical F5-P5 Authority Queue Types
export * from './alert';

// Canonical F5-P6 Field Team & Verification Types
export * from './inspection';

// Canonical F7 Event Context Types
export * from './event';

// Canonical Phase 4 Assistant Types
export * from './assistant';

