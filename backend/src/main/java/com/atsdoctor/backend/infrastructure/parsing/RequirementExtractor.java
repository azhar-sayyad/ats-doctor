package com.atsdoctor.backend.infrastructure.parsing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns the {@code requirement_extraction} AI output into {@code job_requirements}
 * rows (FEAT-020, TASK-043). Deterministic MVP normalization on top of the AI
 * output: blank texts are dropped, {@code type} coalesces to one of
 * {@code skill|experience|education} and {@code importance} to
 * {@code high|medium|low} so the DB CHECK constraints can never reject a row.
 *
 * <p>Accepts either a bare JSON array or a {@code {"requirements": [...]}}
 * object (tolerant of LLM output shapes).
 */
@Component
public class RequirementExtractor {

    private static final Logger log = LoggerFactory.getLogger(RequirementExtractor.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final Set<String> TYPES = Set.of("skill", "experience", "education");
    private static final Set<String> IMPORTANCES = Set.of("high", "medium", "low");

    public record ExtractedRequirement(String text, String type, String importance, List<String> keywords) {
    }

    public List<ExtractedRequirement> extract(String aiOutputJson) {
        List<ExtractedRequirement> out = new ArrayList<>();
        if (aiOutputJson == null || aiOutputJson.isBlank()) {
            return out;
        }
        try {
            JsonNode root = MAPPER.readTree(aiOutputJson);
            JsonNode list = root != null && root.isArray() ? root : root.path("requirements");
            if (!list.isArray()) {
                log.warn("requirement_extraction output had no array/requirements node — 0 rows");
                return out;
            }
            for (JsonNode node : list) {
                String text = node.path("text").asText("").trim();
                if (text.isEmpty()) {
                    continue;
                }
                out.add(new ExtractedRequirement(
                        text,
                        coalesce(node.path("type").asText(""), TYPES, "skill"),
                        coalesce(node.path("importance").asText(""), IMPORTANCES, "medium"),
                        cleanKeywords(node.path("keywords"))));
            }
        } catch (JsonProcessingException ex) {
            log.warn("Could not parse requirement_extraction output, 0 rows: {}", ex.getMessage());
            return List.of();
        }
        return out;
    }

    private static String coalesce(String value, Set<String> allowed, String fallback) {
        String v = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return allowed.contains(v) ? v : fallback;
    }

    private static List<String> cleanKeywords(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        Set<String> deduped = new LinkedHashSet<>();
        node.forEach(item -> {
            String k = item.asText("").trim().toLowerCase(Locale.ROOT);
            if (!k.isEmpty()) {
                deduped.add(k);
            }
        });
        return List.copyOf(deduped);
    }
}