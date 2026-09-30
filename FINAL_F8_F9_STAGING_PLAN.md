# AeroSentinel — Final F8/F9 Pre-Commit Staging Plan

**Date**: September 30, 2026  
**Branch**: eature/f8-f9-integration  
**Current Base / HEAD**: 7084de539e10737ee14bf51792036e522018039 (origin/main, tag F7-STABLE-BEFORE-F8-F9)  
**Safety Stash**: stash@{0} (70dca4ee9e5c2ba0f833bda0110abdc3da6a235b)  
**Status**: READ-ONLY AUDIT & STAGING PLAN GENERATION ONLY (No commits, no pushes, no additional staging)  

---

## 1. Repository State

| Property | Current State | Audit Confirmation |
|---|---|:---:|
| Active Git Branch | eature/f8-f9-integration | **PASS** |
| HEAD Commit | 7084de539e10737ee14bf51792036e522018039 | **PASS** |
| Upstream Base | origin/main (7084de) | **PASS** |
| Protected Tag | F7-STABLE-BEFORE-F8-F9 (7084de) | **PASS** |
| Safety Stash | stash@{0} (70dca4ee9e5c2ba0f833bda0110abdc3da6a235b) | **PASS** |
| Merge Conflict Status | 0 unmerged / UU files | **PASS** |
| PollutionMap.tsx Status | Conflict resolved; staged intentionally | **PASS** |
| Staged Entries | 48 (46 modifications + 2 deletions) | **PASS** |
| Untracked Files | 181 (backend, federated, frontend, tests, storage) | **PASS** |

---

## 2. Currently Staged Files

The following 48 entries are currently staged in the Git index (restored cleanly from stash@{0} index plus the resolved PollutionMap.tsx):

| Path | Category | Reason | Safe to Stage |
|------|----------|--------|---------------|
| ai-service/app/services/gemini_pipeline.py | INTEGRATION | Grounding fallback prepending H3 cell context to analyst summary | **YES** |
| backend/src/main/java/com/aerosentinel/action/AuthorityAction.java | INTEGRATION | Added compatibility getters/setters (getActionId, getEventId, getNotes, getResult) | **YES** |
| backend/src/main/java/com/aerosentinel/alert/Alert.java | INTEGRATION | Added compatibility getters/setters (getAlertId, getH3CellId, getEvidenceSummary, getClosedAt) | **YES** |
| backend/src/main/java/com/aerosentinel/alert/AlertRepository.java | INTEGRATION | Added findByEventIdOrderByCreatedAtDesc method query | **YES** |
| backend/src/main/java/com/aerosentinel/citizen/CitizenReport.java | INTEGRATION | Added compatibility getters getReportId, getUpdatedAt | **YES** |
| backend/src/main/java/com/aerosentinel/event/PollutionEvent.java | INTEGRATION | Added compatibility getters/setters (getEventId, getRiskScore, getConfidence, setEndedAt) | **YES** |
| backend/src/main/java/com/aerosentinel/event/PollutionEventRepository.java | INTEGRATION | Added default method findByEventId mapping to findByEventCode | **YES** |
| backend/src/main/java/com/aerosentinel/event/PollutionEventStatus.java | INTEGRATION | Added canTransitionTo and canTransition lifecycle methods | **YES** |
| backend/src/main/java/com/aerosentinel/evidence/EvidenceOrchestrationService.java | INTEGRATION | Replaced @Transactional(readOnly = true) with @Transactional on fallback orchestration | **YES** |
| backend/src/main/java/com/aerosentinel/federated/FederatedController.java | F9 | Deleted 0-byte initial scaffold stub; replaced by modular REST controllers | **YES** |
| backend/src/main/java/com/aerosentinel/federated/FederatedNode.java | F9 | Enhanced JPA entity mapping federated_nodes table | **YES** |
| backend/src/main/java/com/aerosentinel/federated/FederatedService.java | F9 | Deleted 0-byte initial scaffold stub; replaced by modular services | **YES** |
| backend/src/main/java/com/aerosentinel/federated/ModelUpdate.java | F9 | Enhanced JPA entity mapping model_updates table | **YES** |
| backend/src/main/java/com/aerosentinel/hotspot/AiServiceHotspotClient.java | INTEGRATION | Added HTTP_1_1 client version and artifact fallback path resolution | **YES** |
| backend/src/main/java/com/aerosentinel/monitoring/MonitoringController.java | F8 | REST controller for monitoring coverage, priority, and recommendations | **YES** |
| backend/src/main/java/com/aerosentinel/monitoring/MonitoringPriority.java | F8 | Priority level enum (LOW, MEDIUM, HIGH) | **YES** |
| backend/src/main/java/com/aerosentinel/monitoring/MonitoringService.java | F8 | Canonical service computing station distance, normalized priority, and recommendations | **YES** |
| backend/src/main/resources/application-dev.yml | INTEGRATION | Set hibernate.ddl-auto: update for development profile | **YES** |
| backend/src/main/resources/application.yml | INTEGRATION | Added allow-bean-definition-overriding: true and app.monitoring.priority.* weights | **YES** |
| backend/src/test/java/com/aerosentinel/evidence/EvidenceIntegrationTest.java | TEST | Assertions relaxed for real ML model outputs and non-null predictionId filter | **YES** |
| backend/src/test/java/com/aerosentinel/hotspot/HotspotIntegrationTest.java | TEST | Added f3_classifier_v1 to permitted version assertions | **YES** |
| federated/clients/delhi/client.py | F9 | Delhi DPCC municipal network client implementation | **YES** |
| federated/clients/delhi/local_data.py | F9 | Delhi 36-feature local data provider | **YES** |
| federated/clients/delhi/trainer.py | F9 | Delhi local Ridge training harness | **YES** |
| federated/clients/mumbai/client.py | F9 | Mumbai BMC municipal network client implementation | **YES** |
| federated/clients/mumbai/local_data.py | F9 | Mumbai 36-feature local data provider | **YES** |
| federated/clients/mumbai/trainer.py | F9 | Mumbai local Ridge training harness | **YES** |
| federated/clients/pune/client.py | F9 | Pune municipal network client implementation | **YES** |
| federated/clients/pune/local_data.py | F9 | Pune 36-feature local data provider | **YES** |
| federated/clients/pune/trainer.py | F9 | Pune local Ridge training harness | **YES** |
| federated/coordinator/aggregator.py | F9 | Sample-weighted Federated Averaging (FedAvg) aggregation implementation | **YES** |
| federated/coordinator/coordinator.py | F9 | Federated orchestration engine managing round lifecycles | **YES** |
| federated/coordinator/model_registry.py | F9 | Model registry tracking active models, catalog, and file persistence | **YES** |
| federated/coordinator/round_manager.py | F9 | In-memory round state machine managing quorums and node submissions | **YES** |
| federated/models/global_model.py | F9 | Global consensus model definition, evaluation metrics, and joblib serialisation | **YES** |
| federated/models/local_model.py | F9 | Local municipal Ridge model definition and 36-feature evaluation | **YES** |
| frontend/package-lock.json | DO_NOT_COMMIT | Platform metadata change (libc stripped on Windows); no dependency version changes | **YES** |
| frontend/src/App.tsx | INTEGRATION | Added /monitoring route rendering MonitoringDashboard | **YES** |
| frontend/src/components/federated/CityNodeCard.tsx | F9 | Node card component displaying node status, model version, and heartbeat button | **YES** |
| frontend/src/components/federated/FederatedStatus.tsx | F9 | Overview banner component displaying network synchronization and quorum status | **YES** |
| frontend/src/components/layout/Sidebar.tsx | INTEGRATION | Added Monitoring Priority navigation item (/monitoring) with Radio icon | **YES** |
| frontend/src/components/map/MonitoringCoverageLayer.tsx | F8 | Hexagonal H3 layer displaying color-coded monitoring priority and coverage gaps | **YES** |
| frontend/src/components/map/PollutionMap.tsx | INTEGRATION | Added MonitoringCoverageLayer, layer toggle, priority legend, and retry status | **YES** |
| frontend/src/pages/federated/FederatedNetwork.tsx | F9 | Complete React page for Multi-City Federated Network Console | **YES** |
| frontend/src/pages/public/PollutionMap.tsx | INTEGRATION | Added toolbar toggle button for Monitoring Gaps | **YES** |
| frontend/src/services/federated.service.ts | F9 | Axios API service for federated nodes, rounds, models, and FedAvg trigger | **YES** |
| frontend/src/services/monitoring.service.ts | F8 | Axios API service for monitoring recommendations and H3 cell details | **YES** |
| frontend/src/types/index.ts | INTEGRATION | Barrel file exporting canonical ./federated and ./monitoring type interfaces | **YES** |

