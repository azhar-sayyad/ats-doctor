package com.atsdoctor.backend.infrastructure.matching;

import java.util.List;

/** Match outcome for one requirement: which evidence supports it and how strongly. */
public record RequirementMatch(
        RequirementData requirement,
        String status,
        String matchType,
        List<EvidenceHit> evidence,
        List<String> keywordsMatched,
        double similarity) {

    /** §4.4 statuses: matched / partial / unmatched. */
    public boolean isMatched() {
        return "partial".equals(status) || "matched".equals(status);
    }
}
