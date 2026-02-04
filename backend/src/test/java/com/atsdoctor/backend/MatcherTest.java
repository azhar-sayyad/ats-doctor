package com.atsdoctor.backend;

import com.atsdoctor.backend.infrastructure.matching.EvidenceDoc;
import com.atsdoctor.backend.infrastructure.matching.EvidenceHit;
import com.atsdoctor.backend.infrastructure.matching.EvidenceIndex;
import com.atsdoctor.backend.infrastructure.matching.ExactMatcher;
import com.atsdoctor.backend.infrastructure.matching.HybridMatcher;
import com.atsdoctor.backend.infrastructure.matching.MatchPipeline;
import com.atsdoctor.backend.infrastructure.matching.MatchRequest;
import com.atsdoctor.backend.infrastructure.matching.MatchingResult;
import com.atsdoctor.backend.infrastructure.matching.RequirementData;
import com.atsdoctor.backend.infrastructure.matching.RequirementMatch;
import com.atsdoctor.backend.infrastructure.matching.SemanticMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** TASK-047/048/049 — exact/alias/keyword matching, evidence index, requirement→evidence mapping. */
class MatcherTest {

    private static final EvidenceIndex INDEX = EvidenceIndex.build(List.of(
            new EvidenceDoc("b1", "Built FastAPI services processing 2M events/day."),
            new EvidenceDoc("b2", "Led a team of 4 engineers delivering the fraud detection platform."),
            new EvidenceDoc("role", "Senior Backend Engineer at Tech Corp"),
            new EvidenceDoc("s1", "Python"),
            new EvidenceDoc("s2", "FastAPI"),
            new EvidenceDoc("s3", "PostgreSQL")));

    @Test
    void exact_skill_match() {
        assertThat(ExactMatcher.match("Python", "Built services in Python")).contains(ExactMatcher.Grade.EXACT);
        assertThat(ExactMatcher.match("Python", "unrelated text")).isEmpty();
    }

    @Test
    void alias_canonicalization_js_to_javascript() {
        assertThat(ExactMatcher.match("JavaScript", "5 years of JS experience")).contains(ExactMatcher.Grade.EXACT);
    }

    @Test
    void token_overlap_is_partial_grade() {
        assertThat(ExactMatcher.match("REST API", "API")).contains(ExactMatcher.Grade.TOKEN);
    }

    @Test
    void back_end_is_canonicalized_to_backend() {
        assertThat(ExactMatcher.match("backend", "back-end developer")).contains(ExactMatcher.Grade.EXACT);
    }

    @Test
    void fuzzy_matches_close_spellings() {
        assertThat(ExactMatcher.match("spring", "spirng framework experience")).contains(ExactMatcher.Grade.FUZZY);
        assertThat(ExactMatcher.match("backend", "triage")).isEmpty();
    }

