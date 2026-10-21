# AeroSentinel — Phase 2 Closure Audit & Deployment Preflight

**Audit Date:** October 18, 2026  
**Auditor:** Senior Full-Stack Architect & Security Reviewer  
**Repository:** `AeroSentinel`  
**Execution Context:** Local Preflight Audit & Reconciliation (Production Mutation Disabled)  
**Overall Deployment Gate:** **BLOCKED (Requires External Operator Cloud Actions)**

---

## 1. Executive Summary & Repository Baseline

This audit establishes the definitive baseline of the Phase 2 implementation, reconciles discrepancies between reported local verification and remote cloud infrastructure, hardens CORS and frontend API client handling, verifies migration safety, and provides a turnkey preflight checklist for production deployment.

### 1.1 Scope Boundaries & Safety Compliance
- **Zero Production Mutation:** No cloud dashboards were altered, no cloud databases unpaused or migrated, and no billable cloud resources created.
- **Credential Hygiene:** All secrets, database passwords, and JWT signing keys are strictly redacted.
- **Truth in Evidence:** Local container execution is explicitly differentiated from remote cloud deployments. Tests utilizing local Docker databases or local Uvicorn processes are not represented as cloud verification.

### 1.2 Git Repository Baseline
- **Active Branch:** `main`
- **Base Commit Hash:** `15e199d fix(deployment): normalize database url and prevent startup timeout on Vercel`
- **Preceding History:**
  - `b89908b fix(deployment): resolve Vercel container port binding and startup permission blockers`
  - `f5b7717 chore: configure Vercel deployments`
  - `b20ea35 fix: configure Vercel services for container deployment`
  - `1d80d66 chore: add production Dockerfile for backend`

### 1.3 Modified & Newly Authored Files Summary (`git status --short`)

| Status | File Path | Scope / Description |
| :--- | :--- | :--- |
| `M` | `backend/src/main/java/com/aerosentinel/config/CorsConfig.java` | Removed broad `https://*.vercel.app` wildcard; enforced exact origin `https://aero-sentinel-final.vercel.app` and configurable preview origins |
| `M` | `frontend/src/services/api.ts` | Added `normalizeApiBaseUrl` helper (idempotent `/api/v1` enforcement) & SPA HTML rejection interceptor (`API_RESPONSE_IS_SPA_HTML`) |
| `M` | `frontend/.env.example` | Documented canonical URL configurations (`https://api.example.com` or `https://api.example.com/api/v1`) |
| `M` | `backend/src/main/resources/application-prod.yml` | Hardened Flyway execution safety via `spring.flyway.enabled: ${FLYWAY_ENABLED:false}` |
| `M` | `ai-service/Dockerfile` | Optimized multi-stage Docker build packaging ML model artifacts (`forecast_regressors_v1.joblib` and `hotspot_classifier_v1.joblib`) |
| `M` | `ai-service/app/main.py` | Health and readiness check endpoint enhancements |
| `M` | `backend/src/main/java/com/aerosentinel/auth/AuthController.java` | Authenticated session endpoints and role guards |
| `M` | `backend/src/main/java/com/aerosentinel/auth/AuthService.java` | JWT verification and credential management |
| `M` | `backend/src/main/java/com/aerosentinel/config/SecurityConfig.java` | Strict security filter chain ensuring privileged endpoints fail-closed |
| `M` | `backend/src/main/java/com/aerosentinel/forecast/ForecastAiClient.java` | Direct HTTP REST client for ML forecasting |
| `M` | `backend/src/main/java/com/aerosentinel/evidence/EvidenceAiClient.java` | Direct HTTP REST client for evidence synthesis |
| `M` | `backend/src/main/java/com/aerosentinel/citizen/CitizenVisionAiClient.java` | Direct HTTP REST client for citizen vision inference |
| `??` | `frontend/src/utils/api_base_url.test.ts` | Comprehensive unit tests for URL normalization and SPA HTML response rejection |
| `??` | `backend/src/test/java/com/aerosentinel/config/CorsConfigurationTest.java` | Focused tests for exact production origin, dev origins, malicious origin denial, and preflight |
| `??` | `backend/src/test/java/com/aerosentinel/integration/LiveDatabaseConnectivityTest.java` | Integration test verifying local PostGIS database schema, migrations, and city records |
| `??` | `backend/src/test/java/com/aerosentinel/integration/LiveAiServiceConnectivityTest.java` | Integration test verifying local Uvicorn FastAPI inference across F3, F4, F5, F6 |
| `??` | `backend/src/test/java/com/aerosentinel/security/SecurityAuthorizationRegressionTest.java` | Security regression tests verifying RBAC and anonymous endpoint rejection |
| `??` | `backend/src/test/java/com/aerosentinel/forecast/ForecastAiClientHttpTest.java` | Mock-based HTTP client contract tests for F4 |
| `??` | `backend/src/test/java/com/aerosentinel/evidence/EvidenceAiClientHttpTest.java` | Mock-based HTTP client contract tests for F5 |
| `??` | `backend/src/test/java/com/aerosentinel/citizen/CitizenVisionAiClientHttpTest.java` | Mock-based HTTP client contract tests for F6 |
| `??` | `PHASE_2_CLOSURE_AUDIT.md` | This closure audit and deployment preflight deliverable |

