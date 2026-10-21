# AeroSentinel — Master Product Roadmap (Phase 3 Onward)

**Purpose:** Source-of-truth roadmap for completing the existing AeroSentinel project without losing previously agreed features, architecture, safety rules, or deployment blockers.

**Product position:** *A trusted conversational weather and environmental-intelligence platform with evidence-backed authority workflows.* The conversational weather experience is the citizen-facing entry point; AeroSentinel's differentiator remains the connected spatial pollution-intelligence and authority-response workflow.

## 0. Project status and operating rules

### What the reports currently say

- **Phase 0:** Audit report produced; identified production disconnection, missing AI model artifacts on the historical Vercel AI deployment, Java/Python subprocess incompatibility, authorization weaknesses, and F4 model metrics that were worse than the persistence baseline.
- **Phase 1:** The submitted report says security controls were tightened, F4/F5/F6 local `ProcessBuilder` calls were replaced with FastAPI HTTP calls, fabricated forecast multiplier fallbacks were removed, and the reported focused tests passed. Real production identity-provider and Gemini configuration remained unverified/blocked.
- **Phase 2 local work:** The submitted closure report says API URL normalization, strict rejection of SPA HTML as API success, CORS tightening, a Dockerfile for the AI service, and a Flyway production-startup safeguard were added. Local PostGIS and local model inference were tested.
- **Cloud release:** Still **BLOCKED / DEFERRED** in the reports because the historical Vercel AI service did not load models, the frontend did not point to a live backend, and the Supabase project was reported paused. The user subsequently confirmed that Step 1 (Supabase restoration/initial checks) was completed; that does not by itself prove all cloud paths work end-to-end. Reconfirm current state during the dedicated deployment phase.
- The reported test numbers are from execution summaries, not independently rerun in this roadmap document. Future prompts must check the current repository rather than assume every historical claim remains true.

### Non-negotiable product and engineering rules

1. Keep the existing repository and core architecture: React + TypeScript + Vite frontend, Java 21/Spring Boot orchestration/business logic, Python FastAPI for ML/AI inference, PostgreSQL/PostGIS, and H3 Resolution 8 as the shared spatial anchor (verify current code before making changes).
2. Do not start a duplicate project, duplicate database, or duplicate business logic.
3. Never invent environmental observations, official warnings, numerical predictions, model confidence, evidence, inference results, or workflow success.
4. Every observation/forecast shown to users should carry source/provenance and relevant timestamps. Make stale/no-data/unavailable states visible; never silently substitute fake numbers.
5. Clearly separate current weather observations, weather forecasts, air-quality observations, PM2.5 forecasts, official government warnings, AeroSentinel risk assessments, and Gemini interpretation.
6. Gemini can explain validated results; it cannot fabricate or overwrite numeric values, model outputs, or official warnings.
7. Citizen reports/photos are auxiliary evidence, not scientific measurements or conclusive proof. A report/photo alone must not create a high-severity alert.
8. Distinguish `riskScore`, `evidenceScore`, model probability, forecast uncertainty, and image/vision confidence. Preserve `forecastConfidence: null` if unsupported by the current contract.
9. Privileged actions require server-side authentication, trusted role mapping, validation, and auditability. Missing auth configuration must fail closed.
10. Do not claim the Pune-focused F3 model is validated for other cities. Cross-city inference requires independent evaluation.
11. Forecast ML must be compared with a persistence baseline per horizon. If the ML model is worse, show the baseline or mark ML experimental/unavailable as the evaluated product design permits; never call it superior without evidence.
12. Deployment is postponed until the dedicated deployment window, but all required cloud actions remain on the tracked release checklist. Local/staging success is not production success.
13. Each phase must end with a report and `PASS`, `FAIL`, or `BLOCKED`; do not start the next phase until the current gate and known blockers are reviewed.

## 1. Target product experiences

### Citizen

