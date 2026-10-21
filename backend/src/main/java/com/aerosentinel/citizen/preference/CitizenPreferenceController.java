package com.aerosentinel.citizen.preference;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/citizen/preferences/locations")
public class CitizenPreferenceController {

    private final CitizenPreferenceService preferenceService;

    public CitizenPreferenceController(CitizenPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ResponseEntity<List<SavedLocationDto.Response>> getSavedLocations(
            Principal principal,
            @RequestHeader(name = "X-Citizen-Id", required = false) String citizenIdHeader
    ) {
        String userId = resolveUserId(principal, citizenIdHeader);
        return ResponseEntity.ok(preferenceService.getSavedLocations(userId));
    }

    @PostMapping
    public ResponseEntity<SavedLocationDto.Response> createSavedLocation(
            @Valid @RequestBody SavedLocationDto.CreateRequest request,
            Principal principal,
            @RequestHeader(name = "X-Citizen-Id", required = false) String citizenIdHeader
    ) {
        String userId = resolveUserId(principal, citizenIdHeader);
        SavedLocationDto.Response response = preferenceService.createSavedLocation(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SavedLocationDto.Response> updateLocation(
            @PathVariable UUID id,
            @Valid @RequestBody SavedLocationDto.UpdateRequest request,
            Principal principal,
            @RequestHeader(name = "X-Citizen-Id", required = false) String citizenIdHeader
    ) {
        String userId = resolveUserId(principal, citizenIdHeader);
        return ResponseEntity.ok(preferenceService.updateLocation(userId, id, request));
    }

    @PutMapping("/{id}/default")
    public ResponseEntity<SavedLocationDto.Response> setDefaultLocation(
            @PathVariable UUID id,
            Principal principal,
            @RequestHeader(name = "X-Citizen-Id", required = false) String citizenIdHeader
    ) {
        String userId = resolveUserId(principal, citizenIdHeader);
        return ResponseEntity.ok(preferenceService.setDefaultLocation(userId, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSavedLocation(
            @PathVariable UUID id,
            Principal principal,
            @RequestHeader(name = "X-Citizen-Id", required = false) String citizenIdHeader
    ) {
        String userId = resolveUserId(principal, citizenIdHeader);
        preferenceService.deleteSavedLocation(userId, id);
        return ResponseEntity.noContent().build();
    }

    private String resolveUserId(Principal principal, String citizenIdHeader) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            return principal.getName().trim();
        }
        if (citizenIdHeader != null && !citizenIdHeader.trim().isBlank()) {
            return "guest-" + citizenIdHeader.trim();
        }
        return "anonymous-citizen";
    }
}
