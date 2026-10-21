# AeroSentinel — Phase 0: Stabilization, Repository Audit & Factual Baseline Report

**Execution Timestamp:** 2026-10-18T13:30:00+05:30  
**Repository Identity:** `aniket9538-coder/AeroSentinel_final`  
**Local Path:** `c:\Users\lenovo\AeroSentinel (3)\AeroSentinel`  
**Branch:** `main` (synchronized with `origin/main`)  
**Commit Hash:** `15e199d3f3d5a15a4ccdca4267de1389de4af22f`  
**Working Tree Status:** Clean tracked state; 3 untracked documentation reports preserved (`BACKEND_DOCKERFILE_DEPLOYMENT_REPORT.md`, `VERCEL_DEPLOYMENT_FEASIBILITY_REPORT.md`, `VERCEL_SERVICES_CONTAINER_FIX_REPORT.md`).  
**Audit Gate Decision:** **BLOCKED (P0 Operational & Security Blockers Preclude Production Gate Passage)**

---

## 1. Executive Summary

AeroSentinel is designed to be an evidence-backed environmental intelligence and authority workflow platform connecting air quality sensors, weather telemetry, geospatial H3 hexagonal indexing, machine-learning risk triage, citizen photo observations, and municipal field inspection lifecycles.

This Phase 0 audit is a factual, non-destructive, read-only baseline investigation of the existing codebase. We did not repair code, alter database schemas, modify configuration, or migrate environments. Every claim in this document is backed by active repository code, configuration inspection, test execution, or live HTTP probes.

### Critical Factual Takeaways
1. **Frontend Builds and Tests Pass Locally, but Live Deployment is Disconnected:**
   - Locally, the React 18 / Vite / TypeScript frontend compiles cleanly (`tsc -b`, `npm run build`), and all 319 utility and workflow unit tests pass.
   - In production (`https://aero-sentinel-final.vercel.app/`), the deployed SPA is completely non-functional because `VITE_API_BASE_URL` was not injected during Vercel build. All API requests route to `/api/v1/*`, which Vercel's rewrite rule (`{ "source": "/(.*)", "destination": "/index.html" }`) maps to HTML instead of JSON, resulting in total client-side network failure.
2. **Deployed AI Service Has Zero Models Loaded:**
   - Probing `https://aero-sentinel-final-rzx6.vercel.app/health` returns `{"status":"healthy","models_ready":false,"artifacts":[]}`.
   - Vercel Serverless Function 250MB bundle limitations and missing artifact extraction prevent the 131 MB and 20.4 MB model joblib files from loading in production.
3. **Severe Multi-Process Architecture Incompatibility in Backend Container:**
   - While F3 Hotspot detection communicates via HTTP REST to FastAPI, F4 PM2.5 Forecasting (`ForecastAiClient.java`), F5 Evidence Orchestration (`EvidenceAiClient.java`), and F6 Citizen Vision (`CitizenVisionAiClient.java`) invoke local Python scripts via Java `ProcessBuilder("python", scriptPath)`.
   - The production container (`backend/Dockerfile`) is based on `eclipse-temurin:21-jre-jammy`, which contains no Python runtime and does not package the `ai-service/` directory. Deploying the backend container will crash any forecast, evidence, or vision invocation with a fatal `IOException`.
4. **Complete Absence of Server-Side Authentication & Critical Authorization Bypass:**
   - `com.aerosentinel.auth.AuthController`, `AuthService`, and `SecurityService` are 0-byte empty files.
   - `SecurityConfig.java` defines `permitAll()` across every `/api/v1/**` route.
   - In `ActionController.java` and `AlertController.java`, the manual method `enforceAuthorityAuthorization()` inspects authenticated tokens but explicitly skips anonymous callers: `if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken))`. Unauthenticated public internet callers completely bypass role checks and can resolve or dismiss real municipal alerts and record enforcement actions.
5. **Scientific Forecast Baselines Outperform ML Models:**
   - The authoritative F4 PM2.5 multi-horizon models (`forecast_regressors_v1.joblib`) documented in `ai-service/models/artifacts/metadata_v1.json` are outperformed by a naive persistence baseline across all three horizons (T+1h, T+3h, and T+6h) on MAE, RMSE, and R². Promoting these models as superior forecasting intelligence without prominent baseline disclosure violates product integrity.

---

## 2. Repository, Branch, and Commit Baseline