- Select/confirm location using permission or manual city/area/PIN selection.
- Conversational, source-grounded weather and environmental questions.
- Current conditions and genuine forecast information, clearly distinguished.
- Official warnings with issuer, impacted location, validity period, and source link when available.
- Air-quality observations with station/source, measurement time, freshness, and pollutant units.
- PM2.5 risk/forecast only when the model/input path is validated; show horizon and uncertainty honestly.
- Plain-language explanation that cites the data used and explains unavailable/missing inputs.
- Submit pollution observations and photos; view own report status and suitable status updates.
- English, Hindi, and Marathi UX where feasible and tested; no misleading translation of official warning details.
- Later enhancements: saved locations, opt-in notifications, historical trend comparison, and accessibility/mobile improvements.

### Authority / field team

- Spatial view of H3 cells, monitoring stations, valid observations, supported model results, evidence sources, and freshness.
- Review queue containing only qualifying candidate events.
- Evidence/provenance detail and explicit explanation of why a case is `INSUFFICIENT_EVIDENCE`, `MONITOR`, or `ALERT_CANDIDATE`.
- Authorized acknowledge, assign, inspect, record field observations, verify, resolve/close, and view state history.
- Citizen information only as needed and according to permissions; never expose restricted internal notes to citizens.
- Consistent synchronization between PollutionEvent, Alert, inspection, action, and resolution states.

### Platform administrator

- Provider ingestion health and data freshness.
- Service liveness/readiness and per-model/per-operation availability.
- User/role administration only through a real trusted identity/authorization design.
- Error rates, safe audit trail, configuration/readiness status, and actionable diagnostics without leaking secrets or citizen PII.
- Admin is a separate role/view, but uses the same project/backend and shared domain logic.

### Analyst/researcher (optional existing role)

- Read-only, privacy-safe analysis of spatial trends, model evaluations, forecast errors, and data coverage.
- No implied ability to perform authority actions.

## 2. Phase roadmap

## Phase 3 — Data Integrity, Freshness, and ML Reliability Gate

**Goal:** Establish which existing F1–F4 outputs are trustworthy enough to power new features. This is a reliability/build phase, not a model-retraining project by default.

### Scope

- Re-audit current F1 air-quality and F2 weather data paths: actual provider, payload mapping, station identity, units, `observedAt`/`fetchedAt`, source lineage, duplicate/out-of-order handling, freshness, cache policy, and missing-data behavior.
- Preserve distinction between weather observations and actual weather forecasts. Identify the authorized/available forecast and official-warning sources needed for later phases; do not guess APIs or scrape undocumented interfaces.
- Validate H3 Resolution 8 generation and consistency between station/observation/grid/evidence records. Reject or explicitly handle invalid/missing `h3Index`.
- Verify F3 artifact readiness, input feature ordering, preprocessing, calibrated output, threshold, supported geographic domain, and missing-artifact behavior.
- Reproduce F4 horizon metrics on the same untouched evaluation rows for the Random Forest and persistence baseline (+1h, +3h, +6h). Check historical metric inconsistency; do not repeat old numbers without reproduction.
- Decide output policy per horizon based on evidence. Preserve genuine ML output for engineering/testing, but do not promote an inferior model as the primary forecast. If an existing persistence baseline is used in a user-facing path, label it explicitly as a baseline and use real timestamped input.
- Verify P10/P90 interval validity, nonnegative PM2.5 bounds, units, model version, and null `forecastConfidence` contract.
- Standardize honest service/data states such as `LIVE`, `STALE`, `NO_DATA`, `SOURCE_UNAVAILABLE`, and `MODEL_UNAVAILABLE`, reusing current contracts if they already exist.

### Acceptance gate

- Source and timestamp visible in APIs for relevant readings/results.
- No synthetic observations or made-up forecast values.
- H3 consistency tests pass.
- F3/F4 ready/unavailable states are truthful.
- Horizon-level benchmark is reproducible and documents ML-versus-baseline results.
- Unsupported geographic/horizon requests are clearly handled.
- Existing F1/F2 behavior has regression coverage.

