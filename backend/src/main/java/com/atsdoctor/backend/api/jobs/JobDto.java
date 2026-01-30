package com.atsdoctor.backend.api.jobs;

import com.atsdoctor.backend.application.job.JobValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

/**
 * Structured JD per PRD §4.3 (structured.json) — TASK-042. Records map the
 * canonical JSON shape; {@link #parse} validates AI output against the schema
 * before it is persisted.
 */
public record JobDto(
        @Valid Metadata metadata,
        @Valid JobInfo job,
        List<@Valid Requirement> requirements,
        @Valid SkillGroups skills,
        List<String> responsibilities,
        List<String> keywords) {

    /** §4.3 job block. */
    public record JobInfo(
            @NotBlank(message = "job.title is required") String title,
            String company,
            String location,
            String seniority,
            String postDate,
            String url) {
    }

    public record Metadata(
            String id,
            String source,
            String filename,
            String parsedAt,
            String modelUsed,
            String modelVersion,
            String promptVersion,
            BigDecimal temperature) {
    }

    /** §4.3 requirements[] — type/importance are normalized by RequirementExtractor. */
    public record Requirement(
            String id,
            @NotBlank(message = "requirements[].text is required") String text,
            String type,
            String importance,
            List<String> keywords) {
    }

    public record SkillGroups(
            List<String> required,
            List<String> preferred,
            List<String> niceToHave) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /**
     * Deserializes and Bean-validates AI output against the §4.3 schema.
     *
     * @throws JobValidationException if the JSON is malformed or invalid
     */
    public static JobDto parse(String json, Validator validator) {
        String cleaned = stripCodeFences(json);
        JobDto dto;
        try {
            dto = MAPPER.readValue(cleaned, JobDto.class);
        } catch (JsonProcessingException ex) {
            throw new JobValidationException(
                    "Structured JD is not valid JSON (" + ex.getOriginalMessage() + ")");
        }
        var violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getMessage() == null ? v.getPropertyPath().toString() : v.getMessage())
                    .distinct()
                    .sorted()
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Unknown validation error");
            throw new JobValidationException("Structured JD failed validation: " + detail);
        }
        return dto;
    }

    /** Strip markdown fences and parse the AI JSON output. */
    static String stripCodeFences(String json) {
        String s = json == null ? "" : json.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline >= 0) {
                s = s.substring(firstNewline + 1).trim();
            }
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3).trim();
            }
        }
        if (s.isEmpty()) {
            throw new JobValidationException("Structured JD is empty");
        }
        return s;
    }

    /**
     * Canonical §4.3 JSON (drops unknown fields the provider added). Used to
     * persist validated output and to feed {@code requirement_extraction}.
     */
    public static String toJson(JobDto dto) {
        try {
            return MAPPER.writeValueAsString(dto);
        } catch (JsonProcessingException ex) {
            throw new JobValidationException(
                    "Could not serialize structured JD (" + ex.getOriginalMessage() + ")");
        }
    }
}