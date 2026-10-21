package com.aerosentinel.citizen;

import com.aerosentinel.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhotoStorageServiceTest {

    @TempDir
    Path tempDir;

    private PhotoStorageService photoStorageService;

    // Standard magic byte signatures
    private static final byte[] VALID_JPEG_BYTES = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1};
    private static final byte[] VALID_PNG_BYTES = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    private static final byte[] VALID_WEBP_BYTES = new byte[]{'R', 'I', 'F', 'F', 100, 0, 0, 0, 'W', 'E', 'B', 'P'};

    @BeforeEach
    void setUp() {
        photoStorageService = new PhotoStorageService(tempDir.toString());
        photoStorageService.init();
    }

    @Test
    @DisplayName("Successfully stores valid JPEG image with randomized server-side storage key")
    void testStoreValidJpeg() {
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "test_camera_photo.jpg",
                "image/jpeg",
                VALID_JPEG_BYTES
        );

        PhotoStorageService.StoredPhoto stored = photoStorageService.storePhoto(file);

        assertThat(stored).isNotNull();
        assertThat(stored.storageKey()).endsWith(".jpg");
        assertThat(stored.accessUrl()).isEqualTo("/api/v1/citizen/photos/" + stored.storageKey());
        assertThat(Files.exists(stored.absolutePath())).isTrue();
        assertThat(stored.sizeBytes()).isEqualTo(VALID_JPEG_BYTES.length);
    }

    @Test
    @DisplayName("Successfully stores valid PNG image")
    void testStoreValidPng() {
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "screenshot.png",
                "image/png",
                VALID_PNG_BYTES
        );

        PhotoStorageService.StoredPhoto stored = photoStorageService.storePhoto(file);

        assertThat(stored).isNotNull();
        assertThat(stored.storageKey()).endsWith(".png");
        assertThat(Files.exists(stored.absolutePath())).isTrue();
    }

    @Test
    @DisplayName("Successfully stores valid WebP image")
    void testStoreValidWebp() {
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "mobile_capture.webp",
                "image/webp",
                VALID_WEBP_BYTES
        );

        PhotoStorageService.StoredPhoto stored = photoStorageService.storePhoto(file);

        assertThat(stored).isNotNull();
        assertThat(stored.storageKey()).endsWith(".webp");
        assertThat(Files.exists(stored.absolutePath())).isTrue();
    }

    @Test
    @DisplayName("Rejects upload exceeding maximum size limit of 10MB")
    void testRejectsOversizedPhoto() {
        byte[] oversizedBytes = new byte[16];
        System.arraycopy(VALID_JPEG_BYTES, 0, oversizedBytes, 0, VALID_JPEG_BYTES.length);

        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "giant_camera_raw.jpg",
                "image/jpeg",
                oversizedBytes
        ) {
            @Override
            public long getSize() {
                return 11 * 1024 * 1024L; // 11 MB
            }
        };

        assertThatThrownBy(() -> photoStorageService.storePhoto(file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("exceeds maximum permitted size of 10MB");
    }

    @Test
    @DisplayName("Successfully stores multiple valid photos up to 5 photos")
    void testStoreMultiplePhotos() {
        MockMultipartFile file1 = new MockMultipartFile("photo1", "p1.jpg", "image/jpeg", VALID_JPEG_BYTES);
        MockMultipartFile file2 = new MockMultipartFile("photo2", "p2.png", "image/png", VALID_PNG_BYTES);
        MockMultipartFile file3 = new MockMultipartFile("photo3", "p3.webp", "image/webp", VALID_WEBP_BYTES);

        java.util.List<PhotoStorageService.StoredPhoto> stored = photoStorageService.storePhotos(java.util.List.of(file1, file2, file3));
        assertThat(stored).hasSize(3);
        for (PhotoStorageService.StoredPhoto p : stored) {
            assertThat(Files.exists(p.absolutePath())).isTrue();
        }
    }

    @Test
    @DisplayName("Rejects upload exceeding 5 photos per submission")
    void testRejectsExceedingFivePhotos() {
        java.util.List<org.springframework.web.multipart.MultipartFile> list = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            list.add(new MockMultipartFile("photo" + i, "p" + i + ".jpg", "image/jpeg", VALID_JPEG_BYTES));
        }

        assertThatThrownBy(() -> photoStorageService.storePhotos(list))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Maximum 5 photos permitted");
    }

    @Test
    @DisplayName("Rejects upload exceeding total aggregate limit of 25MB")
    void testRejectsExceedingTotalSizeLimit() {
        MockMultipartFile large1 = new MockMultipartFile("p1", "p1.jpg", "image/jpeg", VALID_JPEG_BYTES) {
            @Override public long getSize() { return 9 * 1024 * 1024L; }
        };
        MockMultipartFile large2 = new MockMultipartFile("p2", "p2.jpg", "image/jpeg", VALID_JPEG_BYTES) {
            @Override public long getSize() { return 9 * 1024 * 1024L; }
        };
        MockMultipartFile large3 = new MockMultipartFile("p3", "p3.jpg", "image/jpeg", VALID_JPEG_BYTES) {
            @Override public long getSize() { return 9 * 1024 * 1024L; }
        };

        assertThatThrownBy(() -> photoStorageService.storePhotos(java.util.List.of(large1, large2, large3)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("exceeds maximum permitted limit of 25MB");
    }

    @Test
    @DisplayName("Rejects unsupported MIME type (e.g. PDF or text)")
    void testRejectsUnsupportedMimeType() {
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "document.pdf",
                "application/pdf",
                new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '5'}
        );

        assertThatThrownBy(() -> photoStorageService.storePhoto(file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unsupported image format");
    }

    @Test
    @DisplayName("Rejects image with spoofed extension and invalid binary magic bytes")
    void testRejectsCorruptedOrSpoofedImage() {
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "malicious_script.jpg",
                "image/jpeg",
                new byte[]{'<', '?', 'p', 'h', 'p', ' ', 'e', 'v', 'a', 'l'}
        );

        assertThatThrownBy(() -> photoStorageService.storePhoto(file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("does not match standard JPEG, PNG, or WebP binary signatures");
    }

    @Test
    @DisplayName("Guards against path traversal in photo resolution and loading")
    void testPathTraversalGuards() {
        assertThatThrownBy(() -> photoStorageService.resolvePhotoPath("../../etc/passwd"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid photo storage key format");

        assertThatThrownBy(() -> photoStorageService.resolvePhotoPath("nonexistent-key-12345.jpg"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Photo not found in storage");
    }

    @Test
    @DisplayName("Loads stored photo as readable Resource")
    void testLoadPhotoAsResource() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "sample.jpg",
                "image/jpeg",
                VALID_JPEG_BYTES
        );
        PhotoStorageService.StoredPhoto stored = photoStorageService.storePhoto(file);

        Resource resource = photoStorageService.loadPhotoAsResource(stored.storageKey());
        assertThat(resource).isNotNull();
        assertThat(resource.exists()).isTrue();
        assertThat(resource.isReadable()).isTrue();
    }
}