**Deliverable:** `PHASE_3_DATA_ML_RELIABILITY_REPORT.md`.

## Phase 4 — Trusted Conversational Weather & Environmental Assistant

**Goal:** Build the conversational assistant as the main citizen entry point without replacing the existing AeroSentinel pollution workflow.

### Scope

- Build one guarded assistant/orchestrator in the existing app. Avoid many unsupervised agents and avoid placing business rules in the LLM.
- Expose typed backend tools/services only for capabilities with real implementations, for example:
  - `getCurrentWeather(location)`
  - `getWeatherForecast(location, timeRange)`
  - `getOfficialWarnings(location)`
  - `getAirQuality(location)`
  - `getPM25Risk(h3Index, horizon)`
  - `getDataFreshness(source)`
  - `getOfficialGuidance(topic)` only when a verified authoritative source is available.
- Assess official provider availability, access rules, attribution, update cadence, coverage, and documented endpoints before integration. Candidate sources previously discussed include IMD for weather and NDMA SACHET for official warnings, but current access and contracts must be verified first.
- Use validated backend payloads as the source of truth; Gemini generates a user-friendly explanation grounded in those payloads and references.
- Include source, time, location, forecast horizon, uncertainty/limitations, and fallback state in assistant answers.
- Resolve missing-location questions safely (ask one follow-up when necessary); use explicit timezone and India-local time handling where appropriate.
- Handle unsupported questions and unavailable sources without guessing. Keep official alert text separate from generated summaries.
- Add prompt-injection/data-trust safeguards for user input and retrieved external text. The assistant cannot execute sensitive authority actions through citizen chat.

### Acceptance gate

- Each tool has a schema and unit test, including invalid inputs and provider failures.
- Answers are grounded in actual tool results and show relevant source/timestamp.
- No model-generated number can overwrite numeric source data.
- Missing location, stale data, no warnings, source timeout, Gemini timeout, malformed output, and unsupported questions have tested responses.
- Citizen conversational API works with a real local/staging model/provider when credentials/access exist; otherwise live-provider verification is explicitly blocked.

**Deliverable:** `PHASE_4_CONVERSATIONAL_ASSISTANT_REPORT.md`.

## Phase 5 — Unified Citizen Experience (Weather + Environment)

**Goal:** Integrate verified data and the assistant into a complete, coherent citizen UX in the existing frontend.

### Scope

- Audit current routes/components and reuse existing working screens instead of rebuilding the frontend.
- Location selection/manual location entry and appropriately permissioned geolocation.
- Current conditions, forecast, official warnings, air quality, and supported PM2.5 risk cards with clear labels and provenance.
- Chat/assistant interface that calls the typed backend assistant, not a browser-side secret or ungrounded direct LLM call.
- Useful loading, empty, stale, offline, timeout, permission-denied, and server-error states. Do not leave infinite spinners or render error HTML as data.
- Accessible responsive design for desktop/mobile; clear language and units; explain the difference between observation, forecast, official warning, and AeroSentinel risk.
- Keep existing route conventions and preserve public data pages and map functionality.
- Do not claim a user has received a notification unless a real notification delivery path exists.

### Acceptance gate

- The main citizen journey works from location selection to sourced answer and related weather/air-quality detail.
- All displayed claims map to API data; no hardcoded sample values in the production path.
- Type-check, tests, build, and relevant browser tests pass.
- Screens work on mobile and desktop; all unavailable states are understandable.

**Deliverable:** `PHASE_5_CITIZEN_UX_REPORT.md`.

## Phase 6 — Citizen Reports, Photo Intake, and Report Tracking (F6)

**Goal:** Finish the complete citizen-report workflow, with privacy and evidence semantics intact.

### Scope

