package com.atsdoctor.backend.application.analysis;

import com.atsdoctor.backend.domain.states.AnalysisState;
import com.atsdoctor.backend.infrastructure.matching.MatchPipeline;
import com.atsdoctor.backend.infrastructure.matching.MatchingResult;
import com.atsdoctor.backend.infrastructure.matching.RequirementMatch;
import com.atsdoctor.backend.infrastructure.scoring.ScoreCalculator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Async analysis pipeline (FEAT-027, TASK-056/057): QUEUED → MATCHING (exact
 * matching, one stub hook for semantics) → SCORING (deterministic weights) →
 * READY with score/breakdown/matches/gaps persisted. No AI calls — matching is
 * deterministic in the MVP, so nothing is recorded in {@code ai_runs}.
 */
@Component
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class AnalysisPipeline {

    private static final Logger log = LoggerFactory.getLogger(AnalysisPipeline.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AnalysisService analysisService;
    private final MatchPipeline matchPipeline;
    private final ScoreCalculator scoreCalculator;

    public AnalysisPipeline(AnalysisService analysisService, MatchPipeline matchPipeline,
                            ScoreCalculator scoreCalculator) {
        this.analysisService = analysisService;
        this.matchPipeline = matchPipeline;
        this.scoreCalculator = scoreCalculator;
    }

    @Async("resumeTaskExecutor")
    @EventListener
    public void onQueued(AnalysisQueuedEvent event) {
        UUID analysisId = event.analysisId();
        try {
            analysisService.mark(analysisId, AnalysisState.MATCHING);
            AnalysisService.AnalysisInput input = analysisService.load(analysisId);
            MatchingResult result = matchPipeline.run(input.matchRequest());

            analysisService.mark(analysisId, AnalysisState.SCORING);
            ScoreCalculator.ScoreOutcome outcome = scoreCalculator.calculate(
                    result.requirements().stream().map(r -> new ScoreCalculator.MatchRequirement(
                            r.requirement().text(), r.requirement().type(), r.requirement().importance(), r.status())).toList(),
                    result.keywordHits().stream().map(k -> new ScoreCalculator.MatchKeyword(k.keyword(), k.matched())).toList(),
                    result.responsibilities().stream().map(r -> new ScoreCalculator.MatchResponsibility(r.text(), r.matched())).toList(),
                    new ScoreCalculator.MatchSeniority(result.seniority().jdSeniority(), result.seniority().aligned()));

            // The §4.4 breakdown gains a `total` key so the persisted score is
            // reconstructible from the JSON alone (FEAT-026).
            Map<String, Object> breakdown = new LinkedHashMap<>(outcome.breakdown());
            breakdown.put("total", outcome.total());
            analysisService.completeSuccess(
                    analysisId,
                    write(breakdown),
                    write(matchesJson(result.requirements())),
                    write(outcome.gaps()),
                    write(generationJson()));
            log.info("Analysis {} complete: score {} ({} requirements, {} gaps)",
                    analysisId, outcome.total(), result.requirements().size(), outcome.gaps().size());
        } catch (Exception ex) {
            analysisService.completeFailure(analysisId, ex);
        }
    }

    /** §4.4-shaped matches list. */
    private static List<Map<String, Object>> matchesJson(List<RequirementMatch> matches) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RequirementMatch m : matches) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("requirement_id", m.requirement().id());
            map.put("requirement_text", m.requirement().text());
            map.put("status", m.status());
            map.put("match_type", m.matchType());
            map.put("keywords_matched", m.keywordsMatched());
            map.put("similarity", m.similarity());
            map.put("evidence", m.evidence().stream().map(h -> {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("id", h.evidence().id());
                e.put("text", h.evidence().text());
                return e;
            }).toList());
            out.add(map);
        }
        return out;
    }

    /** Transparency metadata: which matching/scoring recipe produced this run. */
    private static Map<String, Object> generationJson() {
        Map<String, Object> matching = new LinkedHashMap<>();
        matching.put("mode", "exact");
        matching.put("semantic_enabled", false);
        Map<String, Object> scoring = new LinkedHashMap<>();
        scoring.put("formula", "hybrid-weights-v1");
        scoring.put("weights", new LinkedHashMap<>(ScoreCalculator.WEIGHTS));
        Map<String, Object> generation = new LinkedHashMap<>();
        generation.put("matching", matching);
        generation.put("scoring", scoring);
        return generation;
    }

    private static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize analysis output", ex);
        }
    }
}
