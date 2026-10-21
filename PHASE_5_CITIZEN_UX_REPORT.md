# AeroSentinel Phase 5 — Citizen UX & Conversational Portal Final Report

> **Document Version:** 1.0.0  
> **Date:** October 19, 2026  
> **Status:** PASS (with documented environment & port collision disclosures)  
> **Author:** Antigravity Autonomous Agent (Phase 5 Verification Slice)  
> **Target File:** `PHASE_5_CITIZEN_UX_REPORT.md`

---

## 1. Executive Summary

Phase 5 delivers the dedicated **AeroSentinel Citizen Portal**, providing the public with an intuitive, trustworthy, and aesthetically polished environmental intelligence experience across desktop, tablet, and mobile form factors.

Crucially, Phase 5 achieves complete spatial and navigational separation between the **Citizen Portal** and the existing **Authority Command Center / Analyst Intelligence Console**:
1. **Isolated Citizen Shell:** Operating under the dedicated route `/citizen/*`, the portal renders via a standalone `CitizenLayout` and `CitizenNavbar`, completely free of internal incident triage queues, field team assignments, or authority-only controls.
2. **Preserved Authority Workflows:** All existing Command Center routes (`/dashboard`, `/air-quality`, `/weather`, `/map`, `/hotspots`, `/forecast`, `/monitoring`, `/analyst/*`, `/authority/*`, `/admin/*`, `/federated`) remain fully intact and operational. Bidirectional navigation bridges ("Citizen Portal" pills on the landing/navbar and "Command Center" external buttons in the Citizen view) enable seamless transition without route clobbering.
3. **Rigorous Data Truthfulness & Anti-Fabrication Safeguards:**
   - Ground PM2.5 readings from CPCB / OpenAQ monitors are attributed with station names (`PUN-001`, Shivajinagar) and explicit observation ages. Outdated measurements are flagged truthfully as `NO_DATA` / `STALE` and are never substituted with synthetic zeroes.
   - Surface weather observations are explicitly disclosed as derived from **Open-Meteo Numerical Weather Prediction (NWP) model analysis** (seamless GFS/ECMWF/ICON blend) rather than in-situ physical telemetry.
   - Official disaster and weather alerts adhere strictly to official statutory feeds from the **India Meteorological Department (IMD)** and **NDMA SACHET (CAP RSS 2.0)**. When unconfigured or unreachable, the system returns `FEED_UNAVAILABLE` / `SOURCE_UNAVAILABLE`, never falsely claiming "no warnings".
   - The calibrated Random Forest PM2.5 hazard model is strictly bounded to the **Pune Metropolitan Region**. Queries outside Pune return `MODEL_DOMAIN_UNSUPPORTED`, and the UI explicitly discloses that holdout evaluations demonstrated the persistence baseline outperformed the Random Forest regressor (MAE 11.2 vs 8.4 µg/m³).
4. **Conversational Assistant Integration:** The "Ask AeroSentinel" interface routes strictly through typed frontend services (`assistantService`) and backend Spring Boot endpoints (`/api/v1/assistant/*`). Zero client-side API keys or direct LLM calls exist in the browser. Interactive features include missing-location clarification prompts, rapid-request debouncing, in-flight submission locks, and a 60-second client-side countdown timer for HTTP 429 rate limit responses.

---

## 2. Repository & Working-Tree State

- **Active Branch:** `main`
- **Head Commit:** `15e199d3f3d5a15a4ccdca4267de1389de4af22f`
- **Commit Message:** `fix(deployment): normalize database url and prevent startup timeout on Vercel`
- **Working Tree State:** Clean working tree with existing tracked modifications and untracked Phase 4/5 integration deliverables preserved intact.
- **Modified Tracked Files:**
  - `backend/src/main/java/com/aerosentinel/citizen/CitizenVisionAiClient.java` (Added `@Autowired` to primary injection constructor)
  - `backend/src/main/java/com/aerosentinel/forecast/ForecastAiClient.java` (Added `@Autowired` to primary injection constructor)
  - `backend/src/main/java/com/aerosentinel/evidence/EvidenceAiClient.java` (Added `@Autowired` to primary injection constructor)
  - `backend/src/main/java/com/aerosentinel/assistant/warning/OfficialWarningClient.java` (Added `@Autowired` to primary injection constructor)
  - `backend/src/main/java/com/aerosentinel/assistant/service/AssistantSynthesisClient.java` (Added `@Autowired` to primary injection constructor)
  - `backend/src/test/java/com/aerosentinel/assistant/OfficialWarningToolTest.java` (Updated hardcoded expired test timestamp to dynamic future timestamp)
