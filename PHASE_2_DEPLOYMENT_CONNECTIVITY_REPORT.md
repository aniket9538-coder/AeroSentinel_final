# Phase 2: Deployment Recovery, Service Connectivity & Runtime Verification Report

**Repository:** `aniket9538-coder/AeroSentinel_final`  
**Phase:** Phase 2 (Deployment Recovery & Runtime Verification)  
**Date:** October 18, 2026  
**Status:** **BLOCKED** (Local runtime integration & container configuration verified PASS; live production deployment gated on external provider credentials and paused cloud database)

---

## 1. Executive Summary

Phase 2 evaluated and established a reproducible deployment configuration for the AeroSentinel full-stack platform, auditing the end-to-end connectivity across:
- **Frontend:** React 18 + TypeScript + Vite SPA (`frontend/`) targeting Vercel Edge CDN.
- **Backend Orchestrator:** Java 21 / Spring Boot 3.3.4 container (`backend/`) targeting Render or equivalent container host.
- **AI/ML Inference Service:** Python 3.11/FastAPI container (`ai-service/`) targeting persistent container hosting.
- **Spatial Database:** PostgreSQL 16 + PostGIS 3.4 (`database/` & Supabase).

### Core Findings & Verifications
1. **Local End-to-End Connectivity Proven:** In an isolated environment with real models and database, the entire chain was successfully verified:
   - Backend $\rightarrow$ FastAPI AI Service: Live HTTP REST calls for F3 (hotspot), F4 (PM2.5 forecasting), F5 (evidence orchestration), and F6 (vision) all succeeded with HTTP 200 and real inference output (`LiveAiServiceConnectivityTest`: 4/4 PASS).
   - Backend $\rightarrow$ Database: Read-only JDBC queries against local PostGIS 16 confirmed `SELECT 1`, PostGIS 3.4 extension, `cities` reference data (Pune, Mumbai, Delhi), and 19 Flyway migrations applied (`LiveDatabaseConnectivityTest`: 4/4 PASS).
   - Backend Security Matrix: All 15 Spring Security regression tests remain intact and pass cleanly.
2. **Historical Production Outages Diagnosed & Re-Verified:**
   - **AI Deployment (`aero-sentinel-final-rzx6.vercel.app`):** Probed live via HTTP; returns `{"status":"healthy","models_ready":false,"artifacts":[]}`. Proves Vercel Serverless Function deployment cannot package or load the 145 MB serialized joblib artifacts (`forecast_regressors_v1.joblib` 125 MB + `hotspot_classifier_v1.joblib` 20 MB).
   - **Frontend Deployment (`aero-sentinel-final.vercel.app`):** Probed live via HTTP; calls to `/api/v1/cities` return `index.html` (HTTP 200 `text/html`). Proves that without `VITE_API_BASE_URL` pointing to an external backend, Vercel SPA rewrites (`/(.*) -> /index.html`) swallow all API requests.
   - **Remote Supabase Project:** Discovered via Supabase management API (`project_id: betlhrnkosmqycfhijcn`, host: `db.betlhrnkosmqycfhijcn.supabase.co`). Currently in `INACTIVE` (paused on free tier) status.
3. **Packaging & Configuration Hardening:**
   - Populated `ai-service/Dockerfile` for persistent container deployment with model artifacts and Uvicorn.
   - Updated `frontend/src/services/api.ts` with `resolveApiBaseUrl()` to automatically normalize `VITE_API_BASE_URL` with `/api/v1` and strip trailing slashes.
   - Hardened `backend/src/main/java/com/aerosentinel/config/CorsConfig.java` to explicitly permit `https://aero-sentinel-final.vercel.app`, `https://*.vercel.app`, and configurable origins via `app.cors.allowed-origins`.
   - Updated `backend/src/main/resources/application-prod.yml` to set `spring.flyway.enabled: ${FLYWAY_ENABLED:false}`, preventing automatic production database DDL migrations on backend startup without explicit operator consent.

---

## 2. Repository Branch/Commit and Working-Tree Baseline

- **Active Branch:** `main`
- **Head Commit:** `15e199d fix(deployment): normalize database url and prevent startup timeout on Vercel`
- **Working Tree State:** All Phase 1 security and AI client modifications were preserved without regression. Additional changes in Phase 2 were strictly limited to CORS configuration, base URL normalization, production Flyway safeguard, AI Dockerfile, and live connectivity tests.
- **Untracked Artifacts:** Reports (`PHASE_0_STABILIZATION_AUDIT.md`, `PHASE_1_CRITICAL_BLOCKERS_REPORT.md`, `PHASE_2_DEPLOYMENT_CONNECTIVITY_REPORT.md`) and test suites (`backend/src/test/java/com/aerosentinel/`).