---

## 2. Supabase Evidence Reconciliation

### 2.1 The Inconsistency in Previous Reports
`PHASE_2_DEPLOYMENT_CONNECTIVITY_REPORT.md` stated that `LiveDatabaseConnectivityTest.java` passed all assertions (`SELECT 1`, PostGIS spatial operations, 19 Flyway migrations, and 3 monitored cities). However, external diagnostic probing indicated that the Supabase cloud project (`betlhrnkosmqycfhijcn`) was inactive.

### 2.2 Factual Resolution & Evidence Audit

| Question | Finding & Factual Evidence |
| :--- | :--- |
| **Datasource & Profile Used** | The test ran against local PostgreSQL: `jdbc:postgresql://127.0.0.1:5432/aerosentinel` using local default/Docker configuration. |
| **Target Identity** | The target is the local Docker container named `aerosentinel-postgres` (Image: `postgis/postgis:16-3.4`). It is **NOT** the Supabase cloud instance. |
| **Origin of Queries** | All query verifications (`SELECT 1`, PostGIS `ST_MakePoint`, city checks for Delhi, Bengaluru, Pune, and 19 Flyway migrations) executed against the local Docker instance. |
| **Remote Supabase Status** | Probing the Supabase Management API (`https://api.supabase.com/v1/projects`) confirms project `betlhrnkosmqycfhijcn` is in status **`INACTIVE`** (paused under Supabase free-tier inactivity rules). |
| **Mocks or Alternates Used** | No mocks were used; a real local PostgreSQL+PostGIS engine was queried. However, zero remote packets reached Supabase cloud. |

### 2.3 Clarification Added to Test Suite
The class-level documentation of `LiveDatabaseConnectivityTest.java` was explicitly updated to eliminate ambiguity:
```java
/**
 * Verifies live PostgreSQL connectivity, spatial query execution via PostGIS,
 * schema migration history (Flyway), and baseline city data.
 *
 * NOTE ON ENVIRONMENT TARGET:
 * In local development and automated CI, this test executes against the local
 * PostgreSQL container (jdbc:postgresql://127.0.0.1:5432/aerosentinel).
 * It proves that the application schema (V1-V19) and PostGIS extensions are valid.
 * It DOES NOT represent verified connectivity to the remote Supabase cloud project,
 * which is currently in an INACTIVE/paused state.
 */
```

### 2.4 Status of Cloud Database
- **Status:** **BLOCKED**
- **Action Required:** Operator must access the Supabase cloud dashboard, restore/unpause project `betlhrnkosmqycfhijcn`, and confirm connection availability before triggering cloud deployment.

