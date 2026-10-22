# AeroSentinel — Vercel-Only Deployment Feasibility Audit Report

**Date:** September 30, 2026  
**Auditor:** Antigravity Autonomous Systems & Architecture  
**Target Repository:** `AeroSentinel`  
**Classification:** Architectural & Deployment Feasibility Assessment  

---

## Executive Summary

This audit assesses the feasibility of deploying the full-stack **AeroSentinel** platform under a **Vercel-only** deployment topology (combining Vercel and hosted Supabase PostgreSQL/PostGIS).

AeroSentinel is a multi-tier environmental intelligence system comprising:
1. **React 18 + TypeScript + Vite SPA** (`frontend/`)
2. **Java 21 Spring Boot 3.3 Orchestrator** with Hibernate Spatial and Flyway (`backend/`)
3. **Python 3.11+ FastAPI ML Inference Service** with scikit-learn and H3 (`ai-service/`)
4. **PostgreSQL 16 + PostGIS 3.4 Spatial Database** (`database/`)
5. **Multi-city Federated Learning Prototype** with iterative coordinator and municipal client nodes (`federated/`)

Based on strict technical evaluation against Vercel's architectural limitations (serverless execution model, absence of persistent container hosting, lack of Java/JVM runtime support, 250MB deployment size caps, and stateless lifecycles), a **Vercel-Only deployment of the full AeroSentinel platform is NOT FEASIBLE**.

---

## 1. Component-by-Component Feasibility Matrix

| Component | Target Runtime / Platform | Feasibility | Key Architectural Reason / Blocker |
| :--- | :--- | :--- | :--- |
| **Frontend** | Vercel Frontend Service (Static / SPA) | **FEASIBLE** | React 18 / Vite compiles to standard static assets (`dist/`), deployable on Vercel Edge CDN with rewrite rules. |
| **Backend** | Vercel Container Service (Spring Boot) | **NOT FEASIBLE** | Vercel **does not support container hosting** or persistent daemons; Vercel Serverless Functions have **no Java/JVM runtime**, cannot support Spring Boot `@EnableScheduling`, and cannot run local `ProcessBuilder` subprocesses. |
| **AI Service** | Vercel Service / Container (FastAPI) | **NOT FEASIBLE** | Vercel has no container service. Running FastAPI as `@vercel/python` Serverless Function fails due to 250MB bundle limits (numpy + scipy + scikit-learn + pandas + geopandas + joblib exceeds quota), 10–15s cold starts, and inability to execute local CLI scripts invoked by Spring Boot. |
| **Database** | Supabase PostgreSQL 16 + PostGIS 3.4 | **FEASIBLE** | Supabase natively provides managed PostgreSQL 15/16 with PostGIS and `uuid-ossp`. Works directly with Flyway and Hibernate Spatial over direct session connection (port 5432) with SSL. |
| **F9 Federated** | Vercel Runtime Model | **NOT FEASIBLE** | Requires long-running training loops, in-memory stateful round coordination (`RoundManager`), local model artifact disk persistence (`storage/models/global`), and direct socket/TCP database connectivity. Serverless execution destroys coordinator state and times out. |

---

## 2. In-Depth Architectural Evaluation

### 2.1 Vercel Frontend Service
- **Status:** **FEASIBLE (100% Native Compatibility)**
- **Technical Analysis:**
  - `frontend/package.json` defines a standard client-side React 18 + Vite application.
  - The build script (`npm run build` -> `tsc -b && vite build`) generates static bundles in `frontend/dist/`.
  - Client-side routing with `react-router-dom` (e.g., `/dashboard`, `/analyst/evidence`, `/authority/incidents`) requires a standard SPA fallback rule routing all traffic to `/index.html`.
  - The API client (`frontend/src/services/api.ts`) already reads `import.meta.env.VITE_API_BASE_URL` dynamically, allowing easy configuration to point to a production backend.

