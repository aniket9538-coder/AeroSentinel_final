import React, { useState, useEffect } from 'react';
import { AlertTriangle } from 'lucide-react';
import { useApp } from '../../store/AppContext';
import { LocationDto, OfficialWarningToolResult } from '../../types/assistant';
import { assistantService } from '../../services/assistant.service';
import { CitizenLocationSelector } from '../../components/citizen/CitizenLocationSelector';
import { CitizenWarningsCard } from '../../components/citizen/CitizenWarningsCard';

export interface CitizenWarningsPageProps {
  currentLocale?: string;
}

export const CitizenWarningsPage: React.FC<CitizenWarningsPageProps> = () => {
  const { selectedCity } = useApp();

  const [location, setLocation] = useState<LocationDto>(() => ({
    cityName: selectedCity?.name || 'Pune',
    latitude: selectedCity?.latitude || 18.5314,
    longitude: selectedCity?.longitude || 73.8446,
  }));

  const [warnings, setWarnings] = useState<OfficialWarningToolResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchWarningsData = async (loc: LocationDto) => {
    setLoading(true);
    try {
      const data = await assistantService.getWarnings(loc);
      setWarnings(data);
      setError(null);
    } catch (err: any) {
      setError(err.message || 'Failed to fetch official warnings');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchWarningsData(location);
  }, [location]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      <section className="citizen-hero-banner">
        <div className="citizen-hero-eyebrow">
          <AlertTriangle size={13} />
          <span>Statutory Disaster Feeds</span>
        </div>
        <h1 className="citizen-hero-title">Official Government Warnings</h1>
        <p className="citizen-hero-sub">
          Verified weather, cyclone, heatwave, and environmental emergency bulletins published directly by the India Meteorological Department (IMD) and National Disaster Management Authority (NDMA SACHET).
        </p>
      </section>

      <CitizenLocationSelector
        currentLocation={location}
        onLocationChange={(newLoc) => setLocation(newLoc)}
      />

      <div className="citizen-grid-container">
        <div className="citizen-col-12">
          <CitizenWarningsCard
            warningsResult={warnings}
            loading={loading && !warnings}
            error={error}
            onRefresh={() => fetchWarningsData(location)}
          />
        </div>
      </div>
    </div>
  );
};