- **Untracked Phase 5 Core Assets:**
  - `frontend/src/components/citizen/CitizenLayout.tsx`
  - `frontend/src/components/citizen/CitizenNavbar.tsx`
  - `frontend/src/components/citizen/CitizenAirQualityCard.tsx`
  - `frontend/src/components/citizen/CitizenWeatherCard.tsx`
  - `frontend/src/components/citizen/CitizenWarningsCard.tsx`
  - `frontend/src/components/citizen/CitizenPM25RiskCard.tsx`
  - `frontend/src/components/citizen/CitizenForecastTimeline.tsx`
  - `frontend/src/components/citizen/CitizenLocationSelector.tsx`
  - `frontend/src/components/citizen/CitizenQuickAssistant.tsx`
  - `frontend/src/components/citizen/citizen.css`
  - `frontend/src/pages/citizen/CitizenOverview.tsx`
  - `frontend/src/pages/citizen/CitizenAssistantPage.tsx`
  - `frontend/src/pages/citizen/CitizenWeatherPage.tsx`
  - `frontend/src/pages/citizen/CitizenAirQualityPage.tsx`
  - `frontend/src/pages/citizen/CitizenWarningsPage.tsx`
  - `frontend/src/services/assistant.service.ts`
  - `frontend/src/types/assistant.ts`
  - `frontend/src/utils/citizen_portal.test.ts`
  - `frontend/src/utils/assistant_contract.test.ts`

---

## 3. Architecture & UI Separation

### 3.1 Structural Isolation
The React routing layer (`frontend/src/App.tsx`) enforces a strict binary layout switch based on `location.pathname.startsWith('/citizen')`:

```tsx
// frontend/src/App.tsx
const isCitizenPortal = location.pathname.startsWith('/citizen');

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
        <Route path="/citizen/*" element={<Navigate to="/citizen" replace />} />
      </Routes>
    </CitizenLayout>
  );
}
```

### 3.2 Key Differences Between Shells

| Dimension | Citizen Portal (`/citizen/*`) | Authority Command Center (`/*`) |
|---|---|---|
| **Primary Navbar** | `CitizenNavbar` with direct tabs: Overview, Ask AeroSentinel, Weather & Forecast, Air Quality, Warnings, Report | Full operational navbar with city sync status, live timestamp, theme toggle, notifications dropdown |
| **Sidebar Navigation** | **None** (Full-width clean viewport) | Dynamic collapsible sidebar (`Sidebar.tsx`) with Monitor, Intelligence, Action, Network sections |
| **Language Localization** | Active English (`en`), Hindi (`hi`), Marathi (`mr`) selector | English standard |
| **Access Rights** | Unauthenticated, publicly accessible | Role-guarded operational workflows (Alert acknowledgment, team assignment, field verification) |
| **Action Modalities** | Citizen reports, conversational Q&A, location inspection | Incident queue management, authority dispatch, model monitoring |

---

## 4. Routes, Components & API Contract Mapping

### 4.1 Route Catalog

| Route Path | View Component | Key Sub-Components | Purpose |
|---|---|---|---|
| `/citizen` | `CitizenOverview.tsx` | `CitizenLocationSelector`, `CitizenAirQualityCard`, `CitizenWeatherCard`, `CitizenWarningsCard`, `CitizenPM25RiskCard`, `CitizenForecastTimeline`, `CitizenQuickAssistant` | Central environmental hub summarizing localized telemetry and alerts |
| `/citizen/assistant` | `CitizenAssistantPage.tsx` | Message list, source citation cards, needs-clarification form, 429 countdown badge | Grounded natural language environmental assistant |
| `/citizen/weather` | `CitizenWeatherPage.tsx` | `CitizenWeatherCard`, `CitizenForecastTimeline` | Detailed NWP model weather trends and 72-hour forward forecasting |
| `/citizen/air-quality` | `CitizenAirQualityPage.tsx` | `CitizenAirQualityCard`, multi-station monitor list | In-situ ground monitor readings, station codes, and freshness indicators |
| `/citizen/warnings` | `CitizenWarningsPage.tsx` | `CitizenWarningsCard`, emergency contacts | IMD / NDMA statutory bulletins and state emergency contact directory |
| `/citizen/report` | `CitizenReport.tsx` | `ReportForm.tsx`, `ImageUploader.tsx`, `LocationPickerMap.tsx`, `GeminiVisionCard.tsx` | Crowdsourced citizen incident submission and multimodal vision feedback |

