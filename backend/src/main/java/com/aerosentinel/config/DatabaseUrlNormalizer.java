package com.aerosentinel.config;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Normalizes PostgreSQL / Supabase connection strings into valid pgJDBC connection URLs
 * and extracts credentials safely.
 *
 * Supports formats:
 *   - postgres://user:password@host:port/database
 *   - postgresql://user:password@host:port/database?sslmode=require
 *   - jdbc:postgresql://host:port/database?sslmode=require
 *
 * Never logs credentials. Passwords are redacted in representations.
 */
public final class DatabaseUrlNormalizer {

    private DatabaseUrlNormalizer() {}

    public record NormalizedDatabaseConfig(
            String jdbcUrl,
            String username,
            String password,
            boolean sslEnforced
    ) {
        @Override
        public String toString() {
            String maskedPass = (password != null && !password.isEmpty()) ? "******" : "<none>";
            return "NormalizedDatabaseConfig[jdbcUrl=" + jdbcUrl + ", username=" + username + ", password=" + maskedPass + ", sslEnforced=" + sslEnforced + "]";
        }
    }

    /**
     * Converts a raw database connection string into a NormalizedDatabaseConfig.
     *
     * @param rawUrl The raw URL string (postgres://, postgresql://, or jdbc:postgresql://)
     * @param explicitUser Optional explicit username from environment variable
     * @param explicitPass Optional explicit password from environment variable
     * @return NormalizedDatabaseConfig containing standard pgJDBC URL and resolved credentials
     */
    public static NormalizedDatabaseConfig normalize(String rawUrl, String explicitUser, String explicitPass) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return new NormalizedDatabaseConfig(
                    null,
                    normalizeString(explicitUser),
                    normalizeString(explicitPass),
                    false
            );
        }

        String trimmed = rawUrl.trim();
        String extractedUser = null;
        String extractedPass = null;

        // Strip known scheme prefixes
        String withoutScheme = trimmed;
        if (withoutScheme.startsWith("jdbc:postgresql://")) {
            withoutScheme = withoutScheme.substring("jdbc:postgresql://".length());
        } else if (withoutScheme.startsWith("postgres://")) {
            withoutScheme = withoutScheme.substring("postgres://".length());
        } else if (withoutScheme.startsWith("postgresql://")) {
            withoutScheme = withoutScheme.substring("postgresql://".length());
        }

        // Split into authority and pathAndQuery
        int slashIdx = withoutScheme.indexOf('/');
        String authority = (slashIdx >= 0) ? withoutScheme.substring(0, slashIdx) : withoutScheme;
        String pathAndQuery = (slashIdx >= 0) ? withoutScheme.substring(slashIdx) : "/";

        // Check if authority contains user info (before last '@')
        int atIdx = authority.lastIndexOf('@');
        String hostAndPort = authority;
        if (atIdx >= 0) {
            String userInfo = authority.substring(0, atIdx);
            hostAndPort = authority.substring(atIdx + 1);

            int colonIdx = userInfo.indexOf(':');
            if (colonIdx >= 0) {
                extractedUser = safeUrlDecode(userInfo.substring(0, colonIdx));
                extractedPass = safeUrlDecode(userInfo.substring(colonIdx + 1));
            } else {
                extractedUser = safeUrlDecode(userInfo);
            }
        }

        // Parse path and query
        String path = pathAndQuery;
        String query = "";
        int qIdx = pathAndQuery.indexOf('?');
        if (qIdx >= 0) {
            path = pathAndQuery.substring(0, qIdx);
            query = pathAndQuery.substring(qIdx + 1);
        }
        if (path.isEmpty()) {
            path = "/";
        }

        // Host check for remote SSL enforcement
        String hostOnly = hostAndPort;
        int portColonIdx = hostAndPort.indexOf(':');
        if (portColonIdx >= 0) {
            hostOnly = hostAndPort.substring(0, portColonIdx);
        }

        boolean isRemote = !hostOnly.isBlank() &&
                !hostOnly.equalsIgnoreCase("localhost") &&
                !hostOnly.equals("127.0.0.1");

        boolean hasSslMode = query.contains("sslmode=");
        if (isRemote && !hasSslMode) {
            query = query.isEmpty() ? "sslmode=require" : query + "&sslmode=require";
            hasSslMode = true;
        }

        StringBuilder cleanUrl = new StringBuilder("jdbc:postgresql://");
        cleanUrl.append(hostAndPort).append(path);
        if (!query.isEmpty()) {
            cleanUrl.append("?").append(query);
        }

        String finalUser = (explicitUser != null && !explicitUser.isBlank())
                ? explicitUser.trim()
                : extractedUser;
        String finalPass = (explicitPass != null && !explicitPass.isBlank())
                ? explicitPass.trim()
                : extractedPass;

        return new NormalizedDatabaseConfig(cleanUrl.toString(), finalUser, finalPass, hasSslMode);
    }

    private static String safeUrlDecode(String str) {
        if (str == null) return null;
        try {
            return URLDecoder.decode(str, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return str;
        }
    }

    private static String normalizeString(String val) {
        return (val != null && !val.isBlank()) ? val.trim() : null;
    }
}
