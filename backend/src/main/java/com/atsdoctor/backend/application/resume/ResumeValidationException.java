package com.atsdoctor.backend.application.resume;

/**
 * Thrown when structured resume data fails §4.2 validation. The message is
 * safe to surface (stored on the version row as FAILED detail, or returned as
 * the 400 detail on PUT /resumes/{version_id}/edit).
 */
public class ResumeValidationException extends RuntimeException {

    public ResumeValidationException(String message) {
        super(message);
    }
}