# AeroSentinel — Ambiguous File Reconciliation Report

**Audit Mode**: Safe Reconciliation (Read-Only Code Integrity)  
**Date**: September 30, 2026  
**Active Branch**: `feature/f8-f9-integration`  
**Base Commit / HEAD**: `e7084de539e10737ee14bf51792036e522018039` (`origin/main`, tag `F7-STABLE-BEFORE-F8-F9`)  
**Safety Stash Reference**: `stash@{0}` (`70dca4ee9e5c2ba0f833bda0110abdc3da6a235b`)  
**Status**: All 5 Ambiguities Audited, Reconciled, and Verified  

---

## Executive Summary

| Target | Item | Resolution | Action Taken | Ambiguity Status |
|:---:|---|---|---|:---:|
| **1** | `AssignTeamRequest` | **Distinct API Contracts (Keep Both)** | Verified separate contracts: `dto.inspection.AssignTeamRequest` (UUID `teamId` for Alert Inspection) vs `dto.authority.AssignTeamRequest` (String `teamId` for Event Assignment). Kept both; imports verified. | **RESOLVED** |
| **2** | `RecordActionRequest` | **Distinct API Contracts (Keep Both)** | Verified separate contracts: `dto.action.RecordActionRequest` (`alertId`, `actionType`, `notes`, `performedBy` for `POST /api/v1/actions`) vs `dto.authority.RecordActionRequest` (`actionType`, `notes`, `result` for `POST /api/v1/events/{eventId}/actions`). Kept both; imports verified. | **RESOLVED** |
| **3** | Mislocated `CitizenReportResponse.java` | **Relocated to Canonical Package** | The real implementation was located in `com/aerosentinel/citizen/` while declaring package `com.aerosentinel.dto.citizen`. It was recreated in the canonical `dto/citizen/` directory and the misplaced file was safely removed. | **RESOLVED** |
| **4** | 0-byte `CitizenReportResponse.java` | **Populated Canonical File** | Populated `backend/src/main/java/com/aerosentinel/dto/citizen/CitizenReportResponse.java` with the full 15-field canonical record contract required by `CitizenReportMapper.java`. | **RESOLVED** |
| **5** | `PRE_COMMIT_F8_F9_CHANGE_CLASSIFICATION.md` | **Classified as Audit History Artifact** | Confirmed as a temporary pre-commit audit document. Preserved untracked in the working tree for lineage; strictly excluded from feature staging allowlists. | **RESOLVED** |

---

## 1. AssignTeamRequest Analysis (Target 1)

### Detailed Analysis
- **`com.aerosentinel.dto.inspection.AssignTeamRequest`** (Tracked in Git):
  ```java
  public record AssignTeamRequest(
      @NotNull UUID teamId,
      UUID assignedBy,
      Instant scheduledAt,
      String notes
  ) {}
  ```
  - **Controllers / Services**: `InspectionController` (`POST /api/v1/inspections/assign`), `InspectionService` (`assignFieldTeam(UUID alertId, AssignTeamRequest request)`), `AlertController` (`POST /api/v1/alerts/{alertId}/assign`).
  - **Consumers**: Frontend `inspectionApi.ts`, `InspectionUnitTest`, `InspectionIntegrationTest`, `OperationalWorkflowLifecycleTest`.
  - **Domain Scope**: Assigning an inspection field team to an **Alert** using database UUID primary keys.

- **`com.aerosentinel.dto.authority.AssignTeamRequest`** (Untracked Baseline):
  ```java
  public record AssignTeamRequest(
      @NotBlank(message = "teamId is required (e.g. FIELD-TEAM-01)")
      String teamId,
      String notes
  ) {}
  ```
  - **Controllers / Services**: `AssignmentService` (`assignTeamToEvent(String eventId, AssignTeamRequest request)`).
  - **Domain Scope**: Assigning a municipal authority team to a **PollutionEvent** using human-readable team code identifiers (e.g. `"FIELD-TEAM-01"`, `"TEAM-PUN-01"`) resolved via `AuthorityTeamRepository.findByTeamId(@Param("teamId") String teamId)`.

