package com.aerosentinel.citizen;

import com.aerosentinel.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Robust, privacy-preserving photo storage service for citizen reports.
 * Enforces magic byte inspection, path traversal protection, server-side key generation,
 * and bounded file size checks.
 */
@Service
public class PhotoStorageService {

    private static final Logger log = LoggerFactory.getLogger(PhotoStorageService.class);

    public static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB per file
    public static final long MAX_TOTAL_SIZE_BYTES = 25 * 1024 * 1024; // 25 MB total submission
    public static final int MAX_PHOTOS_PER_REPORT = 5;

    public static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private static final Pattern SAFE_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+\\.(jpg|jpeg|png|webp)$");

    private final Path uploadDir;

    public record StoredPhoto(
            String storageKey,
            String accessUrl,
            Path absolutePath,
            long sizeBytes,
            String mimeType
    ) {}

    public PhotoStorageService(@Value("${app.citizen.upload-dir:uploads/citizen-reports}") String uploadDirStr) {
        this.uploadDir = Paths.get(uploadDirStr).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
                log.info("Initialized citizen report photo storage directory: {}", uploadDir);
            }
        } catch (IOException e) {
            log.error("Fatal: failed to create citizen photo storage directory: {}", uploadDir, e);
            throw new IllegalStateException("Failed to initialize photo storage directory", e);
        }
    }

    /**
     * Inspects, validates, and securely stores an uploaded citizen photograph.
     *
     * @param file the uploaded multipart file
     * @return metadata representing the securely stored photo
     */
    public StoredPhoto storePhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Citizen observation photo is empty or missing");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ValidationException("Uploaded photo exceeds maximum permitted size of 10MB (" + file.getSize() + " bytes)");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new ValidationException("Unsupported image format: " + contentType + ". Permitted formats: JPEG, PNG, WebP.");
        }

        // Validate actual binary contents via magic byte inspection
        String extension = detectAndValidateMagicBytes(file);

        // Generate safe randomized server-side storage key (never trust client filename)
        String storageKey = UUID.randomUUID().toString() + "." + extension;
        Path targetPath = uploadDir.resolve(storageKey).normalize();

        // Enforce path traversal protection
        if (!targetPath.startsWith(uploadDir)) {
            throw new ValidationException("Illegal path traversal attempt detected in upload storage");
        }

        try (InputStream is = file.getInputStream()) {
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored citizen photo {} ({} bytes, MIME: {})", storageKey, file.getSize(), contentType);
        } catch (IOException e) {
            log.error("Failed to write citizen photo to storage: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to persist citizen report image", e);
        }

        String accessUrl = "/api/v1/citizen/photos/" + storageKey;
        return new StoredPhoto(storageKey, accessUrl, targetPath, file.getSize(), contentType);
    }

    /**
     * Stores up to 5 photos with aggregate size validation (max 25MB total).
     * Cleans up any already saved files if any photo fails in the batch.
     */
    public List<StoredPhoto> storePhotos(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }

        List<MultipartFile> validFiles = files.stream()
                .filter(f -> f != null && !f.isEmpty())
                .toList();

        if (validFiles.isEmpty()) {
            return Collections.emptyList();
        }

        if (validFiles.size() > MAX_PHOTOS_PER_REPORT) {
            throw new ValidationException("Maximum " + MAX_PHOTOS_PER_REPORT + " photos permitted per submission (" + validFiles.size() + " provided)");
        }

        long totalBytes = validFiles.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalBytes > MAX_TOTAL_SIZE_BYTES) {
            throw new ValidationException("Total uploaded photos size (" + totalBytes + " bytes) exceeds maximum permitted limit of 25MB");
        }

        List<StoredPhoto> storedList = new ArrayList<>();
        try {
            for (MultipartFile file : validFiles) {
                storedList.add(storePhoto(file));
            }
            return storedList;
        } catch (Exception e) {
            // Rollback / cleanup on partial failure
            for (StoredPhoto sp : storedList) {
                try {
                    deletePhoto(sp.storageKey());
                } catch (Exception cleanupEx) {
                    log.warn("Failed to delete partially uploaded photo {} during rollback: {}", sp.storageKey(), cleanupEx.getMessage());
                }
            }
            throw e;
        }
    }

    /**
     * Safely deletes a stored photo file if it exists, enforcing traversal protection.
     */
    public boolean deletePhoto(String storageKey) {
        if (storageKey == null || !SAFE_KEY_PATTERN.matcher(storageKey.trim()).matches()) {
            return false;
        }
        try {
            Path targetPath = uploadDir.resolve(storageKey.trim()).normalize();
            if (targetPath.startsWith(uploadDir) && Files.exists(targetPath)) {
                Files.delete(targetPath);
                log.info("Deleted photo file from storage: {}", storageKey);
                return true;
            }
        } catch (IOException e) {
            log.warn("Failed to delete photo file {}: {}", storageKey, e.getMessage());
        }
        return false;
    }

    /**
     * Resolves the absolute path for a stored photo key with strict traversal guards.
     */
    public Path resolvePhotoPath(String storageKey) {
        if (storageKey == null || !SAFE_KEY_PATTERN.matcher(storageKey.trim()).matches()) {
            throw new ValidationException("Invalid photo storage key format: " + storageKey);
        }
        Path resolved = uploadDir.resolve(storageKey.trim()).normalize();
        if (!resolved.startsWith(uploadDir) || !Files.exists(resolved)) {
            throw new ValidationException("Photo not found in storage: " + storageKey);
        }
        return resolved;
    }

    /**
     * Loads the photo as a Spring Resource for HTTP download.
     */
    public Resource loadPhotoAsResource(String storageKey) {
        Path path = resolvePhotoPath(storageKey);
        try {
            Resource resource = new UrlResource(path.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new ValidationException("Photo is unreadable: " + storageKey);
        } catch (MalformedURLException e) {
            throw new ValidationException("Invalid photo URI: " + e.getMessage());
        }
    }

    /**
     * Validates image binary header bytes to prevent disguised executable uploads.
     */
    private String detectAndValidateMagicBytes(MultipartFile file) {
        byte[] header = new byte[12];
        try (InputStream is = file.getInputStream()) {
            int read = is.read(header);
            if (read < 4) {
                throw new ValidationException("Invalid or corrupted image header");
            }
        } catch (IOException e) {
            throw new ValidationException("Unable to read image content for validation: " + e.getMessage());
        }

        // JPEG: FF D8 FF
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        // PNG: 89 50 4E 47
        if ((header[0] & 0xFF) == 0x89 && (header[1] & 0xFF) == 0x50 &&
            (header[2] & 0xFF) == 0x4E && (header[3] & 0xFF) == 0x47) {
            return "png";
        }
        // WebP: RIFF ... WEBP (bytes 0-3 == RIFF, bytes 8-11 == WEBP)
        if ((header[0] & 0xFF) == 'R' && (header[1] & 0xFF) == 'I' &&
            (header[2] & 0xFF) == 'F' && (header[3] & 0xFF) == 'F' &&
            header.length >= 12 &&
            (header[8] & 0xFF) == 'W' && (header[9] & 0xFF) == 'E' &&
            (header[10] & 0xFF) == 'B' && (header[11] & 0xFF) == 'P') {
            return "webp";
        }

        throw new ValidationException("Image content does not match standard JPEG, PNG, or WebP binary signatures");
    }
}
