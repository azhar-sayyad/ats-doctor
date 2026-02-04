package com.atsdoctor.backend;

import com.atsdoctor.backend.api.jobs.JobDto;
import com.atsdoctor.backend.application.job.JobValidationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TASK-042 — structured JD validation against the §4.3 schema. */
class JobDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void parses_a_valid_structured_jd() {
        String json = """
                {
                  "metadata": { "id": "job_001", "source": "upload", "filename": "jd.md",
                                "prompt_version": "jd-parser-v1", "temperature": 0.1 },
                  "job": { "title": "Senior Backend Engineer", "company": "Google",
                           "location": "Mountain View, CA", "seniority": "Senior" },
                  "requirements": [
                    { "id": "req_001", "text": "5+ years of backend development experience",
                      "type": "experience", "importance": "high", "keywords": ["backend", "5+ years"] }
                  ],
                  "skills": { "required": ["Python", "FastAPI"], "preferred": ["Docker"], "nice_to_have": ["Kafka"] },
                  "responsibilities": ["Design and implement scalable REST APIs"],
                  "keywords": ["Python", "FastAPI", "REST"]
                }
                """;

        JobDto dto = JobDto.parse(json, validator);

        assertThat(dto.job().title()).isEqualTo("Senior Backend Engineer");
        assertThat(dto.requirements()).hasSize(1);
        assertThat(dto.skills().required()).containsExactly("Python", "FastAPI");
    }

    @Test
    void rejects_missing_job_title() {
        String json = """
                { "job": { "company": "Google" }, "requirements": [], "keywords": [] }
                """;

        assertThatThrownBy(() -> JobDto.parse(json, validator))
                .isInstanceOf(JobValidationException.class)
                .hasMessageContaining("job.title");
    }

    @Test
    void rejects_malformed_json() {
        assertThatThrownBy(() -> JobDto.parse("{ not json", validator))
                .isInstanceOf(JobValidationException.class)
                .hasMessageContaining("not valid JSON");
    }

    @Test
    void strips_markdown_code_fences() {
        String json = "```json\n{\"job\": {\"title\": \"Backend Engineer\"}}\n```";

        JobDto dto = JobDto.parse(json, validator);

        assertThat(dto.job().title()).isEqualTo("Backend Engineer");
    }

    @Test
    void ignores_unknown_provider_fields() {
        String json = """
                { "_stub": true, "task": "jd_parser",
                  "job": { "title": "Backend Engineer" }, "extra": {"anything": 1} }
                """;

        JobDto dto = JobDto.parse(json, validator);

        assertThat(dto.job().title()).isEqualTo("Backend Engineer");
    }

    @Test
    void round_trips_canonical_json() {
        JobDto dto = JobDto.parse("{\"job\": {\"title\": \"Backend Engineer\"}}", validator);

        String json = JobDto.toJson(dto);

        assertThat(json).contains("\"job\"").contains("Backend Engineer").doesNotContain("_stub");
    }
}