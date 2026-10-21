import React, { useState, useEffect } from 'react';
import { PageContainer } from '../../components/layout/PageContainer';
import { Card } from '../../components/common/Card';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Loading } from '../../components/common/Loading';
import { ErrorState } from '../../components/common/ErrorState';
import {
  fetchAdminOverview,
  fetchServiceHealth,
  fetchProviderObservability,
  fetchModelObservability,
  fetchSafeAuditEvents,
} from '../../services/adminApi';
import {
  AdminOverview,
  ServiceHealth,
  ProviderObservability,
  ModelObservability,
  SafeAuditEvent,
} from '../../types/admin';
import {
  Activity,
  Server,
  Database,
  Cpu,
  RefreshCw,
  CheckCircle2,
  AlertTriangle,
  AlertCircle,
  ShieldAlert,
  Radio,
  Clock,
  Layers,
  Search,
  ExternalLink,
  ShieldCheck,
  Zap,
  Lock,
  LogIn,
  LogOut,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { authService } from '../../services/auth.service';

export const AdminDashboard: React.FC = () => {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<'overview' | 'services' | 'providers' | 'models' | 'audit'>('overview');

  const [overview, setOverview] = useState<AdminOverview | null>(null);
  const [health, setHealth] = useState<ServiceHealth | null>(null);
  const [providers, setProviders] = useState<ProviderObservability | null>(null);
  const [models, setModels] = useState<ModelObservability | null>(null);
  const [auditEvents, setAuditEvents] = useState<SafeAuditEvent[]>([]);

  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [auditSearch, setAuditSearch] = useState('');

  // Admin Authentication State
  const [adminEmail, setAdminEmail] = useState('admin@aerosentinel.gov');
  const [adminPassword, setAdminPassword] = useState('password');
  const [isLoggingIn, setIsLoggingIn] = useState(false);
  const [loginError, setLoginError] = useState<string | null>(null);

  const loadAllData = async () => {
    try {
      setError(null);
      const [ovData, healthData, provData, modelData, auditData] = await Promise.all([
        fetchAdminOverview(),
        fetchServiceHealth(),
        fetchProviderObservability(),
        fetchModelObservability(),
        fetchSafeAuditEvents(),
      ]);
      setOverview(ovData);
      setHealth(healthData);
      setProviders(provData);
      setModels(modelData);
      setAuditEvents(auditData);
    } catch (err: any) {
      console.error('Failed to load admin observability data:', err);
      const status = err.response?.status;
      if (status === 401 || status === 403) {
        setError('Administrator authentication required. Operational diagnostics and HikariCP connection pool metrics are restricted to privileged administrators.');
      } else {
        const msg = err.response?.data?.message || err.message || 'Failed to authenticate or fetch operational observability telemetry.';
        setError(msg);
      }
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  const handleAdminLogin = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    setIsLoggingIn(true);
    setLoginError(null);
    try {
      await authService.login({ email: adminEmail, password: adminPassword });
      setLoading(true);
      await loadAllData();
    } catch (err: any) {
      setLoginError(err.response?.data?.message || err.message || 'Invalid administrator credentials');
    } finally {
      setIsLoggingIn(false);
    }
  };

  const handleAdminLogout = () => {
    authService.logout();
    setOverview(null);
    setHealth(null);
    setProviders(null);
    setModels(null);
    setAuditEvents([]);
    setError('Session terminated. Please re-authenticate as an administrator.');
  };

  useEffect(() => {
    loadAllData();
  }, []);

  const handleRefresh = () => {
    setRefreshing(true);
    loadAllData();
  };

  const filteredAuditEvents = auditEvents.filter((e) => {
    if (!auditSearch) return true;
    const term = auditSearch.toLowerCase();
    return (
      e.actionType.toLowerCase().includes(term) ||
      e.actorUsername.toLowerCase().includes(term) ||
      (e.safeReason && e.safeReason.toLowerCase().includes(term)) ||
      (e.previousStatus && e.previousStatus.toLowerCase().includes(term)) ||
      (e.newStatus && e.newStatus.toLowerCase().includes(term))
    );
  });

  if (loading) {
    return (
      <PageContainer title="ADMIN OBSERVABILITY" subtitle="Authenticating and retrieving system diagnostics...">
        <div style={{ padding: '4rem 0' }}>
          <Loading message="Loading live operational metrics..." subMessage="Authenticating administrative session and querying service health" />
        </div>
      </PageContainer>
    );
  }

  if (error && !overview) {
    return (
      <PageContainer title="ADMIN OBSERVABILITY & MONITORING" subtitle="Administrative operational diagnostics">
        <div style={{ maxWidth: '640px', margin: '2rem auto' }}>
          <Card
            title={
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#dc2626' }}>
                <Lock size={20} />
                <span>Administrator Authentication Required</span>
              </div>
            }
          >
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem', padding: '0.5rem 0' }}>
              <div style={{
                padding: '0.85rem 1rem',
                borderRadius: '0.5rem',
                background: '#fef2f2',
                border: '1px solid #fee2e2',
                color: '#991b1b',
                fontSize: '0.875rem',
                lineHeight: 1.5,
              }}>
                <div style={{ fontWeight: 600, marginBottom: '0.25rem' }}>Restricted Operational Endpoint (Phase 9 Policy)</div>
                {error}
              </div>

              {loginError && (
                <div style={{
                  padding: '0.75rem 1rem',
                  borderRadius: '0.5rem',
                  background: '#fee2e2',
                  border: '1px solid #f87171',
                  color: '#b91c1c',
                  fontSize: '0.85rem',
                }}>
                  {loginError}
                </div>
              )}

              <form onSubmit={handleAdminLogin} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '0.35rem' }}>
                    Administrator Email
                  </label>
                  <input
                    type="email"
                    value={adminEmail}
                    onChange={(e) => setAdminEmail(e.target.value)}
                    required
                    style={{
                      width: '100%',
                      padding: '0.6rem 0.8rem',
                      borderRadius: '0.375rem',
                      border: '1px solid #cbd5e1',
                      fontSize: '0.9rem',
                      outline: 'none',
                    }}
                  />
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '0.35rem' }}>
                    Password
                  </label>
                  <input
                    type="password"
                    value={adminPassword}
                    onChange={(e) => setAdminPassword(e.target.value)}
                    required
                    style={{
                      width: '100%',
                      padding: '0.6rem 0.8rem',
                      borderRadius: '0.375rem',
                      border: '1px solid #cbd5e1',
                      fontSize: '0.9rem',
                      outline: 'none',
                    }}
                  />
                </div>

                <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.5rem' }}>
                  <Button
                    type="submit"
                    variant="primary"
                    isLoading={isLoggingIn}
                    style={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '0.5rem' }}
                  >
                    <LogIn size={16} />
                    {isLoggingIn ? 'Authenticating...' : 'Sign In as Administrator'}
                  </Button>
                </div>
              </form>

              <div style={{
                borderTop: '1px solid #e2e8f0',
                paddingTop: '1rem',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                fontSize: '0.8rem',
                color: '#64748b'
              }}>
                <span>Default Local Test Admin:</span>
                <button
                  type="button"
                  onClick={() => {
                    setAdminEmail('admin@aerosentinel.gov');
                    setAdminPassword('password');
                    handleAdminLogin();
                  }}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: '#2563eb',
                    fontWeight: 600,
                    cursor: 'pointer',
                    textDecoration: 'underline',
                  }}
                >
                  Quick Fill & Login (admin@aerosentinel.gov)
                </button>
              </div>
            </div>
          </Card>
        </div>
      </PageContainer>
    );
  }

  const getSystemStatusBadge = (status?: string) => {
    switch (status) {
      case 'HEALTHY':
        return (
          <Badge variant="success" pulse>
            <CheckCircle2 size={12} style={{ marginRight: '4px' }} />
            SYSTEM HEALTHY
          </Badge>
        );
      case 'DEGRADED':
        return (
          <Badge variant="warning" pulse>
            <AlertTriangle size={12} style={{ marginRight: '4px' }} />
            DEGRADED
          </Badge>
        );
      case 'CRITICAL':
      default:
        return (
          <Badge variant="danger" pulse>
            <AlertCircle size={12} style={{ marginRight: '4px' }} />
            CRITICAL OUTAGE
          </Badge>
        );
    }
  };

  return (
    <PageContainer
      title="ADMIN OBSERVABILITY & MONITORING"
      subtitle="Operational telemetry, ingestion freshness, AI inference readiness, and F8 coverage intelligence"
      action={
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.4rem',
            padding: '0.25rem 0.65rem',
            borderRadius: '0.375rem',
            background: '#ecfdf5',
            border: '1px solid #a7f3d0',
            color: '#065f46',
            fontSize: '0.75rem',
            fontWeight: 600,
          }}>
            <ShieldCheck size={14} />
            <span>ADMIN: {adminEmail}</span>
          </div>
          {getSystemStatusBadge(overview?.systemHealth)}
          <Button
            variant="outline"
            size="sm"
            onClick={handleRefresh}
            isLoading={refreshing}
          >
            <RefreshCw size={14} />
            {refreshing ? 'Probing...' : 'Refresh Telemetry'}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={handleAdminLogout}
            style={{ color: '#ef4444' }}
            title="Log out from administrator session"
          >
            <LogOut size={14} />
            Logout
          </Button>
        </div>
      }
    >
      {/* Navigation Tabs */}
      <div
        style={{
          display: 'flex',
          gap: '0.5rem',
          borderBottom: '1px solid var(--border-medium)',
          paddingBottom: '0.75rem',
          marginBottom: '1.5rem',
          overflowX: 'auto',
        }}
      >
        <button
          onClick={() => setActiveTab('overview')}
          style={{
            padding: '0.5rem 1rem',
            borderRadius: '6px',
            border: 'none',
            fontSize: '0.85rem',
            fontWeight: 600,
            cursor: 'pointer',
            background: activeTab === 'overview' ? 'var(--brand-primary)' : 'transparent',
            color: activeTab === 'overview' ? '#ffffff' : 'var(--text-secondary)',
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
          }}
        >
          <Activity size={14} />
          Overview & Diagnostics
        </button>

        <button
          onClick={() => setActiveTab('services')}
          style={{
            padding: '0.5rem 1rem',
            borderRadius: '6px',
            border: 'none',
            fontSize: '0.85rem',
            fontWeight: 600,
            cursor: 'pointer',
            background: activeTab === 'services' ? 'var(--brand-primary)' : 'transparent',
            color: activeTab === 'services' ? '#ffffff' : 'var(--text-secondary)',
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
          }}
        >
          <Server size={14} />
          Core Services & Pool
        </button>

        <button
          onClick={() => setActiveTab('providers')}
          style={{
            padding: '0.5rem 1rem',
            borderRadius: '6px',
            border: 'none',
            fontSize: '0.85rem',
            fontWeight: 600,
            cursor: 'pointer',
            background: activeTab === 'providers' ? 'var(--brand-primary)' : 'transparent',
            color: activeTab === 'providers' ? '#ffffff' : 'var(--text-secondary)',
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
          }}
        >
          <Radio size={14} />
          Ingestion & Freshness ({providers?.activeProvidersCount ?? 0}/{providers?.totalProvidersCount ?? 0})
        </button>

        <button
          onClick={() => setActiveTab('models')}
          style={{
            padding: '0.5rem 1rem',
            borderRadius: '6px',
            border: 'none',
            fontSize: '0.85rem',
            fontWeight: 600,
            cursor: 'pointer',
            background: activeTab === 'models' ? 'var(--brand-primary)' : 'transparent',
            color: activeTab === 'models' ? '#ffffff' : 'var(--text-secondary)',
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
          }}
        >
          <Cpu size={14} />
          AI Models & Inference ({models?.readyModelsCount ?? 0}/{models?.totalModelsCount ?? 0})
        </button>

        <button
          onClick={() => setActiveTab('audit')}
          style={{
            padding: '0.5rem 1rem',
            borderRadius: '6px',
            border: 'none',
            fontSize: '0.85rem',
            fontWeight: 600,
            cursor: 'pointer',
            background: activeTab === 'audit' ? 'var(--brand-primary)' : 'transparent',
            color: activeTab === 'audit' ? '#ffffff' : 'var(--text-secondary)',
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
          }}
        >
          <ShieldCheck size={14} />
          Safe Operational Audit ({auditEvents.length})
        </button>
      </div>

      {/* TAB 1: OVERVIEW & DIAGNOSTICS */}
      {activeTab === 'overview' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          {/* Diagnostic Guidance Banner */}
          {overview?.diagnosticRecommendations && overview.diagnosticRecommendations.length > 0 && (
            <Card title="Operational Recommendations — What to Investigate Next">
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginTop: '0.5rem' }}>
                {overview.diagnosticRecommendations.map((rec) => (
                  <div
                    key={rec.id}
                    style={{
                      padding: '0.75rem 1rem',
                      borderRadius: '8px',
                      background:
                        rec.severity === 'CRITICAL'
                          ? 'rgba(239, 68, 68, 0.1)'
                          : rec.severity === 'WARNING'
                          ? 'rgba(245, 158, 11, 0.1)'
                          : 'rgba(56, 189, 248, 0.1)',
                      border: `1px solid ${
                        rec.severity === 'CRITICAL'
                          ? 'rgba(239, 68, 68, 0.3)'
                          : rec.severity === 'WARNING'
                          ? 'rgba(245, 158, 11, 0.3)'
                          : 'rgba(56, 189, 248, 0.3)'
                      }`,
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '0.25rem',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        <Badge
                          variant={
                            rec.severity === 'CRITICAL' ? 'danger' : rec.severity === 'WARNING' ? 'warning' : 'info'
                          }
                        >
                          {rec.severity}
                        </Badge>
                        <span style={{ fontWeight: 600, fontSize: '0.9rem' }}>{rec.title}</span>
                      </div>
                      <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{rec.component}</span>
                    </div>
                    <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginTop: '2px' }}>
                      {rec.details}
                    </div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--brand-primary)', fontWeight: 500, marginTop: '2px' }}>
                      Action: {rec.recommendedAction}
                    </div>
                  </div>
                ))}
              </div>
            </Card>
          )}

          {/* Quick Metrics Grid */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '1rem' }}>
            <Card title="Air Observations">
              <div style={{ fontSize: '2rem', fontWeight: 700, color: 'var(--brand-primary)' }}>
                {overview?.recordCounts.totalAirObservations.toLocaleString() ?? '0'}
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                Validated CPCB & OpenAQ telemetry
              </div>
            </Card>

            <Card title="Weather Records">
              <div style={{ fontSize: '2rem', fontWeight: 700, color: '#10b981' }}>
                {overview?.recordCounts.totalWeatherObservations.toLocaleString() ?? '0'}
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                Boundary meteorology data
              </div>
            </Card>

            <Card title="Active CAAQMS Stations">
              <div style={{ fontSize: '2rem', fontWeight: 700, color: '#f59e0b' }}>
                {overview?.recordCounts.activeStationsCount.toLocaleString() ?? '0'}
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                Across {overview?.recordCounts.totalCitiesCount ?? 3} pilot municipalities
              </div>
            </Card>

            <Card title="Active Incident Alerts">
              <div style={{ fontSize: '2rem', fontWeight: 700, color: '#ec4899' }}>
                {overview?.recordCounts.activeAlertsCount.toLocaleString() ?? '0'}
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                Authority lifecycle cases (F7)
              </div>
            </Card>
          </div>

          {/* F8 Monitoring-Gap Intelligence Context */}
          <Card title="F8 Environmental Monitoring-Gap Intelligence">
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                  Evaluates under-monitored geographic regions using Uber H3 Resolution 8 and Haversine station distance.
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => navigate('/monitoring')}
                >
                  <ExternalLink size={14} style={{ marginRight: '4px' }} />
                  Open Monitoring Dashboard
                </Button>
              </div>

              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(3, 1fr)',
                  gap: '1rem',
                  padding: '1rem',
                  borderRadius: '8px',
                  background: 'var(--bg-card-subtle)',
                }}
              >
                <div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Coverage Radius (Radius_cov)</div>
                  <div style={{ fontSize: '1.25rem', fontWeight: 600 }}>
                    {overview?.monitoringGapContext.coverageRadiusKm ?? 5.0} km
                  </div>
                </div>
                <div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Gap Threshold (D_threshold)</div>
                  <div style={{ fontSize: '1.25rem', fontWeight: 600, color: '#f59e0b' }}>
                    {overview?.monitoringGapContext.gapThresholdKm ?? 7.0} km
                  </div>
                </div>
                <div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Algorithm Policy Version</div>
                  <div style={{ fontSize: '1.25rem', fontWeight: 600, fontFamily: 'monospace' }}>
                    {overview?.monitoringGapContext.policyVersion ?? 'F8-P3-v1.0'}
                  </div>
                </div>
              </div>

              <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontStyle: 'italic' }}>
                Notice: {overview?.monitoringGapContext.notice}
              </div>
            </div>
          </Card>
        </div>
      )}

      {/* TAB 2: CORE SERVICES & HIKARICP POOL */}
      {activeTab === 'services' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '1.5rem' }}>
          <Card title="Spring Boot Orchestrator (Backend)">
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontWeight: 600 }}>Liveness Status:</span>
                <Badge variant={health?.backend.status === 'UP' ? 'success' : 'danger'}>
                  {health?.backend.status ?? 'UNKNOWN'}
                </Badge>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>JVM Runtime:</span>
                <span style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                  Java {health?.backend.javaVersion ?? '21'}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Active Thread Count:</span>
                <span style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                  {health?.backend.activeThreads ?? 0} threads
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Memory Utilization:</span>
                <span style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                  {health?.backend.totalMemoryMb} MB total / {health?.backend.freeMemoryMb} MB free
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Uptime:</span>
                <span style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                  {health ? `${Math.floor(health.uptimeSeconds / 60)} minutes` : 'Unknown'}
                </span>
              </div>
            </div>
          </Card>

          <Card title="PostgreSQL + PostGIS Spatial Engine">
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontWeight: 600 }}>Connection Validity:</span>
                <Badge variant={health?.database.connectionValid ? 'success' : 'danger'}>
                  {health?.database.connectionValid ? 'CONNECTED' : 'DISCONNECTED'}
                </Badge>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Engine Specification:</span>
                <span style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                  {health?.database.databaseProduct}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>HikariCP Active Connections:</span>
                <span style={{ fontFamily: 'monospace', fontSize: '0.85rem', fontWeight: 600 }}>
                  {health?.database.activeConnections} active / {health?.database.idleConnections} idle ({health?.database.totalConnections} total)
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Threads Awaiting Connection:</span>
                <span style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                  {health?.database.threadsAwaitingConnection ?? 0}
                </span>
              </div>
            </div>
          </Card>

          <Card title="Python FastAPI AI Inference Engine" style={{ gridColumn: 'span 2' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <span style={{ fontWeight: 600 }}>AI Service Status:</span>
                  <Badge variant={health?.aiService.status === 'UP' ? 'success' : 'danger'}>
                    {health?.aiService.status ?? 'DOWN'}
                  </Badge>
                  {health?.aiService.modelsReady && (
                    <Badge variant="info">MODELS READY</Badge>
                  )}
                </div>
                <span style={{ fontFamily: 'monospace', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                  Latency: {health?.aiService.latencyMs ?? 0} ms
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Endpoint URL:</span>
                <span style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                  {health?.aiService.endpoint}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Loaded Serialized Artifacts:</span>
                <span style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                  {health?.aiService.loadedArtifacts.join(', ') || 'None'}
                </span>
              </div>
              {health?.aiService.errorMessage && (
                <div style={{ fontSize: '0.8rem', color: '#ef4444', marginTop: '0.25rem' }}>
                  Error: {health.aiService.errorMessage}
                </div>
              )}
            </div>
          </Card>
        </div>
      )}

      {/* TAB 3: INGESTION & DATA PROVIDERS */}
      {activeTab === 'providers' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
              Data-provider freshness, last valid observation timestamps, and record counters.
            </span>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              Total Observations: <strong>{providers?.totalObservationsIngested.toLocaleString()}</strong>
            </span>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '1rem' }}>
            {providers?.providers.map((p) => (
              <Card key={p.providerId} title={p.displayName}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                      <Badge variant={p.status === 'ACTIVE' ? 'success' : 'neutral'}>
                        {p.status}
                      </Badge>
                      <Badge
                        variant={
                          p.freshnessStatus === 'FRESH'
                            ? 'success'
                            : p.freshnessStatus === 'ACCEPTABLE'
                            ? 'info'
                            : p.freshnessStatus === 'STALE'
                            ? 'warning'
                            : 'neutral'
                        }
                      >
                        {p.freshnessStatus}
                      </Badge>
                    </div>
                    <span style={{ fontSize: '0.75rem', fontFamily: 'monospace', color: 'var(--text-muted)' }}>
                      {p.category}
                    </span>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem' }}>
                    <span style={{ color: 'var(--text-muted)' }}>Endpoint:</span>
                    <span style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{p.endpoint}</span>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem' }}>
                    <span style={{ color: 'var(--text-muted)' }}>Total Records Ingested:</span>
                    <span style={{ fontWeight: 600 }}>{p.recordCount.toLocaleString()}</span>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem' }}>
                    <span style={{ color: 'var(--text-muted)' }}>Latest Observation:</span>
                    <span style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                      {p.latestObservationAt ? new Date(p.latestObservationAt).toLocaleString() : 'Not tracked / No data'}
                    </span>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem' }}>
                    <span style={{ color: 'var(--text-muted)' }}>Data Age:</span>
                    <span style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                      {p.freshnessMinutes != null ? `${p.freshnessMinutes} minutes ago` : 'N/A'}
                    </span>
                  </div>

                  <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginTop: '0.25rem' }}>
                    {p.safeNotes}
                  </div>
                </div>
              </Card>
            ))}
          </div>
        </div>
      )}

      {/* TAB 4: AI MODELS & CAPABILITIES */}
      {activeTab === 'models' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
              Artifact readiness, parameter configurations, and model versions.
            </span>
            <Badge variant={models?.allModelsReady ? 'success' : 'warning'}>
              {models?.readyModelsCount} / {models?.totalModelsCount} MODELS READY
            </Badge>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '1rem' }}>
            {models?.models.map((m) => (
              <Card key={m.modelId} title={m.modelName}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Badge variant={m.ready ? 'success' : 'danger'}>
                      {m.status}
                    </Badge>
                    <span style={{ fontSize: '0.75rem', fontFamily: 'monospace', color: 'var(--text-muted)' }}>
                      {m.framework}
                    </span>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem' }}>
                    <span style={{ color: 'var(--text-muted)' }}>Artifact / Location:</span>
                    <span style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{m.artifactIdentifier}</span>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem' }}>
                    <span style={{ color: 'var(--text-muted)' }}>Operational Parameters:</span>
                    <span style={{ fontSize: '0.8rem', textAlign: 'right', maxWidth: '60%' }}>
                      {m.operationalDetails}
                    </span>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem' }}>
                    <span style={{ color: 'var(--text-muted)' }}>Last Verified:</span>
                    <span style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                      {new Date(m.lastVerifiedAt).toLocaleTimeString()}
                    </span>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        </div>
      )}

      {/* TAB 5: SAFE OPERATIONAL AUDIT */}
      {activeTab === 'audit' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '1rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flex: 1, maxWidth: '400px' }}>
              <Search size={16} color="var(--text-muted)" />
              <input
                type="text"
                placeholder="Search action type, actor, or status..."
                value={auditSearch}
                onChange={(e) => setAuditSearch(e.target.value)}
                style={{
                  width: '100%',
                  padding: '0.45rem 0.75rem',
                  borderRadius: '6px',
                  border: '1px solid var(--border-medium)',
                  background: 'var(--bg-surface)',
                  color: 'var(--text-primary)',
                  fontSize: '0.85rem',
                }}
              />
            </div>
            <Badge variant="info">
              <ShieldCheck size={12} style={{ marginRight: '4px' }} />
              PII & Credential Redaction Active
            </Badge>
          </div>

          <Card>
            {filteredAuditEvents.length === 0 ? (
              <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-muted)' }}>
                No recent operational audit actions found.
              </div>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
                  <thead>
                    <tr style={{ borderBottom: '1px solid var(--border-medium)', textAlign: 'left', color: 'var(--text-muted)' }}>
                      <th style={{ padding: '0.75rem' }}>Timestamp</th>
                      <th style={{ padding: '0.75rem' }}>Action Type</th>
                      <th style={{ padding: '0.75rem' }}>Lifecycle Shift</th>
                      <th style={{ padding: '0.75rem' }}>Actor</th>
                      <th style={{ padding: '0.75rem' }}>Role</th>
                      <th style={{ padding: '0.75rem' }}>Safe Decision Reason</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredAuditEvents.map((evt) => (
                      <tr
                        key={evt.actionId}
                        style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.05)' }}
                      >
                        <td style={{ padding: '0.75rem', fontFamily: 'monospace', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                          {new Date(evt.performedAt).toLocaleString()}
                        </td>
                        <td style={{ padding: '0.75rem', fontWeight: 600 }}>
                          {evt.actionType}
                        </td>
                        <td style={{ padding: '0.75rem' }}>
                          {evt.previousStatus ? (
                            <span style={{ fontSize: '0.8rem' }}>
                              <code>{evt.previousStatus}</code> &rarr; <code>{evt.newStatus}</code>
                            </span>
                          ) : (
                            <span style={{ fontSize: '0.8rem' }}>
                              <code>{evt.newStatus ?? 'N/A'}</code>
                            </span>
                          )}
                        </td>
                        <td style={{ padding: '0.75rem', fontFamily: 'monospace' }}>
                          {evt.actorUsername}
                        </td>
                        <td style={{ padding: '0.75rem' }}>
                          <Badge variant="neutral">{evt.actorRole}</Badge>
                        </td>
                        <td style={{ padding: '0.75rem', color: 'var(--text-secondary)', maxWidth: '350px' }}>
                          {evt.safeReason || 'No reason recorded'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </div>
      )}
    </PageContainer>
  );
};

export default AdminDashboard;
