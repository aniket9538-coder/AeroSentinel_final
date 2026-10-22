import React, { useState, useEffect } from 'react';
import { Wind } from 'lucide-react';
import { useApp } from '../../store/AppContext';
import { LocationDto, AirQualityToolResult } from '../../types/assistant';
import { AirQualityObservationResponse } from '../../types/airQuality';
import { assistantService } from '../../services/assistant.service';
import { airQualityApi } from '../../services/airQualityApi';
import { CitizenLocationSelector } from '../../components/citizen/CitizenLocationSelector';
import { CitizenAirQualityCard } from '../../components/citizen/CitizenAirQualityCard';

export interface CitizenAirQualityPageProps {
  currentLocale?: string;
}

export const CitizenAirQualityPage: React.FC<CitizenAirQualityPageProps> = () => {
  const { selectedCity } = useApp();

  const [location, setLocation] = useState<LocationDto>(() => ({
    cityName: selectedCity?.name || 'Pune',
    latitude: selectedCity?.latitude || 18.5314,
    longitude: selectedCity?.longitude || 73.8446,
  }));

  const [airQuality, setAirQuality] = useState<AirQualityToolResult | null>(null);
  const [stations, setStations] = useState<AirQualityObservationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchAirData = async (loc: LocationDto) => {
    setLoading(true);
    try {
      const data = await assistantService.getAirQuality(loc);
      setAirQuality(data);
      setError(null);
    } catch (err: any) {
      setError(err.message || 'Failed to fetch air quality');
    }

    if (selectedCity?.id) {
      airQualityApi.getLatestAirQuality(selectedCity.id)
        .then((resp) => {
          if (resp?.observations) {
            setStations(resp.observations);
          }
        })
        .catch(() => {});
    }

    setLoading(false);
  };

  useEffect(() => {
    fetchAirData(location);
  }, [location]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      <section className="citizen-hero-banner">
        <div className="citizen-hero-eyebrow">
          <Wind size={13} />
          <span>Ground Reference Monitor Network</span>
        </div>
        <h1 className="citizen-hero-title">Air Quality Telemetry</h1>
        <p className="citizen-hero-sub">
          Live ground observations from Central Pollution Control Board (CPCB), state pollution control boards, and OpenAQ calibrated telemetry monitors across India.
        </p>
      </section>

      <CitizenLocationSelector
        currentLocation={location}
        onLocationChange={(newLoc) => setLocation(newLoc)}
      />

      <div className="citizen-grid-container">
        <div className="citizen-col-12">
          <CitizenAirQualityCard
            airQuality={airQuality}
            stationList={stations}
            loading={loading && !airQuality}
            error={error}
            onRefresh={() => fetchAirData(location)}
          />
        </div>
      </div>
    </div>
  );
};
