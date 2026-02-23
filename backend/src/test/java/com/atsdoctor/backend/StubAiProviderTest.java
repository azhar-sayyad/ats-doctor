package com.atsdoctor.backend;

import com.atsdoctor.backend.application.ai.AiRequest;
import com.atsdoctor.backend.application.ai.AiTask;
import com.atsdoctor.backend.infrastructure.ai.AiProvider;
import com.atsdoctor.backend.infrastructure.ai.ProviderRequest;
import com.atsdoctor.backend.infrastructure.ai.ProviderResponse;
import com.atsdoctor.backend.infrastructure.ai.StubAiProvider;
import com.atsdoctor.backend.infrastructure.validation.ValidationIssue;
import com.atsdoctor.backend.infrastructure.validation.ValidationIssueNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** TASK-068 — stub fact_validation output: every issue is well-formed (no %s literals). */
class StubAiProviderTest {

    private final StubAiProvider provider = new StubAiProvider();

    @Test
    void fact_validation_issues_are_fully_formatted() {
        AiRequest request = new AiRequest(AiTask.FACT_VALIDATION, "Built microservices with Java and Kafka",
                java.util.Map.of(
                        "original_resume", "Built microservices with Java",
                        "tailored_resume", "Built microservices with Java and Kafka",
                        "resume_evidence", "Built microservices with Java",
                        "claim_category", "B"));

        ProviderResponse response = complete(request);

        List<ValidationIssue> issues = ValidationIssueNormalizer.parse(response.output());
        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.text()).isEqualTo("kafka");
            assertThat(issue.code()).isEqualTo("unsupported_descriptor");
            assertThat(issue.suggestion()).contains("kafka");
            assertThat(issue.suggestion()).doesNotContain("%s");
        });
    }

    @Test
    void fact_validation_types_technology_shaped_tokens() {
        AiRequest request = new AiRequest(AiTask.FACT_VALIDATION, "Built services with Java and c++",
                java.util.Map.of(
                        "original_resume", "Built services with Java",
                        "tailored_resume", "Built services with Java and c++",
                        "resume_evidence", "Built services with Java",
                        "claim_category", "B"));

        ProviderResponse response = complete(request);

        List<ValidationIssue> issues = ValidationIssueNormalizer.parse(response.output());
        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.text()).isEqualTo("c++");
            assertThat(issue.code()).isEqualTo("unsupported_technology");
            assertThat(issue.suggestion()).contains("c++").doesNotContain("%s");
        });
    }

    @Test
    void fact_validation_strips_trailing_punctuation_before_grounding() {
        AiRequest request = new AiRequest(AiTask.FACT_VALIDATION, "Built services with postgresql.",
                java.util.Map.of(
                        "original_resume", "Built services",
                        "tailored_resume", "Built services with postgresql.",
                        "resume_evidence", "postgresql",
                        "claim_category", "B"));

        ProviderResponse response = complete(request);

        assertThat(ValidationIssueNormalizer.parse(response.output())).isEmpty();
    }

    @Test
    void fact_validation_category_c_is_clean() {
        AiRequest request = new AiRequest(AiTask.FACT_VALIDATION, "anything",
                java.util.Map.of(
                        "original_resume", "original",
                        "tailored_resume", "tailored",
                        "resume_evidence", "evidence",
                        "claim_category", "C"));

        ProviderResponse response = complete(request);

        assertThat(ValidationIssueNormalizer.parse(response.output())).isEmpty();
    }

    private ProviderResponse complete(AiRequest request) {
        try {
            return provider.complete(new ProviderRequest(request, null));
        } catch (com.atsdoctor.backend.infrastructure.ai.AiProviderException ex) {
            throw new AssertionError(ex);
        }
    }
}
