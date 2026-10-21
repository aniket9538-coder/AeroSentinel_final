package com.aerosentinel.citizen;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.aerosentinel.assistant.service.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller handling citizen environmental report submissions,
 * status inquiries, and secure photo access.
 */
@RestController
@RequestMapping("/api/v1/citizen")
public class CitizenReportController {

    private final CitizenReportService citizenReportService;
    private final PhotoStorageService photoStorageService;
    private final CitizenLookupRateLimiter lookupRateLimiter;
    private final ClientIpResolver clientIpResolver;

    @org.springframework.beans.factory.annotation.Autowired
    public CitizenReportController(
            CitizenReportService citizenReportService,
            PhotoStorageService photoStorageService,
            CitizenLookupRateLimiter lookupRateLimiter,
            ClientIpResolver clientIpResolver
    ) {
        this.citizenReportService = citizenReportService;
        this.photoStorageService = photoStorageService;
        this.lookupRateLimiter = lookupRateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    public CitizenReportController(
            CitizenReportService citizenReportService,
            PhotoStorageService photoStorageService
    ) {
        this(citizenReportService, photoStorageService, new CitizenLookupRateLimiter(20), new ClientIpResolver(""));
    }

    /**
     * Multipart form ingestion endpoint supporting citizen photos, GPS coordinates,
     * category, qualitative descriptions, and incident datetime.
     */
    @PostMapping(value = "/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CitizenReportResponseDto> submitMultipartReport(
            @RequestParam("cityId") UUID cityId,
            @RequestParam("latitude") Double latitude,
            @RequestParam("longitude") Double longitude,
            @RequestParam("category") String category,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "photo", required = false) MultipartFile photo,
            @RequestParam(value = "photos", required = false) List<MultipartFile> photos,
            @RequestParam(value = "observedAt", required = false) String observedAt
    ) {
        Instant parsedObservedAt = null;
        if (observedAt != null && !observedAt.isBlank()) {
            try {
                parsedObservedAt = Instant.parse(observedAt.trim());
            } catch (Exception ignored) {
                parsedObservedAt = Instant.now();
            }
        }

        List<MultipartFile> allPhotos = new ArrayList<>();
        if (photos != null && !photos.isEmpty()) {
            allPhotos.addAll(photos);
        } else if (photo != null && !photo.isEmpty()) {
            allPhotos.add(photo);
        }

        UUID userId = resolveCurrentUserId();

        CitizenReportResponseDto response = citizenReportService.submitReport(
                cityId,
                latitude,
                longitude,
                category,
                description,
                allPhotos,
                parsedObservedAt,
                userId
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Preserved JSON ingestion endpoint for backward compatibility with programmatic callers.
     */
    @PostMapping(value = "/reports", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CitizenReport> submitJsonReport(@RequestBody CitizenReport report) {
        return ResponseEntity.status(HttpStatus.CREATED).body(citizenReportService.createReport(report));
    }

    /**
     * Retrieves an individual citizen report and its Gemini Vision interpretation by ID.
     */
    @GetMapping("/reports/{reportId}")
    public ResponseEntity<CitizenReportResponseDto> getReportById(@PathVariable UUID reportId) {
        UUID userId = resolveCurrentUserId();
        boolean isPrivileged = isCallerPrivileged();
        return ResponseEntity.ok(citizenReportService.getReportById(reportId, userId, isPrivileged));
    }

    /**
     * Retrieves report by reference code (receipt reference e.g. CR-XXXXXXXX or UUID).
     * Enforces rate limiting against brute-force guessing and minimal data disclosure.
     */
    @GetMapping("/reports/reference/{reference}")
    public ResponseEntity<CitizenReportResponseDto> getReportByReference(
            @PathVariable String reference,
            HttpServletRequest request
    ) {
        String clientIp = clientIpResolver != null ? clientIpResolver.resolveClientIp(request) : request.getRemoteAddr();
        if (lookupRateLimiter != null && !lookupRateLimiter.tryAcquire(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "60")
                    .build();
        }

        UUID userId = resolveCurrentUserId();
        boolean isPrivileged = isCallerPrivileged();
        return ResponseEntity.ok(citizenReportService.getReportByReference(reference, userId, isPrivileged));
    }

    /**
     * Retrieves all citizen reports submitted by the authenticated citizen.
     */
    @GetMapping("/reports/my")
    public ResponseEntity<List<CitizenReportResponseDto>> getMyReports() {
        UUID userId = resolveCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(citizenReportService.getReportsByUser(userId));
    }

    /**
     * Lists all citizen reports for a specific city.
     */
    @GetMapping("/reports")
    public ResponseEntity<List<CitizenReportResponseDto>> getReports(@RequestParam UUID cityId) {
        return ResponseEntity.ok(citizenReportService.getReportsByCity(cityId));
    }

    /**
     * Securely serves stored citizen observation photos with traversal protection,
     * independent authorization, and anti-sniff headers.
     */
    @GetMapping("/photos/{storageKey:[a-zA-Z0-9._-]+}")
    public ResponseEntity<Resource> getPhoto(@PathVariable String storageKey) {
        UUID userId = resolveCurrentUserId();
        boolean isPrivileged = isCallerPrivileged();

        // Enforce photo authorization: if photo is linked to an authenticated citizen report,
        // caller must be either the citizen owner or a privileged authority/analyst
        Optional<CitizenReport> reportOpt = citizenReportService.findReportByPhotoKey(storageKey);
        if (reportOpt.isPresent()) {
            CitizenReport report = reportOpt.get();
            if (report.getUserId() != null && !isPrivileged) {
                if (userId == null || !report.getUserId().equals(userId)) {
                    throw new org.springframework.security.access.AccessDeniedException("Access denied: You are not authorized to access this private photo evidence.");
                }
            }
        }

        Resource resource = photoStorageService.loadPhotoAsResource(storageKey);
        Path path = photoStorageService.resolvePhotoPath(storageKey);
        String contentType = "image/jpeg";
        try {
            String probe = Files.probeContentType(path);
            if (probe != null) {
                contentType = probe;
            }
        } catch (IOException ignored) {}

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + storageKey + "\"")
                .body(resource);
    }

    private UUID resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            try {
                return UUID.fromString(auth.getName());
            } catch (Exception ignored) {}
        }
        return null;
    }

    private boolean isCallerPrivileged() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return auth.getAuthorities().stream().anyMatch(a ->
                    "ROLE_AUTHORITY".equals(a.getAuthority()) ||
                    "ROLE_ADMIN".equals(a.getAuthority()) ||
                    "ROLE_ANALYST".equals(a.getAuthority()));
        }
        return false;
    }
}
