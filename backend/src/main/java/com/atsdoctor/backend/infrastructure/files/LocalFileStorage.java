package com.atsdoctor.backend.infrastructure.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * Local disk storage for uploaded resumes and JD files (FEAT-010, TASK-028;
 * FEAT-017/018, TASK-039/040). Files are written with a server-generated name
 * ({@code <id>.<safe-ext>}) so the original filename is never used as a path
 * component — path traversal is impossible.
 */
@Component
public class LocalFileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private final Path resumeRoot;
    private final Path jobsRoot;

    public LocalFileStorage(@Value("${ats.doctor.storage.resume-dir:data/resumes}") String resumeDirectory,
                            @Value("${ats.doctor.storage.jobs-dir:data/jobs}") String jobsDirectory) {
        this.resumeRoot = Path.of(resumeDirectory).toAbsolutePath().normalize();
        this.jobsRoot = Path.of(jobsDirectory).toAbsolutePath().normalize();
    }

    public Path store(UUID id, String originalFilename, byte[] content) throws IOException {
        return store(resumeRoot, id, originalFilename, content);
    }

    /** Deterministic path for a stored upload — extracted back by version id. */
    public Path resolve(UUID id, String originalFilename) {
        return resumeRoot.resolve(nameFor(id, originalFilename)).normalize();
    }

    public Path storeJob(UUID id, String originalFilename, byte[] content) throws IOException {
        return store(jobsRoot, id, originalFilename, content);
    }

    /** Deterministic path for a stored JD — extracted back by job id. */
    public Path resolveJob(UUID id, String originalFilename) {
        return jobsRoot.resolve(nameFor(id, originalFilename)).normalize();
    }

    private Path store(Path root, UUID id, String originalFilename, byte[] content) throws IOException {
        Path dir = Files.createDirectories(root);
        Path target = dir.resolve(nameFor(id, originalFilename)).normalize();
        if (!target.getParent().equals(dir)) {
            throw new IOException("Unsafe storage path for " + originalFilename);
        }
        Files.write(target, content);
        log.info("Stored upload {} ({} bytes) as {}", id, content.length, target);
        return target;
    }

    private String nameFor(UUID id, String originalFilename) {
        String ext = sanitizeExtension(originalFilename);
        return ext.isEmpty() ? id.toString() : id + "." + ext;
    }

    /** Keep only a safe lowercase alphanumeric extension (max 10 chars). */
    static String sanitizeExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.matches("[a-z0-9]{1,10}") ? ext : "";
    }
}