    @Test
    void evidence_index_returns_strongest_first() {
        var hits = INDEX.search("FastAPI", 5);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).grade()).isEqualTo(ExactMatcher.Grade.EXACT);
        assertThat(hits.get(0).similarity()).isEqualTo(1.0);
    }

    @Test
    void index_caps_results_and_skips_non_matches() {
        assertThat(INDEX.search("zzz-nothing", 3)).isEmpty();
        assertThat(INDEX.search("Python", 1)).hasSize(1);
    }

    @Test
    void hybrid_matcher_marks_requirement_matched_on_keyword_exact() {
        RequirementData req = new RequirementData("r1", "Experience with Python and FastAPI",
                "skill", "high", List.of("Python", "FastAPI"));

        RequirementMatch match = HybridMatcher.match(req, INDEX);

        assertThat(match.status()).isEqualTo("matched");
        assertThat(match.matchType()).isEqualTo("exact");
        assertThat(match.keywordsMatched()).containsExactly("Python", "FastAPI");
        assertThat(match.isMatched()).isTrue();
    }

    @Test
    void hybrid_matcher_reports_unmatched_when_nothing_overlaps() {
        RequirementData req = new RequirementData("r2", "Experience with Kafka streams",
                "skill", "high", List.of("Kafka"));

        RequirementMatch match = HybridMatcher.match(req, INDEX);

        assertThat(match.status()).isEqualTo("unmatched");
        assertThat(match.matchType()).isEqualTo("none");
        assertThat(match.similarity()).isEqualTo(0.0);
    }

    @Test
    void hybrid_matcher_falls_back_to_requirement_tokens_when_no_keywords() {
        RequirementData req = new RequirementData("r3", "Fraud detection leadership",
                "skill", "medium", List.of());

        RequirementMatch match = HybridMatcher.match(req, INDEX);

        assertThat(match.status()).isEqualTo("matched");
        assertThat(match.evidence()).isNotEmpty();
    }

    @Test
    void evidence_index_edge_paths() {
        EvidenceIndex empty = EvidenceIndex.build(null);
        assertThat(empty.docs()).isEmpty();
        assertThat(empty.search("anything", 3)).isEmpty();
        assertThat(empty.idsForToken("python")).isEmpty();
        assertThat(INDEX.docs()).isNotEmpty();
        assertThat(INDEX.idsForToken("python")).isNotEmpty();
        assertThat(INDEX.idsForToken("  ")).isEmpty();
    }

    @Test
    void exact_matcher_empty_and_token_count_paths() {
        assertThat(ExactMatcher.match("", "anything")).isEmpty();
        assertThat(ExactMatcher.match("anything", "")).isEmpty();
        assertThat(ExactMatcher.match(null, "anything")).isEmpty();
        assertThat(ExactMatcher.match("Python FastAPI", "FastAPI Python experience"))
                .contains(ExactMatcher.Grade.EXACT);
        assertThat(ExactMatcher.match("REST API", "API development"))
                .contains(ExactMatcher.Grade.TOKEN);
    }

    @Test
    void hybrid_matcher_phrase_fallback_paths() {
        RequirementMatch phraseExact = HybridMatcher.match(
                new RequirementData("r5", "services processing events", "skill", "medium", List.of("zzz-none")),
                INDEX);
        assertThat(phraseExact.status()).isEqualTo("matched");
        assertThat(phraseExact.matchType()).isEqualTo("exact");

        RequirementMatch none = HybridMatcher.match(
                new RequirementData("r6", "quantum teleportation COBOL", "skill", "low", List.of("zzz-none")),
                INDEX);
        assertThat(none.status()).isEqualTo("unmatched");
        assertThat(none.matchType()).isEqualTo("none");
        assertThat(none.similarity()).isEqualTo(0.0);

        RequirementMatch partial = HybridMatcher.match(
                new RequirementData("r7", "fraud kafka", "skill", "high", List.of("zzz-none")),
                INDEX);
        assertThat(partial.status()).isEqualTo("partial");
        assertThat(partial.matchType()).isEqualTo("token");
    }

    @Test
    void match_pipeline_plain_run_covers_keywords_responsibilities_and_seniority() {
        MatchingResult res = new MatchPipeline(new SemanticMatcher(false)).run(new MatchRequest(
                List.of(new RequirementData("r8", "quantum teleportation", "skill", "high", List.of("zzz"))),
                List.of("Python", "COBOL", "zzz-ghost"),
                List.of("Python developer", "zzz-flaky", "Leadership"),
                "Senior",
                INDEX.docs(),
                "Hands-on Senior COBOL experience.",
                8));

        assertThat(res.requirements()).singleElement().satisfies(m -> {
            assertThat(m.status()).isEqualTo("unmatched");
            assertThat(m.matchType()).isEqualTo("none");
        });
        assertThat(res.keywordHits()).extracting(report -> report.keyword() + ":" + report.matched())
                .containsExactly("Python:true", "COBOL:true", "zzz-ghost:false");
        assertThat(res.responsibilities()).hasSize(3);
        assertThat(res.responsibilities().get(0).matched()).isTrue();
        assertThat(res.responsibilities().get(1).matched()).isFalse();
        assertThat(res.seniority().jdSeniority()).isEqualTo("Senior");
        assertThat(res.seniority().resumeSeniority()).isEqualTo("senior");
        assertThat(res.seniority().aligned()).isTrue();
    }

    @Test
    void match_pipeline_replaces_unmatched_with_semantic_hit() {
        SemanticMatcher returning = new SemanticMatcher(true) {
            @Override
            public Optional<EvidenceHit> findSemanticMatch(String hint) {
                return Optional.of(new EvidenceHit(
                        new EvidenceDoc("sem", "semantic tech"), ExactMatcher.Grade.FUZZY, 0.75));
            }
        };

        MatchingResult res = new MatchPipeline(returning).run(new MatchRequest(
                List.of(new RequirementData("r9", "quantum teleportation", "skill", "high", List.of())),
                List.of(), List.of(), null, INDEX.docs(), "", 0));

        assertThat(res.requirements()).singleElement().satisfies(m -> {
            assertThat(m.status()).isEqualTo("partial");
            assertThat(m.matchType()).isEqualTo("semantic");
            assertThat(m.similarity()).isEqualTo(0.75);
        });
        assertThat(res.seniority().jdSeniority()).isNull();
        assertThat(res.seniority().aligned()).isFalse();
    }

    @Test
    void semantic_matcher_enabled_returns_empty_with_warning() {
        SemanticMatcher enabled = new SemanticMatcher(true);
        assertThat(enabled.enabled()).isTrue();
        assertThat(enabled.findSemanticMatch("any hint")).isEmpty();
    }
}