### 4.2 API Integration Mapping

All network communication originates from the centralized typed client `assistantService` (`frontend/src/services/assistant.service.ts`), proxied via Vite to the Spring Boot orchestrator:

| Service Method | HTTP Method & Path | Backend Controller / Service | Data Sources Fused |
|---|---|---|---|
| `assistantService.chat()` | `POST /api/v1/assistant/chat` | `AssistantController` &rarr; `AssistantOrchestrationService` | CPCB ground stations, Open-Meteo weather, NDMA SACHET, F3 hotspot classifier, FastAPI synthesis |
| `assistantService.getCurrentWeather()` | `GET /api/v1/assistant/weather/current` | `AssistantController` &rarr; `WeatherTool` &rarr; `WeatherClient` | Open-Meteo seamless NWP model analysis |
| `assistantService.getWeatherForecast()` | `GET /api/v1/assistant/weather/forecast` | `AssistantController` &rarr; `WeatherTool` &rarr; `WeatherClient` | Open-Meteo 72-hour hourly forecast |
| `assistantService.getAirQuality()` | `GET /api/v1/assistant/air-quality` | `AssistantController` &rarr; `AirQualityTool` | Local PostGIS database (`AirObservation`), CPCB / OpenAQ ground feeds |
| `assistantService.getWarnings()` | `GET /api/v1/assistant/warnings` | `AssistantController` &rarr; `OfficialWarningTool` &rarr; `OfficialWarningClient` | NDMA SACHET CAP RSS 2.0 XML / IMD portals |
| `assistantService.getPM25Risk()` | `GET /api/v1/assistant/pm25-risk` | `AssistantController` &rarr; `PM25RiskTool` | Uber H3 resolution 8 spatial index, F3 Random Forest classifier (`hotspot_classifier_v1`) |

---

## 5. Verification Against Acceptance Criteria

### 5.1 Citizen Entry and Navigation
- **Start UI Preservation:** `HeroSection.tsx` and `OnboardingHeader.tsx` preserve all primary brand assets, metrics, and operations sections while exposing prominent "Citizen Portal" action buttons (`id="hero-citizen-portal-btn"` and `id="header-citizen-portal-btn"`).
- **Dedicated Shell:** Clicking "Citizen Portal" cleanly enters `/citizen` under `CitizenLayout` with zero leakage of authority sidebar controls.
- **Deep Links & Reverse Links:** Deep links (`/citizen/weather`, `/citizen/air-quality`, `/citizen/warnings`, `/citizen/assistant`) navigate reliably. Clicking the "Command Center" button in `CitizenNavbar` smoothly returns to `/dashboard`.
- **No Duplicate Architecture:** Implemented strictly inside the existing React/Vite single-page application and Spring Boot backend; no secondary web server or duplicate repository was created.

### 5.2 Citizen Dashboard & Real Data Integrity
- **Location Selector:** `CitizenLocationSelector.tsx` supports one-click switching between supported metropolitan regions (Pune, Mumbai, Delhi), manual latitude/longitude input, and browser geolocation with graceful error handling when denied.
- **Data Provenance & Freshness:**
  - Air Quality: Shivajinagar CAAQMS (`PUN-001`) PM2.5 = 78.0 µg/m³, observed on `2026-09-24T22:00:00Z` (35,110 minutes old). Status = `NO_DATA`. Displayed with orange stale notice; never fabricated as current live data.
  - Weather: Open-Meteo NWP model analysis at `2026-10-19T06:30:00Z` (33.1°C, 38.0% humidity, 9.1 km/h wind). Status = `LIVE`. Explicit disclaimer that data is from numerical model analysis, not in-situ physical telemetry.
  - Official Warnings: NDMA SACHET portal checked; returns `NO_ACTIVE_WARNINGS` with direct link to `https://sachet.ndma.gov.in`.
