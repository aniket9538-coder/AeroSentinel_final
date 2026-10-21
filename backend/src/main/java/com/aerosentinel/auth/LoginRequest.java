package com.aerosentinel.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        String email,
        String username,
        String password
) {
    public LoginRequest(String email, String password) {
        this(email, null, password);
    }

    public String getUsername() {
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        if (username != null && !username.isBlank()) {
            return username.trim();
        }
        return "";
    }
}