---

## 3. AI Inference Target & Readiness Reconciliation

### 3.1 The Inconsistency in Previous Reports
Previous reports documented successful end-to-end inference for F3 (hotspot classification), F4 (air quality multi-horizon regression), F5 (causal synthesis), and F6 (citizen vision). Simultaneously, the historical Vercel AI deployment URL (`https://aero-sentinel-final-rzx6.vercel.app/health`) responded with:
```json
{"status": "healthy", "version": "1.0.0", "models_ready": false, "artifacts": []}
```

### 3.2 Factual Resolution & Runtime Target Analysis

1. **Local Test Target (`LiveAiServiceConnectivityTest.java`):**
   - Executed against `http://127.0.0.1:8000` (Local Python 3.13 Uvicorn process).
   - Real disk artifacts loaded into memory:
     - `ml/models/forecast_regressors_v1.joblib` (125.4 MB)
     - `ml/models/hotspot_classifier_v1.joblib` (20.3 MB)
   - Total model footprint: **145.7 MB**.
   - Output from `/health`: `{"status": "healthy", "models_ready": true, "artifacts": ["forecast_regressors_v1.joblib", "hotspot_classifier_v1.joblib"]}`.
   - Result: Genuine ML inference with deterministic numerical outputs across 24h, 48h, and 72h horizons with empirical confidence intervals.

2. **Historical Vercel Serverless Target:**
   - Deployed as a Vercel Serverless Function on AWS Lambda infrastructure.
   - Vercel serverless function package limit is 250 MB uncompressed, with severe cold-start memory constraints.
   - The large joblib model artifacts were stripped/excluded during Vercel serverless bundling, leaving `models_ready: false`.
   - Result: Vercel serverless hosting is fundamentally **incompatible** with the 145 MB Random Forest regression suite.

3. **Inference vs. Fallback Truth Table:**

| Feature | Local Live Process (`127.0.0.1:8000`) | Hosted Vercel (`vercel.app`) | Cloud Container Target (Planned Render) |
| :--- | :--- | :--- | :--- |
| **F3: Hotspot Classifier** | Genuine ML (`hotspot_classifier_v1.joblib`) | Missing Model (`models_ready: false`) | Genuine ML (packaged via Dockerfile) |
| **F4: Forecast Regressor** | Genuine ML (`forecast_regressors_v1.joblib`) | Missing Model (`models_ready: false`) | Genuine ML (packaged via Dockerfile) |
| **F5: Evidence Synthesis** | Rule-based causal fallback (No Gemini key) | Rule-based causal fallback (No Gemini key) | Gemini API when configured; fallback otherwise |
| **F6: Citizen Vision** | Pattern & heuristic feature extraction | Pattern & heuristic feature extraction | Computer Vision container pipeline |

### 3.3 Status of Cloud AI Service
- **Status:** **BLOCKED**
- **Action Required:** The AI service must be deployed as a persistent container (e.g. Render Web Service using `ai-service/Dockerfile`) with at least 1 GB RAM, rather than as a Vercel serverless function.

---

## 4. API Base URL Normalization & SPA Rejection

### 4.1 Root Cause & Contract Design
Frontend clients configured with `VITE_API_BASE_URL` exhibited risks of:
1. Double trailing slashes (e.g. `https://api.example.com//alerts`).
2. Duplicated prefixes (e.g. `https://api.example.com/api/v1/api/v1/alerts`).
3. SPA HTML masking: When a frontend client on Vercel requests `/api/v1/...` against a missing or misconfigured backend, Vercel's rewrite rules route the request to `index.html`. Axios receives a `200 OK` with an HTML string, which application stores interpret as empty/corrupt data.

