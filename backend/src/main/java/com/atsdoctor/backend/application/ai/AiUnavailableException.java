package com.atsdoctor.backend.application.ai;

/**
 * Controlled error raised when the AI layer exhausts retries, the configured
 * fallback, and stub degradation is disabled (PRD §6.4).
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(AiTask task, String profile, String reason) {
        super("AI generation failed for task '" + task.id() + "' (profile " + profile + "): " + reason);
    }
}
