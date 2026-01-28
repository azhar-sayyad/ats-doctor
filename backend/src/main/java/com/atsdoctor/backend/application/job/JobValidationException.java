package com.atsdoctor.backend.application.job;

/** §4.3 JD validation failure (TASK-042) → 400 ProblemDetail. */
public class JobValidationException extends RuntimeException {

    public JobValidationException(String message) {
        super(message);
    }
}