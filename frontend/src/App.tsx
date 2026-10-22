import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AppProvider, useApp } from './store/AppContext';
import { Navbar } from './components/layout/Navbar';
import { Sidebar } from './components/layout/Sidebar';

// Public Pages (F0, F1, F2, F3, F4, F6)
import { Home } from './pages/public/Home';
import { Dashboard } from './pages/public/Dashboard';
import { AirQuality } from './pages/public/AirQuality';
import { WeatherSpatial } from './pages/public/WeatherSpatial';
import { PollutionMap } from './pages/public/PollutionMap';
import { Forecast } from './pages/public/Forecast';
import { Hotspots } from './pages/public/Hotspots';
import { CitizenReport } from './pages/public/CitizenReport';
import { MonitoringDashboard } from './pages/public/MonitoringDashboard';

// Analyst Pages (F5 Evidence & AI Reasoning)
import { AnalystDashboard } from './pages/analyst/AnalystDashboard';
import { HotspotAnalysis } from './pages/analyst/HotspotAnalysis';
import { EvidenceAnalysis } from './pages/analyst/EvidenceAnalysis';
import { ForecastAnalysis } from './pages/analyst/ForecastAnalysis';

// Authority Pages
import { AuthorityDashboard } from './pages/authority/AuthorityDashboard';
import { Alerts } from './pages/authority/Alerts';
import { IncidentQueuePage } from './pages/authority/IncidentQueue';
import { InspectionPage } from './pages/authority/Inspection';
import { ActionsPage } from './pages/authority/Actions';

// Admin Pages
import { AdminDashboard } from './pages/admin/AdminDashboard';
import { UsersPage } from './pages/admin/Users';
import { CitiesPage } from './pages/admin/Cities';
import { SystemStatus } from './pages/admin/SystemStatus';

// Federated Pages
import { FederatedNetwork } from './pages/federated/FederatedNetwork';
import { ErrorBoundary } from './components/common/ErrorBoundary';
import { F3ForecastContinuityBridge } from './components/forecasting/F3ForecastContinuityBridge';

// Citizen Portal Pages (Phase 5)
import { CitizenLayout } from './components/citizen/CitizenLayout';
import { CitizenOverview } from './pages/citizen/CitizenOverview';
import { CitizenAssistantPage } from './pages/citizen/CitizenAssistantPage';
import { CitizenWeatherPage } from './pages/citizen/CitizenWeatherPage';
import { CitizenAirQualityPage } from './pages/citizen/CitizenAirQualityPage';
import { CitizenWarningsPage } from './pages/citizen/CitizenWarningsPage';

const AppLayout: React.FC = () => {
  const location = useLocation();
  const { sidebarOpen } = useApp();
  const isOnboarding = location.pathname === '/' || location.pathname === '/home';
  const isCitizenPortal = location.pathname.startsWith('/citizen');

  // If in dedicated Citizen Portal, render with distinct Citizen Layout & Navigation
  if (isCitizenPortal) {
    return (
      <CitizenLayout>
        <Routes>
          <Route path="/citizen" element={<CitizenOverview />} />
          <Route path="/citizen/overview" element={<CitizenOverview />} />
          <Route path="/citizen/assistant" element={<CitizenAssistantPage />} />
          <Route path="/citizen/weather" element={<CitizenWeatherPage />} />
          <Route path="/citizen/air-quality" element={<CitizenAirQualityPage />} />
          <Route path="/citizen/warnings" element={<CitizenWarningsPage />} />
          <Route path="/citizen/report" element={<CitizenReport />} />
          <Route path="/citizen/report/:reportId" element={<CitizenReport />} />
          <Route path="/citizen/*" element={<Navigate to="/citizen" replace />} />
        </Routes>
      </CitizenLayout>
    );
  }

  return (
    <div className={`app-shell ${isOnboarding ? 'onboarding-shell' : ''}`}>
      {/* Seamless F3 -> Forecast H3 continuity bridge */}
      <F3ForecastContinuityBridge />

      {/* Sidebar Navigation: Hidden on Onboarding/Landing page, active across Command Center */}
      {!isOnboarding && <Sidebar />}

      {/* App Main (Navbar + Page Content) */}
      <div
        className={`app-main ${!isOnboarding && sidebarOpen ? 'sidebar-open' : 'sidebar-closed'}`}
        style={isOnboarding ? { marginLeft: 0, width: '100%', minWidth: '100%' } : undefined}
      >
        {!isOnboarding && <Navbar />}
        <main className="app-content-scroll">
          <Routes>
            {/* Landing & Foundation */}
            <Route path="/" element={<Home />} />
            <Route path="/home" element={<Home />} />

            {/* Command Center & F1 City + Air Quality */}
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/air-quality" element={<AirQuality />} />

            {/* F2 Weather & H3 Spatial Layer */}
            <Route path="/weather" element={<WeatherSpatial />} />
            <Route path="/spatial" element={<WeatherSpatial />} />
            <Route path="/map" element={<PollutionMap />} />

            {/* F3 Hotspots & F4 Forecast */}
            <Route path="/hotspots" element={<Hotspots />} />
            <Route path="/forecast" element={<Forecast />} />

            {/* F8 Monitoring Priority & Recommendations */}
            <Route path="/monitoring" element={<MonitoringDashboard />} />

            {/* F6 Citizen Reporting & Evidence Upload */}
            <Route path="/report" element={<CitizenReport />} />
            <Route path="/report/status/:reportId" element={<CitizenReport />} />
            <Route path="/citizen/report" element={<CitizenReport />} />

            {/* F5 Evidence & Gemini WHY Intelligence */}
            <Route path="/analyst/evidence" element={<EvidenceAnalysis />} />
            <Route path="/gemini-why" element={<EvidenceAnalysis />} />

            {/* Analyst Routes */}
            <Route path="/analyst" element={<AnalystDashboard />} />
            <Route path="/analyst/hotspots" element={<HotspotAnalysis />} />
            <Route path="/analyst/forecast" element={<ForecastAnalysis />} />

            {/* Authority Routes */}
            <Route path="/authority" element={<AuthorityDashboard />} />
            <Route path="/authority/incidents" element={<IncidentQueuePage />} />
            <Route path="/authority/alerts" element={<Alerts />} />
            <Route path="/authority/inspection" element={<InspectionPage />} />
            <Route path="/authority/actions" element={<ActionsPage />} />

            {/* Admin Routes */}
            <Route path="/admin" element={<AdminDashboard />} />
            <Route path="/admin/users" element={<UsersPage />} />
            <Route path="/admin/cities" element={<CitiesPage />} />
            <Route path="/admin/status" element={<SystemStatus />} />

            {/* Federated Prototype & Aliases */}
            <Route path="/federated" element={<FederatedNetwork />} />
            <Route path="/federated-network" element={<FederatedNetwork />} />
            <Route path="/authority/federated-network" element={<FederatedNetwork />} />

            {/* Fallback */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
      </div>
    </div>
  );
};

export const App: React.FC = () => {
  return (
    <ErrorBoundary>
      <AppProvider>
        <Router>
          <AppLayout />
        </Router>
      </AppProvider>
    </ErrorBoundary>
  );
};

export default App;
