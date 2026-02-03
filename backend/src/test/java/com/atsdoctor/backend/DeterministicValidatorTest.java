package com.atsdoctor.backend;

import com.atsdoctor.backend.infrastructure.validation.DeterministicValidator;
import com.atsdoctor.backend.infrastructure.validation.ValidationIssue;
import com.atsdoctor.backend.infrastructure.validation.ValidationIssueType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** TASK-067 — DeterministicValidator: A-facts grounded, B-descriptors supported, C deferred. */
class DeterministicValidatorTest {

    private final DeterministicValidator validator = new DeterministicValidator();

    @Test
    void grounded_category_a_claim_passes_clean() {
        List<ValidationIssue> issues = validator.validate(
                "A",
                "Built FastAPI services processing 2M events/day.",
                "Built FastAPI backend services processing 2M events/day using Python and PostgreSQL.",
                evidence("Building FastAPI, Python and PostgreSQL backend services."));

        assertThat(issues).isEmpty();
    }

    @Test
    void flags_new_technology_as_unsupported() {
        List<ValidationIssue> issues = validator.validate(
                "A",
                "Built Docker-based deployment pipelines.",
                "Built Docker-based deployment pipelines with Kubernetes.",
                evidence("Deployment experience with Docker."));

        assertThat(issues)
                .extracting(ValidationIssue::text)
                .containsExactly("kubernetes");
        assertThat(issues.get(0).type()).isEqualTo(ValidationIssueType.UNSUPPORTED_DESCRIPTOR);
        assertThat(issues.get(0).code()).isEqualTo("unsupported_descriptor");
        assertThat(issues.get(0).suggestion()).contains("kubernetes");
    }

    @Test
    void flags_technology_shaped_tokens_as_unsupported_fact() {
        List<ValidationIssue> issues = validator.validate(
                "A",
                "Built event processing in C.",
                "Built event processing in C++, .NET and node.js.",
                evidence("Event processing in C on embedded devices."));

        assertThat(issues)
                .extracting(ValidationIssue::text)
                .containsExactlyInAnyOrder("c++", ".net", "node.js");
        assertThat(issues).allSatisfy(i ->
                assertThat(i.type()).isEqualTo(ValidationIssueType.UNSUPPORTED_FACT));
        assertThat(issues)
                .extracting(ValidationIssue::code)
                .containsOnly("unsupported_technology");
    }

    @Test
    void flags_inflated_metric_when_it_differs_from_evidence() {
        List<ValidationIssue> issues = validator.validate(
                "A",
                "Built FastAPI services processing 2M events/day.",
                "Built FastAPI services processing 10M events/day.",
                evidence("Built FastAPI services processing 2M events/day."));

        assertThat(issues)
                .extracting(ValidationIssue::text)
                .containsExactly("10m");
        assertThat(issues.get(0).type()).isEqualTo(ValidationIssueType.UNSUPPORTED_FACT);
        assertThat(issues.get(0).code()).isEqualTo("inflated_metric");
        assertThat(issues.get(0).suggestion()).contains("Revert to the original metric");
    }

    @Test
    void metric_present_in_evidence_passes() {
        List<ValidationIssue> issues = validator.validate(
                "A",
                "Reduced latency on the queue.",
                "Reduced queue latency by 40%.",
                evidence("Reduced latency by 40% on the ingest queue."));

        assertThat(issues).isEmpty();
    }

    @Test
    void supports_descriptor_found_in_evidence() {
        List<ValidationIssue> issues = validator.validate(
                "B",
                "Built services for event processing.",
                "Built scalable services for event processing.",
                evidence("Scalable event processing at 2M events/day."));

        assertThat(issues).isEmpty();
    }

    @Test
    void flags_descriptor_without_evidence_support() {
        List<ValidationIssue> issues = validator.validate(
                "B",
                "Built services for event processing.",
                "Built secure enterprise services for event processing.",
                evidence("Event processing services in production."));

        assertThat(issues)
                .extracting(ValidationIssue::text)
                .containsExactlyInAnyOrder("enterprise", "secure");
        assertThat(issues).allSatisfy(i ->
                assertThat(i.type()).isEqualTo(ValidationIssueType.UNSUPPORTED_DESCRIPTOR));
    }

