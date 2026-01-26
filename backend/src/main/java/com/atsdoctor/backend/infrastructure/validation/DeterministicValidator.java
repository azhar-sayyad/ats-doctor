package com.atsdoctor.backend.infrastructure.validation;

import com.atsdoctor.backend.infrastructure.matching.Normalizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic fact-grounding validator (FEAT-033, TASK-067) — the first
 * stage of the defense-in-depth validation (PRD §5.5). Pure rule checks, no
 * LLM: <ul>
 * <li><b>Category-A facts</b> (technology/metric) must exist in the source
 *     resume evidence; metric VALUES must match (an {@code 10m} tailors
 *     against evidence {@code 2m} → {@code inflated_metric}).</li>
 * <li><b>Category-B descriptors</b> must be supported by evidence tokens —
 *     a JD keyword alone is not a license to add it (PRD validation-rules
 *     table: JD {@code Kubernetes} vs evidence {@code Docker} → flagged).</li>
 * <li><b>Category-C claims</b> — {@link #validateClaims} (rule engine,
 *     TASK-070): introduced factual tokens are typed as
 *     {@code unsupported_achievement}/{@code UNSUPPORTED_CLAIM}.</li>
 * </ul>
 * Technology detection is heuristic (no NER): tokens carrying a digit are
 * metrics; tokens containing {@code + # .} characters are technology-shaped
 * (c++, c#, node.js, .net). Bare proper-noun technologies without a digit or
 * such a character are reported as descriptors; the AI validator (TASK-068)
 * adds named-entity precision later.
 *
 * <p>Null/blank inputs are variance-safe: blanks are treated as empty
 * evidence (supported tokens are simply absent), never an exception.
 */
@Component
public class DeterministicValidator {

    /** Derived from the tailored text but already present in the source (original + evidence). */
    private static final String CODE_UNSUPPORTED_TECHNOLOGY = "unsupported_technology";
    private static final String CODE_INFLATED_METRIC = "inflated_metric";
    private static final String CODE_UNSUPPORTED_DESCRIPTOR = "unsupported_descriptor";
    private static final String CODE_UNSUPPORTED_ACHIEVEMENT = "unsupported_achievement";

    private static final Set<String> FUNCTION_WORDS = Set.of(
            "a", "an", "and", "as", "at", "by", "for", "from", "in", "into",
            "of", "on", "or", "over", "the", "to", "under", "with", "focus", "using",
            "areas", "area", "also", "e.g", "ie", "etc");

    /** GRANULAR codes exposed for the API contract (validation JSON). */
    public record ValidationTarget(
            String claimCategory,
            String originalText,
            String tailoredText,
            List<String> evidenceTexts) {

        public ValidationTarget {
            evidenceTexts = evidenceTexts == null ? List.of() : evidenceTexts;
        }
    }

    /**
     * @param claimCategory {@code A} | {@code B} | {@code C} (blank behaves like B except C defers)
     * @param originalText  the claim text before tailoring (itself source evidence)
     * @param tailoredText  the rewritten claim text under validation
     * @param evidenceTexts source resume evidence rows the claim traces to
     */
    public List<ValidationIssue> validate(String claimCategory, String originalText,
                                          String tailoredText, List<String> evidenceTexts) {
        if ("C".equalsIgnoreCase(blankToNull(claimCategory))) {
            return List.of();
        }
        if (blankToNull(tailoredText) == null) {
            return List.of();
        }
        Set<String> corpus = corpusTokens(originalText, evidenceTexts);
        Set<String> originalTokens = tokens(originalText);

        List<ValidationIssue> issues = new ArrayList<>();
        for (String token : addedTokens(tailoredText, originalTokens, corpus)) {
            issues.add(describe(token, tailoredText));
        }
        return issues;
    }

    /**
     * Category-C variant (TASK-070): any factual content the tailor introduced
     * is a new-achievement issue — {@code PRD §5.5}: "No new achievements
     * ever". Same grounding-token rule as A/B, but typed
     * {@code UNSUPPORTED_CLAIM}/{@code unsupported_achievement}.
     */
    public List<ValidationIssue> validateClaims(String originalText, String tailoredText,
                                                List<String> evidenceTexts) {
        if (blankToNull(tailoredText) == null) {
            return List.of();
        }
        Set<String> corpus = corpusTokens(originalText, evidenceTexts);
        Set<String> originalTokens = tokens(originalText);

        List<ValidationIssue> issues = new ArrayList<>();
        for (String token : addedTokens(tailoredText, originalTokens, corpus)) {
            issues.add(new ValidationIssue(ValidationIssueType.UNSUPPORTED_CLAIM,
                    CODE_UNSUPPORTED_ACHIEVEMENT, token,
                    "Remove '" + token + "' — the achievement is not supported by the resume evidence.",
                    snippet(tailoredText, token)));
        }
        return issues;
    }

    /** Ordered significant tokens the tailor INTRODUCED: in the tailored text, absent from original AND evidence. */
    private static List<String> addedTokens(String tailoredText, Set<String> originalTokens, Set<String> corpus) {
        List<String> added = new ArrayList<>();
        for (String token : tokens(tailoredText)) {
            if (token.length() < 3 || FUNCTION_WORDS.contains(token)) {
                continue;
            }
            if (originalTokens.contains(token) || corpus.contains(token)) {
                continue;
            }
            added.add(token);
        }
        return added;
    }

    private static ValidationIssue describe(String token, String tailoredText) {
        if (token.matches(".*\\d.*")) {
            return new ValidationIssue(ValidationIssueType.UNSUPPORTED_FACT,
                    CODE_INFLATED_METRIC, token,
                    "Revert to the original metric — '" + token + "' is not in the resume evidence.",
                    snippet(tailoredText, token));
        }
        if (isTechnologyShaped(token)) {
            return new ValidationIssue(ValidationIssueType.UNSUPPORTED_FACT,
                    CODE_UNSUPPORTED_TECHNOLOGY, token,
                    "Remove '" + token + "' or replace it with a technology present in the resume evidence.",
                    snippet(tailoredText, token));
        }
        return new ValidationIssue(ValidationIssueType.UNSUPPORTED_DESCRIPTOR,
                CODE_UNSUPPORTED_DESCRIPTOR, token,
                "Remove '" + token + "' — the descriptor is not supported by the resume evidence.",
                snippet(tailoredText, token));
    }

    /** Sustained lowercase token or one carrying a symbol typical of technology names (c++, .net, node.js). */
    private static boolean isTechnologyShaped(String token) {
        return token.startsWith(".") || token.matches(".*[+#.].*");
    }

    /** Normalized tokens with sentence-punctuation stripped from the trailing edge on BOTH sides of a comparison. */
    private static Set<String> tokens(String text) {
        Set<String> out = new LinkedHashSet<>();
        for (String token : Normalizer.tokens(text)) {
            String trimmed = token;
            while (!trimmed.isEmpty()
                    && ".?!;:,".indexOf(trimmed.charAt(trimmed.length() - 1)) >= 0) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    /** Corpus = normalized tokens of the original claim PLUS every evidence row (original is itself evidence). */
    private static Set<String> corpusTokens(String originalText, List<String> evidenceTexts) {
        Set<String> corpus = new LinkedHashSet<>(tokens(originalText));
        for (String evidence : evidenceTexts) {
            corpus.addAll(tokens(evidence));
        }
        return corpus;
    }

    /** ~120-char window of the tailored text around the FIRST occurrence of the token. */
    private static String snippet(String text, String token) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        int at = lower.indexOf(token);
        int from = Math.max(0, at - 40);
        int to = Math.min(text.length(), at + token.length() + 80);
        String out = text.substring(from, to).trim();
        return (from > 0 ? "…" : "") + out + (to < text.length() ? "…" : "");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}