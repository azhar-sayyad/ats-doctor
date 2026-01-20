package com.atsdoctor.backend.infrastructure.matching;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Text normalization for matching (TASK-046, FEAT-022): lowercase, strip
 * punctuation, collapse whitespace, tokenize. Every comparison in the matching
 * and scoring stacks goes through this so "Python" and " python. " compare
 * equal. Mirrors the resume-side {@code EvidenceExtractor.normalize}.
 */
public final class Normalizer {

    private Normalizer() {
    }

    /** Lowercase, strip non-alphanumeric (keeps {@code +}, {@code #}, {@code .}, {@code -} inside tokens), collapse whitespace. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9+#.-]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    /** Normalized token set (empty if the text is blank). */
    public static Set<String> tokens(String text) {
        String s = normalize(text);
        if (s.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String token : s.split(" ")) {
            if (!token.isBlank()) {
                out.add(token);
            }
        }
        return out;
    }

    /** True when the normalized token set is non-empty and fully contained in {@code candidateText}'s tokens. */
    public static boolean tokensContained(String phrase, String candidateText) {
        Set<String> phraseTokens = tokens(phrase);
        if (phraseTokens.isEmpty()) {
            return false;
        }
        return tokens(candidateText).containsAll(phraseTokens);
    }
}
