package com.atsdoctor.backend.infrastructure.matching;

import java.util.List;

/** All match outputs used by the scorer. */
public record MatchingResult(
        List<RequirementMatch> requirements,
        List<KeywordHit> keywordHits,
        List<ResponsibilityMatch> responsibilities,
        SeniorityMatch seniority) {
}
