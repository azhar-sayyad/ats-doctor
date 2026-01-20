package com.atsdoctor.backend.infrastructure.matching;

import java.util.Comparator;

/** A single requirement/candidate pair that matched: grade + normalized similarity (0..1). */
public record EvidenceHit(EvidenceDoc evidence, ExactMatcher.Grade grade, double similarity) {

    public static EvidenceHit of(String phrase, EvidenceDoc doc) {
        return ExactMatcher.match(phrase, doc.text())
                .map(g -> new EvidenceHit(doc, g, similarity(phrase, doc.text())))
                .orElse(null);
    }

    /** 1.0 for substring/token containment, else token-overlap ratio (mirrors ExactMatcher grades). */
    private static double similarity(String phrase, String candidate) {
        String req = AliasTable.canonicalize(phrase).trim();
        String cand = AliasTable.canonicalize(candidate).trim();
        if (req.isEmpty() || cand.isEmpty()) {
            return 0;
        }
        if (cand.contains(req)) {
            return 1.0;
        }
        var reqTokens = Normalizer.tokens(req);
        if (reqTokens.isEmpty()) {
            return 0;
        }
        var candTokens = Normalizer.tokens(cand);
        if (candTokens.containsAll(reqTokens)) {
            return 1.0;
        }
        long overlap = reqTokens.stream().filter(candTokens::contains).count();
        return (double) overlap / reqTokens.size();
    }

    /** Strongest-first: EXACT &gt; TOKEN &gt; FUZZY, then similarity desc. */
    public static Comparator<EvidenceHit> byStrength() {
        return Comparator
                .comparingInt((EvidenceHit h) -> h.grade().ordinal())
                .thenComparing(EvidenceHit::similarity, Comparator.reverseOrder());
    }
}
