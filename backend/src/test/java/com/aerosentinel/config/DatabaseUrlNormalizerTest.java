package com.aerosentinel.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DatabaseUrlNormalizer Unit Tests")
class DatabaseUrlNormalizerTest {

    @Test
    @DisplayName("Normalizes standard Supabase direct URI with dummy credentials")
    void testStandardSupabaseDirectUri() {
        String raw = "postgres://dummy_user:dummy_secret_pass@db.abcdefgh.supabase.co:5432/postgres";
        DatabaseUrlNormalizer.NormalizedDatabaseConfig config =
                DatabaseUrlNormalizer.normalize(raw, null, null);

        assertEquals("jdbc:postgresql://db.abcdefgh.supabase.co:5432/postgres?sslmode=require", config.jdbcUrl());
        assertEquals("dummy_user", config.username());
        assertEquals("dummy_secret_pass", config.password());
        assertTrue(config.sslEnforced());
    }

    @Test
    @DisplayName("Normalizes Supabase connection pooler URI with project-qualified username")
    void testSupabasePoolerUri() {
        String raw = "postgresql://postgres.abcdefgh:pooler_dummy_pass@aws-0-ap-south-1.pooler.supabase.com:6543/postgres?sslmode=require";
        DatabaseUrlNormalizer.NormalizedDatabaseConfig config =
                DatabaseUrlNormalizer.normalize(raw, null, null);

        assertEquals("jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:6543/postgres?sslmode=require", config.jdbcUrl());
        assertEquals("postgres.abcdefgh", config.username());
        assertEquals("pooler_dummy_pass", config.password());
        assertTrue(config.sslEnforced());
    }

    @Test
    @DisplayName("Handles URL-encoded special characters in dummy credentials without breaking host parsing")
    void testUrlEncodedSpecialCharacters() {
        String raw = "postgres://dummy_user:p%40ss%25word%231@db.dummy.supabase.co:5432/aerosentinel";
        DatabaseUrlNormalizer.NormalizedDatabaseConfig config =
                DatabaseUrlNormalizer.normalize(raw, null, null);

        assertEquals("jdbc:postgresql://db.dummy.supabase.co:5432/aerosentinel?sslmode=require", config.jdbcUrl());
        assertEquals("dummy_user", config.username());
        assertEquals("p@ss%word#1", config.password());
        assertTrue(config.sslEnforced());
    }

    @Test
    @DisplayName("Explicit environment credentials take precedence over URL-embedded credentials")
    void testExplicitCredentialsPrecedence() {
        String raw = "postgres://url_user:url_pass@db.dummy.supabase.co:5432/postgres";
        DatabaseUrlNormalizer.NormalizedDatabaseConfig config =
                DatabaseUrlNormalizer.normalize(raw, "explicit_user", "explicit_pass");

        assertEquals("jdbc:postgresql://db.dummy.supabase.co:5432/postgres?sslmode=require", config.jdbcUrl());
        assertEquals("explicit_user", config.username());
        assertEquals("explicit_pass", config.password());
    }

    @Test
    @DisplayName("Preserves existing jdbc:postgresql:// format while extracting separate credentials")
    void testAlreadyJdbcFormattedUrl() {
        String raw = "jdbc:postgresql://db.dummy.supabase.co:5432/postgres?sslmode=require";
        DatabaseUrlNormalizer.NormalizedDatabaseConfig config =
                DatabaseUrlNormalizer.normalize(raw, "admin_user", "admin_pass");

        assertEquals("jdbc:postgresql://db.dummy.supabase.co:5432/postgres?sslmode=require", config.jdbcUrl());
        assertEquals("admin_user", config.username());
        assertEquals("admin_pass", config.password());
        assertTrue(config.sslEnforced());
    }

    @Test
    @DisplayName("Localhost development URLs do not enforce sslmode=require")
    void testLocalhostDevelopmentUrl() {
        String raw = "postgres://local_user:local_pass@localhost:5432/aerosentinel";
        DatabaseUrlNormalizer.NormalizedDatabaseConfig config =
                DatabaseUrlNormalizer.normalize(raw, null, null);

        assertEquals("jdbc:postgresql://localhost:5432/aerosentinel", config.jdbcUrl());
        assertEquals("local_user", config.username());
        assertEquals("local_pass", config.password());
        assertFalse(config.sslEnforced());
    }

    @Test
    @DisplayName("toString() masks passwords to prevent accidental secret leakage in logs")
    void testPasswordMaskingInToString() {
        String raw = "postgres://user:super_secret_password@db.dummy.supabase.co:5432/postgres";
        DatabaseUrlNormalizer.NormalizedDatabaseConfig config =
                DatabaseUrlNormalizer.normalize(raw, null, null);

        String strRepresentation = config.toString();
        assertFalse(strRepresentation.contains("super_secret_password"), "Password must never be logged or in toString");
        assertTrue(strRepresentation.contains("******"));
    }

    @Test
    @DisplayName("Gracefully handles null or empty input")
    void testNullOrBlankInput() {
        DatabaseUrlNormalizer.NormalizedDatabaseConfig config =
                DatabaseUrlNormalizer.normalize("", null, null);
        assertNull(config.jdbcUrl());
        assertNull(config.username());
        assertNull(config.password());
    }
}
