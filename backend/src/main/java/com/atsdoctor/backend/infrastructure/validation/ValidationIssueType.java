package com.atsdoctor.backend.infrastructure.validation;

/**
 * Typed validation issue shape (SPRINT-05, TASK-067; engine widened by
 * TASK-070). {@code UNSUPPORTED_CLAIM} is reserved for the rule engine over
 * category-C claims — the deterministic stage (category A/B) never emits it.
 */
public enum ValidationIssueType {
    /** Category-A facts (technology/metric) absent from the source resume evidence. */
    UNSUPPORTED_FACT,
    /** Category-B descriptor token without support in the source resume evidence. */
    UNSUPPORTED_DESCRIPTOR,
    /** Category-C achievement not grounded in evidence (rule engine, TASK-070). */
    UNSUPPORTED_CLAIM
}