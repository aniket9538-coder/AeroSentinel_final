import React, { useState, useEffect } from 'react';
import { PageContainer } from '../../components/layout/PageContainer';
import { IncidentQueue } from '../../components/authority/IncidentQueue';
import { ActionPanel } from '../../components/authority/ActionPanel';
import { InspectionForm } from '../../components/authority/InspectionForm';
import { ResolutionPanel } from '../../components/authority/ResolutionPanel';
import { AlertDetails } from '../../components/alerts/AlertDetails';
import { Alert } from '../../types';
import alertApi from '../../services/alertApi';
import { authService } from '../../services/auth.service';
import { ShieldCheck, ShieldAlert } from 'lucide-react';

export const AuthorityDashboard: React.FC = () => {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [selectedAlert, setSelectedAlert] = useState<Alert | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isUnauthorized, setIsUnauthorized] = useState(false);
  const [authenticating, setAuthenticating] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);

  const fetchAlerts = async () => {
    setLoading(true);
    setError(null);
    setIsUnauthorized(false);
    try {
      const items = await alertApi.getAuthorityQueue();
      const mapped: Alert[] = items.map((item) => ({
        id: item.alertId,
        eventId: item.eventId,
        h3Index: item.h3Index,
        severity: item.severity === 'CRITICAL' ? 'CRITICAL' : item.severity === 'LOW' ? 'INFO' : 'WARNING',
        title: item.title,
        message: item.message,
        status: item.status,
        createdAt: item.createdAt,
        acknowledgedAt: item.acknowledgedAt || undefined,
      }));
      setAlerts(mapped);
      if (mapped.length > 0) {
        setSelectedAlert(mapped[0]);
      } else {
        setSelectedAlert(null);
      }
    } catch (err: any) {
      const isAuth =
        err?.response?.status === 401 ||
        err?.response?.data?.status === 401 ||
        (err?.response?.data?.message || '').toLowerCase().includes('authentication required');
      setIsUnauthorized(isAuth);
      setError(err?.response?.data?.message || err?.message || 'Failed to load authority incidents');
      setAlerts([]);
      setSelectedAlert(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAlerts();
  }, []);

  const handleLogAction = (data: any) => {
    setActionLoading(true);
    setTimeout(() => {
      alert(`Mitigation action recorded: ${data.actionType}`);
      setActionLoading(false);
    }, 800);
  };

  const handleScheduleInspection = (data: any) => {
    setActionLoading(true);
    setTimeout(() => {
      alert(`Inspection team dispatched: ${data.assignedTeam}`);
      setActionLoading(false);
    }, 800);
  };

  const handleResolveAlert = async (alertId: string) => {
    setActionLoading(true);
    try {
      await alertApi.resolveAlert(alertId);
      setAlerts((prev) => prev.filter((a) => a.id !== alertId));
      setSelectedAlert((prev) => (prev?.id === alertId ? null : prev));
      alert('Incident officially resolved and closed.');
    } catch {
      alert('Failed to resolve alert.');
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <PageContainer
      title="Municipal Environmental Authority Hub"
      subtitle="Operational incident response, team dispatch, and mitigation tracking"
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#9ca3af' }}>
          Loading authority incidents...
        </div>
      ) : isUnauthorized ? (
        <div
          style={{
            padding: '3rem 2rem',
            textAlign: 'center',
            background: 'var(--bg-surface-elevated, #1f2937)',
            borderRadius: '12px',
            border: '1px solid var(--border-medium, #374151)',
            maxWidth: '560px',
            margin: '2rem auto',
          }}
        >
          <div
            style={{
              width: '52px',
              height: '52px',
              borderRadius: '50%',
              background: 'rgba(2, 132, 199, 0.15)',
              border: '1px solid rgba(2, 132, 199, 0.4)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 1rem',
              color: '#38bdf8',
            }}
          >
            <ShieldAlert size={26} />
          </div>
          <h3 style={{ fontSize: '1.15rem', fontWeight: 700, color: '#f3f4f6', marginBottom: '0.4rem' }}>
            Authority Authentication Required
          </h3>
          <p style={{ fontSize: '0.85rem', color: '#9ca3af', lineHeight: 1.5, marginBottom: '1.5rem' }}>
            Viewing and managing active municipal incident response candidates requires an authorized officer session (Safety Invariant 12).
          </p>
          <button
            onClick={async () => {
              setAuthenticating(true);
              try {
                await authService.loginAsAuthority();
                await fetchAlerts();
              } finally {
                setAuthenticating(false);
              }
            }}
            disabled={authenticating}
            style={{
              padding: '0.65rem 1.4rem',
              borderRadius: '8px',
              background: '#0284c7',
              color: '#ffffff',
              border: 'none',
              fontSize: '0.9rem',
              fontWeight: 600,
              cursor: authenticating ? 'wait' : 'pointer',
              boxShadow: '0 2px 4px rgba(2, 132, 199, 0.25)',
            }}
          >
            {authenticating ? 'Authenticating...' : 'Authenticate as Municipal Authority Officer'}
          </button>
        </div>
      ) : alerts.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#9ca3af', background: '#111827', borderRadius: '12px' }}>
          No active authority alert candidates requiring intervention.
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '1.5rem' }}>
          <div>
            <IncidentQueue
              alerts={alerts}
              selectedAlertId={selectedAlert?.id}
              onSelectAlert={(a) => setSelectedAlert(a)}
            />
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
            {selectedAlert && <AlertDetails alert={selectedAlert} />}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
              <InspectionForm
                alertId={selectedAlert?.id}
                onScheduleInspection={handleScheduleInspection}
                isLoading={actionLoading}
              />
              <ActionPanel
                alertId={selectedAlert?.id}
                onLogAction={handleLogAction}
                isLoading={actionLoading}
              />
            </div>
            <ResolutionPanel
              alertId={selectedAlert?.id}
              onResolve={handleResolveAlert}
              isLoading={actionLoading}
            />
          </div>
        </div>
      )}
    </PageContainer>
  );
};

export default AuthorityDashboard;
