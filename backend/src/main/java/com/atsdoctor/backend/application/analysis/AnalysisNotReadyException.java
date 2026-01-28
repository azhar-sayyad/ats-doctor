package com.atsdoctor.backend.application.analysis;

/** Analysis is busy (QUEUED/MATCHING/SCORING) → 409 ProblemDetail. */
public class AnalysisNotReadyException extends RuntimeException {

    public AnalysisNotReadyException(String message) {
        super(message);
    }
}