    @Test
    void jd_keyword_alone_is_not_a_license_to_add_it() {
        List<ValidationIssue> issues = validator.validate(
                "B",
                "Managed Docker containers.",
                "Managed Docker containers with a focus on Kubernetes.",
                evidence("Docker container orchestration at scale."));

        assertThat(issues)
                .extracting(ValidationIssue::text)
                .contains("kubernetes");
    }

    @Test
    void category_c_claims_are_deferred_to_the_rule_engine() {
        List<ValidationIssue> issues = validator.validate(
                "C",
                "Worked on internal tools.",
                "Led a team of 10 to deliver the flagship platform.",
                evidence("Internal tooling maintenance."));

        assertThat(issues).isEmpty();
    }

    @Test
    void validate_claims_types_introduced_content_as_unsupported_achievement() {
        List<ValidationIssue> issues = validator.validateClaims(
                "Worked on internal tools.",
                "Led a team of 10x growth to deliver the flagship platform.",
                evidence("Internal tooling maintenance."));

        assertThat(issues).isNotEmpty();
        assertThat(issues).allSatisfy(i -> {
            assertThat(i.type()).isEqualTo(ValidationIssueType.UNSUPPORTED_CLAIM);
            assertThat(i.code()).isEqualTo("unsupported_achievement");
            assertThat(i.suggestion()).contains("achievement");
        });
        assertThat(issues)
                .filteredOn(i -> i.text().equals("10x"))
                .isNotEmpty();

        assertThat(validator.validateClaims(null, "  ", null)).isEmpty();
    }

    @Test
    void tokens_already_in_the_original_are_not_flagged() {
        List<ValidationIssue> issues = validator.validate(
                "A",
                "Designed REST APIs with Redis caching.",
                "Designed REST APIs with Redis caching for backend services.",
                evidence("REST API design with Redis caching and backend services."));

        assertThat(issues).isEmpty();
    }

    @Test
    void null_and_blank_variance_never_throws() {
        assertThat(validator.validate(null, null, null, null)).isEmpty();
        assertThat(validator.validate("", "", "", List.of())).isEmpty();
        assertThat(validator.validate("A", null, "   ", null)).isEmpty();

        List<ValidationIssue> issues = validator.validate(
                "A", null, "Built services with Kubernetes.", List.of());
        assertThat(issues).extracting(ValidationIssue::text).contains("kubernetes");
    }

    @Test
    void issue_context_locates_the_token_in_the_tailored_text() {
        List<ValidationIssue> issues = validator.validate(
                "B",
                "Processed events.",
                "Processed events and delivered robustly with comprehensive enterprise hardening across multiple regions.",
                evidence("Event processing only."));

        ValidationIssue issue = issues.stream()
                .filter(i -> i.text().equals("enterprise"))
                .findFirst().orElseThrow();
        assertThat(issue.context()).contains("enterprise");
        assertThat(issue.context()).contains("comprehensive");
        assertThat(issue.context()).startsWith("…");
    }

    @Test
    void validation_target_defaults_null_evidence_to_empty() {
        DeterministicValidator.ValidationTarget nullEvidence =
                new DeterministicValidator.ValidationTarget("B", "original", "tailored", null);
        assertThat(nullEvidence.evidenceTexts()).isEmpty();

        DeterministicValidator.ValidationTarget withEvidence =
                new DeterministicValidator.ValidationTarget("B", "original", "tailored", List.of("e1"));
        assertThat(withEvidence.evidenceTexts()).containsExactly("e1");
    }

    @Test
    void snippet_uses_ellipsis_for_late_tokens_in_long_texts() {
        List<ValidationIssue> issues = validator.validate(
                "B",
                "plain original text and plain evidence",
                "x ".repeat(70) + "zoox" + " y".repeat(90),
                evidence("plain evidence only"));

        assertThat(issues).anySatisfy(i -> {
            assertThat(i.text()).isEqualTo("zoox");
            assertThat(i.context())
                    .startsWith("…")
                    .endsWith("…")
                    .contains("zoox");
        });
    }

    @Test
    void snippet_leaves_short_texts_untouched() {
        List<ValidationIssue> issues = validator.validate(
                "B",
                "original text",
                "we use zoox here",
                evidence("plain"));

        assertThat(issues).anySatisfy(i -> {
            assertThat(i.text()).isEqualTo("zoox");
            assertThat(i.context()).isEqualTo("we use zoox here").doesNotContain("…");
        });
    }

    // ------------------------------------------------------------------

    private static List<String> evidence(String... rows) {
        return List.of(rows);
    }
}