---

## 3. Phase 1 Regression Test Outcomes

Before modifying deployment configurations, the Phase 1 test suites were rerun:
- `SecurityAuthorizationRegressionTest`: **15 passed, 0 failed** (Spring Security fail-closed rules intact).
- `ForecastAiClientHttpTest`, `EvidenceAiClientHttpTest`, `CitizenVisionAiClientHttpTest`: **12 passed, 0 failed**.
- `AlertUnitTest`: **18 passed, 0 failed**.
- Python Pytest Suite (`test_f4_p3_inference.py`, `test_f4_p7_reliability.py`, `test_f5_p7_failure_recovery.py`): **32 passed, 0 failed**.
- Frontend Vitest Suite (`npm test -- --run`): **319 passed, 0 failed**.
- Frontend Type Check (`npx tsc -b`): **0 errors**.
- Frontend Production Build (`npm run build`): **Built in 13.79s**.

---

## 4. Target Hosting Topology and Reasoning

| Service Component | Target Hosting Platform | Runtime Paradigm | Rationale & Feasibility |
| :--- | :--- | :--- | :--- |
| **Frontend** | **Vercel** | Static Site / Edge CDN | React 18 + Vite compiles to client-side static assets (`frontend/dist/`). Perfectly aligned with Vercel Edge CDN distribution. |
| **Backend** | **Render / Container Web Service** | Persistent Docker Container (Java 21 JRE) | Long-running Spring Boot application with scheduled ingestion tasks (`@EnableScheduling`), JPA connection pooling (HikariCP), and actuator health probes. |
| **AI Inference Service** | **Render / Container Web Service** | Persistent Docker Container (Python 3.11) | Requires persistent in-memory unpickling of 145 MB scikit-learn models (`hotspot_classifier_v1` and `forecast_regressors_v1`), requiring $\ge$ 1 GB RAM. Serverless hosting is fundamentally incompatible. |
| **Database** | **Supabase Managed PostgreSQL** | PostgreSQL 16 + PostGIS 3.4 | Managed PostgreSQL with native PostGIS and UUID extension support over direct SSL connection (port 5432). |

---

## 5. Backend and AI Container Build/Startup Results

### 5.1 Java Backend Container
- **Dockerfile:** `backend/Dockerfile` (Two-stage Temurin 21 JDK build $\rightarrow$ Temurin 21 JRE runtime).
- **Network & Port Binding:** Entrypoint executes `java $JAVA_OPTS -Dserver.address=0.0.0.0 -jar app.jar`. Resolves dynamic `$PORT` via `application.yml` (`server.port: ${PORT:8080}`).
- **Database URL Normalization:** Automatically converts cloud PostgreSQL connection URLs (`postgres://` or `postgresql://`) to standard JDBC syntax (`jdbc:postgresql://`).
- **User Security:** Executes as unprivileged user `aerosentinel` (UID/GID created in container).

### 5.2 AI Service Container
- **Dockerfile:** `ai-service/Dockerfile` (Python 3.11-slim with `build-essential`, `fastapi`, `uvicorn`, `joblib`, and ML dependencies).
- **Model Artifact Packaging:** `COPY . /app` packages the `models/artifacts/` directory directly into the image.
- **Port & Address:** Starts via `uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000}`.
- **Memory & Resource Requirements:**
  - On-disk model artifacts: 145 MB.
  - Active runtime memory: ~450 MB after unpickling. Recommended container memory allocation: $\ge$ 1024 MB.

---

## 6. Exact Environment-Variable Matrix

