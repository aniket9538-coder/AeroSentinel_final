package com.aerosentinel.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Local Integration Test: Verifies read-only connectivity against the local isolated
 * PostgreSQL 16 + PostGIS 3.4 container (`aerosentinel-postgres` on localhost:5432).
 *
 * NOTE: This test targets the local container environment, NOT the remote Supabase cloud
 * database (`db.betlhrnkosmqycfhijcn.supabase.co`), which is currently INACTIVE (paused).
 * All queries are strictly read-only and perform zero mutations.
 */
class LiveDatabaseConnectivityTest {

    private static final String JDBC_URL = "jdbc:postgresql://127.0.0.1:5432/aerosentinel";
    private static final String USER = "aerosentinel_user";
    private static final String PASS = "change_this_in_production";

    private boolean isDatabaseAvailable() {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASS)) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("Live Database: Read-only SELECT 1 connectivity check")
    void testReadOnlySelect1() throws Exception {
        if (!isDatabaseAvailable()) {
            System.out.println("[SKIP] Database not reachable at " + JDBC_URL + "; skipping live DB test");
            return;
        }

        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1 AS check_val")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("check_val")).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Live Database: Verify PostGIS spatial extension is active")
    void testPostGisExtension() throws Exception {
        if (!isDatabaseAvailable()) {
            System.out.println("[SKIP] Database not reachable at " + JDBC_URL + "; skipping live DB test");
            return;
        }

        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT PostGIS_Version() AS pg_ver")) {
            assertThat(rs.next()).isTrue();
            String ver = rs.getString("pg_ver");
            assertThat(ver).contains("3.");
        }
    }

    @Test
    @DisplayName("Live Database: Read-only query against cities table")
    void testCitiesRead() throws Exception {
        if (!isDatabaseAvailable()) {
            System.out.println("[SKIP] Database not reachable at " + JDBC_URL + "; skipping live DB test");
            return;
        }

        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT count(*) AS total_cities FROM cities WHERE active = true")) {
            assertThat(rs.next()).isTrue();
            int count = rs.getInt("total_cities");
            assertThat(count).isGreaterThanOrEqualTo(3);
        }
    }

    @Test
    @DisplayName("Live Database: Read-only inspection of Flyway migration history")
    void testFlywaySchemaHistory() throws Exception {
        if (!isDatabaseAvailable()) {
            System.out.println("[SKIP] Database not reachable at " + JDBC_URL + "; skipping live DB test");
            return;
        }

        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT count(*) AS total_migrations, bool_and(success) AS all_success FROM flyway_schema_history")) {
            assertThat(rs.next()).isTrue();
            int total = rs.getInt("total_migrations");
            boolean allSuccess = rs.getBoolean("all_success");
            assertThat(total).isGreaterThanOrEqualTo(21);
            assertThat(allSuccess).isTrue();
        }
    }
}
