package com.atsdoctor.backend.application.job;

/** Job row not found (FEAT-021, TASK-045) → 404 ProblemDetail. */
public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(String message) {
        super(message);
    }
}