- Location/category/description/photo validation and sensible image type/size limits.
- Server derives the canonical H3 index; never trust client-supplied H3 as the source of truth.
- Use safe image storage/access rules; avoid exposing private upload URLs or report details publicly.
- Persist the citizen report according to the existing contract even when Gemini/Vision is unavailable, where technically supported.
- Call FastAPI vision endpoint over HTTP and validate structured output; label deterministic category fallback distinctly from live image analysis.
- Record model/provider status, visual observations, uncertainty, timestamps, and report-to-evidence linkage when available.
- Link the report as `AUXILIARY` evidence to matching H3/time context. It must not be treated as a pollutant measurement.
- Citizen can view their own report and an appropriate sanitized status; no private authority notes or cross-user report access.
- Prevent duplicates/abuse where current design supports it; add rate limits/validation if needed.

### Acceptance gate

- Report submission, persistence, image validation/storage, H3 assignment, safe vision failure, evidence linkage, and citizen status flow are tested end-to-end in an isolated environment.
- Bad image, too-large file, malformed fields, unavailable AI, and cross-user access are tested.
- A report/photo alone cannot create an actionable/high-severity alert.

**Deliverable:** `PHASE_6_CITIZEN_REPORT_REPORT.md`.

## Phase 7 — Evidence Fusion, Risk Explanation, and Candidate Triage (F5)

**Goal:** Make every candidate risk/explanation traceable and safe before it reaches authorities.

### Scope

- Connect validated F1–F4 outputs and auxiliary F6 reports using canonical H3/time context.
- Preserve evidence source, timestamp, freshness, location match, score, and input lineage.
- Keep `riskScore`, `evidenceScore`, hotspot probability, forecast intervals, and vision confidence as separate named values.
- Verify Gemini explanations are generated only from validated evidence; outputs must be schema-validated and cannot rewrite numerical results.
- Preserve `INSUFFICIENT_EVIDENCE`, `MONITOR`, and `ALERT_CANDIDATE` semantics (verify the actual current contract).
- Insufficient evidence or unavailable AI must never create an actionable alert. Conservative deterministic behavior must be transparent, not a fabricated score or story.
- Explain missing/conflicting/old evidence and why the system recommends monitor versus candidate review.
- Audit duplicate candidate detection and traceability to evidence used in each decision.

### Acceptance gate

- Golden and adversarial tests demonstrate no alert from citizen-only evidence or failed AI calls.
- Every decision has traceable evidence and versioned inputs.
- Gemini output cannot alter numbers or official warnings.
- Candidate creation only follows the validated server-side eligibility contract.

**Deliverable:** `PHASE_7_EVIDENCE_TRIAGE_REPORT.md`.

## Phase 8 — Complete Authority / Field-Team Lifecycle (F7)

**Goal:** Make the operational response process reliable from qualifying candidate to verified case outcome.

### Scope

- Map/queue of qualifying candidate events and alerts with evidence, H3 cell, timestamps, and source details.
- Fix and test synchronization between PollutionEvent, Alert, inspection, action, and resolution state.
- Supported states/transitions documented explicitly; reject invalid or repeated transitions safely.
- Authority acknowledgement, field-team assignment, inspection start, field observation entry, verification, resolution/closure, and state history.
- Enforce server-side authority/admin role permissions and object-level access on every privileged action.
- Audit actor, action, timestamp, and state change without storing unnecessary sensitive information.
- Ensure citizen-visible status updates do not expose internal authority notes.
- Distinguish AeroSentinel candidate alerts from official government warnings.
- Add idempotency/concurrency protection where duplicate actions or race conditions can corrupt state.

### Acceptance gate

- Full isolated E2E flow: eligible evidence → candidate → authority queue → acknowledge → assign → inspect → verify → resolve.
- Negative flow: insufficient evidence → no actionable alert.
- Unauthorized actors, invalid transitions, duplicate submissions, stale updates, and downstream failure are tested.
- Event and alert statuses never silently diverge.

