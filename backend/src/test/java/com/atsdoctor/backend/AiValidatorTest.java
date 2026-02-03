package com.atsdoctor.backend;

import com.atsdoctor.backend.application.ai.AiRequest;
import com.atsdoctor.backend.application.ai.AiResult;
import com.atsdoctor.backend.application.ai.AiTask;
import com.atsdoctor.backend.application.ai.AIService;
import com.atsdoctor.backend.infrastructure.validation.AiValidator;
import com.atsdoctor.backend.infrastructure.validation.ValidationIssue;
import com.atsdoctor.backend.infrastructure.validation.ValidationIssueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TASK-068 — AiValidator: fact_validation wiring, prompt variables, tolerant issue normalization. */
@ExtendWith(MockitoExtension.class)
class AiValidatorTest {

    @Mock
    private AIService aiService;

    private AiValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AiValidator(aiService);
    }

    @Test
    void calls_fact_validation_with_prompt_variables() {
        when(aiService.generate(any())).thenReturn(result("{\"is_valid\": true, \"issues\": []}"));

        validator.validate("A", "Built FastAPI services processing 2M events/day.",
                "Built FastAPI backend services using Python.",
                List.of("FastAPI and Python backend experience."));

        ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
        verify(aiService).generate(captor.capture());
        AiRequest request = captor.getValue();
        assertThat(request.task()).isEqualTo(AiTask.FACT_VALIDATION);
        assertThat(request.input()).isEqualTo("Built FastAPI backend services using Python.");
        assertThat(request.variables())
                .containsKeys("original_resume", "tailored_resume", "resume_evidence", "claim_category");
        assertThat(request.variables().get("claim_category")).isEqualTo("A");
        assertThat(request.variables().get("original_resume"))
                .isEqualTo("Built FastAPI services processing 2M events/day.");
    }

    @Test
    void maps_typed_issues_from_json_object() {
        when(aiService.generate(any())).thenReturn(result("""
                {"is_valid": false, "issues": [
                  {"type": "unsupported_technology", "text": "Kubernetes", "original_evidence": "", "suggestion": "drop"},
                  {"type": "inflated_metric", "text": "10M", "original_evidence": "2M events", "suggestion": "revert"},
                  {"type": "new_company", "text": "Google", "original_evidence": "Tech Corp", "suggestion": "remove"},
                  {"type": "new_title", "text": "Director", "original_evidence": "Engineer", "suggestion": "remove"},
                  {"type": "unsupported_achievement", "text": "Led a team of 50", "original_evidence": "", "suggestion": "remove"},
                  {"type": "unsupported_descriptor", "text": "enterprise", "original_evidence": "", "suggestion": "remove"}
                ]}"""));

        List<ValidationIssue> issues =
                validator.validate("A", "orig", "tailored", List.of("evidence"));

        assertThat(issues).hasSize(6);
        assertThat(issues)
                .filteredOn(i -> i.code().equals("unsupported_technology"))
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.type()).isEqualTo(ValidationIssueType.UNSUPPORTED_FACT);
                    assertThat(i.text()).isEqualTo("Kubernetes");
                });
        assertThat(issues)
                .filteredOn(i -> i.code().equals("new_company"))
                .singleElement()
                .satisfies(i -> assertThat(i.type()).isEqualTo(ValidationIssueType.UNSUPPORTED_FACT));
        assertThat(issues)
                .filteredOn(i -> i.code().equals("unsupported_achievement"))
                .singleElement()
                .satisfies(i -> assertThat(i.type()).isEqualTo(ValidationIssueType.UNSUPPORTED_CLAIM));
        assertThat(issues)
                .filteredOn(i -> i.code().equals("unsupported_descriptor"))
                .singleElement()
                .satisfies(i -> assertThat(i.type()).isEqualTo(ValidationIssueType.UNSUPPORTED_DESCRIPTOR));
        assertThat(issues)
                .filteredOn(i -> i.text().equals("10M"))
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.code()).isEqualTo("inflated_metric");
                    assertThat(i.context()).isEqualTo("2M events");
                });
    }

    @Test
    void parses_a_bare_issues_array() {
        when(aiService.generate(any())).thenReturn(result(
                "[{\"type\": \"new_title\", \"text\": \"CTO\", \"suggestion\": \"remove\"}]"));

        List<ValidationIssue> issues = validator.validate("B", "o", "t", List.of());

        assertThat(issues).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo("new_title");
            assertThat(i.type()).isEqualTo(ValidationIssueType.UNSUPPORTED_FACT);
            assertThat(i.text()).isEqualTo("CTO");
            assertThat(i.context()).isEqualTo("CTO");
        });
    }

    @Test
    void clean_verdict_yields_no_issues() {
        when(aiService.generate(any())).thenReturn(result(
                "{\"is_valid\": true, \"issues\": []}"));

        assertThat(validator.validate("A", "o", "tailored", List.of())).isEmpty();
    }

    @Test
    void malformed_and_blank_outputs_are_tolerated() {
        when(aiService.generate(any())).thenReturn(result("not json at all"));
        assertThat(validator.validate("A", "o", "t", List.of())).isEmpty();

        when(aiService.generate(any())).thenReturn(result(""));
        assertThat(validator.validate("A", "o", "t", List.of())).isEmpty();

        when(aiService.generate(any())).thenReturn(result("{\"issues\": []}"));
        assertThat(validator.validate("B", "o", "t", List.of())).isEmpty();
    }

    @Test
    void blank_tailored_skips_the_ai_call() {
        assertThat(validator.validate("A", "original", "   ", List.of("evidence"))).isEmpty();
        verify(aiService, never()).generate(any());
    }

    @Test
    void clips_oversized_evidence_context() {
        when(aiService.generate(any())).thenReturn(result("{\"is_valid\": true, \"issues\": []}"));
        String huge = "word ".repeat(4000);

        validator.validate("A", "o", "t", List.of(huge));

        ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
        verify(aiService).generate(captor.capture());
        String evidence = (String) captor.getValue().variables().get("resume_evidence");
        assertThat(evidence.length()).isLessThanOrEqualTo(AiValidator.MAX_CONTEXT_CHARS + 1);
    }

    private static AiResult result(String output) {
        return new AiResult(AiTask.FACT_VALIDATION, output, "stub", "stub", 5, Map.of());
    }
}