package com.foodbridge.service;

import com.foodbridge.exception.BusinessRuleException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Safe image uploads: size limit, extension whitelist, magic-byte check, random file names
 * (the client's file name is never used, which prevents path traversal).
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final long MAX_BYTES = 2L * 1024 * 1024;

    private final Path root;

    public FileStorageService(@Value("${foodbridge.upload-dir}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    /** Returns the stored file name (not a path). */
    public String store(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessRuleException("The image is too large. Maximum size is 2 MB.");
        }
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(12);
            String ext = detectExtension(head);
            if (ext == null) {
                throw new BusinessRuleException("Only JPG, PNG or WebP images are allowed.");
            }
            Files.createDirectories(root);
            String name = UUID.randomUUID() + "." + ext;
            Path target = root.resolve(name).normalize();
            if (!target.startsWith(root)) {
                throw new BusinessRuleException("Invalid file name.");
            }
            try (InputStream again = file.getInputStream()) {
                Files.copy(again, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return name;
        } catch (IOException e) {
            log.error("Could not store upload", e);
            throw new BusinessRuleException("The image could not be saved. Please try again.");
        }
    }

    /** Deletes a previously stored file; ignores URLs and missing files. */
    public void deleteQuietly(String stored) {
        if (stored == null || stored.startsWith("http")) {
            return;
        }
        try {
            Path target = root.resolve(stored).normalize();
            if (target.startsWith(root)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException e) {
            log.warn("Could not delete {}", stored);
        }
    }

    /** Checks the real file signature instead of trusting the declared content type. */
    static String detectExtension(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') {
            return "png";
        }
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