**Deliverable:** `PHASE_8_AUTHORITY_LIFECYCLE_REPORT.md`.

## Phase 9 — Admin Observability and F8 Monitoring-Gap Intelligence

**Goal:** Give operators a truthful system view and provide defensible recommendations for under-monitored areas.

### Admin scope

- Data-provider freshness/health, last successful ingestion, failures, and row counts without personal data.
- Backend/AI-service liveness/readiness; per-model and Gemini configuration/availability.
- Role/user administration only through verified identity and authorization.
- Error summaries, safe audit events, and deployment/configuration status; no secret-bearing logs.
- Separate operational admin from authority case-response permissions unless a documented policy grants both.

### F8 monitoring scope

- Reuse existing station data, H3, Haversine distance, and coverage-context calculations where verified.
- Compute transparent monitoring gaps/priority from coverage, distance, supported risk, and uncertainty; document actual formula and units instead of inventing unexplained weights.
- Show nearest stations, station counts/radius, data freshness, relevant H3, recommendation rationale, and limitations.
- Distinguish visualization of low coverage from an actionable station-placement recommendation.
- Prefer derived/on-demand recommendations for MVP if appropriate; add persistence only if justified by actual product requirements and schema review.
- Show recommendations on the existing map and provide a useful empty/unavailable state.

### Acceptance gate

- Admin information matches actual service/provider status.
- No sensitive config or PII leakage.
- Monitoring result is explainable, reproducible, H3-consistent, and tested against missing-station/data edge cases.
- F8 does not claim scientific certainty or authority approval for a suggested site.

**Deliverable:** `PHASE_9_ADMIN_MONITORING_REPORT.md`.

## Phase 10 — Advanced Intelligence and User Value

**Goal:** Add advanced features only after the source-backed core workflow is stable.

Candidate features, prioritized only after dependency review:

1. Saved citizen locations and opt-in alert subscriptions.
2. User-selected weather/air-quality threshold notifications grounded in verified sources.
3. Historical trend comparison and time-of-day/day-over-day context with honest missing data.
4. H3 timeline/filters for supported air-quality and weather layers.
5. Model error monitoring and horizon-by-horizon benchmark visibility for internal analysts.
6. Calibrated risk explanation or feature contribution analysis only if the model and method support it.
7. Duplicate citizen-report clustering and multi-source corroboration without treating crowd evidence as measurement truth.
8. Multilingual responses/UI (English, Hindi, Marathi) with tests and faithful source attribution.
9. Graceful cached stale-data view, if cache behavior is safe, clearly timestamped and not represented as live.
10. Performance optimization, pagination, debounced map queries, image-size handling, and backend rate limits as measurements justify.

Do not add features just for quantity. Every advanced feature must have a defined user problem, evidence/data dependency, privacy/safety implications, acceptance criteria, and measurable test.

### Acceptance gate

- Each selected feature is connected to real APIs/data, tested, accessible, and documented.
- No fabricated notification delivery, prediction quality, or multilingual fidelity claims.
- Core citizen-report and authority workflows continue to pass regression tests.

**Deliverable:** `PHASE_10_ADVANCED_FEATURES_REPORT.md`.

## Phase 11 — F9 Federated Multi-City Learning (Advanced / gated)

**Goal:** Build a real, auditable federated-learning demonstration only after core phases pass. This feature must not be falsely marked production-ready merely because node screens or coordinator classes exist.

### Scope

- Define the node/coordinator protocol and model/update schema.
- Establish local node identities/configuration for Pune, Mumbai, and Delhi only when independent data and permissions exist.
- Distinguish simulated nodes from real independently executing nodes.
- Implement local training/update generation, aggregation, global model versioning, round history, and evaluation against baseline.
- Never upload or transmit raw citizen reports or private records in the name of federated learning.
- Document threat model, update validation, poisoning/range checks, transport auth, privacy limits, and logging.
- Verify geographic/model domain validity before publishing global predictions; Pune-only F3 validation cannot be generalized merely by federating.
- Test failed/disconnected node, malformed update, partial round, version mismatch, and aggregation failure.