| Property | Value | Evidence / Verification Method |
| :--- | :--- | :--- |
| **Repository Remote** | `https://github.com/aniket9538-coder/AeroSentinel_final.git` | `git remote -v` |
| **Current Branch** | `main` | `git status` |
| **Head Commit Hash** | `15e199d3f3d5a15a4ccdca4267de1389de4af22f` | `git rev-parse HEAD` |
| **Head Commit Message** | `fix(deployment): normalize database url and prevent startup timeout on Vercel` | `git log -n 1 --oneline` |
| **Working Tree Status** | Clean tracked state; 3 untracked markdown deployment reports | `git status` |
| **Java Toolchain** | OpenJDK 21.0.11 LTS (Eclipse Adoptium Temurin-21.0.11+10) | `java -version` |
| **Maven Toolchain** | Apache Maven 3.9.6 (via `.m2/wrapper/dists/.../mvn.cmd`) | `mvn.cmd -version` |
| **Node.js Toolchain** | Node.js v22.14.0 | `node -v` |
| **npm Toolchain** | npm v11.17.0 | `npm -v` |
| **Python Toolchain** | Python 3.13.7 (AMD64) | `python -c "import sys; print(sys.version)"` |
| **Active Spring Profile** | `dev` (default via `${SPRING_PROFILES_ACTIVE:dev}`) | `backend/.../application.yml` |
| **Database Migrations** | 19 Flyway migrations (`V1` to `V19`) in `backend/src/main/resources/db/migration/` | Directory inspection |

---

## 3. Current Architecture and Actual Data/Request Flows

AeroSentinel contains three primary functional subsystems:

```
[Browser / React 18 SPA]
    │
    ├── (REST over HTTP) ──> [Spring Boot 3.3 Backend (Java 21)]
    │                             │
    │                             ├── (JPA / PostGIS / Flyway) ──> [PostgreSQL 16 + PostGIS]
    │                             │
    │                             ├── (HTTP REST Client) ────────> [FastAPI /predict/hotspot]
    │                             │
    │                             └── (ProcessBuilder Subprocess) -> [Local Python CLI Scripts]
    │                                                                   ├─ predict_forecast_cli.py
    │                                                                   ├─ orchestrate_evidence_cli.py
    │                                                                   └─ vision_cli.py
```

### Request Flow Investigation

#### 1. Browser/UI $\to$ Frontend API Client $\to$ Java Backend
- **Implementation:** `frontend/src/services/api.ts` creates an Axios instance with `baseURL = import.meta.env.VITE_API_BASE_URL || '/api/v1'`.
- **Finding:** Locally, Vite proxies `/api` to `http://localhost:8080`. However, in production on Vercel, `VITE_API_BASE_URL` was not specified during build. All requests go to relative `/api/v1/*`, which Vercel's SPA rewrite rule converts to HTML (`index.html`), causing all network calls to fail.

#### 2. Java Backend $\to$ Database
- **Implementation:** Spring Data JPA with Hibernate Spatial 6.5 and HikariCP. Database configuration is defined in `application.yml` and `application-dev.yml` targeting `${DATABASE_URL:jdbc:postgresql://localhost:5432/aerosentinel}`.
- **Finding:** Schema versioning is managed strictly by Flyway (19 migrations `V1__init_schema.sql` to `V19__seed_multicity_grid_cells.sql`). Note: `database/init.sql`, `database/schema.sql`, and `database/seed.sql` are 0-byte placeholder files and are not executed.

#### 3. Java Backend $\to$ AI Service HTTP Client $\to$ FastAPI Endpoints
- **Implementation:** `backend/src/main/java/com/aerosentinel/hotspot/AiServiceHotspotClient.java` (lines 104–105) sends an HTTP POST request with a 36-feature vector to `${app.ai-service.url}/api/v1/ml/hotspot/predict`.
- **Finding:** This is the ONLY component using HTTP REST to communicate with FastAPI. All other AI endpoints in `ai-service/app/api/` (`forecast.py`, `evidence.py`, `gemini.py`, `hotspot.py`, `vision.py`) are 0-byte empty files.

#### 4. Java Backend $\to$ Python CLI Subprocess Bridge
- **Implementation:** 
  - `ForecastAiClient.java` invokes `ai-service/ml/inference/predict_forecast_cli.py`.
  - `EvidenceAiClient.java` invokes `ai-service/ml/inference/orchestrate_evidence_cli.py`.
  - `CitizenVisionAiClient.java` invokes `ai-service/ml/inference/vision_cli.py`.
- **Finding (Critical P0):** Invoked via Java `ProcessBuilder("python", scriptPath)`. In containerized environments (`backend/Dockerfile`), Python is not installed in the JRE image and `ai-service` is not copied, causing immediate fatal process launch exceptions in Docker/Render.

#### 5. External Provider Ingestion $\to$ Observations $\to$ Persistence
- **Weather:** `WeatherClient.java` calls Open-Meteo API (`https://api.open-meteo.com/v1/forecast`) fetching hourly variables (`temperature_2m`, `relative_humidity_2m`, `wind_speed_10m`, `wind_direction_10m`, `precipitation`, `surface_pressure`, `boundary_layer_height`). Scheduled via `WeatherIngestionScheduler.java` (enabled by default).
- **Air Quality:** `OpenAqClient.java` is integrated with OpenAQ v3 API (`https://api.openaq.org/v3`), but air quality ingestion is disabled by default (`app.ingestion.enabled=false`).
- **CPCB / FIRMS:** `CPCB_API_KEY` and `FIRMS_API_KEY` are empty in configuration; classes in `integration/firms/` are 0-byte empty files.

