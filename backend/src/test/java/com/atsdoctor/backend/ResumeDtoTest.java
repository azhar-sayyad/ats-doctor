package com.atsdoctor.backend;

import com.atsdoctor.backend.api.resume.ResumeDto;
import com.atsdoctor.backend.application.resume.ResumeValidationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TASK-032 — structured resume validation against the §4.2 schema. */
class ResumeDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void parses_a_valid_structured_resume() {
        String json = """
                {
                  "metadata": { "id": "resume_001", "version": 1, "prompt_version": "resume-parser-v1", "temperature": 0.1 },
                  "basics": { "name": "Jane Doe", "email": "jane@example.com" },
                  "summary": "Senior Engineer.",
                  "skills": [ { "name": "Python", "category": "Language", "years": 5 } ],
                  "experience": [
                    { "id": "exp_001", "company": "Tech Corp", "title": "Engineer",
                      "bullets": [ { "id": "b1", "text": "Built services.", "technologies": ["Python"] } ] }
                  ],
                  "projects": [ { "name": "Queue", "outcomes": ["Reduced latency by 40%"] } ],
                  "education": [ { "institution": "Stanford", "degree": "MSc" } ]
                }
                """;

        ResumeDto dto = ResumeDto.parse(json, validator);

        assertThat(dto.basics().name()).isEqualTo("Jane Doe");
        assertThat(dto.skills()).hasSize(1);
        assertThat(dto.experience().get(0).bullets().get(0).text()).isEqualTo("Built services.");
    }

    @Test
    void rejects_missing_basics_name() {
        String json = """
                { "basics": { "email": "jane@example.com" }, "skills": [] }
                """;

        assertThatThrownBy(() -> ResumeDto.parse(json, validator))
                .isInstanceOf(ResumeValidationException.class)
                .hasMessageContaining("basics.name");
    }

    @Test
    void rejects_malformed_json() {
        assertThatThrownBy(() -> ResumeDto.parse("{ not json", validator))
                .isInstanceOf(ResumeValidationException.class)
                .hasMessageContaining("not valid JSON");
    }

    @Test
    void strips_markdown_code_fences() {
        String json = "```json\n{\"basics\": {\"name\": \"Jane\"}}\n```";

        ResumeDto dto = ResumeDto.parse(json, validator);

        assertThat(dto.basics().name()).isEqualTo("Jane");
    }

    @Test
    void ignores_unknown_provider_fields() {
        String json = """
                { "_stub": true, "task": "resume_parser",
                  "basics": { "name": "Jane" }, "extra": {"anything": 1} }
                """;

        ResumeDto dto = ResumeDto.parse(json, validator);

        assertThat(dto.basics().name()).isEqualTo("Jane");
    }

    @Test
    void round_trips_canonical_json() {
        ResumeDto dto = ResumeDto.parse("{\"basics\": {\"name\": \"Jane\"}}", validator);

        String json = ResumeDto.toJson(dto);

        assertThat(json).contains("\"basics\"").contains("Jane").doesNotContain("_stub");
    }
}