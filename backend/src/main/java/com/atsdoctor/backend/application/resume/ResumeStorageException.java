package com.atsdoctor.backend.application.resume;

/**
 * The uploaded file could not be stored on disk (→ 500, ProblemDetail).
 */
public class ResumeStorageException extends RuntimeException {

    public ResumeStorageException(String message) {
        super(message);
    }
}