### Acceptance gate

- At least one fully reproducible end-to-end training/aggregation round in an isolated environment.
- Artifacts and metrics show actual updates/aggregation, not just UI animation or hand-written weights.
- Privacy boundaries and simulation-vs-live status are explicit.
- Cross-city evaluation is documented; unsupported cities do not receive misleading validated-model claims.

**Deliverable:** `PHASE_11_FEDERATED_NETWORK_REPORT.md`.

## Phase 12 — Complete System Integration, Security, and Release Candidate

**Goal:** Prove the major features work together rather than only in separate unit tests.

### Required golden paths

**Citizen information path:**
Location → current weather/forecast source lookup → official warnings → air-quality data → supported PM2.5 risk when validated → grounded assistant answer with source/time.

**Citizen report path:**
Citizen submits report/photo → server validation + server-side H3 → persistence → optional Vision analysis → auxiliary evidence → F5 triage → citizen-safe status.

**Authority path:**
Valid multi-source evidence → candidate triage → case queue → acknowledgement → assignment → inspection → observed verification → resolution → safe audit/status updates.

**Operations path:**
Provider ingestion → provenance/freshness → H3 consistency → model readiness → admin visibility → truthful failure state on provider/model outage.

### Quality work

- Re-run all existing frontend, backend, AI, and relevant integration tests.
- Test authentication/authorization across the actual route inventory.
- Test data/provider failures, missing keys/models, slow requests, malformed inputs, stale caches, and unavailable services.
- Test database transaction boundaries, concurrency, duplicate submissions, and state synchronization in an isolated environment.
- Validate CORS, rate limits, upload limits, secret redaction, PII access, prompt injection, and server-side input validation.
- Run dependency/security checks using available safe tooling.
- Measure API/inference latency and avoid unbounded requests/retries.
- Test desktop/mobile UX, accessibility, loading/error states, and core navigation.
- Verify documentation, environment-variable matrix, backup/restore expectations, operational runbook, known limitations, and rollback strategy.

### Acceptance gate

- All critical E2E paths pass in a staging/local integrated environment.
- No P0 security or data-integrity findings remain open.
- Every remaining limitation is documented; mocks are clearly separated from live verification.
- Release candidate has reproducible build instructions and known-good commit/hash.

**Deliverable:** `PHASE_12_RELEASE_CANDIDATE_REPORT.md`.

## Phase 13 — Dedicated Cloud Deployment Day and Production Gate

**This phase is intentionally scheduled after feature development.** Reserve the user's planned dedicated deployment day, but do not promise all live integration work will finish in one day if external access, provider permissions, hosting limits, or identity credentials block it.

### Deployment order

1. Freeze/record the release-candidate commit, cleanly document any working-tree changes, and run required build/test checks.
2. Reconfirm Supabase project is `ACTIVE`, verify the correct project/host, inspect migration history read-only, and confirm backup/recovery options. Never run unreviewed migrations automatically.
3. Deploy `ai-service/` as a container on the approved host with sufficient memory. Verify loaded artifacts, per-operation readiness, and genuine F3/F4 inference; test F5/F6 contracts and clearly report missing Gemini configuration.
4. Deploy the Java backend container with the approved production profile, safe `PORT`, DB connection settings, and correct `AI_SERVICE_URL`. Verify read-only DB connectivity, Flyway state, public read APIs, and AI HTTP integration.
5. Configure the Vercel frontend's `VITE_API_BASE_URL` using the actual backend URL and canonical single `/api/v1` prefix. Build/redeploy and verify real browser API calls return JSON rather than SPA HTML.
6. Verify exact CORS origins, HTTPS, auth issuer/audience/role mapping, anonymous/unauthorized denials, and a real authorized authority test identity.
7. Verify real Gemini provider-backed vision/explanation only when a valid securely configured key is available.
8. Run production-safe read/health/inference checks. Do not send production citizen reports or mutate authority cases just to test the release.
9. Review logs for secrets/PII, confirm error handling, and publish an exact PASS/FAIL/BLOCKED release checklist.