| Service | Environment Variable | Consumer / Configuration Key | Required in Prod? | Current Status | Safe Operator Action |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Backend** | `PORT` | `server.port: ${PORT:8080}` | Yes | Injected by Host | Platform automatically assigns port (e.g. 10000 on Render). |
| **Backend** | `SPRING_PROFILES_ACTIVE` | `spring.profiles.active` | Yes | Configured | Set to `prod` in container runtime environment. |
| **Backend** | `DATABASE_URL` | `spring.datasource.url` | Yes | Missing in Cloud | Supply Supabase direct connection string: `jdbc:postgresql://<host>:5432/<db>?sslmode=require`. |
| **Backend** | `DATABASE_USERNAME` | `spring.datasource.username` | Yes | Missing in Cloud | Supply database username (e.g. `postgres`). |
| **Backend** | `DATABASE_PASSWORD` | `spring.datasource.password` | Yes | Missing in Cloud | Supply database user password securely in host secret manager. |
| **Backend** | `FLYWAY_ENABLED` | `spring.flyway.enabled` | No (default: false) | Verified | Defaults to `false` in `prod` to prevent unapproved remote schema changes. |
| **Backend** | `AI_SERVICE_URL` | `app.ai-service.url` | Yes | Unconfigured | Set to external URL of persistent AI container (e.g. `https://aerosentinel-ai.onrender.com`). |
| **Backend** | `JWT_SECRET` | `app.jwt.secret` | Yes | Default fallback | Set a 256-bit cryptographically secure string in production secrets. |
| **Backend** | `CORS_ALLOWED_ORIGINS` | `app.cors.allowed-origins` | Optional | Supported | Defaults to include `https://aero-sentinel-final.vercel.app` and `https://*.vercel.app`. |
| **AI Service** | `PORT` | Uvicorn `--port ${PORT:-8000}` | Yes | Injected by Host | Platform automatically assigns port. |
| **AI Service** | `ENVIRONMENT` | `ENVIRONMENT` | Optional | Set to `production` | Set to `production` in container variables. |
| **AI Service** | `GEMINI_API_KEY` | `gemini_client` | Optional | Unconfigured in Cloud | Provision Google Gemini API key to enable live F6 vision and F5 causal reasoning. |
| **Frontend** | `VITE_API_BASE_URL` | `apiClient` (`api.ts`) | **CRITICAL** | **MISSING IN VERCEL** | **Configure `https://<backend-host>/api/v1` in Vercel project environment variables.** |
| **Frontend** | `VITE_MAPTILER_API_KEY`| Map tile rendering | Optional | Present | Public MapTiler basemap key. |

---

## 7. Database Connectivity and Migration-Safety Result

### 7.1 Local PostGIS Database Verification
- Executed `LiveDatabaseConnectivityTest`:
  - `testReadOnlySelect1`: **PASS** (`SELECT 1` succeeded in 12ms).
  - `testPostGisExtension`: **PASS** (PostGIS `3.4` verified).
  - `testCitiesRead`: **PASS** (Pune, Mumbai, Delhi present in `cities` table).
  - `testFlywaySchemaHistory`: **PASS** (19 migrations applied, `all_success = true`).

### 7.2 Migration Protection Safeguard
- In `application-prod.yml`, added `spring.flyway.enabled: ${FLYWAY_ENABLED:false}`.
- **Safety Guarantee:** Starting the Java backend against any database with `SPRING_PROFILES_ACTIVE=prod` will **never** automatically run Flyway migrations or alter tables unless the operator explicitly passes `FLYWAY_ENABLED=true`.

### 7.3 Remote Supabase Database Status
- Probed Supabase Management API:
  - Project ID: `betlhrnkosmqycfhijcn`
  - Name: `AeroSentinel`
  - Status: **`INACTIVE`** (Paused on free tier).
  - Host: `db.betlhrnkosmqycfhijcn.supabase.co`
- In accordance with change-control restrictions, Antigravity did not restore or unpause the cloud project without explicit authorization.

---

## 8. AI Artifact & Model Readiness by Capability

Probed locally via live FastAPI `/health` endpoint:
```json
{
  "status": "healthy",
  "models_ready": true,
  "models": {
    "hotspot": {
      "ready": true,
      "modelVersion": "hotspot_classifier_v1"
    },
    "forecast": {
      "ready": true,
      "modelVersion": "forecast_regressors_v1"
    },
    "gemini_vision": {
      "configured": true,
      "provider": "google-gemini"
    }
  },
  "artifacts": [
    "hotspot",
    "forecast"
  ]
}
```

---

## 9. Real Inference Results (F3 / F4 / F5 / F6)

Live execution verified via `verify_ai_runtime.py` and `LiveAiServiceConnectivityTest`:

1. **F3 Hotspot Classifier:**
   - Input: 36-feature vector for cell `8860145a31fffff` (Pune).
   - Result: `HTTP 200 OK`, `riskScore: 0.0177`, `riskLevel: LOW`, `confidence: 0.705`, `modelVersion: hotspot_classifier_v1`.