---

## 3. F7 Baseline Files (Omitted Baseline F5–F7 Files)

These 45 legitimate backend Java implementation files were created during F5–F7 but omitted from commit 77ebff9. They are verified required dependencies for the baseline F5–F7 services:

| Path | Category | Reason | Safe to Stage |
|------|----------|--------|---------------|
| backend/src/main/java/com/aerosentinel/action/ActionType.java | BASELINE_F7 | Action type enum for authority action workflow (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/action/AuthorityActionController.java | BASELINE_F7 | REST controller for recording authority field actions (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/action/AuthorityActionRepository.java | BASELINE_F7 | JPA repository for authority actions (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/action/AuthorityActionService.java | BASELINE_F7 | Business service for authority actions (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/alert/AlertMapper.java | BASELINE_F7 | Mapper for Alert to AlertResponse DTO (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/alert/AlertProperties.java | BASELINE_F7 | Configuration properties for alert thresholds (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/alert/AlertSeverity.java | BASELINE_F7 | Alert severity enum (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/alert/AlertStatus.java | BASELINE_F7 | Alert lifecycle status enum (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/authority/AssignmentService.java | BASELINE_F7 | Service for assigning field inspection teams (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/authority/AuthorityBriefService.java | BASELINE_F7 | Generates operational briefing summaries for field teams (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/authority/AuthorityQueueController.java | BASELINE_F7 | REST controller for operational authority triage queue (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/authority/AuthorityQueueService.java | BASELINE_F7 | Service managing authority operational queue items (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/authority/AuthorityTeam.java | BASELINE_F7 | Entity representing municipal authority field teams (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/authority/AuthorityTeamRepository.java | BASELINE_F7 | JPA repository for field teams (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/citizen/CitizenReportCategory.java | BASELINE_F7 | Enum for citizen report categories (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/citizen/CitizenReportMapper.java | BASELINE_F7 | Mapper for citizen reports to DTO (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/citizen/CitizenReportStatus.java | BASELINE_F7 | Status enum for citizen reports (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/citizen/GeminiAnalysisStatus.java | BASELINE_F7 | Status enum for Gemini vision analysis (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/citizen/InvalidImageException.java | BASELINE_F7 | Validation exception for invalid citizen uploads (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/citizen/PhotoStorageException.java | BASELINE_F7 | Storage exception for citizen photos (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/dto/alert/AlertResponse.java | BASELINE_F7 | DTO response for alerts (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/dto/alert/EvaluateAlertRequest.java | BASELINE_F7 | DTO request for alert evaluation (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/dto/authority/ActionResponse.java | BASELINE_F7 | DTO response for authority actions (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/dto/authority/AssignmentResponse.java | BASELINE_F7 | DTO response for team assignments (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/dto/authority/AuthorityBriefResponse.java | BASELINE_F7 | DTO response for operational briefs (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/dto/authority/AuthorityQueueItemResponse.java | BASELINE_F7 | DTO response for queue items (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/dto/authority/CompleteInspectionRequest.java | BASELINE_F7 | DTO request to complete inspection (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/dto/authority/CreateInspectionRequest.java | BASELINE_F7 | DTO request to schedule inspection (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/dto/authority/InspectionResponse.java | BASELINE_F7 | DTO response for inspection (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/dto/authority/ResolveEventRequest.java | BASELINE_F7 | DTO request to resolve event (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/dto/citizen/AttachEvidenceRequest.java | BASELINE_F7 | DTO request to attach evidence (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/dto/citizen/CreateCitizenReportRequest.java | BASELINE_F7 | DTO request to submit citizen report (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/dto/citizen/GeminiVisionResponse.java | BASELINE_F7 | DTO response for Gemini vision results (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/dto/citizen/ReportStatusResponse.java | BASELINE_F7 | DTO response for report status (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/dto/event/CreateEventRequest.java | BASELINE_F7 | DTO request to create pollution event (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/dto/event/EventDetailResponse.java | BASELINE_F7 | DTO response with evidence lineage (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/dto/event/EventEvidenceDto.java | BASELINE_F7 | DTO representing individual evidence items (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/dto/event/PollutionEventResponse.java | BASELINE_F7 | DTO response for pollution events (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/event/EventEvidenceRepository.java | BASELINE_F7 | JPA repository for event evidence (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/event/EventEvidenceService.java | BASELINE_F7 | Business service for event evidence (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/event/EvidenceType.java | BASELINE_F7 | Enum for evidence types (AIR, WEATHER, HOTSPOT, FORECAST, CITIZEN) | **YES** |
| backend/src/main/java/com/aerosentinel/event/PollutionEventMapper.java | BASELINE_F7 | Mapper for events and evidence to DTOs (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/inspection/InspectionStatus.java | BASELINE_F7 | Status enum for inspections (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/integration/ai/AiServiceException.java | BASELINE_F7 | Base exception for AI service client errors (untracked baseline) | **YES** |
| backend/src/main/java/com/aerosentinel/integration/ai/dto/PythonVisionResponse.java | BASELINE_F7 | DTO response from Python vision inference CLI (untracked baseline) | **YES** |

---

## 4. F0–F7 Integration Files (Required Integration Changes)

These 17 files contain necessary, backward-compatible integration changes made to existing F0–F7 code to support F8/F9 features:

| Path | Category | Reason | Safe to Stage |
|------|----------|--------|---------------|
| ai-service/app/services/gemini_pipeline.py | INTEGRATION | Grounding fallback prepending H3 cell context to analyst summary | **YES** |
| backend/src/main/java/com/aerosentinel/action/AuthorityAction.java | INTEGRATION | Added compatibility getters/setters (getActionId, getEventId, getNotes, getResult) | **YES** |
| backend/src/main/java/com/aerosentinel/alert/Alert.java | INTEGRATION | Added compatibility getters/setters (getAlertId, getH3CellId, getEvidenceSummary, getClosedAt) | **YES** |
| backend/src/main/java/com/aerosentinel/alert/AlertRepository.java | INTEGRATION | Added findByEventIdOrderByCreatedAtDesc method query | **YES** |
| backend/src/main/java/com/aerosentinel/citizen/CitizenReport.java | INTEGRATION | Added compatibility getters getReportId, getUpdatedAt | **YES** |
| backend/src/main/java/com/aerosentinel/event/PollutionEvent.java | INTEGRATION | Added compatibility getters/setters (getEventId, getRiskScore, getConfidence, setEndedAt) | **YES** |
| backend/src/main/java/com/aerosentinel/event/PollutionEventRepository.java | INTEGRATION | Added default method findByEventId mapping to findByEventCode | **YES** |
| backend/src/main/java/com/aerosentinel/event/PollutionEventStatus.java | INTEGRATION | Added canTransitionTo and canTransition lifecycle methods | **YES** |
| backend/src/main/java/com/aerosentinel/evidence/EvidenceOrchestrationService.java | INTEGRATION | Replaced @Transactional(readOnly = true) with @Transactional on fallback orchestration | **YES** |
| backend/src/main/java/com/aerosentinel/hotspot/AiServiceHotspotClient.java | INTEGRATION | Added HTTP_1_1 client version and artifact fallback path resolution | **YES** |
| backend/src/main/resources/application-dev.yml | INTEGRATION | Set hibernate.ddl-auto: update for development profile | **YES** |
| backend/src/main/resources/application.yml | INTEGRATION | Added allow-bean-definition-overriding: true and app.monitoring.priority.* weights | **YES** |
| frontend/src/App.tsx | INTEGRATION | Added /monitoring route rendering MonitoringDashboard | **YES** |
| frontend/src/components/layout/Sidebar.tsx | INTEGRATION | Added Monitoring Priority navigation item (/monitoring) with Radio icon | **YES** |
| frontend/src/components/map/PollutionMap.tsx | INTEGRATION | Added MonitoringCoverageLayer, layer toggle, priority legend, and retry status | **YES** |
| frontend/src/pages/public/PollutionMap.tsx | INTEGRATION | Added toolbar toggle button for Monitoring Gaps | **YES** |
| frontend/src/types/index.ts | INTEGRATION | Barrel file exporting canonical ./federated and ./monitoring type interfaces | **YES** |

---

## 5. F8 Files (Hyperlocal Monitoring Priority & Coverage)

These 15 files comprise the canonical Feature 8 implementation across backend services, DTOs, and frontend components:

| Path | Category | Reason | Safe to Stage |
|------|----------|--------|---------------|
| backend/src/main/java/com/aerosentinel/monitoring/MonitoringController.java | F8 | REST controller for monitoring coverage, priority, and recommendations | **YES** |
| backend/src/main/java/com/aerosentinel/monitoring/MonitoringPriority.java | F8 | Priority level enum (LOW, MEDIUM, HIGH) | **YES** |
| backend/src/main/java/com/aerosentinel/monitoring/MonitoringPriorityConfig.java | F8 | Spring @ConfigurationProperties class for priority formula weights | **YES** |
| backend/src/main/java/com/aerosentinel/monitoring/MonitoringPriorityService.java | F8 | Domain service computing priority score and classification | **YES** |
| backend/src/main/java/com/aerosentinel/monitoring/MonitoringRecommendationType.java | F8 | Canonical recommendation enum (ROUTINE, TARGETED, MOBILE_SENSOR, FIELD_VERIFICATION) | **YES** |
| backend/src/main/java/com/aerosentinel/monitoring/MonitoringService.java | F8 | Canonical service computing station distance, normalized priority, and recommendations | **YES** |
| backend/src/main/java/com/aerosentinel/monitoring/dto/MonitoringCoverageResponse.java | F8 | Canonical response DTO for station distance and coverage gap flag | **YES** |
| backend/src/main/java/com/aerosentinel/monitoring/dto/MonitoringPriorityResponse.java | F8 | Canonical response DTO for monitoring priority calculation | **YES** |
| backend/src/main/java/com/aerosentinel/monitoring/dto/MonitoringRecommendationResponse.java | F8 | Canonical response DTO for audit lineage and recommendations | **YES** |
| frontend/src/components/map/MonitoringCoverageLayer.tsx | F8 | Hexagonal H3 layer displaying color-coded monitoring priority and coverage gaps | **YES** |
| frontend/src/components/monitoring/MonitoringRecommendationDetailCard.tsx | F8 | Detailed card component displaying audit factors, station distance, and recommendations | **YES** |
| frontend/src/hooks/useMonitoringRecommendations.ts | F8 | Custom React hook fetching and caching monitoring recommendations | **YES** |
| frontend/src/pages/public/MonitoringDashboard.tsx | F8 | Complete React page for Hyperlocal Monitoring Priority Dashboard | **YES** |
| frontend/src/services/monitoring.service.ts | F8 | Axios API service for monitoring recommendations and H3 cell details | **YES** |
| frontend/src/types/monitoring.ts | F8 | TypeScript interfaces for monitoring priority and recommendation contracts | **YES** |

---

## 6. F9 Files (Multi-City Federated Network)

These 82 files comprise the canonical Feature 9 implementation across backend control plane, migrations, municipal node clients (Pune, Mumbai, Delhi), FedAvg coordinator, and React console UI:

| Path | Category | Reason | Safe to Stage |
|------|----------|--------|---------------|
| backend/src/main/java/com/aerosentinel/dto/federated/CreateRoundRequest.java | F9 | REST request DTO to initiate a federated training round | **YES** |
| backend/src/main/java/com/aerosentinel/dto/federated/FederatedNodeResponse.java | F9 | REST response DTO for municipal node status | **YES** |
| backend/src/main/java/com/aerosentinel/dto/federated/FederatedRoundResponse.java | F9 | REST response DTO for round summary | **YES** |
| backend/src/main/java/com/aerosentinel/dto/federated/GlobalModelResponse.java | F9 | REST response DTO for active global model and metrics | **YES** |
| backend/src/main/java/com/aerosentinel/dto/federated/ModelCatalogItemResponse.java | F9 | REST response DTO for model catalog lineage | **YES** |
| backend/src/main/java/com/aerosentinel/dto/federated/ModelUpdateResponse.java | F9 | REST response DTO for submitted local model update | **YES** |
| backend/src/main/java/com/aerosentinel/dto/federated/NodeHeartbeatRequest.java | F9 | REST request DTO for node heartbeat | **YES** |
| backend/src/main/java/com/aerosentinel/dto/federated/RegisterNodeRequest.java | F9 | REST request DTO for node registration | **YES** |
| backend/src/main/java/com/aerosentinel/dto/federated/SubmitModelUpdateRequest.java | F9 | REST request DTO for submitting node updates | **YES** |
| backend/src/main/java/com/aerosentinel/federated/FederatedController.java | F9 | Deleted 0-byte initial scaffold stub; replaced by modular REST controllers | **YES** |
| backend/src/main/java/com/aerosentinel/federated/FederatedGlobalModel.java | F9 | JPA entity mapping federated_global_models table | **YES** |
| backend/src/main/java/com/aerosentinel/federated/FederatedGlobalModelRepository.java | F9 | JPA repository for consensus global models | **YES** |
| backend/src/main/java/com/aerosentinel/federated/FederatedNode.java | F9 | Enhanced JPA entity mapping federated_nodes table | **YES** |
| backend/src/main/java/com/aerosentinel/federated/FederatedNodeController.java | F9 | REST controller for municipal node registration and heartbeat | **YES** |
| backend/src/main/java/com/aerosentinel/federated/FederatedNodeRepository.java | F9 | JPA repository for municipal nodes | **YES** |
| backend/src/main/java/com/aerosentinel/federated/FederatedNodeService.java | F9 | Service for node registration, status transitions, and heartbeat management | **YES** |
| backend/src/main/java/com/aerosentinel/federated/FederatedNodeUpdate.java | F9 | JPA entity mapping federated_node_updates table | **YES** |
| backend/src/main/java/com/aerosentinel/federated/FederatedNodeUpdateRepository.java | F9 | JPA repository for node update weights and metrics | **YES** |
| backend/src/main/java/com/aerosentinel/federated/FederatedRound.java | F9 | JPA entity mapping federated_rounds table | **YES** |
| backend/src/main/java/com/aerosentinel/federated/FederatedRoundController.java | F9 | REST controller for round creation, quorum inspection, and FedAvg trigger | **YES** |
| backend/src/main/java/com/aerosentinel/federated/FederatedRoundRepository.java | F9 | JPA repository for federated training rounds | **YES** |
| backend/src/main/java/com/aerosentinel/federated/FederatedRoundService.java | F9 | Service managing round lifecycle, quorum checks, and FedAvg orchestration | **YES** |
| backend/src/main/java/com/aerosentinel/federated/FederatedService.java | F9 | Deleted 0-byte initial scaffold stub; replaced by modular services | **YES** |
| backend/src/main/java/com/aerosentinel/federated/GlobalModelController.java | F9 | REST controller for retrieving active model and catalog lineage | **YES** |
| backend/src/main/java/com/aerosentinel/federated/GlobalModelService.java | F9 | Service managing global model activation, catalog, and weights persistence | **YES** |
| backend/src/main/java/com/aerosentinel/federated/ModelUpdate.java | F9 | Enhanced JPA entity mapping model_updates table | **YES** |
| backend/src/main/java/com/aerosentinel/federated/ModelUpdateController.java | F9 | REST controller for submitting local model weights and evaluation metrics | **YES** |
| backend/src/main/java/com/aerosentinel/federated/ModelUpdateRepository.java | F9 | JPA repository for model updates | **YES** |
| backend/src/main/java/com/aerosentinel/federated/ModelUpdateService.java | F9 | Service for model update validation and status transitions | **YES** |
| backend/src/main/java/com/aerosentinel/federated/NodeStatus.java | F9 | Enum for node status (ONLINE, OFFLINE, TRAINING) | **YES** |
| backend/src/main/java/com/aerosentinel/federated/RoundStatus.java | F9 | Enum for round lifecycle (CREATED, IN_PROGRESS, AGGREGATING, COMPLETED, FAILED) | **YES** |
| backend/src/main/java/com/aerosentinel/federated/UpdateStatus.java | F9 | Enum for update validation status (RECEIVED, ACCEPTED, REJECTED, AGGREGATED) | **YES** |
| backend/src/main/java/com/aerosentinel/federated/dto/AggregationResponse.java | F9 | Service transfer record for aggregation results | **YES** |
| backend/src/main/java/com/aerosentinel/federated/dto/EvaluationMetricsDto.java | F9 | Service transfer record for validation metrics (MAE, RMSE, ROC-AUC, Brier) | **YES** |
| backend/src/main/java/com/aerosentinel/federated/dto/GlobalModelDto.java | F9 | Service transfer record for global model representation | **YES** |
| backend/src/main/java/com/aerosentinel/federated/dto/NodeUpdateDto.java | F9 | Service transfer record for node weights and sample counts | **YES** |
| backend/src/main/java/com/aerosentinel/federated/dto/RoundDetailResponse.java | F9 | Service transfer record for detailed round state with submitted updates | **YES** |
| backend/src/main/java/com/aerosentinel/federated/dto/RoundResponse.java | F9 | Service transfer record for round summary | **YES** |
| backend/src/main/java/com/aerosentinel/federated/dto/StartRoundRequest.java | F9 | Service transfer record to initialize a round | **YES** |
| backend/src/main/java/com/aerosentinel/federated/dto/UpdateResponse.java | F9 | Service transfer record for update submission acknowledgment | **YES** |
| backend/src/main/resources/db/migration/V18__f9_federated_network.sql | F9 | PostgreSQL schema migration creating federated tables and seeding nodes | **YES** |
| backend/src/main/resources/db/migration/V19__seed_multicity_grid_cells.sql | F9 | Multi-city grid cells seed migration for Mumbai and Delhi | **YES** |
| federated/__init__.py | F9 | Package initializer for federated root package | **YES** |
| federated/clients/__init__.py | F9 | Package initializer for municipal client implementations | **YES** |
| federated/clients/base_client.py | F9 | Abstract base class for municipal federated clients | **YES** |
| federated/clients/delhi/__init__.py | F9 | Package initializer for Delhi client module | **YES** |
| federated/clients/delhi/client.py | F9 | Delhi DPCC municipal network client implementation | **YES** |
| federated/clients/delhi/local_data.py | F9 | Delhi 36-feature local data provider | **YES** |
| federated/clients/delhi/trainer.py | F9 | Delhi local Ridge training harness | **YES** |
| federated/clients/mumbai/__init__.py | F9 | Package initializer for Mumbai client module | **YES** |
| federated/clients/mumbai/client.py | F9 | Mumbai BMC municipal network client implementation | **YES** |
| federated/clients/mumbai/local_data.py | F9 | Mumbai 36-feature local data provider | **YES** |
| federated/clients/mumbai/trainer.py | F9 | Mumbai local Ridge training harness | **YES** |
| federated/clients/network_client.py | F9 | HTTP client communicating with backend control plane REST APIs | **YES** |
| federated/clients/pune/__init__.py | F9 | Package initializer for Pune client module | **YES** |
| federated/clients/pune/client.py | F9 | Pune municipal network client implementation | **YES** |
| federated/clients/pune/local_data.py | F9 | Pune 36-feature local data provider | **YES** |
| federated/clients/pune/trainer.py | F9 | Pune local Ridge training harness | **YES** |
| federated/coordinator/__init__.py | F9 | Package initializer for federated coordinator | **YES** |
| federated/coordinator/aggregator.py | F9 | Sample-weighted Federated Averaging (FedAvg) aggregation implementation | **YES** |
| federated/coordinator/coordinator.py | F9 | Federated orchestration engine managing round lifecycles | **YES** |
| federated/coordinator/model_registry.py | F9 | Model registry tracking active models, catalog, and file persistence | **YES** |
| federated/coordinator/round_manager.py | F9 | In-memory round state machine managing quorums and node submissions | **YES** |
| federated/models/__init__.py | F9 | Package initializer for model architecture definitions | **YES** |
| federated/models/global_model.py | F9 | Global consensus model definition, evaluation metrics, and joblib serialisation | **YES** |
| federated/models/local_model.py | F9 | Local municipal Ridge model definition and 36-feature evaluation | **YES** |
| federated/reset_federated_db.py | F9 | Utility script to reset federated PostgreSQL tables for clean test runs | **YES** |
| federated/run_e2e_federated_orchestration.py | F9 | Executable CLI demonstrating end-to-end multi-round cross-stack federated training | **YES** |
| federated/run_local_nodes_demo.py | F9 | CLI demonstrating local model training across Pune, Mumbai, and Delhi | **YES** |
| federated/run_round_simulation.py | F9 | CLI simulating a complete federated training round without backend dependency | **YES** |
| frontend/src/components/federated/CityNodeCard.tsx | F9 | Node card component displaying node status, model version, and heartbeat button | **YES** |
| frontend/src/components/federated/FederatedStatus.tsx | F9 | Overview banner component displaying network synchronization and quorum status | **YES** |
| frontend/src/components/federated/GlobalModelHero.tsx | F9 | Hero card component displaying active global consensus model and metrics | **YES** |
| frontend/src/components/federated/InitiateRoundModal.tsx | F9 | Modal dialog component to configure and launch a new federated training round | **YES** |
| frontend/src/components/federated/ModelCatalogTable.tsx | F9 | Table component displaying consensus model version catalog and lineage | **YES** |
| frontend/src/components/federated/NodeStatusGrid.tsx | F9 | Grid component displaying participating municipal nodes (Pune, Mumbai, Delhi) | **YES** |
| frontend/src/components/federated/RoundLifecycleManager.tsx | F9 | Round lifecycle management card with quorum progress and trigger button | **YES** |
| frontend/src/components/federated/RoundQuorumProgress.tsx | F9 | Visual quorum progress bar component | **YES** |
| frontend/src/pages/federated/FederatedNetwork.tsx | F9 | Complete React page for Multi-City Federated Network Console | **YES** |
| frontend/src/services/federated.service.ts | F9 | Axios API service for federated nodes, rounds, models, and FedAvg trigger | **YES** |
| frontend/src/types/federated.ts | F9 | TypeScript interfaces for nodes, rounds, models, updates, and metrics | **YES** |
| frontend/src/utils/federatedUtils.ts | F9 | Utility functions formatting percentages, errors, timestamps, and badges | **YES** |

---

## 7. F8/F9 Test Files

These 16 test suites validate F8 monitoring, F9 federated algorithms, cross-stack integration, and updated F3/F5 assertions:

| Path | Category | Reason | Safe to Stage |
|------|----------|--------|---------------|
| backend/src/test/java/com/aerosentinel/evidence/EvidenceIntegrationTest.java | TEST | Assertions relaxed for real ML model outputs and non-null predictionId filter | **YES** |
| backend/src/test/java/com/aerosentinel/federated/FederatedControllersIntegrationTest.java | TEST | Spring Boot MockMvc integration test for all 4 federated REST controllers | **YES** |
| backend/src/test/java/com/aerosentinel/federated/FederatedNodeServiceTest.java | TEST | Unit test suite for federated node registration and heartbeat | **YES** |
| backend/src/test/java/com/aerosentinel/federated/FederatedRoundServiceTest.java | TEST | Unit test suite for round lifecycle and quorum checks | **YES** |
| backend/src/test/java/com/aerosentinel/hotspot/HotspotIntegrationTest.java | TEST | Added f3_classifier_v1 to permitted version assertions | **YES** |
| backend/src/test/java/com/aerosentinel/monitoring/MonitoringControllerIntegrationTest.java | TEST | Spring Boot MockMvc integration test for monitoring REST endpoints | **YES** |
| backend/src/test/java/com/aerosentinel/monitoring/MonitoringPriorityServiceTest.java | TEST | Unit test suite for priority scoring math and weighting formulas | **YES** |
| backend/src/test/java/com/aerosentinel/monitoring/MonitoringPriorityTest.java | TEST | Unit test suite for priority enum classification boundaries | **YES** |
| backend/src/test/java/com/aerosentinel/monitoring/MonitoringRecommendationTest.java | TEST | Unit test suite for recommendation determination logic | **YES** |
| backend/src/test/java/com/aerosentinel/monitoring/MonitoringServiceTest.java | TEST | Unit test suite for coverage calculation, distance normalization, and city recommendations | **YES** |
| federated/tests/test_coordinator.py | TEST | Unit test suite for FedAvg aggregation, model registry, and round manager | **YES** |
| federated/tests/test_e2e_orchestration.py | TEST | End-to-end cross-stack orchestration test suite | **YES** |
| federated/tests/test_local_nodes.py | TEST | Unit test suite for local node models, 36-feature schemas, and trainers | **YES** |
| frontend/src/utils/f8_p5_monitoring_dashboard.test.ts | TEST | Frontend unit test suite for monitoring dashboard components | **YES** |
| frontend/src/utils/f8_p6_h3_monitoring_map.test.ts | TEST | Frontend unit test suite for H3 monitoring map layers | **YES** |
| frontend/src/utils/f9_p6_federated_console.test.ts | TEST | Frontend unit test suite for federated network console UI | **YES** |

---

## 8. Documentation Files

These 11 engineering audit reports document the architecture, math, test passes, and verification of F8 and F9:

| Path | Category | Reason | Safe to Stage |
|------|----------|--------|---------------|
| F8_P2_NEAREST_STATION_REPORT.md | DOC | Engineering audit report for F8 Phase 2 (Nearest Station Distance) | **YES** |
| F8_P3_MONITORING_PRIORITY_REPORT.md | DOC | Engineering audit report for F8 Phase 3 (Priority Scoring Formula) | **YES** |
| F8_P4_MONITORING_RECOMMENDATION_REPORT.md | DOC | Engineering audit report for F8 Phase 4 (Monitoring Recommendations) | **YES** |
| F8_P5_MONITORING_DASHBOARD_REPORT.md | DOC | Engineering audit report for F8 Phase 5 (Monitoring Dashboard UI) | **YES** |
| F8_P6_H3_MONITORING_MAP_REPORT.md | DOC | Engineering audit report for F8 Phase 6 (H3 Spatial Map Layer) | **YES** |
| F9_P1_FEDERATED_NETWORK_AUDIT_REPORT.md | DOC | Engineering audit report for F9 Phase 1 (Federated Network Architecture) | **YES** |
| F9_P2_LOCAL_NODES_REPORT.md | DOC | Engineering audit report for F9 Phase 2 (Municipal Local Node Engines) | **YES** |
| F9_P3_COORDINATOR_REPORT.md | DOC | Engineering audit report for F9 Phase 3 (FedAvg Coordinator & Registry) | **YES** |
| F9_P4_BACKEND_CONTROL_PLANE_REPORT.md | DOC | Engineering audit report for F9 Phase 4 (Spring Boot Control Plane) | **YES** |
| F9_P5_E2E_ORCHESTRATION_REPORT.md | DOC | Engineering audit report for F9 Phase 5 (Cross-Stack E2E Orchestration) | **YES** |
| F9_P6_FEDERATED_CONSOLE_REPORT.md | DOC | Engineering audit report for F9 Phase 6 (React Federated Console UI) | **YES** |

---

## 9. DO NOT STAGE Files (Blocklist)

These 48 files must NEVER be staged or committed. They contain secrets, scratch scripts, binary model weights, simulated runtime payloads, duplicate JPA entities, or platform build artifacts:

| Path | Category | Reason | Safe to Stage |
|------|----------|--------|---------------|
| PRE_COMMIT_F8_F9_CHANGE_CLASSIFICATION.md | AUDIT_WORKING_DOC | Pre-commit classification working document; exclude from feature commit | **NO** |
| ai-service/ml/models\artifacts\forecast_regressors_v1.joblib | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| ai-service/ml/models\artifacts\hotspot_classifier_v1.joblib | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| ai-service/ml/models\artifacts\metadata_v1.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| ai-service/ml/models\artifacts\sample_event_output.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| backend/fix_service.py | SCRATCH | Temporary scratch Python script used during development | **NO** |
| backend/src/main/java/com/aerosentinel/citizen/CitizenReportResponse.java | SUSPICIOUS | Mislocated file declaring package com.aerosentinel.dto.citizen; inside com/aerosentinel/citizen/ | **NO** |
| backend/src/main/java/com/aerosentinel/citizen/GeminiAnalysis.java | SUSPICIOUS | Duplicate JPA entity mapping @Table(name = "gemini_analyses") while model/GeminiAnalysis.java is tracked | **NO** |
| backend/src/main/java/com/aerosentinel/citizen/GeminiAnalysisRepository.java | SUSPICIOUS | Duplicate repository for gemini_analyses table | **NO** |
| backend/src/main/java/com/aerosentinel/dto/authority/AssignTeamRequest.java | SUSPICIOUS | Duplicate DTO with String teamId (differs from inspection/AssignTeamRequest.java UUID) | **NO** |
| backend/src/main/java/com/aerosentinel/dto/authority/RecordActionRequest.java | SUSPICIOUS | Duplicate DTO (has result field; differs from dto/action/RecordActionRequest.java) | **NO** |
| backend/src/main/java/com/aerosentinel/dto/citizen/CitizenReportResponse.java | SUSPICIOUS | 0-byte empty file | **NO** |
| backend/src/main/java/com/aerosentinel/dto/monitoring/GeoJsonFeatureCollection.java | SUSPICIOUS | Unused prototype GeoJSON DTO (superseded by canonical F8 DTOs) | **NO** |
| backend/src/main/java/com/aerosentinel/dto/monitoring/MonitoringCoverageDto.java | SUSPICIOUS | Unused prototype coverage DTO (superseded by MonitoringCoverageResponse) | **NO** |
| backend/src/main/java/com/aerosentinel/dto/monitoring/MonitoringPriorityResponse.java | SUSPICIOUS | Duplicate flat DTO (superseded by monitoring.dto.MonitoringPriorityResponse) | **NO** |
| backend/src/main/java/com/aerosentinel/event/EventEvidence.java | SUSPICIOUS | Duplicate JPA entity mapping @Table(name = "event_evidence") | **NO** |
| backend/src/main/java/com/aerosentinel/hotspot/HotspotPredictionRepository.java | SUSPICIOUS | Duplicate unused repository interface (canonical is HotspotRepository.java) | **NO** |
| backend/src/main/java/com/aerosentinel/monitoring/MonitoringCoverage.java | SUSPICIOUS | Unused prototype entity/record (canonical is MonitoringCoverageResponse) | **NO** |
| backend/src/main/java/com/aerosentinel/monitoring/MonitoringMapper.java | SUSPICIOUS | Unused prototype mapper class (canonical is direct response construction) | **NO** |
| backend/src/main/java/com/aerosentinel/monitoring/MonitoringProperties.java | SUSPICIOUS | Duplicate prototype properties class (canonical is MonitoringPriorityConfig) | **NO** |
| backend/src/main/java/com/aerosentinel/monitoring/RecommendationType.java | SUSPICIOUS | Duplicate prototype recommendation enum | **NO** |
| backend/src/main/java/com/aerosentinel/sensor/MonitoringStationRepository.java | SUSPICIOUS | Duplicate repository for monitoring stations (canonical is SensorRepository.java) | **NO** |
| backend/src/main/java/com/aerosentinel/sensor/StationDistanceService.java | SUSPICIOUS | Unused prototype service (canonical distance logic is inside MonitoringService.java) | **NO** |
| frontend/package-lock.json | BUILD_METADATA | Incidental Windows libc platform metadata removal; zero package changes | **NO** |
| scripts/inspect_db.py | SECRET / SCRATCH | Plaintext database credentials and temporary foreign key queries | **NO** |
| storage\models\global\global-v1.joblib | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\global\global-v2.joblib | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\global\global-v3.joblib | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\updates\delhi_round-001.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\updates\delhi_sim-r1-5641d1.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\updates\delhi_sim-r1-8b3ce7.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\updates\delhi_sim-r1-a08b1c.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\updates\mumbai_round-001.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\updates\mumbai_round-002.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\updates\mumbai_sim-r1-5641d1.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\updates\mumbai_sim-r1-8b3ce7.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\updates\mumbai_sim-r1-a08b1c.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\updates\mumbai_sim-r2-0e19c9.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\updates\mumbai_sim-r2-4548f3.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\updates\mumbai_sim-r2-641d6a.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\updates\pune_round-001.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\updates\pune_round-002.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\updates\pune_sim-r1-5641d1.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\updates\pune_sim-r1-8b3ce7.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\updates\pune_sim-r1-a08b1c.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\updates\pune_sim-r2-0e19c9.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\updates\pune_sim-r2-4548f3.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |
| storage\models\updates\pune_sim-r2-641d6a.json | SUSPICIOUS / PROTOTYPE | Suspicious or duplicate prototype file | **NO** |

---

## 10. Ambiguous Files (Requiring Pre-Commit Code Resolution)

The following 5 files require explicit code remediation before any staging or committing can safely occur:

| Path | Category | Reason | Safe to Stage |
|------|----------|--------|---------------|
| backend/src/main/java/com/aerosentinel/dto/authority/AssignTeamRequest.java | SUSPICIOUS / AMBIGUOUS | Duplicate DTO declaring teamId as String; overlaps with inspection/AssignTeamRequest.java (UUID teamId). Used by AssignmentService. | **NO** |
| backend/src/main/java/com/aerosentinel/dto/authority/RecordActionRequest.java | SUSPICIOUS / AMBIGUOUS | Duplicate DTO with extra result field; overlaps with dto/action/RecordActionRequest.java. Used by AuthorityActionService. | **NO** |
| backend/src/main/java/com/aerosentinel/citizen/CitizenReportResponse.java | SUSPICIOUS / AMBIGUOUS | Mislocated file declaring package com.aerosentinel.dto.citizen inside citizen/ folder. Used by CitizenReportMapper. | **NO** |
| backend/src/main/java/com/aerosentinel/dto/citizen/CitizenReportResponse.java | SUSPICIOUS / AMBIGUOUS | Empty 0-byte file located in canonical dto/citizen directory. | **NO** |
| PRE_COMMIT_F8_F9_CHANGE_CLASSIFICATION.md | DOC / AMBIGUOUS | Untracked audit report generated during pre-commit audit; not in original 228-row inventory. | **NO** |

### Required Remediation for Ambiguous Files:

1. **AssignTeamRequest.java**: Reconcile com.aerosentinel.dto.authority.AssignTeamRequest (String teamId) with com.aerosentinel.dto.inspection.AssignTeamRequest (UUID teamId) before staging.
2. **RecordActionRequest.java**: Reconcile com.aerosentinel.dto.authority.RecordActionRequest (with result field) with com.aerosentinel.dto.action.RecordActionRequest before staging.
3. **CitizenReportResponse.java**: Delete the 0-byte stub ackend/src/main/java/com/aerosentinel/dto/citizen/CitizenReportResponse.java and relocate ackend/src/main/java/com/aerosentinel/citizen/CitizenReportResponse.java into package directory dto/citizen/.
4. **PRE_COMMIT_F8_F9_CHANGE_CLASSIFICATION.md**: Decide whether to commit as repository documentation or preserve in local tracking.

---

## 11. Exact File Counts

| Group | Category | File Count | Safe to Stage |
|---|---|:---:|:---:|
| Group A | Omitted F5–F7 Baseline Files | 45 | YES |
| Group B | Required F0–F7 Integration Changes | 17 | YES |
| Group C | Feature 8 Implementation | 15 | YES |
| Group D | Feature 9 Implementation | 82 | YES (80 files + 2 deletions) |
| Group E | F8/F9 Automated Test Suites | 16 | YES |
| Group F | Engineering Audit Reports (Documentation) | 11 | YES |
| Blocklist | DO NOT STAGE (Secrets, Scratch, Binaries, Duplicates) | 48 | **NO** |
| Ambiguous | Files Requiring Code Remediation | 5 | **NO** |

---

## 12. Total Proposed Staged Count

- **Total Proposed Staged Entries**: **186 entries**
  - 45 Omitted F5–F7 Baseline files
  - 17 Required F0–F7 Integration changes (including staged PollutionMap.tsx)
  - 15 Feature 8 implementation files
  - 82 Feature 9 implementation files (80 files on disk + 2 staged deletions)
  - 16 Automated test suite files
  - 11 Engineering phase audit documentation reports
- **Total Blocklist (DO NOT STAGE)**: **48 files**
- **Total Ambiguous Files**: **5 files**