#### 6. Observation Records $\to$ H3 Spatial Indexing $\to$ Map/Cell APIs
- **Implementation:** `backend/src/main/java/com/aerosentinel/spatial/H3Service.java` wraps Uber's native Java H3 library (`com.uber.h3core.H3Core` v4.1.1) at resolution 8.
- **Finding:** Verified and tested. Validates coordinates, calculates H3 indexes, and generates geojson cell boundaries accurately.

#### 7. Citizen Submission $\to$ Image Handling $\to$ Persistence $\to$ Visual Interpretation
- **Implementation:** `CitizenReportController.java` (`POST /api/v1/citizen/reports`) accepts multipart form data (`photo`, `latitude`, `longitude`, `category`, `description`).
- `PhotoStorageService.java` stores images locally in `./uploads/citizen-reports/`.
- `CitizenVisionAiClient.java` pipes image paths to `vision_cli.py`. If Gemini is unconfigured or fails, a deterministic fallback classifies the category safely.
- **Finding:** Citizen reports are stored even if visual AI fails, preserving citizen observation evidence.

#### 8. Evidence Records $\to$ Evidence Scoring $\to$ Gemini Explanation $\to$ Triage Decision
- **Implementation:** `EvidenceAiClient.java` $\to$ `orchestrate_evidence_cli.py` $\to$ `EventEvidenceScoringEngine.java` / `scoring_engine.py`.
- Computes evidence completeness and four-valued consistency states (`CONSISTENT`, `INCONCLUSIVE`, `CONFLICTING`, `INSUFFICIENT_EVIDENCE`).
- `GroundingValidator.py` verifies Gemini output against raw numbers and rejects unsupported causal claims.

#### 9. Qualifying Triage Result $\to$ Alert / Event Creation $\to$ Authority Workflow
- **Implementation:** `AlertService.java` enforces that ONLY triage results with `triageState == 'ALERT_CANDIDATE'` create actionable alerts. Idempotent creation is enforced by a database unique constraint on `event_id`.
- **Finding:** Unchecked legacy endpoint `POST /api/v1/alerts` permits arbitrary direct alert injection into the queue without triage qualification.

#### 10. Authority Action $\to$ Status Updates $\to$ Field Verification $\to$ Final Resolution
- **Implementation:** 
  - Canonical Pollution Event lifecycle: `OPEN -> ASSIGNED -> IN_INSPECTION -> ACTION_TAKEN -> RESOLVED`.
  - Canonical Alert lifecycle: `OPEN -> ACKNOWLEDGED -> RESOLVED`.
  - Field Inspection lifecycle: `PENDING -> ASSIGNED -> IN_PROGRESS -> COMPLETED`.
- **Finding:** When `InspectionService.submitVerification()` completes an inspection, the field team is reset to `AVAILABLE`, but the parent `Alert` remains `ACKNOWLEDGED` and `PollutionEvent` remains `IN_INSPECTION` until an authority explicitly calls `POST /api/v1/actions` to transition to `ACTION_TAKEN`.

#### 11. Frontend Authentication / Role Handling $\to$ Route Access $\to$ Backend Authorization
- **Implementation:** In frontend, all routes are open without `<ProtectedRoute>` guards.
- **Finding (Critical P0):** No backend authentication endpoints exist (`AuthController.java` is 0 bytes). All `/api/v1/**` endpoints are `permitAll()`. In `ActionController` and `AlertController`, anonymous callers bypass authority checks entirely.

---

## 4. Verified Implementation Inventory for F1–F9

