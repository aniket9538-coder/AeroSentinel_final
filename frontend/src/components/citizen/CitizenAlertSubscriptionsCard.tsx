import React, { useState, useEffect } from 'react';
import {
  Bell,
  Plus,
  Trash2,
  CheckCircle2,
  AlertTriangle,
  ShieldAlert,
  Clock,
  Radio,
  ExternalLink,
} from 'lucide-react';
import {
  AlertSubscription,
  InAppNotification,
  SavedLocation,
} from '../../types/citizenPreferences';
import {
  fetchAlertSubscriptions,
  createAlertSubscription,
  deleteAlertSubscription,
  fetchInAppNotifications,
  markNotificationAsRead,
  evaluateAlertSubscriptions,
} from '../../services/citizenPreferencesApi';
import { getTranslation } from '../../i18n/citizenTranslations';

interface CitizenAlertSubscriptionsCardProps {
  currentLocale?: string;
  savedLocations?: SavedLocation[];
  currentCityName?: string;
}

export const CitizenAlertSubscriptionsCard: React.FC<CitizenAlertSubscriptionsCardProps> = ({
  currentLocale = 'en',
  savedLocations = [],
  currentCityName = 'Pune',
}) => {
  const t = getTranslation(currentLocale);

  const [subscriptions, setSubscriptions] = useState<AlertSubscription[]>([]);
  const [notifications, setNotifications] = useState<InAppNotification[]>([]);
  const [loading, setLoading] = useState(true);
  const [evaluating, setEvaluating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [evalNotice, setEvalNotice] = useState<string | null>(null);

  // New subscription form
  const [isAdding, setIsAdding] = useState(false);
  const [pollutant, setPollutant] = useState('PM25');
  const [threshold, setThreshold] = useState('60');
  const [selectedLocationId, setSelectedLocationId] = useState<string>('');
  const [saving, setSaving] = useState(false);

  const loadData = async () => {
    try {
      setError(null);
      const [subs, notifs] = await Promise.all([
        fetchAlertSubscriptions(),
        fetchInAppNotifications(),
      ]);
      setSubscriptions(subs);
      setNotifications(notifs);
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to load alert subscriptions.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    const val = parseFloat(threshold);
    if (isNaN(val) || val <= 0) {
      setError('Threshold value must be greater than zero.');
      return;
    }

    setSaving(true);
    try {
      await createAlertSubscription({
        cityName: currentCityName,
        pollutant,
        thresholdValue: val,
        locationId: selectedLocationId || undefined,
        deliveryChannel: 'IN_APP',
      });
      setIsAdding(false);
      await loadData();
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to create alert subscription.');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteAlertSubscription(id);
      await loadData();
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to remove subscription.');
    }
  };

  const handleMarkRead = async (id: string) => {
    try {
      await markNotificationAsRead(id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, readStatus: true } : n))
      );
    } catch (err: any) {
      console.error('Failed to mark notification read:', err);
    }
  };

  const handleEvaluate = async () => {
    setEvaluating(true);
    setEvalNotice(null);
    try {
      const res = await evaluateAlertSubscriptions(currentCityName);
      setEvalNotice(`Evaluated ${res.subscriptionsEvaluated} active subscriptions: ${res.notificationsTriggered} triggered alerts.`);
      await loadData();
    } catch (err: any) {
      setError('Evaluation failed: ' + (err.response?.data?.message || err.message));
    } finally {
      setEvaluating(false);
    }
  };

  const unreadCount = notifications.filter((n) => !n.readStatus).length;

  return (
    <div
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border-medium)',
        borderRadius: '1rem',
        padding: '1.5rem',
        boxShadow: 'var(--shadow-sm)',
        display: 'flex',
        flexDirection: 'column',
        gap: '1rem',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
        <div>
          <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 700, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Bell size={18} style={{ color: 'var(--brand-primary)' }} />
            {t.alertSubscriptionsTitle}
            {unreadCount > 0 && (
              <span style={{ fontSize: '0.7rem', fontWeight: 700, padding: '0.15rem 0.5rem', borderRadius: '9999px', background: '#ef4444', color: '#ffffff' }}>
                {unreadCount} Unread
              </span>
            )}
          </h3>
          <p style={{ margin: '0.2rem 0 0', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            {t.alertSubscriptionsSubtitle}
          </p>
        </div>

        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <button
            onClick={handleEvaluate}
            disabled={evaluating}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.35rem',
              padding: '0.45rem 0.75rem',
              borderRadius: '0.5rem',
              background: 'transparent',
              border: '1px solid var(--border-medium)',
              color: 'var(--text-secondary)',
              fontSize: '0.78rem',
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            <Radio size={13} style={{ color: '#10b981' }} />
            {evaluating ? 'Probing...' : 'Check Thresholds'}
          </button>

          <button
            onClick={() => setIsAdding(!isAdding)}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.4rem',
              padding: '0.45rem 0.85rem',
              borderRadius: '0.5rem',
              background: 'var(--brand-primary)',
              color: '#ffffff',
              border: 'none',
              fontSize: '0.8rem',
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            <Plus size={14} />
            {t.createAlertBtn}
          </button>
        </div>
      </div>

      {evalNotice && (
        <div style={{ padding: '0.65rem 0.85rem', borderRadius: '0.5rem', background: '#ecfdf5', border: '1px solid #a7f3d0', color: '#065f46', fontSize: '0.8rem' }}>
          {evalNotice}
        </div>
      )}

      {error && (
        <div style={{ padding: '0.65rem 0.85rem', borderRadius: '0.5rem', background: '#fee2e2', border: '1px solid #fca5a5', color: '#b91c1c', fontSize: '0.8rem' }}>
          {error}
        </div>
      )}

      {/* Add Subscription Form */}
      {isAdding && (
        <form onSubmit={handleCreate} style={{ padding: '1rem', background: 'var(--bg-surface-elevated)', borderRadius: '0.75rem', border: '1px solid var(--border-subtle)', display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          <div style={{ fontWeight: 600, fontSize: '0.85rem', color: 'var(--text-primary)' }}>New Threshold Alert Subscription</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '0.75rem' }}>
            <div>
              <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '0.25rem' }}>Pollutant Target</label>
              <select
                value={pollutant}
                onChange={(e) => setPollutant(e.target.value)}
                style={{ width: '100%', padding: '0.45rem 0.65rem', borderRadius: '0.375rem', border: '1px solid var(--border-medium)', background: 'var(--bg-surface)', color: 'var(--text-primary)', fontSize: '0.85rem' }}
              >
                <option value="PM25">PM2.5 (Fine Particulate)</option>
                <option value="PM10">PM10 (Coarse Particulate)</option>
                <option value="AQI">Air Quality Index (AQI)</option>
                <option value="NO2">Nitrogen Dioxide (NO2)</option>
              </select>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '0.25rem' }}>Threshold Exceedance Value</label>
              <input
                type="number"
                step="1"
                placeholder="e.g. 60"
                value={threshold}
                onChange={(e) => setThreshold(e.target.value)}
                required
                style={{ width: '100%', padding: '0.45rem 0.65rem', borderRadius: '0.375rem', border: '1px solid var(--border-medium)', background: 'var(--bg-surface)', color: 'var(--text-primary)', fontSize: '0.85rem' }}
              />
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '0.25rem' }}>Scope (City or Saved Location)</label>
              <select
                value={selectedLocationId}
                onChange={(e) => setSelectedLocationId(e.target.value)}
                style={{ width: '100%', padding: '0.45rem 0.65rem', borderRadius: '0.375rem', border: '1px solid var(--border-medium)', background: 'var(--bg-surface)', color: 'var(--text-primary)', fontSize: '0.85rem' }}
              >
                <option value="">{currentCityName} (City-Wide Stations)</option>
                {savedLocations.map((loc) => (
                  <option key={loc.id} value={loc.id}>{loc.label} ({loc.cityName})</option>
                ))}
              </select>
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '0.25rem' }}>
            <button
              type="button"
              onClick={() => setIsAdding(false)}
              style={{ padding: '0.4rem 0.75rem', borderRadius: '0.375rem', border: '1px solid var(--border-medium)', background: 'transparent', color: 'var(--text-secondary)', fontSize: '0.8rem', cursor: 'pointer' }}
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={saving}
              style={{ padding: '0.4rem 0.85rem', borderRadius: '0.375rem', border: 'none', background: 'var(--brand-primary)', color: '#ffffff', fontWeight: 600, fontSize: '0.8rem', cursor: 'pointer' }}
            >
              {saving ? 'Creating...' : 'Activate Subscription'}
            </button>
          </div>
        </form>
      )}

      {/* Active Subscriptions List */}
      <div>
        <div style={{ fontSize: '0.78rem', fontWeight: 700, color: 'var(--text-secondary)', marginBottom: '0.5rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
          Active Subscriptions ({subscriptions.length})
        </div>

        {subscriptions.length === 0 ? (
          <div style={{ padding: '1rem', textAlign: 'center', background: 'var(--bg-surface-elevated)', borderRadius: '0.5rem', color: 'var(--text-muted)', fontSize: '0.8rem' }}>
            No threshold subscriptions configured. Click "+ Create Threshold Alert" to receive alerts when PM2.5 or AQI exceeds safe bounds.
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            {subscriptions.map((sub) => (
              <div
                key={sub.id}
                style={{
                  padding: '0.75rem 1rem',
                  borderRadius: '0.5rem',
                  background: 'var(--bg-surface-elevated)',
                  border: '1px solid var(--border-subtle)',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  flexWrap: 'wrap',
                  gap: '0.5rem',
                }}
              >
                <div>
                  <div style={{ fontWeight: 600, fontSize: '0.85rem', color: 'var(--text-primary)' }}>
                    {sub.pollutant} &ge; {sub.thresholdValue} µg/m³
                    <span style={{ marginLeft: '0.5rem', fontSize: '0.75rem', color: 'var(--text-secondary)', fontWeight: 400 }}>
                      in {sub.locationLabel || sub.cityName}
                    </span>
                  </div>
                  <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: '0.15rem' }}>
                    {sub.channelStatusNote || 'In-app delivery supported'}
                    {sub.lastTriggeredAt && ` • Last alert: ${new Date(sub.lastTriggeredAt).toLocaleTimeString()}`}
                  </div>
                </div>

                <button
                  onClick={() => handleDelete(sub.id)}
                  title="Remove alert subscription"
                  style={{ background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer', padding: '0.25rem' }}
                >
                  <Trash2 size={14} />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* In-App Notifications Feed */}
      <div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
          <div style={{ fontSize: '0.78rem', fontWeight: 700, color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
            Recent In-App Alerts ({notifications.length})
          </div>
        </div>

        {notifications.length === 0 ? (
          <div style={{ padding: '1rem', textAlign: 'center', background: 'var(--bg-surface-elevated)', borderRadius: '0.5rem', color: 'var(--text-muted)', fontSize: '0.8rem' }}>
            No threshold notifications triggered yet. When real sensor values exceed your thresholds, alerts appear here.
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', maxHeight: '240px', overflowY: 'auto' }}>
            {notifications.map((notif) => (
              <div
                key={notif.id}
                style={{
                  padding: '0.75rem 1rem',
                  borderRadius: '0.5rem',
                  background: notif.readStatus ? 'var(--bg-surface-elevated)' : '#fffbeb',
                  border: notif.readStatus ? '1px solid var(--border-subtle)' : '1px solid #fde68a',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'flex-start',
                  gap: '0.75rem',
                }}
              >
                <div style={{ display: 'flex', gap: '0.65rem' }}>
                  <AlertTriangle size={16} style={{ color: '#d97706', flexShrink: 0, marginTop: '2px' }} />
                  <div>
                    <div style={{ fontWeight: notif.readStatus ? 600 : 700, fontSize: '0.82rem', color: '#92400e' }}>
                      {notif.title}
                    </div>
                    <div style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', lineHeight: 1.4, margin: '0.2rem 0' }}>
                      {notif.message}
                    </div>
                    <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
                      Source: <strong>{notif.triggeringSource}</strong> • {new Date(notif.createdAt).toLocaleTimeString()}
                    </div>
                  </div>
                </div>

                {!notif.readStatus && (
                  <button
                    onClick={() => handleMarkRead(notif.id)}
                    style={{
                      background: 'none',
                      border: 'none',
                      color: 'var(--brand-primary)',
                      fontSize: '0.72rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {t.markReadBtn}
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
