package com.atsdoctor.backend.api.tailor;

import com.atsdoctor.backend.infrastructure.persistence.TailoredResume;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * tailored_resume response per 07-api-contract §4 (PRD §8.1). Exposed as a
 * snake_case JSON map; the content JSONB is re-parsed so clients get real
 * objects.
 */
public record TailoredResponse(
        UUID id,
        UUID analysisId,
        UUID resumeVersionId,
        String state,
        Integer scoreBefore,
        Integer scoreAfter,
        JsonNode content,
        String error,
        Instant createdAt,
        Instant updatedAt) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static TailoredResponse of(TailoredResume tailored) {
        return new TailoredResponse(
                tailored.getId(),
                tailored.getAnalysis().getId(),
                tailored.getResumeVersion().getId(),
                tailored.getState(),
                tailored.getScoreBefore(),
                tailored.getScoreAfter(),
                json(tailored.getContent()),
                tailored.getError(),
                tailored.getCreatedAt(),
                tailored.getUpdatedAt());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("analysis_id", analysisId);
        map.put("resume_version_id", resumeVersionId);
        map.put("state", state);
        map.put("score_before", scoreBefore);
        map.put("score_after", scoreAfter);
        map.put("content", content);
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