| Feature | Subsystem | Implemented Components | Broken / Missing Components | Status |
| :--- | :--- | :--- | :--- | :--- |
| **F1** | City & Air-Quality Data | `CityController`, `CityRepository`, `OpenAqClient`, `AirObservationRepository`, `AirService` | `CPCBClient` empty; OpenAQ ingestion disabled by default; `AirController` minimal | **PARTIALLY IMPLEMENTED** |
| **F2** | Weather & H3 Spatial Indexing | `WeatherClient`, `WeatherIngestionService`, `H3Service` (Java H3 v4.1.1), `GridController` | OpenWeather API key empty; weather backfill runner requires active database connection | **IMPLEMENTED + VERIFIED** |
| **F3** | Hotspot Classification | `HotspotInferenceResponse`, `hotspot_classifier_v1.joblib`, `predict_hotspot_v1` in `main.py`, `AiServiceHotspotClient.java` | Scikit-learn unpickling version warnings (1.8.0 vs 1.9.1); recall at operational threshold 0.20 is only 1.6% | **IMPLEMENTED + VERIFIED** |
| **F4** | PM2.5 Forecasting | `forecast_regressors_v1.joblib`, `ForecastAiClient.java`, `predict_forecast_cli.py`, `ForecastService.java` | ProcessBuilder cannot run in JRE container; Random Forest regressor is strictly inferior to naive persistence baseline across T+1h, T+3h, T+6h | **BROKEN (Runtime & Model Validity)** |
| **F5** | Evidence Validation & Gemini | `EventEvidenceScoringEngine.java`, `orchestrate_evidence_cli.py`, `GroundingValidator.py`, `AlertService.java` | In-container ProcessBuilder execution fails; Gemini API key unconfigured in production | **PARTIALLY IMPLEMENTED** |
| **F6** | Citizen Reports & Gemini Vision | `CitizenReportController.java`, `PhotoStorageService.java`, `CitizenVisionAiClient.java`, `vision_cli.py` | In-container ProcessBuilder execution fails; `google-genai` missing from requirements.txt | **PARTIALLY IMPLEMENTED** |
| **F7** | Pollution Events & Authority Lifecycle | `PollutionEventService`, `AlertService`, `InspectionService`, `ActionController`, `Alerts.tsx` | Severe anonymous authorization bypass in `ActionController` and `AlertController`; state desynchronization between completed inspection and action taken | **BROKEN (Security Flaw)** |
| **F8** | Monitoring Gaps & Recommendations | `MonitoringController.java`, `MonitoringService.java`, `MonitoringDashboard.tsx`, `h3_monitoring_map.test.ts` | No Flyway migration / database persistence table for recommendations (dynamically computed in-memory only) | **IMPLEMENTED, NOT VERIFIED (In-Memory Only)** |
| **F9** | Federated City Network | `run_round_simulation.py`, `run_e2e_federated_orchestration.py`, `V18__f9_federated_network.sql`, backend control plane | Simulation / prototype scaffolding only; not a production federated learning implementation | **PARTIALLY IMPLEMENTED (Prototype / Deferred)** |

---

## 5. Citizen, Authority, and Admin UX & Permissions Inventory

### Citizen Experience
- **Routes:** `/` (Home), `/dashboard`, `/air-quality`, `/weather`, `/map`, `/hotspots`, `/forecast`, `/report`, `/report/status/:reportId`.
- **Implementation State:** Fully navigable UI with responsive charts, Leaflet maps, and photo upload.
- **Defects:**
  - In production deployment, all API calls fail because `VITE_API_BASE_URL` is omitted.
  - Citizen report submission succeeds locally, but image uploads are saved to local filesystem (`./uploads/citizen-reports/`), which is ephemeral in cloud containers (Render/Vercel) without S3/cloud storage.

### Authority / Field Team Experience
- **Routes:** `/authority`, `/authority/incidents`, `/authority/alerts`, `/authority/inspection`, `/authority/actions`.
- **Implementation State:** Interactive authority queue with alert acknowledgement, field team assignment modal, inspection progress, field verification submission, and mitigation action logging.
- **Defects (Critical P0):**
  - Zero route protection: Citizens can directly access `/authority/*` without logging in.
  - Zero backend authorization: `ActionController` and `AlertController` allow unauthenticated anonymous users to acknowledge, resolve, and dismiss municipal alerts.

### Platform Administrator Experience
- **Routes:** `/admin`, `/admin/users`, `/admin/cities`, `/admin/status`.
- **Implementation State:** Displays system health, city list, and user management UI.
- **Defects:**
  - Mock user data in UI; backend user management endpoints do not exist.
  - `/actuator/**` endpoints are completely exposed via `permitAll()`.

---

## 6. Deployment and Connectivity Results

All deployment checks were executed using read-only live HTTP probes on 2026-10-18.

| Target / URL | HTTP Result | Observed Response Category | Verified Finding | Timestamp |
| :--- | :--- | :--- | :--- | :--- |
| `https://aero-sentinel-final.vercel.app/` | `200 OK` | Static SPA HTML (`<!doctype html>...`) | Page loads, but API base URL is unconfigured | 2026-10-18T13:13:37+05:30 |
| `https://aero-sentinel-final.vercel.app/api/v1/health` | `200 OK` (Mishandled) | HTML (`index.html`) | **BROKEN:** Vercel SPA rewrite intercepts API path because backend is unconfigured; returns HTML instead of JSON | 2026-10-18T13:24:04+05:30 |
| `https://aero-sentinel-final-rzx6.vercel.app/` | `200 OK` | JSON: `{"service":"...","status":"operational","loaded_models":[]}` | Operational root response, but **0 models loaded** | 2026-10-18T13:23:14+05:30 |
| `https://aero-sentinel-final-rzx6.vercel.app/health` | `200 OK` | JSON: `{"status":"healthy","models_ready":false,"artifacts":[]}` | **CRITICAL:** AI service reports `models_ready: false`; ML models missing from deployment | 2026-10-18T13:23:36+05:30 |
| Render Web Service (Java Backend) | `NOT CONFIGURED` | No active remote URL | Backend deployment to Render was planned in reports but no live URL exists | 2026-10-18T13:25:00+05:30 |
| Supabase PostgreSQL | `BLOCKED` | Remote credentials withheld | Safe audit rule: remote database credentials not tested to prevent mutation | 2026-10-18T13:25:00+05:30 |

