package com.smartstudy.planning.service;

import com.smartstudy.planning.config.StorageProperties;
import com.smartstudy.shared.logging.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Manages PDF file storage on disk using the structure:
 * {baseDir}/{userId}/{courseId}/{materialId}.pdf
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private final Path baseDir;

    public FileStorageService(StorageProperties props) {
        this.baseDir = Path.of(props.baseDir());
        log.info("File storage base directory: {}", baseDir.toAbsolutePath());
    }

    /**
     * Save a PDF file to disk.
     *
     * @return the relative file path stored (for persisting in the database)
     */
    public String save(String userId, UUID courseId, UUID materialId, InputStream input) throws IOException {
        Path dir = baseDir.resolve(userId).resolve(courseId.toString());
        Files.createDirectories(dir);

        String fileName = materialId + ".pdf";
        Path filePath = dir.resolve(fileName);
        Files.copy(input, filePath, StandardCopyOption.REPLACE_EXISTING);

        String relativePath = baseDir.relativize(filePath).toString();
        log.info("Saved material file: {}", relativePath);
        return relativePath;
    }

    /**
     * Save a file that is not attached to a course yet:
     * {baseDir}/{userId}/unattached/{materialId}.pdf
     */
    public String saveUnattached(String userId, UUID materialId, InputStream input) throws IOException {
        Path dir = baseDir.resolve(userId).resolve("unattached");
        Files.createDirectories(dir);
        Path filePath = dir.resolve(materialId + ".pdf");
        Files.copy(input, filePath, StandardCopyOption.REPLACE_EXISTING);
        String relativePath = baseDir.relativize(filePath).toString();
        log.info("Saved unattached material file: {}", relativePath);
        return relativePath;
    }

    /**
     * Move a stored file into a course directory (used when a material is
     * linked to a course). Returns the new relative path, or the old one when
     * the move is impossible so the stored filePath keeps resolving.
     */
    public String moveToCourse(String relativePath, String userId, UUID courseId, UUID materialId) {
        Path source = baseDir.resolve(relativePath);
        if (Files.notExists(source)) {
            return relativePath;
        }
        try {
            Path dir = baseDir.resolve(userId).resolve(courseId.toString());
            Files.createDirectories(dir);
            Path target = dir.resolve(materialId + ".pdf");
            if (source.equals(target)) {
                return relativePath;
            }
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            String newRelative = baseDir.relativize(target).toString();
            log.info("Moved material file {} -> {}", relativePath, newRelative);
            return newRelative;
        } catch (IOException e) {
            log.warn("Failed to move material file {}: {}", relativePath, e.getMessage());
            return relativePath;
        }
    }

    /**
     * Get the absolute Path to a stored material file.
     */
    public Path load(String userId, UUID courseId, UUID materialId) {
        return baseDir
                .resolve(userId)
                .resolve(courseId.toString())
                .resolve(materialId + ".pdf");
    }

    /**
     * Resolve a relative file path against baseDir.
     */
    public Path resolve(String relativePath) {
        return baseDir.resolve(relativePath);
    }

    /**
     * Delete a single material file from disk.
     */
    public void delete(String userId, UUID courseId, UUID materialId) {
        Path filePath = load(userId, courseId, materialId);
        try {
            if (Files.deleteIfExists(filePath)) {
                log.info("Deleted material file: {}", filePath);
            }
        } catch (IOException e) {
            log.warn("Failed to delete material file: {}", filePath, e);
        }
    }

    /**
     * Delete all material files for a course (used on course deletion).
     */
    public void deleteCourseDir(String userId, UUID courseId) {
        Path courseDir = baseDir.resolve(userId).resolve(courseId.toString());
        deleteDirectoryRecursively(courseDir);
    }

    /**
     * Delete all material files for a user (GDPR compliance).
     */
    public void deleteUserDir(String userId) {
        Path userDir = baseDir.resolve(userId);
        deleteDirectoryRecursively(userDir);
    }

    private void deleteDirectoryRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.warn("Failed to delete: {}", path, e);
                        }
                    });
            log.info("Deleted directory: {}", dir);
        } catch (IOException e) {
            log.warn("Failed to walk directory for deletion: {}", dir, e);
        }
    }
}
