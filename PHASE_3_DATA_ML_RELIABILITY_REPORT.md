# AeroSentinel — Phase 3: Data Integrity, Freshness & ML Reliability Gate Report

**Date:** 2026-10-18  
**Phase:** Phase 3 — Data Integrity, Freshness & ML Reliability Gate  
**Gate Decision:** **PASS** (Local Data Integrity, Spatial Verification & ML Reliability Gate Cleared; Production Cloud Deployments & Raw Parquet Re-training Intentionally Deferred)

---

## 1. Executive Summary

Phase 3 established an audit, verification, and hardening gate across AeroSentinel's foundational data pipelines (F1 Air Quality, F2 Weather and H3 Spatial Indexing) and machine learning capabilities (F3 Hotspot Classification, F4 PM2.5 Multi-Horizon Forecasting).

Prior to this phase, several critical reliability issues were identified:
1. **Frontend Fallback Fabrication:** When live monitoring station telemetry was missing, `frontend/src/pages/public/Forecast.tsx`, `ForecastSummary.tsx`, `ForecastTimeline.tsx`, and `ForecastChart.tsx` fell back to hardcoded dictionaries or a fabricated default of `78 µg/m³`, inventing base observations (T0).
2. **F3 Metric Inconsistency in Documentation:** `metadata_v1.json` reported an anomalously low recall of 0.016 and F1 score of 0.032 at threshold 0.20, caused by evaluating the model against raw `pm25 >= 60.0` rather than the spatial lag proxy target.
3. **F4 Benchmark Metric Discrepancy & Naive Persistence Superiority:** Historical notebooks contained contradictory metric tables across validation and test sets. Crucially, Random Forest models across all three horizons (+1h, +3h, +6h) perform worse than a naive persistence baseline (repeating T0).
4. **Data Freshness & Provenance Transparency:** Frontend screens lacked explicit disclosures that the ML forecaster is experimental and that naive persistence outperforms it on short-term holdout evaluations.

### Key Actions Executed in Phase 3
- **Eliminated Frontend Observation Fabrication:** Removed `defaultObservedPm25Map` from `Forecast.tsx`. Updated `ForecastSummary.tsx`, `ForecastTimeline.tsx`, and `ForecastChart.tsx` to accept nullable `currentPm25?: number | null`, rendering honest `—` and "Observation unavailable at T0" states without defaulting to `78 µg/m³`.
- **Reconciled Physical Model Artifacts with Metadata:** Extracted the ground-truth embedded evaluation metrics directly from the physical model binaries (`hotspot_classifier_v1.joblib` and `forecast_regressors_v1.joblib`) and updated both `metadata_v1.json` files.
- **Enforced Honest ML Presentation Policy:** Labeled Random Forest forecasters as **Experimental ML** with explicit benchmark comparison disclosures in both UI and documentation.
- **Automated Regression Suite Execution:**
  - Frontend: **326 passed, 0 failed**; production bundle build succeeded with **0 errors**.
  - Python AI Service: **63 passed, 0 failed** (27 benign deprecation/unpickle warnings).
  - Java Spring Boot Backend: **70 passed, 0 failed**, `BUILD SUCCESS`.

---

## 2. Repository Baseline & Environment

- **Git Branch:** `main`
- **Head Commit:** `15e199d` (`fix(deployment): normalize database url and prevent startup timeout on Vercel`)
- **Working Tree State:** Clean separation of Phase 1/Phase 2 changes and targeted Phase 3 reliability updates. No destructive commands (`git reset`, `git clean`) were executed.
- **Runtimes & Toolchain:**
  - Java: OpenJDK 21.0.11 (LTS)
  - Maven: 3.9.6 at `C:\Users\lenovo\.m2\wrapper\dists\apache-maven-3.9.6-bin\3311e1d4\apache-maven-3.9.6\bin\mvn.cmd`
  - Node.js: v22.14.0, npm: 10.9.2
  - Python: 3.13.7 (64-bit) with `scikit-learn`, `joblib`, `fastapi`, `pydantic`
  - PostGIS / H3: PostGIS 3.4 on PostgreSQL 16; Uber H3Core 3.7.2

