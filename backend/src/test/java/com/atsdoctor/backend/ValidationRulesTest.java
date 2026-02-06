package com.atsdoctor.backend;

import com.atsdoctor.backend.application.ai.AiRequest;
import com.atsdoctor.backend.application.ai.AiResult;
import com.atsdoctor.backend.application.ai.AiTask;
import com.atsdoctor.backend.application.ai.AIService;
import com.atsdoctor.backend.infrastructure.validation.DeterministicValidator;
import com.atsdoctor.backend.infrastructure.validation.ValidationIssue;
import com.atsdoctor.backend.infrastructure.validation.ValidationIssueType;
import com.atsdoctor.backend.infrastructure.validation.ValidationRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** TASK-070 — ValidationRules: merges deterministic + AI stages, types A/B/C, dedupes. */
@ExtendWith(MockitoExtension.class)
class ValidationRulesTest {

    @Mock
    private AIService aiService;

    private ValidationRules engine;

    @BeforeEach
    void setUp() {
        engine = new ValidationRules(new DeterministicValidator(),
                new com.atsdoctor.backend.infrastructure.validation.AiValidator(aiService));
    }

    @Test
    void merges_dedupes_and_orders_issues_across_stages() {
        when(aiService.generate(any())).thenReturn(aiResult("""
                {"is_valid": false, "issues": [
                  {"type": "unsupported_technology", "text": "C++", "suggestion": "drop"},
                  {"type": "new_company", "text": "Google", "suggestion": "drop"}
                ]}"""));

        ValidationRules.Verdict verdict = engine.validate(
                "A",
                "Built Docker-based deployment pipelines.",
                "Built Docker-based deployment pipelines with C++ and Kubernetes.",
                List.of("Deployment experience with Docker."));

        assertThat(verdict.valid()).isFalse();
        // c++ appears in BOTH stages (case-insensitive dedupe collapses it onto the
        // deterministic hit); google comes only from the AI stage; kubernetes is a
        // descriptor per the deterministic heuristic (no digit / no technology shape).
        assertThat(verdict.issues())
                .extracting(ValidationIssue::text)
                .containsExactly("c++", "Google", "kubernetes");
        assertThat(verdict.issues())
                .extracting(ValidationIssue::type)
                .containsExactly(ValidationIssueType.UNSUPPORTED_FACT,
                        ValidationIssueType.UNSUPPORTED_FACT,
                        ValidationIssueType.UNSUPPORTED_DESCRIPTOR);
    }

    @Test
    void deterministic_stage_beats_the_ai_stage_on_duplicate_issues() {
        when(aiService.generate(any())).thenReturn(aiResult("""
                {"is_valid": false, "issues": [
                  {"type": "unsupported_technology", "text": "Kubernetes", "suggestion": "ai says drop"}
                ]}"""));

        ValidationRules.Verdict verdict = engine.validate(
                "A",
                "Built Docker-based deployment pipelines.",
                "Built Docker-based deployment pipelines with Kubernetes.",
                List.of("Deployment experience with Docker."));

        assertThat(verdict.issues()).hasSize(1);
        assertThat(verdict.issues().get(0).text()).isEqualTo("kubernetes");
        assertThat(verdict.issues().get(0).suggestion()).contains("resume evidence");
    }

    @Test
    void category_b_descriptors_keep_their_type_after_merge() {
        when(aiService.generate(any())).thenReturn(aiResult("{\"is_valid\": true, \"issues\": []}"));

        ValidationRules.Verdict verdict = engine.validate(
                "B",
                "Built services for event processing.",
                "Built scalable services for event processing.",
                List.of("Event processing only."));

        assertThat(verdict.valid()).isFalse();
        assertThat(verdict.issues()).singleElement().satisfies(i -> {
            assertThat(i.type()).isEqualTo(ValidationIssueType.UNSUPPORTED_DESCRIPTOR);
            assertThat(i.code()).isEqualTo("unsupported_descriptor");
        });
    }

    @Test
    void category_c_claims_get_achievement_typing() {
        when(aiService.generate(any())).thenReturn(aiResult("{\"is_valid\": true, \"issues\": []}"));

        ValidationRules.Verdict verdict = engine.validate(
                "C",
                "Worked on internal tools.",
                "Led a team of 10 to deliver the flagship platform.",
                List.of("Internal tooling maintenance."));

        assertThat(verdict.valid()).isFalse();
        assertThat(verdict.issues()).isNotEmpty();
        assertThat(verdict.issues()).allSatisfy(i -> {
            assertThat(i.type()).isEqualTo(ValidationIssueType.UNSUPPORTED_CLAIM);
            assertThat(i.code()).isEqualTo("unsupported_achievement");
        });
    }

    @Test
    void grounded_claim_is_valid() {
        when(aiService.generate(any())).thenReturn(aiResult("{\"is_valid\": true, \"issues\": []}"));

        ValidationRules.Verdict verdict = engine.validate(
                "A",
                "Built FastAPI services processing 2M events/day.",
                "Built FastAPI services processing 2M events/day using Python and PostgreSQL.",
                List.of("FastAPI, Python and PostgreSQL backend experience."));

        assertThat(verdict.valid()).isTrue();
        assertThat(verdict.issues()).isEmpty();
    }

    @Test
    void null_and_blank_variance_never_throws() {
        assertThat(engine.validate(null, null, null, null)).isEqualTo(ValidationRules.Verdict.clean());
        assertThat(engine.validate("", "", "", List.of()).valid()).isTrue();
    }

    private static AiResult aiResult(String output) {
        return new AiResult(AiTask.FACT_VALIDATION, output, "stub", "stub", 5, Map.of());
    }
}