### Code Ambiguity Resolution
- **BEFORE**: Flagged as `SUSPICIOUS` due to name collision across packages (`dto.authority` vs `dto.inspection`).
- **AFTER**: Kept both classes in their respective package namespaces.
- **WHY**: They represent genuinely distinct API contracts operating at different entity granularities (Alert UUID vs PollutionEvent String code).
- **REFERENCES VERIFIED**: 100% of imports are fully qualified:
  - `AlertController`, `InspectionController`, `InspectionService`, `OperationalWorkflowLifecycleTest`, and `InspectionUnitTest` import `com.aerosentinel.dto.inspection.AssignTeamRequest`.
  - `AssignmentService` imports `com.aerosentinel.dto.authority.AssignTeamRequest`.
- **BACKWARD COMPATIBILITY**: Full backward compatibility preserved; zero changes to existing F5/F7 endpoint signatures.

---

## 2. RecordActionRequest Analysis (Target 2)

### Detailed Analysis
- **`com.aerosentinel.dto.action.RecordActionRequest`** (Tracked in Git):
  ```java
  public record RecordActionRequest(
      @NotNull(message = "Alert ID is required") UUID alertId,
      @NotBlank(message = "Action type is required") String actionType,
      @NotBlank(message = "Action notes/details are required") String notes,
      String performedBy
  ) {}
  ```
  - **Endpoint**: `POST /api/v1/actions`
  - **Controllers / Services**: `ActionController.java`, `ActionService.java`.
  - **Domain Scope**: Recording field mitigation actions triggered by an **Alert**. The alert identifier is passed inside the request body.

- **`com.aerosentinel.dto.authority.RecordActionRequest`** (Untracked Baseline):
  ```java
  public record RecordActionRequest(
      @NotBlank(message = "actionType is required (...)")
      String actionType,
      String notes,
      String result
  ) {}
  ```
  - **Endpoint**: `POST /api/v1/events/{eventId}/actions`
  - **Controllers / Services**: `AuthorityActionController.java`, `AuthorityActionService.java`.
  - **Domain Scope**: Recording field authority actions for a **PollutionEvent**. The `eventId` is a `@PathVariable("eventId") String eventId` in the URL; the body supplies operational mitigation details including the required `result` outcome (e.g. `"EMISSION_HALTED"`, `"WARNING_ISSUED"`).

### Code Ambiguity Resolution
- **BEFORE**: Flagged as `SUSPICIOUS` due to duplicate class name and differing `result` field.
- **AFTER**: Kept both classes in their respective package namespaces.
- **WHY**: The contracts are structurally and functionally distinct:
  - `POST /api/v1/actions` requires `alertId` in body and does not require `result`.
  - `POST /api/v1/events/{eventId}/actions` carries `eventId` in URI path and mandates the `result` field to transition `PollutionEvent` lifecycle to `ACTION_TAKEN`.
- **REFERENCES VERIFIED**:
  - `ActionController`, `ActionService`, and `OperationalWorkflowLifecycleTest` import `com.aerosentinel.dto.action.RecordActionRequest`.
  - `AuthorityActionController` and `AuthorityActionService` import `com.aerosentinel.dto.authority.RecordActionRequest`.
- **BACKWARD COMPATIBILITY**: Completely preserved; no endpoints altered.

---

## 3. CitizenReportResponse Analysis (Targets 3 & 4)

### Detailed Analysis
- **Problem**:
  1. `backend/src/main/java/com/aerosentinel/citizen/CitizenReportResponse.java` contained the real 15-field record implementation, but declared `package com.aerosentinel.dto.citizen;` despite residing in the `citizen/` folder.
  2. `backend/src/main/java/com/aerosentinel/dto/citizen/CitizenReportResponse.java` was a 0-byte empty file.
  3. `CitizenReportMapper.java` imported `com.aerosentinel.dto.citizen.CitizenReportResponse` and instantiated it with 15 arguments.

### Code Ambiguity Resolution
- **BEFORE**: Mislocated file in `citizen/` directory with 0-byte placeholder in `dto/citizen/` directory.
- **AFTER**:
  - Populated canonical file: `backend/src/main/java/com/aerosentinel/dto/citizen/CitizenReportResponse.java`.
  - Removed misplaced file: `backend/src/main/java/com/aerosentinel/citizen/CitizenReportResponse.java`.
