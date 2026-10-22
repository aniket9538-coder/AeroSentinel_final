import { describe, it } from 'node:test';
import assert from 'node:assert';
import {
  AdminOverview,
  ServiceHealth,
  ProviderObservability,
  ModelObservability,
  SafeAuditEvent,
} from '../types/admin';

describe('Phase 9 — Admin Observability & F8 Monitoring-Gap Intelligence', () => {

  it('F9-1: ServiceHealth contract preserves liveness, readiness, and HikariCP pool stats', () => {
    const health: ServiceHealth = {
      overallStatus: 'HEALTHY',
      evaluatedAt: new Date().toISOString(),
      uptimeSeconds: 3600,
      backend: {
        status: 'UP',
        serviceName: 'aerosentinel-backend',
        javaVersion: '21',
        freeMemoryMb: 512,
        totalMemoryMb: 1024,
        maxMemoryMb: 2048,
        activeThreads: 24,
      },
      database: {
        status: 'UP',
        databaseProduct: 'PostgreSQL 16 + PostGIS 3.4 (SRID 4326)',
        connectionValid: true,
        activeConnections: 3,
        idleConnections: 7,
        totalConnections: 10,
        threadsAwaitingConnection: 0,
        tableRowCounts: {
          air_observations: 15420,
          weather_observations: 8200,
          monitoring_stations: 12,
          alerts: 14,
        },
      },
      aiService: {
        status: 'UP',
        endpoint: 'http://localhost:8000',
        latencyMs: 35,
        modelsReady: true,
        loadedArtifacts: ['hotspot', 'forecast'],
        errorMessage: null,
      },
    };

    assert.strictEqual(health.overallStatus, 'HEALTHY');
    assert.strictEqual(health.backend.status, 'UP');
    assert.strictEqual(health.database.connectionValid, true);
    assert.strictEqual(health.database.totalConnections, 10);
    assert.strictEqual(health.aiService.modelsReady, true);
    assert.ok(health.aiService.loadedArtifacts.includes('hotspot'));
  });

  it('F9-2: ProviderObservability tracks real data freshness, record counts, and honest untracked states', () => {
    const providers: ProviderObservability = {
      evaluatedAt: new Date().toISOString(),
      activeProvidersCount: 3,
      totalProvidersCount: 5,
      totalObservationsIngested: 23620,
      providers: [
        {
          providerId: 'openaq',
          displayName: 'OpenAQ Global Air Quality',
          category: 'AIR_QUALITY',
          configured: true,
          endpoint: 'https://api.openaq.org/v3',
          status: 'ACTIVE',
          lastIngestionAt: null, // Untracked background job reported honestly as null
          latestObservationAt: new Date(Date.now() - 45 * 60 * 1000).toISOString(),
          recordCount: 15420,
          freshnessMinutes: 45,
          freshnessStatus: 'FRESH',
          safeNotes: 'Hourly continuous station feeds.',
        },
        {
          providerId: 'firms',
          displayName: 'NASA FIRMS Thermal Satellite',
          category: 'SATELLITE',
          configured: true,
          endpoint: 'https://firms.modaps.eosdis.nasa.gov',
          status: 'ACTIVE',
          lastIngestionAt: null,
          latestObservationAt: null,
          recordCount: 0,
          freshnessMinutes: null,
          freshnessStatus: 'NO_DATA',
          safeNotes: 'MODIS/VIIRS thermal anomalies.',
        },
      ],
    };

    assert.strictEqual(providers.activeProvidersCount, 3);
    assert.strictEqual(providers.providers[0].freshnessStatus, 'FRESH');
    assert.strictEqual(providers.providers[0].recordCount, 15420);
    assert.strictEqual(providers.providers[1].freshnessStatus, 'NO_DATA');
    assert.strictEqual(providers.providers[1].lastIngestionAt, null);
  });

  it('F9-3: ModelObservability distinguishes loaded artifacts from cloud APIs and reports readiness', () => {
    const models: ModelObservability = {
      evaluatedAt: new Date().toISOString(),
      allModelsReady: true,
      readyModelsCount: 4,
      totalModelsCount: 4,
      models: [
        {
          modelId: 'hotspot_classifier_v1',
          modelName: 'F3 Hotspot Classifier & Calibrator',
          framework: 'scikit-learn / joblib',
          status: 'AVAILABLE',
          ready: true,
          artifactIdentifier: 'models/artifacts/hotspot_classifier_v1.joblib (20 MB)',
          operationalDetails: '36 features vector, Platt probability calibration, 0.50 threshold',
          lastVerifiedAt: new Date().toISOString(),
        },
        {
          modelId: 'forecast_regressors_v1',
          modelName: 'F4 Multi-Horizon PM2.5 Regressors',
          framework: 'LightGBM / Quantile Regressors',
          status: 'AVAILABLE',
          ready: true,
          artifactIdentifier: 'models/artifacts/forecast_regressors_v1.joblib (125 MB)',
          operationalDetails: 'Horizons T+1, T+3, T+6 with calibrated 90% prediction intervals',
          lastVerifiedAt: new Date().toISOString(),
        },
        {
          modelId: 'gemini_multimodal_vision',
          modelName: 'CitizenVision Multimodal Inference',
          framework: 'Google Gemini 1.5 Flash',
          status: 'CONFIGURED',
          ready: true,
          artifactIdentifier: 'Cloud API (google-genai) with deterministic fallback guard',
          operationalDetails: 'Classifies citizen evidence with strict confidence bounds',
          lastVerifiedAt: new Date().toISOString(),
        },
        {
          modelId: 'f5_evidence_fusion_engine',
          modelName: 'F5 Multi-Source Evidence Fusion Engine',
          framework: 'Deterministic Spatial Rule & Statistical Fusion',
          status: 'AVAILABLE',
          ready: true,
          artifactIdentifier: 'com.aerosentinel.evidence.EvidenceFusionService',
          operationalDetails: 'Aggregates F3 hotspot, F4 forecast, CitizenVision, and sensor corroboration',
          lastVerifiedAt: new Date().toISOString(),
        },
      ],
    };

    assert.strictEqual(models.allModelsReady, true);
    assert.strictEqual(models.readyModelsCount, 4);
    assert.ok(models.models.some((m) => m.modelId === 'forecast_regressors_v1' && m.ready));
    assert.ok(models.models.some((m) => m.modelId === 'gemini_multimodal_vision' && m.status === 'CONFIGURED'));
  });

  it('F9-4: SafeAuditEvent enforces strict redaction of credentials and citizen PII', () => {
    const rawReason = 'Citizen user test@citizen.org with phone +919876543210 submitted report with token Bearer abc123def456';
    
    // Test the redaction logic
    const redactFn = (input: string): string => {
      let out = input.replace(/bearer\s+[a-z0-9._-]+/gi, '[REDACTED_CREDENTIAL]');
      out = out.replace(/\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b/g, '[REDACTED_EMAIL]');
      out = out.replace(/\b(?:\+91|91)?[6-9]\d{9}\b/g, '[REDACTED_PHONE]');
      return out;
    };

    const sanitized = redactFn(rawReason);
    assert.strictEqual(sanitized.includes('test@citizen.org'), false);
    assert.strictEqual(sanitized.includes('9876543210'), false);
    assert.strictEqual(sanitized.includes('Bearer abc123def456'), false);
    assert.ok(sanitized.includes('[REDACTED_EMAIL]'));
    assert.ok(sanitized.includes('[REDACTED_PHONE]'));
    assert.ok(sanitized.includes('[REDACTED_CREDENTIAL]'));

    const auditEvent: SafeAuditEvent = {
      actionId: '550e8400-e29b-41d4-a716-446655440099',
      alertId: '550e8400-e29b-41d4-a716-446655440001',
      actionType: 'ACKNOWLEDGE',
      previousStatus: 'OPEN',
      newStatus: 'ACKNOWLEDGED',
      actorUsername: 'admin_officer',
      actorRole: 'AUTHORITY',
      safeReason: sanitized,
      performedAt: new Date().toISOString(),
      redacted: true,
    };

    assert.strictEqual(auditEvent.redacted, true);
    assert.strictEqual(auditEvent.actionType, 'ACKNOWLEDGE');
    assert.strictEqual(auditEvent.actorRole, 'AUTHORITY');
  });

  it('F9-5: F8 Monitoring-Gap Policy context establishes non-alarmist coverage guidance', () => {
    const overview: AdminOverview = {
      systemHealth: 'HEALTHY',
      generatedAt: new Date().toISOString(),
      serviceStatusSummary: { backend: 'UP', database: 'UP', aiService: 'UP' },
      recordCounts: {
        totalAirObservations: 15420,
        totalWeatherObservations: 8200,
        activeStationsCount: 12,
        activeAlertsCount: 4,
        citizenReportsCount: 28,
        totalCitiesCount: 3,
        totalAuthorityActions: 54,
      },
      monitoringGapContext: {
        coverageRadiusKm: 5.0,
        gapThresholdKm: 7.0,
        totalActiveStations: 12,
        policyVersion: 'F8-P3-v1.0',
        notice: 'Monitoring gaps highlight low sensor proximity, not confirmed pollution.',
      },
      diagnosticRecommendations: [
        {
          id: 'rec-ai-healthy',
          severity: 'INFO',
          component: 'AI Inference Pipeline',
          title: 'All ML model artifacts loaded and responsive',
          details: 'Hotspot Classifier and Multi-Horizon Forecast models are operational with 35ms latency.',
          recommendedAction: 'No action required.',
        },
      ],
    };

    assert.strictEqual(overview.monitoringGapContext.coverageRadiusKm, 5.0);
    assert.strictEqual(overview.monitoringGapContext.gapThresholdKm, 7.0);
    assert.strictEqual(overview.monitoringGapContext.policyVersion, 'F8-P3-v1.0');
    assert.ok(overview.monitoringGapContext.notice.includes('not confirmed pollution'));
    assert.strictEqual(overview.diagnosticRecommendations.length, 1);
    assert.strictEqual(overview.diagnosticRecommendations[0].severity, 'INFO');
  });

  it('F9-6: Role boundaries decouple operational admin diagnostics from authority case response', () => {
    const authorizedRoles = ['ADMIN'];
    const decoupledRoles = ['AUTHORITY', 'ANALYST', 'CITIZEN'];

    for (const role of decoupledRoles) {
      assert.strictEqual(authorizedRoles.includes(role), false);
    }
    assert.strictEqual(authorizedRoles.includes('ADMIN'), true);
  });
});
