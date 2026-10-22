import React, { useState, useRef, useEffect } from 'react';
import {
  Send,
  Sparkles,
  MapPin,
  RefreshCw,
  AlertCircle,
  ExternalLink,
  Shield,
  HelpCircle,
  Clock,
  CheckCircle,
  Info,
} from 'lucide-react';
import { useApp } from '../../store/AppContext';
import { assistantService } from '../../services/assistant.service';
import {
  AssistantChatResponse,
  LocationDto,
  SourceReferenceDto,
} from '../../types/assistant';

interface ChatMessage {
  id: string;
  sender: 'user' | 'assistant';
  text: string;
  timestamp: string;
  responseDto?: AssistantChatResponse;
  isError?: boolean;
  isRateLimit?: boolean;
}

export interface CitizenAssistantPageProps {
  currentLocale?: string;
}

export const CitizenAssistantPage: React.FC<CitizenAssistantPageProps> = ({
  currentLocale = 'en',
}) => {
  const { selectedCity } = useApp();

  const [location, setLocation] = useState<LocationDto>(() => ({
    cityName: selectedCity?.name || 'Pune',
    latitude: selectedCity?.latitude || 18.5314,
    longitude: selectedCity?.longitude || 73.8446,
  }));

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputText, setInputText] = useState('');
  const [inFlight, setInFlight] = useState(false);
  const [rateLimitSeconds, setRateLimitSeconds] = useState<number | null>(null);
  const [clarificationCity, setClarificationCity] = useState('');

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Keep location aligned with global city selection
  useEffect(() => {
    if (selectedCity && selectedCity.name !== location.cityName) {
      setLocation({
        cityName: selectedCity.name,
        latitude: selectedCity.latitude,
        longitude: selectedCity.longitude,
      });
    }
  }, [selectedCity]);

  // Handle live 429 countdown timer
  useEffect(() => {
    if (rateLimitSeconds == null || rateLimitSeconds <= 0) return;
    const interval = setInterval(() => {
      setRateLimitSeconds((prev) => (prev != null && prev > 1 ? prev - 1 : null));
    }, 1000);
    return () => clearInterval(interval);
  }, [rateLimitSeconds]);

  // Auto-scroll to bottom of messages without disrupting user manual scrolling
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, inFlight]);

  // Starter Prompts
  const starterPrompts = [
    `What is the weather in ${location.cityName || 'Pune'} right now?`,
    `What is the weather forecast for tomorrow in ${location.cityName || 'Pune'}?`,
    `Are there any active official weather or disaster warnings for ${location.cityName || 'Pune'}?`,
    `What is the latest ground PM2.5 reading in ${location.cityName || 'Pune'}?`,
    `How old is the current environmental telemetry and which source provided it?`,
    `Is an experimental PM2.5 risk prediction available for my location?`,
  ];

  const handleSendMessage = async (queryText?: string) => {
    const textToSend = (queryText ?? inputText).trim();
    if (!textToSend || inFlight || rateLimitSeconds != null) return;

    if (textToSend.length > 500) {
      alert('Message exceeds maximum limit of 500 characters.');
      return;
    }

    const userMsgId = `user-${Date.now()}`;
    const userMsg: ChatMessage = {
      id: userMsgId,
      sender: 'user',
      text: textToSend,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    };

    setMessages((prev) => [...prev, userMsg]);
    setInputText('');
    setInFlight(true);

    try {
      const resp = await assistantService.chat({
        message: textToSend,
        location,
        locale: currentLocale,
      });

      const assistantMsg: ChatMessage = {
        id: `asst-${Date.now()}`,
        sender: 'assistant',
        text: resp.answer,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        responseDto: resp,
      };

      setMessages((prev) => [...prev, assistantMsg]);
    } catch (err: any) {
      if (err.response?.status === 429) {
        setRateLimitSeconds(60);
        const rateLimitMsg: ChatMessage = {
          id: `asst-err-${Date.now()}`,
          sender: 'assistant',
          text: 'Rate limit exceeded: You have submitted too many requests in a short period. Please wait 60 seconds before sending another message.',
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
          isRateLimit: true,
        };
        setMessages((prev) => [...prev, rateLimitMsg]);
      } else {
        const errorMsg: ChatMessage = {
          id: `asst-err-${Date.now()}`,
          sender: 'assistant',
          text: err.response?.data?.message || err.message || 'Unable to connect to assistant service.',
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
          isError: true,
        };
        setMessages((prev) => [...prev, errorMsg]);
      }
    } finally {
      setInFlight(false);
      textareaRef.current?.focus();
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  const handleClarificationSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!clarificationCity.trim()) return;

    const updatedLoc: LocationDto = {
      ...location,
      cityName: clarificationCity.trim(),
    };
    setLocation(updatedLoc);
    setClarificationCity('');
    handleSendMessage(`What are the conditions in ${clarificationCity.trim()}?`);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', maxWidth: '1000px', margin: '0 auto' }}>
      {/* Intro Header */}
      <div>
        <div className="citizen-hero-eyebrow">
          <Sparkles size={13} />
          <span>Grounded Natural-Language Intelligence</span>
        </div>
        <h1 className="citizen-hero-title">Ask AeroSentinel</h1>
        <p className="citizen-hero-sub">
          Ask questions about local weather, forward forecasts, official disaster bulletins, ground air quality observations, or data freshness. All responses are strictly grounded in verified environmental tools.
        </p>
      </div>

      {/* Selected Location Context Strip */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: '0.65rem',
          padding: '0.65rem 1rem',
          background: 'var(--bg-card)',
          borderRadius: '12px',
          border: '1px solid var(--border-subtle)',
          fontSize: '0.82rem',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem' }}>
          <MapPin size={15} style={{ color: 'var(--brand-primary)' }} />
          <span>
            Active Context: <strong>{location.cityName || 'Pune'}</strong> ({(location.latitude ?? 18.53).toFixed(2)}°N, {(location.longitude ?? 73.84).toFixed(2)}°E)
          </span>
        </div>
        <div style={{ color: 'var(--text-muted)', fontSize: '0.78rem' }}>
          Language: <strong>{currentLocale.toUpperCase()}</strong>
        </div>
      </div>

      {/* Rate Limit Active Notice */}
      {rateLimitSeconds != null && (
        <div className="citizen-rate-limit-banner" role="alert">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <AlertCircle size={18} />
            <span>Rate limit active. Please wait <strong>{rateLimitSeconds}s</strong> before sending another question.</span>
          </div>
        </div>
      )}

      {/* Chat Container */}
      <div className="citizen-chat-container">
        {/* Messages Thread */}
        <div className="citizen-chat-messages" role="log" aria-live="polite">
          {messages.length === 0 ? (
            /* Empty Conversation Starter */
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', gap: '1.25rem', textAlign: 'center', padding: '2rem 1rem' }}>
              <div
                style={{
                  width: '56px',
                  height: '56px',
                  borderRadius: '16px',
                  background: 'var(--brand-surface)',
                  color: 'var(--brand-primary)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Sparkles size={28} />
              </div>
              <div>
                <h2 style={{ fontSize: '1.25rem', fontWeight: 800, color: 'var(--text-primary)', marginBottom: '0.35rem' }}>
                  What would you like to know?
                </h2>
                <p style={{ fontSize: '0.88rem', color: 'var(--text-secondary)', maxWidth: '480px' }}>
                  Choose a suggested query below or type your question in English, Hindi, or Marathi:
                </p>
              </div>

              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem', justifyContent: 'center', maxWidth: '680px' }}>
                {starterPrompts.map((prompt, idx) => (
                  <button
                    key={idx}
                    type="button"
                    className="citizen-quick-chip"
                    onClick={() => handleSendMessage(prompt)}
                  >
                    <span>{prompt}</span>
                  </button>
                ))}
              </div>
            </div>
          ) : (
            /* Message Thread */
            messages.map((msg) => {
              const isUser = msg.sender === 'user';
              const dto = msg.responseDto;

              return (
                <div key={msg.id} className={`citizen-message ${isUser ? 'user' : 'assistant'}`}>
                  <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginBottom: '0.2rem', paddingLeft: isUser ? 0 : '0.25rem', paddingRight: isUser ? '0.25rem' : 0 }}>
                    {isUser ? 'You' : 'AeroSentinel Assistant'} • {msg.timestamp}
                  </div>

                  <div className="citizen-bubble">
                    {msg.text}

                    {/* Needs Clarification Prompt Input */}
                    {dto?.status === 'NEEDS_CLARIFICATION' && (
                      <form onSubmit={handleClarificationSubmit} style={{ marginTop: '0.75rem', display: 'flex', gap: '0.5rem' }}>
                        <input
                          type="text"
                          placeholder="Type city (e.g. Pune, Mumbai, Delhi)..."
                          value={clarificationCity}
                          onChange={(e) => setClarificationCity(e.target.value)}
                          style={{
                            flex: 1,
                            padding: '0.4rem 0.65rem',
                            borderRadius: '8px',
                            background: 'var(--bg-primary)',
                            border: '1px solid var(--border-medium)',
                            color: 'var(--text-primary)',
                            fontSize: '0.85rem',
                          }}
                        />
                        <button
                          type="submit"
                          style={{
                            padding: '0.4rem 0.85rem',
                            borderRadius: '8px',
                            background: 'var(--brand-primary)',
                            color: '#ffffff',
                            border: 'none',
                            fontWeight: 700,
                            fontSize: '0.82rem',
                            cursor: 'pointer',
                          }}
                        >
                          Confirm
                        </button>
                      </form>
                    )}

                    {/* Sources Provenance Cards */}
                    {dto?.sources && dto.sources.length > 0 && (
                      <div className="citizen-sources-panel">
                        <div style={{ fontSize: '0.74rem', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                          Verified Sourced Evidence ({dto.sources.length})
                        </div>
                        {dto.sources.map((src, i) => (
                          <div key={i} className="citizen-source-card">
                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '0.5rem' }}>
                              <span style={{ fontWeight: 700, color: 'var(--text-primary)' }}>
                                {src.provider}
                              </span>
                              {src.freshness && (
                                <span
                                  className={`citizen-card-badge ${
                                    src.freshness === 'LIVE' ? 'badge-live' : src.freshness === 'STALE' ? 'badge-stale' : 'badge-unavailable'
                                  }`}
                                  style={{ fontSize: '0.65rem', padding: '0.1rem 0.35rem' }}
                                >
                                  {src.freshness}
                                </span>
                              )}
                            </div>

                            {src.dataType && (
                              <div style={{ color: 'var(--text-secondary)', fontSize: '0.74rem' }}>
                                Type: {src.dataType}
                              </div>
                            )}

                            {src.stationName && (
                              <div style={{ color: 'var(--text-secondary)', fontSize: '0.74rem' }}>
                                Station: {src.stationName} {src.stationCode ? `(${src.stationCode})` : ''}
                              </div>
                            )}

                            {src.timestamp && (
                              <div style={{ color: 'var(--text-muted)', fontSize: '0.7rem' }}>
                                Observation Time: {new Date(src.timestamp).toLocaleString()}
                              </div>
                            )}

                            {src.url && (
                              <a
                                href={src.url}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="citizen-source-link"
                                style={{ marginTop: '0.2rem' }}
                              >
                                <span>Official Source Link</span>
                                <ExternalLink size={10} />
                              </a>
                            )}
                          </div>
                        ))}
                      </div>
                    )}

                    {/* Contract Limitations Disclosures */}
                    {dto?.limitations && dto.limitations.length > 0 && (
                      <div style={{ marginTop: '0.65rem', paddingTop: '0.5rem', borderTop: '1px solid var(--border-subtle)', fontSize: '0.74rem', color: 'var(--text-muted)', display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
                        {dto.limitations.map((lim, idx) => (
                          <div key={idx} style={{ display: 'flex', alignItems: 'flex-start', gap: '0.35rem' }}>
                            <Info size={11} style={{ marginTop: '2px', flexShrink: 0 }} />
                            <span>{lim}</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              );
            })
          )}

          {/* In-Flight Status */}
          {inFlight && (
            <div className="citizen-message assistant">
              <div className="citizen-bubble" style={{ display: 'flex', alignItems: 'center', gap: '0.65rem', color: 'var(--text-secondary)' }}>
                <RefreshCw size={15} className="animate-spin" />
                <span>Consulting verified environmental tools...</span>
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Input Bar */}
        <div className="citizen-chat-input-bar">
          <textarea
            ref={textareaRef}
            className="citizen-chat-textarea"
            rows={1}
            placeholder={`Ask about weather, forecast, air quality, or warnings in ${location.cityName || 'your area'}... (Enter to send)`}
            value={inputText}
            onChange={(e) => setInputText(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={inFlight || rateLimitSeconds != null}
            aria-label="Ask AeroSentinel a question"
            maxLength={500}
            id="citizen-chat-input"
          />

          <button
            className="citizen-chat-send-btn"
            onClick={() => handleSendMessage()}
            disabled={!inputText.trim() || inFlight || rateLimitSeconds != null}
            aria-label="Send question"
            type="button"
            id="citizen-chat-send-btn"
          >
            <Send size={18} />
          </button>
        </div>
      </div>
    </div>
  );
};