- **Anti-Fabrication Safeguards:**
  - `SOURCE_UNAVAILABLE` is distinctly rendered with an amber warning card stating: *"Unable to reach government disaster portal feeds. An absent or unreachable feed does not guarantee that conditions are clear."* It is never presented as "no warnings".
  - PM2.5 ML risk is strictly withheld outside the Pune metropolitan boundary with an explicit notification: *"Capability Unavailable: Geographic Domain Boundary. Operational spatial regressors are not yet validated for Mumbai/Delhi."*
  - Benchmark honesty: Displays notice thatholdout evaluation proved the naive persistence baseline outperformed the Random Forest regressor (MAE 11.2 vs 8.4 µg/m³).

### 5.3 Conversational Assistant
- **Zero Client LLM Exposure:** All chat traffic flows to `POST /api/v1/assistant/chat`. No Google GenAI SDK or API keys are imported or bundled client-side.
- **Structured Evidence Citations:** Chat responses include structured source reference pills indicating provider, station, timestamp, and freshness badges.
- **Clarification Workflow:** Prompts lacking location context (e.g., *"What is the air quality?"*) return `NEEDS_CLARIFICATION` and render an inline location prompt allowing one-click city confirmation.
- **Rate Limit & Submission Protection:**
  - Duplicate submissions are prevented by disabling the textarea and send button while `inFlight === true`.
  - HTTP 429 triggers an active 60-second countdown badge, temporarily locking the input to prevent server congestion.

---

## 6. Real Verification & Command Execution

### 6.1 Frontend Test Suite
- **Command:** `npm test` (executed from `c:\Users\lenovo\AeroSentinel (3)\AeroSentinel\frontend`)
- **Framework:** Node.js native test runner via `npx tsx --test src/utils/*.test.ts src/utils/*.test.tsx`
- **Output Summary:**
  ```
  # tests 341
  # suites 9
  # pass 341
  # fail 0
  # cancelled 0
  # skipped 0
  # todo 0
  # duration_ms 4864.363
  ```
- **Exit Code:** `0` (Clean PASS across all 341 tests, including 43 dedicated citizen portal and assistant contract tests)

### 6.2 TypeScript Type-Check & Production Build
- **Command:** `npm run build` (`tsc -b && vite build`)
- **Output Summary:**
  ```
  vite v5.4.21 building for production...
  transforming...
  ✓ 2592 modules transformed.
  rendering chunks...
  computing gzip size...
  dist/index.html                     1.23 kB │ gzip:   0.67 kB
  dist/assets/index-EzrxEBxD.css     45.47 kB │ gzip:   8.84 kB
  dist/assets/index-CipjYBbR.js   1,582.61 kB │ gzip: 414.77 kB
  ✓ built in 37.57s
  ```
- **Exit Code:** `0` (Zero compiler errors, valid minified production bundle generated)

### 6.3 Backend Assistant & Tool Contract Tests
- **Command:** `mvn.cmd test -Dtest="*Assistant*,*Tool*"`
- **Target Classes:** `AssistantOrchestrationServiceTest`, `AssistantRateLimiterTest`, `OfficialWarningToolTest`, `PM25RiskToolTest`, `WeatherToolTest`, `AirQualityToolTest`
- **Output Summary:**
  ```
  [INFO] Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
  [INFO] BUILD SUCCESS
  [INFO] Total time: 24.816 s
  ```
- **Exit Code:** `0` (All 36 backend contract tests passed)

### 6.4 Backend Controller & Vision Integration Tests
- **Command:** `mvn.cmd test -Dtest="*AssistantController*,*CitizenVision*"`
- **Output Summary:**
  ```
  [INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
  [INFO] BUILD SUCCESS
  [INFO] Total time: 30.805 s
  ```
- **Exit Code:** `0` (Validation errors, rate limiting, and vision fallback HTTP behaviors verified)

### 6.5 Live REST API Contract Verification