### Required external access

- Supabase project owner/operator access for current project status and recovery.
- Approved container-host access for AI and Java services; no unapproved paid resource creation.
- Vercel project access to configure the frontend build environment.
- Production identity-provider configuration and authorized test identity.
- Gemini key/provider configuration if live Vision/explanation is a release requirement.

### Production gate

Production is PASS only when frontend → backend, backend → database, and backend → AI-service paths work with real hosted components, model readiness and actual inference are verified, the exact allowed origin works in the browser, auth is real and fail-closed, and the key end-to-end product journeys pass. Missing access means BLOCKED, not PASS.

**Deliverable:** `PHASE_13_DEPLOYMENT_RELEASE_REPORT.md`.

## 3. Phase-by-phase working agreement

For every phase prompt:

1. Read this roadmap and current phase reports first.
2. Inspect the actual repository, branch, commit, and working tree before changing files.
3. Revalidate old findings rather than assuming they still hold.
4. State in-scope/out-of-scope items and exact acceptance criteria.
5. Implement only the current phase, preserving existing working behavior.
6. Add/update tests for happy path, invalid input, permission failure, unavailable dependency, and regression where applicable.
7. Run tests and report exact commands/results, separating mocks from local and hosted runtime evidence.
8. Review the diff, avoid accidental user-change loss, and do not perform unauthorized production mutations.
9. Create the named phase report with evidence, limitations, and `PASS`, `FAIL`, or `BLOCKED`.
10. Stop and await review. Do not automatically start the next phase.

## 4. Dependency map

- Phase 3 establishes trustworthy inputs/outputs for Phase 4–10.
- Phase 4 builds grounded backend tools and conversational orchestration; Phase 5 integrates that into citizen UX.
- Phase 6 supplies auxiliary citizen evidence; Phase 7 fuses validated evidence and applies safe triage.
- Phase 8 consumes qualifying candidates and completes authority workflow.
- Phase 9 exposes operational status and monitoring coverage/recommendations.
- Phase 10 adds high-value advanced user capabilities on top of reliable APIs.
- Phase 11 (F9) is gated by privacy, reliable core services, independent city data, and model validation.
- Phase 12 validates complete integrated journeys and security.
- Phase 13 deploys the frozen release candidate and verifies real cloud operation.

## 5. Never-forget checklist

- [ ] Existing F1 real air-quality data and source/freshness behavior.
- [ ] Existing F2 weather observations, H3 Resolution 8, map/cell APIs; actual forecast remains a separate capability.
- [ ] F3 hotspot model readiness, Pune domain limits, calibrated probability/threshold provenance.
- [ ] F4 +1h/+3h/+6h model-vs-persistence evaluation, uncertainty intervals, no fake fallback, honest model status.
- [ ] F5 evidence validation, grounded explanation, distinct risk/evidence/model/vision metrics, conservative triage.
- [ ] F6 citizen reports, image handling, server-computed H3, auxiliary evidence, safe report status.
- [ ] F7 synchronized event/alert/inspection/action lifecycle and server-enforced roles.
- [ ] F8 monitoring coverage gap and transparent station recommendation with a rationale.
- [ ] F9 real auditable federated round or explicitly labeled prototype/simulation.
- [ ] Citizen conversational weather/environment assistant and sourced official warnings.
- [ ] Separate Citizen, Authority/Field Team, and Admin experiences inside the same app/shared backend.
- [ ] English/Hindi/Marathi support only where actually implemented/tested.
- [ ] No fake data, fabricated confidence, fabricated notifications, unsupported nationwide claims, or mock-as-production claims.
- [ ] Security, privacy, observability, error states, tests, documentation, and final deployment day.
