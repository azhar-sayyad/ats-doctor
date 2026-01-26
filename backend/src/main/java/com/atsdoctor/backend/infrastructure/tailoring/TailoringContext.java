package com.atsdoctor.backend.infrastructure.tailoring;

import com.atsdoctor.backend.api.resume.ResumeDto;

import java.util.List;

/**
 * Immutable inputs for the tailoring decision layer (FEAT-028, TASK-059).
 * Mirrors what the analysis pipeline consumed, plus the match gaps so the
 * decider knows which requirements are not yet covered by the resume.
 */
public record TailoringContext(
        ResumeDto resume,
        List<RequirementGap> gaps,
        List<String> jdKeywords,
        String allEvidenceText) {

    /** One still-uncovered requirement: its text and the keywords to align. */
    public record RequirementGap(String text, List<String> keywords) {
    }

    public record Bullet(
            String bulletId,
            String originalText,
            String claimCategory,
            String evidenceId) {
    }
}