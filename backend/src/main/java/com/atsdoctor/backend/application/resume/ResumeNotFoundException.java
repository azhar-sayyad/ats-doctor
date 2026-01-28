package com.atsdoctor.backend.application.resume;

/**
 * Resume version was not found (GET /resumes/{id} → 404, ProblemDetail).
 */
public class ResumeNotFoundException extends RuntimeException {

    public ResumeNotFoundException(String message) {
        super(message);
    }
}