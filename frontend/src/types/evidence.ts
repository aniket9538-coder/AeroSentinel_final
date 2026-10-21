/**
 * Authoritative TypeScript contract for Feature 5 (Evidence & WHY Diagnostic Layer).
 * Matches backend EvidenceSummaryResponse DTO exactly.
 */

import type { ConfidenceBreakdown } from './hotspot';

export interface EvidenceContext {
  h3Index: string;
  cityId?: string | null;
  cityName?: string | null;
  predictionId?: string | null;
  featureSnapshotId?: string | null;
  eventId?: string | null;
  eventCode?: string | null;
  generatedAt: string;
}

export interface ObservedAirContext {
  dataQuality?: string;
  status?: string;
  pm25?: number | null;
  pm10?: number | null;
  no2?: number | null;
  so2?: number | null;
  co?: number | null;
  o3?: number | null;
  observedAt?: string | null;
  stationId?: string | null;
  recentPm25Mean24h?: number | null;
  groundPm25Mean?: number | null;
}

export interface ObservedWeatherContext {
  dataQuality?: string;
  status?: string;
  temperature?: number | null;
  humidity?: number | null;
  windSpeedKmh?: number | null;
  windSpeedMps?: number | null;
  windDirection?: number | null;
  surfacePressure?: number | null;
  precipitation?: number | null;
  observedAt?: string | null;
}

export interface ObservedMonitoringCoverage {
  dataQuality?: string;
  status?: string;
  nearestStationDistanceKm?: number | null;
  stationsWithin5kmCount?: number | null;
  monitoringCoverageGapFlag?: number | boolean | null;
  spatialCoverageConfidence?: number | null;
}

export interface ObservedSpatialDispersion {
  dataQuality?: string;
  status?: string;
  pm25SpatialLagMean?: number | null;
  windU?: number | null;
  windV?: number | null;
}

export interface ObservedGisContext {
  dataQuality?: string;
  status?: string;
  distToNearestIndustrialKm?: number | null;
  distToNearestMajorRoadKm?: number | null;
  sensitiveReceptorsCount2km?: number | null;
  industrialZoneWithin2kmFlag?: number | boolean | null;
  fireCount24h25km?: number | null;
  nearestFireDistanceKm?: number | null;
}

export interface ObservedFacts {
  air?: ObservedAirContext | null;
  weather?: ObservedWeatherContext | null;
  monitoringCoverage?: ObservedMonitoringCoverage | null;
  spatialDispersion?: ObservedSpatialDispersion | null;
  gisContext?: ObservedGisContext | null;
}

export interface HotspotModelOutput {
  riskScore: number;
  operationalThreshold: number;
  isHotspot: boolean;
  riskLevel: 'LOW' | 'MODERATE' | 'HIGH' | 'CRITICAL' | string;
  confidence: number;
  confidenceBreakdown?: ConfidenceBreakdown | null;
  modelVersion: string;
  engineType: string;
}

export interface ForecastHorizonItem {
  horizonHours: number;
  targetTime?: string | null;
  predictedPm25: number;
  lowerBound?: number | null;
  upperBound?: number | null;
  unit?: string | null;
}

export interface ForecastModelOutput {
  baseTimestamp?: string | null;
  generatedAt?: string | null;
  forecastModelVersion: string;
  parentPredictionId?: string | null;
  forecastConfidence?: number | null; // strictly null per locked F4 contract
  horizons: ForecastHorizonItem[];
}

export interface ModelOutputs {
  hotspot: HotspotModelOutput;
  forecast?: ForecastModelOutput | null;
}

export interface EvidenceSignal {
  signalId?: string;
  sourceType: string;
  description: string;
  timestamp?: string;
  confidenceScore?: number;
  dataSource?: string | null;
  relevanceTier?: string | null;
  sourceRef?: string | null;
  level?: string;
  value?: number;
  unit?: string;
  observedAt?: string;
}

