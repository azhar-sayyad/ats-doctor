package com.atsdoctor.backend.infrastructure.tailoring;

import com.atsdoctor.backend.application.ai.AiRequest;
import com.atsdoctor.backend.application.ai.AiResult;
import com.atsdoctor.backend.application.ai.AiTask;
import com.atsdoctor.backend.application.ai.AIService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bullet- and summary-level tailoring via the AI layer (FEAT-029/030,
 * TASK-061/063). One {@code ai.generate(task='resume_tailoring')} call per
 * selected bullet (recorded in {@code ai_runs} by the facade). Output parsing
 * is tolerant: a JSON object with {@code tailored_text} (stub provider), a raw
 * string (real provider), truthy the original text as fallback — the rewriter
 * never emits a blank. A/B/C safety is a prompt-level constraint here; the
 * deterministic validator lands in SPRINT-05.
 */
@Component
public class BulletRewriter {

    public static final int MAX_JD_CONTEXT_CHARS = 4000;
    public static final int MAX_EVIDENCE_CONTEXT_CHARS = 6000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AIService aiService;

    public BulletRewriter(AIService aiService) {
        this.aiService = aiService;
    }

    public record RewrittenText(String text, String promptVersion) {
    }

    /**
     * @param requirementTexts gap requirement texts (JD context for the prompt)
     * @param evidenceText     joined resume evidence (grounding vocabulary)
     */
    public RewrittenText rewriteBullet(String originalBullet, List<String> requirementTexts,
                                       String evidenceText) {
        return rewriteBullet(originalBullet, requirementTexts, evidenceText, List.of());
    }

    /**
     * @param requirementTexts gap requirement texts (JD context for the prompt)
     * @param evidenceText     joined resume evidence (grounding vocabulary)
     * @param jobKeywords      JD keywords — grounding candidates for the stub
     */
    public RewrittenText rewriteBullet(String originalBullet, List<String> requirementTexts,
                                       String evidenceText, List<String> jobKeywords) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("jd_requirements", clip(String.join("\n", requirementTexts), MAX_JD_CONTEXT_CHARS));
        variables.put("resume_evidence", clip(evidenceText, MAX_EVIDENCE_CONTEXT_CHARS));
        variables.put("original_bullet", originalBullet);
        variables.put("jd_keywords", String.join("\n", jobKeywords == null ? List.of() : jobKeywords));
        AiResult result = aiService.generate(new AiRequest(AiTask.RESUME_TAILORING, originalBullet, variables));
        return new RewrittenText(sanitize(extractText(result.output()), originalBullet),
                result.task().promptVersion());
    }

    /** Summary variant (FEAT-030, TASK-063) — same prompt, the summary as the "bullet". */
    public RewrittenText rewriteSummary(String summary, List<String> requirementTexts, String evidenceText) {
        return rewriteBullet(summary, requirementTexts, evidenceText, List.of());
    }

    /** Summary variant with JD keywords (stub grounding). */
    public RewrittenText rewriteSummary(String summary, List<String> requirementTexts,
                                        String evidenceText, List<String> jobKeywords) {
        return rewriteBullet(summary, requirementTexts, evidenceText, jobKeywords);
    }

    private static String extractText(String output) {
        if (output == null || output.isBlank()) {
            return null;
        }
        String trimmed = output.trim();
        try {
            var node = MAPPER.readTree(trimmed);
            if (node.isTextual()) {
                return node.asText();
            }
            if (node.isObject() && node.path("tailored_text").isTextual()) {
                return node.path("tailored_text").asText();
            }
            // JSON without tailored_text: fall through to the raw text below,
            // which is friendlier than dropping the rewrite entirely.
        } catch (Exception ignored) {
            // Plain-text output (real provider) — use as-is.
        }
        return stripFences(trimmed);
    }

    private static String stripFences(String text) {
        String out = text.replaceAll("(?s)^```[a-zA-Z0-9]*\\s*", "").replaceAll("(?s)\\s*```$", "").trim();
        return out.isBlank() ? null : out;
    }

    private static String sanitize(String text, String fallback) {
        String candidate = text == null ? "" : text.trim();
        if (candidate.isBlank()) {
            return fallback;
        }
        return candidate;
    }

    private static String clip(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxChars) + "…";
    }
}