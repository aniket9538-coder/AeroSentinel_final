import React, { useState, useEffect } from 'react';
import {
  MapPin,
  Star,
  Plus,
  Trash2,
  CheckCircle2,
  AlertCircle,
  Navigation,
} from 'lucide-react';
import { SavedLocation } from '../../types/citizenPreferences';
import {
  fetchSavedLocations,
  createSavedLocation,
  setDefaultSavedLocation,
  deleteSavedLocation,
} from '../../services/citizenPreferencesApi';
import { useApp } from '../../store/AppContext';
import { getTranslation } from '../../i18n/citizenTranslations';

interface CitizenSavedLocationsCardProps {
  currentLocale?: string;
  onSelectLocation?: (lat: number, lon: number, cityName: string) => void;
}

export const CitizenSavedLocationsCard: React.FC<CitizenSavedLocationsCardProps> = ({
  currentLocale = 'en',
  onSelectLocation,
}) => {
  const t = getTranslation(currentLocale);
  const { selectedCity, setSelectedCity, availableCities } = useApp();

  const [locations, setLocations] = useState<SavedLocation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Form modal/expand
  const [isAdding, setIsAdding] = useState(false);
  const [newLabel, setNewLabel] = useState('');
  const [newCity, setNewCity] = useState(selectedCity?.name || 'Pune');
  const [newLat, setNewLat] = useState('18.5204');
  const [newLon, setNewLon] = useState('73.8567');
  const [isDefault, setIsDefault] = useState(false);
  const [saving, setSaving] = useState(false);

  const loadLocations = async () => {
    try {
      setError(null);
      const data = await fetchSavedLocations();
      setLocations(data);
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to load saved locations.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadLocations();
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    const lat = parseFloat(newLat);
    const lon = parseFloat(newLon);

    if (isNaN(lat) || isNaN(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
      setError('Coordinates must be between -90..90 (latitude) and -180..180 (longitude).');
      return;
    }

    setSaving(true);
    try {
      await createSavedLocation({
        label: newLabel,
        cityName: newCity,
        latitude: lat,
        longitude: lon,
        isDefault,
      });
      setNewLabel('');
      setIsAdding(false);
      await loadLocations();
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to create saved location.');
    } finally {
      setSaving(false);
    }
  };

  const handleSetDefault = async (id: string) => {
    try {
      await setDefaultSavedLocation(id);
      await loadLocations();
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to update default location.');
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteSavedLocation(id);
      await loadLocations();
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to delete saved location.');
    }
  };

  const handleQuickSelect = (loc: SavedLocation) => {
    const matched = availableCities.find((c) => c.name.toLowerCase() === loc.cityName.toLowerCase());
    if (matched) {
      setSelectedCity(matched);
    }
    if (onSelectLocation) {
      onSelectLocation(loc.latitude, loc.longitude, loc.cityName);
    }
  };

  return (
    <div
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border-medium)',
        borderRadius: '1rem',
        padding: '1.5rem',
        boxShadow: 'var(--shadow-sm)',
        display: 'flex',
        flexDirection: 'column',
        gap: '1rem',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
        <div>
          <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 700, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <MapPin size={18} style={{ color: 'var(--brand-primary)' }} />
            {t.savedLocationsTitle}
          </h3>
          <p style={{ margin: '0.2rem 0 0', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            {t.savedLocationsSubtitle}
          </p>
        </div>

        <button
          onClick={() => setIsAdding(!isAdding)}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '0.4rem',
            padding: '0.45rem 0.85rem',
            borderRadius: '0.5rem',
            background: 'var(--brand-primary)',
            color: '#ffffff',
            border: 'none',
            fontSize: '0.8rem',
            fontWeight: 600,
            cursor: 'pointer',
          }}
        >
          <Plus size={14} />
          {t.addLocationBtn}
        </button>
      </div>

      {error && (
        <div style={{ padding: '0.65rem 0.85rem', borderRadius: '0.5rem', background: '#fee2e2', border: '1px solid #fca5a5', color: '#b91c1c', fontSize: '0.8rem' }}>
          {error}
        </div>
      )}

      {/* Add Location Form */}
      {isAdding && (
        <form onSubmit={handleCreate} style={{ padding: '1rem', background: 'var(--bg-surface-elevated)', borderRadius: '0.75rem', border: '1px solid var(--border-subtle)', display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          <div style={{ fontWeight: 600, fontSize: '0.85rem', color: 'var(--text-primary)' }}>New Saved Location</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '0.75rem' }}>
            <div>
              <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '0.25rem' }}>Location Label</label>
              <input
                type="text"
                placeholder="e.g. Home, Workplace, Parents"
                value={newLabel}
                onChange={(e) => setNewLabel(e.target.value)}
                required
                style={{ width: '100%', padding: '0.45rem 0.65rem', borderRadius: '0.375rem', border: '1px solid var(--border-medium)', background: 'var(--bg-surface)', color: 'var(--text-primary)', fontSize: '0.85rem' }}
              />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '0.25rem' }}>City</label>
              <select
                value={newCity}
                onChange={(e) => setNewCity(e.target.value)}
                style={{ width: '100%', padding: '0.45rem 0.65rem', borderRadius: '0.375rem', border: '1px solid var(--border-medium)', background: 'var(--bg-surface)', color: 'var(--text-primary)', fontSize: '0.85rem' }}
              >
                {availableCities.map((c) => (
                  <option key={c.name} value={c.name}>{c.name}</option>
                ))}
              </select>
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '0.25rem' }}>Latitude</label>
              <input
                type="number"
                step="0.0001"
                value={newLat}
                onChange={(e) => setNewLat(e.target.value)}
                required
                style={{ width: '100%', padding: '0.45rem 0.65rem', borderRadius: '0.375rem', border: '1px solid var(--border-medium)', background: 'var(--bg-surface)', color: 'var(--text-primary)', fontSize: '0.85rem' }}
              />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '0.25rem' }}>Longitude</label>
              <input
                type="number"
                step="0.0001"
                value={newLon}
                onChange={(e) => setNewLon(e.target.value)}
                required
                style={{ width: '100%', padding: '0.45rem 0.65rem', borderRadius: '0.375rem', border: '1px solid var(--border-medium)', background: 'var(--bg-surface)', color: 'var(--text-primary)', fontSize: '0.85rem' }}
              />
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.5rem', marginTop: '0.25rem' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.8rem', color: 'var(--text-secondary)', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={isDefault}
                onChange={(e) => setIsDefault(e.target.checked)}
              />
              <span>Set as default startup location</span>
            </label>

            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <button
                type="button"
                onClick={() => setIsAdding(false)}
                style={{ padding: '0.4rem 0.75rem', borderRadius: '0.375rem', border: '1px solid var(--border-medium)', background: 'transparent', color: 'var(--text-secondary)', fontSize: '0.8rem', cursor: 'pointer' }}
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={saving}
                style={{ padding: '0.4rem 0.85rem', borderRadius: '0.375rem', border: 'none', background: 'var(--brand-primary)', color: '#ffffff', fontWeight: 600, fontSize: '0.8rem', cursor: 'pointer' }}
              >
                {saving ? 'Saving...' : 'Save Location'}
              </button>
            </div>
          </div>
        </form>
      )}

      {/* Locations List */}
      {loading ? (
        <div style={{ textAlign: 'center', padding: '1rem', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
          Loading saved locations...
        </div>
      ) : locations.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '1.5rem', color: 'var(--text-muted)', background: 'var(--bg-surface-elevated)', borderRadius: '0.75rem', fontSize: '0.85rem' }}>
          No saved locations yet. Click "+ Add Saved Location" to track home, workplace, or family zones.
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '0.75rem' }}>
          {locations.map((loc) => (
            <div
              key={loc.id}
              style={{
                padding: '0.85rem 1rem',
                borderRadius: '0.75rem',
                background: 'var(--bg-surface-elevated)',
                border: '1px solid var(--border-subtle)',
                display: 'flex',
                flexDirection: 'column',
                gap: '0.5rem',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                  <span style={{ fontWeight: 700, fontSize: '0.9rem', color: 'var(--text-primary)' }}>{loc.label}</span>
                  {loc.isDefault && (
                    <span style={{ fontSize: '0.65rem', fontWeight: 700, padding: '0.15rem 0.45rem', borderRadius: '9999px', background: '#dcfce7', color: '#166534', border: '1px solid #bbf7d0' }}>
                      {t.defaultBadge}
                    </span>
                  )}
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                  {!loc.isDefault && (
                    <button
                      onClick={() => handleSetDefault(loc.id)}
                      title="Set as Default"
                      style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', padding: '0.2rem' }}
                    >
                      <Star size={14} />
                    </button>
                  )}
                  <button
                    onClick={() => handleDelete(loc.id)}
                    title="Delete Location"
                    style={{ background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer', padding: '0.2rem' }}
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              </div>

              <div style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>
                {loc.cityName} • {loc.latitude.toFixed(4)}, {loc.longitude.toFixed(4)}
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                <span>H3: <code style={{ fontFamily: 'monospace' }}>{loc.h3Index}</code></span>
                <button
                  onClick={() => handleQuickSelect(loc)}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: 'var(--brand-primary)',
                    fontWeight: 600,
                    cursor: 'pointer',
                    textDecoration: 'underline',
                  }}
                >
                  View Hyperlocal
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