export interface ScoreBreakdown {
  observationStrength?: number | null;
  mlForecastSupport?: number | null;
  multiSourceAgreement?: number | null;
  spatialConsistency?: number | null;
  temporalPersistence?: number | null;
  recencyFactor?: number | null;
  conflictPenalty?: number | null;
  evidenceCompleteness?: number | null;
  finalEvidenceScore?: number | null;
  unweightedEvidenceScore?: number | null;
  evidenceScore?: number | null;
}

export interface EvidenceData {
  signals: EvidenceSignal[];
  evidenceScore: number;
  scoreBreakdown?: ScoreBreakdown | null;
  consistency: string;
  triageState: 'ALERT_CANDIDATE' | 'MONITOR' | 'INSUFFICIENT_EVIDENCE' | string;
  sourceMatrix?: Record<string, string>;
  unavailableSources?: string[];
  conflictingNotes?: string[];
  clusterH3Cells?: string[];
  recencyTimestamp?: string | null;
}

export interface AiInterpretation {
  summaryPublic: string;
  summaryAnalyst?: string | null;
  detectedCondition?: string | null;
  supportingSignals?: string[];
  forecastTrajectory?: string | null;
  uncertaintyStatement?: string | null;
  unsupportedConclusions?: string[];
  causalClaimSupported?: boolean | null;
  isGrounded?: boolean | null;
  modelVersion?: string | null;
  promptVersion?: string | null;
  privacyCheckPassed?: boolean | null;
  geminiModel?: string | null;
}

export interface RecommendedVerification {
  action: string;
  priority: 'ROUTINE' | 'ELEVATED' | 'HIGH' | 'URGENT' | string;
  guidelines?: string[];
}

export interface EvidenceProvenance {
  h3Index: string;
  cityId?: string | null;
  f3PredictionId?: string | null;
  parentPredictionId?: string | null;
  f3ModelVersion?: string | null;
  f4ModelVersion?: string | null;
  f5ScoringVersion?: string | null;
  geminiModelVersion?: string | null;
  geminiPromptVersion?: string | null;
  evaluatedAt?: string | null;
}

export interface EvidenceSummaryResponse {
  context: EvidenceContext;
  observedFacts: ObservedFacts;
  modelOutputs: ModelOutputs;
  evidence: EvidenceData;
  aiInterpretation?: AiInterpretation | null;
  recommendedVerification?: RecommendedVerification | null;
  provenance: EvidenceProvenance;
  status: string;
}

export type EvidenceClass =
  | 'CITIZEN_SUBMITTED'
  | 'CITIZEN_VISION'
  | 'ENVIRONMENTAL_MEASUREMENT'
  | 'WEATHER_MODEL_CONTEXT'
  | 'OFFICIAL_WARNING'
  | 'EXPERIMENTAL_ML_OUTPUT';

export interface NormalizedEvidenceItem {
  evidenceId: string;
  evidenceClass: EvidenceClass;
  sourceType: string;
  sourceId?: string | null;
  eventTimestamp?: string | null;
  observedTimestamp?: string | null;
  h3Index: string;
  measurementValue?: number | null;
  measurementUnit?: string | null;
  freshnessStatus: string;
  qualityStatus: string;
  isIndependent: boolean;
  clusterGroupId?: string | null;
  metadata?: Record<string, any> | null;
}

export interface ExcludedEvidenceDto {
  evidenceId: string;
  source: string;
  reasonCode: string;
  description: string;
}

export interface TriageCandidateDto {
  candidateId: string;
  h3Index: string;
  cityId?: string | null;
  cityName?: string | null;
  category: string;
  triageStatus: 'PROVISIONAL_TRIAGE' | 'ALERT_CANDIDATE' | 'MONITOR' | 'INSUFFICIENT_EVIDENCE' | string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | string;
  evidenceScore: number;
  riskScore?: number | null;
  visualConfidence?: number | null;
  independentSourceCount: number;
  relatedSourceCount: number;
  isProvisional: boolean;
  contributingEvidence: NormalizedEvidenceItem[];
  excludedEvidence: ExcludedEvidenceDto[];
  explanations: string[];
  sourceIndependenceBreakdown: Record<string, string>;
  policyVersion: string;
  createdAt: string;
}