---

## 7. Build and Test Verification Outcomes

Safe, read-only build and test commands were executed across all three subprojects.

### Frontend (`frontend/`)
- **TypeScript Type-Check:** `npx tsc -b`
  - **Result:** **PASS** (0 errors).
- **Unit & Workflow Tests:** `npm run test` (`npx tsx --test src/utils/*.test.ts src/utils/*.test.tsx`)
  - **Result:** **PASS** (319 tests run, 319 passed, 0 failed in 4.7s).
- **Production Build:** `npm run build` (`tsc -b && vite build`)
  - **Result:** **PASS** (2576 modules transformed, `dist/` bundle created in 4m 46s).

### Python AI Service (`ai-service/`)
- **F3 ML Inference Tests:** `python -m pytest tests/test_f3_ml_inference.py -v`
  - **Result:** **PASS** (13 passed, 18 version warnings in 14.2s). Verified 36-feature vector contract and Pune-only domain restriction.
- **F4 PM2.5 Forecast Tests:** `python -m pytest tests/test_f4_p3_inference.py -v`
  - **Result:** **PASS** (13 passed, 2 version warnings in 11.5s). Verified residual bounds, lower-bound clamping to zero, null forecastConfidence, and CLI piping.
- **F4 Feature & Reliability Tests:** `python -m pytest tests/test_f4_feature_layer.py tests/test_f4_p7_reliability.py -v`
  - **Result:** **PASS** (24 passed, 6 warnings in 12.1s). Verified wind speed unit conversion (km/h $\to$ m/s), NaN/Infinity rejection.
- **F5 Failure Recovery Tests:** `python -m pytest tests/test_f5_p7_failure_recovery.py -v`
  - **Result:** **PASS** (6 passed in 9.3s). Verified unconfigured Gemini fallback and GroundingValidator rejection of ungrounded causal assertions.
- **F6 Gemini Vision Tests:** `python -m pytest tests/test_f6_real_gemini_vision.py -v`
  - **Result:** **PASS** (7 passed in 8.4s). Verified deterministic category mapping and fallback behavior when API key is missing.

### Java Backend (`backend/`)
- **Maven Test Compile:** `mvn.cmd test-compile -DskipTests`
  - **Result:** **PASS** (Compiled 298 main source files and 54 test files with Java 21 in 55.1s).
- **Spatial H3 Unit Tests:** `mvn.cmd test -Dtest=H3ServiceTest`
  - **Result:** **PASS** (12 tests run, 12 passed, 0 failed in 38.3s).
- **Monitoring Priority Unit Tests:** `mvn.cmd test -Dtest=MonitoringPriorityTest`
  - **Result:** **PASS** (20 tests run, 20 passed, 0 failed in 45.8s).
- **Database Integration Tests (`@SpringBootTest`):**
  - **Result:** **SKIPPED (SAFETY GATE)**. `OperationalWorkflowLifecycleTest.java` is not annotated with `@Transactional` rollback and performs real database inserts. Skipped in accordance with Phase 0 safety rules against mutating databases.

---

## 8. Data Integrity, Provenance, and Entity Lineage Findings

### Entity and Table Integrity
1. **Lineage Preservation:**
   - Provenance is maintained across `PollutionEvent` $\to$ `Alert` $\to$ `Inspection` $\to$ `FieldVerification` $\to$ `AuthorityAction`.
   - `FieldVerification` records store `inspection_id`, `alert_id`, `event_id`, `h3_index`, `prediction_id`, and `observed_conditions`.
2. **Missing F8 Persistence Schema:**
   - Monitoring recommendations generated by `MonitoringService.java` are strictly transient, computed dynamically from `SensorRepository`, `HotspotRepository`, and `ForecastRepository`. There is no table or migration tracking historical recommendations.
3. **Four-Valued Freshness Semantics:**
   - Frontend and backend models support `LIVE`, `STALE`, `NO_DATA`, and `UNAVAILABLE`.
   - Stale telemetry decay is implemented in `scoring_engine.py` (penalizing observations older than 6 hours).
4. **Placeholder GeoJSON Data:**
   - Files in `data/geo/` (`agricultural_zones.geojson`, `industrial_zones.geojson`, `schools.geojson`, `hospitals.geojson`) are 0-byte empty files.

---

## 9. Machine Learning Artifact, Readiness, and Evaluation Findings