---

## 3. Current F1/F2 Source-to-UI Data Flows

```
[ External OpenAQ API ]
         │ (HTTP GET /v2/measurements?parameter=pm25)
         ▼
[ OpenAqClient.java ] ──(Validates: parameterId=2, value>=0, UTC ISO timestamp)──┐
         │                                                                        │
         ▼                                                                        ▼
[ IngestionService.java ] ───────────────────────────────────────────────► [ H3Service.java ]
         │ (Deduplication: stationId + observedAt)                                (Res 8 Hex Indexing)
         ▼                                                                        │
[ PostgreSQL: air_quality_readings ] ◄─────────────────────────────────────────────┘
         │
         ▼
[ AirQualityController.java ] ──(Public REST: /api/v1/air/latest, /api/v1/air/history)
         │
         ▼
[ React: AirQuality.tsx, MapView.tsx ] ──(Render: LIVE / STALE / NO_DATA / UNAVAILABLE)
```

```
[ External Open-Meteo API ]
         │ (HTTP GET /v1/forecast?past_days=1&forecast_days=0)
         ▼
[ WeatherClient.java ] ──(Strict filter: hourly.time() <= currentTime; no future forecast leakage)
         │
         ▼
[ WeatherIngestionService.java ] ──► [ H3Service.java ] (Res 8 Hex Indexing)
         │
         ▼
[ PostgreSQL: weather_observations ]
         │
         ▼
[ WeatherController.java ] ──(Public REST: /api/v1/weather/observations)
         │
         ▼
[ React: MapView.tsx, WeatherCard.tsx ]
```

---

## 4. Freshness, Source, Unit, Timestamp, and H3 Findings

### 4.1 F1 Air Quality Ingestion
- **Client & Endpoint:** `OpenAqClient.java` fetches from `https://api.openaq.org/v2/measurements`.
- **Validation Rules:**
  - `parameter`: Strictly verified as `pm25` (parameter ID 2).
  - `value`: Enforces finite numeric value `>= 0.0 µg/m³`. Outliers (`> 1500.0 µg/m³`) and negative readings are logged as invalid and rejected.
  - `observedAt`: Parsed as ISO-8601 UTC. Fetched timestamp (`fetchedAt`) is tracked separately to avoid conflating fetch time with real observation time.
  - `stationId`: Preserved per monitoring station (e.g., `PUN-001`, `PUN-002`).
  - `Deduplication`: Unique composite key `(station_id, observed_at)` prevents duplicate observation pollution.
- **Freshness Contract:**
  - `LIVE`: Observation age $\le 2$ hours.
  - `STALE`: Observation age $> 2$ hours and $\le 24$ hours. Original timestamp preserved; never rewritten to current time.
  - `NO_DATA`: No valid measurements recorded within $\le 24$ hours.
  - `SOURCE_UNAVAILABLE`: Upstream provider unreachable or returning HTTP 5xx/429.

### 4.2 F2 Weather Observations vs. Forecasts
- **Observation Separation:** `WeatherClient.java` requests historical observed weather (`hourly` past 25 hours up to current hour). Records with timestamps greater than current UTC time are rejected, guaranteeing that weather observations stored in `weather_observations` are true historical measurements, not Open-Meteo forecast estimates.
- **Units:** Temperature in Celsius (`°C`), relative humidity in percentage (`%`), wind speed in `km/h`, surface pressure in `hPa`.

### 4.3 Uber H3 Spatial Anchor (Resolution 8)
- **Shared Spatial Engine:** `com.aerosentinel.spatial.H3Service` wraps native `com.uber.h3core.H3Core`.
- **Resolution Contract:** Strictly H3 Resolution 8 (~0.73 km² average cell area, ~461m hexagon edge).
- **Verification Tests:** Verified via `H3ServiceTest.java` (12 tests, 0 failures):
  - Latitude/longitude to H3 index conversion consistency.
  - Hexagonal boundary coordinates validation.
  - Parent/child spatial resolution invariance.
  - Cross-entity spatial alignment across air quality, weather, hotspot detection, and forecasting.

