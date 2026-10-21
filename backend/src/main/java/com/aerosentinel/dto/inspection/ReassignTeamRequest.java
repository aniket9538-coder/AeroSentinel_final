package com.aerosentinel.dto.inspection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record ReassignTeamRequest(
        @NotNull UUID newTeamId,
        @NotBlank String reason,
        Instant scheduledAt,
        String notes
) {
    public ReassignTeamRequest(UUID newTeamId, String reason, String notes) {
        this(newTeamId, reason, null, notes);
    }

    public ReassignTeamRequest(UUID newTeamId, String reason) {
        this(newTeamId, reason, null, null);
    }
}
