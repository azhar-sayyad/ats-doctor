package com.atsdoctor.backend.application.resume;

import java.util.UUID;

/**
 * Published after a resume upload transaction commits; the async pipeline
 * listener picks it up (UPLOADED → EXTRACTING → PARSING → READY/FAILED).
 */
public record ResumeUploadedEvent(UUID versionId) {
}