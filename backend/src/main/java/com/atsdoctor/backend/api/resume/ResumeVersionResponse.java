package com.atsdoctor.backend.api.resume;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * resume_version response per 07-api-contract (PRD §8.1). Exposed as a
 * snake_case JSON map (Spring Jackson SNAKE_CASE naming strategy, PRD §8) via
 * {@link #toMap()}.
 */
public record ResumeVersionResponse(
        UUID id,
        UUID resumeId,
        Integer version,
        String state,
        String rawText,
        String structuredData,
        String sourceFilename,
        String modelUsed,
        String modelVersion,
        String promptVersion,
        BigDecimal temperature,
        String error,
        Map<String, Object> evidenceSummary,
        Instant createdAt,
        Instant updatedAt) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("resume_id", resumeId);
        map.put("version", version);
        map.put("state", state);
        map.put("raw_text", rawText);
        map.put("structured_data", structuredData);
        map.put("source_filename", sourceFilename);
        map.put("model_used", modelUsed);
        map.put("model_version", modelVersion);
        map.put("prompt_version", promptVersion);
        map.put("temperature", temperature);
        map.put("error", error);
        map.put("evidence_summary", evidenceSummary);
        map.put("created_at", createdAt);
        map.put("updated_at", updatedAt);
        return map;
    }
}