package com.atsdoctor.backend.application.ai;

/**
 * Immutable snapshot of one AI execution (PRD §6.7 / schema §1.9). Raw prompts
 * are never stored — only a SHA-256 {@code inputHash} of the input.
 */
public record AiRunRecord(
        AiTask task,
        String provider,
        String model,
        String modelVersion,
        String profile,
        String promptVersion,
        String inputHash,
        String output,
        String status,
        String error,
        long latencyMs,
        Integer inputTokens,
        Integer outputTokens) {
}
