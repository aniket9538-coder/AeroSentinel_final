import React, { useState } from 'react';
import { MapPin, Navigation, AlertCircle, Check, Search } from 'lucide-react';
import { useApp, CityConfig } from '../../store/AppContext';
import { LocationDto } from '../../types/assistant';

export interface CitizenLocationSelectorProps {
  currentLocation?: LocationDto;
  onLocationChange: (location: LocationDto) => void;
}

export const CitizenLocationSelector: React.FC<CitizenLocationSelectorProps> = ({
  currentLocation,
  onLocationChange,
}) => {
  const { selectedCity, setSelectedCity, availableCities } = useApp();
  const [geoLoading, setGeoLoading] = useState(false);
  const [geoError, setGeoError] = useState<string | null>(null);
  const [customLat, setCustomLat] = useState<string>('');
  const [customLon, setCustomLon] = useState<string>('');
  const [showCustomCoords, setShowCustomCoords] = useState(false);

  // Handle HTML5 Geolocation with strict user permission & truthful errors
  const handleUseMyLocation = () => {
    if (!navigator.geolocation) {
      setGeoError('Geolocation is not supported by your browser.');
      return;
    }

    setGeoLoading(true);
    setGeoError(null);

    navigator.geolocation.getCurrentPosition(
      (position) => {
        setGeoLoading(false);
        const lat = parseFloat(position.coords.latitude.toFixed(4));
        const lon = parseFloat(position.coords.longitude.toFixed(4));

        // Determine if coordinates match any known city
        let matchedCity: CityConfig | undefined;
        for (const c of availableCities) {
          if (c.latitude != null && c.longitude != null) {
            const dLat = Math.abs(c.latitude - lat);
            const dLon = Math.abs(c.longitude - lon);
            if (dLat < 0.25 && dLon < 0.25) {
              matchedCity = c;
              break;
            }
          }
        }

        if (matchedCity) {
          setSelectedCity(matchedCity);
          onLocationChange({
            cityName: matchedCity.name,
            latitude: lat,
            longitude: lon,
          });
        } else {
          onLocationChange({
            cityName: `GPS (${lat}, ${lon})`,
            latitude: lat,
            longitude: lon,
          });
        }
      },
      (error) => {
        setGeoLoading(false);
        switch (error.code) {
          case error.PERMISSION_DENIED:
            setGeoError('Location permission denied. Please select a city manually.');
            break;
          case error.POSITION_UNAVAILABLE:
            setGeoError('Location information is currently unavailable.');
            break;
          case error.TIMEOUT:
            setGeoError('Location request timed out. Please try again or select a city.');
            break;
          default:
            setGeoError('Unable to retrieve device location.');
            break;
        }
      },
      { timeout: 10000, enableHighAccuracy: true }
    );
  };

  const handleApplyCustomCoords = (e: React.FormEvent) => {
    e.preventDefault();
    const lat = parseFloat(customLat);
    const lon = parseFloat(customLon);

    if (isNaN(lat) || isNaN(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
      setGeoError('Invalid coordinates. Latitude must be -90 to 90, Longitude -180 to 180.');
      return;
    }

    setGeoError(null);
    onLocationChange({
      cityName: `Coordinates (${lat}, ${lon})`,
      latitude: lat,
      longitude: lon,
    });
  };

  const activeName = currentLocation?.cityName || selectedCity?.name || 'Pune';
  const activeLat = currentLocation?.latitude ?? selectedCity?.latitude ?? 18.5314;
  const activeLon = currentLocation?.longitude ?? selectedCity?.longitude ?? 73.8446;

  return (
    <div className="citizen-card" style={{ padding: '1.1rem 1.25rem' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.75rem' }}>
        {/* Left: Active Location Details */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem' }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: '36px',
              height: '36px',
              borderRadius: '10px',
              background: 'var(--brand-surface)',
              color: 'var(--brand-primary)',
            }}
          >
            <MapPin size={20} />
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <span style={{ fontSize: '1.05rem', fontWeight: 800, color: 'var(--text-primary)' }}>
                {activeName}
              </span>
              <span
                style={{
                  fontSize: '0.72rem',
                  fontWeight: 600,
                  color: 'var(--text-muted)',
                  background: 'var(--bg-surface)',
                  padding: '0.15rem 0.45rem',
                  borderRadius: '4px',
                  border: '1px solid var(--border-subtle)',
                }}
              >
                {activeLat.toFixed(4)}°N, {activeLon.toFixed(4)}°E
              </span>
            </div>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>
              Selected spatial boundary for environmental telemetry and predictions
            </div>
          </div>
        </div>

        {/* Right: Quick City Pills & GPS action */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
          {availableCities.map((city) => (
            <button
              key={city.id || city.name}
              onClick={() => {
                setSelectedCity(city);
                onLocationChange({
                  cityName: city.name,
                  latitude: city.latitude,
                  longitude: city.longitude,
                });
                setGeoError(null);
              }}
              type="button"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.3rem',
                padding: '0.35rem 0.75rem',
                borderRadius: '16px',
                border: activeName === city.name ? '1px solid var(--brand-primary)' : '1px solid var(--border-subtle)',
                background: activeName === city.name ? 'var(--brand-surface)' : 'var(--bg-surface)',
                color: activeName === city.name ? 'var(--brand-primary)' : 'var(--text-secondary)',
                fontWeight: activeName === city.name ? 700 : 500,
                fontSize: '0.8rem',
                cursor: 'pointer',
                transition: 'all 0.15s ease',
              }}
            >
              <span>{city.name}</span>
              {activeName === city.name && <Check size={12} />}
            </button>
          ))}

          {/* Use My Location Button */}
          <button
            onClick={handleUseMyLocation}
            disabled={geoLoading}
            type="button"
            title="Request current GPS location"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.35rem',
              padding: '0.35rem 0.75rem',
              borderRadius: '16px',
              border: '1px solid var(--border-medium)',
              background: 'var(--bg-surface)',
              color: 'var(--text-primary)',
              fontWeight: 600,
              fontSize: '0.8rem',
              cursor: geoLoading ? 'wait' : 'pointer',
            }}
          >
            <Navigation size={13} style={{ color: '#0ea5e9' }} />
            <span>{geoLoading ? 'Acquiring GPS...' : 'Use GPS'}</span>
          </button>

          {/* Toggle Custom Coords */}
          <button
            onClick={() => setShowCustomCoords(!showCustomCoords)}
            type="button"
            style={{
              background: 'transparent',
              border: 'none',
              color: 'var(--text-muted)',
              fontSize: '0.78rem',
              cursor: 'pointer',
              textDecoration: 'underline',
              padding: '0.2rem 0.4rem',
            }}
          >
            {showCustomCoords ? 'Cancel' : 'Enter Coords'}
          </button>
        </div>
      </div>

      {/* Optional Custom Coordinates Form */}
      {showCustomCoords && (
        <form
          onSubmit={handleApplyCustomCoords}
          style={{
            marginTop: '0.85rem',
            paddingTop: '0.85rem',
            borderTop: '1px solid var(--border-subtle)',
            display: 'flex',
            alignItems: 'center',
            gap: '0.65rem',
            flexWrap: 'wrap',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
            <label style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', fontWeight: 600 }}>Lat:</label>
            <input
              type="number"
              step="any"
              placeholder="e.g. 18.5204"
              value={customLat}
              onChange={(e) => setCustomLat(e.target.value)}
              style={{
                width: '110px',
                padding: '0.35rem 0.55rem',
                borderRadius: '6px',
                background: 'var(--bg-primary)',
                border: '1px solid var(--border-medium)',
                color: 'var(--text-primary)',
                fontSize: '0.82rem',
              }}
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
            <label style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', fontWeight: 600 }}>Lon:</label>
            <input
              type="number"
              step="any"
              placeholder="e.g. 73.8567"
              value={customLon}
              onChange={(e) => setCustomLon(e.target.value)}
              style={{
                width: '110px',
                padding: '0.35rem 0.55rem',
                borderRadius: '6px',
                background: 'var(--bg-primary)',
                border: '1px solid var(--border-medium)',
                color: 'var(--text-primary)',
                fontSize: '0.82rem',
              }}
            />
          </div>

          <button
            type="submit"
            style={{
              padding: '0.35rem 0.85rem',
              borderRadius: '6px',
              background: 'var(--brand-primary)',
              color: '#ffffff',
              border: 'none',
              fontWeight: 700,
              fontSize: '0.8rem',
              cursor: 'pointer',
            }}
          >
            Apply Coordinates
          </button>
        </form>
      )}

      {/* Geolocation / Validation Error Banner */}
      {geoError && (
        <div
          role="alert"
          style={{
            marginTop: '0.65rem',
            padding: '0.5rem 0.75rem',
            borderRadius: '8px',
            background: 'rgba(239, 68, 68, 0.1)',
            border: '1px solid rgba(239, 68, 68, 0.25)',
            color: '#ef4444',
            fontSize: '0.8rem',
            display: 'flex',
            alignItems: 'center',
            gap: '0.45rem',
          }}
        >
          <AlertCircle size={14} />
          <span>{geoError}</span>
        </div>
      )}
    </div>
  );
};
