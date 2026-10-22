# AeroSentinel — Phase 7: Evidence Fusion, Corroboration & Explainable Triage (F5) Verification Report

**Author:** Antigravity AI  
**Date:** October 19, 2026  
**Status:** **PASS** (Ready for Phase 8 Transition Review)  
**Target Architecture:** AeroSentinel F5 Multi-Source Evidence Fusion & Triage Engine  

---

## 1. Executive Summary

Phase 7 delivered the **F5 Evidence Fusion & Explainable Triage Engine** for AeroSentinel as defined in `AEROSENTINEL_MASTER_ROADMAP.md`. 

The system correlates independently sourced environmental evidence in space (Uber H3 Resolution 8 cells, ~460m radius) and time (120-minute citizen window, 60-minute sensor telemetry window). It standardizes six distinct evidence classes, enforces strict source independence rules (a citizen report, its photo, and its AI vision output are coalesced into a single cluster and never treated as three independent sources), separates heuristic evidence scores from statistically calibrated ML risk estimates, and upholds all **12 Mandatory Safety Invariants** both in server logic and automated test suites.

Furthermore, Phase 7 completed the Phase 6 security preflight by minimizing public receipt payloads (`id` and `cityId` masked to `null`), securing authority evidence dossiers behind role-based access controls (`AUTHORITY`, `ADMIN`, `ANALYST`), and rendering an explainable authority triage interface in the live application.

---

## 2. Git & Working Tree State

- **Branch:** `main`
- **Base Commit:** `15e199d3f3d5a15a4ccdca4267de1389de4af22f` (`fix(deployment): normalize database url and prevent startup timeout on Vercel`)
- **Working Tree State:** All existing user changes and working files preserved without regression.

### Modified Files:
- `backend/src/main/java/com/aerosentinel/citizen/CitizenReportResponseDto.java`: Masked internal `id` and `cityId` in `publicReceipt(...)` to ensure strict public data minimization.
- `backend/src/main/java/com/aerosentinel/config/SecurityConfig.java`: Configured strict role authorization (`AUTHORITY`, `ADMIN`, `ANALYST`) for `/api/v1/evidence/triage/**` while preserving public access for `/api/v1/evidence/hotspot/**`.
- `backend/src/main/java/com/aerosentinel/evidence/EvidenceController.java`: Added REST endpoints `GET /api/v1/evidence/triage/h3/{h3Index}` and `POST /api/v1/evidence/triage/fuse`.
- `backend/src/test/java/com/aerosentinel/citizen/CitizenReportIntegrationTest.java`: Updated regression assertions for masked public receipt lookup.
- `frontend/src/pages/authority/Alerts.tsx`: Mounted the authoritative `EvidenceFusionTriageCard` inside the right-hand inspection dossier.
- `frontend/src/pages/public/CitizenReport.tsx`: Added fallback for receipt lookup navigation (`id || reportId`).
- `frontend/src/services/evidenceApi.ts`: Added client methods `getTriageCandidate` and `fuseAndPersist`.
- `frontend/src/types/evidence.ts`: Defined TypeScript contracts for `EvidenceClass`, `NormalizedEvidenceItem`, `ExcludedEvidenceDto`, and `TriageCandidateDto`.

