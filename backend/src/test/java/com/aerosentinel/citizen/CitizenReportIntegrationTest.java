package com.aerosentinel.citizen;

import com.aerosentinel.alert.AlertRepository;
import com.aerosentinel.city.City;
import com.aerosentinel.city.CityRepository;
import com.aerosentinel.model.GeminiAnalysis;
import com.aerosentinel.repository.GeminiAnalysisRepository;
import com.aerosentinel.util.H3Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CitizenReportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CitizenReportRepository citizenReportRepository;

    @Autowired
    private GeminiAnalysisRepository geminiAnalysisRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private com.aerosentinel.security.JwtService jwtService;

    @Autowired
    private CitizenLookupRateLimiter lookupRateLimiter;

    @Autowired
    private CitizenReportService citizenReportService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private UUID createTestUser(String email, String role) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, role, active, created_at) VALUES (?, ?, ?, ?, ?, true, NOW())",
                userId, email, "{noop}testpass", "Citizen Test User", role
        );
        return userId;
    }

    private UUID puneCityId;

    // Real coordinates in Shivajinagar, Pune Metropolitan Region
    private static final double REAL_PUNE_LAT = 18.5304;
    private static final double REAL_PUNE_LNG = 73.8467;

    // Standard valid JPEG header bytes
    private static final byte[] SAMPLE_JPEG_BYTES = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1};

    @BeforeEach
    void setUp() {
        if (lookupRateLimiter != null) {
            lookupRateLimiter.reset();
        }
        City city = cityRepository.findByNameIgnoreCase("Pune")
                .orElseGet(() -> {
                    City c = new City();
                    c.setName("Pune");
                    c.setState("Maharashtra");
                    c.setCountry("India");
                    c.setLatitude(REAL_PUNE_LAT);
                    c.setLongitude(REAL_PUNE_LNG);
                    return cityRepository.save(c);
                });
        puneCityId = city.getId();
    }

    @Test
    @DisplayName("End-to-end integration: multipart submission -> H3 res 8 -> citizen_reports -> GeminiAnalysis -> GET report -> No direct alert")
    void testEndToEndCitizenReportFlow() throws Exception {
        long alertCountBefore = alertRepository.count();

        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "shivajinagar_smoke.jpg",
                "image/jpeg",
                SAMPLE_JPEG_BYTES
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/citizen/reports")
                        .file(photo)
                        .param("cityId", puneCityId.toString())
                        .param("latitude", String.valueOf(REAL_PUNE_LAT))
                        .param("longitude", String.valueOf(REAL_PUNE_LNG))
                        .param("category", "SMOKE")
                        .param("description", "Heavy localized black smoke plume observed near transport terminal.")
                        .param("observedAt", "2026-09-28T12:00:00Z"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.h3Index").exists())
                .andExpect(jsonPath("$.category").value("SMOKE"))
                .andExpect(jsonPath("$.status").exists())
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID reportId = UUID.fromString(responseJson.get("id").asText());
        String h3Index = responseJson.get("h3Index").asText();

        // 1. Verify H3 resolution 8 derivation
        String expectedH3 = H3Utils.coordinatesToH3(REAL_PUNE_LAT, REAL_PUNE_LNG, 8);
        assertThat(h3Index).isEqualTo(expectedH3);
        assertThat(h3Index).hasSize(15);

        // 2. Verify PostgreSQL persistence in citizen_reports
        CitizenReport persistedReport = citizenReportRepository.findById(reportId).orElse(null);
        assertThat(persistedReport).isNotNull();
        assertThat(persistedReport.getCityId()).isEqualTo(puneCityId);
        assertThat(persistedReport.getH3Index()).isEqualTo(expectedH3);
        assertThat(persistedReport.getImageUrl()).isNotNull();
        assertThat(persistedReport.getStatus()).isIn("ANALYZED", "PENDING");

        // 3. Verify PostgreSQL persistence in gemini_analyses with foreign key citizen_report_id
        List<GeminiAnalysis> analyses = geminiAnalysisRepository.findByCitizenReportIdOrderByCreatedAtDesc(reportId);
        assertThat(analyses).isNotEmpty();
        GeminiAnalysis ga = analyses.get(0);
        assertThat(ga.getCitizenReportId()).isEqualTo(reportId);
        assertThat(ga.getH3Index()).isEqualTo(expectedH3);
        assertThat(ga.getDetectedCategory()).isNotNull();
        assertThat(ga.getConfidence()).isNotNull();
        assertThat(ga.getConfidence()).isBetween(0.0, 1.0);
        assertThat(ga.getIsGrounded()).isTrue();

        // 4. Verify GET /api/v1/citizen/reports/{reportId}
        mockMvc.perform(get("/api/v1/citizen/reports/" + reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reportId.toString()))
                .andExpect(jsonPath("$.h3Index").value(expectedH3))
                .andExpect(jsonPath("$.visionAnalysis").exists())
                .andExpect(jsonPath("$.visionAnalysis.detectedCategory").value(ga.getDetectedCategory()));

        // 5. Verify Photo Retrieval endpoint
        String imageUrl = persistedReport.getImageUrl();
        String storageKey = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
        mockMvc.perform(get("/api/v1/citizen/photos/" + storageKey))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));

        // 6. Strict Governance: Verify citizen report NEVER directly created an alert
        long alertCountAfter = alertRepository.count();
        assertThat(alertCountAfter).isEqualTo(alertCountBefore);
    }

    @Test
    @DisplayName("F6-P7 Failure Case 1: Rejects report missing mandatory geographic coordinates")
    void testFailureCase1_missingLocationReturnsBadRequest() throws Exception {
        long reportCountBefore = citizenReportRepository.count();

        mockMvc.perform(multipart("/api/v1/citizen/reports")
                        .param("cityId", puneCityId.toString())
                        .param("category", "SMOKE")
                        .param("description", "Missing coordinates"))
                .andExpect(status().isBadRequest());

        assertThat(citizenReportRepository.count()).isEqualTo(reportCountBefore);
    }

    @Test
    @DisplayName("F6-P7 Failure Case 2: Rejects report with out-of-bounds latitude/longitude")
    void testFailureCase2_outOfBoundsCoordinatesReturnsBadRequest() throws Exception {
        long reportCountBefore = citizenReportRepository.count();

        mockMvc.perform(multipart("/api/v1/citizen/reports")
                        .param("cityId", puneCityId.toString())
                        .param("latitude", "95.5")
                        .param("longitude", String.valueOf(REAL_PUNE_LNG))
                        .param("category", "SMOKE")
                        .param("description", "Out of bounds latitude"))
                .andExpect(status().isBadRequest());

        assertThat(citizenReportRepository.count()).isEqualTo(reportCountBefore);
    }

    @Test
    @DisplayName("F6-P7 Failure Case 3: Rejects unsupported file MIME type (e.g. application/pdf)")
    void testFailureCase3_unsupportedFileFormatReturnsBadRequest() throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "photo",
                "evidence_document.pdf",
                "application/pdf",
                "%PDF-1.5 test content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/citizen/reports")
                        .file(pdfFile)
                        .param("cityId", puneCityId.toString())
                        .param("latitude", String.valueOf(REAL_PUNE_LAT))
                        .param("longitude", String.valueOf(REAL_PUNE_LNG))
                        .param("category", "SMOKE")
                        .param("description", "Attempting PDF upload"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("F6-P7 Failure Case 4: Rejects file with spoofed .jpg extension and invalid magic bytes")
    void testFailureCase4_fakeExtensionWithInvalidMagicBytesReturnsBadRequest() throws Exception {
        MockMultipartFile fakeJpg = new MockMultipartFile(
                "photo",
                "malicious_script.jpg",
                "image/jpeg",
                "echo 'not a real jpeg binary image content'".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/citizen/reports")
                        .file(fakeJpg)
                        .param("cityId", puneCityId.toString())
                        .param("latitude", String.valueOf(REAL_PUNE_LAT))
                        .param("longitude", String.valueOf(REAL_PUNE_LNG))
                        .param("category", "SMOKE")
                        .param("description", "Spoofed file upload"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("F6-P7 Failure Case 5: Rejects upload exceeding 15MB size limit")
    void testFailureCase5_oversizedPhotoReturnsBadRequest() throws Exception {
        byte[] oversizedBytes = new byte[16];
        System.arraycopy(SAMPLE_JPEG_BYTES, 0, oversizedBytes, 0, SAMPLE_JPEG_BYTES.length);

        MockMultipartFile oversizedFile = new MockMultipartFile(
                "photo",
                "giant_raw_image.jpg",
                "image/jpeg",
                oversizedBytes
        ) {
            @Override
            public long getSize() {
                return (15 * 1024 * 1024) + 1024; // 15MB + 1KB
            }
        };

        mockMvc.perform(multipart("/api/v1/citizen/reports")
                        .file(oversizedFile)
                        .param("cityId", puneCityId.toString())
                        .param("latitude", String.valueOf(REAL_PUNE_LAT))
                        .param("longitude", String.valueOf(REAL_PUNE_LNG))
                        .param("category", "SMOKE")
                        .param("description", "Oversized upload"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("F6-P7 Text-Only Success: Allows submission without photo without creating synthetic Gemini analysis")
    void testTextOnlyReportSubmitsCleanlyWithoutGeminiAnalysis() throws Exception {
        long analysisCountBefore = geminiAnalysisRepository.count();

        MvcResult result = mockMvc.perform(multipart("/api/v1/citizen/reports")
                        .param("cityId", puneCityId.toString())
                        .param("latitude", String.valueOf(REAL_PUNE_LAT))
                        .param("longitude", String.valueOf(REAL_PUNE_LNG))
                        .param("category", "ODOR")
                        .param("description", "Pungent chemical odor detected near industrial gate"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.category").value("ODOR"))
                .andExpect(jsonPath("$.visionAnalysis").doesNotExist())
                .andReturn();

        // Verify zero GeminiAnalysis rows were fabricated
        assertThat(geminiAnalysisRepository.count()).isEqualTo(analysisCountBefore);
    }

    @Test
    @DisplayName("Phase 6: Multi-photo submission succeeds and stores up to 5 photos")
    void testMultiPhotoSubmissionSucceeds() throws Exception {
        MockMultipartFile p1 = new MockMultipartFile("photos", "p1.jpg", "image/jpeg", SAMPLE_JPEG_BYTES);
        MockMultipartFile p2 = new MockMultipartFile("photos", "p2.jpg", "image/jpeg", SAMPLE_JPEG_BYTES);

        MvcResult result = mockMvc.perform(multipart("/api/v1/citizen/reports")
                        .file(p1)
                        .file(p2)
                        .param("cityId", puneCityId.toString())
                        .param("latitude", String.valueOf(REAL_PUNE_LAT))
                        .param("longitude", String.valueOf(REAL_PUNE_LNG))
                        .param("category", "SMOKE")
                        .param("description", "Multiple photo angles of visible emission"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.photoUrls").isArray())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("photoUrls").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Phase 6: Rejection when exceeding 5 photos per submission")
    void testRejectsExceedingFivePhotosInReport() throws Exception {
        var builder = multipart("/api/v1/citizen/reports");
        for (int i = 0; i < 6; i++) {
            builder.file(new MockMultipartFile("photos", "p" + i + ".jpg", "image/jpeg", SAMPLE_JPEG_BYTES));
        }
        builder.param("cityId", puneCityId.toString())
                .param("latitude", String.valueOf(REAL_PUNE_LAT))
                .param("longitude", String.valueOf(REAL_PUNE_LNG))
                .param("category", "SMOKE")
                .param("description", "Excessive photos");

        mockMvc.perform(builder).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Phase 6: Rejects future incident timestamps")
    void testRejectsFutureIncidentTimestamp() throws Exception {
        mockMvc.perform(multipart("/api/v1/citizen/reports")
                        .param("cityId", puneCityId.toString())
                        .param("latitude", String.valueOf(REAL_PUNE_LAT))
                        .param("longitude", String.valueOf(REAL_PUNE_LNG))
                        .param("category", "SMOKE")
                        .param("description", "Future event")
                        .param("observedAt", java.time.Instant.now().plus(java.time.Duration.ofDays(2)).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Phase 6: Receipt reference lookup resolves report status")
    void testLookupByReceiptReference() throws Exception {
        MvcResult submitResult = mockMvc.perform(multipart("/api/v1/citizen/reports")
                        .param("cityId", puneCityId.toString())
                        .param("latitude", String.valueOf(REAL_PUNE_LAT))
                        .param("longitude", String.valueOf(REAL_PUNE_LNG))
                        .param("category", "DUST")
                        .param("description", "Road construction dust"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(submitResult.getResponse().getContentAsString());
        String repId = json.get("reportId").asText();

        mockMvc.perform(get("/api/v1/citizen/reports/reference/" + repId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(repId))
                .andExpect(jsonPath("$.category").value("DUST"));
    }

    @Test
    @DisplayName("Security: Receipt lookup applies data minimization for unauthenticated citizen tracking")
    void testReceiptLookupDataMinimization() throws Exception {
        MockMultipartFile photo = new MockMultipartFile("photo", "private_evidence.jpg", "image/jpeg", SAMPLE_JPEG_BYTES);

        MvcResult submitResult = mockMvc.perform(multipart("/api/v1/citizen/reports")
                        .file(photo)
                        .param("cityId", puneCityId.toString())
                        .param("latitude", String.valueOf(REAL_PUNE_LAT))
                        .param("longitude", String.valueOf(REAL_PUNE_LNG))
                        .param("category", "BURNING")
                        .param("description", "Very specific private observation details near house #42"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(submitResult.getResponse().getContentAsString());
        String refId = json.get("reportId").asText();

        // Query anonymously through public receipt lookup
        MvcResult lookupResult = mockMvc.perform(get("/api/v1/citizen/reports/reference/" + refId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(refId))
                .andExpect(jsonPath("$.category").value("BURNING"))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.h3Index").exists())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.cityId").doesNotExist())
                .andExpect(jsonPath("$.latitude").doesNotExist())
                .andExpect(jsonPath("$.longitude").doesNotExist())
                .andExpect(jsonPath("$.imageUrl").doesNotExist())
                .andExpect(jsonPath("$.photoUrls").isEmpty())
                .andReturn();

        JsonNode lookupJson = objectMapper.readTree(lookupResult.getResponse().getContentAsString());
        // Description is masked with standard audit notice
        assertThat(lookupJson.get("description").asText()).contains("auxiliary spatial evaluation");
    }

    @Test
    @DisplayName("Security: Authenticated owner can access full dossier via receipt reference")
    void testReceiptLookupAuthenticatedOwnerFullDossier() throws Exception {
        UUID ownerId = createTestUser("owner-" + UUID.randomUUID() + "@example.com", "CITIZEN");
        String ownerToken = "Bearer " + jwtService.generateToken(ownerId.toString(), "CITIZEN");

        MockMultipartFile photo = new MockMultipartFile("photo", "owner_pic.jpg", "image/jpeg", SAMPLE_JPEG_BYTES);

        // Submit as authenticated owner
        CitizenReportResponseDto submitted = citizenReportService.submitReport(
                puneCityId,
                REAL_PUNE_LAT,
                REAL_PUNE_LNG,
                "SMOKE",
                "Owner private note",
                List.of(photo),
                null,
                ownerId
        );
        String refId = submitted.reportId();

        // Query with owner token
        mockMvc.perform(get("/api/v1/citizen/reports/reference/" + refId)
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.cityId").exists())
                .andExpect(jsonPath("$.latitude").value(REAL_PUNE_LAT))
                .andExpect(jsonPath("$.longitude").value(REAL_PUNE_LNG))
                .andExpect(jsonPath("$.description").value("Owner private note"))
                .andExpect(jsonPath("$.photoUrls").isArray());
    }

    @Test
    @DisplayName("Security: Enforce rate limiting on receipt reference lookup to prevent brute-force guessing")
    void testReceiptLookupRateLimiterEnforced() throws Exception {
        lookupRateLimiter.reset();

        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/api/v1/citizen/reports/reference/CR-NONEXIST" + i))
                    .andExpect(status().isNotFound());
        }

        // 21st attempt exceeds rate limit
        mockMvc.perform(get("/api/v1/citizen/reports/reference/CR-NONEXIST99"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    @DisplayName("Security: IDOR protection on report details by ID prevents access to other users' reports")
    void testReportDetailIdorProtection() throws Exception {
        UUID userA = createTestUser("user-a-" + UUID.randomUUID() + "@example.com", "CITIZEN");
        UUID userB = createTestUser("user-b-" + UUID.randomUUID() + "@example.com", "CITIZEN");

        String tokenA = "Bearer " + jwtService.generateToken(userA.toString(), "CITIZEN");
        String tokenB = "Bearer " + jwtService.generateToken(userB.toString(), "CITIZEN");
        String authorityToken = "Bearer " + jwtService.generateToken("authority-user", "AUTHORITY");

        CitizenReportResponseDto reportA = citizenReportService.submitReport(
                puneCityId,
                REAL_PUNE_LAT,
                REAL_PUNE_LNG,
                "ODOR",
                "User A confidential report",
                Collections.emptyList(),
                null,
                userA
        );

        UUID reportId = reportA.id();

        // 1. User B cannot access User A's report by ID -> 403 Forbidden
        mockMvc.perform(get("/api/v1/citizen/reports/" + reportId)
                        .header("Authorization", tokenB))
                .andExpect(status().isForbidden());

        // 2. Anonymous caller cannot access User A's report by ID -> 403 Forbidden
        mockMvc.perform(get("/api/v1/citizen/reports/" + reportId))
                .andExpect(status().isForbidden());

        // 3. User A can access own report -> 200 OK
        mockMvc.perform(get("/api/v1/citizen/reports/" + reportId)
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("User A confidential report"));

        // 4. Authority can access report -> 200 OK
        mockMvc.perform(get("/api/v1/citizen/reports/" + reportId)
                        .header("Authorization", authorityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("User A confidential report"));
    }

    @Test
    @DisplayName("Security: Photo delivery authorization prevents unauthorized photo access for user reports")
    void testPhotoAuthorizationProtection() throws Exception {
        UUID userA = createTestUser("photo-user-a-" + UUID.randomUUID() + "@example.com", "CITIZEN");
        UUID userB = createTestUser("photo-user-b-" + UUID.randomUUID() + "@example.com", "CITIZEN");

        String tokenA = "Bearer " + jwtService.generateToken(userA.toString(), "CITIZEN");
        String tokenB = "Bearer " + jwtService.generateToken(userB.toString(), "CITIZEN");
        String authorityToken = "Bearer " + jwtService.generateToken("authority-user", "AUTHORITY");

        MockMultipartFile photo = new MockMultipartFile("photos", "classified_scene.jpg", "image/jpeg", SAMPLE_JPEG_BYTES);

        CitizenReportResponseDto reportA = citizenReportService.submitReport(
                puneCityId,
                REAL_PUNE_LAT,
                REAL_PUNE_LNG,
                "SMOKE",
                "Classified observation",
                List.of(photo),
                null,
                userA
        );

        String photoUrl = reportA.photoUrls().get(0);
        String storageKey = photoUrl.substring(photoUrl.lastIndexOf('/') + 1);

        // 1. Anonymous access is denied -> 403 Forbidden
        mockMvc.perform(get("/api/v1/citizen/photos/" + storageKey))
                .andExpect(status().isForbidden());

        // 2. Different user access is denied -> 403 Forbidden
        mockMvc.perform(get("/api/v1/citizen/photos/" + storageKey)
                        .header("Authorization", tokenB))
                .andExpect(status().isForbidden());

        // 3. Owner access is allowed -> 200 OK
        mockMvc.perform(get("/api/v1/citizen/photos/" + storageKey)
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));

        // 4. Authority access is allowed -> 200 OK
        mockMvc.perform(get("/api/v1/citizen/photos/" + storageKey)
                        .header("Authorization", authorityToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));
    }

    @Test
    @DisplayName("Security: Non-existent reference returns clean 404 without internal stack traces")
    void testNonExistentReferenceCleanError() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/citizen/reports/reference/CR-NOTREAL1"))
                .andExpect(status().isNotFound())
                .andReturn();

        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.has("status")).isTrue();
        assertThat(json.get("status").asInt()).isEqualTo(404);
        assertThat(json.get("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(json.has("message")).isTrue();
        // Assert no stack trace is leaked
        assertThat(json.has("trace")).isFalse();
        assertThat(json.has("stackTrace")).isFalse();
    }
}
