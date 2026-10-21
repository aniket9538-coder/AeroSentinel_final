import React from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowRight, Compass } from 'lucide-react';

export const CommandCenterCTA: React.FC = () => {
  const navigate = useNavigate();

  return (
    <section className="onboarding-cta-section">
      <div className="onboarding-cta-card">
        {/* Subtle Mountain Silhouette SVG Background */}
        <svg
          className="onboarding-cta-bg-mountains"
          viewBox="0 0 1200 120"
          preserveAspectRatio="none"
          xmlns="http://www.w3.org/2000/svg"
        >
          {/* Back Mountain Ridge */}
          <path
            d="M 0,120 L 0,80 L 150,45 L 320,85 L 500,30 L 680,75 L 850,20 L 1020,65 L 1200,35 L 1200,120 Z"
            fill="#38bdf8"
            opacity="0.35"
          />
          {/* Front Mountain Ridge */}
          <path
            d="M 0,120 L 0,95 L 100,75 L 240,100 L 410,60 L 590,90 L 760,50 L 930,85 L 1100,55 L 1200,70 L 1200,120 Z"
            fill="#0284c7"
            opacity="0.5"
          />
        </svg>

        {/* Content */}
        <div className="onboarding-cta-content">
          <h2 className="onboarding-cta-heading">
            From pollution data to early action.
          </h2>
          <p className="onboarding-cta-sub">
            Join municipal authorities, atmospheric researchers, and citizens in building proactive climate resilience.
          </p>

          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '1rem', flexWrap: 'wrap' }}>
            <button
              className="onboarding-cta-btn"
              onClick={() => navigate('/dashboard')}
              type="button"
            >
              <span>Enter AeroSentinel Command Center</span>
              <ArrowRight size={18} />
            </button>

            <button
              onClick={() => navigate('/citizen')}
              type="button"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.5rem',
                padding: '0.85rem 1.5rem',
                borderRadius: '12px',
                background: 'rgba(255, 255, 255, 0.15)',
                border: '1px solid rgba(255, 255, 255, 0.3)',
                color: '#ffffff',
                fontWeight: 700,
                fontSize: '0.95rem',
                cursor: 'pointer',
                backdropFilter: 'blur(8px)',
                transition: 'all 0.15s ease',
              }}
              id="cta-citizen-portal-btn"
            >
              <Compass size={18} />
              <span>Explore Citizen Portal</span>
            </button>
          </div>
        </div>
      </div>
    </section>
  );
};