- **WHY**: Conforms physical directory structure to Java package specifications (`com.aerosentinel.dto.citizen`).
- **REFERENCES VERIFIED**:
  - `CitizenReportMapper.java` line 3: `import com.aerosentinel.dto.citizen.CitizenReportResponse;` cleanly resolves against the canonical DTO.
  - Zero files in repository import `com.aerosentinel.citizen.CitizenReportResponse`.
- **BACKWARD COMPATIBILITY**: 100% compliant. Field types, order, and Jackson annotations (`@JsonInclude(JsonInclude.Include.NON_NULL)`) remain identical.

---

## 4. Classification Audit Document Handling (Target 5)

- **File**: `PRE_COMMIT_F8_F9_CHANGE_CLASSIFICATION.md`
- **Assessment**: Contains the foundational 228-row change classification audit performed prior to feature branch alignment.
- **Action**: Preserved **untracked** in the local working tree for engineering documentation and lineage. Confirmed excluded from staging allowlists (`DO_NOT_STAGE_FILES.txt`). Not modified.

---

## 5. Exact Files Changed / Removed

1. **Populated (Canonical)**:
   - `backend/src/main/java/com/aerosentinel/dto/citizen/CitizenReportResponse.java` (634 bytes, 25 lines)
2. **Removed (Misplaced Duplicate)**:
   - `backend/src/main/java/com/aerosentinel/citizen/CitizenReportResponse.java` (Deleted)
3. **Confirmed Intact (Distinct Contracts)**:
   - `backend/src/main/java/com/aerosentinel/dto/authority/AssignTeamRequest.java` (Kept)
   - `backend/src/main/java/com/aerosentinel/dto/inspection/AssignTeamRequest.java` (Kept)
   - `backend/src/main/java/com/aerosentinel/dto/authority/RecordActionRequest.java` (Kept)
   - `backend/src/main/java/com/aerosentinel/dto/action/RecordActionRequest.java` (Kept)
4. **Preserved Untracked**:
   - `PRE_COMMIT_F8_F9_CHANGE_CLASSIFICATION.md` (Kept untracked, excluded from staging)

---

## 6. Verification Results

### Backend Compilation
- **Command**: `.\mvnw.cmd test-compile` (in `backend/`)
- **Status**: **BUILD SUCCESS**
- **Metrics**: 298 main source files compiled + 54 test source files compiled with javac release 21 in 27.765 s.
- **Failures**: **0**

### Frontend TypeScript Verification
- **Command**: `npx tsc --noEmit` (in `frontend/`)
- **Status**: **PASS** (Exit code 0, zero diagnostic errors)

### Relevant Automated Tests
- **Command**: `.\mvnw.cmd test "-Dtest=InspectionUnitTest,CitizenReportUnitTest,OperationalWorkflowLifecycleTest"`
- **Results**:
  - `OperationalWorkflowLifecycleTest`: 16/16 passed (Alert → Acknowledge → Assign → Inspection → Authority Action → Resolve)
  - `CitizenReportUnitTest`: 8/8 passed (Citizen report submission, status, validation, Gemini vision fallback)
  - `InspectionUnitTest`: 14/14 passed (Field team assignment, verification completion, state machine constraints)
- **Total Tests Run**: **38**
- **Failures**: **0**
- **Errors**: **0**
- **Skipped**: **0**

---

## 7. Working Tree & Stash Preservation Check

- **Staged Files**: 48 entries (46 modified + 2 deletions, unchanged from previous step).
- **Unstaged Modified Tracked Files**: **0** (`git diff --stat` is completely clean).
- **Unmerged Files**: **0** (No `UU` files).
- **Safety Stash**: `stash@{0}` confirmed present:
  ```
  stash@{0}: On feature/f8-f9-integration: F8-F9 WIP safety snapshot before aligning with protected main
  ```

---

## 8. Remaining Ambiguities

- **Total Remaining Ambiguities**: **0**

Every identified ambiguity has been thoroughly investigated, validated against codebase references, and either confirmed as a necessary distinct contract or reconciled to its canonical location.

---

## Final Decision

```
AMBIGUITIES RESOLVED = YES

SAFE FOR STAGING AUDIT = YES
```
