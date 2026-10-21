package com.aerosentinel.admin;

import com.aerosentinel.admin.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for Admin Observability & System Monitoring (Phase 9).
 * Strictly requires ROLE_ADMIN. Operational diagnostics are decoupled from authority incident workflows.
 */
@RestController
@RequestMapping("/api/v1/admin/observability")
@PreAuthorize("hasRole('ADMIN')")
public class AdminObservabilityController {

    private final AdminObservabilityService observabilityService;

    public AdminObservabilityController(AdminObservabilityService observabilityService) {
        this.observabilityService = observabilityService;
    }

    /**
     * Top-level system overview including aggregate status, vital counts, and diagnostic guidance.
     */
    @GetMapping("/overview")
    public ResponseEntity<AdminOverviewDto> getAdminOverview() {
        return ResponseEntity.ok(observabilityService.getAdminOverview());
    }

    /**
     * Detailed service liveness/readiness, JVM metrics, PostgreSQL pool stats, and AI service probe.
     */
    @GetMapping("/services")
    public ResponseEntity<ServiceHealthDto> getServiceHealth() {
        return ResponseEntity.ok(observabilityService.getServiceHealth());
    }

    /**
     * Ingestion status, data freshness, and record counts across supported environmental providers.
     */
    @GetMapping("/providers")
    public ResponseEntity<ProviderObservabilityDto> getProviderObservability() {
        return ResponseEntity.ok(observabilityService.getProviderObservability());
    }

    /**
     * Machine learning model readiness, artifact verification, and inference capability status.
     */
    @GetMapping("/models")
    public ResponseEntity<ModelObservabilityDto> getModelObservability() {
        return ResponseEntity.ok(observabilityService.getModelObservability());
    }

    /**
     * Safe operational and administrative audit events with all credentials and citizen PII redacted.
     */
    @GetMapping("/audit-events")
    public ResponseEntity<List<SafeAuditEventDto>> getSafeAuditEvents() {
        return ResponseEntity.ok(observabilityService.getSafeAuditEvents());
    }
}
