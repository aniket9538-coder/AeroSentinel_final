package com.aerosentinel.evidence;

import com.aerosentinel.dto.evidence.EvidenceSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/evidence")
public class EvidenceController {

    private final EvidenceService evidenceService;
    private final EvidenceOrchestrationService orchestrationService;
    private final EvidenceFusionService evidenceFusionService;

    public EvidenceController(
            EvidenceService evidenceService,
            EvidenceOrchestrationService orchestrationService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) EvidenceFusionService evidenceFusionService
    ) {
        this.evidenceService = evidenceService;
        this.orchestrationService = orchestrationService;
        this.evidenceFusionService = evidenceFusionService;
    }

    /**
     * Authoritative Phase 7: Evaluates multi-source evidence fusion and returns an explainable triage candidate.
     */
    @GetMapping("/triage/h3/{h3Index}")
    public ResponseEntity<TriageCandidateDto> getTriageCandidate(@PathVariable String h3Index) {
        if (evidenceFusionService == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok(evidenceFusionService.evaluateTriageCandidate(h3Index));
    }

    /**
     * Authoritative Phase 7: Fuses and idempotently persists canonical event & alert candidate if verified.
     */
    @PostMapping("/triage/fuse")
    public ResponseEntity<TriageCandidateDto> fuseAndPersist(
            @RequestParam(required = false) String h3Index,
            @RequestBody(required = false) java.util.Map<String, String> body
    ) {
        if (evidenceFusionService == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        String targetH3 = (h3Index != null && !h3Index.isBlank())
                ? h3Index
                : (body != null ? body.get("h3Index") : null);
        if (targetH3 == null || targetH3.isBlank()) {
            throw new com.aerosentinel.exception.ValidationException("h3Index is required for evidence fusion");
        }
        return ResponseEntity.ok(evidenceFusionService.fuseAndPersist(targetH3));
    }

    @GetMapping
    public ResponseEntity<List<EventEvidence>> getEvidence(@RequestParam UUID eventId) {
        return ResponseEntity.ok(evidenceService.getEvidenceByEvent(eventId));
    }

    /**
     * Synthesizes and returns unified, grounded evidence intelligence for a single H3 hexagon.
     * Prefers persisted evidence dossier data to prevent unnecessary timeouts and duplicate Gemini calls.
     *
     * @param h3Index 15-character Uber H3 resolution 8 index
     * @return grounded EvidenceSummaryResponse
     */
    @GetMapping("/hotspot/{h3Index}")
    public ResponseEntity<EvidenceSummaryResponse> getEvidenceByH3(@PathVariable String h3Index) {
        return ResponseEntity.ok(orchestrationService.getPersistedOrOrchestratedEvidence(h3Index));
    }

    /**
     * Explicitly triggers fresh AI evidence scoring & Gemini narrative orchestration for an H3 cell.
     */
    @PostMapping("/orchestrate")
    public ResponseEntity<EvidenceSummaryResponse> orchestrateEvidence(
            @RequestParam(required = false) String h3Index,
            @RequestBody(required = false) java.util.Map<String, String> body
    ) {
        String targetH3 = (h3Index != null && !h3Index.isBlank())
                ? h3Index
                : (body != null ? body.get("h3Index") : null);
        if (targetH3 == null || targetH3.isBlank()) {
            throw new com.aerosentinel.exception.ValidationException("h3Index is required for orchestration");
        }
        return ResponseEntity.ok(orchestrationService.getOrchestratedEvidence(targetH3));
    }
}