### 2.2 Vercel Container Service for Spring Boot
- **Status:** **NOT FEASIBLE**
- **Blockers:**
  1. **Absence of Container Hosting on Vercel:** Vercel is a Jamstack and serverless function provider. It **does not have a container hosting product** (unlike AWS ECS, Google Cloud Run, Railway, Render, or Fly.io). It cannot deploy or run Docker containers.
  2. **No Java / JVM Runtime in Vercel Functions:** Vercel's Serverless Function engine only supports Node.js, Python, Go, and Ruby. Java 21 is completely unsupported.
  3. **Continuous Background Tasks & Schedulers:** AeroSentinel's backend relies on `@EnableScheduling` (`AeroSentinelApplication.java`) and background threads (e.g., `WeatherIngestionScheduler` fetching Open-Meteo feeds every 15 minutes). Serverless execution freezes execution immediately upon request completion.
  4. **Subprocess Invocations (`ProcessBuilder`):** The backend orchestrator directly spawns local Python processes (`ForecastAiClient`, `EvidenceAiClient`, `CitizenVisionAiClient`) via `ProcessBuilder("python", scriptPath)`. Serverless platforms isolate runtimes and do not allow arbitrary multi-language cross-process execution on a shared filesystem.
  5. **Heavy Initialization & Memory:** AeroSentinel compiles 298 Java source files, boots 31 JPA repositories, and executes 19 Flyway migrations. Cold starts take 15–25 seconds and require 512MB–1GB RAM, far exceeding serverless function constraints.

### 2.3 Vercel Service / Container for FastAPI AI Service
- **Status:** **NOT FEASIBLE for Production ML Stack**
- **Blockers:**
  1. **No Docker Daemon / Long-Running Uvicorn:** Cannot run `uvicorn app.main:app --port 8000` as a continuous web server on Vercel.
  2. **Severe Serverless Bundle Size Limit:** Vercel limits unzipped serverless function bundles to 250 MB. The compiled wheels for `numpy`, `scipy`, `pandas`, `scikit-learn`, `geopandas`, `shapely`, `pyarrow`, `h3`, and Google GenAI SDK exceed 450 MB.
  3. **Artifact Unpickling & Cold Starts:** During application lifespan (`lifespan(app)` in `ai-service/app/main.py`), the service unpickles `hotspot_classifier_v1.joblib` and `forecast_regressors_v1.joblib` into memory. Serverless cold starts under this memory footprint induce heavy latency (5–12s) and exceed HTTP gateway thresholds.
  4. **Tight Subprocess Coupling:** Spring Boot expects the Python AI scripts (`predict_forecast_cli.py`, `orchestrate_evidence_cli.py`, `vision_cli.py`) to reside on local disk. Hosting FastAPI independently as a serverless function leaves Spring Boot's CLI execution broken unless the Java backend is refactored to make REST calls.

### 2.4 Supabase PostgreSQL + PostGIS Integration
- **Status:** **FEASIBLE (Configuration Only)**
- **Technical Analysis:**
  - Supabase provides full-featured PostgreSQL 15/16 instances with the `postgis` and `uuid-ossp` extensions.
  - AeroSentinel's 19 Flyway migrations (`V1__init_schema.sql` through `V19__seed_multicity_grid_cells.sql`) execute standard SQL that runs cleanly on Supabase.
  - **Prerequisites for Supabase:**
    1. **Direct Connection Mode (Port 5432):** Flyway migrations execute schema modifications and acquire advisory locks (`pg_advisory_lock`), which fail under transaction poolers (PgBouncer/Supavisor on port 6543). Direct session mode (port 5432) must be used.
    2. **SSL Enforcement:** Supabase requires SSL encryption. The JDBC URL must specify `?sslmode=require`.

