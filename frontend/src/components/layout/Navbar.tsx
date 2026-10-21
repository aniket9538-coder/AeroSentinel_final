import React, { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Sun,
  Moon,
  Bell,
  ChevronDown,
  Menu,
  X,
  MapPin,
  ShieldCheck,
  RefreshCw,
  Compass,
  LogIn,
  LogOut,
} from 'lucide-react';
import { useApp } from '../../store/AppContext';
import { authService } from '../../services/auth.service';

export const Navbar: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const {
    theme,
    toggleTheme,
    selectedCity,
    setSelectedCity,
    availableCities,
    isLive,
    isOnline,
    toggleSidebar,
    sidebarOpen,
    backendStatus,
    backendHealth,
    lastUpdated,
    refreshData,
    isLoading,
  } = useApp();

  const [cityDropdownOpen, setCityDropdownOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [userDropdownOpen, setUserDropdownOpen] = useState(false);
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(() => {
    return typeof localStorage !== 'undefined' && !!localStorage.getItem('aerosentinel_jwt');
  });

  const handleToggleAuth = async () => {
    if (isAuthenticated) {
      authService.logout();
      setIsAuthenticated(false);
      setUserDropdownOpen(false);
      window.location.reload();
    } else {
      try {
        await authService.loginAsAuthority();
        setIsAuthenticated(true);
        setUserDropdownOpen(false);
        window.location.reload();
      } catch (e) {
        console.error('Failed to authenticate:', e);
      }
    }
  };

  // Derive page context title from route
  const getPageTitle = (path: string): string => {
    if (path === '/' || path === '/dashboard') return 'Overview';
    if (path === '/air-quality') return 'Air Quality';
    if (path === '/weather') return 'Weather & Spatial';
    if (path === '/map' || path === '/spatial') return 'Spatial Grid';
    if (path.includes('hotspot')) return 'Hotspot Intelligence';
    if (path.includes('forecast')) return 'Predictive Forecast';
    if (path.includes('evidence')) return 'Gemini Evidence';
    if (path.includes('alerts')) return 'Active Alerts';
    if (path.includes('authority') || path.includes('incident')) return 'Authority Command';
    if (path.includes('federated')) return 'Federated Network';
    if (path.includes('admin') || path.includes('status')) return 'System Diagnostics';
    if (path.includes('citizen')) return 'Citizen Telemetry';
    return 'Command Center';
  };

  const formatTime = (date: Date) => {
    try {
      return date.toLocaleTimeString('en-US', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: true,
      });
    } catch {
      return 'N/A';
    }
  };

  return (
    <header
      style={{
        height: '64px',
        background: 'var(--bg-surface)',
        borderBottom: '1px solid var(--border-subtle)',
        position: 'sticky',
        top: 0,
        zIndex: 100,
        padding: '0 1.5rem',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        boxShadow: 'var(--shadow-sm)',
        flexShrink: 0,
      }}
    >
      {/* LEFT: Sidebar Toggle + Brand + Page Context + Version */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.85rem' }}>
        <button
          onClick={toggleSidebar}
          style={{
            background: 'var(--bg-surface-elevated)',
            border: '1px solid var(--border-medium)',
            color: 'var(--text-secondary)',
            cursor: 'pointer',
            height: '36px',
            width: '36px',
            borderRadius: '8px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            transition: 'all 0.15s ease',
          }}
          title={sidebarOpen ? 'Collapse sidebar' : 'Expand sidebar'}
        >
          {sidebarOpen ? <X size={18} /> : <Menu size={18} />}
        </button>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span
            style={{
              fontSize: '0.95rem',
              fontWeight: 800,
              fontFamily: 'var(--font-heading)',
              color: 'var(--brand-primary)',
              letterSpacing: '-0.01em',
            }}
          >
            AeroSentinel
          </span>
          <span style={{ color: 'var(--border-medium)', fontSize: '0.9rem' }}>/</span>
          <span
            style={{
              fontSize: '0.95rem',
              fontWeight: 700,
              fontFamily: 'var(--font-heading)',
              color: 'var(--text-primary)',
            }}
          >
            {getPageTitle(location.pathname)}
          </span>
          <span
            style={{
              fontSize: '0.7rem',
              color: 'var(--text-muted)',
              fontWeight: 600,
              background: 'var(--bg-surface-elevated)',
              padding: '0.15rem 0.45rem',
              borderRadius: '6px',
              border: '1px solid var(--border-subtle)',
            }}
          >
            v2.4.0
          </span>
        </div>
      </div>

      {/* CENTER: Operating City Selector */}
      <div style={{ position: 'relative' }}>
        <button
          onClick={() => setCityDropdownOpen(!cityDropdownOpen)}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
            height: '36px',
            padding: '0 0.85rem',
            background: 'var(--bg-surface-elevated)',
            border: '1px solid var(--border-medium)',
            borderRadius: '8px',
            color: 'var(--text-primary)',
            fontSize: '0.85rem',
            fontWeight: 600,
            cursor: 'pointer',
            transition: 'all 0.15s ease',
          }}
        >
          <MapPin size={15} color="var(--brand-primary)" />
          <span>{selectedCity?.name || 'Select City'}</span>
          {selectedCity && (
            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
              ({selectedCity.state})
            </span>
          )}
          <ChevronDown
            size={14}
            style={{
              transform: cityDropdownOpen ? 'rotate(180deg)' : 'none',
              transition: 'transform 0.15s ease',
              color: 'var(--text-secondary)',
            }}
          />
        </button>

        {cityDropdownOpen && (
          <div
            style={{
              position: 'absolute',
              top: 'calc(100% + 6px)',
              left: 0,
              width: '210px',
              background: 'var(--bg-surface)',
              border: '1px solid var(--border-medium)',
              borderRadius: '12px',
              boxShadow: 'var(--shadow-lg)',
              overflow: 'hidden',
              zIndex: 200,
              padding: '0.4rem',
            }}
          >
            <div
              style={{
                fontSize: '0.7rem',
                fontWeight: 700,
                textTransform: 'uppercase',
                color: 'var(--text-muted)',
                padding: '0.4rem 0.6rem',
                letterSpacing: '0.05em',
              }}
            >
              Select Operating City
            </div>
            {availableCities.map((c) => (
              <button
                key={c.id}
                onClick={() => {
                  setSelectedCity(c);
                  setCityDropdownOpen(false);
                }}
                style={{
                  width: '100%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '0.5rem 0.65rem',
                  background:
                    selectedCity?.id === c.id ? 'var(--brand-surface)' : 'transparent',
                  border: 'none',
                  borderRadius: '8px',
                  color:
                    selectedCity?.id === c.id
                      ? 'var(--brand-primary)'
                      : 'var(--text-primary)',
                  fontSize: '0.85rem',
                  fontWeight: selectedCity?.id === c.id ? 600 : 500,
                  cursor: 'pointer',
                  textAlign: 'left',
                }}
              >
                <span>{c.name}</span>
                <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                  {c.state}
                </span>
              </button>
            ))}
          </div>
        )}
      </div>

      {/* RIGHT: System Status + Live Timestamp + Sync + Theme + Notifications + Admin */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem' }}>
        {/* F0 Backend Connectivity Indicator */}
        <div
          style={{
            height: '36px',
            display: 'flex',
            alignItems: 'center',
            gap: '0.45rem',
            padding: '0 0.75rem',
            borderRadius: '8px',
            background:
              !isOnline
                ? 'rgba(239, 68, 68, 0.12)'
                : backendStatus === 'CONNECTED'
                ? 'rgba(16, 185, 129, 0.12)'
                : backendStatus === 'CONNECTING'
                ? 'rgba(245, 158, 11, 0.12)'
                : 'rgba(239, 68, 68, 0.12)',
            border:
              !isOnline
                ? '1px solid rgba(239, 68, 68, 0.35)'
                : backendStatus === 'CONNECTED'
                ? '1px solid rgba(16, 185, 129, 0.35)'
                : backendStatus === 'CONNECTING'
                ? '1px solid rgba(245, 158, 11, 0.35)'
                : '1px solid rgba(239, 68, 68, 0.35)',
            color:
              !isOnline
                ? 'var(--accent-rose)'
                : backendStatus === 'CONNECTED'
                ? 'var(--aqi-good)'
                : backendStatus === 'CONNECTING'
                ? 'var(--accent-amber)'
                : 'var(--accent-rose)',
            fontSize: '0.725rem',
            fontWeight: 700,
            letterSpacing: '0.03em',
          }}
          title={
            !isOnline
              ? 'Browser is offline. Check your network or internet connection.'
              : backendStatus === 'CONNECTED'
              ? `Backend: ${backendHealth?.service} (Status: ${backendHealth?.status}, Time: ${backendHealth?.timestamp})`
              : backendStatus === 'CONNECTING'
              ? 'Connecting to AeroSentinel backend (/api/v1/health)...'
              : 'Backend unavailable (/api/v1/health unreachable)'
          }
        >
          <span
            style={{
              width: '7px',
              height: '7px',
              borderRadius: '50%',
              backgroundColor:
                !isOnline
                  ? 'var(--accent-rose)'
                  : backendStatus === 'CONNECTED'
                  ? 'var(--aqi-good)'
                  : backendStatus === 'CONNECTING'
                  ? 'var(--accent-amber)'
                  : 'var(--accent-rose)',
            }}
            className={isOnline && backendStatus === 'CONNECTED' ? 'live-indicator-dot' : ''}
          />
          <span>
            {!isOnline
              ? 'OFFLINE'
              : backendStatus === 'CONNECTED'
              ? 'SYSTEM: CONNECTED'
              : backendStatus === 'CONNECTING'
              ? 'CONNECTING...'
              : 'OFFLINE'}
          </span>
        </div>

        {/* Live Telemetry Updated Badge */}
        <div
          style={{
            height: '36px',
            display: 'flex',
            alignItems: 'center',
            gap: '0.4rem',
            padding: '0 0.75rem',
            borderRadius: '8px',
            background: 'var(--brand-surface)',
            border: '1px solid var(--brand-border)',
            fontSize: '0.725rem',
            fontWeight: 700,
            color: 'var(--brand-primary)',
            letterSpacing: '0.02em',
          }}
          title={`Last synchronization at ${formatTime(lastUpdated)}`}
        >
          <span
            style={{
              width: '6px',
              height: '6px',
              borderRadius: '50%',
              backgroundColor: 'var(--aqi-good)',
            }}
          />
          <span>{isLive ? 'LIVE' : 'SYNCED'} • {formatTime(lastUpdated)}</span>
        </div>

        {/* Sync Control Button */}
        <button
          onClick={refreshData}
          disabled={isLoading}
          style={{
            height: '36px',
            padding: '0 0.85rem',
            borderRadius: '8px',
            background: 'var(--bg-surface-elevated)',
            border: '1px solid var(--border-medium)',
            color: 'var(--text-primary)',
            fontSize: '0.78rem',
            fontWeight: 600,
            cursor: isLoading ? 'default' : 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: '0.4rem',
            transition: 'all 0.15s ease',
            opacity: isLoading ? 0.7 : 1,
          }}
          title="Synchronize real-time telemetry from city sensors"
        >
          <RefreshCw
            size={13}
            style={{
              animation: isLoading ? 'spin 1s linear infinite' : 'none',
              color: 'var(--brand-primary)',
            }}
          />
          <span>Sync</span>
        </button>

        {/* Theme Toggle Button */}
        <button
          onClick={toggleTheme}
          style={{
            width: '36px',
            height: '36px',
            borderRadius: '8px',
            background: 'var(--bg-surface-elevated)',
            border: '1px solid var(--border-medium)',
            color: 'var(--text-secondary)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            transition: 'all 0.15s ease',
          }}
          title={`Switch to ${theme === 'dark' ? 'Light' : 'Dark'} mode`}
          aria-label="Toggle Theme"
        >
          {theme === 'dark' ? (
            <Sun size={16} color="#fbbf24" />
          ) : (
            <Moon size={16} color="var(--brand-primary)" />
          )}
        </button>

        {/* Notifications Icon with popover */}
        <div style={{ position: 'relative' }}>
          <button
            onClick={() => setNotificationsOpen(!notificationsOpen)}
            style={{
              width: '36px',
              height: '36px',
              borderRadius: '8px',
              background: 'var(--bg-surface-elevated)',
              border: '1px solid var(--border-medium)',
              color: 'var(--text-secondary)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              cursor: 'pointer',
              position: 'relative',
              transition: 'all 0.15s ease',
            }}
            title="System Notifications"
            aria-label="Notifications"
          >
            <Bell size={16} />
            <span
              style={{
                position: 'absolute',
                top: '7px',
                right: '7px',
                width: '6px',
                height: '6px',
                borderRadius: '50%',
                backgroundColor: 'var(--accent-amber)',
              }}
            />
          </button>

          {notificationsOpen && (
            <div
              style={{
                position: 'absolute',
                top: 'calc(100% + 8px)',
                right: 0,
                width: '300px',
                background: 'var(--bg-surface)',
                border: '1px solid var(--border-medium)',
                borderRadius: '12px',
                boxShadow: 'var(--shadow-lg)',
                padding: '0.85rem',
                zIndex: 200,
              }}
            >
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  marginBottom: '0.6rem',
                  paddingBottom: '0.4rem',
                  borderBottom: '1px solid var(--border-subtle)',
                }}
              >
                <span style={{ fontSize: '0.8rem', fontWeight: 700, color: 'var(--text-primary)' }}>
                  Active Alerts ({selectedCity?.name || 'City'})
                </span>
                <span style={{ fontSize: '0.7rem', color: 'var(--accent-amber)', fontWeight: 600 }}>
                  1 Action Required
                </span>
              </div>
              <div
                style={{
                  fontSize: '0.78rem',
                  color: 'var(--text-secondary)',
                  lineHeight: 1.4,
                  padding: '0.4rem 0',
                }}
              >
                <strong>Industrial Zone Spike:</strong> Elevated PM2.5 threshold reached in Hadapsar (91 µg/m³).
              </div>
            </div>
          )}
        </div>

        {/* Switch to Citizen Portal */}
        <button
          onClick={() => navigate('/citizen')}
          style={{
            height: '36px',
            padding: '0 0.75rem',
            borderRadius: '8px',
            background: 'var(--brand-surface)',
            border: '1px solid var(--brand-border)',
            color: 'var(--brand-primary)',
            fontSize: '0.78rem',
            fontWeight: 700,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: '0.4rem',
            transition: 'all 0.15s ease',
          }}
          title="Open Citizen Portal"
          id="navbar-citizen-portal-btn"
        >
          <Compass size={14} />
          <span>Citizen Portal</span>
        </button>

        {/* User / Admin Status Badge */}
        <div style={{ position: 'relative' }}>
          <button
            onClick={() => setUserDropdownOpen(!userDropdownOpen)}
            style={{
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              gap: '0.45rem',
              padding: '0 0.75rem',
              borderRadius: '8px',
              background: 'var(--bg-surface-elevated)',
              border: `1px solid ${isAuthenticated ? 'var(--brand-primary)' : 'var(--border-medium)'}`,
              cursor: 'pointer',
              color: 'var(--text-primary)',
            }}
            title={isAuthenticated ? 'Authority officer session active' : 'Click to sign in as Municipal Authority Officer'}
            id="navbar-auth-status-btn"
          >
            <div
              style={{
                width: '22px',
                height: '22px',
                borderRadius: '5px',
                background: isAuthenticated
                  ? 'linear-gradient(135deg, var(--brand-primary), var(--accent-blue))'
                  : '#6b7280',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#ffffff',
              }}
            >
              <ShieldCheck size={13} />
            </div>
            <span style={{ fontSize: '0.78rem', fontWeight: 600 }}>
              {isAuthenticated ? 'Authority (Active)' : 'Sign In (Authority)'}
            </span>
          </button>

          {userDropdownOpen && (
            <div
              style={{
                position: 'absolute',
                top: 'calc(100% + 8px)',
                right: 0,
                width: '240px',
                background: 'var(--bg-surface)',
                border: '1px solid var(--border-medium)',
                borderRadius: '10px',
                boxShadow: 'var(--shadow-lg)',
                padding: '0.75rem',
                zIndex: 200,
              }}
            >
              <div style={{ marginBottom: '0.6rem', paddingBottom: '0.4rem', borderBottom: '1px solid var(--border-subtle)' }}>
                <div style={{ fontSize: '0.8rem', fontWeight: 700, color: 'var(--text-primary)' }}>
                  {isAuthenticated ? 'Authority Officer Session' : 'Unauthenticated Session'}
                </div>
                <div style={{ fontSize: '0.72rem', color: 'var(--text-secondary)' }}>
                  {isAuthenticated ? 'authority@aerosentinel.gov' : 'Public read-only session'}
                </div>
              </div>
              <button
                onClick={handleToggleAuth}
                style={{
                  width: '100%',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.45rem',
                  padding: '0.45rem 0.75rem',
                  borderRadius: '6px',
                  background: isAuthenticated ? '#fee2e2' : '#0284c7',
                  color: isAuthenticated ? '#dc2626' : '#ffffff',
                  border: 'none',
                  fontSize: '0.8rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  justifyContent: 'center',
                }}
              >
                {isAuthenticated ? <LogOut size={14} /> : <LogIn size={14} />}
                <span>{isAuthenticated ? 'Sign Out of Authority' : 'Sign In as Authority Officer'}</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
};
