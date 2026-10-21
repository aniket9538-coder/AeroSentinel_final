package com.aerosentinel.inspection;

import com.aerosentinel.dto.inspection.AssignTeamRequest;
import com.aerosentinel.dto.inspection.FieldVerificationDto;
import com.aerosentinel.dto.inspection.InspectionResponseDto;
import com.aerosentinel.dto.inspection.SubmitVerificationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Field Inspections & Field Verifications (F5-P6).
 */
@RestController
@RequestMapping("/api/v1/inspections")
public class InspectionController {

    private final InspectionService inspectionService;

    public InspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    /**
     * Retrieves an inspection by UUID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<InspectionResponseDto> getInspectionById(@PathVariable UUID id) {
        return ResponseEntity.ok(inspectionService.getInspectionById(id));
    }

    /**
     * Starts an assigned inspection, transitioning it to IN_PROGRESS.
     */
    @PatchMapping("/{id}/start")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('AUTHORITY', 'ADMIN', 'OPERATOR')")
    public ResponseEntity<InspectionResponseDto> startInspection(@PathVariable UUID id) {
        enforceAuthorityAuthorization();
        String actor = getAuthenticatedActor();
        return ResponseEntity.ok(inspectionService.startInspection(id, actor));
    }

    /**
     * Submits an observational field verification report for an IN_PROGRESS inspection.
     * Transitions inspection to COMPLETED.
     */
    @PostMapping(value = {"/{id}/verification", "/{id}/verify"})
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('AUTHORITY', 'ADMIN', 'OPERATOR')")
    public ResponseEntity<InspectionResponseDto> submitVerification(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitVerificationRequest request
    ) {
        enforceAuthorityAuthorization();
        String actor = getAuthenticatedActor();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inspectionService.submitVerification(id, request, actor));
    }

    /**
     * Retrieves all field verification records for an inspection.
     */
    @GetMapping("/{id}/verifications")
    public ResponseEntity<List<FieldVerificationDto>> getVerificationsForInspection(@PathVariable UUID id) {
        return ResponseEntity.ok(inspectionService.getVerificationsForInspection(id));
    }

    /**
     * Direct inspection assignment endpoint for an alert.
     */
    @PostMapping("/alert/{alertId}/assign")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('AUTHORITY', 'ADMIN', 'OPERATOR')")
    public ResponseEntity<InspectionResponseDto> assignInspection(
            @PathVariable UUID alertId,
            @Valid @RequestBody AssignTeamRequest request
    ) {
        enforceAuthorityAuthorization();
        String actor = getAuthenticatedActor();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inspectionService.assignFieldTeam(alertId, request, actor));
    }

    /**
     * Reassigns an inspection to a different eligible municipal field team (F7-P8).
     */
    @PostMapping("/alert/{alertId}/reassign")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('AUTHORITY', 'ADMIN', 'OPERATOR')")
    public ResponseEntity<InspectionResponseDto> reassignInspection(
            @PathVariable UUID alertId,
            @Valid @RequestBody com.aerosentinel.dto.inspection.ReassignTeamRequest request
    ) {
        enforceAuthorityAuthorization();
        String actor = getAuthenticatedActor();
        return ResponseEntity.ok(inspectionService.reassignFieldTeam(alertId, request, actor));
    }

    private void enforceAuthorityAuthorization() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Access denied: Authentication required for field inspection operations");
        }
        boolean hasAuthorityRole = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_AUTHORITY")
                        || a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_OPERATOR"));
        if (!hasAuthorityRole) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Access denied: Field inspection operations require AUTHORITY, ADMIN, or OPERATOR role");
        }
    }

    private String getAuthenticatedActor() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            return auth.getName();
        }
        return "Municipal Authority Officer";
    }
}
