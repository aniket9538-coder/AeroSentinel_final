/**
 * Admin Observability & System Monitoring Types (Phase 9)
 */

export interface BackendDetails {
  status: 'UP' | 'DOWN' | 'DEGRADED';
  serviceName: string;
  javaVersion: string;
  freeMemoryMb: number;
  totalMemoryMb: number;
  maxMemoryMb: number;
  activeThreads: number;
}

export interface DatabaseDetails {
  status: 'UP' | 'DOWN';
  databaseProduct: string;
  connectionValid: boolean;
  activeConnections: number;
  idleConnections: number;
  totalConnections: number;
  threadsAwaitingConnection: number;
  tableRowCounts: Record<string, number>;
}

export interface AiServiceDetails {
  status: 'UP' | 'DOWN' | 'DEGRADED';
  endpoint: string;
  latencyMs: number;
  modelsReady: boolean;
  loadedArtifacts: string[];
  errorMessage?: string | null;
}

export interface ServiceHealth {
  overallStatus: 'HEALTHY' | 'DEGRADED' | 'CRITICAL';
  evaluatedAt: string;
  uptimeSeconds: number;
  backend: BackendDetails;
  database: DatabaseDetails;
  aiService: AiServiceDetails;
}

export interface ProviderItem {
  providerId: string;
  displayName: string;
  category: 'AIR_QUALITY' | 'WEATHER' | 'SATELLITE' | 'OFFICIAL_WARNING';
  configured: boolean;
  endpoint: string;
  status: 'ACTIVE' | 'STALE' | 'NOT_CONFIGURED' | 'DEGRADED' | 'UNAVAILABLE';
  lastIngestionAt?: string | null;
  latestObservationAt?: string | null;
  recordCount: number;
  freshnessMinutes?: number | null;
  freshnessStatus: 'FRESH' | 'ACCEPTABLE' | 'STALE' | 'NO_DATA' | 'NOT_CONFIGURED';
  safeNotes: string;
}

export interface ProviderObservability {
  evaluatedAt: string;
  activeProvidersCount: number;
  totalProvidersCount: number;
  totalObservationsIngested: number;
  providers: ProviderItem[];
}

export interface ModelItem {
  modelId: string;
  modelName: string;
  framework: string;
  status: 'AVAILABLE' | 'LOADED' | 'CONFIGURED' | 'FALLBACK_MODE' | 'UNAVAILABLE';
  ready: boolean;
  artifactIdentifier: string;
  operationalDetails: string;
  lastVerifiedAt: string;
}

export interface ModelObservability {
  evaluatedAt: string;
  allModelsReady: boolean;
  readyModelsCount: number;
  totalModelsCount: number;
  models: ModelItem[];
}

export interface SafeAuditEvent {
  actionId: string;
  alertId?: string | null;
  actionType: string;
  previousStatus?: string | null;
  newStatus?: string | null;
  actorUsername: string;
  actorRole: string;
  safeReason: string;
  performedAt: string;
  redacted: boolean;
}

export interface DiagnosticRecommendation {
  id: string;
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  component: string;
  title: string;
  details: string;
  recommendedAction: string;
}

export interface RecordCounts {
  totalAirObservations: number;
  totalWeatherObservations: number;
  activeStationsCount: number;
  activeAlertsCount: number;
  citizenReportsCount: number;
  totalCitiesCount: number;
  totalAuthorityActions: number;
}

export interface MonitoringGapContext {
  coverageRadiusKm: number;
  gapThresholdKm: number;
  totalActiveStations: number;
  policyVersion: string;
  notice: string;
}

export interface AdminOverview {
  systemHealth: 'HEALTHY' | 'DEGRADED' | 'CRITICAL';
  generatedAt: string;
  serviceStatusSummary: Record<string, string>;
  recordCounts: RecordCounts;
  monitoringGapContext: MonitoringGapContext;
  diagnosticRecommendations: DiagnosticRecommendation[];
}