### 2.5 F9 Federated Learning Runtime Model
- **Status:** **NOT FEASIBLE on Vercel**
- **Blockers:**
  1. **Stateful In-Memory Coordination:** The F9 coordinator (`FederatedCoordinator`, `RoundManager`) tracks active rounds, node registrations, update queues, and quorum status in memory (`RoundState.INITIALIZED`, `COLLECTING_UPDATES`, `AGGREGATING`, `COMPLETED`). Serverless requests are routed across arbitrary stateless lambda instances, destroying round state.
  2. **Long-Running Computation:** Federated training across municipal nodes (`PuneClient`, `MumbaiClient`, `DelhiClient`) trains ML regressors over thousands of telemetry records (`sample_count=1200`). This exceeds serverless request execution timeouts.
  3. **Local Filesystem Persistence:** The coordinator writes consensus global models (`global-v1.joblib`, `global-v2.joblib`) to `storage/models/global/` and updates to `storage/models/updates/`. Serverless functions have an ephemeral, read-only filesystem (except `/tmp`, which is purged after execution).

---

## 3. Technical Specifications & Topology Inventory

### 3.1 Exact Entrypoints

| Tier | Component | File Path | Entrypoint Identifier |
| :--- | :--- | :--- | :--- |
| **Frontend** | React / Vite SPA | `frontend/index.html` -> `frontend/src/main.tsx` | `ReactDOM.createRoot` |
| **Backend** | Spring Boot Orchestrator | `backend/src/main/java/com/aerosentinel/AeroSentinelApplication.java` | `com.aerosentinel.AeroSentinelApplication.main(String[] args)` |
| **AI Service** | FastAPI HTTP Engine | `ai-service/app/main.py` | `app = FastAPI(...)` (ASGI: `app.main:app`) |
| **AI CLI 1** | Forecast CLI Subprocess | `ai-service/ml/inference/predict_forecast_cli.py` | `main()` |
| **AI CLI 2** | Evidence CLI Subprocess | `ai-service/ml/inference/orchestrate_evidence_cli.py` | `main()` |
| **AI CLI 3** | Citizen Vision Subprocess | `ai-service/ml/inference/vision_cli.py` | `main()` |
| **F9 Sim** | Federated Round Simulation | `federated/run_round_simulation.py` | `main()` |
| **F9 E2E** | E2E Cross-Stack Orchestrator | `federated/run_e2e_federated_orchestration.py` | `run_e2e_orchestration()` |

### 3.2 Exact Ports

| Component | Protocol | Local / Container Port | Target Production / Cloud Port |
| :--- | :--- | :--- | :--- |
| **Frontend** | HTTP / HTTPS | `3000` (Vite) | `80` / `443` (Vercel Edge CDN) |
| **Backend** | HTTP / REST | `8080` (`server.port`) | `8080` (or injected `${PORT}`) |
| **AI Service** | HTTP / REST | `8000` (Uvicorn) | `8000` (Internal container network) |
| **Database** | PostgreSQL Wire | `5432` | `5432` (Supabase Direct Session Mode) |
| **F9 Coordinator** | Internal Python / HTTP | In-process + connects to `8080` | Internal VPC / Worker |

### 3.3 Exact Environment Variables

#### Frontend (`frontend/.env` / Vercel Environment Settings)
```bash
VITE_API_BASE_URL=https://api.aerosentinel.yourdomain.com/api/v1
```