| Endpoint | Method | Payload / Query | Observed Response Status | Verified Data Properties |
|---|---|---|---|---|
| `/api/v1/assistant/health` | GET | None | `200 OK` | `service: "AeroSentinel Conversational Assistant"`, `mode: "Grounded Tool Orchestration"`, `status: "UP"` |
| `/api/v1/assistant/weather/current` | GET | `?cityName=Pune` | `200 OK` | `temperature: 33.1°C`, `humidity: 38.0%`, `source: "Open-Meteo NWP Model Analysis"`, `freshness: "LIVE"` |
| `/api/v1/assistant/air-quality` | GET | `?cityName=Pune` | `200 OK` | `stationCode: "PUN-001"`, `pm25: 78.0 µg/m³`, `source: "CPCB"`, `freshness: "NO_DATA"` (35,110m old) |
| `/api/v1/assistant/warnings` | GET | `?cityName=Pune` | `200 OK` | `status: "NO_ACTIVE_WARNINGS"`, `sourceAuthority: "IMD / NDMA SACHET"`, `sourceUrl: "https://sachet.ndma.gov.in"` |
| `/api/v1/assistant/pm25-risk` | GET | `?cityName=Pune` | `200 OK` | `domainSupported: true`, `status: "VALID"`, naive persistence limitation explicitly present |
| `/api/v1/assistant/pm25-risk` | GET | `?cityName=Mumbai` | `200 OK` | `domainSupported: false`, `status: "MODEL_DOMAIN_UNSUPPORTED"`, risk withheld outside Pune |
| `/api/v1/assistant/chat` | POST | `{"message":"What is the air quality in Pune right now?"}` | `200 OK` | Grounded answer referencing PUN-001, 78.0 µg/m³, staleness disclosure, source reference pills |
| `/api/v1/assistant/chat` | POST | `{"message":"What is the air quality?"}` | `200 OK` | `status: "NEEDS_CLARIFICATION"`, `clarificationPrompt: "Which city or area would you like environmental information for...?"` |

---

## 7. Browser Verification & Environmental Disclosures

### 7.1 Port Collision Investigation
During runtime browser verification, an environmental socket collision was observed on the local machine:
- A secondary project process (`node.exe` running `Smart Timetable` Next.js server, PID `34200`) had previously established a listening socket on `[::]:3000` (IPv6).
- The AeroSentinel Vite server was actively listening on `0.0.0.0:3000` (IPv4).
- Direct connection to `http://127.0.0.1:3000` correctly and exclusively reaches the AeroSentinel application (`<title>AeroSentinel — Hyperlocal Air Quality & Pollution Intelligence</title>`).

### 7.2 Browser Automation Execution
- Real browser inspection was conducted using live Chromium DevTools Protocol (CDP) session against the running application instance (`http://127.0.0.1:3000`) and Spring Boot backend (`http://localhost:8080`).
- Automated browser subagent interactive session was supplemented with programmatic CDP automation to test all 5 required responsive viewports, evaluate DOM bounding rects, interact with prompt chips, and verify live backend streaming responses.
- Concrete screenshot evidence was captured from actual browser rendering and saved to the repository workspace (`scratch/*.png`).

---

## 8. Data Provenance & Reliability Findings

1. **Ground Telemetry Provenance:**
   - Provider: Central Pollution Control Board (CPCB) via OpenAQ sync.
   - Station: Shivajinagar CAAQMS (`PUN-001`).
   - Ground Value: 78.0 µg/m³ PM2.5.
   - Provenance Truth: Observed on `2026-09-24T22:00:00Z`. Flagged with `NO_DATA` freshness status. Telemetry age is transparently stated as over 24 days old, preventing obsolete data from misleading citizens.
2. **Surface Weather Provenance:**
   - Provider: Open-Meteo.com under Creative Commons Attribution 4.0 International (CC BY 4.0).
   - Provenance Truth: Disclosed as derived from global Numerical Weather Prediction (NWP) model analysis rather than a physical weather station.
3. **Official Warnings Provenance:**
   - Provider: India Meteorological Department (IMD) / National Disaster Management Authority (NDMA SACHET).
   - Protocol: OASIS Common Alerting Protocol (CAP RSS 2.0).
   - Provenance Truth: Active NDMA portal link (`https://sachet.ndma.gov.in`) provided. Unreachable feeds trigger `FEED_UNAVAILABLE` rather than false safety reassurances.
4. **Machine Learning Risk Provenance:**
   - Provider: AeroSentinel F3 Random Forest Hotspot Classifier (`hotspot_classifier_v1.joblib`).
   - Domain Boundary: Strictly calibrated for the Pune metropolitan bounding box (`[18.0..19.2°N, 73.3..74.4°E]`).
   - Provenance Truth: Transparently discloses holdout performance vs. the persistence baseline. Non-Pune queries return `MODEL_DOMAIN_UNSUPPORTED`.

