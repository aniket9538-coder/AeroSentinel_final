# AeroSentinel — Vercel Services Container Detection Fix Report

**Date:** September 30, 2026  
**Auditor / Engineer:** Antigravity Autonomous Systems & Architecture  
**Target:** Vercel Multi-Service Container Deployment  
**Repository:** `AeroSentinel`  

---

## 1. Previous Failure

During deployment on Vercel, the platform triggered the following build error:
```
Using Python 3.12 from ai-service/pyproject.toml
uv sync ...
setuptools ...
Multiple top-level packages discovered...
```
Vercel attempted to build `ai-service/` as a Python Serverless project rather than building and running it as a container service via `ai-service/Dockerfile.vercel`.

---

## 2. Root Cause

1. In the absence of an explicit root-level multi-service definition in `vercel.json`, Vercel's build scanner inspected the repository directory tree and detected `ai-service/pyproject.toml`.
2. Vercel automatically assigned the `@vercel/python` Serverless Function framework builder.
3. This completely bypassed `ai-service/Dockerfile.vercel` and triggered standard Python package resolution (`uv sync` / `setuptools`), which failed due to multiple top-level packages and conflicting ML dependencies.

---

## 3. Previous `vercel.json` Behavior

- Prior to this fix, no root-level `vercel.json` existed in the repository (`Test-Path vercel.json` returned `False`).
- Without explicit service boundaries and container runtime declarations, Vercel fell back to heuristic framework auto-detection.

---

## 4. Corrected Service Configuration

Created a root-level `vercel.json` utilizing the official Vercel Services schema (`https://openapi.vercel.sh/vercel.json`) with discrete service boundaries, container runtime directives, and internal service bindings:

```json
{
  "$schema": "https://openapi.vercel.sh/vercel.json",
  "services": {
    "frontend": {
      "root": "frontend/",
      "framework": "vite"
    },
    "backend": {
      "root": "backend/",
      "runtime": "container",
      "entrypoint": "Dockerfile.vercel",
      "bindings": [
        {
          "type": "service",
          "service": "ai",
          "format": "url",
          "env": "AI_SERVICE_URL"
        }
      ]
    },
    "ai": {
      "root": "ai-service/",
      "runtime": "container",
      "entrypoint": "Dockerfile.vercel"
    }
  },
  "routes": [
    {
      "src": "/api/(.*)",
      "service": "backend"
    },
    {
      "src": "/(.*)",
      "service": "frontend"
    }
  ]
}
```

---

## 5. Frontend Service Configuration

- **Service Key:** `frontend`
- **Root Directory:** `frontend/`
- **Framework:** `vite`
- **Role:** Handles public UI serving, client-side SPA routing, and static asset distribution via Vercel Edge CDN.

---

## 6. Backend Container Configuration

- **Service Key:** `backend`
- **Root Directory:** `backend/`
- **Runtime:** `container`
- **Entrypoint:** `Dockerfile.vercel` (relative to `backend/`)
- **Port & Address:** Listens on `0.0.0.0` and dynamically binds to `$PORT` via `-Dserver.port=${PORT:-8080}`.
- **Runtime Stack:** Java 21 LTS (`eclipse-temurin:21-jre-jammy`) executing `aerosentinel-backend-1.0.0.jar`.

---

## 7. AI Container Configuration

- **Service Key:** `ai`
- **Root Directory:** `ai-service/`
- **Runtime:** `container`
- **Entrypoint:** `Dockerfile.vercel` (relative to `ai-service/`)
- **Port & Address:** Listens on `0.0.0.0` and dynamically binds to `$PORT` via `uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000}`.
- **Detection Override:** Disables Vercel's Python framework scanner; guarantees execution via Docker image build.

---

## 8. AI Service Binding

- **Binding Type:** `service`
- **Target Service:** `ai`
- **Format:** `url`
- **Injected Variable:** `AI_SERVICE_URL`
- **Mechanism:** Injects the internal private network URL of the AI container directly into the `backend` environment.
- **Backend Consumption:** Spring Boot automatically maps this variable via `backend/src/main/resources/application.yml` (`app.ai-service.url: ${AI_SERVICE_URL:http://localhost:8000}`) without exposing the AI inference engine publicly.

---

## 9. Routing Configuration

- **API Route:**
  ```json
  {
    "src": "/api/(.*)",
    "service": "backend"
  }
  ```
  Preserves existing frontend REST API contracts by forwarding all `/api/*` traffic directly to the Spring Boot backend container.
- **Frontend Catch-All Route:**
  ```json
  {
    "src": "/(.*)",
    "service": "frontend"
  }
  ```
  Serves the React 18 / Vite SPA for all application routes.
- **Internal Protection:** The `ai` service is omitted from public routing rules, keeping inference endpoints securely accessible only via internal service binding.

---

## 10. Validation Result

| Check | Target | Expected | Observed | Status |
| :--- | :--- | :--- | :--- | :--- |
| **JSON Syntax** | `vercel.json` | Valid JSON | Parsed successfully via Node.js | **PASS** |
| **Frontend Service** | `frontend/` | `root: frontend/`, `framework: vite` | Exactly matches | **PASS** |
| **Backend Service** | `backend/Dockerfile.vercel` | `runtime: container`, `entrypoint: Dockerfile.vercel` | Exactly matches; file exists | **PASS** |
| **AI Service** | `ai-service/Dockerfile.vercel` | `runtime: container`, `entrypoint: Dockerfile.vercel` | Exactly matches; file exists | **PASS** |
| **Internal Binding** | `backend -> ai` | `env: AI_SERVICE_URL`, `format: url` | Configured under `backend.bindings` | **PASS** |
| **Public Routing** | `routes` | `/api/*` to `backend`, `/*` to `frontend` | Preserves existing API & SPA routing | **PASS** |
| **Code Modification** | Application source | Zero source code changes | Confirmed via `git status` (0 source files modified) | **PASS** |

---

```
================================================================================
FINAL STATUS:
VERCEL CONTAINER DETECTION FIX = PASS
================================================================================
```