#### Backend Orchestrator (`backend/src/main/resources/application.yml`)
```bash
# Server & Profile
PORT=8080
SPRING_PROFILES_ACTIVE=prod

# Supabase PostgreSQL / PostGIS
DATABASE_URL=jdbc:postgresql://db.<supabase-project-ref>.supabase.co:5432/postgres?sslmode=require
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=<SUPABASE_DB_PASSWORD>

# AI Microservice URL & Process Bridges
AI_SERVICE_URL=http://aerosentinel-ai:8000
AI_SERVICE_TIMEOUT_MS=10000
FORECAST_AI_PYTHON_COMMAND=python
FORECAST_AI_CLI_PATH=ai-service/ml/inference/predict_forecast_cli.py
FORECAST_AI_TIMEOUT_MS=30000
EVIDENCE_AI_PYTHON_COMMAND=python
EVIDENCE_AI_CLI_PATH=ai-service/ml/inference/orchestrate_evidence_cli.py
EVIDENCE_AI_TIMEOUT_MS=15000
CITIZEN_VISION_PYTHON_COMMAND=python
CITIZEN_VISION_CLI_PATH=ai-service/ml/inference/vision_cli.py
CITIZEN_VISION_TIMEOUT_MS=30000

# Security & Secrets
JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
JWT_EXPIRATION_MS=86400000
GEMINI_API_KEY=<GOOGLE_GEMINI_API_KEY>

# External Data Feeds
OPENAQ_API_KEY=b094486bd6df343cfbc2a1f9c18b6fd40834dccd175347eece1acb4be6306766
OPENAQ_BASE_URL=https://api.openaq.org/v3
OPENAQ_ENABLED=true
OPEN_METEO_BASE_URL=https://api.open-meteo.com/v1/forecast
CPCB_BASE_URL=https://api.cpcb.gov.in
CPCB_API_KEY=
FIRMS_BASE_URL=https://firms.modaps.eosdis.nasa.gov
FIRMS_API_KEY=
WEATHER_BASE_URL=https://api.openweathermap.org/data/2.5
WEATHER_API_KEY=

# Ingestion Schedulers
AEROSENTINEL_WEATHER_INGESTION_ENABLED=true
AEROSENTINEL_WEATHER_INGESTION_INTERVAL_MS=900000
AEROSENTINEL_WEATHER_BACKFILL_ENABLED=true
AEROSENTINEL_INGESTION_ENABLED=false
AEROSENTINEL_INGESTION_INTERVAL_MS=300000
AEROSENTINEL_PROVIDER=openaq
AEROSENTINEL_H3_RESOLUTION=8

# Monitoring Priority Weights
MONITORING_RISK_WEIGHT=0.45
MONITORING_UNCERTAINTY_WEIGHT=0.30
MONITORING_DISTANCE_WEIGHT=0.25
MONITORING_UNCERTAINTY_MAX_INTERVAL_WIDTH=20.0
MONITORING_DISTANCE_MAX_KM=20.0
MONITORING_MEDIUM_THRESHOLD=40
MONITORING_HIGH_THRESHOLD=70
```

#### AI Service (`ai-service/.env`)
```bash
GEMINI_API_KEY=<GOOGLE_GEMINI_API_KEY>
GEMINI_MODEL=gemini-2.5-flash
ENVIRONMENT=production
```

#### F9 Federated Orchestrator
```bash
CONTROL_PLANE_URL=https://api.aerosentinel.yourdomain.com
DB_HOST=db.<supabase-project-ref>.supabase.co
DB_PORT=5432
DB_NAME=postgres
DB_USER=postgres
DB_PASSWORD=<SUPABASE_DB_PASSWORD>
```

---

## 4. Required Deployment Configurations & Artifacts

### 4.1 Required `vercel.json` (For Frontend Deployment Only)
To deploy the frontend to Vercel, the following `vercel.json` must be configured at the frontend root:

```json
{
  "framework": "vite",
  "buildCommand": "npm run build",
  "outputDirectory": "dist",
  "rewrites": [
    {
      "source": "/(.*)",
      "destination": "/index.html"
    }
  ]
}
```

### 4.2 Required Production Dockerfiles (for Container Runtimes)
The repository currently contains empty 0-byte placeholders in `infrastructure/docker/`. To run on a container cloud platform (e.g., Render, Railway, AWS ECS, Google Cloud Run), the following production Dockerfiles are required:

#### A. Backend Dockerfile (`infrastructure/docker/backend.Dockerfile`)
```dockerfile
# Multi-stage build for Java 21 Spring Boot
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY backend/pom.xml .
COPY backend/.mvn .mvn
COPY backend/mvnw .
RUN ./mvnw dependency:go-offline -B
COPY backend/src src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# Install Python 3 for ProcessBuilder CLI scripts
RUN apt-get update && apt-get install -y python3 python3-pip curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/aerosentinel-backend-1.0.0.jar app.jar
COPY ai-service /app/ai-service
RUN pip3 install --no-cache-dir -r /app/ai-service/requirements.txt
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### B. AI Service Dockerfile (`infrastructure/docker/ai.Dockerfile`)
```dockerfile
FROM python:3.11-slim
WORKDIR /app
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
COPY ai-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY ai-service /app
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

---

## 5. Architectural Remediation & Code Changes Required

If the project were forced to transition toward a serverless or decoupled cloud environment, the following non-trivial code modifications would be mandatory:

1. **Decouple Java Subprocess Bridges to Pure REST:**
   - Modify `ForecastAiClient.java`, `EvidenceAiClient.java`, and `CitizenVisionAiClient.java` to eliminate `ProcessBuilder` execution.
   - Refactor these clients to communicate exclusively via HTTP REST endpoints against the FastAPI service.
2. **Externalize F9 Model Registry:**
   - Modify `federated/coordinator/model_registry.py` to persist model artifacts and round metadata to cloud object storage (e.g., AWS S3, Google Cloud Storage, or Supabase Storage) rather than local disk (`storage/models/global/`).
3. **Externalize Background Ingestion Schedulers:**
   - Extract `@Scheduled` tasks in Spring Boot (`WeatherIngestionScheduler`) into external serverless cron triggers (e.g., GitHub Actions Cron or AWS EventBridge calling webhooks).

---

## 6. Recommended Production Architecture (Realistic Alternative)

To maintain all system capabilities without compromise:

```text
┌────────────────────────────────────────────────────────┐
│              VERCEL (Edge CDN / Static)               │
│       React 18 + Vite SPA Frontend (Port 80/443)       │
└───────────────────────────┬────────────────────────────┘
                            │ HTTPS REST API
                            ▼
┌────────────────────────────────────────────────────────┐
│     CONTAINER HOST (AWS ECS / Cloud Run / Railway)     │
│                                                        │
│   ┌──────────────────────────────────────────────┐     │
│   │ Java 21 Spring Boot Backend (Port 8080)      │     │
│   │ Orchestrator, Auth, Ingestion Schedulers     │     │
│   └───────────────────────┬──────────────────────┘     │
│                           │ Internal HTTP              │
│                           ▼                            │
│   ┌──────────────────────────────────────────────┐     │
│   │ Python 3.11 FastAPI AI Service (Port 8000)   │     │
│   │ ML Hotspot & Forecasting Inference Engine    │     │
│   └──────────────────────────────────────────────┘     │
└──────────────┬───────────────────────────┬─────────────┘
               │ Direct SQL (Port 5432)    │ HTTPS
               ▼                           ▼
┌──────────────────────────────┐ ┌───────────────────────┐
│     SUPABASE MANAGED DB      │ │    GOOGLE GEMINI      │
│  PostgreSQL 16 + PostGIS 3.4 │ │   Multimodal API      │
└──────────────────────────────┘ └───────────────────────┘
```

---

## 7. Audit Conclusion & Final Verdict

- **Frontend:** Fully supported on Vercel.
- **Supabase DB:** Fully supported with direct SSL connection mode.
- **Spring Boot Backend:** **Incompatible with Vercel** (requires persistent JVM container).
- **FastAPI AI Engine:** **Incompatible with Vercel** (exceeds serverless bundle/cold start limits; breaks subprocess CLI bridges).
- **F9 Federated Coordinator:** **Incompatible with Vercel** (stateful, long-running, requires persistent filesystem).

```
================================================================================
FINAL VERDICT:
VERCEL-ONLY DEPLOYMENT = NOT FEASIBLE
================================================================================
```