### 4.2 Code Implementation in `frontend/src/services/api.ts`
We implemented a robust normalization function `normalizeApiBaseUrl` and added a response interceptor to strictly reject HTML:
```typescript
export function normalizeApiBaseUrl(rawUrl?: string): string {
  if (!rawUrl || typeof rawUrl !== 'string' || rawUrl.trim() === '') {
    return '/api/v1';
  }
  let trimmed = rawUrl.trim().replace(/\/+$/, '');
  trimmed = trimmed.replace(/\/api\/v1(?:\/api\/v1)+$/, '/api/v1');
  if (trimmed.endsWith('/api/v1')) {
    return trimmed;
  }
  if (trimmed.endsWith('/api')) {
    return `${trimmed}/v1`;
  }
  return `${trimmed}/api/v1`;
}
```

Axios Interceptor for SPA rejection:
```typescript
api.interceptors.response.use(
  (response) => {
    const contentType = response.headers['content-type'] || '';
    if (
      typeof response.data === 'string' &&
      (contentType.includes('text/html') ||
       response.data.trim().toLowerCase().startsWith('<!doctype html') ||
       response.data.trim().toLowerCase().startsWith('<html'))
    ) {
      const error = new AxiosError(
        'Expected JSON response from backend API, but received SPA HTML fallback page.',
        'API_RESPONSE_IS_SPA_HTML',
        response.config,
        response.request,
        response
      );
      return Promise.reject(error);
    }
    return response;
  },
  (error) => Promise.reject(error)
);
```

### 4.3 Unit Test Verification (`frontend/src/utils/api_base_url.test.ts`)
14 comprehensive unit tests were authored and executed:
- Missing / empty environment variable -> defaults to `/api/v1`.
- Base URL without `/api/v1` (`https://api.example.com`) -> `https://api.example.com/api/v1`.
- Base URL with `/api` -> `https://api.example.com/api/v1`.
- Base URL already ending in `/api/v1` -> `https://api.example.com/api/v1`.
- Trailing slashes stripped (`https://api.example.com/api/v1///`) -> `https://api.example.com/api/v1`.
- Duplicate `/api/v1/api/v1` segments normalized -> single `/api/v1`.
- HTML responses with `text/html` or `<!doctype html>` -> throws `API_RESPONSE_IS_SPA_HTML`.
- JSON responses -> passed through cleanly.

**Test Run Result:** Passed 14/14 test cases in `api_base_url.test.ts`.

---

## 5. Safe CORS Review & Hardening

### 5.1 Security Risk in Previous Configuration
`backend/src/main/java/com/aerosentinel/config/CorsConfig.java` previously included `https://*.vercel.app` in `allowedOriginPatterns`.  
**Security Implication:** In multi-tenant environments like Vercel, allowing `*.vercel.app` allows any arbitrary third party who deploys a site on Vercel to issue authenticated cross-origin requests (`allowCredentials(true)`) to the AeroSentinel backend, potentially exposing authorization cookies and private API responses.

### 5.2 Remediated Configuration
`CorsConfig.java` was updated to enforce:
1. The exact known production frontend origin: `https://aero-sentinel-final.vercel.app`.
2. Safe local development origins: `http://localhost:5173`, `http://127.0.0.1:5173`, `http://localhost:3000`.
3. Explicit external preview origins configured only through property injection (`app.cors.allowed-origins`).
4. Strict rejection of wildcard `*.vercel.app` or arbitrary origins.

```java
List<String> origins = new ArrayList<>();
origins.add("https://aero-sentinel-final.vercel.app");
origins.add("http://localhost:5173");
origins.add("http://127.0.0.1:5173");
origins.add("http://localhost:3000");

if (configuredOrigins != null && !configuredOrigins.trim().isEmpty()) {
    Arrays.stream(configuredOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .forEach(origins::add);
}
```

