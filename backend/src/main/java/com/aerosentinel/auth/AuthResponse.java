package com.aerosentinel.auth;

import java.util.Map;

public record AuthResponse(
        String token,
        String tokenType,
        String username,
        String role,
        Map<String, Object> user
) {
    public static AuthResponse of(String token, String username, String role) {
        String cleanRole = role != null && role.startsWith("ROLE_") ? role.substring(5) : role;
        return new AuthResponse(
                token,
                "Bearer",
                username,
                cleanRole,
                Map.of(
                        "username", username,
                        "role", cleanRole != null ? cleanRole : "CITIZEN"
                )
        );
    }
}
