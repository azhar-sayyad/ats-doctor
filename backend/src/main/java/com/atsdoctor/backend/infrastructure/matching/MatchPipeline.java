package com.atsdoctor.backend.infrastructure.matching;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Phased matching orchestration (TASK-050, FEAT-024; PRD §5.3): exact-first —
 * one shared evidence index is built, then requirements, JD keywords and
 * responsibilities are matched against it, and seniority is aligned by token.
 * The semantic phase is consulted only for still-unmatched requirements and is
 * a no-op stub today (TASK-051).
 */
@Component
public class MatchPipeline {

    private final SemanticMatcher semanticMatcher;

    public MatchPipeline(SemanticMatcher semanticMatcher) {
        this.semanticMatcher = semanticMatcher;
    }

    public MatchingResult run(MatchRequest request) {
        EvidenceIndex index = EvidenceIndex.build(request.evidence());
        List<RequirementMatch> requirements = new ArrayList<>();
        for (RequirementData requirement : request.requirements()) {
            RequirementMatch match = HybridMatcher.match(requirement, index);
            if ("unmatched".equals(match.status()) && semanticMatcher.enabled()) {
                match = semanticMatcher.findSemanticMatch(requirement.text())
                        .map(hit -> new RequirementMatch(requirement, "partial", "semantic",
                                List.of(hit), List.of(), hit.similarity()))
                        .orElse(match);
            }
            requirements.add(match);
        }
        return new MatchingResult(
                requirements,
                matchKeywords(request.keywords(), request.resumeText(), index),
                matchResponsibilities(request.responsibilities(), index),
                alignSeniority(request.seniority(), request.resumeText()));
    }

    private static List<KeywordHit> matchKeywords(List<String> keywords, String resumeText, EvidenceIndex index) {
        List<KeywordHit> hits = new ArrayList<>();
        for (String keyword : keywords) {
            List<EvidenceHit> found = index.search(keyword, 3);
            boolean inResumeText = Normalizer.tokensContained(keyword, resumeText);
            List<String> ids = found.stream().map(h -> h.evidence().id()).toList();
            hits.add(new KeywordHit(keyword, !found.isEmpty() || inResumeText, ids));
        }
        return hits;
    }

    private static List<ResponsibilityMatch> matchResponsibilities(List<String> responsibilities, EvidenceIndex index) {
        List<ResponsibilityMatch> out = new ArrayList<>();
        for (String responsibility : responsibilities) {
            List<EvidenceHit> found = index.search(responsibility, 3);
            boolean matched = !found.isEmpty()
                    && found.stream().anyMatch(h -> h.grade() != ExactMatcher.Grade.FUZZY);
            String type = found.isEmpty() ? "none" : found.get(0).grade().name().toLowerCase();
            out.add(new ResponsibilityMatch(responsibility, matched, type,
                    found.stream().map(h -> h.evidence().id()).toList()));
        }
        return out;
    }

    private static SeniorityMatch alignSeniority(String jdSeniority, String resumeText) {
        if (jdSeniority == null || jdSeniority.isBlank()) {
            return new SeniorityMatch(null, null, false);
        }
        Set<String> jdTokens = Normalizer.tokens(jdSeniority);
        Set<String> resumeTokens = Normalizer.tokens(resumeText);
        String detected = jdTokens.stream().filter(resumeTokens::contains).findFirst().orElse(null);
        return new SeniorityMatch(jdSeniority, detected, detected != null);
    }
}
