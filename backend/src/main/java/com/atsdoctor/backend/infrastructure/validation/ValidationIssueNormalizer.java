package com.atsdoctor.backend.infrastructure.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared normalization for validation output (FEAT-034, TASK-069): parses raw
 * provider responses and canonicalizes issues so the deterministic stage and
 * the AI stage merge into ONE shape before the rule engine (TASK-070).
 *
 * <p>Normalization rules:<ul>
 * <li>tolerant JSON: object {@code {is_valid, issues[]}}, bare {@code issues[]}
 *     array, fenced JSON, or — on malformed/blank output — no issues;</li>
 * <li>codes lowercased/trimmed (whitespace → underscore) and mapped to the
 *     canonical {@link ValidationIssueType} table;</li>
 * <li>{@code text} is mandatory — issues without an offending token drop;</li>
 * <li>{@code context} falls back to the offending token when
 *     {@code original_evidence} is absent;</li>
 * <li>dedupe by {@code (code, text)} keeps the first occurrence; merge order is
 *     caller-supplied (stable).</li>
 * </ul>
 */
public final class ValidationIssueNormalizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> FACT_CODES = Set.of(
            "unsupported_technology", "inflated_metric", "new_company", "new_title");
    private static final Set<String> CLAIM_CODES = Set.of("unsupported_achievement");

    private ValidationIssueNormalizer() {
    }

    /** Tolerant parse of a provider response into normalized issues (never throws). */
    public static List<ValidationIssue> parse(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(stripFences(output.trim()));
        } catch (Exception ignored) {
            return List.of();
        }
        JsonNode issues = root.isArray() ? root : root.path("issues");
        if (!issues.isArray() || issues.isEmpty()) {
            return List.of();
        }
        List<ValidationIssue> out = new ArrayList<>();
        for (JsonNode node : issues) {
            if (node == null || !node.isObject()) {
                continue;
            }
            String text = node.path("text").asText("");
            if (text.isBlank()) {
                continue;
            }
            String code = normalizeCode(node.path("type").asText(""));
            out.add(new ValidationIssue(
                    typeOf(code),
                    code.isBlank() ? null : code,
                    text,
                    node.path("suggestion").asText(""),
                    snippetOf(node, text)));
        }
        return out;
    }

    /** Canonical type for a normalized code (blank/unknown → descriptor). */
    public static ValidationIssueType typeOf(String code) {
        if (code != null && !code.isBlank()) {
            if (FACT_CODES.contains(code)) {
                return ValidationIssueType.UNSUPPORTED_FACT;
            }
            if (CLAIM_CODES.contains(code)) {
                return ValidationIssueType.UNSUPPORTED_CLAIM;
            }
        }
        return ValidationIssueType.UNSUPPORTED_DESCRIPTOR;
    }

    /** Lowercase, trim, collapse whitespace to underscores: {@code "Unsupported Technology"} → {@code unsupported_technology}. */
    public static String normalizeCode(String code) {
        if (code == null) {
            return "";
        }
        return code.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", "_");
    }

    /** Stable dedupe by offending token (case-insensitive text) — first occurrence wins; order preserved. Deterministic (lowercased) and AI (verbatim) hits on the same token collapse into ONE issue, the deterministic one. */
    public static List<ValidationIssue> dedupe(List<ValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<ValidationIssue> out = new ArrayList<>();
        for (ValidationIssue issue : issues) {
            String key = issue.text().toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                out.add(issue);
            }
        }
        return out;
    }

    private static String stripFences(String text) {
        String out = text.replaceAll("(?s)^```[a-zA-Z0-9]*\\s*", "").replaceAll("(?s)\\s*```$", "").trim();
        return out.isBlank() ? text : out;
    }

    private static String snippetOf(JsonNode node, String text) {
        String evidence = node.path("original_evidence").asText("");
        return evidence.isBlank() ? text : evidence;
    }
}