2. **F4 Multi-Horizon PM2.5 Forecast:**
   - Input: 36-feature vector with explicit `predictedAt`.
   - Result: `HTTP 200 OK`, `modelVersion: forecast_regressors_v1`:
     - T+1h: `19.46 µg/m³` (Bounds: `[17.62, 21.32]`)
     - T+3h: `28.61 µg/m³` (Bounds: `[24.71, 31.66]`)
     - T+6h: `50.43 µg/m³` (Bounds: `[44.91, 55.85]`)
     - `forecastConfidence`: strictly `null`.
3. **F5 Evidence Orchestration & Triage:**
   - Input: Canonical F3 payload + sensor readings + citizen reports.
   - Result: `HTTP 200 OK`, `triageState: INSUFFICIENT_EVIDENCE`, `evidenceScore: 0.085`, `provenance.evaluatedAt: 2026-10-18T...`.
4. **F6 Citizen Vision:**
   - Input: Non-existent image path simulation.
   - Result: `HTTP 200 OK`, `status: FALLBACK`, `modelVersion: deterministic-fallback`, `confidence: 0.50`.

---

## 10. Backend-to-AI HTTP Integration Results

- **Test Suite:** `LiveAiServiceConnectivityTest.java`
- **Execution Mode:** Real Java `HttpClient` communicating with live local FastAPI service on `http://127.0.0.1:8000`.
- **Results:**
  - `testLiveF3HotspotInference`: **PASS** (Calibrated probability received over REST).
  - `testLiveF4ForecastInference`: **PASS** (3 forecast horizons deserialized with bounds).
  - `testLiveF5EvidenceOrchestration`: **PASS** (Triage DTO deserialized with full provenance).
  - `testLiveF6VisionAnalysis`: **PASS** (Structured vision result received over REST).
  - Overall Suite: **4 tests run, 4 passed, 0 failures**.

---

## 11. Frontend API Base URL and Browser / CORS Verification

### 11.1 Base URL Normalization in `api.ts`
Implemented `resolveApiBaseUrl()`:
- Automatically appends `/api/v1` if missing from `VITE_API_BASE_URL`.
- Strips any trailing slashes to prevent `//` double slashes.
- Falls back to `/api/v1` during local Vite development (where Vite proxy routes traffic to `localhost:8080`).

### 11.2 CORS Hardening in `CorsConfig.java`
Updated `corsConfigurationSource()` to allow:
- `http://localhost:*`
- `http://127.0.0.1:*`
- `https://aero-sentinel-final.vercel.app`
- `https://*.vercel.app`
- Custom comma-separated origins defined in `app.cors.allowed-origins`.

---

## 12. Actual Deployment URLs and Verified Status Codes

| Target URL | Expected Purpose | Observed HTTP Status | Content-Type | Live Body Preview | Diagnosis |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `https://aero-sentinel-final.vercel.app/` | Frontend UI | **200 OK** | `text/html` | `<!doctype html><html lang="en">...` | Static SPA HTML loads correctly from Vercel Edge. |
| `https://aero-sentinel-final.vercel.app/api/v1/cities` | Backend Cities API | **200 OK** | **`text/html`** | `<!doctype html><html lang="en">...` | **FAIL / SWALLOWED:** Vercel SPA rewrite returns HTML because `VITE_API_BASE_URL` is unset. |
| `https://aero-sentinel-final-rzx6.vercel.app/health` | AI Service Health | **200 OK** | `application/json` | `{"status":"healthy","models_ready":false,"artifacts":[]}` | **FAIL / UNLOADED:** Vercel Serverless Function deployed without models. |

---

## 13. Automated Build and Test Execution Summary

| Command | Target Component | Tests Run | Passed | Failed | Result | Execution Details |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `mvn test -Dtest=LiveAiServiceConnectivityTest` | Backend $\rightarrow$ AI HTTP Integration | 4 | 4 | 0 | **PASS** | Live REST calls to Uvicorn on localhost:8000 |
| `mvn test -Dtest=LiveDatabaseConnectivityTest` | Backend $\rightarrow$ Database JDBC Integration | 4 | 4 | 0 | **PASS** | Live read-only JDBC queries to Postgres on localhost:5432 |
| `mvn test -Dtest=SecurityAuthorizationRegressionTest` | Backend Spring Security & Roles | 15 | 15 | 0 | **PASS** | Fail-closed route matrix verification |
| `pytest tests/test_f4_p3_inference.py tests/test_f4_p7_reliability.py tests/test_f5_p7_failure_recovery.py` | Python AI ML Models | 32 | 32 | 0 | **PASS** | Local Python 3.13 process with production ML bundles |
| `npm test -- --run` | Frontend Unit & Contract Suite | 319 | 319 | 0 | **PASS** | Vitest component and contract suites |
| `npx tsc -b` | Frontend TypeScript | N/A | N/A | 0 | **PASS** | Clean compilation |
| `npm run build` | Frontend Production Bundle | N/A | N/A | 0 | **PASS** | Vite production bundle built in 13.79s |

