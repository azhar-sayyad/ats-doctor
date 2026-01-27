package com.atsdoctor.backend.application.analysis;

/** Analysis row not found (FEAT-027, TASK-058) → 404 ProblemDetail. */
public class AnalysisNotFoundException extends RuntimeException {

    public AnalysisNotFoundException(String message) {
        super(message);
    }
}
