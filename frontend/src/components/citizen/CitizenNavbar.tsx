import React, { useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import {
  Shield,
  MapPin,
  ChevronDown,
  Sun,
  Moon,
  MessageSquare,
  CloudSun,
  Wind,
  AlertTriangle,
  FileText,
  ExternalLink,
  Menu,
  X,
  Check,
  Compass,
} from 'lucide-react';
import { useApp } from '../../store/AppContext';

export interface CitizenNavbarProps {
  currentLocale: string;
  onLocaleChange: (locale: string) => void;
  onOpenLocationModal?: () => void;
}

export const CitizenNavbar: React.FC<CitizenNavbarProps> = ({
  currentLocale,
  onLocaleChange,
  onOpenLocationModal,
}) => {
  const navigate = useNavigate();
  const {
    theme,
    toggleTheme,
    selectedCity,
    setSelectedCity,
    availableCities,
  } = useApp();

  const [cityMenuOpen, setCityMenuOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const cityName = selectedCity?.name || 'Pune';
  const stateName = selectedCity?.state || 'Maharashtra';

  return (
    <header className="citizen-navbar" role="banner">
      {/* Left: Brand Identity & Desktop Navigation */}
      <div className="citizen-nav-left">
        <div
          className="citizen-nav-brand"
          onClick={() => navigate('/citizen')}
          role="button"
          tabIndex={0}
          aria-label="AeroSentinel Citizen Portal Home"
        >
          <div className="citizen-brand-badge">
            <Compass size={20} strokeWidth={2.5} />
          </div>
          <div className="citizen-brand-titles">
            <span className="citizen-brand-name">AeroSentinel</span>
            <span className="citizen-brand-portal-tag">Citizen Portal</span>
          </div>
        </div>

        {/* Desktop Nav Links */}
        <nav className="citizen-nav-links" aria-label="Citizen Navigation">
          <NavLink
            to="/citizen"
            end
            className={({ isActive }) => `citizen-nav-link ${isActive ? 'active' : ''}`}
          >
            <span>Overview</span>
          </NavLink>

          <NavLink
            to="/citizen/assistant"
            className={({ isActive }) => `citizen-nav-link ${isActive ? 'active' : ''}`}
          >
            <MessageSquare size={15} />
            <span>Ask AeroSentinel</span>
          </NavLink>

          <NavLink
            to="/citizen/weather"
            className={({ isActive }) => `citizen-nav-link ${isActive ? 'active' : ''}`}
          >
            <CloudSun size={15} />
            <span>Weather & Forecast</span>
          </NavLink>

          <NavLink
            to="/citizen/air-quality"
            className={({ isActive }) => `citizen-nav-link ${isActive ? 'active' : ''}`}
          >
            <Wind size={15} />
            <span>Air Quality</span>
          </NavLink>

          <NavLink
            to="/citizen/warnings"
            className={({ isActive }) => `citizen-nav-link ${isActive ? 'active' : ''}`}
          >
            <AlertTriangle size={15} />
            <span>Warnings</span>
          </NavLink>

          <NavLink
            to="/citizen/report"
            className={({ isActive }) => `citizen-nav-link ${isActive ? 'active' : ''}`}
          >
            <FileText size={15} />
            <span>Report</span>
          </NavLink>
        </nav>
      </div>

      {/* Right: Location Pill, Language, Theme, Command Center link */}
      <div className="citizen-nav-right">
        {/* Location Dropdown Pill */}
        <div style={{ position: 'relative' }}>
          <button
            className="citizen-location-btn"
            onClick={() => setCityMenuOpen(!cityMenuOpen)}
            type="button"
            aria-expanded={cityMenuOpen}
            aria-label={`Current location: ${cityName}, ${stateName}`}
            id="citizen-location-pill"
          >
            <MapPin size={14} style={{ color: 'var(--brand-primary)' }} />
            <span>{cityName}</span>
            <ChevronDown size={13} style={{ opacity: 0.6 }} />
          </button>

          {cityMenuOpen && (
            <div
              style={{
                position: 'absolute',
                top: 'calc(100% + 8px)',
                right: 0,
                background: 'var(--bg-surface)',
                border: '1px solid var(--border-medium)',
                borderRadius: '12px',
                padding: '0.4rem',
                boxShadow: 'var(--shadow-lg)',
                minWidth: '220px',
                zIndex: 110,
              }}
            >
              <div
                style={{
                  fontSize: '0.72rem',
                  fontWeight: 700,
                  color: 'var(--text-muted)',
                  padding: '0.4rem 0.6rem',
                  textTransform: 'uppercase',
                  letterSpacing: '0.05em',
                }}
              >
                Supported Cities
              </div>
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
                    background:
                      selectedCity?.name === city.name ? 'var(--brand-surface)' : 'transparent',
                    color:
                      selectedCity?.name === city.name
                        ? 'var(--brand-primary)'
                        : 'var(--text-primary)',
                    fontWeight: selectedCity?.name === city.name ? 700 : 500,
                    fontSize: '0.85rem',
                    cursor: 'pointer',
                    textAlign: 'left',
                  }}
                >
                  <span>{city.name}</span>
                  {selectedCity?.name === city.name && <Check size={14} />}
                </button>
              ))}

              {onOpenLocationModal && (
                <button
                  onClick={() => {
                    setCityMenuOpen(false);
                    onOpenLocationModal();
                  }}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.4rem',
                    width: '100%',
                    marginTop: '0.35rem',
                    padding: '0.5rem 0.75rem',
                    borderTop: '1px solid var(--border-subtle)',
                    borderBottom: 'none',
                    borderLeft: 'none',
                    borderRight: 'none',
                    background: 'transparent',
                    color: 'var(--brand-primary)',
                    fontWeight: 600,
                    fontSize: '0.8rem',
                    cursor: 'pointer',
                  }}
                >
                  <MapPin size={13} />
                  <span>Custom Coordinates / GPS</span>
                </button>
              )}
            </div>
          )}
        </div>

        {/* Language Switcher */}
        <select
          className="citizen-lang-select"
          value={currentLocale}
          onChange={(e) => onLocaleChange(e.target.value)}
          aria-label="Select language"
          id="citizen-locale-select"
        >
          <option value="en">English (EN)</option>
          <option value="hi">हिंदी (HI)</option>
          <option value="mr">मराठी (MR)</option>
        </select>

        {/* Theme Toggle */}
        <button
          className="citizen-icon-btn"
          onClick={toggleTheme}
          title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
          aria-label="Toggle visual theme"
          type="button"
        >
          {theme === 'dark' ? <Sun size={17} /> : <Moon size={17} />}
        </button>

        {/* Link to Authority Command Center */}
        <button
          className="citizen-command-center-btn"
          onClick={() => navigate('/dashboard')}
          type="button"
          title="Switch to Authority Command Center"
        >
          <span>Command Center</span>
          <ExternalLink size={13} />
        </button>

        {/* Mobile Hamburger Button */}
        <button
          className="citizen-mobile-menu-btn"
          onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          aria-label={mobileMenuOpen ? 'Close menu' : 'Open menu'}
          type="button"
        >
          {mobileMenuOpen ? <X size={22} /> : <Menu size={22} />}
        </button>
      </div>

      {/* Mobile Menu Drawer */}
      {mobileMenuOpen && (
        <div className="citizen-mobile-drawer open" style={{ position: 'absolute', top: '100%', left: 0, width: '100%' }}>
          <NavLink
            to="/citizen"
            end
            onClick={() => setMobileMenuOpen(false)}
            className="citizen-nav-link"
          >
            <span>Overview</span>
          </NavLink>
          <NavLink
            to="/citizen/assistant"
            onClick={() => setMobileMenuOpen(false)}
            className="citizen-nav-link"
          >
            <MessageSquare size={16} />
            <span>Ask AeroSentinel</span>
          </NavLink>
          <NavLink
            to="/citizen/weather"
            onClick={() => setMobileMenuOpen(false)}
            className="citizen-nav-link"
          >
            <CloudSun size={16} />
            <span>Weather & Forecast</span>
          </NavLink>
          <NavLink
            to="/citizen/air-quality"
            onClick={() => setMobileMenuOpen(false)}
            className="citizen-nav-link"
          >
            <Wind size={16} />
            <span>Air Quality</span>
          </NavLink>
          <NavLink
            to="/citizen/warnings"
            onClick={() => setMobileMenuOpen(false)}
            className="citizen-nav-link"
          >
            <AlertTriangle size={16} />
            <span>Official Warnings</span>
          </NavLink>
          <NavLink
            to="/citizen/report"
            onClick={() => setMobileMenuOpen(false)}
            className="citizen-nav-link"
          >
            <FileText size={16} />
            <span>Report Pollution</span>
          </NavLink>
          <button
            onClick={() => {
              setMobileMenuOpen(false);
              navigate('/dashboard');
            }}
            className="citizen-nav-link"
            style={{ background: 'none', border: 'none', textAlign: 'left', width: '100%', cursor: 'pointer', color: 'var(--brand-primary)', borderTop: '1px solid var(--border-subtle)', paddingTop: '0.65rem' }}
          >
            <ExternalLink size={16} />
            <span>Command Center</span>
          </button>
        </div>
      )}
    </header>
  );
};