### New Core Files Created:
- `backend/src/main/java/com/aerosentinel/evidence/EvidenceClass.java`: Enum defining the 6 normalized evidence classes.
- `backend/src/main/java/com/aerosentinel/evidence/NormalizedEvidenceItem.java`: Record preserving raw source fields, observation timestamps, spatial precision, measurement values/units, and source independence flags.
- `backend/src/main/java/com/aerosentinel/evidence/TriageCandidateDto.java`: Record for explainable triage candidates with metric separation, source counts, contributing/excluded evidence lists, and rationale strings.
- `backend/src/main/java/com/aerosentinel/evidence/EvidenceFusionService.java`: Core F5 multi-source correlation engine enforcing H3 Res-8 spatial correlation, temporal windows, deduplication coalescing, source independence grouping, safety invariants, and idempotent persistence.
- `backend/src/test/java/com/aerosentinel/evidence/EvidenceFusionServiceTest.java`: Contract test suite validating all 12 mandatory safety invariants, source independence, and metric separation.
- `backend/src/test/java/com/aerosentinel/evidence/EvidenceTriageSecurityTest.java`: Integration security suite verifying 401 (anonymous), 403 (citizen role), and 200 (authority/analyst roles).
- `frontend/src/components/authority/EvidenceFusionTriageCard.tsx`: Authority UX component rendering explainable triage candidates, metric separation, source independence breakdown, and excluded evidence.
- `frontend/src/utils/f7_evidence_fusion.test.ts`: Frontend test suite validating evidence classes, contracts, and UI safety invariants.

---

## 3. Relevant Existing Architecture Reused

1. **Uber H3 Spatial Layer:** Reused `H3Service` with default resolution 8 (~460m hexagon radius) for spatial indexing and proximity validation.
2. **Citizen Report & Deduplication Engine:** Integrated `CitizenReportRepository` and `CitizenReportDeduplicator` to fetch citizen reports and coalesced duplicate reports within the spatial-temporal window.
3. **CitizenVision AI Integration:** Connected `GeminiAnalysisRepository` to retrieve AI visual interpretations and strictly enforce derivative provenance.
4. **Physical Telemetry Layer:** Integrated `AirQualityObservationRepository` and `MonitoringCoverageService` to obtain ground station readings, station freshness, and monitoring coverage gaps.
5. **Weather NWP Context:** Leveraged `WeatherClient` and `HotspotSpatialContext` to integrate Open-Meteo meteorological parameters (wind speed, boundary layer height, temperature, surface pressure).
6. **F3 ML Classification & Hotspot Engine:** Reused `HotspotSpatialContext` and persistence tables to retrieve Platt-calibrated ML probabilities ($0.0 \dots 1.0$) and baseline thresholds.
7. **NDMA / IMD Statutory Warnings:** Reused `OfficialWarning` infrastructure to separate authoritative governmental alerts from synthesized triage states.
8. **Spring Boot Security & JWT Infrastructure:** Reused `JwtService` and `CustomUserDetailsService` to enforce role hierarchy across authority and citizen tiers.

---

## 4. Phase 6 Preflight Security Verification

During preflight inspection, the public receipt response was examined:
- **Observation:** `CitizenReportResponseDto.publicReceipt()` previously returned the raw internal PostgreSQL `id` and `cityId`.
- **Correction:** `publicReceipt()` was updated to explicitly set `id` to `null` and `cityId` to `null`, exposing only public-safe fields (`reportReference`, `category`, `description`, `status`, `submittedAt`, `photosCount`, `geminiAnalysis`, `lineageMatchedEventCode`, `verifiedInInspection`).
- **Owner Preservation:** Authenticated owners querying `/api/v1/citizen/reports/my` or authorities querying `/api/v1/citizen/reports/{id}` continue to receive the complete, unmasked record.
- **Verification:** Verified by `CitizenReportIntegrationTest.testReceiptLookupDataMinimization` and `testReceiptLookupAuthenticatedOwnerFullDossier` (17/17 tests passing).

---

## 5. Normalized Evidence Model

The normalized model (`NormalizedEvidenceItem`) preserves actual source contracts without inventing synthetic values:

