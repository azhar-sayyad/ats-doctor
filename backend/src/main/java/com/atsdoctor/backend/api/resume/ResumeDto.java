package com.atsdoctor.backend.api.resume;

import com.atsdoctor.backend.application.resume.ResumeValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

/**
 * Structured master resume per PRD §4.2 (master.json) — TASK-032. Records map
 * the canonical JSON shape; {@link #parse} validates AI output against the
 * schema before it is persisted.
 */
public record ResumeDto(
        @Valid Metadata metadata,
        @Valid Basics basics,
        String summary,
        List<@Valid Skill> skills,
        List<@Valid Experience> experience,
        List<@Valid Project> projects,
        List<@Valid Education> education,
        @Valid EvidenceModel evidenceModel) {

    /** §4.2 master.json basics block. */
    public record Basics(
            @NotBlank(message = "basics.name is required") String name,
            String email,
            String phone,
            String location,
            String linkedin,
            String github) {
    }

    public record Metadata(
            String id,
            Integer version,
            String sourceFilename,
            String parsedAt,
            String modelUsed,
            String modelVersion,
            String promptVersion,
            BigDecimal temperature,
            Boolean isSourceOfTruth) {
    }

    public record Skill(
            @NotBlank(message = "skills[].name is required") String name,
            String category,
            Integer years) {
    }

    public record Experience(
            String id,
            @NotBlank(message = "experience[].company is required") String company,
            String title,
            String start,
            String end,
            String location,
            String description,
            List<@Valid Bullet> bullets) {
    }

    public record Bullet(
            String id,
            @NotBlank(message = "experience[].bullets[].text is required") String text,
            List<String> technologies,
            List<String> metrics,
            List<String> domains,
            String evidenceLevel) {
    }

    public record Project(
            String id,
            @NotBlank(message = "projects[].name is required") String name,
            String description,
            List<String> technologies,
            List<String> outcomes,
            List<Object> bullets) {
    }

    public record Education(
            String institution,
            String degree,
            String field,
            String start,
            String end) {
    }

    /** §4.2 evidence_model — the pre-extraction claim list from the AI parse. */
    public record EvidenceModel(
            String sourceOfTruth,
            List<@Valid Claim> claims) {
    }

    public record Claim(
            String id,
            String type,
            String text,
            String category,
            List<String> sourceRefs,
            List<@Valid Fact> facts) {
    }

    public record Fact(String kind, String value) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /**
     * Deserializes and Bean-validates AI output against the §4.2 schema.
     *
     * @throws ResumeValidationException if the JSON is malformed or invalid
     */
    public static ResumeDto parse(String json, Validator validator) {
        String cleaned = stripCodeFences(json);
        ResumeDto dto;
        try {
            dto = MAPPER.readValue(normalizeLenientShapes(cleaned), ResumeDto.class);
        } catch (JsonProcessingException ex) {
            throw new ResumeValidationException(
                    "Structured resume is not valid JSON (" + ex.getOriginalMessage() + ")");
        }
        var violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getMessage() == null ? v.getPropertyPath().toString() : v.getMessage())
                    .distinct()
                    .sorted()
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Unknown validation error");
            throw new ResumeValidationException("Structured resume failed validation: " + detail);
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
            throw new ResumeValidationException("Structured resume is empty");
        }
        return s;
    }

    /**
     * Normalizes common provider variance before strict Bean validation: plain
     * strings in {@code skills[]} become {@code {"name": "..."}} and plain
     * strings in {@code experience[].bullets[]} become {@code {"text": "..."}}.
     * Real models occasionally flatten these arrays despite the prompt contract.
     */
    static String normalizeLenientShapes(String json) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException ex) {
            return json;
        }
        boolean changed = false;
        if (root.has("skills") && root.get("skills").isArray()) {
            ArrayNode skills = (ArrayNode) root.get("skills");
            for (int i = 0; i < skills.size(); i++) {
                JsonNode item = skills.get(i);
                if (item.isTextual()) {
                    skills.set(i, MAPPER.createObjectNode().put("name", item.asText()));
                    changed = true;
                }
            }
        }
        if (root.has("experience") && root.get("experience").isArray()) {
            for (JsonNode exp : root.get("experience")) {
                if (exp.has("bullets") && exp.get("bullets").isArray()) {
                    ArrayNode bullets = (ArrayNode) exp.get("bullets");
                    for (int i = 0; i < bullets.size(); i++) {
                        JsonNode bullet = bullets.get(i);
                        if (bullet.isTextual()) {
                            bullets.set(i, MAPPER.createObjectNode().put("text", bullet.asText()));
                            changed = true;
                        }
                    }
                }
            }
        }
        if (!changed) {
            return json;
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            return json;
        }
    }

    /**
     * Canonical §4.2 JSON (drops any unknown fields the provider added). Used
     * to persist validated output / edit payloads.
     */
    public static String toJson(ResumeDto dto) {
        try {
            return MAPPER.writeValueAsString(dto);
        } catch (JsonProcessingException ex) {
            throw new ResumeValidationException(
                    "Could not serialize structured resume (" + ex.getOriginalMessage() + ")");
        }
    }
}