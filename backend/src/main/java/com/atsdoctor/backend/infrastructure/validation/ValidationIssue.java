package com.atsdoctor.backend.infrastructure.validation;

/**
 * One fact-grounding issue on a tailored claim (PRD §5.5 output shape).
 *
 * @param type       coarse issue type (drives the review UI)
 * @param code       granular code: {@code inflated_metric}, {@code unsupported_technology},
 *                   {@code unsupported_descriptor} (API-compatible granularity)
 * @param text       the offending token/phrase in the tailored text
 * @param suggestion actionable fix hint
 * @param context    snippet of the tailored text around the offending token
 */
public record ValidationIssue(
        ValidationIssueType type,
        String code,
        String text,
        String suggestion,
        String context) {
}