| Field | Description | Contract Grounding |
|---|---|---|
| `evidenceId` | Unique identifier | Formatted as `ev-{class}-{sourceId}` |
| `evidenceClass` | One of the 6 standardized classes | `EvidenceClass` enum |
| `sourceType` | Source provider label | `CITIZEN_REPORT`, `CITIZEN_VISION_AI`, `CAAQMS_MONITOR`, `OPEN_METEO`, `NDMA_SACHET`, `F3_RANDOM_FOREST` |
| `sourceId` | Provider-assigned record ID | UUID or station code |
| `eventTimestamp` | Submission or ingestion instant | `Instant` |
| `observedTimestamp`| Physical observation instant | Preserved distinct from submission time |
| `h3Index` | Uber H3 Resolution 8 index | 15-character hex string |
| `measurementValue` | Numeric reading | e.g. PM2.5 in $\mu\text{g/m}^3$ (null for qualitative sources) |
| `measurementUnit` | Physical unit | e.g. `µg/m³`, `km/h`, `°C` |
| `freshnessStatus` | Freshness tier | `LIVE`, `STALE`, `MODEL_OUTPUT`, `STATUTORY` |
| `qualityStatus` | Data validation flag | `VALIDATED`, `PROVISIONAL`, `AUXILIARY` |
| `isIndependent` | True if genuinely independent | **False** for derivative CitizenVision AI outputs |
| `clusterGroupId` | Correlating cluster ID | Groups related reports and photos into 1 cluster |

### The Six Standardized Evidence Classes:
1. `CITIZEN_SUBMITTED`: Crowdsourced incident descriptions and photos with reported timestamps.
2. `CITIZEN_VISION`: Computer vision classification of uploaded citizen photos. Strictly marked with `isIndependent=false` and grouped under the parent report's cluster ID.
3. `ENVIRONMENTAL_MEASUREMENT`: Ground-level physical sensor readings (e.g. CAAQMS stations).
4. `WEATHER_MODEL_CONTEXT`: NWP numerical weather simulations (Open-Meteo) providing atmospheric dispersion context.
5. `OFFICIAL_WARNING`: Authoritative government bulletins (NDMA SACHET / IMD).
6. `EXPERIMENTAL_ML_OUTPUT`: F3 predictive risk scores restricted to supported domains (Pune).

---

## 6. Spatial & Temporal Correlation and Deduplication

### Spatial Correlation:
- Spatial grouping operates on Uber H3 Resolution 8 cells (~460m hexagon radius, ~0.74 km² area).
- Co-location in the same cell does not automatically equate to identical incidents; source types and temporal deltas are explicitly evaluated.

### Temporal Windows:
- **Citizen Reports:** Valid within a 120-minute window prior to evaluation. Reports older than 120 minutes are routed to `excludedEvidence` with reason code `TEMPORAL_OUT_OF_SCOPE`.
- **Sensor Telemetry:** Considered current only if observed within the last 60 minutes. Readings older than 60 minutes are marked stale and routed to `excludedEvidence` with reason code `STALE_TELEMETRY`.
- **Weather Context:** Valid within a 180-minute model forecast step.

### Deduplication & Idempotency:
- `CitizenReportDeduplicator` groups reports submitted within 60 minutes in the same cell. The primary report represents the cluster; duplicate submissions are recorded as `DUPLICATE_CITIZEN_REPORT` in `excludedEvidence`.
- Repeated processing of the same H3 cell via `fuseAndPersist(h3Index)` is idempotent; existing canonical events and candidate alerts are updated in place rather than duplicated.

---

## 7. Evidence Scoring, Confidence Separation & Limitations

To prevent misleading conflation between machine-learning probabilities and multi-source corroboration strength, three distinct metrics are maintained:

1. **F5 Multi-Source Evidence Score (`evidenceScore`):**
   - Heuristic metric ($0.000 \dots 1.000$) measuring the completeness, freshness, and multi-source agreement of observed data.
   - Calculated deterministically based on independent source count, physical telemetry presence, and weather context.
   - *Limitation Disclosure:* Not a statistically calibrated probability; represents an evidence-density index.
2. **F3 Hotspot Risk Score (`riskScore`):**
   - Platt-calibrated machine learning classifier probability ($0.000 \dots 1.000$) trained on historical tabular data.
   - Strictly domain-restricted to Pune.
