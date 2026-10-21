import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MessageSquare, ArrowRight, Sparkles, RefreshCw, AlertCircle, ExternalLink } from 'lucide-react';
import { LocationDto, AssistantChatResponse } from '../../types/assistant';
import { assistantService } from '../../services/assistant.service';

export interface CitizenQuickAssistantProps {
  location?: LocationDto;
  locale?: string;
}

export const CitizenQuickAssistant: React.FC<CitizenQuickAssistantProps> = ({
  location,
  locale = 'en',
}) => {
  const navigate = useNavigate();
  const [activeQuery, setActiveQuery] = useState<string | null>(null);
  const [response, setResponse] = useState<AssistantChatResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const locName = location?.cityName || 'my area';

  const promptSuggestions = [
    `What's the weather in ${locName} right now?`,
    `What will the weather be like this evening in ${locName}?`,
    `Show the latest air-quality reading for ${locName}.`,
    `Are any official warnings available for ${locName}?`,
    `Why is this air quality reading stale?`,
  ];

  const handlePromptClick = async (promptText: string) => {
    setActiveQuery(promptText);
    setLoading(true);
    setError(null);
    setResponse(null);

    try {
      const resp = await assistantService.chat({
        message: promptText,
        location,
        locale,
      });
      setResponse(resp);
    } catch (err: any) {
      if (err.response?.status === 429) {
        setError('Rate limit reached (maximum 30 req/min). Please wait 60 seconds.');
      } else {
        setError(err.message || 'Failed to obtain assistant answer.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="citizen-quick-assistant" role="region" aria-label="Ask AeroSentinel Quick Assistant">
      <div className="citizen-quick-title-row">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <Sparkles size={18} style={{ color: 'var(--brand-primary)' }} />
          <span style={{ fontSize: '1rem', fontWeight: 800, color: 'var(--text-primary)' }}>
            Ask AeroSentinel Assistant
          </span>
        </div>

        <button
          onClick={() => navigate('/citizen/assistant')}
          type="button"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '0.35rem',
            background: 'transparent',
            border: 'none',
            color: 'var(--brand-primary)',
            fontSize: '0.82rem',
            fontWeight: 700,
            cursor: 'pointer',
          }}
        >
          <span>Open Full Conversational UI</span>
          <ArrowRight size={14} />
        </button>
      </div>

      <div style={{ fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
        Get grounded, source-verified environmental answers supported by real tool observations:
      </div>

      {/* Suggestion Chips */}
      <div className="citizen-quick-chips">
        {promptSuggestions.map((prompt, idx) => (
          <button
            key={idx}
            type="button"
            className="citizen-quick-chip"
            onClick={() => handlePromptClick(prompt)}
            disabled={loading}
          >
            <MessageSquare size={13} style={{ opacity: 0.7 }} />
            <span>{prompt}</span>
          </button>
        ))}
      </div>

      {/* Loading Indicator */}
      {loading && (
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--brand-primary)', fontSize: '0.85rem', padding: '0.5rem 0' }}>
          <RefreshCw size={15} className="animate-spin" />
          <span>Querying verified meteorological and air-quality tools for {locName}...</span>
        </div>
      )}

      {/* Error Notice */}
      {error && (
        <div
          role="alert"
          style={{
            padding: '0.65rem 0.85rem',
            borderRadius: '8px',
            background: 'rgba(239, 68, 68, 0.1)',
            border: '1px solid rgba(239, 68, 68, 0.25)',
            color: '#ef4444',
            fontSize: '0.82rem',
            display: 'flex',
            alignItems: 'center',
            gap: '0.4rem',
          }}
        >
          <AlertCircle size={15} />
          <span>{error}</span>
        </div>
      )}

      {/* Real Answer Card */}
      {response && !loading && (
        <div
          style={{
            background: 'var(--bg-surface)',
            border: '1px solid var(--border-medium)',
            borderRadius: '12px',
            padding: '1rem',
            display: 'flex',
            flexDirection: 'column',
            gap: '0.65rem',
          }}
        >
          <div style={{ fontSize: '0.78rem', fontWeight: 700, color: 'var(--brand-primary)', textTransform: 'uppercase' }}>
            Answer to: “{activeQuery}”
          </div>
          <div style={{ fontSize: '0.9rem', color: 'var(--text-primary)', lineHeight: 1.55 }}>
            {response.answer}
          </div>

          {/* Sources List */}
          {response.sources && response.sources.length > 0 && (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.4rem', paddingTop: '0.4rem', borderTop: '1px solid var(--border-subtle)' }}>
              {response.sources.map((src, i) => (
                <span
                  key={i}
                  style={{
                    fontSize: '0.72rem',
                    padding: '0.2rem 0.45rem',
                    borderRadius: '4px',
                    background: 'var(--bg-surface-elevated)',
                    border: '1px solid var(--border-subtle)',
                    color: 'var(--text-secondary)',
                  }}
                >
                  {src.provider} {src.freshness ? `(${src.freshness})` : ''}
                </span>
              ))}
            </div>
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '0.25rem' }}>
            <button
              onClick={() => navigate('/citizen/assistant')}
              type="button"
              style={{
                fontSize: '0.78rem',
                color: 'var(--brand-primary)',
                background: 'transparent',
                border: 'none',
                cursor: 'pointer',
                fontWeight: 700,
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.25rem',
              }}
            >
              <span>Continue in Chat</span>
              <ArrowRight size={13} />
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
