package com.atsdoctor.backend.infrastructure.matching;

import java.util.List;

/**
 * A JD requirement as fed to the matcher (from {@code job_requirements} rows /
 * §4.3 {@code requirements}); type/importance already coerced by
 * {@code RequirementExtractor}.
 */
public record RequirementData(
        String id,
        String text,
        String type,
        String importance,
        List<String> keywords) {

    public RequirementData {
        type = type == null ? "skill" : type;
        importance = importance == null ? "medium" : importance;
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }

    /** Keywords to search with — explicit ones, or the requirement tokens as fallback. */
    public List<String> effectiveKeywords() {
        return keywords.isEmpty() ? List.copyOf(Normalizer.tokens(text)) : keywords;
    }
}
