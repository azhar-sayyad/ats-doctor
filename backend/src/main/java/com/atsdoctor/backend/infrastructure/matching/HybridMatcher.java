package com.atsdoctor.backend.infrastructure.matching;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Requirement → evidence matcher (TASK-049, FEAT-023): for each JD requirement
 * search the evidence index with its keywords first (exact/preferred), falling
 * back to the full requirement text. Produces the §4.4 statuses
 * {@code matched | partial | unmatched}.
 */
public final class HybridMatcher {

    private HybridMatcher() {
    }

    public static RequirementMatch match(RequirementData requirement, EvidenceIndex index) {
        List<EvidenceHit> hits = new ArrayList<>();
        Set<String> matchedKeywords = new LinkedHashSet<>();
        Set<String> seenIds = new LinkedHashSet<>();

        for (String keyword : requirement.effectiveKeywords()) {
            List<EvidenceHit> keywordHits = index.search(keyword, 3);
            boolean keywordMatched = false;
            for (EvidenceHit hit : keywordHits) {
                if (hit.grade() == ExactMatcher.Grade.EXACT) {
                    keywordMatched = true;
                }
                if (seenIds.add(hit.evidence().id())) {
                    hits.add(hit);
                }
            }
            if (keywordMatched) {
                matchedKeywords.add(keyword);
            }
        }

        if (!matchedKeywords.isEmpty()) {
            return new RequirementMatch(requirement, "matched", "exact", top(hits), List.copyOf(matchedKeywords), best(hits));
        }

        // No exact keyword hit — try the full phrase.
        List<EvidenceHit> phraseHits = index.search(requirement.text(), 3);
        for (EvidenceHit hit : phraseHits) {
            if (seenIds.add(hit.evidence().id())) {
                hits.add(hit);
            }
            if (hit.grade() == ExactMatcher.Grade.EXACT) {
                return new RequirementMatch(requirement, "matched", "exact", top(hits), List.copyOf(matchedKeywords), best(hits));
            }
        }
        if (phraseHits.isEmpty()) {
            return new RequirementMatch(requirement, "unmatched", "none", List.of(), List.copyOf(matchedKeywords), 0.0);
        }
        double best = best(hits);
        String status = best >= 0.5 ? "partial" : "unmatched";
        String type = best >= 0.5 ? phraseHits.get(0).grade().name().toLowerCase() : "none";
        return new RequirementMatch(requirement, status, type, top(hits), List.copyOf(matchedKeywords), best);
    }

    /** Cap cited evidence to keep analysis JSON lean. */
    private static List<EvidenceHit> top(List<EvidenceHit> hits) {
        return hits.stream().sorted(EvidenceHit.byStrength()).limit(3).toList();
    }

    private static double best(List<EvidenceHit> hits) {
        return hits.stream().mapToDouble(EvidenceHit::similarity).max().orElse(0.0);
    }
}
