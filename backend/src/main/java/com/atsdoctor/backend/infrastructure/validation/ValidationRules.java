package com.atsdoctor.backend.infrastructure.validation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shared validation rule engine (FEAT-035, TASK-070): runs the stages of the
 * defense-in-depth validation (PRD §5.5) per claim and merges them into ONE
 * typed, deduplicated issue list — the shape the {@code validate} API and the
 * review UI consume.
 *
 * <p>Stage merge:<ul>
 * <li>deterministic stage (TASK-067) — category A/B via {@link DeterministicValidator#validate},
 *     category C via {@link DeterministicValidator#validateClaims}
 *     ({@code unsupported_claim} typing);</li>
 * <li>AI stage (TASK-068/069, {@code fact_validation} /
 *     {@code validator-v1}) — always run, one {@code ai_runs} row per claim;</li>
 * <li>dedupe by {@code (code, text)} — deterministic wins the collision
 *     (it runs first), then stable ordering by issue type
 *     (fact → descriptor → claim).</li>
 * </ul>
 * The engine is pure — it does not persist; TASK-071 wires it into
 * {@code POST /tailored/{id}/validate} and records results on the tailored
 * resume.
 */
@Component
public class ValidationRules {

    private final DeterministicValidator deterministicValidator;
    private final AiValidator aiValidator;

    public ValidationRules(DeterministicValidator deterministicValidator, AiValidator aiValidator) {
        this.deterministicValidator = deterministicValidator;
        this.aiValidator = aiValidator;
    }

    /** Verdict for one claim: {@code valid} when no merged issue survives dedupe. */
    public record Verdict(boolean valid, List<ValidationIssue> issues) {

        public static Verdict clean() {
            return new Verdict(true, List.of());
        }
    }

    public Verdict validate(String claimCategory, String originalText,
                            String tailoredText, List<String> evidenceTexts) {
        List<ValidationIssue> merged = new ArrayList<>();
        if ("C".equalsIgnoreCase(blankToNull(claimCategory))) {
            merged.addAll(deterministicValidator.validateClaims(originalText, tailoredText, evidenceTexts));
        } else {
            merged.addAll(deterministicValidator.validate(claimCategory, originalText, tailoredText, evidenceTexts));
        }
        merged.addAll(aiValidator.validate(claimCategory, originalText, tailoredText, evidenceTexts));

        List<ValidationIssue> issues = new ArrayList<>(ValidationIssueNormalizer.dedupe(merged));
        issues.sort(Comparator.comparingInt(i -> i.type().ordinal()));
        return new Verdict(issues.isEmpty(), issues);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}