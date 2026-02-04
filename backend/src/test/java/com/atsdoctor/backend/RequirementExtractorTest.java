package com.atsdoctor.backend;

import com.atsdoctor.backend.infrastructure.parsing.RequirementExtractor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** TASK-043 — deterministic normalization of requirement_extraction output. */
class RequirementExtractorTest {

    private final RequirementExtractor extractor = new RequirementExtractor();

    @Test
    void extracts_from_a_bare_array() {
        String json = """
                [ { "text": "Experience with Python and FastAPI", "type": "skill",
                    "importance": "high", "keywords": ["Python", "FastAPI"] } ]
                """;

        List<RequirementExtractor.ExtractedRequirement> out = extractor.extract(json);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).type()).isEqualTo("skill");
        assertThat(out.get(0).importance()).isEqualTo("high");
        assertThat(out.get(0).keywords()).containsExactly("python", "fastapi");
    }

    @Test
    void extracts_from_an_object_with_requirements_key() {
        String json = """
                { "_stub": true, "requirements": [
                    { "text": "5+ years of backend experience", "type": "experience",
                      "importance": "high", "keywords": ["backend", "experience"] }
                ] }
                """;

        List<RequirementExtractor.ExtractedRequirement> out = extractor.extract(json);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).type()).isEqualTo("experience");
    }

    @Test
    void coalesces_unknown_type_and_importance() {
        String json = """
                [ { "text": "Familiarity with Kafka", "type": "weird_type",
                    "importance": "critical", "keywords": ["Kafka"] } ]
                """;

        List<RequirementExtractor.ExtractedRequirement> out = extractor.extract(json);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).type()).isEqualTo("skill");
        assertThat(out.get(0).importance()).isEqualTo("medium");
    }

    @Test
    void skips_blank_texts_and_cleans_keywords() {
        String json = """
                [ { "text": "   ", "type": "skill" },
                  { "text": "Docker", "type": "skill", "keywords": [" docker ", "Docker", ""] } ]
                """;

        List<RequirementExtractor.ExtractedRequirement> out = extractor.extract(json);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).keywords()).containsExactly("docker");
    }

    @Test
    void garbage_output_yields_no_rows() {
        assertThat(extractor.extract("this is not json {{{")).isEmpty();
        assertThat(extractor.extract(null)).isEmpty();
        assertThat(extractor.extract("{\"requirements\": \"not-an-array\"}")).isEmpty();
    }
}