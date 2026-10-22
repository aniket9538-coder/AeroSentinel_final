package com.aerosentinel.citizen.preference;

import com.aerosentinel.exception.ResourceNotFoundException;
import com.aerosentinel.exception.ValidationException;
import com.aerosentinel.spatial.H3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CitizenPreferenceServiceTest {

    @Mock
    private CitizenSavedLocationRepository locationRepository;

    @Mock
    private H3Service h3Service;

    private CitizenPreferenceService preferenceService;

    @BeforeEach
    void setUp() {
        preferenceService = new CitizenPreferenceService(locationRepository, h3Service);
    }

    @Test
    @DisplayName("createSavedLocation generates deterministic H3 and sets default for first location")
    void createSavedLocation_success() {
        when(h3Service.coordinatesToH3(18.5204, 73.8567)).thenReturn("8860885357fffff");
        when(locationRepository.findByUserIdOrderByCreatedAtDesc("user-1")).thenReturn(Collections.emptyList());
        when(locationRepository.save(any(CitizenSavedLocation.class))).thenAnswer(inv -> {
            CitizenSavedLocation loc = inv.getArgument(0);
            loc.setId(UUID.randomUUID());
            return loc;
        });

        SavedLocationDto.CreateRequest request = new SavedLocationDto.CreateRequest(
                "Home",
                "Pune",
                18.5204,
                73.8567,
                false
        );

        SavedLocationDto.Response response = preferenceService.createSavedLocation("user-1", request);

        assertThat(response).isNotNull();
        assertThat(response.label()).isEqualTo("Home");
        assertThat(response.h3Index()).isEqualTo("8860885357fffff");
        assertThat(response.isDefault()).isTrue(); // First location auto-defaults
        verify(locationRepository).save(any(CitizenSavedLocation.class));
    }

    @Test
    @DisplayName("createSavedLocation rejects invalid latitude or longitude")
    void createSavedLocation_invalidCoords() {
        SavedLocationDto.CreateRequest invalidLat = new SavedLocationDto.CreateRequest(
                "Invalid", "Pune", 95.0, 73.8567, false
        );

        assertThatThrownBy(() -> preferenceService.createSavedLocation("user-1", invalidLat))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid latitude");

        SavedLocationDto.CreateRequest invalidLon = new SavedLocationDto.CreateRequest(
                "Invalid", "Pune", 18.5204, 185.0, false
        );

        assertThatThrownBy(() -> preferenceService.createSavedLocation("user-1", invalidLon))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid longitude");
    }

    @Test
    @DisplayName("setDefaultLocation switches default and enforces user ownership isolation")
    void setDefaultLocation_ownershipEnforced() {
        UUID locId = UUID.randomUUID();
        CitizenSavedLocation loc = new CitizenSavedLocation("user-1", "Work", "Pune", 18.5, 73.8, "8860885357fffff", false);
        loc.setId(locId);

        when(locationRepository.findByIdAndUserId(locId, "user-1")).thenReturn(Optional.of(loc));
        when(locationRepository.findByUserIdAndIsDefaultTrue("user-1")).thenReturn(Optional.empty());
        when(locationRepository.save(any(CitizenSavedLocation.class))).thenAnswer(inv -> inv.getArgument(0));

        SavedLocationDto.Response result = preferenceService.setDefaultLocation("user-1", locId);

        assertThat(result.isDefault()).isTrue();

        // Foreign user access rejection
        when(locationRepository.findByIdAndUserId(locId, "user-2")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> preferenceService.setDefaultLocation("user-2", locId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found or not owned");
    }

    @Test
    @DisplayName("deleteSavedLocation promotes another location to default if deleted was default")
    void deleteSavedLocation_promotesRemaining() {
        UUID locId = UUID.randomUUID();
        CitizenSavedLocation deletedLoc = new CitizenSavedLocation("user-1", "Default", "Pune", 18.5, 73.8, "8860885357fffff", true);
        deletedLoc.setId(locId);

        CitizenSavedLocation remainingLoc = new CitizenSavedLocation("user-1", "Secondary", "Pune", 18.6, 73.9, "88608850e5fffff", false);
        remainingLoc.setId(UUID.randomUUID());

        when(locationRepository.findByIdAndUserId(locId, "user-1")).thenReturn(Optional.of(deletedLoc));
        when(locationRepository.findByUserIdOrderByCreatedAtDesc("user-1")).thenReturn(List.of(remainingLoc));

        preferenceService.deleteSavedLocation("user-1", locId);

        verify(locationRepository).delete(deletedLoc);
        assertThat(remainingLoc.getIsDefault()).isTrue();
        verify(locationRepository).save(remainingLoc);
    }
}
