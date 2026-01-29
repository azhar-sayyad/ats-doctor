package com.atsdoctor.backend.application.resume;

/**
 * Version is not in a state that allows the requested operation (e.g. editing
 * a version that is still processing → 409 Conflict, ProblemDetail).
 */
public class ResumeNotReadyException extends RuntimeException {

    public ResumeNotReadyException(String message) {
        super(message);
    }
}