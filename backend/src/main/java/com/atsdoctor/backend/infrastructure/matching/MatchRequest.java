package com.atsdoctor.backend.infrastructure.matching;

import java.util.List;

/** Everything the pipeline needs to score one job against one resume version. */
public record MatchRequest(
        List<RequirementData> requirements,
        List<String> keywords,
        List<String> responsibilities,
        String seniority,
        List<EvidenceDoc> evidence,
        String resumeText,
        Integer maxResumeYears) {

    public MatchRequest {
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        responsibilities = responsibilities == null ? List.of() : List.copyOf(responsibilities);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        resumeText = resumeText == null ? "" : resumeText;
    }
}