---

## 9. Responsive Design & Accessibility Audit

1. **Responsive Viewport Tokens:**
   - Desktop (&ge; 1024px): Multi-column grid layout displaying Weather, Air Quality, PM2.5 Risk, and Official Warnings cards side-by-side with sticky header and top ambient glow.
   - Tablet (768px – 1023px): Two-column adaptive card reflow with flexible font metrics and preserved card margins.
   - Mobile (&le; 767px): Single-column stack with zero horizontal overflow (`overflow-x: hidden`). Nav tabs collapse into a full-width drawer toggled by a touch-target hamburger icon (`aria-label="Open menu"`).
2. **Chat Input Ergonomics:**
   - The assistant input container is anchored with `position: sticky; bottom: 0;` and a background blur backdrop filter (`backdrop-filter: blur(12px)`).
   - Keyboard navigation supports `Enter` to submit and `Shift + Enter` for multi-line inputs.
3. **Accessibility Attributes:**
   - Landmark roles implemented: `<header className="citizen-navbar" role="banner">`, `<main className="citizen-main-content">`, `<div className="citizen-card" role="region">`.
   - Accessible names provided for all interactive controls: `aria-label="AeroSentinel Citizen Portal Home"`, `aria-label="Current location: Pune, Maharashtra"`, `aria-label="Toggle visual theme"`, `aria-label="Refresh telemetry"`.
   - High-contrast visual indicators: NAAQS PM2.5 color badges (`var(--aqi-good)`, `var(--aqi-moderate)`, `var(--aqi-poor)`, `var(--aqi-severe)`) paired with text category labels to ensure readability for colorblind users.

---

## 10. Defects Identified, Remediated & Remaining Risks

### 10.1 Defects Identified & Resolved
1. **Spring Constructor Ambiguity in AI Client Components:**
   - **Defect:** Multiple public constructors existed in `OfficialWarningClient.java`, `AssistantSynthesisClient.java`, `CitizenVisionAiClient.java`, `ForecastAiClient.java`, and `EvidenceAiClient.java`. Without `@Autowired`, Spring's constructor resolution defaulted to searching for a no-argument constructor, throwing `NoSuchMethodException: ...<init>()` and failing ApplicationContext startup.
   - **Resolution:** Added `@Autowired` to the primary Spring configuration constructor on each component.
   - **Verification:** Recompiled, repackaged, and verified via `mvn test` (all 46 tests passed) and live Tomcat startup on port 8080.
2. **Clock-Sensitive Hardcoded Expiration in `OfficialWarningToolTest.java`:**
   - **Defect:** The unit test used a hardcoded expiration timestamp (`2026-10-19T06:00:00Z`). When execution passed 06:00 UTC, the test failed as the alert was legitimately filtered as expired.
   - **Resolution:** Updated the mock test fixture to use dynamic timestamps (`Instant.now().plus(Duration.ofHours(24))`).
   - **Verification:** Re-ran test; passed in 0.216 seconds.
3. **Narrow Mobile Hamburger Menu Clipping (360px – 390px Viewports):**
   - **Defect:** On narrow mobile screens (&le; 390px), the top navbar was overcrowded with 5 separate action controls plus the brand title, pushing the hamburger button off-screen (`menuBtnRect.right = 391px` on 360px viewport, `isClipped: true`). Users on narrow devices could not access the mobile navigation drawer.
   - **Resolution:** Refined `@media (max-width: 640px)` in `frontend/src/components/citizen/citizen.css` to hide the non-essential `CITIZEN PORTAL` subtitle and desktop Command Center button in the top bar on mobile, and added a dedicated Command Center navigation button into `frontend/src/components/citizen/CitizenNavbar.tsx` inside the mobile drawer.
   - **Verification:** Programmatic bounding client rect measurement confirmed `isClipped: false` on 360x800 (`right: 353px`) and 390x844 (`right: 374px`). Visual verification screenshots confirmed unclipped rendering (`scratch/fixed_mobile_menu_360x800.png`, `scratch/fixed_mobile_drawer_360x800.png`).

### 10.2 Known Limitations & Environmental Risks
- **Historical Ground Sensor Telemetry:** Active CPCB ground observations stored in the local development database originate from September 2026. While the UI and API handle this with perfect truthfulness (`NO_DATA` freshness status), fresh live OpenAQ synchronization will be required for real-time live citizen deployments.

