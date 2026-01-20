package com.atsdoctor.backend.infrastructure.matching;

import org.apache.commons.text.similarity.FuzzyScore;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Exact-first matching (TASK-047, FEAT-022; PRD §5.3 "Exact Matching"):
 *<ul>
 * <li><b>EXACT</b> — canonicalized phrase is contained in the candidate text,
 *     or every token of the phrase appears in the candidate (skills, keywords).</li>
 * <li><b>TOKEN</b> — ≥50% token overlap on short phrases (≈ PRD keyword
 *     substring/token overlap, e.g. JD {@code REST API} ↔ resume {@code API}).</li>
 * <li><b>FUZZY</b> — Apache Commons Text {@link FuzzyScore} for single-token
 *     phrases (PRD: JD {@code backend} ↔ resume {@code back-end}).</li>
 *</ul>
 * Fuzzy scores are normalized against the candidate length and require a
 * &gt;0.6 ratio so low-value matches are not fabricated.
 */
public final class ExactMatcher {

    public enum Grade {
        EXACT, TOKEN, FUZZY
    }

    private static final double TOKEN_OVERLAP_THRESHOLD = 0.5;
    private static final double FUZZY_RATIO_THRESHOLD = 0.6;
    private static final int FUZZY_MAX_PHRASE_TOKENS = 1;

    private ExactMatcher() {
    }

    public static Optional<Grade> match(String requirementText, String candidateText) {
        String req = AliasTable.canonicalize(requirementText).trim();
        String cand = AliasTable.canonicalize(candidateText).trim();
        if (req.isEmpty() || cand.isEmpty()) {
            return Optional.empty();
        }
        if (cand.contains(req)) {
            return Optional.of(Grade.EXACT);
        }
        Set<String> reqTokens = Normalizer.tokens(req);
        Set<String> candTokens = Normalizer.tokens(cand);
        if (!reqTokens.isEmpty() && candTokens.containsAll(reqTokens)) {
            return Optional.of(Grade.EXACT);
        }
        long overlap = reqTokens.stream().filter(candTokens::contains).count();
        if (!reqTokens.isEmpty() && (double) overlap / reqTokens.size() >= TOKEN_OVERLAP_THRESHOLD) {
            return Optional.of(Grade.TOKEN);
        }
        if (reqTokens.size() == FUZZY_MAX_PHRASE_TOKENS && req.length() > 3 && cand.length() > 3) {
            // Score normalized against the requirement length, not the (possibly
            // multi-token) candidate, so long candidate sentences don't dilute it.
            double ratio = new FuzzyScore(Locale.ROOT).fuzzyScore(req, cand) / (double) req.length();
            if (ratio > FUZZY_RATIO_THRESHOLD) {
                return Optional.of(Grade.FUZZY);
            }
        }
        return Optional.empty();
    }
}
