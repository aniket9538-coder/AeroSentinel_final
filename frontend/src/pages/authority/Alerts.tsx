import React, { useState, useEffect, useMemo } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { PageContainer } from '../../components/layout/PageContainer';
import { useAuthorityQueue } from '../../hooks/useAuthorityQueue';
import { AuthorityQueueItem } from '../../types/alert';
import { InspectionForm } from '../../components/authority/InspectionForm';
import { AuthorityAlertMap } from '../../components/authority/AuthorityAlertMap';
import { EvidenceFusionTriageCard } from '../../components/authority/EvidenceFusionTriageCard';
import { eventService } from '../../services/event.service';
import { PollutionEventContext } from '../../types/event';
import {
  AlertTriangle,
  CheckCircle,
  Clock,
  ExternalLink,
  RefreshCw,
  ShieldAlert,
  Sparkles,
  TrendingUp,
  Users,
  Copy,
  Check,
  ArrowRight,
  Activity,
  AlertCircle,
  ShieldCheck,
  CheckCircle2,
  Camera,
  Info,
  X,
  Send,
  Ban,
  ListChecks,
  MessageSquare,
  HelpCircle,
  CheckCheck,
  ChevronRight,
  FileText,
  XCircle,
  ClipboardList,
  PlusCircle,
  Play,
  History,
} from 'lucide-react';
import { actionApi, AuthorityActionItem } from '../../services/actionApi';
import { inspectionApi } from '../../services/inspectionApi';
import { FieldTeam } from '../../types/inspection';
import { authService } from '../../services/auth.service';

