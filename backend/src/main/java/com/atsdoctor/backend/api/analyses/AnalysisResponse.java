package com.atsdoctor.backend.api.analyses;

import com.atsdoctor.backend.infrastructure.persistence.Analysis;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * analysis response per 07-api-contract (PRD §8.1). Exposed as a snake_case
 * JSON map (Spring Jackson naming convention, PRD §8) via {@link #toMap()}.
 * JSONB columns are re-parsed with a plain ObjectMapper so clients get real
 * objects, not escaped strings.
 */
public record AnalysisResponse(
        UUID id,
        UUID jobId,
        UUID resumeVersionId,
        String state,
        Integer score,
        JsonNode scoreBreakdown,
        JsonNode matches,
        JsonNode gaps,
        JsonNode generation,
        String error,
        Instant createdAt,
        Instant updatedAt) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static AnalysisResponse of(Analysis analysis) {
        return new AnalysisResponse(
                analysis.getId(),
                analysis.getJob().getId(),
                analysis.getResumeVersion().getId(),
                analysis.getState(),
                analysis.getScore(),
                json(analysis.getScoreBreakdown()),
                json(analysis.getMatches()),
                json(analysis.getGaps()),
                json(analysis.getGeneration()),
                analysis.getError(),
                analysis.getCreatedAt(),
                analysis.getUpdatedAt());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("job_id", jobId);
        map.put("resume_version_id", resumeVersionId);
        map.put("state", state);
        map.put("score", score);
        map.put("score_breakdown", scoreBreakdown);
        map.put("matches", matches);
        map.put("gaps", gaps);
        map.put("generation", generation);
        map.put("error", error);
        map.put("created_at", createdAt);
        map.put("updated_at", updatedAt);
        return map;
    }

    private static JsonNode json(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(stored);
        } catch (Exception ex) {
            return null;
        }
    }
}