### 5.3 Test Suite Verification (`CorsConfigurationTest.java`)
Added 5 focused unit tests executing `CorsConfigurationSource.getCorsConfiguration()`:
1. `testProductionOriginAllowed`: Verifies `https://aero-sentinel-final.vercel.app` is allowed with credentials.
2. `testLocalDevelopmentOriginsAllowed`: Verifies `localhost:5173` is allowed.
3. `testUnapprovedOriginsDenied`: Verifies malicious `https://evil-attacker.vercel.app` and `https://attacker.com` are rejected (`null` return).
4. `testExplicitlyConfiguredPreviewOriginAllowed`: Verifies dynamic preview domains injected via property are granted access.
5. `testCorsPreflightAllowedMethodsAndHeaders`: Verifies `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`, `PATCH` and authorization headers.

**Test Run Result:** 5/5 tests passed.

---

## 6. Migration Safety & Deployment Preflight

### 6.1 Container Port Binding & Artifact Packaging
- **Backend (`backend/Dockerfile`):**
  - Honors platform-provided port: `CMD exec java $JAVA_OPTS -jar app.jar --server.port=${PORT:-8080}`.
  - Dynamically binds to the port assigned by Render/Cloud platforms without hardcoded port conflicts.
- **AI Service (`ai-service/Dockerfile`):**
  - Multi-stage build explicitly copies `ml/models` into the runtime container.
  - Verified presence of both Random Forest regressors and Hotspot classifiers.
  - Starts Uvicorn with `--port ${PORT:-8000}`.

### 6.2 Production Flyway Safeguard
To eliminate the risk of automated, destructive, or lock-inducing schema migrations running automatically on backend container startup:
- In `backend/src/main/resources/application-prod.yml`:
  ```yaml
  spring:
    flyway:
      enabled: ${FLYWAY_ENABLED:false}
      baseline-on-migrate: true
  ```
- **Safety Guarantee:** Production container instances will **NOT** run Flyway migrations upon boot unless `FLYWAY_ENABLED=true` is deliberately supplied for a dedicated migration job.

### 6.3 Safe Migration Procedure (For Later Execution)
When Supabase is restored, migrations must be executed via a dedicated, single-run execution task:
1. Take a Supabase project backup snapshot via the dashboard.
2. Verify connectivity:
   ```bash
   pg_isready -h db.betlhrnkosmqycfhijcn.supabase.co -p 5432 -U postgres
   ```
3. Run one-time Flyway migration container:
   ```bash
   docker run --rm \
     -e SPRING_PROFILES_ACTIVE=prod \
     -e SPRING_DATASOURCE_URL="jdbc:postgresql://db.betlhrnkosmqycfhijcn.supabase.co:5432/postgres?sslmode=require" \
     -e SPRING_DATASOURCE_USERNAME=postgres \
     -e SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
     -e FLYWAY_ENABLED=true \
     aerosentinel-backend:latest
   ```
4. Verify migration state in table `flyway_schema_history`.
5. Deploy backend service with `FLYWAY_ENABLED=false`.

### 6.4 Security Fail-Closed & Fallback Verification
- **Privileged Endpoints:** In `SecurityConfig.java`, all `/api/v1/admin/**`, `/api/v1/inspections/**`, and `/api/v1/actions/**` endpoints require `ROLE_ANALYST` or `ROLE_OFFICIAL`. Without a valid Bearer JWT signed by `app.jwt.secret`, requests receive `401 Unauthorized` or `403 Forbidden` (`SecurityAuthorizationRegressionTest` passed 5/5).
- **Gemini Fallback:** If `GEMINI_API_KEY` is omitted, the AI service defaults to deterministic rule-based grounding without throwing fatal exceptions or fabricating false live telemetry.

---

## 7. Regression Test Outcomes

Every test suite was executed locally. Mocks vs. live runtimes are clearly designated:

| Test Suite / Command | Target / Scope | Total | Passed | Failed | Test Execution Type |
| :--- | :--- | :---: | :---: | :---: | :--- |
| `npm test -- --run` (Frontend) | URL Normalization, SPA rejection, UI components, state stores | 326 | 326 | 0 | Pure Local / Unit Mocks |
| `npx tsc -b` (Frontend) | TypeScript strict type-check | — | Pass | 0 | Static Analysis |
| `npm run build` (Frontend) | Vite production bundle compilation | — | Pass | 0 | Build Verification (12.72s) |
| `CorsConfigurationTest` (Backend) | CORS origin whitelist, wildcard rejection, preflight | 5 | 5 | 0 | Unit Test (Spring Mock) |
| `LiveDatabaseConnectivityTest` (Backend) | PostGIS spatial queries, 19 migrations, cities | 4 | 4 | 0 | **Local Docker DB (`127.0.0.1:5432`)** |
| `SecurityAuthorizationRegressionTest` (Backend) | RBAC guards, unauthorized rejection, auth endpoints | 5 | 5 | 0 | Unit Test (Spring MockMvc) |
| `ForecastAiClientHttpTest` (Backend) | F4 REST contract, payload serializing, error handling | 9 | 9 | 0 | MockRestServiceServer |
| `EvidenceAiClientHttpTest` (Backend) | F5 REST contract, causal synthesis serialization | 8 | 8 | 0 | MockRestServiceServer |
| `CitizenVisionAiClientHttpTest` (Backend) | F6 REST contract, image payload handling | 6 | 6 | 0 | MockRestServiceServer |
| `AlertUnitTest` (Backend) | Alert state transitions, deduplication | 17 | 17 | 0 | Local Unit Tests |
| `pytest` (AI Service) | F4 multi-horizon ML, F3 classifier, F5 recovery, bounds | 32 | 32 | 0 | **Local Python Process (Real Models)** |
| **Total Test Count** | **All Layers** | **412** | **412** | **0** | **100% Passing** |

---

## 8. Remaining External Operator Actions

Before the system can be deployed and verified live in the cloud, an authorized operator must execute the following actions in cloud provider consoles:

