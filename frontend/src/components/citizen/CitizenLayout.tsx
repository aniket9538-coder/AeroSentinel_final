import React, { useState, useEffect } from 'react';
import { CitizenNavbar } from './CitizenNavbar';
import './citizen.css';

export interface CitizenLayoutProps {
  children: React.ReactNode;
  onOpenLocationModal?: () => void;
}

export const CitizenLayout: React.FC<CitizenLayoutProps> = ({
  children,
  onOpenLocationModal,
}) => {
  const [currentLocale, setCurrentLocale] = useState<string>(() => {
    return localStorage.getItem('aerosentinel_locale') || 'en';
  });

  const handleLocaleChange = (newLocale: string) => {
    setCurrentLocale(newLocale);
    localStorage.setItem('aerosentinel_locale', newLocale);
  };

  return (
    <div className="citizen-portal-shell">
      {/* Ambient Atmospheric Top Glow */}
      <div className="citizen-ambient-glow" />

      {/* Citizen Navbar */}
      <CitizenNavbar
        currentLocale={currentLocale}
        onLocaleChange={handleLocaleChange}
        onOpenLocationModal={onOpenLocationModal}
      />

      {/* Main Page Content */}
      <main className="citizen-main-content">
        {React.cloneElement(children as React.ReactElement, {
          currentLocale,
        })}
      </main>

      {/* Clean Trustworthy Citizen Footer */}
      <footer
        style={{
          borderTop: '1px solid var(--border-subtle)',
          padding: '1.5rem',
          textAlign: 'center',
          fontSize: '0.78rem',
          color: 'var(--text-muted)',
          background: 'var(--bg-surface)',
          marginTop: 'auto',
          position: 'relative',
          zIndex: 1,
        }}
      >
        <div style={{ maxWidth: '960px', margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
          <div>
            <strong>AeroSentinel Citizen Portal</strong> — Trusted environmental intelligence for Indian metropolitan regions.
          </div>
          <div>
            Sources: Open-Meteo NWP model analysis (CC BY 4.0) • CPCB & OpenAQ ground monitors • NDMA SACHET / IMD OASIS CAP feeds.
          </div>
          <div style={{ fontSize: '0.72rem', opacity: 0.8 }}>
            Notice: Model-based forecasts and experimental ML risk assessments are clearly disclosed and are never presented as official government warnings.
          </div>
        </div>
      </footer>
    </div>
  );
};
