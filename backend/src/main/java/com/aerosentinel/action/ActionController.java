package com.aerosentinel.action;

import com.aerosentinel.dto.action.ActionResponseDto;
import com.aerosentinel.dto.action.RecordActionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Authoritative REST Controller for Authority Actions (Feature 7 Phase 6).
 */
@RestController
@RequestMapping("/api/v1/actions")
public class ActionController {

    private final ActionService actionService;

    public ActionController(ActionService actionService) {
        this.actionService = actionService;
    }

    /**
     * Records a municipal authority action against an alert.
     */
    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('AUTHORITY', 'ADMIN')")
    public ResponseEntity<ActionResponseDto> recordAction(
            @Valid @RequestBody RecordActionRequest request,
            Principal principal
    ) {
        enforceAuthorityAuthorization();

        String officer = (principal != null) ? principal.getName() : "Municipal Officer";
        ActionResponseDto dto = actionService.recordAction(request, officer);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Retrieves all recorded actions for an alert.
     */
    @GetMapping
    public ResponseEntity<List<ActionResponseDto>> getActions(@RequestParam UUID alertId) {
        return ResponseEntity.ok(actionService.getActionsForAlert(alertId));
    }

    /**
     * Validates that authenticated callers possess AUTHORITY or ADMIN role.
     * Rejects unauthenticated callers and CITIZEN or ANALYST users with 403 Forbidden.
     */
    private void enforceAuthorityAuthorization() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Access denied: Authentication required for authority operational actions");
        }
        boolean hasAuthorityRole = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_AUTHORITY") || a.getAuthority().equals("ROLE_ADMIN"));
        if (!hasAuthorityRole) {
            throw new AccessDeniedException("Access denied: Authority operational actions require AUTHORITY or ADMIN role");
        }
    }
}