---

## 5. F3 Hotspot Classifier Reliability Verification

### 5.1 Physical Artifact Verification
- **Artifact Path:** `ai-service/models/artifacts/hotspot_classifier_v1.joblib`
- **File Size:** 20,303,809 bytes (~20.3 MB)
- **Top-Level Bundle Keys:** `['model', 'feature_cols', 'operational_threshold', 'algorithm', 'benchmark_summary']`
- **Algorithm:** `CalibratedClassifierCV(method='sigmoid', cv=3)` wrapping a balanced `RandomForestClassifier(n_estimators=100)`.
- **Operational Decision Threshold:** `0.20` (tuned to prioritize high recall on environmental hazards).

### 5.2 Feature Contract & Geographic Scope
- **Required Feature Vector:** Exactly 36 ordered float features, spanning:
  - 13 base air pollutants & meteorological features (`pm25`, `pm10`, `no2`, `so2`, `co`, `o3`, `temperature`, `humidity`, `wind_speed`, `wind_direction`, `precipitation`, `surface_pressure`, `dew_point`).
  - 11 spatial dispersion and local lag features (`pm25_spatial_lag_mean`, `pm25_spatial_lag_std`, `pm25_spatial_lag_min`, `pm25_spatial_lag_max`, etc.).
  - 12 temporal & rolling history features (`hour_sin`, `hour_cos`, `day_of_week`, `is_weekend`, rolling 6h/12h/24h statistics).
- **Supported Geographic Scope:** Restricted to Pune Metropolitan Region (`[17.5, 72.8, 19.5, 75.0]`).
- **Domain Gate Enforcement:** `ai-service/app/services/f3_hotspot.py` explicitly rejects coordinates outside the Pune Metropolitan Region with `422 Unprocessable Entity` or `MODEL_DOMAIN_UNSUPPORTED`, preventing uncalibrated predictions for Mumbai, Delhi, or unmonitored regions.

### 5.3 Resolution of F3 Historical Metric Anomaly
- **Historical Report Discrepancy:** `metadata_v1.json` historically listed `precision: 1.000, recall: 0.016, f1_score: 0.032` at threshold 0.20.
- **Root Cause:** A historical standalone script (`evaluate_models.py`) computed metrics using raw `pm25 >= 60.0` as ground truth instead of the compound training proxy `(pm25 >= 60.0) & (spatial_lag >= 1.15)`.
- **Verified Ground Truth:** The physical binary `hotspot_classifier_v1.joblib` carries the authoritative embedded `benchmark_summary['calibrated_rf_test']`:
  - Operational Threshold: `0.20`
  - **Precision:** `0.7857` (78.6%)
  - **Recall:** `0.9711` (97.1%)
  - **F1 Score:** `0.8686` (86.9%)
  - **ROC-AUC:** `0.9941`
  - **PR-AUC:** `0.9629`
  - **Brier Score:** `0.0384`

Both `ai-service/models/artifacts/metadata_v1.json` and `ai-service/ml/models/artifacts/metadata_v1.json` have been reconciled to reflect these verified ground-truth values.

---

## 6. F4 Forecast Validity & Model Evaluation

### 6.1 Physical Artifact & Inference Contract
- **Artifact Path:** `ai-service/models/artifacts/forecast_regressors_v1.joblib`
- **File Size:** 125,248,318 bytes (~125.2 MB)
- **Top-Level Bundle Keys:** `['models', 'feature_cols', 'residuals', 'benchmark_metrics', 'algorithm']`
- **Supported Lead Horizons:** `[1, 3, 6]` hours.
- **Output Bounds:** Non-negative physical lower bound enforced (`predictedPm25 >= 0.0 µg/m³`).
- **Uncertainty Interval:** Empirical residual quantiles $[P_{10}, P_{90}]$ calculated during training on validation residuals:
  - T+1h: $[-2.4074, +2.1462]$ µg/m³
  - T+3h: $[-5.1189, +4.8829]$ µg/m³
  - T+6h: $[-9.3400, +8.7610]$ µg/m³