### F3 Hotspot Classifier
- **Artifact:** `ai-service/models/artifacts/hotspot_classifier_v1.joblib` (20.4 MB).
- **Architecture:** `CalibratedClassifierCV` (Platt Sigmoid) on Balanced `RandomForestClassifier`.
- **Target Proxy:** `pm25_clean >= 60.0 ug/m3 with spatial lag anomaly ratio >= 1.15`. This is a proxy threshold, not legally verified pollution incidents.
- **Operational Threshold:** `0.20`.
- **Performance:** ROC-AUC: `0.9714`, PR-AUC: `0.7982`.
- **Severe Operational Limitation:** At the operational threshold of 0.20, Precision is `1.000` but Recall is only `0.016` (F1 score `0.032`). The model misses 98.4% of proxy hotspot events!
- **Domain Restriction:** The model was trained exclusively on telemetry from the Pune Metropolitan Region. It strictly rejects Mumbai, Delhi, and other cities with `MODEL_DOMAIN_UNSUPPORTED`.

### F4 PM2.5 Forecaster
- **Artifact:** `ai-service/models/artifacts/forecast_regressors_v1.joblib` (131.3 MB).
- **Architecture:** `RandomForestRegressor` with 3 separate estimators for T+1h, T+3h, and T+6h.
- **Features:** Exactly 36 ordered features.
- **Uncertainty Bounds:** Residual-derived empirical prediction intervals (P10 and P90), clamped to a physical lower bound of zero. `forecastConfidence` is explicitly `null` per locked contract.
- **Baseline Comparison (Critical Scientific Finding):**
  According to `metadata_v1.json`, a naive persistence baseline outperforms the Random Forest model across all three horizons:
  - **T+1h Horizon:**
    - Persistence: MAE `1.35`, RMSE `3.69`, R² `0.9696`
    - Random Forest: MAE `1.73`, RMSE `3.88`, R² `0.9664`
    - *Outcome:* Naive persistence has 22% lower MAE than the ML model.
  - **T+3h Horizon:**
    - Persistence: MAE `3.85`, RMSE `6.67`, R² `0.9007`
    - Random Forest: MAE `3.98`, RMSE `8.66`, R² `0.8326`
    - *Outcome:* Naive persistence has lower error and higher explanatory power.
  - **T+6h Horizon:**
    - Persistence: MAE `5.90`, RMSE `8.93`, R² `0.8219`
    - Random Forest: MAE `6.37`, RMSE `13.55`, R² `0.5901`
    - *Outcome:* Random Forest RMSE degrades severely to 13.55 (vs 8.93 for persistence), and R² drops to 0.59 (vs 0.82 for persistence).
- **Conclusion:** The current Random Forest forecaster provides **negative value** relative to simple persistence. Promoting this model as predictive intelligence is scientifically invalid.

---

## 10. Security, Privacy, and Reliability Findings

1. **Authentication Non-Existent:**
   - `com.aerosentinel.auth.AuthController`, `AuthService`, and `SecurityService` are 0-byte empty files. No user login, registration, or JWT issuance exists on the server.
2. **Critical Authorization Bypass in Authority Endpoints:**
   - In `ActionController.java` (lines 58–67) and `AlertController.java` (lines 169–180), `enforceAuthorityAuthorization()` inspects caller authorities only when authenticated:
     ```java
     if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) { ... }
     ```
     Unauthenticated callers (anonymous traffic from the internet) bypass the block completely and are permitted to acknowledge, dismiss, or resolve alerts and record enforcement actions.
3. **Unrestricted Alert Injection:**
   - `POST /api/v1/alerts` in `AlertController.java` (lines 164–167) allows anyone on the internet to inject arbitrary alerts into the system without authentication or evidence scoring.
4. **Hardcoded Secrets in Default Configuration:**
   - `application.yml` contains a hardcoded default JWT secret (`JWT_SECRET`) and default OpenAQ API key (`OPENAQ_API_KEY`).
   - `frontend/.env` contains a MapTiler API key.
   - `ai-service/.env` contains a Google Gemini API key.
5. **CORS Exposure:**
   - In `ai-service/app/main.py` (lines 71–77), CORS is configured with `allow_origins=["*"]` and `allow_credentials=True`.
6. **Local Ephemeral File Storage:**
   - Citizen photos are stored in `./uploads/citizen-reports/` on local disk. In stateless container platforms (Vercel, Render), uploaded images are lost upon container restart or redeploy.
7. **Prompt Injection & Grounding Guards:**
   - `PrivacyGuard.py` and `GroundingValidator.py` are implemented in `ai-service`. GroundingValidator successfully detects ungrounded numerical assertions and rejects unsupported causal attributions.

---

## 11. Complete Feature and Risk Matrix