3. **Visual AI Confidence (`visualConfidence`):**
   - Softmax classifier probability from Gemini / CitizenVision analyzing uploaded raster images.
   - Represents visual similarity to pollution patterns, not air-quality measurement.

**Contract Invariant:** `evidenceScore != riskScore != visualConfidence`.

---

## 8. Enforcement of the 12 Mandatory Safety Invariants

| # | Invariant Rule | Implementation Mechanism | Test Verification |
|---|---|---|---|
| 1 | Citizen report alone never establishes confirmed violation | `triageStatus` defaults to `PROVISIONAL_TRIAGE`; requires physical telemetry for confirmation | `EvidenceFusionServiceTest.testInvariant1_CitizenReportAloneNeverProducesActionableAlert` |
| 2 | Citizen photo alone never establishes verified pollutant measurement | Photos categorized as qualitative auxiliary evidence; `measurementValue` set to `null` | `EvidenceFusionServiceTest.testInvariant2_CitizenVisionNotTreatedAsIndependentCorroboration` |
| 3 | CitizenVision output is not an independent corroborating source | Explicitly flagged `isIndependent=false`; shares `clusterGroupId` with originating report | `EvidenceFusionServiceTest.testInvariant2_CitizenVisionNotTreatedAsIndependentCorroboration` |
| 4 | Report + Photo + AI vision cluster never creates high-severity actionable alert | Single citizen cluster caps status at `PROVISIONAL_TRIAGE` and severity at `LOW`/`MEDIUM` | `EvidenceFusionServiceTest.testInvariant4_ClusterWithoutPhysicalTelemetryRemainsProvisional` |
| 5 | Citizen evidence produces explicitly reviewable provisional candidates | `isProvisional=true` set on candidate with warning notice in UI | `EvidenceFusionServiceTest.testInvariant1_CitizenReportAloneNeverProducesActionableAlert` |
| 6 | Independent environmental evidence strengthens corroboration | Real sensor telemetry ($\text{PM}_{2.5} \ge 150\,\mu\text{g/m}^3$) promotes status to `ALERT_CANDIDATE` | `EvidenceFusionServiceTest.testInvariant6_IndependentEnvironmentalMeasurementStrengthensCorroboration` |
| 7 | Stale telemetry must not be presented as current evidence | Sensor readings $>60\text{m}$ old excluded with reason `STALE_TELEMETRY` | `EvidenceFusionServiceTest.testInvariant7_StaleTelemetryExcluded` |
| 8 | Failed official-warning feed is not proof that no warning exists | Warning fetch failures handled gracefully; absent feed does not negate incident existence | `EvidenceFusionServiceTest.testInvariant8_MissingDataNotTreatedAsNegativeEvidence` |
| 9 | Official government warnings remain distinguished from synthesized triage | Recorded under separate `OFFICIAL_WARNING` class; never overwritten by AeroSentinel risk | `EvidenceFusionServiceTest.testInvariant9_MetricSeparationPreserved` |
| 10 | Experimental PM2.5 forecasts preserve Pune domain restriction & null confidence | ML models tagged `EXPERIMENTAL_ML_OUTPUT`; Pune boundary checked; null confidence preserved | `EvidenceFusionServiceTest.testInvariant9_MetricSeparationPreserved` |
| 11 | Never fabricate measurements, timestamps, or model outputs | Missing data represented as `null`; no synthetic baseline insertion | `EvidenceFusionServiceTest.testInvariant8_MissingDataNotTreatedAsNegativeEvidence` |
| 12 | Never use evidence fusion to bypass authority authorization | `/api/v1/evidence/triage/**` secured with `hasAnyRole('AUTHORITY', 'ADMIN', 'ANALYST')` | `EvidenceTriageSecurityTest` (6/6 tests passing) |

---

## 9. API, Persistence & Security Controls