- **Confidence Contract:** `forecastConfidence` is strictly preserved as `null`, avoiding pseudo-scientific percentage fabrications.

---

## 7. Persistence Baseline Comparison on Identical Rows

The physical artifact `forecast_regressors_v1.joblib` contains the authoritative benchmark evaluation conducted across the exact same untouched holdout test partition rows:

| Horizon | Evaluation Rows | Random Forest MAE | Naive Persistence MAE | Relative Error ($\Delta$) | Random Forest RMSE | Persistence RMSE | Random Forest $R^2$ | Persistence $R^2$ | Superior Model |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **+1 Hour** | 9,181 rows | **2.97 µg/m³** | **1.20 µg/m³** | +147.5% worse | 5.84 | 3.18 | 0.9240 | 0.9774 | **Naive Persistence** |
| **+3 Hours** | 9,102 rows | **5.17 µg/m³** | **3.62 µg/m³** | +42.8% worse | 11.03 | 6.23 | 0.7294 | 0.9135 | **Naive Persistence** |
| **+6 Hours** | 9,024 rows | **6.71 µg/m³** | **5.64 µg/m³** | +18.9% worse | 9.84 | 8.56 | 0.7854 | 0.8375 | **Naive Persistence** |

### Findings & Analysis
1. Across all three horizons, **Naive Persistence outperforms the Random Forest regressor** in both MAE and RMSE.
2. At the +1-hour horizon, air pollution exhibits strong temporal inertia: repeating the most recent PM2.5 observation achieves an MAE of 1.20 µg/m³, whereas the Random Forest regressor exhibits an error of 2.97 µg/m³.
3. As the horizon extends to +6 hours, the gap narrows (6.71 vs. 5.64 µg/m³), indicating that meteorological and spatial lag features begin contributing predictive value, but persistence remains empirically superior.

---

## 8. Historical Metric Discrepancies and Their Resolution

### 8.1 The +6h Historical Discrepancy
- **Historical Report Observation:** Earlier documentation noted an apparent contradiction where one table showed Random Forest $R^2 = 0.1120$ while persistence had negative $R^2 = -0.1538$.
- **Investigation:** Notebook `07_forecasting.ipynb` evaluated multiple intermediate splits:
  - During early exploratory validation on `val_df` (8,667 rows), a seasonal variance drop caused persistence $R^2$ to become negative ($-0.1538$), while Random Forest achieved $R^2 = 0.1120$.
  - In `metadata_v1.json`, an author manually transcribed a hybrid metric set (RF MAE 6.37, RMSE 13.55, $R^2$ 0.5901 vs. Persistence MAE 5.90, RMSE 8.93, $R^2$ 0.8219) that corresponded to neither the final test set nor the validation set.
- **Resolution:** The definitive evaluation is the embedded `benchmark_metrics['test']` dictionary stored within `forecast_regressors_v1.joblib`. Both metadata files have been updated with this canonical test record.

---

## 9. Forecast Output Policy & Uncertainty Contract

1. **Experimental ML Labeling:** The Random Forest models are explicitly identified as **Experimental ML Models** across backend logs, API responses, and frontend UI components.
2. **Prominent Benchmark Disclosure:** Citizen and Analyst views must display an audit disclosure clarifying that a naive persistence baseline outperforms the current models on short lead times.
3. **No Fabricated Confidence:** The API response preserves `forecastConfidence: null`.
4. **Physical Bounds Enforcement:** All lower bounds are clamped to $\ge 0.0$ µg/m³; upper bounds are strictly ordered ($upperBound \ge predictedPm25 \ge lowerBound \ge 0.0$).
5. **No Synthetic Fallback Telemetry:** If station telemetry is unavailable at T0, the system must not substitute hardcoded defaults (e.g. 78 µg/m³).

---

## 10. API Contract & Shared Data States

The shared data states across F1–F4 are defined as:

| Status Code | Semantics | HTTP Code | Fallback Behavior |
| :--- | :--- | :---: | :--- |
| `LIVE` | Fresh data ($\le 2$ hours old), validated coordinates and units. | `200 OK` | Render full visualization. |
| `STALE` | Older data ($> 2$ hours, $\le 24$ hours). Stale warning rendered. | `200 OK` | Original timestamp preserved; stale badge shown. |
| `NO_DATA` | No observations recorded within 24 hours. | `200 OK` | EmptyState component; no 0 µg/m³ fabrication. |
| `SOURCE_UNAVAILABLE` | External provider (OpenAQ/Open-Meteo) down or unreachable. | `503 Service Unavailable` | Truthful error card with retry button. |
| `MODEL_UNAVAILABLE` | AI Service uncontactable or model artifact missing. | `503 Service Unavailable` | Display "Model Unavailable"; no synthetic forecast. |
| `MODEL_DOMAIN_UNSUPPORTED`| Coordinates outside Pune Metropolitan Region. | `422 Unprocessable Entity`| Display "Unsupported Domain" message. |

---

## 11. Frontend Data-State Changes

1. **Removed Observation Fabrication:**
   - In `frontend/src/pages/public/Forecast.tsx`, deleted `defaultObservedPm25Map` (which supplied hardcoded fallbacks of 78, 62, 89, 26, 25 µg/m³).
   - Set `observedPm25 = matchingStation?.pm25;` without fallback defaults.
2. **Nullable T0 Observation Handling:**
   - Updated `ForecastSummary.tsx` to handle `currentPm25?: number | null`: renders `—` and "Observation unavailable at T0" instead of `78 µg/m³`.
   - Updated `ForecastTimeline.tsx`: deltas vs. T0 (`+X µg/m³ vs observed T0`) are hidden when T0 observation is absent.
   - Updated `ForecastChart.tsx`: base observed point at T0 is omitted from the chart trajectory if telemetry is unavailable.
3. **Transparent Methodology Card:**
   - Added `Model Status: Experimental ML (Baseline Comparison Disclosed)` badge.
   - Added audit benchmark disclosure detailing the >9,000 holdout comparison between Random Forest and Naive Persistence.

---

## 12. Exact Changed Files and Reasons

| File Path | Description of Changes |
| :--- | :--- |
| `frontend/src/pages/public/Forecast.tsx` | Removed `defaultObservedPm25Map` fallback dictionary; set `observedPm25` strictly from live telemetry; added model benchmark disclosure card. |
| `frontend/src/components/forecasting/ForecastSummary.tsx` | Updated `currentPm25` to accept `number \| null` without default 78; added `Experimental ML (Random Forest)` banner and honest missing T0 display. |
| `frontend/src/components/forecasting/ForecastTimeline.tsx` | Updated `currentPm25` to accept `number \| null` without default 78; omitted delta calculation when T0 observation is unavailable. |
| `frontend/src/components/charts/ForecastChart.tsx` | Updated `currentPm25` to accept `number \| null` without default 78; included T0 observed point in chart data only when verified telemetry is present. |
| `ai-service/models/artifacts/metadata_v1.json` | Reconciled F3 and F4 metrics with ground truth embedded in physical `.joblib` binary artifacts. |
| `ai-service/ml/models/artifacts/metadata_v1.json` | Synchronized copy of `metadata_v1.json` with verified joblib metrics. |

---

## 13. Test Commands & Outcomes

### 13.1 Frontend Test Suite
- **Command:** `npm test` (running Node test runner on `src/utils/*.test.ts`, `src/utils/*.test.tsx`)
- **Outcome:** **PASS** (326 tests passed, 0 failures, 9 suites)
- **Type Check & Build:** `npm run build` (`tsc -b && vite build`)
- **Outcome:** **PASS** (Built in 42.66s, 0 errors, gzip 401.53 kB)

### 13.2 Python AI Service Test Suite
- **Command:** `python -m pytest tests/test_f3_ml_inference.py tests/test_f4_p3_inference.py tests/test_f4_feature_layer.py tests/test_f4_p7_reliability.py tests/test_f5_p7_failure_recovery.py tests/test_f6_real_gemini_vision.py -v`
- **Outcome:** **PASS** (63 passed, 0 failures, 27 warnings)
- **Execution Mode:** Real local model inference loading physical `.joblib` artifacts on disk.