---

## 11. Final Acceptance Checklist

- [x] Citizen entry button present on start UI and header without breaking existing layout
- [x] Dedicated Citizen Portal route (`/citizen/*`) rendered under isolated `CitizenLayout`
- [x] Authority Command Center routes and navigation completely preserved
- [x] Location selector handles city switching, manual coordinates, and geolocation
- [x] In-situ air quality observations truthfully rendered with station attribution and freshness states
- [x] Surface weather accurately labeled as Open-Meteo NWP model analysis
- [x] Official warnings strictly mapped to IMD / NDMA feeds; `FEED_UNAVAILABLE` never shown as "no warnings"
- [x] Experimental PM2.5 forecasts labeled with `EXPERIMENTAL_ML` and persistence benchmark disclosure
- [x] ML risk strictly bounded to Pune; non-Pune queries return `MODEL_DOMAIN_UNSUPPORTED`
- [x] Natural-language assistant questions route strictly through backend orchestrator (zero client LLM keys)
- [x] Source attribution pills, timestamps, and model limitation disclaimers rendered in chat bubbles
- [x] Missing-location prompts trigger inline clarification workflow
- [x] Rate limiting (HTTP 429) triggers 60-second countdown lock in UI
- [x] Visual and responsive audit completed across all 5 viewports (1440, 1024, 768, 390, 360)
- [x] Narrow mobile hamburger clipping resolved and validated via CDP bounding box checks
- [x] Frontend test suite: 341 tests pass (0 failures)
- [x] TypeScript type-check and Vite production build pass without errors
- [x] Backend assistant, tool, and vision contract tests: 46 tests pass (0 failures)
- [x] Live backend API endpoints verified via HTTP requests

---

## 12. Final Gate & Recommendation

### Final Status: **PASS (AUDIT CLOSED)**

All core acceptance criteria and visual/responsive requirements for **AeroSentinel Phase 5 (Citizen UX & Conversational Portal)** are fully verified across live browser rendering, backend contract endpoints, and automated test layers. Spatial isolation from authority workflows is complete, and narrow-mobile ergonomics have been verified down to 360px.

---

## 13. Visual & Responsive Verification Addendum

### 13.1 Routes & URLs Audited
The following active routes were verified against the real running application (`http://127.0.0.1:3000` and Spring Boot backend `http://localhost:8080`):
1. **Landing / Start Page:** `http://127.0.0.1:3000/`
2. **Citizen Overview:** `http://127.0.0.1:3000/citizen`
3. **Conversational Assistant:** `http://127.0.0.1:3000/citizen/assistant`
4. **Weather & Forecast:** `http://127.0.0.1:3000/citizen/weather`
5. **Air Quality Telemetry:** `http://127.0.0.1:3000/citizen/air-quality`
6. **Official Warnings:** `http://127.0.0.1:3000/citizen/warnings`
7. **Authority Command Center (Return Navigation):** `http://127.0.0.1:3000/dashboard`

### 13.2 Viewports Tested
The real application was rendered and audited at all five required viewport dimensions:
- **Desktop (1440 × 900):** Multi-column layout, full horizontal desktop navbar, complete cards grid (`scratch/cdp_citizen_desktop_1440x900.png`, `scratch/pune_1440_fullpage.png`).
- **Laptop / Tablet Landscape (1024 × 768):** Reflowed layout, navigation links collapsed to hamburger icon with preserved quick actions, zero overflow (`scratch/cdp_citizen_laptop_1024x768.png`).
- **Tablet Portrait (768 × 1024):** Vertical card stack reflow, legible fonts and metrics, zero horizontal clipping (`scratch/cdp_citizen_tablet_768x1024.png`).
- **Mobile (390 × 844):** Single-column layout, sticky assistant input bar, unclipped hamburger menu (`menuBtnRect.right = 374px < 390px`), `isClipped: false` (`scratch/fixed_mobile_menu_390x844.png`).
- **Narrow Mobile (360 × 800):** Compact mobile layout, zero horizontal overflow (`scrollWidth = 360px`), hamburger menu comfortably within viewport (`menuBtnRect.right = 353px < 360px`), `isClipped: false` (`scratch/fixed_mobile_menu_360x800.png`, `scratch/fixed_mobile_drawer_360x800.png`).