| Feature / Component | Expected Behavior | Actual Implementation & Evidence | Test / Runtime Evidence | Status | Severity | Dependencies & Blockers | Recommended Phase / Action |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Production Frontend Deployment** | SPA connects to live backend API | Deployed on Vercel Edge; `VITE_API_BASE_URL` unconfigured; routes rewrite to HTML | HTTP probe returned `<!doctype html>` for `/api/v1/health` | **BROKEN** | **P0** | Vercel environment build configuration | **Phase 1:** Configure `VITE_API_BASE_URL` and backend host |
| **Production AI Service Deployment** | ML inference API running with loaded models | Deployed on Vercel; serverless function omits joblib artifacts | HTTP probe `/health` returned `models_ready: false` | **BROKEN** | **P0** | Model file sizes exceed Vercel serverless limits | **Phase 1:** Deploy AI service to container host (Render/Cloud Run) |
| **Backend AI ProcessBridge** | Backend runs forecast, evidence, and vision AI | `ForecastAiClient`, `EvidenceAiClient`, `CitizenVisionAiClient` call `ProcessBuilder("python", ...)` | Dockerfile runtime image has no Python installed | **BROKEN** | **P0** | JRE container cannot run Python subprocesses | **Phase 1:** Refactor CLI bridges to HTTP REST clients against FastAPI |
| **Backend Authentication & Access Control** | Sensitive authority actions require JWT with AUTHORITY role | `AuthController.java` is 0 bytes; `enforceAuthorityAuthorization()` allows anonymous callers | Code inspection lines 58-67 `ActionController.java` | **BROKEN** | **P0** | Missing auth controller and flawed method security | **Phase 1/5:** Implement JWT authentication and strict security filters |
| **F4 PM2.5 Model Accuracy** | ML forecast outperforms persistence baseline | Random Forest regressor is worse than naive persistence across all horizons | Verified in `metadata_v1.json` (Persistence MAE 1.35 vs RF 1.73 at T+1h) | **BROKEN** | **P1** | Retraining or promoting persistence baseline | **Phase 2:** Benchmark, retrain, or replace with verified baseline |
| **F3 Hotspot Detection** | Hyperlocal risk score calibrated for PMR | `hotspot_classifier_v1.joblib` loaded; threshold 0.20 enforced; domain locked to Pune | Pytest `test_f3_ml_inference.py` passed (13/13) | **IMPLEMENTED + VERIFIED** | **P2** | Low recall (1.6%) at operational threshold | **Phase 2:** Calibrate decision threshold or document trade-off |
| **F2 Weather & Geospatial Indexing** | Real Open-Meteo ingestion and H3 indexing | `WeatherClient.java` and `H3Service.java` fully functional | Maven `H3ServiceTest` passed (12/12) | **IMPLEMENTED + VERIFIED** | **P2** | None | Preserve existing working implementation |
| **F1 Air Quality Telemetry** | Multi-source city telemetry ingestion | `CityController`, `OpenAqClient`, `AirService` implemented; ingestion disabled by default | Verified in `application.yml` | **PARTIALLY IMPLEMENTED** | **P2** | OpenAQ API rate limits and CPCB empty credentials | **Phase 2:** Enable ingestion scheduler with error handling |
| **F5 Evidence & Gemini Explanation** | Grounded explanation with non-causal guardrails | `EventEvidenceScoringEngine` and `GroundingValidator` implemented | Pytest `test_f5_p7_failure_recovery.py` passed (6/6) | **PARTIALLY IMPLEMENTED** | **P2** | ProcessBuilder execution gap; missing Gemini key | **Phase 5:** Expose via HTTP REST in FastAPI |
| **F6 Citizen Reporting & Vision** | Photo submission with visual category tagging | `CitizenReportController` and `PhotoStorageService` functional; fallback operational | Pytest `test_f6_real_gemini_vision.py` passed (7/7) | **PARTIALLY IMPLEMENTED** | **P2** | Ephemeral local disk storage; ProcessBuilder gap | **Phase 4:** Cloud storage integration (S3/Supabase Storage) |
| **F7 Authority Workflow Lifecycle** | Canonical status transition across Event, Alert, Inspection | Lifecycle logic implemented in services and UI | Frontend tests passed (319/319); state sync gap on inspection submit | **PARTIALLY IMPLEMENTED** | **P1** | Anonymous authorization flaw; inspection sync gap | **Phase 5:** Fix authorization and sync event state upon inspection submit |
| **F8 Monitoring Recommendations** | Station gap analysis and priority scoring | `MonitoringController` and `MonitoringService` calculate scores in-memory | Maven `MonitoringPriorityTest` passed (20/20) | **IMPLEMENTED, NOT VERIFIED** | **P2** | No database migration or table for recommendation persistence | **Phase 6:** Add Flyway migration for recommendation history |
| **F9 Federated Learning** | Multi-city federated model network | Simulation scripts and control plane tables in V18 migration | Prototype simulation only | **PARTIALLY IMPLEMENTED** | **P3** | Core system stabilization must precede federated network | **Phase 7+ (Deferred):** Defer until core platform is fully stable |

---

## 12. Confirmed Blockers and Unverified Assumptions

