package com.atsdoctor.backend.application.analysis;

/** Invalid analysis request (job/resume not READY, malformed input) → 400. */
public class AnalysisValidationException extends RuntimeException {

    public AnalysisValidationException(String message) {
        super(message);
    }
}
