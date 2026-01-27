package com.atsdoctor.backend.application.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AiRequest(AiTask task, String input, Map<String, Object> variables) {

    public AiRequest {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    /**
     * Single-input convenience factory (PRD §6.1): fills every variable of the
     * task's prompt template with the given input. Multi-variable tasks keep
     * the same value per slot — callers needing distinct values use the full
     * constructor with explicit variables.
     */
    public static AiRequest of(AiTask task, String input) {
        Map<String, Object> variables = new LinkedHashMap<>();
        for (String variable : promptVariables(task)) {
            variables.put(variable, input);
        }
        return new AiRequest(task, input, variables);
    }

    private static List<String> promptVariables(AiTask task) {
        return switch (task) {
            case RESUME_PARSER -> List.of("resume_text");
            case JD_PARSER -> List.of("jd_text");
            case REQUIREMENT_EXTRACTION -> List.of("jd_json");
            case RESUME_TAILORING -> List.of("jd_requirements", "jd_keywords", "resume_evidence", "original_bullet");
            case FACT_VALIDATION -> List.of("original_resume", "tailored_resume", "resume_evidence", "claim_category");
            case EMBEDDING -> List.of();
        };
    }
}