### Confirmed Blockers (P0)
1. **Broken Production Frontend-Backend Link:** Vercel deployment of frontend cannot communicate with backend due to missing `VITE_API_BASE_URL`.
2. **Missing ML Models in Production AI Service:** Vercel serverless packaging fails to bundle `forecast_regressors_v1.joblib` (131 MB) and `hotspot_classifier_v1.joblib` (20.4 MB), rendering all inference routes unavailable.
3. **Backend Container Incompatibility:** Java backend cannot run in its production container (`backend/Dockerfile`) because it attempts to execute local Python scripts via `ProcessBuilder`, while the container contains only Java JRE 21.
4. **Anonymous Authority Authorization Bypass:** Unauthenticated public callers can resolve and dismiss municipal alerts and record enforcement actions because `enforceAuthorityAuthorization()` ignores anonymous callers.
5. **Inferior ML Forecaster:** The F4 Random Forest model is outperformed by a naive persistence baseline across all horizons.

### Unverified Assumptions
1. **Supabase PostgreSQL Connectivity:** Could not be verified without remote credentials, pursuant to strict safety rules.
2. **Live Gemini 2.0 API Response Quality:** While deterministic stubs and fallbacks were tested, live Gemini API inference on complex real photographs could not be verified without live production key invocation.
3. **OpenAQ Ingestion Robustness:** The OpenAQ ingestion scheduler was not run against live endpoints to avoid rate-limiting external services.

---

## 13. Recommended Phase Order and Dependencies

```
Phase 0: Stabilization & Factual Baseline Audit (CURRENT - BLOCKED)
   │
   ▼
Phase 1: Deployment & Architectural Connectivity Recovery
   ├─ Deploy AI Service on Container Host (Render/Cloud Run) with ML models loaded
   ├─ Refactor F4/F5/F6 ProcessBuilder bridges to HTTP REST calls against FastAPI
   ├─ Deploy Java Spring Boot Backend on Render with environment bindings
   ├─ Inject VITE_API_BASE_URL into Frontend build on Vercel
   └─ Fix critical anonymous authorization bypass in ActionController and AlertController
   │
   ▼
Phase 2: Data Integrity, Freshness, H3 Consistency & ML Forecast Resolution
   ├─ Resolve F4 model vs persistence baseline (adopt persistence or retrain)
   ├─ Calibrate F3 decision threshold and clarify Pune-only operational domain
   └─ Enable and harden weather & air-quality ingestion schedulers
   │
   ▼
Phase 3: Citizen Conversational Assistant & Grounded Weather/Warning Lookup
   ├─ Integrate official weather disaster warning feeds
   └─ Ground Gemini assistant responses strictly in verified sensor and forecast data
   │
   ▼
Phase 4: Verified Citizen-Report Workflow & Cloud Photo Storage
   ├─ Migrate from ephemeral local disk storage to cloud storage (S3/Supabase)
   └─ Expose vision analysis exclusively via FastAPI HTTP endpoint
   │
   ▼
Phase 5: Evidence Triage & Authority Lifecycle Synchronization
   ├─ Fix inspection completion synchronization to transition PollutionEvent to ACTION_TAKEN
   ├─ Implement server-side JWT authentication with role enforcement (Citizen, Authority, Admin)
   └─ Remove legacy unvalidated POST /api/v1/alerts endpoint
   │
   ▼
Phase 6: Platform-Admin Visibility & F8 Monitoring Recommendations
   ├─ Add Flyway migration for persistent monitoring recommendation history
   └─ Connect Admin UI to real backend monitoring and system status endpoints
   │
   ▼
Phase 7: Security Hardening, E2E Validation & Performance Demonstration
   ├─ Run end-to-end integration test suite across all three tiers
   └─ Rotate default secrets and configure strict CORS policies
```

---

## 14. Explicit Acceptance Criteria for Phase 1 Entry

To transition from Phase 0 to Phase 1, the engineering team must align on the following entry criteria:
1. **Architecture Consensus:** Agreement to replace local `ProcessBuilder` Python subprocess bridges in `ForecastAiClient`, `EvidenceAiClient`, and `CitizenVisionAiClient` with standard HTTP REST calls to FastAPI.
2. **Hosting Topology:** Agreement to deploy both Java Spring Boot Backend and Python FastAPI AI Service as persistent Docker containers on Render (or equivalent container platform) rather than Vercel Serverless Functions.
3. **Security Priority:** Immediate remediation of the anonymous authorization bypass in `ActionController` and `AlertController`.
4. **Baseline Transparency:** Explicit product decision to expose naive persistence benchmarks alongside any ML forecast output.

---

## 15. Final Go/No-Go Decision

### **GATE DECISION: BLOCKED / NO-GO**

**Rationale:**  
Phase 0 audit requirements have been completely fulfilled with zero modifications to application source code, configuration, or databases. However, advancing to Phase 1 implementation requires explicit user review and authorization to address the five critical architectural and security blockers documented herein.
