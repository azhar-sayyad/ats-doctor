package com.atsdoctor.backend;

import com.atsdoctor.backend.infrastructure.parsing.RawTextJobParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** TASK-039 — pasted-JD heuristic parser: title/company/location stay persist-safe. */
class RawTextJobParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void short_company_line_is_kept() throws Exception {
        String jd = "02 / ROLE REQUIREMENTS\nAcme Solutions Inc.\nPython, FastAPI";

        JsonNode job = parseJob(jd);

        assertThat(job.path("job").path("company").asText()).isEqualTo("Acme Solutions Inc.");
    }

    @Test
    void long_prose_line_is_not_used_as_company() throws Exception {
        String paragraph = ("This is a long paragraph describing how the engineering team uses cloud "
                + "technologies and data solutions to build distributed products at scale, spanning "
                + "multiple regions and teams across the organization, together with our partners. ")
                .repeat(2);
        String jd = "02 / ROLE REQUIREMENTS\n" + paragraph + "\nPython, FastAPI";

        JsonNode job = parseJob(jd);

        String company = job.path("job").path("company").asText();
        assertThat(company).isNotEqualTo(paragraph.trim());
        assertThat(company.length()).isLessThanOrEqualTo(120);
    }

    @Test
    void parsed_values_never_exceed_column_widths() throws Exception {
        String jd = "02 / ROLE REQUIREMENTS\n" + ("technologies ".repeat(60)) + "\nPython, FastAPI";

        JsonNode job = parseJob(jd);

        assertThat(job.path("job").path("title").asText().length()).isLessThanOrEqualTo(255);
        assertThat(job.path("job").path("company").asText().length()).isLessThanOrEqualTo(255);
        assertThat(job.path("job").path("location").asText().length()).isLessThanOrEqualTo(255);
        assertThat(job.path("job").path("seniority").asText().length()).isLessThanOrEqualTo(64);
    }

    @Test
    void blank_jd_falls_back_to_canned_job() throws Exception {
        JsonNode job = parseJob("   ");

        assertThat(job.path("job").path("title").asText()).isNotBlank();
    }

    @Test
    void requirements_extract_bullet_lines() throws Exception {
        String out = RawTextJobParser.parseRequirements("""
                • 5+ years of experience with Python
                - Strong knowledge of Kafka
                this is not json at all
                """);

        assertThat(out).contains("python").contains("kafka");
    }

    @Test
    void requirements_reads_existing_json_and_normalizes_importance() throws Exception {
        String out = RawTextJobParser.parseRequirements("""
                {"requirements": [
                  {"id":"r1","text":"Python experience","type":"skill","importance":"sometimes","keywords":["Python"]}
                ]}
                """);

        assertThat(out).contains("python");
    }

    @Test
    void requirements_without_array_falls_through_to_heuristics() throws Exception {
        String out = RawTextJobParser.parseRequirements("{\"foo\": 1}");

        assertThat(MAPPER.readTree(out).size()).isEqualTo(0);
    }

    @Test
    void jd_without_tech_keywords_gets_empty_keywords() throws Exception {
        JsonNode job = parseJob("IS Analyst (Application Support/SRE)\nAcme Corp\nWe build nice things");

        assertThat(job.path("keywords").size()).isEqualTo(0);
        assertThat(job.path("skills").path("required").size()).isEqualTo(0);
    }

    @Test
    void title_is_not_fabricated_when_no_role_line_matches() throws Exception {
        JsonNode job = parseJob("02 / ROLE REQUIREMENTS\n• 3+ years experience with application support and SRE");

        assertThat(job.path("job").path("title").asText()).isEqualTo("Untitled Role");
        assertThat(job.path("job").path("seniority").asText()).isEmpty();
    }

    @Test
    void analyst_sre_title_is_extracted() throws Exception {
        JsonNode job = parseJob("02 / ROLE REQUIREMENTS\nIS Analyst (Application Support/SRE)\nOptum");

        assertThat(job.path("job").path("title").asText()).isEqualTo("IS Analyst");
    }

    @Test
    void location_ca_substring_does_not_false_positive() throws Exception {
        JsonNode job = parseJob("IS Analyst\nAcme Corp\nWe provide care and support for applications");

        assertThat(job.path("job").path("location").asText()).isEmpty();
    }

    @Test
    void remote_location_is_detected() throws Exception {
        JsonNode job = parseJob("IS Analyst\nAcme Corp\nRemote friendly, hybrid options");

        assertThat(job.path("job").path("location").asText()).isEqualTo("Remote");
    }

    @Test
    void known_company_is_detected() throws Exception {
        JsonNode job = parseJob("IS Analyst (Application Support/SRE)\nOptum is hiring");

        assertThat(job.path("job").path("company").asText()).isEqualTo("Optum");
    }

    @Test
    void unknown_company_is_not_fabricated() throws Exception {
        JsonNode job = parseJob("IS Analyst (Application Support/SRE)\nWe support production systems");

        assertThat(job.path("job").path("company").asText()).isEmpty();
    }

    @Test
    void requirements_are_typed_and_prioritized_from_content() throws Exception {
        JsonNode job = parseJob("""
                IS Analyst (Application Support/SRE)
                • 5+ years of experience with application support required
                • BSc in Computer Science preferred
                • Strong knowledge of Linux
                """);

        ArrayNode reqs = (ArrayNode) job.path("requirements");
        assertThat(reqs.size()).isEqualTo(3);
        assertThat(reqs.get(0).path("type").asText()).isEqualTo("experience");
        assertThat(reqs.get(0).path("importance").asText()).isEqualTo("high");
        assertThat(reqs.get(1).path("type").asText()).isEqualTo("education");
        assertThat(reqs.get(1).path("importance").asText()).isEqualTo("medium");
        assertThat(reqs.get(2).path("type").asText()).isEqualTo("skill");
    }

    @Test
    void jd_bullets_are_captured_as_responsibilities() throws Exception {
        JsonNode job = parseJob("Senior Software Engineer\nAcme Corp\n• Built the payments platform\n- Led a team of four");

        assertThat(job.path("responsibilities").toString()).contains("payments platform").contains("Led a team");
    }

    private static JsonNode parseJob(String rawText) throws Exception {
        return MAPPER.readTree(RawTextJobParser.parseJob(rawText));
    }
}
