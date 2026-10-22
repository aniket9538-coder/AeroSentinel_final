import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Sun,
  Moon,
  Bell,
  ChevronDown,
  MapPin,
  Shield,
  User,
  Check,
  Compass,
} from 'lucide-react';
import { useApp } from '../../store/AppContext';

export const OnboardingHeader: React.FC = () => {
  const navigate = useNavigate();
  const {
    theme,
    toggleTheme,
    selectedCity,
    setSelectedCity,
    availableCities,
  } = useApp();

  const [cityMenuOpen, setCityMenuOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);

  const cityName = selectedCity?.name || 'Mumbai';
  const stateName = selectedCity?.state || 'Maharashtra';

  return (
    <header className="onboarding-header">
      {/* Left: Brand Identity */}
      <div
        className="onboarding-brand"
        style={{ cursor: 'pointer' }}
        onClick={() => navigate('/')}
      >
        <div className="onboarding-logo-icon">
          <Shield size={20} strokeWidth={2.4} />
        </div>
        <div className="onboarding-brand-text">
          <span className="onboarding-brand-title">AEROSENTINEL</span>
          <span className="onboarding-brand-tagline">CLIMATE INTELLIGENCE</span>
        </div>
      </div>

      {/* Middle: City Selector Pill */}
      <div style={{ position: 'relative' }}>
        <button
          className="onboarding-city-pill"
          onClick={() => setCityMenuOpen(!cityMenuOpen)}
          type="button"
          aria-expanded={cityMenuOpen}
        >
          <MapPin size={14} style={{ color: '#0284c7' }} />
          <span>{cityName} ({stateName})</span>
          <ChevronDown size={14} style={{ opacity: 0.6 }} />
        </button>

        {cityMenuOpen && (
          <div
            style={{
              position: 'absolute',
              top: 'calc(100% + 8px)',
              left: '50%',
              transform: 'translateX(-50%)',
              background: 'var(--bg-surface)',
              border: '1px solid var(--border-medium)',
              borderRadius: '12px',
              padding: '0.4rem',
              boxShadow: '0 10px 25px rgba(0,0,0,0.15)',
              minWidth: '200px',
              zIndex: 100,
            }}
          >
            {availableCities.map((city) => (
              <button
                key={city.id || city.name}
                onClick={() => {
                  setSelectedCity(city);
                  setCityMenuOpen(false);
                }}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  width: '100%',
                  padding: '0.5rem 0.75rem',
                  border: 'none',
                  borderRadius: '8px',
                  background: (selectedCity?.id === city.id || selectedCity?.name === city.name) ? 'var(--brand-surface)' : 'transparent',
                  color: (selectedCity?.id === city.id || selectedCity?.name === city.name) ? 'var(--brand-primary)' : 'var(--text-primary)',
                  fontWeight: (selectedCity?.id === city.id || selectedCity?.name === city.name) ? 700 : 500,
                  fontSize: '0.85rem',
                  cursor: 'pointer',
                  textAlign: 'left',
                }}
              >
                <span>{city.name}</span>
                {(selectedCity?.id === city.id || selectedCity?.name === city.name) && <Check size={14} />}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Right: Actions */}
      <div className="onboarding-header-actions">
        {/* Citizen Portal Entry Button */}
        <button
          className="onboarding-citizen-pill"
          onClick={() => navigate('/citizen')}
          type="button"
          title="Open Dedicated Citizen Portal"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.45rem',
            padding: '0.42rem 0.9rem',
            background: 'linear-gradient(135deg, #0284c7 0%, #06b6d4 100%)',
            color: '#ffffff',
            border: 'none',
            borderRadius: '20px',
            fontWeight: 700,
            fontSize: '0.82rem',
            cursor: 'pointer',
            boxShadow: '0 2px 8px rgba(2, 132, 199, 0.35)',
            transition: 'all 0.15s ease',
          }}
          id="header-citizen-portal-btn"
        >
          <Compass size={14} strokeWidth={2.4} />
          <span>Citizen Portal</span>
        </button>

        {/* System Status */}
        <div className="onboarding-status-pill">
          <span className="onboarding-status-dot" />
          <span>SYSTEM: CONNECTED</span>
        </div>

        {/* Theme Toggle */}
        <button
          className="onboarding-icon-btn"
          onClick={toggleTheme}
          title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
          type="button"
        >
          {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
        </button>

        {/* Notifications */}
        <div style={{ position: 'relative' }}>
          <button
            className="onboarding-icon-btn"
            onClick={() => setNotificationsOpen(!notificationsOpen)}
            title="Notifications"
            type="button"
          >
            <Bell size={18} />
            <span className="onboarding-badge-count">2</span>
          </button>
        </div>

        {/* User Profile */}
        <div className="onboarding-user-pill" onClick={() => navigate('/dashboard')}>
          <div className="onboarding-avatar">
            <User size={14} />
          </div>
          <span>Admin</span>
          <ChevronDown size={13} style={{ opacity: 0.6 }} />
        </div>
      </div>
    </header>
  );
};