export const Alerts: React.FC = () => {
  const [searchParams] = useSearchParams();
  const urlH3 = searchParams.get('h3');

  const [statusFilter, setStatusFilter] = useState<'ALL' | 'OPEN' | 'ACKNOWLEDGED' | 'ASSIGNED' | 'IN_INSPECTION' | 'ACTION_TAKEN' | 'RESOLVED' | 'DISMISSED'>('ALL');
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  // Fetch full queue without backend status filter so client computes accurate live counts across all statuses
  const {
    items,
    loading,
    error,
    isUnauthorized,
    selectedAlertId,
    setSelectedAlertId,
    refresh,
    acknowledge,
    resolve,
    dismiss,
  } = useAuthorityQueue();

  const [authenticating, setAuthenticating] = useState<boolean>(false);
  const [customOfficerEmail, setCustomOfficerEmail] = useState<string>('authority@aerosentinel.gov');
  const [customOfficerPassword, setCustomOfficerPassword] = useState<string>('password');
  const [showCustomLogin, setShowCustomLogin] = useState<boolean>(false);

  const currentUserRole = useMemo(() => authService.getRole(), [authenticating]);
  const isAnalystReadOnly = currentUserRole === 'ANALYST';

  const [actionInProgress, setActionInProgress] = useState<boolean>(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [showInspectionModal, setShowInspectionModal] = useState<boolean>(false);

  // F7-P6 / F7-P8 Authority Action & Modal state
  const [actions, setActions] = useState<AuthorityActionItem[]>([]);
  const [loadingActions, setLoadingActions] = useState<boolean>(false);
  const [showActionModal, setShowActionModal] = useState<boolean>(false);
  const [selectedActionType, setSelectedActionType] = useState<string>('FIELD_VERIFICATION');
  const [actionNotes, setActionNotes] = useState<string>('');
  const [showResolveModal, setShowResolveModal] = useState<boolean>(false);
  const [resolutionNotes, setResolutionNotes] = useState<string>('');
  const [showDismissModal, setShowDismissModal] = useState<boolean>(false);
  const [dismissalReason, setDismissalReason] = useState<string>('');

  // Reassignment Modal state
  const [showReassignModal, setShowReassignModal] = useState<boolean>(false);
  const [reassignTeamId, setReassignTeamId] = useState<string>('');
  const [reassignReason, setReassignReason] = useState<string>('');
  const [reassignNotes, setReassignNotes] = useState<string>('');
  const [fieldTeams, setFieldTeams] = useState<FieldTeam[]>([]);
  const [loadingTeams, setLoadingTeams] = useState<boolean>(false);

  // Live dynamic counts computed from loaded items
  const counts = useMemo(
    () => ({
      ALL: items.length,
      OPEN: items.filter((i) => i.status === 'OPEN').length,
      ACKNOWLEDGED: items.filter((i) => i.status === 'ACKNOWLEDGED').length,
      ASSIGNED: items.filter((i) => i.status === 'ASSIGNED').length,
      IN_INSPECTION: items.filter((i) => i.status === 'IN_INSPECTION').length,
      ACTION_TAKEN: items.filter((i) => i.status === 'ACTION_TAKEN').length,
      RESOLVED: items.filter((i) => i.status === 'RESOLVED').length,
      DISMISSED: items.filter((i) => i.status === 'DISMISSED').length,
    }),
    [items]
  );

  // Filtered list based on current active tab
  const filteredItems = useMemo(() => {
    if (statusFilter === 'ALL') return items;
    return items.filter((item) => item.status === statusFilter);
  }, [items, statusFilter]);

  // Synchronize URL ?h3=... parameter to automatically select matching alert
  useEffect(() => {
    if (urlH3 && items.length > 0) {
      const matched = items.find((i) => i.h3Index.toLowerCase() === urlH3.trim().toLowerCase());
      if (matched) {
        setSelectedAlertId(matched.alertId);
        // Switch filter if matched alert belongs to another status so it is visible in the list
        if (statusFilter !== 'ALL' && matched.status !== statusFilter) {
          setStatusFilter('ALL');
        }
      }
    } else if (!selectedAlertId && items.length > 0) {
      setSelectedAlertId(items[0].alertId);
    }
  }, [urlH3, items]);

  // Ensure active selection remains valid when filtering
  useEffect(() => {
    if (filteredItems.length > 0) {
      const isSelectedInFilter = filteredItems.some((i) => i.alertId === selectedAlertId);
      if (!isSelectedInFilter) {
        setSelectedAlertId(filteredItems[0].alertId);
      }
    }
  }, [filteredItems, selectedAlertId]);

  // Resolved active alert
  const selectedAlert = useMemo(() => {
    if (!selectedAlertId) return filteredItems[0] || null;
    return items.find((item) => item.alertId === selectedAlertId) || filteredItems[0] || null;
  }, [items, filteredItems, selectedAlertId]);

  // F7-P5 Connected Event Context state
  const [eventContext, setEventContext] = useState<PollutionEventContext | null>(null);
  const [eventLoading, setEventLoading] = useState<boolean>(false);
  const [eventError, setEventError] = useState<string | null>(null);
  const [imgErrorMap, setImgErrorMap] = useState<Record<string, boolean>>({});

  // Fetch full connected Event Context whenever active alert changes
  useEffect(() => {
    if (!selectedAlert?.eventId) {
      setEventContext(null);
      setEventLoading(false);
      setEventError(null);
      return;
    }
    let isMounted = true;
    setEventLoading(true);
    setEventError(null);

    eventService
      .getEventDetails(selectedAlert.eventId)
      .then((ctx) => {
        if (isMounted) setEventContext(ctx);
      })
      .catch((err: any) => {
        if (isMounted) {
          setEventError(err?.message || 'Event context unavailable');
          setEventContext(null);
        }
      })
      .finally(() => {
        if (isMounted) setEventLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [selectedAlert?.eventId]);

  // Authoritative F4 Forecast Horizons (+1h, +3h, +6h)
  const forecastHorizons = useMemo(() => {
    if (eventContext?.forecast?.horizons && eventContext.forecast.horizons.length > 0) {
      const h1 = eventContext.forecast.horizons.find((h) => h.horizonHours === 1);
      const h3 = eventContext.forecast.horizons.find((h) => h.horizonHours === 3);
      const h6 = eventContext.forecast.horizons.find((h) => h.horizonHours === 6);
      return {
        h1: h1 ? `${h1.predictedPm25.toFixed(1)} ${h1.unit || 'µg/m³'}` : null,
        h3: h3 ? `${h3.predictedPm25.toFixed(1)} ${h3.unit || 'µg/m³'}` : null,
        h6: h6 ? `${h6.predictedPm25.toFixed(1)} ${h6.unit || 'µg/m³'}` : null,
        raw: null,
      };
    }
    if (selectedAlert?.forecastSummary) {
      const summary = selectedAlert.forecastSummary;
      const m1 = summary.match(/\+1h:\s*([^|]+)/i);
      const m3 = summary.match(/\+3h:\s*([^|]+)/i);
      const m6 = summary.match(/\+6h:\s*([^|]+)/i);
      if (m1 || m3 || m6) {
        return {
          h1: m1 ? m1[1].trim() : null,
          h3: m3 ? m3[1].trim() : null,
          h6: m6 ? m6[1].trim() : null,
          raw: summary,
        };
      }
      return { h1: null, h3: null, h6: null, raw: summary };
    }
    return null;
  }, [eventContext?.forecast, selectedAlert?.forecastSummary]);

  const handleCopy = (key: string, value: string) => {
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      navigator.clipboard.writeText(value);
      setCopiedKey(key);
      setTimeout(() => setCopiedKey(null), 2000);
    }
  };

  // Fetch recorded Authority Actions whenever active alert changes
  useEffect(() => {
    if (!selectedAlert?.alertId) {
      setActions([]);
      return;
    }
    let isMounted = true;
    setLoadingActions(true);
    actionApi
      .getActions(selectedAlert.alertId)
      .then((acts) => {
        if (isMounted) setActions(acts);
      })
      .catch(() => {
        if (isMounted) setActions([]);
      })
      .finally(() => {
        if (isMounted) setLoadingActions(false);
      });
    return () => {
      isMounted = false;
    };
  }, [selectedAlert?.alertId]);

  // Authoritative Event Status synchronized with backend
  const currentEventStatus = useMemo(() => {
    if (eventContext?.status) return eventContext.status;
    if (eventContext?.event?.status) return eventContext.event.status;
    if (selectedAlert?.status === 'RESOLVED') return 'RESOLVED';
    if (selectedAlert?.status === 'DISMISSED') return 'DISMISSED';
    if (actions && actions.length > 0) return 'ACTION_TAKEN';
    if (selectedAlert?.verificationResult || selectedAlert?.inspectedAt) return 'IN_INSPECTION';
    if (selectedAlert?.assignedTeamName) return 'ASSIGNED';
    return 'OPEN';
  }, [eventContext, selectedAlert, actions]);

  const refreshAllData = async () => {
    await refresh();
    if (selectedAlert?.eventId) {
      try {
        const ctx = await eventService.getEventDetails(selectedAlert.eventId);
        setEventContext(ctx);
      } catch (_) {}
    }
    if (selectedAlert?.alertId) {
      try {
        const acts = await actionApi.getActions(selectedAlert.alertId);
        setActions(acts);
      } catch (_) {}
    }
  };

  const handleAcknowledge = async (alertId: string) => {
    setActionInProgress(true);
    setActionError(null);
    try {
      await acknowledge(alertId);
      await refreshAllData();
    } catch (err: any) {
      setActionError(err?.response?.data?.message || err?.message || 'Failed to acknowledge alert');
    } finally {
      setActionInProgress(false);
    }
  };

  const handleResolveConfirm = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedAlert?.alertId) return;
    if (!resolutionNotes.trim()) {
      setActionError('Resolution notes are mandatory to resolve an alert.');
      return;
    }
    setActionInProgress(true);
    setActionError(null);
    try {
      await resolve(selectedAlert.alertId, resolutionNotes.trim(), 'Municipal Authority Officer');
      setShowResolveModal(false);
      setResolutionNotes('');
      await refreshAllData();
    } catch (err: any) {
      setActionError(err?.response?.data?.message || err?.message || 'Failed to resolve alert');
    } finally {
      setActionInProgress(false);
    }
  };

  const handleDismissConfirm = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedAlert?.alertId) return;
    if (!dismissalReason.trim()) {
      setActionError('Dismissal reason is required to dismiss an alert.');
      return;
    }
    setActionInProgress(true);
    setActionError(null);
    try {
      await dismiss(selectedAlert.alertId, dismissalReason.trim(), 'Municipal Authority Officer');
      setShowDismissModal(false);
      setDismissalReason('');
      await refreshAllData();
    } catch (err: any) {
      setActionError(err?.response?.data?.message || err?.message || 'Failed to dismiss alert');
    } finally {
      setActionInProgress(false);
    }
  };

  const handleRecordAction = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedAlert?.alertId) return;
    if (!actionNotes.trim()) {
      setActionError('Action notes cannot be empty.');
      return;
    }
    setActionInProgress(true);
    setActionError(null);
    try {
      await actionApi.recordAction({
        alertId: selectedAlert.alertId,
        actionType: selectedActionType,
        notes: actionNotes.trim(),
        performedBy: 'Municipal Authority Officer',
      });
      setShowActionModal(false);
      setActionNotes('');
      await refreshAllData();
    } catch (err: any) {
      setActionError(err?.response?.data?.message || err?.message || 'Failed to record authority action');
    } finally {
      setActionInProgress(false);
    }
  };

  const handleStartInspectionDirect = async () => {
    if (!selectedAlert?.activeAssignmentId) {
      setShowInspectionModal(true);
      return;
    }
    setActionInProgress(true);
    setActionError(null);
    try {
      await inspectionApi.startInspection(selectedAlert.activeAssignmentId);
      await refreshAllData();
    } catch (err: any) {
      setActionError(err?.response?.data?.message || err?.message || 'Failed to start inspection');
    } finally {
      setActionInProgress(false);
    }
  };

  const openReassignModal = async () => {
    setShowReassignModal(true);
    setLoadingTeams(true);
    try {
      const teams = await inspectionApi.getFieldTeams();
      setFieldTeams(teams);
      if (teams.length > 0) {
        const otherTeam = teams.find((t) => t.id !== selectedAlert?.assignedTeamId);
        if (otherTeam) {
          setReassignTeamId(otherTeam.id);
        } else {
          setReassignTeamId(teams[0].id);
        }
      }
    } catch {
      // ignore
    } finally {
      setLoadingTeams(false);
    }
  };

  const handleReassignConfirm = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedAlert?.alertId) return;
    if (!reassignTeamId) {
      setActionError('Please select a replacement field team.');
      return;
    }
    if (!reassignReason.trim()) {
      setActionError('Reassignment reason is mandatory.');
      return;
    }
    setActionInProgress(true);
    setActionError(null);
    try {
      await inspectionApi.reassignFieldTeam(selectedAlert.alertId, {
        newTeamId: reassignTeamId,
        reason: reassignReason.trim(),
        notes: reassignNotes.trim() || undefined,
      });
      setShowReassignModal(false);
      setReassignReason('');
      setReassignNotes('');
      await refreshAllData();
    } catch (err: any) {
      setActionError(err?.response?.data?.message || err?.message || 'Failed to reassign field team');
    } finally {
      setActionInProgress(false);
    }
  };

  const LIFECYCLE_STEPS = [
    { key: 'OPEN', label: '1. Detected', desc: 'Candidate Alert' },
    { key: 'ASSIGNED', label: '2. Assigned', desc: 'Team Dispatched' },
    { key: 'IN_INSPECTION', label: '3. Inspecting', desc: 'Ground Assessed' },
    { key: 'ACTION_TAKEN', label: '4. Action Taken', desc: 'Mitigation Logged' },
    { key: 'RESOLVED', label: '5. Resolved', desc: 'Case Closed' },
  ] as const;

  const getStepState = (stepIndex: number, currentStatus: string) => {
    const statusOrder: Record<string, number> = {
      OPEN: 0,
      ASSIGNED: 1,
      IN_INSPECTION: 2,
      ACTION_TAKEN: 3,
      RESOLVED: 4,
    };
    const currentIndex = statusOrder[currentStatus] ?? 0;
    if (stepIndex < currentIndex) return 'completed';
    if (stepIndex === currentIndex) return 'current';
    return 'future';
  };

  const getSyncExplanation = (eventStatus: string, alertStatus: string) => {
    if (alertStatus === 'DISMISSED' || eventStatus === 'DISMISSED') {
      return 'Event and alert synchronized as DISMISSED by municipal authority.';
    }
    if (alertStatus === 'RESOLVED' && eventStatus === 'RESOLVED') {
      return 'Operational workflow complete. Event and Alert synchronized as RESOLVED.';
    }
    if (eventStatus === 'ACTION_TAKEN') {
      return 'Authority mitigation recorded. Candidate is ready for final resolution review.';
    }
    if (eventStatus === 'IN_INSPECTION') {
      return 'Active ground inspection underway. Physical observations and mitigation in progress.';
    }
    if (eventStatus === 'ASSIGNED') {
      return 'Field response squad dispatched. On-site inspection pending start.';
    }
    if (alertStatus === 'ACKNOWLEDGED') {
      return 'Alert acknowledged by operator. Physical field team pending assignment.';
    }
    return 'Alert candidate detected. Pending operational acknowledgment or direct assignment.';
  };

  // Semantic Status Badges (Light Theme)
  const renderStatusBadge = (status: string) => {
    switch (status) {
      case 'OPEN':
        return (
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.35rem',
              padding: '0.2rem 0.55rem',
              borderRadius: '9999px',
              fontSize: '0.7rem',
              fontWeight: 700,
              background: '#fef3c7',
              color: '#d97706',
              border: '1px solid #fde68a',
              letterSpacing: '0.03em',
            }}
          >
            <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#d97706' }} />
            OPEN
          </span>
        );
      case 'ACKNOWLEDGED':
        return (
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.35rem',
              padding: '0.2rem 0.55rem',
              borderRadius: '9999px',
              fontSize: '0.7rem',
              fontWeight: 700,
              background: '#e0f2fe',
              color: '#0284c7',
              border: '1px solid #bae6fd',
              letterSpacing: '0.03em',
            }}
          >
            <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#0284c7' }} />
            ACKNOWLEDGED
          </span>
        );
      case 'ASSIGNED':
        return (
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.35rem',
              padding: '0.2rem 0.55rem',
              borderRadius: '9999px',
              fontSize: '0.7rem',
              fontWeight: 700,
              background: '#f3e8ff',
              color: '#7e22ce',
              border: '1px solid #d8b4fe',
              letterSpacing: '0.03em',
            }}
          >
            <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#7e22ce' }} />
            ASSIGNED
          </span>
        );
      case 'IN_INSPECTION':
        return (
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.35rem',
              padding: '0.2rem 0.55rem',
              borderRadius: '9999px',
              fontSize: '0.7rem',
              fontWeight: 700,
              background: '#fff7ed',
              color: '#c2410c',
              border: '1px solid #fed7aa',
              letterSpacing: '0.03em',
            }}
          >
            <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#c2410c' }} />
            IN_INSPECTION
          </span>
        );
      case 'ACTION_TAKEN':
        return (
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.35rem',
              padding: '0.2rem 0.55rem',
              borderRadius: '9999px',
              fontSize: '0.7rem',
              fontWeight: 700,
              background: '#f0fdfa',
              color: '#0f766e',
              border: '1px solid #99f6e4',
              letterSpacing: '0.03em',
            }}
          >
            <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#0f766e' }} />
            ACTION_TAKEN
          </span>
        );
      case 'CLOSED':
        return (
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.35rem',
              padding: '0.2rem 0.55rem',
              borderRadius: '9999px',
              fontSize: '0.7rem',
              fontWeight: 700,
              background: '#f1f5f9',
              color: '#475569',
              border: '1px solid #cbd5e1',
              letterSpacing: '0.03em',
            }}
          >
            <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#475569' }} />
            CLOSED
          </span>
        );
      case 'RESOLVED':
        return (
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.35rem',
              padding: '0.2rem 0.55rem',
              borderRadius: '9999px',
              fontSize: '0.7rem',
              fontWeight: 700,
              background: '#dcfce7',
              color: '#059669',
              border: '1px solid #86efac',
              letterSpacing: '0.03em',
            }}
          >
            <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#059669' }} />
            RESOLVED
          </span>
        );
      case 'DISMISSED':
        return (
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.35rem',
              padding: '0.2rem 0.55rem',
              borderRadius: '9999px',
              fontSize: '0.7rem',
              fontWeight: 700,
              background: '#fef2f2',
              color: '#dc2626',
              border: '1px solid #fecaca',
              letterSpacing: '0.03em',
            }}
          >
            <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#dc2626' }} />
            DISMISSED
          </span>
        );
      default:
        return <span style={{ fontSize: '0.7rem', color: '#475569' }}>{status}</span>;
    }
  };

  // Semantic Severity Badges (Light Theme)
  const renderSeverityBadge = (severity: string) => {
    const isCritical = severity === 'CRITICAL';
    const isHigh = severity === 'HIGH';
    return (
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: '0.3rem',
          padding: '0.18rem 0.5rem',
          borderRadius: '4px',
          fontSize: '0.68rem',
          fontWeight: 800,
          textTransform: 'uppercase',
          letterSpacing: '0.04em',
          background: isCritical ? '#fee2e2' : isHigh ? '#fef3c7' : '#e0f2fe',
          color: isCritical ? '#dc2626' : isHigh ? '#d97706' : '#0284c7',
          border: `1px solid ${isCritical ? '#fca5a5' : isHigh ? '#fde68a' : '#bae6fd'}`,
        }}
      >
        {isCritical && <AlertTriangle size={11} />}
        {severity}
      </span>
    );
  };

  // Semantic Triage Badges (Light Theme)
  const renderTriageBadge = (triage: string) => {
    return (
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: '0.25rem',
          padding: '0.18rem 0.45rem',
          borderRadius: '4px',
          fontSize: '0.68rem',
          fontWeight: 700,
          letterSpacing: '0.03em',
          background: '#ede9fe',
          color: '#6366f1',
          border: '1px solid #c4b5fd',
        }}
      >
        <Sparkles size={10} />
        {triage}
      </span>
    );
  };

  // Semantic Event Status Badge
  const renderEventStatusBadge = (status: string) => {
    const isResolved = status === 'RESOLVED';
    const isDismissed = status === 'DISMISSED';
    const isAction = status === 'ACTION_TAKEN';
    const isInspection = status === 'IN_INSPECTION';
    const isAssigned = status === 'ASSIGNED';

    return (
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: '0.25rem',
          padding: '0.18rem 0.45rem',
          borderRadius: '4px',
          fontSize: '0.68rem',
          fontWeight: 700,
          letterSpacing: '0.03em',
          background: isResolved
            ? '#ecfdf5'
            : isDismissed
            ? '#fef2f2'
            : isAction
            ? '#f5f3ff'
            : isInspection
            ? '#fffbeb'
            : isAssigned
            ? '#eff6ff'
            : '#f0fdf4',
          color: isResolved
            ? '#059669'
            : isDismissed
            ? '#dc2626'
            : isAction
            ? '#7c3aed'
            : isInspection
            ? '#d97706'
            : isAssigned
            ? '#2563eb'
            : '#15803d',
          border: `1px solid ${
            isResolved
              ? '#a7f3d0'
              : isDismissed
              ? '#fca5a5'
              : isAction
              ? '#ddd6fe'
              : isInspection
              ? '#fde68a'
              : isAssigned
              ? '#bfdbfe'
              : '#bbf7d0'
          }`,
        }}
      >
        <Activity size={10} />
        EVENT: {status}
      </span>
    );
  };

  // Helper to render forecast pills (Light Theme)
  const renderForecastPills = (summary?: string | null) => {
    if (!summary || !summary.trim()) {
      return (
        <span style={{ fontSize: '0.72rem', color: '#64748b', fontStyle: 'italic' }}>
          Forecast unavailable for cell
        </span>
      );
    }
    const parts = summary.split(',').map((s) => s.trim()).filter(Boolean);
    if (parts.length > 0) {
      return (
        <div style={{ display: 'flex', gap: '0.35rem', flexWrap: 'wrap' }}>
          {parts.map((p, idx) => (
            <span
              key={idx}
              style={{
                padding: '0.15rem 0.45rem',
                borderRadius: '4px',
                fontSize: '0.68rem',
                fontWeight: 600,
                background: '#f0f9ff',
                color: '#0369a1',
                border: '1px solid #bae6fd',
                fontFamily: 'var(--font-mono)',
              }}
            >
              {p}
            </span>
          ))}
        </div>
      );
    }
    return <span style={{ fontSize: '0.72rem', color: '#334155' }}>{summary}</span>;
  };

  return (
    <PageContainer
      title="Municipal Authority Alert Queue"
      subtitle="Evidence-backed environmental response candidates evaluated by the F5 triage engine"
      actions={
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem' }}>
          <button
            onClick={() => refresh()}
            disabled={loading}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.4rem',
              padding: '0.45rem 0.9rem',
              borderRadius: '8px',
              fontSize: '0.8rem',
              fontWeight: 600,
              cursor: loading ? 'not-allowed' : 'pointer',
              background: '#ffffff',
              color: '#0284c7',
              border: '1px solid #cbd5e1',
              boxShadow: '0 1px 2px rgba(15, 23, 42, 0.04)',
              transition: 'all 0.15s ease',
            }}
          >
            <RefreshCw size={13} className={loading ? 'animate-spin' : ''} />
            Refresh Queue
          </button>
        </div>
      }
    >
      {/* =========================================================================
          FILTER BAR (LIGHT THEME SEGMENTED CONTROL & DYNAMIC COUNTS)
          ========================================================================= */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '1.25rem',
          flexWrap: 'wrap',
          gap: '0.75rem',
          padding: '0.75rem 1rem',
          borderRadius: '12px',
          background: '#ffffff',
          border: '1px solid #e2e8f0',
          boxShadow: '0 1px 3px rgba(15, 23, 42, 0.03)',
        }}
      >
        <div style={{ display: 'flex', gap: '0.45rem', alignItems: 'center', flexWrap: 'wrap' }}>
          {(['ALL', 'OPEN', 'ACKNOWLEDGED', 'ASSIGNED', 'IN_INSPECTION', 'ACTION_TAKEN', 'RESOLVED', 'DISMISSED'] as const).map((s) => {
            const isActive = statusFilter === s;
            const count = counts[s];
            return (
              <button
                key={s}
                onClick={() => setStatusFilter(s)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.45rem',
                  padding: '0.4rem 0.85rem',
                  borderRadius: '8px',
                  fontSize: '0.8rem',
                  fontWeight: isActive ? 700 : 600,
                  cursor: 'pointer',
                  background: isActive ? '#0284c7' : '#f8fafc',
                  color: isActive ? '#ffffff' : '#334155',
                  border: isActive ? '1px solid #0284c7' : '1px solid #e2e8f0',
                  boxShadow: isActive ? '0 2px 4px rgba(2, 132, 199, 0.25)' : 'none',
                  transition: 'all 0.15s ease',
                }}
              >
                <span>{s}</span>
                <span
                  style={{
                    padding: '0.1rem 0.45rem',
                    borderRadius: '9999px',
                    fontSize: '0.68rem',
                    fontWeight: 700,
                    background: isActive ? 'rgba(255, 255, 255, 0.25)' : '#e2e8f0',
                    color: isActive ? '#ffffff' : '#475569',
                  }}
                >
                  {count}
                </span>
              </button>
            );
          })}
        </div>

        {/* Section 8: Active Queue Indicator */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem', fontSize: '0.8rem', color: '#0f172a', fontWeight: 600 }}>
          <Activity size={14} color="#0284c7" />
          <span>Active Queue &middot; {filteredItems.length} candidate{filteredItems.length === 1 ? '' : 's'}</span>
        </div>
      </div>

      {actionError && (
        <div
          style={{
            padding: '0.75rem 1rem',
            borderRadius: '8px',
            background: '#fee2e2',
            border: '1px solid #fca5a5',
            color: '#dc2626',
            fontSize: '0.85rem',
            marginBottom: '1rem',
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
          }}
        >
          <AlertCircle size={16} />
          <span>{actionError}</span>
        </div>
      )}

      {/* =========================================================================
          MAIN OPERATIONAL VIEWPORT (~42% LIST / ~58% DETAIL)
          ========================================================================= */}
      {loading ? (
        <div
          style={{
            padding: '3.5rem 2rem',
            textAlign: 'center',
            color: '#475569',
            background: '#ffffff',
            borderRadius: '12px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 1px 3px rgba(15, 23, 42, 0.04)',
          }}
        >
          <RefreshCw size={26} className="animate-spin" style={{ margin: '0 auto 0.75rem', color: '#0284c7' }} />
          <p style={{ fontSize: '0.95rem', fontWeight: 700, color: '#0f172a' }}>Synchronizing Authority Alert Queue...</p>
          <p style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '0.25rem' }}>
            Retrieving evidence-backed intervention records from PostgreSQL persistence
          </p>
        </div>
      ) : error ? (
        (isUnauthorized || error.toLowerCase().includes('authentication required') || error.includes('401')) ? (
          <div
            style={{
              padding: '2.5rem',
              textAlign: 'center',
              background: '#ffffff',
              borderRadius: '12px',
              border: '1px solid #bfdbfe',
              boxShadow: '0 2px 8px rgba(2, 132, 199, 0.08)',
              maxWidth: '560px',
              margin: '2rem auto',
            }}
          >
            <div
              style={{
                width: '52px',
                height: '52px',
                borderRadius: '50%',
                background: '#e0f2fe',
                border: '1px solid #bae6fd',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 1rem',
                color: '#0284c7',
              }}
            >
              <ShieldAlert size={28} />
            </div>
            <h3 style={{ color: '#0f172a', fontWeight: 700, fontSize: '1.15rem', marginBottom: '0.4rem' }}>
              Municipal Authority Authentication Required
            </h3>
            <p style={{ color: '#64748b', fontSize: '0.85rem', lineHeight: 1.5, marginBottom: '1.5rem' }}>
              Access to the municipal alert candidate queue and operational intervention pipeline is restricted to verified authority officers and environmental analysts (Safety Invariant 12).
            </p>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', alignItems: 'center' }}>
              <button
                onClick={async () => {
                  setAuthenticating(true);
                  setActionError(null);
                  try {
                    await authService.login({ email: customOfficerEmail.trim() || 'authority@aerosentinel.gov', password: 'password' });
                    await refresh();
                  } catch (err: any) {
                    setActionError(err?.response?.data?.message || err?.message || 'Authentication failed');
                  } finally {
                    setAuthenticating(false);
                  }
                }}
                disabled={authenticating}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.5rem',
                  padding: '0.65rem 1.4rem',
                  borderRadius: '8px',
                  background: '#0284c7',
                  color: '#ffffff',
                  border: 'none',
                  fontSize: '0.9rem',
                  fontWeight: 600,
                  cursor: authenticating ? 'wait' : 'pointer',
                  boxShadow: '0 2px 4px rgba(2, 132, 199, 0.25)',
                  transition: 'all 0.15s ease',
                }}
              >
                <ShieldCheck size={16} />
                <span>{authenticating ? 'Authenticating Officer Session...' : 'Authenticate as Municipal Authority Officer'}</span>
              </button>

              <button
                type="button"
                onClick={() => setShowCustomLogin(!showCustomLogin)}
                style={{
                  background: 'none',
                  border: 'none',
                  color: '#0284c7',
                  fontSize: '0.78rem',
                  cursor: 'pointer',
                  textDecoration: 'underline',
                  padding: '0.2rem',
                }}
              >
                {showCustomLogin ? 'Hide Custom Credentials' : 'Sign in with custom authority email'}
              </button>

              {showCustomLogin && (
                <form
                  onSubmit={async (e) => {
                    e.preventDefault();
                    setAuthenticating(true);
                    setActionError(null);
                    try {
                      await authService.login({ email: customOfficerEmail.trim() || 'authority@aerosentinel.gov', password: customOfficerPassword });
                      await refresh();
                    } catch (err: any) {
                      setActionError(err?.response?.data?.message || err?.message || 'Authentication failed: Invalid credentials');
                    } finally {
                      setAuthenticating(false);
                    }
                  }}
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '0.5rem',
                    width: '100%',
                    maxWidth: '380px',
                    marginTop: '0.5rem',
                  }}
                >
                  <input
                    type="text"
                    value={customOfficerEmail}
                    onChange={(e) => setCustomOfficerEmail(e.target.value)}
                    placeholder="officer@aerosentinel.gov"
                    style={{
                      width: '100%',
                      padding: '0.45rem 0.75rem',
                      borderRadius: '6px',
                      border: '1px solid #cbd5e1',
                      fontSize: '0.825rem',
                      boxSizing: 'border-box',
                    }}
                  />
                  <input
                    type="password"
                    value={customOfficerPassword}
                    onChange={(e) => setCustomOfficerPassword(e.target.value)}
                    placeholder="Password"
                    style={{
                      width: '100%',
                      padding: '0.45rem 0.75rem',
                      borderRadius: '6px',
                      border: '1px solid #cbd5e1',
                      fontSize: '0.825rem',
                      boxSizing: 'border-box',
                    }}
                  />
                  <button
                    type="submit"
                    disabled={authenticating}
                    style={{
                      width: '100%',
                      padding: '0.45rem 0.9rem',
                      borderRadius: '6px',
                      background: '#0f172a',
                      color: '#ffffff',
                      border: 'none',
                      fontSize: '0.825rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                    }}
                  >
                    {authenticating ? 'Verifying Credentials...' : 'Sign In with Credentials'}
                  </button>
                </form>
              )}
            </div>
          </div>
        ) : (
          <div
            style={{
              padding: '2.5rem',
              textAlign: 'center',
              background: '#ffffff',
              borderRadius: '12px',
              border: '1px solid #fecaca',
              boxShadow: '0 1px 3px rgba(15, 23, 42, 0.04)',
            }}
          >
            <AlertTriangle size={30} style={{ margin: '0 auto 0.75rem', color: '#dc2626' }} />
            <p style={{ color: '#dc2626', fontWeight: 700, fontSize: '1rem', marginBottom: '0.25rem' }}>
              Unable to load authority alert queue
            </p>
            <p style={{ color: '#64748b', fontSize: '0.825rem', marginBottom: '1.25rem' }}>{error}</p>
            <button
              onClick={() => refresh()}
              style={{
                padding: '0.45rem 1.15rem',
                borderRadius: '6px',
                background: '#0284c7',
                color: '#ffffff',
                border: 'none',
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              Retry Connection
            </button>
          </div>
        )
      ) : filteredItems.length === 0 ? (
        /* Section 23: Light Operational Empty State */
        <div
          style={{
            padding: '3rem 2rem',
            textAlign: 'center',
            background: '#ffffff',
            borderRadius: '12px',
            border: '1px solid #e2e8f0',
            maxWidth: '560px',
            margin: '2rem auto',
            boxShadow: '0 1px 3px rgba(15, 23, 42, 0.04)',
          }}
        >
          <div
            style={{
              width: '48px',
              height: '48px',
              borderRadius: '50%',
              background: '#dcfce7',
              border: '1px solid #86efac',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 1rem',
            }}
          >
            <CheckCircle size={24} color="#059669" />
          </div>
          <h4 style={{ fontSize: '1.05rem', fontWeight: 700, color: '#0f172a', marginBottom: '0.4rem' }}>
            No alert candidates are currently available
          </h4>
          <p style={{ fontSize: '0.825rem', color: '#64748b', lineHeight: 1.5, margin: 0 }}>
            {statusFilter === 'ALL'
              ? 'No events currently satisfy the authoritative F5 ALERT_CANDIDATE threshold (evidenceScore \u2265 0.55). Ground telemetry and spatial cells remain under automated monitoring.'
              : `No alerts currently match the "${statusFilter}" operational filter.`}
          </p>
          {statusFilter !== 'ALL' && (
            <button
              onClick={() => setStatusFilter('ALL')}
              style={{
                marginTop: '1rem',
                padding: '0.4rem 0.9rem',
                borderRadius: '6px',
                background: '#f0f9ff',
                border: '1px solid #bae6fd',
                color: '#0284c7',
                fontSize: '0.8rem',
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              Show All Alerts ({counts.ALL})
            </button>
          )}
        </div>
      ) : (
        /* Section 9: 42% List / 58% Detail Proportions */
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'minmax(320px, 42%) minmax(420px, 58%)',
            gap: '1.25rem',
            alignItems: 'start',
          }}
        >
          {/* =========================================================================
              LEFT COLUMN: ALERT LIST CARDS (~42%) — LIGHT THEME
              ========================================================================= */}
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: '0.75rem',
              maxHeight: 'calc(100vh - 220px)',
              overflowY: 'auto',
              paddingRight: '4px',
            }}
          >
            {filteredItems.map((item, index) => {
              const isSelected = item.alertId === selectedAlertId;
              const formattedTime = item.createdAt
                ? new Date(item.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                : '';

              return (
                <div
                  key={item.alertId}
                  onClick={() => setSelectedAlertId(item.alertId)}
                  style={{
                    padding: '1.15rem',
                    borderRadius: '12px',
                    background: isSelected ? '#f0f7ff' : '#ffffff',
                    border: isSelected ? '1.5px solid #0284c7' : '1px solid #e2e8f0',
                    boxShadow: isSelected
                      ? '0 0 0 1px #0284c7, 0 4px 12px rgba(2, 132, 199, 0.12)'
                      : '0 1px 3px rgba(15, 23, 42, 0.04)',
                    cursor: 'pointer',
                    transition: 'all 0.18s ease',
                    position: 'relative',
                    animation: `fadeIn 0.25s ease forwards`,
                    animationDelay: `${index * 35}ms`,
                  }}
                  onMouseEnter={(e) => {
                    if (!isSelected) {
                      e.currentTarget.style.borderColor = '#93c5fd';
                      e.currentTarget.style.transform = 'translateY(-2px)';
                      e.currentTarget.style.boxShadow = '0 4px 12px rgba(15, 23, 42, 0.08)';
                    }
                  }}
                  onMouseLeave={(e) => {
                    if (!isSelected) {
                      e.currentTarget.style.borderColor = '#e2e8f0';
                      e.currentTarget.style.transform = 'none';
                      e.currentTarget.style.boxShadow = '0 1px 3px rgba(15, 23, 42, 0.04)';
                    }
                  }}
                >
                  {/* Selected Indicator Pill */}
                  {isSelected && (
                    <div
                      style={{
                        position: 'absolute',
                        left: 0,
                        top: '12px',
                        bottom: '12px',
                        width: '4px',
                        borderRadius: '0 4px 4px 0',
                        background: '#0284c7',
                      }}
                    />
                  )}

                  {/* Top Row: Risk Level, Status, Triage, Timestamp */}
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      marginBottom: '0.5rem',
                      gap: '0.4rem',
                      flexWrap: 'wrap',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', flexWrap: 'wrap' }}>
                      {renderSeverityBadge(item.severity)}
                      {renderStatusBadge(item.status)}
                      {renderTriageBadge(item.triageState)}
                    </div>
                    <span style={{ fontSize: '0.72rem', color: '#64748b', fontFamily: 'var(--font-mono)' }}>
                      {formattedTime}
                    </span>
                  </div>

                  {/* Main: Event Title (Section 11: Dark Navy, 700 weight) */}
                  <h4
                    style={{
                      fontSize: '0.95rem',
                      fontWeight: 700,
                      color: '#0f172a',
                      marginBottom: '0.5rem',
                      lineHeight: 1.35,
                    }}
                  >
                    {item.title}
                  </h4>

                  {/* Metric Strip (Section 13: Soft cool-blue/grey surface) */}
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(3, 1fr)',
                      gap: '0.5rem',
                      background: '#f1f5f9',
                      padding: '0.5rem 0.75rem',
                      borderRadius: '8px',
                      border: '1px solid #e2e8f0',
                      marginBottom: '0.5rem',
                    }}
                  >
                    <div>
                      <span style={{ color: '#64748b', display: 'block', fontSize: '0.66rem', textTransform: 'uppercase', fontWeight: 600 }}>
                        Evidence
                      </span>
                      <span style={{ color: '#0284c7', fontWeight: 800, fontFamily: 'var(--font-mono)', fontSize: '0.85rem' }}>
                        {item.evidenceScore.toFixed(3)}
                      </span>
                    </div>
                    <div>
                      <span style={{ color: '#64748b', display: 'block', fontSize: '0.66rem', textTransform: 'uppercase', fontWeight: 600 }}>
                        F3 Risk
                      </span>
                      <span
                        style={{
                          color: item.riskScore >= 0.7 ? '#dc2626' : item.riskScore >= 0.4 ? '#d97706' : '#059669',
                          fontWeight: 800,
                          fontFamily: 'var(--font-mono)',
                          fontSize: '0.85rem',
                        }}
                      >
                        {item.riskScore != null ? item.riskScore.toFixed(2) : 'N/A'}
                      </span>
                    </div>
                    <div>
                      <span style={{ color: '#64748b', display: 'block', fontSize: '0.66rem', textTransform: 'uppercase', fontWeight: 600 }}>
                        H3 Cell
                      </span>
                      <span style={{ color: '#0f172a', fontFamily: 'var(--font-mono)', fontSize: '0.72rem', fontWeight: 600 }}>
                        {item.h3Index.substring(0, 8)}...
                      </span>
                    </div>
                  </div>

                  {/* Forecast Summary: 1h, 3h, 6h (Section 14) */}
                  {item.forecastSummary && (
                    <div style={{ marginBottom: '0.5rem' }}>
                      {renderForecastPills(item.forecastSummary)}
                    </div>
                  )}

                  {/* Operational Row: Team & Verification State (Section 15) */}
                  {(item.assignedTeamName || item.verificationResult) && (
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        paddingTop: '0.4rem',
                        borderTop: '1px solid #e2e8f0',
                        fontSize: '0.75rem',
                        gap: '0.5rem',
                      }}
                    >
                      {item.assignedTeamName ? (
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', color: '#0284c7' }}>
                          <Users size={13} />
                          <span style={{ fontWeight: 600 }}>{item.assignedTeamName}</span>
                          <span style={{ color: '#64748b' }}>({item.assignmentStatus || 'ASSIGNED'})</span>
                        </div>
                      ) : (
                        <span style={{ color: '#64748b' }}>Unassigned</span>
                      )}

                      {item.verificationResult && (
                        <span
                          style={{
                            padding: '0.1rem 0.45rem',
                            borderRadius: '4px',
                            fontSize: '0.68rem',
                            fontWeight: 700,
                            background:
                              item.verificationResult === 'CONFIRMED'
                                ? '#dcfce7'
                                : item.verificationResult === 'REJECTED'
                                ? '#fee2e2'
                                : '#fef3c7',
                            color:
                              item.verificationResult === 'CONFIRMED'
                                ? '#059669'
                                : item.verificationResult === 'REJECTED'
                                ? '#dc2626'
                                : '#d97706',
                            border: '1px solid currentColor',
                          }}
                        >
                          {item.verificationResult}
                        </span>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          {/* =========================================================================
              RIGHT COLUMN: SELECTED ALERT OPERATIONAL DOSSIER (~58%) — LIGHT THEME
              ========================================================================= */}
          {selectedAlert ? (
            <div
              style={{
                background: '#ffffff',
                borderRadius: '14px',
                border: '1px solid #e2e8f0',
                padding: '1.5rem',
                display: 'flex',
                flexDirection: 'column',
                gap: '1.25rem',
                position: 'sticky',
                top: '1rem',
                maxHeight: 'calc(100vh - 200px)',
                overflowY: 'auto',
                boxShadow: '0 2px 8px rgba(15, 23, 42, 0.05)',
              }}
            >
              {/* Header: Alert Risk, Status, Triage, Event Status, Title */}
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.65rem', flexWrap: 'wrap', gap: '0.5rem' }}>
                  <div style={{ display: 'flex', gap: '0.45rem', alignItems: 'center', flexWrap: 'wrap' }}>
                    {renderSeverityBadge(selectedAlert.severity)}
                    {renderStatusBadge(selectedAlert.status)}
                    {renderTriageBadge(selectedAlert.triageState)}
                    {renderEventStatusBadge(currentEventStatus)}
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                    <span
                      data-testid="alert-detail-alert-id"
                      style={{ fontSize: '0.72rem', color: '#64748b', fontFamily: 'var(--font-mono)' }}
                    >
                      ID: {selectedAlert.alertId}
                    </span>
                    <button
                      type="button"
                      onClick={() => handleCopy('alertId', selectedAlert.alertId)}
                      style={{ background: 'transparent', border: 'none', cursor: 'pointer', color: '#64748b', padding: '0.1rem' }}
                      title="Copy full Alert UUID"
                    >
                      {copiedKey === 'alertId' ? <Check size={12} color="#059669" /> : <Copy size={12} />}
                    </button>
                  </div>
                </div>

                <h3 style={{ fontSize: '1.25rem', fontWeight: 800, color: '#0f172a', marginBottom: '0.4rem', lineHeight: 1.3 }}>
                  {selectedAlert.title}
                </h3>
                <p style={{ fontSize: '0.875rem', color: '#334155', lineHeight: 1.5, margin: 0 }}>
                  {selectedAlert.message}
                </p>

                <div style={{ display: 'flex', gap: '1rem', marginTop: '0.5rem', fontSize: '0.72rem', color: '#64748b', flexWrap: 'wrap' }}>
                  <span>Created: {new Date(selectedAlert.createdAt).toLocaleString()}</span>
                  <span>Jurisdiction: <strong style={{ color: '#0f172a' }}>{selectedAlert.cityName || 'Pune'}</strong></span>
                </div>
              </div>

              {/* Section 2: Canonical Operational Lifecycle Timeline */}
              <div
                data-testid="lifecycle-timeline-card"
                style={{
                  background: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  borderRadius: '10px',
                  padding: '1rem',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '0.75rem',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem' }}>
                    <ShieldCheck size={16} color="#0284c7" />
                    <span style={{ fontSize: '0.8rem', fontWeight: 700, color: '#0f172a', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                      Operational Lifecycle
                    </span>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.75rem' }}>
                    <span style={{ color: '#64748b' }}>Event:</span>
                    {renderEventStatusBadge(currentEventStatus)}
                    <span style={{ color: '#64748b', marginLeft: '0.25rem' }}>Alert:</span>
                    {renderStatusBadge(selectedAlert.status)}
                  </div>
                </div>

                {/* State Sync explanation strip */}
                <div
                  data-testid="status-sync-explanation"
                  style={{
                    fontSize: '0.75rem',
                    color: '#475569',
                    background: '#ffffff',
                    border: '1px solid #e2e8f0',
                    borderRadius: '6px',
                    padding: '0.45rem 0.65rem',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.4rem',
                  }}
                >
                  <Info size={13} color="#0284c7" style={{ flexShrink: 0 }} />
                  <span>{getSyncExplanation(currentEventStatus, selectedAlert.status)}</span>
                </div>

                {/* 5-Step Pipeline */}
                {currentEventStatus === 'DISMISSED' || selectedAlert.status === 'DISMISSED' ? (
                  <div
                    data-testid="dismissed-lifecycle-banner"
                    style={{
                      background: '#fef2f2',
                      border: '1px solid #fecaca',
                      borderRadius: '8px',
                      padding: '0.75rem',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.65rem',
                    }}
                  >
                    <XCircle size={20} color="#dc2626" />
                    <div>
                      <div style={{ fontSize: '0.825rem', fontWeight: 700, color: '#991b1b' }}>
                        Candidate Dismissed from Operational Queue
                      </div>
                      <div style={{ fontSize: '0.75rem', color: '#b91c1c' }}>
                        {selectedAlert.dismissalReason ? `Reason: ${selectedAlert.dismissalReason}` : 'Event evaluated as non-actionable or false anomaly.'}
                      </div>
                    </div>
                  </div>
                ) : (
                  <div
                    data-testid="lifecycle-stepper"
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(5, 1fr)',
                      gap: '0.5rem',
                      marginTop: '0.25rem',
                    }}
                  >
                    {LIFECYCLE_STEPS.map((step, idx) => {
                      const state = getStepState(idx, currentEventStatus);
                      const isCompleted = state === 'completed';
                      const isActive = state === 'current';

                      return (
                        <div
                          key={step.key}
                          data-testid={`step-${step.key.toLowerCase()}`}
                          data-step-state={state}
                          style={{
                            background: isActive ? '#eff6ff' : isCompleted ? '#f0fdf4' : '#ffffff',
                            border: `1.5px solid ${isActive ? '#3b82f6' : isCompleted ? '#22c55e' : '#cbd5e1'}`,
                            borderRadius: '8px',
                            padding: '0.55rem 0.35rem',
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            textAlign: 'center',
                            gap: '0.25rem',
                            position: 'relative',
                            transition: 'all 0.2s ease',
                          }}
                        >
                          <div
                            style={{
                              width: '22px',
                              height: '22px',
                              borderRadius: '50%',
                              background: isActive ? '#2563eb' : isCompleted ? '#16a34a' : '#e2e8f0',
                              color: isActive || isCompleted ? '#ffffff' : '#64748b',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              fontSize: '0.68rem',
                              fontWeight: 700,
                            }}
                          >
                            {isCompleted ? <Check size={12} strokeWidth={3} /> : idx + 1}
                          </div>
                          <span
                            style={{
                              fontSize: '0.68rem',
                              fontWeight: isActive ? 800 : 600,
                              color: isActive ? '#1d4ed8' : isCompleted ? '#15803d' : '#64748b',
                              lineHeight: 1.2,
                            }}
                          >
                            {step.label}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Authority Alert Spatial Map (Real H3 Res-8 Polygon Visualization) */}
              <AuthorityAlertMap
                h3Index={selectedAlert.h3Index}
                severity={selectedAlert.severity}
                title={selectedAlert.title}
                eventCode={selectedAlert.eventCode}
                height="240px"
              />

              {/* Section 3: POTENTIAL POLLUTION EVENT CONTEXT (F7-P3 Unified Context) */}
              <div
                style={{
                  background: '#f8fafc',
                  borderRadius: '10px',
                  padding: '1rem',
                  border: '1px solid #e2e8f0',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.65rem' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.75rem', fontWeight: 700, color: '#475569', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                    <Activity size={13} />
                    <span>Potential Pollution Event</span>
                  </div>
                  <span style={{ fontSize: '0.7rem', padding: '0.15rem 0.45rem', borderRadius: '4px', background: '#f1f5f9', color: '#475569', fontWeight: 600 }}>
                    Event status: {eventContext?.status || eventContext?.event?.status || 'ACTIVE'}
                  </span>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '0.75rem', fontSize: '0.78rem' }}>
                  <div>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.7rem' }}>Canonical Event Code</span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', marginTop: '0.15rem' }}>
                      <span style={{ color: '#0284c7', fontFamily: 'var(--font-mono)', fontWeight: 700 }}>
                        {selectedAlert.eventCode}
                      </span>
                      <button
                        type="button"
                        onClick={() => handleCopy('eventCode', selectedAlert.eventCode)}
                        style={{ background: 'transparent', border: 'none', cursor: 'pointer', color: '#64748b', padding: '0.1rem' }}
                        title="Copy Event Code"
                      >
                        {copiedKey === 'eventCode' ? <Check size={11} color="#059669" /> : <Copy size={11} />}
                      </button>
                    </div>
                  </div>

                  <div>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.7rem' }}>Authoritative H3 Cell</span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', marginTop: '0.15rem' }}>
                      <span style={{ color: '#0f172a', fontFamily: 'var(--font-mono)', fontWeight: 600 }}>
                        {selectedAlert.h3Index}
                      </span>
                      <button
                        type="button"
                        onClick={() => handleCopy('h3Index', selectedAlert.h3Index)}
                        style={{ background: 'transparent', border: 'none', cursor: 'pointer', color: '#64748b', padding: '0.1rem' }}
                        title="Copy H3 Index"
                      >
                        {copiedKey === 'h3Index' ? <Check size={11} color="#059669" /> : <Copy size={11} />}
                      </button>
                    </div>
                  </div>

                  <div>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.7rem' }}>Parent Prediction ID</span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', marginTop: '0.15rem' }}>
                      <span style={{ color: '#334155', fontFamily: 'var(--font-mono)', fontSize: '0.72rem' }}>
                        {selectedAlert.predictionId || eventContext?.predictionId || 'Direct Run'}
                      </span>
                      {selectedAlert.predictionId && (
                        <button
                          type="button"
                          onClick={() => handleCopy('predId', selectedAlert.predictionId)}
                          style={{ background: 'transparent', border: 'none', cursor: 'pointer', color: '#64748b', padding: '0.1rem' }}
                          title="Copy Prediction UUID"
                        >
                          {copiedKey === 'predId' ? <Check size={11} color="#059669" /> : <Copy size={11} />}
                        </button>
                      )}
                    </div>
                  </div>

                  <div>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.7rem' }}>Jurisdiction City</span>
                    <span style={{ color: '#0f172a', fontWeight: 600, marginTop: '0.15rem', display: 'block' }}>
                      {selectedAlert.cityName || eventContext?.cityName || 'Pune'}
                    </span>
                  </div>
                </div>

                <div style={{ marginTop: '0.65rem', padding: '0.45rem 0.65rem', borderRadius: '6px', background: '#f1f5f9', fontSize: '0.72rem', color: '#64748b', fontStyle: 'italic', border: '1px solid #e2e8f0' }}>
                  Notice: Domain context represents a "Potential Pollution Event", not an authenticated emitter or industrial site.
                </div>
              </div>

              {/* Section 4: F3 HOTSPOT PREDICTION */}
              <div
                style={{
                  background: '#f8fafc',
                  borderRadius: '10px',
                  padding: '1rem',
                  border: '1px solid #e2e8f0',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.65rem' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.75rem', fontWeight: 700, color: '#2563eb', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                    <TrendingUp size={13} />
                    <span>Hotspot Prediction</span>
                  </div>
                  <span style={{ fontSize: '0.68rem', color: '#64748b', fontFamily: 'var(--font-mono)' }}>
                    Model: {eventContext?.prediction?.modelVersion || 'hotspot_classifier_v1'}
                  </span>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '0.65rem' }}>
                  <div style={{ background: '#ffffff', padding: '0.65rem 0.8rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.68rem', fontWeight: 600 }}>
                      F3 Risk Score
                    </span>
                    <span
                      style={{
                        color: selectedAlert.riskScore >= 0.7 ? '#dc2626' : selectedAlert.riskScore >= 0.4 ? '#d97706' : '#059669',
                        fontWeight: 800,
                        fontFamily: 'var(--font-mono)',
                        fontSize: '1.05rem',
                      }}
                    >
                      {selectedAlert.riskScore != null ? selectedAlert.riskScore.toFixed(4) : 'N/A'}
                    </span>
                  </div>

                  <div style={{ background: '#ffffff', padding: '0.65rem 0.8rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.68rem', fontWeight: 600 }}>
                      Risk Level
                    </span>
                    <span style={{ color: '#0f172a', fontWeight: 700, fontSize: '0.9rem' }}>
                      {eventContext?.prediction?.riskLevel || (selectedAlert.riskScore >= 0.7 ? 'CRITICAL' : selectedAlert.riskScore >= 0.4 ? 'HIGH' : 'ELEVATED')}
                    </span>
                  </div>

                  <div style={{ background: '#ffffff', padding: '0.65rem 0.8rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '0.68rem', fontWeight: 600 }}>
                      Model Confidence
                    </span>
                    <span style={{ color: '#334155', fontWeight: 700, fontSize: '0.9rem' }}>
                      {eventContext?.prediction?.confidence != null ? `${(eventContext.prediction.confidence * 100).toFixed(1)}%` : 'Platt Calibrated'}
                    </span>
                  </div>
                </div>

                <div style={{ marginTop: '0.5rem', fontSize: '0.7rem', color: '#64748b' }}>
                  Authoritative F3 values preserved. Prediction ID: <span style={{ fontFamily: 'var(--font-mono)' }}>{selectedAlert.predictionId || 'N/A'}</span>. Risk is not derived from citizen reports.
                </div>
              </div>

              {/* Section 5: F4 FORECAST SECTION */}
              <div
                style={{
                  background: '#f8fafc',
                  borderRadius: '10px',
                  padding: '1rem',
                  border: '1px solid #e2e8f0',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.65rem' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.75rem', fontWeight: 700, color: '#0284c7', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                    <Clock size={13} />
                    <span>F4 Dispersion Forecast</span>
                  </div>
                  <span style={{ fontSize: '0.68rem', color: '#64748b' }}>
                    Trajectories (+1h, +3h, +6h)
                  </span>
                </div>

                {forecastHorizons && (forecastHorizons.h1 || forecastHorizons.h3 || forecastHorizons.h6 || forecastHorizons.raw) ? (
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '0.65rem' }}>
                    <div style={{ background: '#ffffff', padding: '0.65rem 0.8rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                      <span style={{ color: '#64748b', display: 'block', fontSize: '0.68rem', fontWeight: 600 }}>
                        +1 Hour
                      </span>
                      <span style={{ color: '#0f172a', fontWeight: 700, fontSize: '0.95rem', fontFamily: 'var(--font-mono)' }}>
                        {forecastHorizons.h1 || (forecastHorizons.raw ? 'Projected' : 'N/A')}
                      </span>
                    </div>

                    <div style={{ background: '#ffffff', padding: '0.65rem 0.8rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                      <span style={{ color: '#64748b', display: 'block', fontSize: '0.68rem', fontWeight: 600 }}>
                        +3 Hours
                      </span>
                      <span style={{ color: '#0f172a', fontWeight: 700, fontSize: '0.95rem', fontFamily: 'var(--font-mono)' }}>
                        {forecastHorizons.h3 || (forecastHorizons.raw ? 'Projected' : 'N/A')}
                      </span>
                    </div>

                    <div style={{ background: '#ffffff', padding: '0.65rem 0.8rem', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                      <span style={{ color: '#64748b', display: 'block', fontSize: '0.68rem', fontWeight: 600 }}>
                        +6 Hours
                      </span>
                      <span style={{ color: '#0f172a', fontWeight: 700, fontSize: '0.95rem', fontFamily: 'var(--font-mono)' }}>
                        {forecastHorizons.h6 || (forecastHorizons.raw ? 'Projected' : 'N/A')}
                      </span>
                    </div>
                  </div>
                ) : (
                  <div style={{ color: '#64748b', fontSize: '0.8rem', fontStyle: 'italic', padding: '0.5rem 0' }}>
                    Forecast Unavailable
                  </div>
                )}
              </div>

              {/* Section 6: AUTHORITATIVE F5 EVIDENCE FUSION & TRIAGE (Phase 7) */}
              <EvidenceFusionTriageCard
                h3Index={selectedAlert.h3Index}
                onRefreshParent={refreshAllData}
              />

              {/* Section 19: FIELD RESPONSE */}
              <div
                style={{
                  background: '#f8fafc',
                  borderRadius: '10px',
                  padding: '1rem',
                  border: '1px solid #e2e8f0',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.65rem' }}>
                  <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#475569', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                    Field Response Workflow
                  </div>
                  {selectedAlert.status === 'ACKNOWLEDGED' && (
                    <button
                      onClick={() => setShowInspectionModal(true)}
                      style={{
                        background: '#e0f2fe',
                        border: '1px solid #bae6fd',
                        color: '#0284c7',
                        padding: '0.25rem 0.65rem',
                        borderRadius: '6px',
                        fontSize: '0.75rem',
                        fontWeight: 600,
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '0.3rem',
                      }}
                    >
                      <Users size={12} />
                      {selectedAlert.assignedTeamName ? 'Manage Inspection' : 'Assign Field Team'}
                    </button>
                  )}
                </div>

                {selectedAlert.assignedTeamName ? (
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '0.65rem', fontSize: '0.78rem' }}>
                    <div>
                      <span style={{ color: '#64748b', display: 'block', fontSize: '0.7rem' }}>Assigned Team</span>
                      <span style={{ color: '#0f172a', fontWeight: 700 }}>{selectedAlert.assignedTeamName}</span>
                    </div>
                    <div>
                      <span style={{ color: '#64748b', display: 'block', fontSize: '0.7rem' }}>Assignment Status</span>
                      <span style={{ color: '#334155', fontWeight: 600 }}>{selectedAlert.assignmentStatus || 'ASSIGNED'}</span>
                    </div>
                    {selectedAlert.assignedAt && (
                      <div>
                        <span style={{ color: '#64748b', display: 'block', fontSize: '0.7rem' }}>Assigned Timestamp</span>
                        <span style={{ color: '#64748b' }}>{new Date(selectedAlert.assignedAt).toLocaleString()}</span>
                      </div>
                    )}
                    {selectedAlert.verificationResult && (
                      <div>
                        <span style={{ color: '#64748b', display: 'block', fontSize: '0.7rem' }}>Ground Verification Result</span>
                        <span
                          style={{
                            fontWeight: 800,
                            color:
                              selectedAlert.verificationResult === 'CONFIRMED'
                                ? '#059669'
                                : selectedAlert.verificationResult === 'REJECTED'
                                ? '#dc2626'
                                : '#d97706',
                          }}
                        >
                          {selectedAlert.verificationResult}
                        </span>
                      </div>
                    )}
                    {selectedAlert.inspectedAt && (
                      <div>
                        <span style={{ color: '#64748b', display: 'block', fontSize: '0.7rem' }}>Inspection Completed</span>
                        <span style={{ color: '#64748b' }}>{new Date(selectedAlert.inspectedAt).toLocaleString()}</span>
                      </div>
                    )}
                  </div>
                ) : (
                  <div style={{ color: '#64748b', fontSize: '0.8rem', fontStyle: 'italic', padding: '0.25rem 0' }}>
                    {selectedAlert.status === 'OPEN'
                      ? 'Alert must be acknowledged before assigning a field team.'
                      : 'No field team currently assigned to this candidate.'}
                  </div>
                )}
              </div>

              {/* Section: Authority Operational Actions History */}
              <div
                data-testid="authority-actions-panel"
                style={{
                  background: '#ffffff',
                  border: '1px solid #e2e8f0',
                  borderRadius: '10px',
                  padding: '1rem',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '0.75rem',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem' }}>
                    <ClipboardList size={16} color="#4f46e5" />
                    <span style={{ fontSize: '0.8rem', fontWeight: 700, color: '#0f172a', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                      Authority Actions Recorded ({actions.length})
                    </span>
                  </div>
                  {(currentEventStatus === 'IN_INSPECTION' || currentEventStatus === 'ACTION_TAKEN') && (
                    <button
                      type="button"
                      data-testid="btn-record-action-header"
                      onClick={() => setShowActionModal(true)}
                      style={{
                        background: '#eff6ff',
                        border: '1px solid #bfdbfe',
                        color: '#1d4ed8',
                        padding: '0.3rem 0.65rem',
                        borderRadius: '6px',
                        fontSize: '0.75rem',
                        fontWeight: 700,
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '0.3rem',
                      }}
                    >
                      <PlusCircle size={13} />
                      Record Action
                    </button>
                  )}
                </div>

                {loadingActions ? (
                  <div style={{ padding: '0.75rem', textAlign: 'center', color: '#64748b', fontSize: '0.78rem' }}>
                    <RefreshCw size={14} className="animate-spin" style={{ display: 'inline', marginRight: '0.35rem' }} />
                    Loading action log...
                  </div>
                ) : actions.length === 0 ? (
                  <div style={{ color: '#64748b', fontSize: '0.78rem', fontStyle: 'italic', padding: '0.35rem 0' }}>
                    No authority mitigation or enforcement actions recorded yet for this alert.
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                    {actions.map((act) => (
                      <div
                        key={act.id}
                        data-testid="action-item"
                        style={{
                          background: '#f8fafc',
                          border: '1px solid #e2e8f0',
                          borderRadius: '6px',
                          padding: '0.65rem',
                          display: 'flex',
                          flexDirection: 'column',
                          gap: '0.25rem',
                        }}
                      >
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <span
                            style={{
                              background: '#ede9fe',
                              color: '#6d28d9',
                              padding: '0.15rem 0.45rem',
                              borderRadius: '4px',
                              fontSize: '0.68rem',
                              fontWeight: 700,
                            }}
                          >
                            {act.actionType}
                          </span>
                          <span style={{ fontSize: '0.7rem', color: '#64748b' }}>
                            {new Date(act.performedAt).toLocaleString()}
                          </span>
                        </div>
                        <div style={{ fontSize: '0.78rem', color: '#1e293b', marginTop: '0.2rem' }}>
                          {act.actionDetails}
                        </div>
                        <div style={{ fontSize: '0.68rem', color: '#64748b' }}>
                          Officer / System: <strong style={{ color: '#334155' }}>{act.performedBy}</strong>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Section 8: GEMINI WHY (Observed Facts, Model Output, AI Interpretation, Recommended Verification) */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.65rem' }}>
                {/* 1. OBSERVED */}
                <div style={{ padding: '0.75rem', borderRadius: '8px', background: '#ffffff', border: '1px solid #e2e8f0', boxShadow: '0 1px 2px rgba(15, 23, 42, 0.02)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', color: '#0891b2', fontSize: '0.75rem', fontWeight: 700, marginBottom: '0.2rem' }}>
                    <Clock size={13} /> OBSERVED FACTS
                  </div>
                  <p style={{ fontSize: '0.825rem', color: '#334155', margin: 0, lineHeight: 1.4 }}>
                    Ground telemetry corroborated with spatial proximity facts. Evaluated at {new Date(selectedAlert.createdAt).toLocaleString()}.
                  </p>
                </div>

                {/* 2. MODEL OUTPUT */}
                <div style={{ padding: '0.75rem', borderRadius: '8px', background: '#ffffff', border: '1px solid #e2e8f0', boxShadow: '0 1px 2px rgba(15, 23, 42, 0.02)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', color: '#2563eb', fontSize: '0.75rem', fontWeight: 700, marginBottom: '0.2rem' }}>
                    <ShieldAlert size={13} /> MODEL OUTPUT
                  </div>
                  <p style={{ fontSize: '0.825rem', color: '#334155', margin: 0, lineHeight: 1.4 }}>
                    F3 Platt-Calibrated Risk: <strong style={{ color: selectedAlert.riskScore >= 0.7 ? '#dc2626' : '#d97706' }}>{selectedAlert.riskScore != null ? selectedAlert.riskScore.toFixed(3) : 'N/A'}</strong> | Forecast: {selectedAlert.forecastSummary || 'Trajectories computed'}
                  </p>
                </div>

                {/* 3. AI INTERPRETATION */}
                <div style={{ padding: '0.75rem', borderRadius: '8px', background: '#ffffff', border: '1px solid #e2e8f0', boxShadow: '0 1px 2px rgba(15, 23, 42, 0.02)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', color: '#7c3aed', fontSize: '0.75rem', fontWeight: 700, marginBottom: '0.2rem' }}>
                    <Sparkles size={13} /> AI INTERPRETATION (WHY)
                  </div>
                  <p style={{ fontSize: '0.825rem', color: '#334155', margin: 0, lineHeight: 1.4 }}>
                    {selectedAlert.geminiSummary || selectedAlert.message}
                  </p>
                </div>

                {/* 4. RECOMMENDED VERIFICATION */}
                {selectedAlert.recommendedAction && (
                  <div style={{ padding: '0.75rem', borderRadius: '8px', background: '#ffffff', border: '1px solid #e2e8f0', boxShadow: '0 1px 2px rgba(15, 23, 42, 0.02)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', color: '#059669', fontSize: '0.75rem', fontWeight: 700, marginBottom: '0.2rem' }}>
                      <CheckCircle2 size={13} /> RECOMMENDED VERIFICATION
                    </div>
                    <p style={{ fontSize: '0.825rem', color: '#334155', margin: 0, lineHeight: 1.4 }}>
                      {selectedAlert.recommendedAction}
                    </p>
                  </div>
                )}
              </div>

              {/* Section 7: CITIZEN VISUAL OBSERVATION (AUXILIARY EVIDENCE) */}
              {selectedAlert.citizenEvidence && selectedAlert.citizenEvidence.length > 0 ? (
                <div
                  style={{
                    background: '#f8fafc',
                    borderRadius: '10px',
                    padding: '1rem',
                    border: '1px solid #e2e8f0',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '0.75rem',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.4rem' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.75rem', fontWeight: 700, color: '#0284c7', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                      <Camera size={13} />
                      <span>Citizen Visual Observation</span>
                    </div>
                    <div style={{ display: 'flex', gap: '0.35rem', alignItems: 'center' }}>
                      <span
                        style={{
                          padding: '0.15rem 0.5rem',
                          borderRadius: '4px',
                          fontSize: '0.68rem',
                          fontWeight: 700,
                          background: '#fef3c7',
                          color: '#b45309',
                          border: '1px solid #fde68a',
                        }}
                      >
                        AUXILIARY EVIDENCE
                      </span>
                      <span
                        style={{
                          padding: '0.15rem 0.5rem',
                          borderRadius: '4px',
                          fontSize: '0.68rem',
                          fontWeight: 700,
                          background: '#e0f2fe',
                          color: '#0369a1',
                          border: '1px solid #bae6fd',
                        }}
                      >
                        CITIZEN SOURCE
                      </span>
                    </div>
                  </div>

                  <div
                    style={{
                      background: '#fffbeb',
                      border: '1px solid #fef3c7',
                      borderRadius: '6px',
                      padding: '0.5rem 0.65rem',
                      fontSize: '0.75rem',
                      color: '#92400e',
                      lineHeight: 1.4,
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: '0.4rem',
                    }}
                  >
                    <Info size={13} style={{ flexShrink: 0, marginTop: '2px' }} />
                    <span>
                      <strong>Evaluator Notice:</strong> Citizen visual observations provide localized spatial context and are categorized as auxiliary evidence. They are strictly separate from official sensor calibrations, dispersion modeling, and regulatory compliance measurements.
                    </span>
                  </div>

                  {selectedAlert.citizenEvidence.map((obs, idx) => (
                    <div
                      key={obs.evidenceKey || obs.reportId || idx}
                      style={{
                        background: '#ffffff',
                        border: '1px solid #e2e8f0',
                        borderRadius: '8px',
                        padding: '0.85rem',
                        boxShadow: '0 1px 2px rgba(15, 23, 42, 0.02)',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '0.65rem',
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
                        <div>
                          <span style={{ fontSize: '0.68rem', color: '#64748b', textTransform: 'uppercase', fontWeight: 600, display: 'block' }}>
                            Report Reference
                          </span>
                          <span style={{ fontSize: '0.85rem', fontWeight: 700, color: '#0f172a', fontFamily: 'var(--font-mono)' }}>
                            {obs.reportReference || obs.reportId}
                          </span>
                        </div>
                        <div style={{ textAlign: 'right' }}>
                          <span style={{ fontSize: '0.68rem', color: '#64748b', textTransform: 'uppercase', fontWeight: 600, display: 'block' }}>
                            Category
                          </span>
                          <span style={{ fontSize: '0.8rem', fontWeight: 600, color: '#334155' }}>
                            {obs.category || 'GENERAL_POLLUTION'}
                          </span>
                        </div>
                      </div>

                      {obs.description && (
                        <div>
                          <span style={{ fontSize: '0.68rem', color: '#64748b', textTransform: 'uppercase', fontWeight: 600, display: 'block' }}>
                            Citizen Description
                          </span>
                          <p style={{ fontSize: '0.8rem', color: '#1e293b', margin: '0.2rem 0 0', lineHeight: 1.45, fontStyle: 'italic' }}>
                            "{obs.description}"
                          </p>
                        </div>
                      )}

                      <div style={{ display: 'grid', gridTemplateColumns: obs.photoUrl ? '1fr 110px' : '1fr', gap: '0.75rem', alignItems: 'start' }}>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.45rem' }}>
                          <div>
                            <span style={{ fontSize: '0.68rem', color: '#64748b', textTransform: 'uppercase', fontWeight: 600, display: 'block' }}>
                              AI Visual Interpretation
                            </span>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginTop: '0.15rem' }}>
                              <span
                                style={{
                                  padding: '0.15rem 0.45rem',
                                  borderRadius: '4px',
                                  fontSize: '0.75rem',
                                  fontWeight: 700,
                                  background:
                                    obs.visibleCondition === 'SMOKE_LIKE'
                                      ? '#fee2e2'
                                      : obs.visibleCondition === 'BURNING_LIKE'
                                      ? '#ffedd5'
                                      : obs.visibleCondition === 'DUST_LIKE'
                                      ? '#fef3c7'
                                      : '#f1f5f9',
                                  color:
                                    obs.visibleCondition === 'SMOKE_LIKE'
                                      ? '#dc2626'
                                      : obs.visibleCondition === 'BURNING_LIKE'
                                      ? '#c2410c'
                                      : obs.visibleCondition === 'DUST_LIKE'
                                      ? '#b45309'
                                      : '#475569',
                                  border: '1px solid currentColor',
                                }}
                              >
                                {obs.visibleCondition || 'UNKNOWN'}
                              </span>
                              {obs.visualConfidence != null && (
                                <span style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600 }}>
                                  Confidence: <strong style={{ color: '#0f172a' }}>{(obs.visualConfidence * 100).toFixed(0)}%</strong>
                                </span>
                              )}
                            </div>
                          </div>

                          {obs.visualObservations && obs.visualObservations.length > 0 && (
                            <div>
                              <span style={{ fontSize: '0.68rem', color: '#64748b', textTransform: 'uppercase', fontWeight: 600, display: 'block' }}>
                                Visual Observations
                              </span>
                              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.25rem', marginTop: '0.2rem' }}>
                                {obs.visualObservations.map((vObs, vIdx) => (
                                  <span
                                    key={vIdx}
                                    style={{
                                      fontSize: '0.72rem',
                                      padding: '0.1rem 0.4rem',
                                      borderRadius: '4px',
                                      background: '#f1f5f9',
                                      color: '#334155',
                                      border: '1px solid #e2e8f0',
                                    }}
                                  >
                                    {vObs}
                                  </span>
                                ))}
                              </div>
                            </div>
                          )}

                          {obs.visualUncertainty && obs.visualUncertainty.length > 0 && (
                            <div>
                              <span style={{ fontSize: '0.68rem', color: '#64748b', textTransform: 'uppercase', fontWeight: 600, display: 'block' }}>
                                Visual Uncertainty
                              </span>
                              <div style={{ fontSize: '0.72rem', color: '#64748b', fontStyle: 'italic', marginTop: '0.15rem' }}>
                                {obs.visualUncertainty.join(' • ')}
                              </div>
                            </div>
                          )}
                        </div>

                        {obs.photoUrl && (
                          <div style={{ textAlign: 'center' }}>
                            {imgErrorMap[obs.reportId] ? (
                              <div
                                style={{
                                  width: '110px',
                                  height: '85px',
                                  borderRadius: '6px',
                                  border: '1px dashed #cbd5e1',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  background: '#f8fafc',
                                  fontSize: '0.65rem',
                                  color: '#64748b',
                                  textAlign: 'center',
                                  padding: '0.25rem',
                                }}
                              >
                                Photo preview unavailable
                              </div>
                            ) : (
                              <a
                                href={obs.photoUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                title="Click to view full photo"
                                style={{ display: 'block' }}
                              >
                                <img
                                  src={obs.photoUrl}
                                  alt="Citizen observation ground photo"
                                  onError={() => setImgErrorMap((prev) => ({ ...prev, [obs.reportId]: true }))}
                                  style={{
                                    width: '110px',
                                    height: '85px',
                                    objectFit: 'cover',
                                    borderRadius: '6px',
                                    border: '1px solid #cbd5e1',
                                    boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
                                  }}
                                />
                              </a>
                            )}
                            <span style={{ fontSize: '0.65rem', color: '#64748b', display: 'block', marginTop: '0.2rem' }}>
                              Ground Photo
                            </span>
                          </div>
                        )}
                      </div>

                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderTop: '1px solid #f1f5f9', paddingTop: '0.4rem', fontSize: '0.7rem', color: '#64748b' }}>
                        <span>Observed: {new Date(obs.observedAt).toLocaleString()}</span>
                        <span style={{ fontFamily: 'var(--font-mono)' }}>Key: {obs.evidenceKey?.substring(0, 24)}...</span>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div
                  style={{
                    background: '#f8fafc',
                    borderRadius: '10px',
                    padding: '1rem',
                    border: '1px solid #e2e8f0',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                    <Camera size={13} />
                    <span>Citizen Visual Observation &middot; Auxiliary Evidence</span>
                  </div>
                  <p style={{ fontSize: '0.8rem', color: '#64748b', margin: '0.5rem 0 0', fontStyle: 'italic' }}>
                    No citizen observation attached.
                  </p>
                </div>
              )}

              {/* Section 9: Direct Link to Unified Evidence & WHY Dossier */}
              <Link
                to={`/analyst/evidence?h3=${encodeURIComponent(selectedAlert.h3Index)}`}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '0.5rem',
                  padding: '0.7rem 1rem',
                  borderRadius: '8px',
                  background: '#f0f9ff',
                  border: '1px solid #0284c7',
                  color: '#0284c7',
                  textDecoration: 'none',
                  fontSize: '0.825rem',
                  fontWeight: 700,
                  transition: 'all 0.15s ease',
                  boxShadow: '0 1px 2px rgba(2, 132, 199, 0.08)',
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.background = '#e0f2fe';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.background = '#f0f9ff';
                }}
              >
                <span>Inspect Full Evidence & Gemini WHY</span>
                <ArrowRight size={14} />
              </Link>

              {/* Section 21.5: Authoritative Lifecycle Audit History Card */}
              <div
                data-testid="audit-history-card"
                style={{
                  background: '#ffffff',
                  borderRadius: '10px',
                  padding: '1rem',
                  border: '1px solid #e2e8f0',
                  boxShadow: '0 1px 3px rgba(15, 23, 42, 0.03)',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem', fontSize: '0.75rem', fontWeight: 700, color: '#0f172a', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                    <History size={14} color="#4f46e5" />
                    <span>Authoritative Lifecycle Audit History</span>
                  </div>
                  <span
                    style={{
                      padding: '0.15rem 0.5rem',
                      borderRadius: '9999px',
                      fontSize: '0.68rem',
                      fontWeight: 700,
                      background: '#eff6ff',
                      color: '#1d4ed8',
                      border: '1px solid #bfdbfe',
                    }}
                  >
                    {actions.length} Action{actions.length === 1 ? '' : 's'}
                  </span>
                </div>

                {loadingActions ? (
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#64748b', fontSize: '0.8rem', padding: '0.5rem 0' }}>
                    <RefreshCw size={13} className="animate-spin" />
                    <span>Loading audit history...</span>
                  </div>
                ) : actions.length === 0 ? (
                  <p style={{ fontSize: '0.8rem', color: '#64748b', margin: 0, fontStyle: 'italic' }}>
                    No operational actions recorded yet for this alert candidate.
                  </p>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                    {actions.map((act) => (
                      <div
                        key={act.id || act.actionId}
                        data-testid={`audit-action-${act.id || act.actionId}`}
                        style={{
                          background: '#f8fafc',
                          border: '1px solid #e2e8f0',
                          borderRadius: '8px',
                          padding: '0.65rem 0.8rem',
                          display: 'flex',
                          flexDirection: 'column',
                          gap: '0.35rem',
                        }}
                      >
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.35rem' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                            <span
                              style={{
                                padding: '0.15rem 0.45rem',
                                borderRadius: '4px',
                                fontSize: '0.68rem',
                                fontWeight: 800,
                                background: '#ede9fe',
                                color: '#6d28d9',
                                border: '1px solid #ddd6fe',
                                textTransform: 'uppercase',
                              }}
                            >
                              {act.actionType}
                            </span>
                            {act.previousStatus && act.newStatus && (
                              <span style={{ fontSize: '0.7rem', fontWeight: 600, color: '#475569' }}>
                                {act.previousStatus} &rarr; <strong style={{ color: '#0f172a' }}>{act.newStatus}</strong>
                              </span>
                            )}
                          </div>
                          <span style={{ fontSize: '0.68rem', color: '#64748b' }}>
                            {new Date(act.performedAt).toLocaleString()}
                          </span>
                        </div>

                        {act.actionDetails && (
                          <div style={{ fontSize: '0.78rem', color: '#1e293b', lineHeight: 1.4 }}>
                            {act.actionDetails}
                          </div>
                        )}

                        {act.reason && (
                          <div
                            style={{
                              fontSize: '0.72rem',
                              color: '#991b1b',
                              background: '#fef2f2',
                              padding: '0.25rem 0.5rem',
                              borderRadius: '4px',
                              border: '1px solid #fecaca',
                            }}
                          >
                            <strong>Reason:</strong> {act.reason}
                          </div>
                        )}

                        <div style={{ display: 'flex', justifyContent: 'flex-end', fontSize: '0.65rem', color: '#64748b' }}>
                          <span>Actor: <strong>{act.performedBy || 'System/Authority'}</strong></span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Section 22: Authority Lifecycle Actions */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', borderTop: '1px solid #e2e8f0', paddingTop: '1rem' }}>
                {actionError && (
                  <div
                    data-testid="authority-action-error"
                    style={{
                      background: '#fef2f2',
                      border: '1px solid #fecaca',
                      color: '#991b1b',
                      borderRadius: '8px',
                      padding: '0.65rem 0.85rem',
                      fontSize: '0.8rem',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: '0.5rem',
                    }}
                  >
                    <span>{actionError}</span>
                    <button
                      type="button"
                      onClick={() => setActionError(null)}
                      style={{ background: 'transparent', border: 'none', color: '#991b1b', cursor: 'pointer', fontWeight: 700 }}
                    >
                      &times;
                    </button>
                  </div>
                )}

                <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
                  {/* Analyst Read-Only Mode Banner */}
                  {isAnalystReadOnly && (
                    <div
                      data-testid="banner-analyst-readonly"
                      style={{
                        width: '100%',
                        padding: '0.75rem 1rem',
                        borderRadius: '8px',
                        background: '#fffbeb',
                        border: '1px solid #fde68a',
                        color: '#92400e',
                        fontSize: '0.8rem',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '0.5rem',
                      }}
                    >
                      <Info size={16} style={{ flexShrink: 0 }} />
                      <span>
                        <strong>Analyst Read-Only Mode:</strong> Operational lifecycle mutations (acknowledgements, assignments, field actions, dismissals, resolutions) are restricted to municipal Authority Officers and Administrators.
                      </span>
                    </div>
                  )}

                  {/* DISMISSED State Banner */}
                  {(currentEventStatus === 'DISMISSED' || selectedAlert.status === 'DISMISSED') && (
                    <div
                      data-testid="banner-dismissed"
                      style={{
                        width: '100%',
                        padding: '0.85rem',
                        borderRadius: '8px',
                        background: '#fef2f2',
                        border: '1px solid #fecaca',
                        color: '#991b1b',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '0.35rem',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem', fontWeight: 800, fontSize: '0.85rem' }}>
                        <XCircle size={16} />
                        Candidate Dismissed from Operational Queue
                      </div>
                      {selectedAlert.dismissalReason && (
                        <div style={{ fontSize: '0.78rem', color: '#b91c1c' }}>
                          Reason: <strong>{selectedAlert.dismissalReason}</strong>
                        </div>
                      )}
                      {selectedAlert.dismissedAt && (
                        <div style={{ fontSize: '0.7rem', color: '#7f1d1d' }}>
                          Dismissed at: {new Date(selectedAlert.dismissedAt).toLocaleString()}
                        </div>
                      )}
                    </div>
                  )}

                  {/* RESOLVED State Banner */}
                  {(currentEventStatus === 'RESOLVED' || selectedAlert.status === 'RESOLVED') && (
                    <div
                      data-testid="banner-resolved"
                      style={{
                        width: '100%',
                        padding: '0.85rem',
                        borderRadius: '8px',
                        background: '#ecfdf5',
                        border: '1px solid #a7f3d0',
                        color: '#065f46',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '0.35rem',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem', fontWeight: 800, fontSize: '0.85rem' }}>
                        <CheckCircle size={16} color="#059669" />
                        Event &amp; Alert Resolved &middot; Lifecycle Complete
                      </div>
                      {selectedAlert.resolutionNotes && (
                        <div style={{ fontSize: '0.78rem', color: '#047857' }}>
                          Resolution Notes: <strong>{selectedAlert.resolutionNotes}</strong>
                        </div>
                      )}
                      {selectedAlert.resolvedAt && (
                        <div style={{ fontSize: '0.7rem', color: '#065f46' }}>
                          Resolved at: {new Date(selectedAlert.resolvedAt).toLocaleString()}
                        </div>
                      )}
                    </div>
                  )}

                  {/* Context Actions for OPEN state */}
                  {!isAnalystReadOnly && currentEventStatus === 'OPEN' && selectedAlert.status !== 'RESOLVED' && selectedAlert.status !== 'DISMISSED' && (
                    <>
                      {selectedAlert.status === 'OPEN' && (
                        <button
                          type="button"
                          data-testid="btn-acknowledge"
                          onClick={() => handleAcknowledge(selectedAlert.alertId)}
                          disabled={actionInProgress}
                          style={{
                            flex: 1,
                            minWidth: '150px',
                            padding: '0.65rem 1rem',
                            borderRadius: '8px',
                            background: '#0284c7',
                            color: '#ffffff',
                            border: 'none',
                            fontWeight: 700,
                            fontSize: '0.85rem',
                            cursor: actionInProgress ? 'not-allowed' : 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: '0.4rem',
                            boxShadow: '0 2px 4px rgba(2, 132, 199, 0.25)',
                          }}
                        >
                          {actionInProgress ? <RefreshCw size={14} className="animate-spin" /> : <ShieldCheck size={15} />}
                          Acknowledge Alert
                        </button>
                      )}

                      <button
                        type="button"
                        data-testid="btn-assign-team"
                        onClick={() => setShowInspectionModal(true)}
                        disabled={actionInProgress}
                        style={{
                          flex: 1,
                          minWidth: '150px',
                          padding: '0.65rem 1rem',
                          borderRadius: '8px',
                          background: '#4f46e5',
                          color: '#ffffff',
                          border: 'none',
                          fontWeight: 700,
                          fontSize: '0.85rem',
                          cursor: actionInProgress ? 'not-allowed' : 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          gap: '0.4rem',
                          boxShadow: '0 2px 4px rgba(79, 70, 229, 0.25)',
                        }}
                      >
                        <Users size={15} />
                        Assign Field Team
                      </button>

                      <button
                        type="button"
                        data-testid="btn-dismiss-open"
                        onClick={() => setShowDismissModal(true)}
                        disabled={actionInProgress}
                        style={{
                          padding: '0.65rem 1rem',
                          borderRadius: '8px',
                          background: '#ffffff',
                          color: '#dc2626',
                          border: '1px solid #fca5a5',
                          fontWeight: 700,
                          fontSize: '0.85rem',
                          cursor: actionInProgress ? 'not-allowed' : 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          gap: '0.4rem',
                        }}
                      >
                        <XCircle size={15} />
                        Dismiss Candidate
                      </button>
                    </>
                  )}

                  {/* Context Actions for ASSIGNED state */}
                  {!isAnalystReadOnly && currentEventStatus === 'ASSIGNED' && selectedAlert.status !== 'RESOLVED' && selectedAlert.status !== 'DISMISSED' && (
                    <>
                      <button
                        type="button"
                        data-testid="btn-start-inspection"
                        onClick={handleStartInspectionDirect}
                        disabled={actionInProgress}
                        style={{
                          flex: 1,
                          minWidth: '160px',
                          padding: '0.65rem 1rem',
                          borderRadius: '8px',
                          background: '#0284c7',
                          color: '#ffffff',
                          border: 'none',
                          fontWeight: 700,
                          fontSize: '0.85rem',
                          cursor: actionInProgress ? 'not-allowed' : 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          gap: '0.4rem',
                          boxShadow: '0 2px 4px rgba(2, 132, 199, 0.25)',
                        }}
                      >
                        {actionInProgress ? <RefreshCw size={14} className="animate-spin" /> : <Play size={15} />}
                        Start Inspection
                      </button>

                      <button
                        type="button"
                        data-testid="btn-reassign-team"
                        onClick={openReassignModal}
                        disabled={actionInProgress}
                        style={{
                          padding: '0.65rem 1rem',
                          borderRadius: '8px',
                          background: '#f8fafc',
                          color: '#4338ca',
                          border: '1px solid #c7d2fe',
                          fontWeight: 700,
                          fontSize: '0.85rem',
                          cursor: actionInProgress ? 'not-allowed' : 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          gap: '0.4rem',
                        }}
                      >
                        <Users size={15} />
                        Reassign Team
                      </button>

                      <button
                        type="button"
                        data-testid="btn-manage-inspection"
                        onClick={() => setShowInspectionModal(true)}
                        disabled={actionInProgress}
                        style={{
                          flex: 1,
                          minWidth: '150px',
                          padding: '0.65rem 1rem',
                          borderRadius: '8px',
                          background: '#eff6ff',
                          color: '#1d4ed8',
                          border: '1px solid #bfdbfe',
                          fontWeight: 700,
                          fontSize: '0.85rem',
                          cursor: actionInProgress ? 'not-allowed' : 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          gap: '0.4rem',
                        }}
                      >
                        <Users size={15} />
                        Manage Field Team
                      </button>

                      <button
                        type="button"
                        data-testid="btn-dismiss-assigned"
                        onClick={() => setShowDismissModal(true)}
                        disabled={actionInProgress}
                        style={{
                          padding: '0.65rem 1rem',
                          borderRadius: '8px',
                          background: '#ffffff',
                          color: '#dc2626',
                          border: '1px solid #fca5a5',
                          fontWeight: 700,
                          fontSize: '0.85rem',
                          cursor: actionInProgress ? 'not-allowed' : 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          gap: '0.4rem',
                        }}
                      >
                        <XCircle size={15} />
                        Dismiss
                      </button>
                    </>
                  )}

                  {/* Context Actions for IN_INSPECTION state */}
                  {!isAnalystReadOnly && currentEventStatus === 'IN_INSPECTION' && selectedAlert.status !== 'RESOLVED' && selectedAlert.status !== 'DISMISSED' && (
                    <>
                      <button
                        type="button"
                        data-testid="btn-record-action"
                        onClick={() => setShowActionModal(true)}
                        disabled={actionInProgress}
                        style={{
                          flex: 1,
                          minWidth: '160px',
                          padding: '0.65rem 1rem',
                          borderRadius: '8px',
                          background: '#4f46e5',
                          color: '#ffffff',
                          border: 'none',
                          fontWeight: 700,
                          fontSize: '0.85rem',
                          cursor: actionInProgress ? 'not-allowed' : 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          gap: '0.4rem',
                          boxShadow: '0 2px 4px rgba(79, 70, 229, 0.25)',
                        }}
                      >
                        <ClipboardList size={15} />
                        Record Mitigation Action
                      </button>

                      <button
                        type="button"
                        data-testid="btn-reassign-team-inspection"
                        onClick={openReassignModal}
                        disabled={actionInProgress}
                        style={{
                          padding: '0.65rem 1rem',
                          borderRadius: '8px',
                          background: '#f8fafc',
                          color: '#4338ca',
                          border: '1px solid #c7d2fe',
                          fontWeight: 700,
                          fontSize: '0.85rem',
                          cursor: actionInProgress ? 'not-allowed' : 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          gap: '0.4rem',
                        }}
                      >
                        <Users size={15} />
                        Reassign Team
                      </button>

                      <button
                        type="button"
                        data-testid="btn-ground-verification"
                        onClick={() => setShowInspectionModal(true)}
                        disabled={actionInProgress}
                        style={{
                          flex: 1,
                          minWidth: '160px',
                          padding: '0.65rem 1rem',
                          borderRadius: '8px',
                          background: '#eff6ff',
                          color: '#1d4ed8',
                          border: '1px solid #bfdbfe',
                          fontWeight: 700,
                          fontSize: '0.85rem',
                          cursor: actionInProgress ? 'not-allowed' : 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          gap: '0.4rem',
                        }}
                      >
                        <ShieldCheck size={15} />
                        Ground Verification
                      </button>
                    </>
                  )}

                  {/* Context Actions for ACTION_TAKEN state */}
                  {!isAnalystReadOnly && currentEventStatus === 'ACTION_TAKEN' && selectedAlert.status !== 'RESOLVED' && selectedAlert.status !== 'DISMISSED' && (
                    <>
                      <button
                        type="button"
                        data-testid="btn-resolve-event"
                        onClick={() => setShowResolveModal(true)}
                        disabled={actionInProgress}
                        style={{
                          flex: 1,
                          minWidth: '160px',
                          padding: '0.65rem 1rem',
                          borderRadius: '8px',
                          background: '#059669',
                          color: '#ffffff',
                          border: 'none',
                          fontWeight: 700,
                          fontSize: '0.85rem',
                          cursor: actionInProgress ? 'not-allowed' : 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          gap: '0.4rem',
                          boxShadow: '0 2px 4px rgba(5, 150, 105, 0.25)',
                        }}
                      >
                        <CheckCircle size={15} />
                        Resolve Event &amp; Alert
                      </button>

                      <button
                        type="button"
                        data-testid="btn-record-action-additional"
                        onClick={() => setShowActionModal(true)}
                        disabled={actionInProgress}
                        style={{
                          flex: 1,
                          minWidth: '160px',
                          padding: '0.65rem 1rem',
                          borderRadius: '8px',
                          background: '#eff6ff',
                          color: '#1d4ed8',
                          border: '1px solid #bfdbfe',
                          fontWeight: 700,
                          fontSize: '0.85rem',
                          cursor: actionInProgress ? 'not-allowed' : 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          gap: '0.4rem',
                        }}
                      >
                        <PlusCircle size={15} />
                        Log Additional Action
                      </button>
                    </>
                  )}
                </div>
              </div>
            </div>
          ) : (
            <div
              style={{
                background: '#ffffff',
                borderRadius: '14px',
                border: '1px solid #e2e8f0',
                padding: '2.5rem',
                textAlign: 'center',
                boxShadow: '0 2px 8px rgba(15, 23, 42, 0.05)',
              }}
            >
              <Info size={32} color="#64748b" style={{ margin: '0 auto 0.75rem' }} />
              <h4 style={{ fontSize: '1.05rem', fontWeight: 700, color: '#0f172a', marginBottom: '0.4rem' }}>
                No Alert Selected
              </h4>
              <p style={{ fontSize: '0.825rem', color: '#64748b', margin: 0 }}>
                Alert candidate not found or no alert currently selected. Please choose an alert from the operational queue.
              </p>
            </div>
          )}
        </div>
      )}

      {/* Field Team Assignment & Verification Modal */}
      {showInspectionModal && selectedAlert && (
        <div
          data-testid="modal-inspection"
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.55)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '1rem',
          }}
        >
          <div style={{ maxWidth: '640px', width: '100%', maxHeight: '90vh', overflowY: 'auto' }}>
            <InspectionForm
              alertId={selectedAlert.alertId}
              alertTitle={selectedAlert.title}
              eventCode={selectedAlert.eventCode}
              h3Index={selectedAlert.h3Index}
              onAssignmentSuccess={() => {
                refreshAllData();
              }}
              onVerificationSuccess={() => {
                refreshAllData();
              }}
              onClose={() => setShowInspectionModal(false)}
            />
          </div>
        </div>
      )}

      {/* Record Authority Action Modal */}
      {showActionModal && selectedAlert && (
        <div
          data-testid="modal-record-action"
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.55)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '1rem',
          }}
        >
          <div
            style={{
              maxWidth: '520px',
              width: '100%',
              background: '#ffffff',
              borderRadius: '12px',
              padding: '1.5rem',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <ClipboardList size={18} color="#4f46e5" />
                <h4 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 800, color: '#0f172a' }}>
                  Record Authority Action
                </h4>
              </div>
              <button
                type="button"
                onClick={() => setShowActionModal(false)}
                style={{ background: 'transparent', border: 'none', cursor: 'pointer', fontSize: '1.25rem', color: '#64748b' }}
              >
                &times;
              </button>
            </div>

            <form onSubmit={handleRecordAction} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 700, color: '#334155', marginBottom: '0.35rem' }}>
                  Action Type
                </label>
                <select
                  data-testid="select-action-type"
                  value={selectedActionType}
                  onChange={(e) => setSelectedActionType(e.target.value as any)}
                  style={{
                    width: '100%',
                    padding: '0.6rem',
                    borderRadius: '6px',
                    border: '1px solid #cbd5e1',
                    fontSize: '0.85rem',
                    color: '#0f172a',
                    background: '#ffffff',
                  }}
                >
                  <option value="FIELD_VERIFICATION">Field Verification</option>
                  <option value="MOBILE_SENSOR_DEPLOYED">Mobile Sensor Deployed</option>
                  <option value="SITE_CHECK">Site Check / Inspection</option>
                  <option value="ADVISORY_ISSUED">Public Advisory Issued</option>
                  <option value="OTHER">Other Operational Action</option>
                </select>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 700, color: '#334155', marginBottom: '0.35rem' }}>
                  Action Details &amp; Operational Notes *
                </label>
                <textarea
                  data-testid="textarea-action-notes"
                  rows={4}
                  value={actionNotes}
                  onChange={(e) => setActionNotes(e.target.value)}
                  placeholder="Describe the operational action, findings, sensor reading, or regulatory measure taken..."
                  style={{
                    width: '100%',
                    padding: '0.6rem',
                    borderRadius: '6px',
                    border: '1px solid #cbd5e1',
                    fontSize: '0.825rem',
                    color: '#0f172a',
                    fontFamily: 'inherit',
                    resize: 'vertical',
                  }}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.65rem', marginTop: '0.5rem' }}>
                <button
                  type="button"
                  onClick={() => setShowActionModal(false)}
                  disabled={actionInProgress}
                  style={{
                    padding: '0.6rem 1rem',
                    borderRadius: '6px',
                    background: '#f1f5f9',
                    border: '1px solid #cbd5e1',
                    color: '#475569',
                    fontWeight: 600,
                    fontSize: '0.85rem',
                    cursor: 'pointer',
                  }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  data-testid="btn-submit-record-action"
                  disabled={actionInProgress || !actionNotes.trim()}
                  style={{
                    padding: '0.6rem 1.25rem',
                    borderRadius: '6px',
                    background: '#4f46e5',
                    border: 'none',
                    color: '#ffffff',
                    fontWeight: 700,
                    fontSize: '0.85rem',
                    cursor: actionInProgress || !actionNotes.trim() ? 'not-allowed' : 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.4rem',
                  }}
                >
                  {actionInProgress && <RefreshCw size={14} className="animate-spin" />}
                  Record Action
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Resolve Event Modal */}
      {showResolveModal && selectedAlert && (
        <div
          data-testid="modal-resolve-event"
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.55)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '1rem',
          }}
        >
          <div
            style={{
              maxWidth: '520px',
              width: '100%',
              background: '#ffffff',
              borderRadius: '12px',
              padding: '1.5rem',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <CheckCircle size={18} color="#059669" />
                <h4 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 800, color: '#0f172a' }}>
                  Resolve Potential Pollution Event
                </h4>
              </div>
              <button
                type="button"
                onClick={() => setShowResolveModal(false)}
                style={{ background: 'transparent', border: 'none', cursor: 'pointer', fontSize: '1.25rem', color: '#64748b' }}
              >
                &times;
              </button>
            </div>

            <p style={{ fontSize: '0.8rem', color: '#475569', marginBottom: '1rem' }}>
              Resolving this candidate will transition the parent Pollution Event and Alert to <strong>RESOLVED</strong>.
              Resolution requires formal documentation of mitigation outcomes.
            </p>

            <form onSubmit={handleResolveConfirm} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 700, color: '#334155', marginBottom: '0.35rem' }}>
                  Resolution Notes *
                </label>
                <textarea
                  data-testid="textarea-resolution-notes"
                  rows={4}
                  value={resolutionNotes}
                  onChange={(e) => setResolutionNotes(e.target.value)}
                  placeholder="State final containment confirmation, emissions reduction, regulatory compliance or closeout summary..."
                  style={{
                    width: '100%',
                    padding: '0.6rem',
                    borderRadius: '6px',
                    border: '1px solid #cbd5e1',
                    fontSize: '0.825rem',
                    color: '#0f172a',
                    fontFamily: 'inherit',
                    resize: 'vertical',
                  }}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.65rem', marginTop: '0.5rem' }}>
                <button
                  type="button"
                  onClick={() => setShowResolveModal(false)}
                  disabled={actionInProgress}
                  style={{
                    padding: '0.6rem 1rem',
                    borderRadius: '6px',
                    background: '#f1f5f9',
                    border: '1px solid #cbd5e1',
                    color: '#475569',
                    fontWeight: 600,
                    fontSize: '0.85rem',
                    cursor: 'pointer',
                  }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  data-testid="btn-submit-resolve-confirm"
                  disabled={actionInProgress || !resolutionNotes.trim()}
                  style={{
                    padding: '0.6rem 1.25rem',
                    borderRadius: '6px',
                    background: '#059669',
                    border: 'none',
                    color: '#ffffff',
                    fontWeight: 700,
                    fontSize: '0.85rem',
                    cursor: actionInProgress || !resolutionNotes.trim() ? 'not-allowed' : 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.4rem',
                  }}
                >
                  {actionInProgress && <RefreshCw size={14} className="animate-spin" />}
                  Confirm Resolution
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Dismiss Event Modal */}
      {showDismissModal && selectedAlert && (
        <div
          data-testid="modal-dismiss-event"
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.55)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '1rem',
          }}
        >
          <div
            style={{
              maxWidth: '520px',
              width: '100%',
              background: '#ffffff',
              borderRadius: '12px',
              padding: '1.5rem',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <XCircle size={18} color="#dc2626" />
                <h4 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 800, color: '#0f172a' }}>
                  Dismiss Alert Candidate
                </h4>
              </div>
              <button
                type="button"
                onClick={() => setShowDismissModal(false)}
                style={{ background: 'transparent', border: 'none', cursor: 'pointer', fontSize: '1.25rem', color: '#64748b' }}
              >
                &times;
              </button>
            </div>

            <p style={{ fontSize: '0.8rem', color: '#475569', marginBottom: '1rem' }}>
              Dismissing this candidate will mark the event and alert as <strong>DISMISSED</strong> and remove it from
              active operational dispatch. An authoritative reason is required.
            </p>

            <form onSubmit={handleDismissConfirm} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 700, color: '#334155', marginBottom: '0.35rem' }}>
                  Dismissal Reason *
                </label>
                <textarea
                  data-testid="textarea-dismissal-reason"
                  rows={4}
                  value={dismissalReason}
                  onChange={(e) => setDismissalReason(e.target.value)}
                  placeholder="State reason for dismissal (e.g. false anomaly, permitted agricultural burn, sensor transient)..."
                  style={{
                    width: '100%',
                    padding: '0.6rem',
                    borderRadius: '6px',
                    border: '1px solid #cbd5e1',
                    fontSize: '0.825rem',
                    color: '#0f172a',
                    fontFamily: 'inherit',
                    resize: 'vertical',
                  }}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.65rem', marginTop: '0.5rem' }}>
                <button
                  type="button"
                  onClick={() => setShowDismissModal(false)}
                  disabled={actionInProgress}
                  style={{
                    padding: '0.6rem 1rem',
                    borderRadius: '6px',
                    background: '#f1f5f9',
                    border: '1px solid #cbd5e1',
                    color: '#475569',
                    fontWeight: 600,
                    fontSize: '0.85rem',
                    cursor: 'pointer',
                  }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  data-testid="btn-submit-dismiss-confirm"
                  disabled={actionInProgress || !dismissalReason.trim()}
                  style={{
                    padding: '0.6rem 1.25rem',
                    borderRadius: '6px',
                    background: '#dc2626',
                    border: 'none',
                    color: '#ffffff',
                    fontWeight: 700,
                    fontSize: '0.85rem',
                    cursor: actionInProgress || !dismissalReason.trim() ? 'not-allowed' : 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.4rem',
                  }}
                >
                  {actionInProgress && <RefreshCw size={14} className="animate-spin" />}
                  Confirm Dismissal
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
      {/* Reassign Field Team Modal */}
      {showReassignModal && selectedAlert && (
        <div
          data-testid="modal-reassign-team"
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.55)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '1rem',
          }}
        >
          <div
            style={{
              maxWidth: '520px',
              width: '100%',
              background: '#ffffff',
              borderRadius: '12px',
              padding: '1.5rem',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Users size={18} color="#4338ca" />
                <h4 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 800, color: '#0f172a' }}>
                  Reassign Field Team
                </h4>
              </div>
              <button
                type="button"
                onClick={() => setShowReassignModal(false)}
                style={{ background: 'transparent', border: 'none', cursor: 'pointer', fontSize: '1.25rem', color: '#64748b' }}
              >
                &times;
              </button>
            </div>

            <p style={{ fontSize: '0.8rem', color: '#475569', marginBottom: '1rem' }}>
              Reassign this incident to another response team. A documented operational reason is required for full audit provenance.
            </p>

            <form onSubmit={handleReassignConfirm} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 700, color: '#334155', marginBottom: '0.35rem' }}>
                  Replacement Field Team *
                </label>
                {loadingTeams ? (
                  <div style={{ fontSize: '0.8rem', color: '#64748b' }}>Loading field teams...</div>
                ) : (
                  <select
                    data-testid="select-reassign-team"
                    value={reassignTeamId}
                    onChange={(e) => setReassignTeamId(e.target.value)}
                    style={{
                      width: '100%',
                      padding: '0.6rem',
                      borderRadius: '6px',
                      border: '1px solid #cbd5e1',
                      fontSize: '0.85rem',
                      color: '#0f172a',
                      background: '#ffffff',
                    }}
                  >
                    {fieldTeams.map((team) => (
                      <option key={team.id} value={team.id}>
                        {team.teamName} ({team.status || 'AVAILABLE'}) &middot; {team.contactNumber || 'Squad'}
                      </option>
                    ))}
                  </select>
                )}
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 700, color: '#334155', marginBottom: '0.35rem' }}>
                  Mandatory Reassignment Reason *
                </label>
                <textarea
                  data-testid="textarea-reassign-reason"
                  rows={3}
                  value={reassignReason}
                  onChange={(e) => setReassignReason(e.target.value)}
                  placeholder="e.g. Initial squad assigned to chemical containment, specialized sensor required, geographic proximity..."
                  style={{
                    width: '100%',
                    padding: '0.6rem',
                    borderRadius: '6px',
                    border: '1px solid #cbd5e1',
                    fontSize: '0.825rem',
                    color: '#0f172a',
                    fontFamily: 'inherit',
                    resize: 'vertical',
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 700, color: '#334155', marginBottom: '0.35rem' }}>
                  Additional Operational Notes
                </label>
                <input
                  type="text"
                  data-testid="input-reassign-notes"
                  value={reassignNotes}
                  onChange={(e) => setReassignNotes(e.target.value)}
                  placeholder="Optional dispatch coordinates, priority handoff instructions..."
                  style={{
                    width: '100%',
                    padding: '0.55rem 0.75rem',
                    borderRadius: '6px',
                    border: '1px solid #cbd5e1',
                    fontSize: '0.825rem',
                    color: '#0f172a',
                  }}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.65rem', marginTop: '0.5rem' }}>
                <button
                  type="button"
                  onClick={() => setShowReassignModal(false)}
                  disabled={actionInProgress}
                  style={{
                    padding: '0.6rem 1rem',
                    borderRadius: '6px',
                    background: '#f1f5f9',
                    border: '1px solid #cbd5e1',
                    color: '#475569',
                    fontWeight: 600,
                    fontSize: '0.85rem',
                    cursor: 'pointer',
                  }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  data-testid="btn-submit-reassign-confirm"
                  disabled={actionInProgress || !reassignReason.trim() || !reassignTeamId}
                  style={{
                    padding: '0.6rem 1.25rem',
                    borderRadius: '6px',
                    background: '#4338ca',
                    border: 'none',
                    color: '#ffffff',
                    fontWeight: 700,
                    fontSize: '0.85rem',
                    cursor: actionInProgress || !reassignReason.trim() || !reassignTeamId ? 'not-allowed' : 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.4rem',
                  }}
                >
                  {actionInProgress && <RefreshCw size={14} className="animate-spin" />}
                  Confirm Reassignment
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </PageContainer>
  );
};

export default Alerts;
