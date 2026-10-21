import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowRight, Play, X, ShieldCheck, Activity, Layers, Compass } from 'lucide-react';
import { HeroMetrics } from './hero/HeroMetrics';
import { HeroMapVisual } from './hero/HeroMapVisual';

export const HeroSection: React.FC = () => {
  const navigate = useNavigate();
  const [demoModalOpen, setDemoModalOpen] = useState(false);

  const handleWatchDemo = () => {
    // Open clean interactive workflow demo modal
    setDemoModalOpen(true);
  };

  const scrollToOperations = () => {
    setDemoModalOpen(false);
    const el = document.getElementById('how-aerosentinel-operates');
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    }
  };

  return (
    <section className="onboarding-hero" aria-label="AeroSentinel Overview">
      {/* Left Column: Product Story & CTA (~45%) */}
      <div className="hero-story-col">
        {/* Eyebrow Badge */}
        <div className="onboarding-eyebrow">
          <span className="onboarding-eyebrow-dot" />
          <span>AI-POWERED HYPERLOCAL POLLUTION INTELLIGENCE</span>
        </div>

        {/* Cinematic Headline */}
        <h1 className="onboarding-headline">
          <span>Detect emerging pollution risk.</span>{' '}
          <span className="onboarding-headline-accent">
            Understand the evidence.
          </span>{' '}
          <span>Act before the spike.</span>
        </h1>

        {/* Supporting Paragraph */}
        <p className="onboarding-description">
          AeroSentinel transforms fragmented environmental signals into predictive sub-kilometer
          intelligence. Powered by real air quality, weather, satellite data, citizen reports and AI,
          it helps cities detect, explain and prevent pollution before it impacts people.
        </p>

        {/* CTA Buttons */}
        <div className="onboarding-hero-actions">
          <button
            className="onboarding-btn-primary"
            onClick={() => navigate('/dashboard')}
            type="button"
            id="hero-get-started-btn"
          >
            <span>Get Started</span>
            <ArrowRight size={18} />
          </button>

          <button
            className="onboarding-btn-citizen"
            onClick={() => navigate('/citizen')}
            type="button"
            id="hero-citizen-portal-btn"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.5rem',
              padding: '0.75rem 1.35rem',
              borderRadius: '12px',
              background: 'linear-gradient(135deg, rgba(2, 132, 199, 0.2) 0%, rgba(6, 182, 212, 0.15) 100%)',
              border: '1px solid var(--brand-border)',
              color: 'var(--brand-primary)',
              fontWeight: 700,
              fontSize: '0.92rem',
              cursor: 'pointer',
              transition: 'all 0.15s ease',
            }}
          >
            <Compass size={18} />
            <span>Citizen Portal</span>
          </button>

          <button
            className="onboarding-btn-demo"
            onClick={handleWatchDemo}
            type="button"
            id="hero-watch-demo-btn"
          >
            <Play size={16} className="onboarding-btn-demo-icon" fill="#0284c7" />
            <span>Watch Demo</span>
          </button>
        </div>

        {/* Real UI Metric Counters Row */}
        <HeroMetrics />
      </div>

      {/* Right Column: Clean Satellite Map + React Overlays (~55%) */}
      <div className="hero-visual-col">
        <HeroMapVisual />
      </div>

      {/* Lightweight Interactive Demo Modal (Zero fake data) */}
      {demoModalOpen && (
        <div
          className="demo-modal-backdrop"
          onClick={() => setDemoModalOpen(false)}
          role="dialog"
          aria-modal="true"
          aria-labelledby="demo-modal-title"
        >
          <div
            className="demo-modal-window"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="demo-modal-header">
              <div className="demo-modal-badge">
                <ShieldCheck size={16} color="#0284c7" />
                <span>AEROSENTINEL PLATFORM WALKTHROUGH</span>
              </div>
              <button
                className="demo-modal-close"
                onClick={() => setDemoModalOpen(false)}
                type="button"
                aria-label="Close modal"
              >
                <X size={18} />
              </button>
            </div>

            <h3 id="demo-modal-title" className="demo-modal-title">
              Autonomous Atmospheric Ingestion & Early Action
            </h3>
            <p className="demo-modal-desc">
              AeroSentinel continuously synthesizes CAAQMS ground monitors, IMD weather feeds, NASA FIRMS
              thermal anomalies, and Sentinel-5P gas columns into deterministic Uber H3 Resolution-8 spatial cells.
            </p>

            <div className="demo-modal-flow">
              <div className="demo-flow-step">
                <div className="demo-step-num">01</div>
                <div className="demo-step-content">
                  <strong>Multi-Source Fusion</strong>
                  <span>Heterogeneous telemetry calibrated in real-time</span>
                </div>
              </div>
              <div className="demo-flow-step">
                <div className="demo-step-num">02</div>
                <div className="demo-step-content">
                  <strong>H3 Spatial Discretization</strong>
                  <span>Eliminating urban sensor blind spots at ~0.7 km²</span>
                </div>
              </div>
              <div className="demo-flow-step">
                <div className="demo-step-num">03</div>
                <div className="demo-step-content">
                  <strong>Early Municipal Action</strong>
                  <span>Targeted smog mitigation before hazardous peaks</span>
                </div>
              </div>
            </div>

            <div className="demo-modal-actions">
              <button
                className="demo-action-btn primary"
                onClick={() => navigate('/dashboard')}
                type="button"
              >
                <span>Enter Live Command Center</span>
                <ArrowRight size={16} />
              </button>
              <button
                className="demo-action-btn secondary"
                onClick={scrollToOperations}
                type="button"
              >
                <Layers size={16} />
                <span>Explore Ingestion Architecture</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
};

export default HeroSection;
