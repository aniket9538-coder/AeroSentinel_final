import React, { useState, useEffect } from 'react';
import { PageContainer } from '../../components/layout/PageContainer';
import { PollutionMap as MapView } from '../../components/map/PollutionMap';
import { Card } from '../../components/common/Card';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { useApp } from '../../store/AppContext';
import { useGrid } from '../../hooks/useGrid';
import { useHotspots } from '../../hooks/useHotspots';
import { useMonitoringRecommendations } from '../../hooks/useMonitoringRecommendations';
import {
  Layers,
  MapPin,
  Flame,
  Radio,
  Activity,
  RefreshCw,
  Hexagon,
  Compass,
} from 'lucide-react';

export const PollutionMap: React.FC = () => {
  const { selectedCity, availableCities, setSelectedCity, stations } = useApp();

  // Layer Visibility State
  const [showHotspots, setShowHotspots] = useState(true);
  const [showGrid, setShowGrid] = useState(true);
  const [showMonitoring, setShowMonitoring] = useState(true);
  const [showStations, setShowStations] = useState(true);
  const [showFires, setShowFires] = useState(true);
  const [showCitizen, setShowCitizen] = useState(true);

  // Real backend spatial telemetry
  const {
    cells,
    selectCell: selectGridCell,
    selectedCellObservations,
    loadingObservations,
    refetchGrid,
  } = useGrid(selectedCity?.id);

  const {
    overview: hotspotOverview,
    selectCell: selectHotspotCell,
  } = useHotspots(selectedCity?.id);

  const {
    recommendations: monitoringRecs,
    selectedRecommendation,
    selectH3: selectMonitoringH3,
    refresh: refreshMonitoring,
  } = useMonitoringRecommendations(selectedCity?.id);

  // Active selected cell (unified across grid, hotspot, and monitoring layers)
  const [activeCellId, setActiveCellId] = useState<string | null>(null);

  useEffect(() => {
    if (cells && cells.length > 0 && !activeCellId) {
      setActiveCellId(cells[0].h3Index);
    }
  }, [cells, activeCellId]);

  const handleSelectCell = (h3Index: string) => {
    setActiveCellId(h3Index);
    selectGridCell(h3Index);
    selectHotspotCell(h3Index);
    selectMonitoringH3(h3Index);
  };

  // Center coordinates based on current city
  const mapCenter: [number, number] = [
    selectedCity?.latitude ?? 18.5204,
    selectedCity?.longitude ?? 73.8567,
  ];

  const mapZoom = selectedCity?.defaultZoom ?? 12;

  // Selected cell data resolution
  const activeCellData = cells?.find((c) => c.h3Index === activeCellId);
  const activeRec = monitoringRecs?.find((r) => r.h3Index === activeCellId) || selectedRecommendation;
  const activeHotspot = hotspotOverview?.cells?.find((h) => h.h3Index === activeCellId);

  return (
    <PageContainer
      title="Hyperlocal Multi-Layer Spatial Map"
      subtitle="Interactive MapTiler Cloud basemaps with real-time H3 hexagonal grid, telemetry sensors, and decision support"
      action={
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}>
          {/* City Switcher */}
          <div
            style={{
              display: 'flex',
              background: 'var(--bg-surface-elevated, #1e293b)',
              padding: '0.2rem',
              borderRadius: '8px',
              border: '1px solid var(--border-subtle, #334155)',
              gap: '0.25rem',
            }}
          >
            {availableCities.map((city) => {
              const isSelected = selectedCity?.id === city.id;
              return (
                <button
                  key={city.id}
                  onClick={() => setSelectedCity(city)}
                  style={{
                    padding: '0.35rem 0.75rem',
                    borderRadius: '6px',
                    fontSize: '0.775rem',
                    fontWeight: isSelected ? 600 : 500,
                    cursor: 'pointer',
                    border: 'none',
                    background: isSelected ? 'var(--brand-primary, #0284c7)' : 'transparent',
                    color: isSelected ? '#ffffff' : 'var(--text-secondary, #94a3b8)',
                    transition: 'all 0.15s ease',
                  }}
                >
                  {city.name}
                </button>
              );
            })}
          </div>

          <Button
            size="sm"
            variant="outline"
            onClick={() => {
              refetchGrid();
              refreshMonitoring();
            }}
          >
            <RefreshCw size={13} style={{ marginRight: '0.35rem' }} />
            Sync Map
          </Button>
        </div>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
        {/* Layer Controls Bar */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            background: 'var(--bg-surface, #0f172a)',
            padding: '0.75rem 1.25rem',
            borderRadius: '12px',
            border: '1px solid var(--border-subtle, #1e293b)',
            flexWrap: 'wrap',
            gap: '0.75rem',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Layers size={16} color="var(--brand-primary, #38bdf8)" />
            <span style={{ fontSize: '0.825rem', fontWeight: 600, color: 'var(--text-primary, #f8fafc)' }}>
              Active Spatial Layers:
            </span>
          </div>

          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            <Button
              size="sm"
              variant={showGrid ? 'primary' : 'outline'}
              onClick={() => setShowGrid(!showGrid)}
            >
              <Hexagon size={13} style={{ marginRight: '0.35rem' }} />
              H3 Spatial Grid ({cells?.length || 0})
            </Button>

            <Button
              size="sm"
              variant={showMonitoring ? 'primary' : 'outline'}
              onClick={() => setShowMonitoring(!showMonitoring)}
            >
              <Radio size={13} style={{ marginRight: '0.35rem' }} />
              F8 Monitoring Gaps ({monitoringRecs?.length || 0})
            </Button>

            <Button
              size="sm"
              variant={showHotspots ? 'primary' : 'outline'}
              onClick={() => setShowHotspots(!showHotspots)}
            >
              <Flame size={13} style={{ marginRight: '0.35rem' }} />
              Hotspots ({hotspotOverview?.cells?.length || 0})
            </Button>

            <Button
              size="sm"
              variant={showStations ? 'primary' : 'outline'}
              onClick={() => setShowStations(!showStations)}
            >
              <MapPin size={13} style={{ marginRight: '0.35rem' }} />
              Stations ({stations?.length || 0})
            </Button>

            <Button
              size="sm"
              variant={showFires ? 'primary' : 'outline'}
              onClick={() => setShowFires(!showFires)}
            >
              <Activity size={13} style={{ marginRight: '0.35rem' }} />
              Thermal Fires
            </Button>
          </div>
        </div>

        {/* Main Spatial Map View + Inspector Grid */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'minmax(0, 1fr) minmax(320px, 360px)',
            gap: '1.25rem',
            alignItems: 'start',
          }}
          className="spatial-map-grid"
        >
          {/* Leaflet + MapTiler Map Container */}
          <div
            style={{
              height: '660px',
              width: '100%',
              borderRadius: '14px',
              overflow: 'hidden',
              border: '1px solid var(--border-subtle, #334155)',
              boxShadow: 'var(--shadow-lg, 0 10px 25px -5px rgba(0, 0, 0, 0.3))',
              position: 'relative',
            }}
          >
            <MapView
              center={mapCenter}
              zoom={mapZoom}
              cityId={selectedCity?.id}
              stations={showStations ? stations : []}
              gridCells={showGrid ? cells : []}
              hotspots={showHotspots ? hotspotOverview?.cells : []}
              monitoringRecommendations={monitoringRecs}
              showMonitoringCoverage={showMonitoring}
              showHotspots={showHotspots}
              showFires={showFires}
              showCitizenReports={showCitizen}
              selectedH3Index={activeCellId}
              onSelectGridCell={(c) => handleSelectCell(c.h3Index)}
              onSelectH3Cell={(h) => handleSelectCell(h.h3Index)}
              onSelectMonitoringRecommendation={(r) => handleSelectCell(r.h3Index)}
              selectedCellObservations={selectedCellObservations}
              isLoadingObservations={loadingObservations}
              height="660px"
            />
          </div>

          {/* Spatial Cell Inspector Sidebar */}
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: '1rem',
              position: 'sticky',
              top: '1rem',
            }}
          >
            {/* Cell Overview Card */}
            <Card
              title={
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <Compass size={16} color="var(--brand-primary, #38bdf8)" />
                  <span>Cell Inspector</span>
                </div>
              }
              badge={
                activeCellId ? (
                  <Badge variant="info" size="sm">
                    Active
                  </Badge>
                ) : undefined
              }
            >
              {activeCellId ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem' }}>
                  <div>
                    <div style={{ fontSize: '0.7rem', color: 'var(--text-muted, #94a3b8)', textTransform: 'uppercase' }}>
                      Uber H3 Resolution 8 Hexagon
                    </div>
                    <div
                      style={{
                        fontFamily: 'var(--font-mono, monospace)',
                        fontSize: '0.9rem',
                        fontWeight: 600,
                        color: 'var(--text-primary, #f8fafc)',
                        marginTop: '0.15rem',
                      }}
                    >
                      {activeCellId}
                    </div>
                  </div>

                  {/* F8 Monitoring Recommendation Details (if cell has recommendation) */}
                  {activeRec && (
                    <div
                      style={{
                        padding: '0.75rem',
                        borderRadius: '8px',
                        background:
                          activeRec.priorityLevel === 'HIGH'
                            ? 'rgba(236, 72, 153, 0.1)'
                            : activeRec.priorityLevel === 'MEDIUM'
                            ? 'rgba(168, 85, 247, 0.1)'
                            : 'rgba(99, 102, 241, 0.1)',
                        border: `1px solid ${
                          activeRec.priorityLevel === 'HIGH'
                            ? '#ec489955'
                            : activeRec.priorityLevel === 'MEDIUM'
                            ? '#a855f755'
                            : '#6366f155'
                        }`,
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
                        <span style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-primary)' }}>
                          Monitoring Priority
                        </span>
                        <Badge
                          variant={
                            activeRec.priorityLevel === 'HIGH'
                              ? 'danger'
                              : activeRec.priorityLevel === 'MEDIUM'
                              ? 'warning'
                              : 'info'
                          }
                          size="sm"
                        >
                          {activeRec.priorityLevel} ({activeRec.priorityScorePercent ?? Math.round(activeRec.priorityScore)}/100)
                        </Badge>
                      </div>

                      <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', lineHeight: 1.35 }}>
                        {activeRec.recommendation}
                      </div>

                      <div
                        style={{
                          display: 'grid',
                          gridTemplateColumns: '1fr 1fr',
                          gap: '0.5rem',
                          marginTop: '0.6rem',
                          paddingTop: '0.5rem',
                          borderTop: '1px solid rgba(255, 255, 255, 0.08)',
                          fontSize: '0.725rem',
                        }}
                      >
                        <div>
                          <div style={{ color: 'var(--text-muted)' }}>Nearest Station:</div>
                          <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>
                            {activeRec.nearestStationName || activeRec.nearestStationCode || 'N/A'}
                          </div>
                        </div>
                        <div>
                          <div style={{ color: 'var(--text-muted)' }}>Distance:</div>
                          <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>
                            {(activeRec.nearestStationDistanceKm ?? activeRec.stationDistanceKm)?.toFixed(2)} km
                          </div>
                        </div>
                      </div>
                    </div>
                  )}

                  {/* Hotspot & Forecast Metrics */}
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: '1fr 1fr',
                      gap: '0.6rem',
                      background: 'var(--bg-surface-elevated, #1e293b)',
                      padding: '0.75rem',
                      borderRadius: '8px',
                    }}
                  >
                    <div>
                      <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Predicted PM2.5</div>
                      <div style={{ fontSize: '1.05rem', fontWeight: 700, color: '#38bdf8' }}>
                        {activeRec?.predictedPm25 != null ? `${activeRec.predictedPm25.toFixed(1)} µg/m³` : 'Observing'}
                      </div>
                    </div>
                    <div>
                      <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Hotspot Risk</div>
                      <div
                        style={{
                          fontSize: '1.05rem',
                          fontWeight: 700,
                          color:
                            activeHotspot?.riskLevel === 'HIGH' || activeRec?.riskLevel === 'HIGH'
                              ? '#ef4444'
                              : '#10b981',
                        }}
                      >
                        {activeHotspot?.riskLevel || activeRec?.riskLevel || 'NORMAL'}
                      </div>
                    </div>
                  </div>

                  {/* City & Geo Context */}
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                    <div>
                      City Region:{' '}
                      <strong style={{ color: 'var(--text-primary)' }}>
                        {selectedCity?.name} ({selectedCity?.state})
                      </strong>
                    </div>
                    {activeCellData && activeCellData.center && (
                      <div style={{ marginTop: '0.25rem' }}>
                        Center Lat/Lng:{' '}
                        <span style={{ fontFamily: 'var(--font-mono)' }}>
                          {activeCellData.center.lat.toFixed(4)}, {activeCellData.center.lng.toFixed(4)}
                        </span>
                      </div>
                    )}
                  </div>
                </div>
              ) : (
                <div style={{ padding: '2rem 1rem', textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                  Click any H3 cell on the MapTiler basemap to inspect real spatial telemetry.
                </div>
              )}
            </Card>

            {/* Map Legend Card */}
            <Card title="Basemap & Spatial Legend">
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', fontSize: '0.75rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <span style={{ width: '12px', height: '12px', borderRadius: '3px', background: '#ec4899' }} />
                  <span>High Priority Monitoring Gap (Mobile Sensor Recommended)</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <span style={{ width: '12px', height: '12px', borderRadius: '3px', background: '#a855f7' }} />
                  <span>Medium Priority Monitoring (Targeted Observation)</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <span style={{ width: '12px', height: '12px', borderRadius: '3px', background: '#6366f1' }} />
                  <span>Low Priority Monitoring (Routine Coverage)</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <span style={{ width: '12px', height: '12px', borderRadius: '50%', background: '#3b82f6', border: '2px solid #ffffff' }} />
                  <span>CAAQMS Air Quality Ground Station</span>
                </div>
              </div>
            </Card>
          </div>
        </div>
      </div>
    </PageContainer>
  );
};

export default PollutionMap;