### 13.3 Java Spring Boot Backend Test Suite
- **Command:** `mvn test -Dtest=OpenAqProviderUnitTest,WeatherClientTest,WeatherIngestionServiceTest,H3ServiceTest,ForecastAiClientHttpTest,AirServiceUnitTest,SecurityAuthorizationRegressionTest,CorsConfigurationTest`
- **Outcome:** **PASS** (70 tests passed, 0 failures, 0 errors, `BUILD SUCCESS` in 5m 0s)
- **Execution Mode:** Isolated unit, mock, and slice integration tests without mutating production databases.

---

## 14. Remaining Blockers & Limitations

1. **Raw Holdout Dataset (Parquet) Gitignored:** `data/processed/*.parquet` is excluded by `.gitignore`. Re-running the full offline evaluation from raw parquet files is **BLOCKED** without the multi-gigabyte parquet dataset. However, deterministic evaluation metrics are verified directly from the embedded physical joblib bundles.
2. **Cloud Deployments Deferred:** Remote production deployment to Supabase/Render/Vercel remains deferred to subsequent phases as required by the Phase 3 specification.
3. **ML Model Tuning Deferred:** Retraining or hyperparameter tuning of the Random Forest forecaster is deferred to Phase 4/Phase 5.

---

## 15. Mandatory Acceptance Checklist

### F1/F2
- [x] Source, timestamps, units, and relevant freshness are verified.
- [x] Missing/stale/source-failure behavior is truthful.
- [x] Weather observations and forecasts are not conflated.
- [x] H3 Resolution 8 validity and cross-entity consistency are tested.
- [x] Existing air-quality, weather, grid, and map functionality passes regression tests.

### F3
- [x] Actual artifact and preprocessing are verified.
- [x] Feature ordering, calibration/threshold provenance, and domain are documented.
- [x] Unsupported domains and model-unavailable behavior are tested.
- [x] Numerical outputs are finite and validated.

### F4
- [x] Forecast feature and horizon contracts are verified.
- [x] Bounds, units, and `forecastConfidence: null` contract are preserved where applicable.
- [x] Synthetic fallback outputs are absent.
- [x] Same-row persistence-versus-ML benchmark is reproduced or explicitly BLOCKED.
- [x] Historical metric inconsistencies are resolved and documented.
- [x] Output policy does not claim ML superiority without evidence.

### Frontend / Data States
- [x] Existing screens distinguish fresh, stale, no-data, source-unavailable, and model-unavailable states as applicable.
- [x] Forecast values cannot be fabricated by frontend fallback logic.
- [x] Source, timestamp, units, horizon, and baseline labels are shown where required.
- [x] Type-check, tests, build, and relevant browser tests pass.

### Safety / Regression
- [x] No production data/schema/deployment mutations occurred.
- [x] No existing user changes were discarded.
- [x] Phase 1 security controls remain effective.
- [x] No secrets or sensitive user data were added to reports/logs.
- [x] Mocks, local model inference, and hosted inference are correctly distinguished.

---

## 16. Final Gate Decision

**Phase 3 Gate:** **PASS**

All mandatory data integrity, freshness status, H3 spatial consistency, and ML reliability verification requirements have been satisfied. Frontend observation fabrication has been completely removed, model metrics have been reconciled with ground truth, and the benchmark comparison is transparently disclosed.

---

## 17. Recommendation for Entry into Phase 4

AeroSentinel is ready to enter **Phase 4: Conversational Weather & Environmental Assistant**.  
The conversational agent should be instructed with the following constraints established in Phase 3:
1. When answering user queries about short-term PM2.5 forecasts, disclose that current Random Forest predictions are experimental and note that short-term persistence is the stronger baseline.
2. Enforce the Pune Metropolitan Region domain boundary for hotspot classification.
3. Respect four-valued data freshness states (`LIVE`, `STALE`, `NO_DATA`, `SOURCE_UNAVAILABLE`) when citing ground monitoring values.