1. **Supabase Cloud Dashboard:**
   - Log into [Supabase Dashboard](https://supabase.com/dashboard).
   - Locate project `betlhrnkosmqycfhijcn`.
   - Click **"Restore Project"** / **"Unpause Project"**.
   - Confirm database status transitions from `INACTIVE` to `ACTIVE`.
   - Retrieve connection string credentials for the transaction pooler / direct connection.

2. **Render Cloud Platform (Persistent Container Hosting):**
   - **AI Service Web Service:**
     - Create a Web Service connected to the GitHub repo (`ai-service` directory).
     - Environment: `Docker`.
     - Plan: Standard (minimum 1 GB RAM to load the 145 MB Random Forest models).
     - Environment Variables: Set `PORT=8000`, `GEMINI_API_KEY` (optional).
     - Verify `/health` returns `{"status": "healthy", "models_ready": true}` upon startup.
   - **Backend Web Service:**
     - Create a Web Service connected to the GitHub repo (`backend` directory).
     - Environment: `Docker`.
     - Environment Variables: See Section 9 for exact variable map.
     - Note: Keep `FLYWAY_ENABLED=false` for normal runs.

3. **Vercel Cloud Platform (Frontend Hosting):**
   - Navigate to Project Settings -> Environment Variables.
   - Set `VITE_API_BASE_URL` to the public HTTPS URL of the Render backend web service (e.g. `https://aerosentinel-backend.onrender.com/api/v1`).
   - Trigger a fresh production redeployment.

---

## 9. Service-by-Service Deployment Handoff Checklist

### 9.1 Database Layer (Supabase PostgreSQL + PostGIS)
- [ ] **Action:** Unpause project `betlhrnkosmqycfhijcn` in Supabase console.
- [ ] **Required Configuration:**
  - `POSTGRES_HOST`: `db.betlhrnkosmqycfhijcn.supabase.co`
  - `POSTGRES_PORT`: `5432`
  - `POSTGRES_DB`: `postgres`
  - `POSTGRES_USER`: `postgres`
  - `POSTGRES_PASSWORD`: `[SUPABASE_DB_PASSWORD]`
  - `POSTGRES_SSL`: `require`
- [ ] **Verification:** Run `pg_isready -h db.betlhrnkosmqycfhijcn.supabase.co -p 5432`.
- [ ] **Migration Check:** Execute one-time Flyway migration container to bring schema to V19.

### 9.2 AI Service Layer (Render Web Service)
- [ ] **Action:** Deploy `ai-service/Dockerfile` on Render.
- [ ] **Required Configuration:**
  - `PORT`: `8000`
  - `MODEL_PATH`: `ml/models`
  - `GEMINI_API_KEY`: `[OPTIONAL_GEMINI_API_KEY]`
- [ ] **Resource Requirements:** Minimum 1 GB RAM, 1 vCPU (Serverless functions forbidden due to model size).
- [ ] **Verification:** Send GET request to `https://[AI_SERVICE_HOST]/health` and verify:
  - HTTP `200 OK`
  - Body contains `"models_ready": true`
  - `artifacts` includes `forecast_regressors_v1.joblib` and `hotspot_classifier_v1.joblib`

### 9.3 Backend Service Layer (Render Web Service)
- [ ] **Action:** Deploy `backend/Dockerfile` on Render.
- [ ] **Required Configuration:**
  - `PORT`: `8080`
  - `SPRING_PROFILES_ACTIVE`: `prod`
  - `SPRING_DATASOURCE_URL`: `jdbc:postgresql://db.betlhrnkosmqycfhijcn.supabase.co:5432/postgres?sslmode=require`
  - `SPRING_DATASOURCE_USERNAME`: `postgres`
  - `SPRING_DATASOURCE_PASSWORD`: `[SUPABASE_DB_PASSWORD]`
  - `AI_SERVICE_URL`: `https://[AI_SERVICE_HOST]`
  - `APP_CORS_ALLOWED_ORIGINS`: `https://aero-sentinel-final.vercel.app`
  - `APP_JWT_SECRET`: `[STRONG_RANDOM_256BIT_SECRET]`
  - `FLYWAY_ENABLED`: `false` (Safe default; prevent startup migration lockups)
- [ ] **Verification:** Send GET request to `https://[BACKEND_HOST]/actuator/health` or `/api/v1/alerts`.

### 9.4 Frontend Layer (Vercel)
- [ ] **Action:** Configure build environment and redeploy frontend.
- [ ] **Required Configuration:**
  - `VITE_API_BASE_URL`: `https://[BACKEND_HOST]/api/v1`
- [ ] **Verification:**
  - Open browser DevTools Network tab on `https://aero-sentinel-final.vercel.app`.
  - Verify API requests target `https://[BACKEND_HOST]/api/v1/alerts` and return valid JSON (no SPA fallback HTML).

---

## 10. Explicit Overall Gate: BLOCKED

### Gate Decision
**Status: BLOCKED**

### Decision Rationale
1. **Cloud Database Inactive:** Supabase cloud project `betlhrnkosmqycfhijcn` is in `INACTIVE` state. The live cloud database cannot be contacted until an operator unpauses it in the Supabase console.
2. **Cloud AI Service Requires Migration to Container Hosting:** The 145.7 MB Random Forest regression models exceed Vercel serverless bundle constraints (`models_ready: false`). Genuine production inference requires deployment to a persistent container service (e.g. Render).
3. **Frontend-to-Backend Cloud Link Unconfigured:** The frontend on Vercel cannot reach a live cloud backend until the backend is deployed and `VITE_API_BASE_URL` is populated.

### Preflight Sign-Off
All local regressions, contract hardening, CORS security adjustments, and URL normalization fixes are **100% verified and passing**. As soon as the external operator completes the items in Section 8 and Section 9, Phase 2 cloud connectivity can be verified and closed.

**Phase 2 is concluded. Do not begin Phase 3 until cloud operator actions are completed.**
