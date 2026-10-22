package com.aerosentinel.citizen.preference;

import com.aerosentinel.exception.ResourceNotFoundException;
import com.aerosentinel.exception.ValidationException;
import com.aerosentinel.spatial.H3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CitizenPreferenceService {

    private static final Logger log = LoggerFactory.getLogger(CitizenPreferenceService.class);

    private final CitizenSavedLocationRepository locationRepository;
    private final H3Service h3Service;

    public CitizenPreferenceService(CitizenSavedLocationRepository locationRepository, H3Service h3Service) {
        this.locationRepository = locationRepository;
        this.h3Service = h3Service;
    }

    @Transactional(readOnly = true)
    public List<SavedLocationDto.Response> getSavedLocations(String userId) {
        String effectiveUserId = sanitizeUserId(userId);
        return locationRepository.findByUserIdOrderByCreatedAtDesc(effectiveUserId)
                .stream()
                .map(SavedLocationDto.Response::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public SavedLocationDto.Response createSavedLocation(String userId, SavedLocationDto.CreateRequest request) {
        String effectiveUserId = sanitizeUserId(userId);
        validateCoordinates(request.latitude(), request.longitude());

        if (request.label() == null || request.label().trim().isBlank()) {
            throw new ValidationException("Location label is required");
        }

        // Canonical spatial identity: Deterministically compute H3 Resolution 8 index from server geometry
        String h3Index = h3Service.coordinatesToH3(request.latitude(), request.longitude());

        boolean isDefault = Boolean.TRUE.equals(request.isDefault());
        if (isDefault) {
            unsetExistingDefault(effectiveUserId);
        } else {
            // If user has no existing saved locations, make this first one default
            List<CitizenSavedLocation> existing = locationRepository.findByUserIdOrderByCreatedAtDesc(effectiveUserId);
            if (existing.isEmpty()) {
                isDefault = true;
            }
        }

        CitizenSavedLocation entity = new CitizenSavedLocation(
                effectiveUserId,
                request.label().trim(),
                request.cityName().trim(),
                request.latitude(),
                request.longitude(),
                h3Index,
                isDefault
        );

        CitizenSavedLocation saved = locationRepository.save(entity);
        log.info("Saved citizen location '{}' ({}, {}) with H3='{}' for user '{}'",
                saved.getLabel(), saved.getLatitude(), saved.getLongitude(), h3Index, effectiveUserId);

        return SavedLocationDto.Response.fromEntity(saved);
    }

    @Transactional
    public SavedLocationDto.Response setDefaultLocation(String userId, UUID locationId) {
        String effectiveUserId = sanitizeUserId(userId);
        CitizenSavedLocation target = locationRepository.findByIdAndUserId(locationId, effectiveUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Saved location not found or not owned by requesting user"));

        unsetExistingDefault(effectiveUserId);

        target.setIsDefault(true);
        target.setUpdatedAt(Instant.now());
        CitizenSavedLocation updated = locationRepository.save(target);

        log.info("Set default location to '{}' ({}) for user '{}'", target.getLabel(), target.getId(), effectiveUserId);
        return SavedLocationDto.Response.fromEntity(updated);
    }

    @Transactional
    public SavedLocationDto.Response updateLocation(String userId, UUID locationId, SavedLocationDto.UpdateRequest request) {
        String effectiveUserId = sanitizeUserId(userId);
        CitizenSavedLocation target = locationRepository.findByIdAndUserId(locationId, effectiveUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Saved location not found or not owned by requesting user"));

        if (request.label() != null && !request.label().trim().isBlank()) {
            target.setLabel(request.label().trim());
        }

        if (Boolean.TRUE.equals(request.isDefault())) {
            unsetExistingDefault(effectiveUserId);
            target.setIsDefault(true);
        }

        target.setUpdatedAt(Instant.now());
        CitizenSavedLocation updated = locationRepository.save(target);
        return SavedLocationDto.Response.fromEntity(updated);
    }

    @Transactional
    public void deleteSavedLocation(String userId, UUID locationId) {
        String effectiveUserId = sanitizeUserId(userId);
        CitizenSavedLocation target = locationRepository.findByIdAndUserId(locationId, effectiveUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Saved location not found or not owned by requesting user"));

        locationRepository.delete(target);
        log.info("Deleted saved location '{}' ({}) for user '{}'", target.getLabel(), locationId, effectiveUserId);

        // If the deleted one was default, designate another one as default if available
        if (Boolean.TRUE.equals(target.getIsDefault())) {
            List<CitizenSavedLocation> remaining = locationRepository.findByUserIdOrderByCreatedAtDesc(effectiveUserId);
            if (!remaining.isEmpty()) {
                CitizenSavedLocation newDefault = remaining.get(0);
                newDefault.setIsDefault(true);
                locationRepository.save(newDefault);
            }
        }
    }

    private void unsetExistingDefault(String userId) {
        Optional<CitizenSavedLocation> currentDefault = locationRepository.findByUserIdAndIsDefaultTrue(userId);
        currentDefault.ifPresent(loc -> {
            loc.setIsDefault(false);
            locationRepository.save(loc);
        });
    }

    private void validateCoordinates(Double lat, Double lon) {
        if (lat == null || lon == null) {
            throw new ValidationException("Latitude and longitude coordinates are required");
        }
        if (lat < -90.0 || lat > 90.0) {
            throw new ValidationException("Invalid latitude: must be between -90.0 and +90.0 degrees");
        }
        if (lon < -180.0 || lon > 180.0) {
            throw new ValidationException("Invalid longitude: must be between -180.0 and +180.0 degrees");
        }
    }

    private String sanitizeUserId(String userId) {
        if (userId == null || userId.trim().isBlank()) {
            return "anonymous-citizen";
        }
        return userId.trim();
    }
}
