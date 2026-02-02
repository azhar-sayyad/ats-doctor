package com.atsdoctor.backend.api.jobs;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * job response per 07-api-contract (PRD §8.1). Exposed as a snake_case JSON map
 * (Spring Jackson naming convention, PRD §8) via {@link #toMap()}.
 */
public record JobResponse(
        UUID id,
        String state,
        String title,
        String company,
        String location,
        String seniority,
        String rawText,
        String structuredData,
        String sourceFilename,
        String modelUsed,
        String modelVersion,
        String promptVersion,
        BigDecimal temperature,
        String error,
        Map<String, Object> requirementSummary,
        Instant createdAt,
        Instant updatedAt) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("state", state);
        map.put("title", title);
        map.put("company", company);
        map.put("location", location);
        map.put("seniority", seniority);
        map.put("raw_text", rawText);
        map.put("structured_data", structuredData);
        map.put("source_filename", sourceFilename);
        map.put("model_used", modelUsed);
        map.put("model_version", modelVersion);
        map.put("prompt_version", promptVersion);
        map.put("temperature", temperature);
        map.put("error", error);
        map.put("requirement_summary", requirementSummary);
        map.put("created_at", createdAt);
        map.put("updated_at", updatedAt);
        return map;
    }
}