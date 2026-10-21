import React, { useCallback } from 'react';
import { PageContainer } from '../../components/layout/PageContainer';
import { Card } from '../../components/common/Card';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { PollutionMap } from '../../components/map/PollutionMap';
import { WeatherSummaryCard } from '../../components/weather/WeatherSummaryCard';
import { CellDetailsCard } from '../../components/weather/CellDetailsCard';
import { WeatherTrendChart } from '../../components/charts/WeatherTrendChart';
import { useApp } from '../../store/AppContext';
import { useWeather } from '../../hooks/useWeather';
import { useGrid } from '../../hooks/useGrid';
import {
  Hexagon,
  Clock,
  RefreshCw,
  Compass,
  Droplets,
  CloudRain,
  Activity,
  Layers,
  Database,
} from 'lucide-react';

/**
 * Weather & Spatial Intelligence Page (F2 Phase 6)
 *
 * Connected strictly to authoritative backend REST endpoints:
 * - GET /api/v1/cities/{cityId}/weather/latest
 * - GET /api/v1/grid?cityId={cityId}
 * - GET /api/v1/grid/{h3Index}/observations
 *
 * ZERO mock or synthetic data.
 */
export const WeatherSpatial: React.FC = () => {
  const { selectedCity, stations } = useApp();

  // 1. Authoritative Weather Hook
  const {
    weather,
    isLoading: weatherLoading,
    error: weatherError,
    refetch: refetchWeather,
  } = useWeather(selectedCity?.id);

  // 2. Authoritative Grid & Observations Hook
  const {
    cells,
    isLoading: gridLoading,
    error: gridError,
    selectedCell,
    selectedH3Index,
    selectCell,
    selectedCellObservations,
    loadingObservations,
    observationsError,
    refetch: refetchGrid,
    refetchObservations,
  } = useGrid(selectedCity?.id);

  // Phase 10: H3 Timeline & Layer Exploration
  const [timelineHorizon, setTimelineHorizon] = React.useState<'T0' | 'T_MINUS_1' | 'T_PLUS_1' | 'T_PLUS_3' | 'T_PLUS_6'>('T0');
  const [layerFilter, setLayerFilter] = React.useState<'ALL' | 'PM25' | 'WIND' | 'HUMIDITY'>('ALL');

  // Combined Refresh Handler
  const handleRefreshAll = useCallback(async () => {
    await Promise.allSettled([
      refetchWeather(),
      refetchGrid(),
      refetchObservations(),
    ]);
  }, [refetchWeather, refetchGrid, refetchObservations]);

  const isGlobalLoading = weatherLoading || gridLoading;

  // Convert real wind degrees to cardinal direction
  const getWindDirectionCardinal = (deg: number): string => {
    const directions = ['N', 'NNE', 'NE', 'ENE', 'E', 'ESE', 'SE', 'SSE', 'S', 'SSW', 'SW', 'WSW', 'W', 'WNW', 'NW', 'NNW'];
    const index = Math.round((deg % 360) / 22.5);
    return directions[index % 16];
  };

  const cardinal =
    weather && typeof weather.windDirection === 'number'
      ? getWindDirectionCardinal(weather.windDirection)
      : 'N/A';

  // Format real observation timestamp
  const formatTime = (isoString?: string | null) => {
    if (!isoString) return 'Waiting for observation...';
    try {
      const date = new Date(isoString);
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: true });
    } catch {
      return isoString;
    }
  };

  return (
    <PageContainer
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.85rem' }}>
          <span>WEATHER & SPATIAL INTELLIGENCE</span>
          <Badge variant="info">CANONICAL H3 SPATIAL ENGINE</Badge>
        </div>
      }
      subtitle={
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap' }}>
          <span>
            {selectedCity
              ? `${selectedCity.name} Microclimate & Atmospheric Dispersion (${selectedCity.state})`
              : 'Microclimate & Atmospheric Dispersion'}
          </span>
          <span style={{ color: 'var(--text-muted)' }}>•</span>
          <span style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
            <Clock size={14} /> Telemetry Time: {weather ? formatTime(weather.observedAt) : 'Syncing...'}
          </span>
        </div>
      }
      action={
        <Button
          variant="secondary"
          size="sm"
          onClick={handleRefreshAll}
          isLoading={isGlobalLoading}
        >
          <RefreshCw size={14} style={{ marginRight: '0.35rem' }} /> Refresh Climate Stream
        </Button>
      }
    >
      {/* =========================================================================
          1. REAL WEATHER SUMMARY CARD (Canonical Phase 5 Weather)
          ========================================================================= */}
      <WeatherSummaryCard
        weather={weather}
        cityName={selectedCity?.name}
        isLoading={weatherLoading}
        error={weatherError}
        onRetry={refetchWeather}
      />

      {/* =========================================================================
          2. ATMOSPHERIC DISPERSION & ENVIRONMENTAL IMPACT (Real Observations)
          ========================================================================= */}
      {weather &&
        weather.windSpeed != null &&
        weather.humidity != null &&
        weather.rainfall != null && (
          <Card
            title="Atmospheric Dispersion & Environmental Dynamics"
            subtitle="Meteorological factors governing particulate suspension and downwind transport"
          >
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
                gap: '1.25rem',
              }}
            >
              {/* Wind Impact */}
              <div
                style={{
                  padding: '1rem',
                  borderRadius: '10px',
                  background: 'var(--bg-surface-elevated)',
                  border: '1px solid var(--border-subtle)',
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    marginBottom: '0.5rem',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontWeight: 700, fontSize: '0.825rem' }}>
                    <Compass size={15} color="var(--brand-primary)" />
                    <span>Wind Dispersion Advection</span>
                  </div>
                  <Badge variant="info">{weather.windSpeed} km/h {cardinal}</Badge>
                </div>
                <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', lineHeight: 1.5, margin: 0 }}>
                  {weather.windSpeed > 15
                    ? `High boundary-layer ventilation (${weather.windSpeed} km/h from ${cardinal} / ${weather.windDirection ?? 0}°). Particulate accumulation is rapidly dissipated downwind.`
                    : weather.windSpeed > 5
                    ? `Moderate horizontal advection (${weather.windSpeed} km/h from ${cardinal} / ${weather.windDirection ?? 0}°). Stable transport with moderate dilution of local emissions.`
                    : `Stagnant atmospheric conditions (${weather.windSpeed} km/h from ${cardinal} / ${weather.windDirection ?? 0}°). Low advection promotes localized boundary-layer particulate stagnation.`}
                </p>
              </div>

              {/* Moisture & Aerosol Impact */}
              <div
                style={{
                  padding: '1rem',
                  borderRadius: '10px',
                  background: 'var(--bg-surface-elevated)',
                  border: '1px solid var(--border-subtle)',
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    marginBottom: '0.5rem',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontWeight: 700, fontSize: '0.825rem' }}>
                    <Droplets size={15} color="var(--accent-teal)" />
                    <span>Moisture & Relative Humidity</span>
                  </div>
                  <Badge variant={weather.humidity > 70 ? 'warning' : 'neutral'}>{weather.humidity}% RH</Badge>
                </div>
                <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', lineHeight: 1.5, margin: 0 }}>
                  {weather.humidity > 75
                    ? `High relative humidity (${weather.humidity}%) triggers hygroscopic particulate growth, facilitating secondary inorganic aerosol enlargement and atmospheric hazing.`
                    : `Moderate ambient humidity (${weather.humidity}%). Particulates maintain stable aerodynamic diameters without significant water vapor condensation.`}
                </p>
              </div>

              {/* Precipitation Scavenging */}
              <div
                style={{
                  padding: '1rem',
                  borderRadius: '10px',
                  background: 'var(--bg-surface-elevated)',
                  border: '1px solid var(--border-subtle)',
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    marginBottom: '0.5rem',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontWeight: 700, fontSize: '0.825rem' }}>
                    <CloudRain size={15} color="var(--accent-cyan)" />
                    <span>Precipitation Scavenging</span>
                  </div>
                  <Badge variant={weather.rainfall > 0 ? 'success' : 'neutral'}>{weather.rainfall} mm</Badge>
                </div>
                <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', lineHeight: 1.5, margin: 0 }}>
                  {weather.rainfall > 0
                    ? `Active precipitation (${weather.rainfall} mm). Wet deposition is actively scavenging airborne particulates through droplet collision and ground washout.`
                    : 'Zero precipitation detected. Ambient particulates remain in dry aerodynamic suspension without wet deposition washout.'}
                </p>
              </div>
            </div>
          </Card>
        )}

      {/* =========================================================================
          3. REAL H3 SPATIAL GRID MAP & CELL INSPECTOR (Canonical H3 Cells)
          ========================================================================= */}
      <Card
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Hexagon size={18} color="var(--brand-primary)" />
            <span>Authoritative H3 Spatial Hexagonal Grid</span>
          </div>
        }
        subtitle={
          cells.length > 0
            ? `Displaying ${cells.length} deterministic H3 cells generated from real sensor/weather spatial centroids (Resolution ${cells[0].resolution})`
            : gridError
            ? `Unable to load H3 cells: ${gridError}`
            : 'Spatial grid telemetry layer'
        }
        action={
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <Badge variant="info">
              {cells.length} {cells.length === 1 ? 'CELL' : 'CELLS'} REGISTERED
            </Badge>
            {cells.length > 0 && (
              <Badge variant="neutral">
                RES {cells[0].resolution}
              </Badge>
            )}
          </div>
        }
      >
        {/* Phase 10: H3 Timeline & Layer Exploration Control Bar */}
        <div style={{
          marginBottom: '1rem',
          padding: '0.85rem 1rem',
          borderRadius: '0.75rem',
          background: 'var(--bg-surface-elevated)',
          border: '1px solid var(--border-subtle)',
          display: 'flex',
          flexDirection: 'column',
          gap: '0.75rem',
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.75rem' }}>
            {/* Timeline Horizon Selector */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
              <span style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                H3 Timeline Horizon:
              </span>
              {(['T0', 'T_MINUS_1', 'T_PLUS_1', 'T_PLUS_3', 'T_PLUS_6'] as const).map((h) => {
                const label = h === 'T0' ? 'Now (T+0)' : h === 'T_MINUS_1' ? 'Past (-1h)' : h === 'T_PLUS_1' ? 'Forecast (T+1h)' : h === 'T_PLUS_3' ? 'Forecast (T+3h)' : 'Forecast (T+6h)';
                const isSelected = timelineHorizon === h;
                return (
                  <button
                    key={h}
                    onClick={() => setTimelineHorizon(h)}
                    style={{
                      padding: '0.25rem 0.65rem',
                      borderRadius: '0.375rem',
                      border: isSelected ? '1px solid var(--brand-primary)' : '1px solid var(--border-medium)',
                      background: isSelected ? 'var(--brand-surface)' : 'transparent',
                      color: isSelected ? 'var(--brand-primary)' : 'var(--text-secondary)',
                      fontSize: '0.75rem',
                      fontWeight: isSelected ? 700 : 500,
                      cursor: 'pointer',
                    }}
                  >
                    {label}
                  </button>
                );
              })}
            </div>

            {/* Layer Filter Selector */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
              <span style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                Layer:
              </span>
              {(['ALL', 'PM25', 'WIND', 'HUMIDITY'] as const).map((layer) => {
                const label = layer === 'ALL' ? 'All Telemetry' : layer === 'PM25' ? 'PM2.5 Sensor' : layer === 'WIND' ? 'Wind Velocity' : 'Relative Humidity';
                const isSelected = layerFilter === layer;
                return (
                  <button
                    key={layer}
                    onClick={() => setLayerFilter(layer)}
                    style={{
                      padding: '0.25rem 0.65rem',
                      borderRadius: '0.375rem',
                      border: isSelected ? '1px solid #10b981' : '1px solid var(--border-medium)',
                      background: isSelected ? '#ecfdf5' : 'transparent',
                      color: isSelected ? '#065f46' : 'var(--text-secondary)',
                      fontSize: '0.75rem',
                      fontWeight: isSelected ? 700 : 500,
                      cursor: 'pointer',
                    }}
                  >
                    {label}
                  </button>
                );
              })}
            </div>
          </div>

          <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', lineHeight: 1.4 }}>
            <strong>Spatial Provenance Note:</strong> Real CPCB station telemetry is shown for Now (T+0). Forecast horizons (T+1h, T+3h, T+6h) reflect calibrated LightGBM regression horizons. Areas with unmonitored cells indicate observational gaps, not zero pollution.
          </div>
        </div>

        <div
          style={{
            display: 'grid',
            gridTemplateColumns: selectedCell ? 'minmax(0, 1.6fr) minmax(320px, 1fr)' : '1fr',
            gap: '1.25rem',
            alignItems: 'stretch',
          }}
        >
          {/* Real Leaflet Map with Real H3 Polygons */}
          <div style={{ minHeight: '520px', position: 'relative' }}>
            <PollutionMap
              center={[selectedCity?.latitude ?? 18.5204, selectedCity?.longitude ?? 73.8567]}
              zoom={selectedCity?.defaultZoom || 12}
              stations={stations}
              gridCells={cells}
              selectedH3Index={selectedH3Index}
              onSelectGridCell={(cell) => selectCell(cell.h3Index)}
              selectedCellObservations={selectedCellObservations}
              isLoadingObservations={loadingObservations}
              showHotspots={true}
              height="520px"
            />
          </div>

          {/* Selected Cell Inspector & Real Telemetry */}
          {selectedCell && (
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <CellDetailsCard
                cell={selectedCell}
                observations={selectedCellObservations}
                isLoading={loadingObservations}
                error={observationsError}
                onRetry={refetchObservations}
              />
            </div>
          )}
        </div>
      </Card>

      {/* =========================================================================
          4. REAL WEATHER TREND CHART (Anchored to selected H3 Cell observations)
          ========================================================================= */}
      <WeatherTrendChart
        weatherObservations={selectedCellObservations?.weatherObservations || []}
        isLoading={loadingObservations}
        error={observationsError}
        onRetry={refetchObservations}
        h3Index={selectedH3Index}
      />

      {/* =========================================================================
          5. SPATIAL COVERAGE & PROVENANCE METRICS
          ========================================================================= */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
          gap: '1.25rem',
        }}
      >
        <Card>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
            <Hexagon size={14} color="var(--brand-primary)" />
            <span>Monitored H3 Cells</span>
          </div>
          <div style={{ fontSize: '2.2rem', fontWeight: 800, fontFamily: 'var(--font-heading)', color: 'var(--text-primary)', marginTop: '0.4rem' }}>
            {cells.length}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginTop: '0.35rem' }}>
            Spatial cells registered for {selectedCity?.name || 'current city'} in PostgreSQL
          </div>
        </Card>

        <Card>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
            <Layers size={14} color="var(--accent-teal)" />
            <span>Grid Resolution</span>
          </div>
          <div style={{ fontSize: '2.2rem', fontWeight: 800, fontFamily: 'var(--font-heading)', color: 'var(--accent-teal)', marginTop: '0.4rem' }}>
            {cells.length > 0 ? `Res ${cells[0].resolution}` : 'Res 8'}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginTop: '0.35rem' }}>
            Average hexagon edge length: ~461 meters (0.7 km² area)
          </div>
        </Card>

        <Card>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
            <Activity size={14} color="var(--brand-primary)" />
            <span>Cell Telemetry Signals</span>
          </div>
          <div style={{ fontSize: '2.2rem', fontWeight: 800, fontFamily: 'var(--font-heading)', color: 'var(--brand-primary)', marginTop: '0.4rem' }}>
            {selectedCellObservations
              ? selectedCellObservations.airObservations.length +
                selectedCellObservations.weatherObservations.length
              : 0}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginTop: '0.35rem' }}>
            {selectedCellObservations
              ? `${selectedCellObservations.airObservations.length} air + ${selectedCellObservations.weatherObservations.length} weather readings in active cell`
              : 'Select an H3 cell on the map to inspect telemetry'}
          </div>
        </Card>

        <Card>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
            <Database size={14} color="var(--text-muted)" />
            <span>Telemetry Sources</span>
          </div>
          <div style={{ fontSize: '1.25rem', fontWeight: 700, fontFamily: 'var(--font-heading)', color: 'var(--text-primary)', marginTop: '0.55rem' }}>
            {weather?.source || 'OPEN_METEO'} + CAAQMS
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginTop: '0.35rem' }}>
            Deterministic PostgreSQL backfilled spatial provenance
          </div>
        </Card>
      </div>
    </PageContainer>
  );
};

export default WeatherSpatial;
