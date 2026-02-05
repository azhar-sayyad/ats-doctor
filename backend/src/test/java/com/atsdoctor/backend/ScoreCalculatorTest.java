package com.atsdoctor.backend;

import com.atsdoctor.backend.infrastructure.scoring.ScoreCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** TASK-052/054/055 — weighted Job Match Score, breakdown, strengths and gaps. */
class ScoreCalculatorTest {

    private final ScoreCalculator calculator = new ScoreCalculator();

    @Test
    void perfect_match_scores_100() {
        var outcome = calculator.calculate(
                List.of(req("skills", "high", "matched")),
                List.of(new ScoreCalculator.MatchKeyword("Python", true)),
                List.of(new ScoreCalculator.MatchResponsibility("Design REST APIs", true)),
                new ScoreCalculator.MatchSeniority("Senior", true));

        assertThat(outcome.total()).isEqualTo(100);
        assertThat(outcome.breakdown().keySet()).containsExactlyElementsOf(ScoreCalculator.WEIGHTS.keySet());
        assertThat(((Map<?, ?>) outcome.breakdown().get("skills")).get("score")).isEqualTo(100);
    }

    @Test
    void weights_are_applied_per_category() {
        // skills 0.5*0.30 + keywords 0.5*0.20 + responsibilities 1*0.20 + experience 1*0.15
        // + seniority 1*0.10 + education 1*0.05 = 0.15+0.10+0.20+0.15+0.10+0.05 = 0.75
        var outcome = calculator.calculate(
                List.of(req("skill", "high", "partial"), req("experience", "high", "matched")),
                List.of(new ScoreCalculator.MatchKeyword("Python", true), new ScoreCalculator.MatchKeyword("REST", false)),
                List.of(new ScoreCalculator.MatchResponsibility("Design REST APIs", true)),
                new ScoreCalculator.MatchSeniority("Senior", true));

        assertThat(outcome.total()).isEqualTo(75);
    }

    @Test
    void importance_factors_scale_partial_and_missing_contributions() {
        // high matched = 1.0, medium unmatched = 0 → skills = 1.0*1/(1.0*1 + 0.75*1) = 0.571
        var outcome = calculator.calculate(
                List.of(req("skill", "high", "matched"), req("skill", "medium", "unmatched")),
                List.of(),
                List.of(),
                new ScoreCalculator.MatchSeniority(null, false));

        Map<?, ?> skills = (Map<?, ?>) outcome.breakdown().get("skills");
        assertThat(skills.get("score")).isEqualTo(57);
    }

    @Test
    void neutral_categories_with_no_requirements_score_100() {
        var outcome = calculator.calculate(
                List.of(),
                List.of(),
                List.of(),
                new ScoreCalculator.MatchSeniority(null, false));

        assertThat(outcome.total()).isEqualTo(100);
    }

    @Test
    void breakdown_lists_matched_and_missing_texts() {
        var outcome = calculator.calculate(
                List.of(req("skill", "high", "matched"), req("skill", "medium", "unmatched")),
                List.of(new ScoreCalculator.MatchKeyword("Python", true)),
                List.of(),
                new ScoreCalculator.MatchSeniority(null, false));

        Map<?, ?> skills = (Map<?, ?>) outcome.breakdown().get("skills");
        assertThat(skills.get("matched")).asList().containsExactly("Python and FastAPI");
        assertThat(skills.get("missing")).asList().containsExactly("Kafka streams");
    }

    @Test
    void gaps_include_deterministic_suggestions() {
        var outcome = calculator.calculate(
                List.of(req("skill", "medium", "unmatched")),
                List.of(),
                List.of(),
                new ScoreCalculator.MatchSeniority("Senior", false));

        assertThat(outcome.gaps()).hasSize(2);
        assertThat(outcome.gaps().get(0))
                .containsEntry("status", "unmatched")
                .containsEntry("text", "Kafka streams");
        @SuppressWarnings("unchecked")
        List<String> suggestions = (List<String>) outcome.gaps().get(0).get("suggestions");
        assertThat(suggestions).containsExactly(
                "Add kafka to skills if applicable.",
                "Highlight any kafka experience.");
    }

    @Test
    void strengths_list_matched_requirements_and_keywords() {
        var outcome = calculator.calculate(
                List.of(req("skill", "high", "matched")),
                List.of(new ScoreCalculator.MatchKeyword("Python", true)),
                List.of(new ScoreCalculator.MatchResponsibility("Design REST APIs", true)),
                new ScoreCalculator.MatchSeniority("Senior", true));

        assertThat(outcome.strengths()).extracting("type")
                .contains("requirement", "keyword", "responsibility", "seniority");
    }

    @Test
    void seniority_not_aligned_gets_partial_credit() {
        var outcome = calculator.calculate(
                List.of(), List.of(), List.of(),
                new ScoreCalculator.MatchSeniority("Staff", false));
        Map<?, ?> seniority = (Map<?, ?>) outcome.breakdown().get("seniority");
        assertThat(seniority.get("score")).isEqualTo(25);
    }

    private static ScoreCalculator.MatchRequirement req(String type, String importance, String status) {
        String text = "unmatched".equals(status) ? "Kafka streams"
                : "experience".equals(type) ? "5+ years of backend experience"
                : "education".equals(type) ? "BSc in Computer Science"
                : "Python and FastAPI";
        return new ScoreCalculator.MatchRequirement(text, type, importance, status);
    }
}