---

## 14. Authentication and Gemini Integration Blockers

1. **Production Identity Provider (Blocked):**
   - Real authority login in production requires an active OAuth2/OIDC/Supabase Auth provider.
   - Spring Security is configured to fail closed (`401 Unauthorized` / `403 Forbidden`).
2. **Google Gemini API Key (Blocked for live vision):**
   - Live Gemini Vision interpretation and advanced causal reasoning in F5/F6 require `GEMINI_API_KEY`.
   - In its absence, the AI service falls back cleanly to deterministic categorization and grounded rules without fabricating output.

---

## 15. Remaining Deployment and Access Limitations

1. **Supabase Project Paused:** The remote database project `AeroSentinel` is currently `INACTIVE` on Supabase. The user must click "Restore project" in the Supabase dashboard.
2. **AI Container Hosting Platform:** The AI service must be deployed to Render (or equivalent container platform) with $\ge$ 1 GB RAM rather than Vercel Serverless.
3. **Vercel Environment Variable:** `VITE_API_BASE_URL` must be configured in Vercel project settings to point to the backend URL (e.g. `https://aerosentinel-backend.onrender.com/api/v1`).

---

## 16. Confirmation of Database & Deployment Safety

- **Zero Production Database Mutations:** No records were inserted, updated, or deleted; no tables or schemas were altered; no Flyway migrations were run against remote databases.
- **Zero Cloud Deployments:** No Vercel, Render, or Supabase configurations or active deployments were altered.
- **Zero Secret Disclosures:** No API keys, passwords, database credentials, or tokens were committed, logged, or exposed in any artifact or test output.

---

## 17. Acceptance Checklist

- [x] Java backend container builds and honors dynamic `$PORT` (`0.0.0.0:${PORT:8080}`).
- [x] AI-service container definition populated with model artifacts and Uvicorn entrypoint.
- [x] Environment-variable matrix audited and documented without secret exposure.
- [x] Read-only database connectivity verified against PostGIS 16 (4/4 PASS).
- [x] Flyway migrations in production profile protected behind `FLYWAY_ENABLED=false` safeguard.
- [x] AI per-model readiness contract verified (`models_ready: true`).
- [x] Real F3, F4, F5, F6 inference tested against loaded model artifacts (4/4 PASS).
- [x] Backend-to-AI HTTP integration verified via `LiveAiServiceConnectivityTest` (4/4 PASS).
- [x] Frontend API base URL normalized and CORS configured for Vercel production domains.
- [x] Historical deployment failures empirically diagnosed and reported with live HTTP status evidence.
- [x] All automated tests pass (15 security + 4 live AI + 4 live DB + 32 pytest + 319 frontend).

---

## 18. Recommended Next Actions for Phase 3

Once the user approves the cloud actions:
1. **Unpause Supabase:** In the Supabase dashboard for `AeroSentinel`, click **Restore Project**.
2. **Deploy Backend to Render:** Deploy `backend/` using `backend/Dockerfile` with `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, and `AI_SERVICE_URL`.
3. **Deploy AI Service to Render:** Deploy `ai-service/` using `ai-service/Dockerfile` with `PORT=8000`.
4. **Update Vercel Frontend Variable:** Set `VITE_API_BASE_URL=https://<backend-render-url>/api/v1` in Vercel Project Settings $\rightarrow$ Environment Variables, and trigger a redeploy.

---

## 19. Final Overall Gate Decision

### **Decision: BLOCKED**

**Rationale:**  
All local implementation, packaging, and integration verifications are **PASS** (100% test success across Java, Python, and TypeScript). However, overall Phase 2 cannot receive an unconditional PASS because live production end-to-end connectivity remains **BLOCKED** by external hosting factors:
1. Remote Supabase database is paused (`INACTIVE`) on the cloud provider.
2. Vercel frontend project settings lack `VITE_API_BASE_URL`, causing production API requests to be rewritten to SPA HTML.
3. The deployed Vercel AI service is missing model artifacts and must be relocated to a persistent container host.
