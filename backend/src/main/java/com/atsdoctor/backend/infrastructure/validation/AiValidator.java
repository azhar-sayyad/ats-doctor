package com.atsdoctor.backend.infrastructure.validation;

import com.atsdoctor.backend.application.ai.AiRequest;
import com.atsdoctor.backend.application.ai.AiResult;
import com.atsdoctor.backend.application.ai.AiTask;
import com.atsdoctor.backend.application.ai.AIService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI fact-grounding validator (FEAT-034, TASK-068): calls
 * {@code ai.generate(task='fact_validation')} per claim with the
 * {@code validator-v1} prompt (cross-check original vs tailored against
 * evidence), one {@code ai_runs} row recorded by the facade, and normalizes
 * the response into the shared {@link ValidationIssue} shape (TASK-069) so
 * deterministic and AI issues merge in the rule engine (TASK-070).
 *
 * <p>Output parsing is tolerant, mirroring {@code BulletRewriter}: a JSON
 * object {@code {is_valid, issues[]}}, a bare {@code issues[]} array, or — on
 * any malformed/blank output — no issues. The validator never throws.
 */
@Component
public class AiValidator {

    public static final int MAX_CONTEXT_CHARS = 6000;

    private final AIService aiService;

    public AiValidator(AIService aiService) {
        this.aiService = aiService;
    }

    /**
     * @param claimCategory  {@code A} | {@code B} | {@code C} (passed to the prompt)
     * @param originalText   claim text before tailoring (source)
     * @param tailoredText   rewritten claim under validation
     * @param evidenceTexts  source resume evidence rows the claim traces to
     */
    public List<ValidationIssue> validate(String claimCategory, String originalText,
                                          String tailoredText, List<String> evidenceTexts) {
        String tailored = blankToNull(tailoredText);
        if (tailored == null) {
            return List.of();
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("original_resume", clip(originalText, MAX_CONTEXT_CHARS));
        variables.put("tailored_resume", tailored);
        variables.put("resume_evidence",
                clip(String.join("\n", evidenceTexts == null ? List.of() : evidenceTexts),
                        MAX_CONTEXT_CHARS));
        variables.put("claim_category", blankToNull(claimCategory) == null ? "" : claimCategory);

        AiResult result = aiService.generate(new AiRequest(AiTask.FACT_VALIDATION, tailored, variables));
        return ValidationIssueNormalizer.parse(result.output());
    }

    private static String clip(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxChars) + "…";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}