### 13.3 Observed Results & Concrete Rendering Evidence
1. **Start Page & Citizen Entry:**
   - Header contains prominent cyan `Citizen Portal` pill button (`id="header-citizen-portal-btn"`).
   - Hero section includes primary `Citizen Portal` action button (`id="hero-citizen-portal-btn"`).
   - Clicking immediately transitions to `/citizen` under `CitizenLayout`.
   - Evidence: `scratch/start_page_1440.png`, `scratch/cdp_start_page_1440.png`.
2. **Separation of Citizen & Authority Interfaces:**
   - On `/citizen/*`, zero authority incident queues, triage candidate cards, or admin sidebars are rendered.
   - Clicking `Command Center` in `CitizenNavbar` smoothly returns to `/dashboard`, restoring the operational console, H3 map, and alert triage queues.
   - Evidence: `scratch/cdp_dashboard_return_1440.png`.
3. **Data Freshness, Provenance & Anti-Fabrication:**
   - **Surface Weather:** Displays live Open-Meteo numerical weather prediction model analysis (`31.4°C`, 36% humidity, 11.5 km/h wind) with `LIVE` green badge and explicit disclaimer noting it is derived from model reanalysis, not in-situ physical telemetry.
   - **Ground Air Quality:** Shivajinagar CAAQMS (`PUN-001`) PM2.5 = 78.0 µg/m³; truthfully flagged as `NO_DATA` with orange badge reflecting 24-day sensor age rather than fabricating fake live data.
   - **Official Warnings:** NDMA SACHET feed reports `NO_ACTIVE_WARNINGS` with direct statutory portal link (`https://sachet.ndma.gov.in`).
   - **ML Domain Boundary:** Pune queries render `LOW RISK` with `EXPERIMENTAL_ML` badge and benchmark disclosure (persistence MAE 8.4 vs RF 11.2). When switched to Mumbai, the UI immediately withholds risk projections and renders: *"Capability Unavailable: Geographic Domain Boundary. Operational spatial regressors are not yet validated for Mumbai."*
   - Evidence: `scratch/cdp_citizen_mumbai_domain_boundary.png`, `scratch/pune_1440_fullpage.png`.
4. **Conversational Assistant & Sourced Evidence:**
   - Navigating to `/citizen/assistant` renders quick prompt chips, language selector, and anchored bottom input bar.
   - Live interaction executed in real browser: Clicking "What is the weather in Pune right now?" returned grounded live weather from Open-Meteo with structured "VERIFIED SOURCED EVIDENCE" citation card, observation timestamp, and provider disclaimers.
   - Evidence: `scratch/assistant_live_chat_response.png`, `scratch/assistant_clarification_state.png`.
5. **Keyboard Navigation & Visible Focus:**
   - Tab key sequence systematically traversed brand button, navigation links, and city selector with visible focus outlines (`rgb(16, 16, 16) auto 0.8px`).
   - Evidence: `scratch/keyboard_focus_state.png`.

### 13.4 Observed Defect & Minimal Resolution
- **Defect:** On narrow mobile screens (360px and 390px), top bar overcrowding pushed the mobile hamburger button off-screen (`menuBtnRect.right = 391px > 360px`, `isClipped: true`).
- **Resolution:**
  - `frontend/src/components/citizen/citizen.css`: Streamlined mobile navbar metrics under `@media (max-width: 640px)` (hid non-essential portal subtitle tag and command center button on mobile top bar, reduced icon button footprint).
  - `frontend/src/components/citizen/CitizenNavbar.tsx`: Added dedicated Command Center link into the mobile drawer.
- **Verification:** Re-measured bounding boxes: 360px viewport &rarr; `right: 353px`, `isClipped: false`; 390px viewport &rarr; `right: 374px`, `isClipped: false`.

### 13.5 Scope Limits & Next Steps
- Automated interactive browser subagent session was curtailed due to user cancellation; full visual verification was completed via direct headless Chrome DevTools Protocol automation.
- **Source Files Changed:** `frontend/src/components/citizen/citizen.css`, `frontend/src/components/citizen/CitizenNavbar.tsx`.
- **Tests Rerun:**
  - `frontend`: `npm test` (341 tests passed, 0 failed).
  - `frontend`: `npm run build` (`tsc -b && vite build`, exit code 0).
- Phase 5 is closed. No Phase 6 tasks or implementation work has been initiated.