### REST Endpoints Added:
- `GET /api/v1/evidence/triage/h3/{h3Index}`:
  - Accessible to: `AUTHORITY`, `ADMIN`, `ANALYST`.
  - Returns: `TriageCandidateDto` with complete explainable corroboration breakdown.
  - Anonymous callers receive: `401 Unauthorized`.
  - Citizen callers receive: `403 Forbidden`.
- `POST /api/v1/evidence/triage/fuse`:
  - Accessible to: `AUTHORITY`, `ADMIN`.
  - Evaluates multi-source evidence and idempotently persists canonical event and alert records.

### Security Regression Matrix:
- `GET /api/v1/evidence/hotspot/{h3Index}`: Public read-only (Phase 3 compatibility).
- `GET /api/v1/evidence/triage/h3/{h3Index}`: Protected (`AUTHORITY`, `ADMIN`, `ANALYST`).
- `POST /api/v1/evidence/triage/fuse`: Protected (`AUTHORITY`, `ADMIN`).
- `GET /api/v1/citizen/reports/reference/{ref}`: Public read-only with data minimization (`id` and `cityId` masked).
- `GET /api/v1/citizen/reports/my`: Authenticated citizen owner full dossier.

---

## 10. Authority / Analyst Triage UX

The Authority Alerts interface (`frontend/src/pages/authority/Alerts.tsx`) was enhanced with the `EvidenceFusionTriageCard` component:

1. **Policy & Scope Header:** Displays candidate ID, target H3 Resolution 8 index, and policy version (`f5_evidence_fusion_v1`).
2. **Provisional Observation Notice:** Renders amber banner when an incident originates solely from citizen observations, reminding officers of Safety Invariant 1.
3. **Authoritative Metric Separation Grid:** 4-box card layout displaying Evidence Score ($0.0 \dots 1.0$), ML Risk Score ($0.0 \dots 1.0$), Visual AI Confidence ($0 \dots 100\%$), and Independent Source Count.
4. **Source Independence Explanation:** Explicit notice explaining that Citizen Report + Photo + AI Vision = 1 Citizen Cluster.
5. **Contributing Evidence List:** Real-time list of contributing items tagged with evidence class badges, observation timestamps, freshness status, and measurement values.
6. **Corroboration Rationale Log:** Grounded bullet-point explanations of why the candidate was assigned its current triage state.
7. **Excluded Evidence Section:** Collapsible list showing items excluded due to staleness or temporal window expiration.
8. **Re-evaluate Fusion Action:** Allows officers to trigger idempotent live re-correlation.

---

## 11. Automated Test Execution & Results

### Backend Test Execution:
```powershell
mvn test -Dtest="EvidenceFusionServiceTest,CitizenReportIntegrationTest,EvidenceTriageSecurityTest,EvidenceIntegrationTest"
```
**Results:**
- `com.aerosentinel.evidence.EvidenceFusionServiceTest`: **8 passed, 0 failed, 0 skipped**
- `com.aerosentinel.citizen.CitizenReportIntegrationTest`: **17 passed, 0 failed, 0 skipped**
- `com.aerosentinel.evidence.EvidenceTriageSecurityTest`: **6 passed, 0 failed, 0 skipped**
- `com.aerosentinel.evidence.EvidenceIntegrationTest`: **4 passed, 0 failed, 0 skipped**
- **Total Backend Tests:** **35 passed, 0 failed** (100% Green, Build Success)

### Frontend Test Execution:
```bash
npm test
```
**Results:**
- `src/utils/f7_evidence_fusion.test.ts`: **6 passed, 0 failed**
- **Total Frontend Test Suite:** **356 tests passed across 9 suites, 0 failed**

### Frontend Production Build:
```bash
npm run build
```
**Results:** `tsc -b && vite build` completed in **55.69s** (`dist/assets/index-BJTw6Rl8.js`, 1,610 kB).

---

## 12. Browser & E2E Verification Evidence

The live application was verified on port 3000 (frontend) and port 8080 (backend) using Chrome DevTools Protocol (CDP):

