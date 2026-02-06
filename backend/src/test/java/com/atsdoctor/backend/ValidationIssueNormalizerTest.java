package com.atsdoctor.backend;

import com.atsdoctor.backend.infrastructure.validation.ValidationIssue;
import com.atsdoctor.backend.infrastructure.validation.ValidationIssueNormalizer;
import com.atsdoctor.backend.infrastructure.validation.ValidationIssueType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** TASK-069 — ValidationIssueNormalizer: tolerant parsing, canonical codes/types, dedupe. */
class ValidationIssueNormalizerTest {

    @Test
    void parses_fenced_json_object() {
        List<ValidationIssue> issues = ValidationIssueNormalizer.parse("""
                ```json
                {"is_valid": false, "issues": [
                  {"type": "inflated_metric", "text": "10M", "original_evidence": "2M", "suggestion": "revert"}
                ]}
                ```""");

        assertThat(issues).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo("inflated_metric");
            assertThat(i.type()).isEqualTo(ValidationIssueType.UNSUPPORTED_FACT);
            assertThat(i.text()).isEqualTo("10M");
            assertThat(i.context()).isEqualTo("2M");
            assertThat(i.suggestion()).isEqualTo("revert");
        });
    }

    @Test
    void normalizes_code_casing_and_whitespace() {
        List<ValidationIssue> issues = ValidationIssueNormalizer.parse("""
                {"issues": [{"type": "Unsupported Technology", "text": "Kubernetes", "suggestion": ""}]}""");

        assertThat(issues).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo("unsupported_technology");
            assertThat(i.type()).isEqualTo(ValidationIssueType.UNSUPPORTED_FACT);
        });
    }

    @Test
    void maps_claim_and_unknown_codes() {
        List<ValidationIssue> issues = ValidationIssueNormalizer.parse("""
                {"issues": [
                  {"type": "unsupported_achievement", "text": "Led 50 people", "suggestion": ""},
                  {"type": "something_else", "text": "mystery", "suggestion": ""},
                  {"text": "no type at all", "suggestion": ""}
                ]}""");

        assertThat(issues)
                .filteredOn(i -> i.text().equals("Led 50 people"))
                .singleElement()
                .satisfies(i -> assertThat(i.type()).isEqualTo(ValidationIssueType.UNSUPPORTED_CLAIM));
        assertThat(issues)
                .filteredOn(i -> i.text().equals("mystery"))
                .singleElement()
                .satisfies(i -> assertThat(i.type()).isEqualTo(ValidationIssueType.UNSUPPORTED_DESCRIPTOR));
        assertThat(issues)
                .filteredOn(i -> i.text().equals("no type at all"))
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.type()).isEqualTo(ValidationIssueType.UNSUPPORTED_DESCRIPTOR);
                    assertThat(i.code()).isNull();
                });
    }

    @Test
    void context_falls_back_to_the_offending_token() {
        List<ValidationIssue> issues = ValidationIssueNormalizer.parse("""
                {"issues": [{"type": "new_company", "text": "Google", "suggestion": ""}]}""");

        assertThat(issues).singleElement()
                .satisfies(i -> assertThat(i.context()).isEqualTo("Google"));
    }

    @Test
    void drops_issues_without_text_and_non_object_entries() {
        List<ValidationIssue> issues = ValidationIssueNormalizer.parse("""
                {"issues": [
                  {"type": "unsupported_technology", "text": ""},
                  {"type": "inflated_metric", "text": "   "},
                  "string entry",
                  42,
                  {"type": "new_title", "text": "CTO", "suggestion": "remove"}
                ]}""");

        assertThat(issues).singleElement().satisfies(i -> assertThat(i.text()).isEqualTo("CTO"));
    }

    @Test
    void tolerates_malformed_and_blank_outputs() {
        assertThat(ValidationIssueNormalizer.parse("not json at all")).isEmpty();
        assertThat(ValidationIssueNormalizer.parse("")).isEmpty();
        assertThat(ValidationIssueNormalizer.parse(null)).isEmpty();
        assertThat(ValidationIssueNormalizer.parse("{\"issues\": []}")).isEmpty();
        assertThat(ValidationIssueNormalizer.parse("{}")).isEmpty();
    }

    @Test
    void dedupes_by_offending_token_case_insensitively_keeping_first() {
        List<ValidationIssue> source = List.of(
                new ValidationIssue(ValidationIssueType.UNSUPPORTED_FACT, "inflated_metric", "10M", "a", "c1"),
                new ValidationIssue(ValidationIssueType.UNSUPPORTED_FACT, "inflated_metric", "10m", "b", "c2"),
                new ValidationIssue(ValidationIssueType.UNSUPPORTED_FACT, "new_company", "10M", "c", "c3"));

        List<ValidationIssue> deduped = ValidationIssueNormalizer.dedupe(source);

        assertThat(deduped).hasSize(1);
        assertThat(deduped.get(0).suggestion()).isEqualTo("a");
        assertThat(deduped.get(0).code()).isEqualTo("inflated_metric");
        assertThat(ValidationIssueNormalizer.dedupe(List.of())).isEmpty();
        assertThat(ValidationIssueNormalizer.dedupe(null)).isEmpty();
    }

    @Test
    void normalize_code_null_returns_empty() {
        assertThat(ValidationIssueNormalizer.normalizeCode(null)).isEmpty();
        assertThat(ValidationIssueNormalizer.normalizeCode("Unsupported Technology"))
                .isEqualTo("unsupported_technology");
    }

    @Test
    void parse_fence_only_output_returns_no_issues() {
        assertThat(ValidationIssueNormalizer.parse("```")).isEmpty();
    }
}