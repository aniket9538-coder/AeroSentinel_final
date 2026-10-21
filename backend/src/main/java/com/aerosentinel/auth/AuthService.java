package com.aerosentinel.auth;

import com.aerosentinel.security.CustomUserDetailsService;
import com.aerosentinel.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public AuthService(
            CustomUserDetailsService userDetailsService,
            JwtService jwtService,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder
    ) {
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse login(LoginRequest request) {
        if (request == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("Username/email must not be blank");
        }

        String username = request.getUsername();
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        String rawPassword = request.password();
        // Production identity limitation note: In-memory demo credentials and username-derived roles
        // are strictly development-only mechanisms for local testing and must be replaced by
        // a certified OIDC/OAuth2 enterprise IdP in production deployments.
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, userDetails.getPassword())) {
            log.warn("Authentication failed for user '{}': invalid password credentials", username);
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid username or password");
        }

        String role = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_CITIZEN");

        String token = jwtService.generateToken(userDetails.getUsername(), role);
        log.info("Authenticated user '{}' with role '{}'", username, role);

        return AuthResponse.of(token, userDetails.getUsername(), role);
    }
}