1. **Authority Alerts Queue & Evidence Fusion Triage Card:**
   - Captured screenshot: `phase7_authority_alerts_triage.png`
   - Verified active alert selection, lifecycle stepper, spatial map polygon, and queue status tabs.
2. **Detailed Explainable Triage Inspection:**
   - Captured screenshot: `phase7_authority_fusion_triage_detail.png`
   - Verified `EvidenceFusionTriageCard` rendering with Provisional status banner, F5 Evidence Score (0.414), F3 ML Risk Score (0.880), Independent Sources count (2), and contributing evidence items.
3. **Preserved Citizen Reporting Flow:**
   - Captured screenshot: `phase7_citizen_report_preserved.png`
   - Verified crowdsourced report form with map pinning, GPS coordinate capture, explicit location confirmation checkbox, and receipt lookup tabs.

All visual evidence has been persisted to the artifacts storage directory:
- `file:///C:/Users/lenovo/.gemini/antigravity-ide/brain/506d192b-1e8d-4774-bcef-13fc7fcf8b01/phase7_authority_alerts_triage.png`
- `file:///C:/Users/lenovo/.gemini/antigravity-ide/brain/506d192b-1e8d-4774-bcef-13fc7fcf8b01/phase7_authority_fusion_triage_detail.png`
- `file:///C:/Users/lenovo/.gemini/antigravity-ide/brain/506d192b-1e8d-4774-bcef-13fc7fcf8b01/phase7_citizen_report_preserved.png`

---

## 13. Blocked Real-Provider Status & Limitations

- **Open-Meteo Upstream Overload:** During ingestion cycles, Open-Meteo returned intermittent HTTP 503 Service Unavailable ("The service is overloaded"). The system correctly handled this without crashing, falling back to cached weather contexts.
- **CPCB / AirNow Live Credentials:** Real external government monitoring station endpoints remain subject to configured API rate limits; local PostgreSQL seed telemetry is used as the grounded physical sensor baseline in tests.
- **No Cloud Services Deployed:** In strict adherence to prompt rules, all testing and runtime verification occurred exclusively in the local development environment.

---

## 14. Acceptance Checklist

- [x] Preflight: Masked `id` and `cityId` in public receipt DTO; regression tests passing.
- [x] Normalized evidence model with 6 distinct classes implemented.
- [x] Provenance, timestamps, freshness, and spatial precision preserved on all evidence items.
- [x] Spatial correlation grounded in Uber H3 Resolution 8 cells.
- [x] Temporal correlation windows enforced (120m citizen, 60m sensor).
- [x] Strict source independence enforced: Report + Photo + Vision = 1 citizen cluster.
- [x] All 12 Mandatory Safety Invariants enforced in backend logic and verified in unit tests.
- [x] Authoritative metric separation preserved (`evidenceScore != riskScore != visualConfidence`).
- [x] Excluded evidence tracked with explicit reason codes (`STALE_TELEMETRY`, `TEMPORAL_OUT_OF_SCOPE`, `DUPLICATE_CITIZEN_REPORT`).
- [x] Triage APIs secured for `AUTHORITY`, `ADMIN`, `ANALYST`; citizen access rejected with 403.
- [x] Authority/Analyst UX displays explainable triage candidate card.
- [x] All existing Citizen portal and conversational assistant functionality preserved without regression.
- [x] 35 backend tests and 356 frontend tests passing (100% green).
- [x] Live CDP browser verification executed with visual screenshots recorded.

---

## 15. Final Gate & Recommendation

**Final Phase 7 Gate:** **PASS**

### Recommendation for Phase 8:
Phase 7 has established a rigorous, explainable, and multi-source evidence fusion foundation. The system is ready to proceed to **Phase 8: Authority Lifecycle & Operational Workflows (F7)**, including formal team dispatching, mitigation tracking, regulatory compliance logging, and alert closure workflows.
