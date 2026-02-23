package com.atsdoctor.backend;

import com.atsdoctor.backend.infrastructure.parsing.RawTextResumeParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Heuristic resume parser: fabricated fallbacks are dropped, real data is kept. */
class RawTextResumeParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void header_contact_and_summary_are_extracted() throws Exception {
        String resume = """
                Jane Doe
                jane.doe@example.com | San Francisco, CA | linkedin.com/in/janedoe

                SUMMARY
                Senior engineer building distributed systems.

                SKILLS
                """;

        JsonNode parsed = parse(resume);

        assertThat(parsed.path("basics").path("name").asText()).isEqualTo("Jane Doe");
        assertThat(parsed.path("basics").path("email").asText()).isEqualTo("jane.doe@example.com");
        assertThat(parsed.path("basics").path("location").asText()).isEqualTo("San Francisco, CA");
        assertThat(parsed.path("summary").asText()).contains("Senior engineer");
    }

    @Test
    void skills_are_extracted_with_categories() throws Exception {
        String resume = """
                Jane Doe
                SUMMARY

                SKILLS
                Languages: Python, TypeScript
                Databases: PostgreSQL, Redis

                EXPERIENCE
                """;

        JsonNode parsed = parse(resume);

        assertThat(parsed.path("skills").size()).isEqualTo(4);
        assertThat(parsed.path("skills").toString()).contains("Python").contains("PostgreSQL");
    }

    @Test
    void skills_fall_back_to_empty_when_no_skills_section() throws Exception {
        JsonNode parsed = parse("Jane Doe\nSUMMARY\nSome summary text here.\n\nEXPERIENCE\n");

        assertThat(parsed.path("skills").isArray()).isTrue();
        assertThat(parsed.path("skills").size()).isEqualTo(0);
    }

    @Test
    void experience_company_line_creates_entry_with_bullets() throws Exception {
        String resume = """
                Jane Doe
                SUMMARY

                EXPERIENCE
                Tech Corp — Senior Backend Engineer (2020-01 to present)
                • Built the payments platform
                • Led a team of four

                EDUCATION
                """;

        JsonNode parsed = parse(resume);

        assertThat(parsed.path("experience").size()).isEqualTo(1);
        JsonNode exp = parsed.path("experience").get(0);
        assertThat(exp.path("company").asText()).isEqualTo("Tech Corp");
        assertThat(exp.path("title").asText()).isEqualTo("Senior Backend Engineer");
        assertThat(exp.path("start").asText()).isEqualTo("2020-01");
        assertThat(exp.path("end").asText()).isEqualTo("present");
        assertThat(exp.path("bullets").size()).isEqualTo(2);
    }

    @Test
    void orphan_bullets_do_not_fabricate_an_employer() throws Exception {
        JsonNode parsed = parse("Jane Doe\nSUMMARY\n\nEXPERIENCE\n• Built a thing without a company\n\nEDUCATION\n");

        assertThat(parsed.path("experience").size()).isEqualTo(0);
    }

    @Test
    void no_experience_section_returns_empty() throws Exception {
        JsonNode parsed = parse("Jane Doe\nSUMMARY\nSome summary.\n\nSKILLS\nLanguages: Java\n");

        assertThat(parsed.path("experience").size()).isEqualTo(0);
    }

    @Test
    void education_parses_years_degree_and_institution() throws Exception {
        String resume = """
                Jane Doe
                SUMMARY

                EXPERIENCE
                Tech Corp (2020 to present)
                • Built things

                EDUCATION
                B.E. Computer Science, XYZ University, 2018 - 2022
                """;

        JsonNode parsed = parse(resume);

        assertThat(parsed.path("education").size()).isEqualTo(1);
        JsonNode edu = parsed.path("education").get(0);
        assertThat(edu.path("institution").asText()).isEqualTo("XYZ University");
        assertThat(edu.path("degree").asText()).isEqualTo("B.E.");
        assertThat(edu.path("start").asText()).isEqualTo("2018");
        assertThat(edu.path("end").asText()).isEqualTo("2022");
    }

    @Test
    void location_ca_substring_does_not_false_positive() throws Exception {
        String resume = "Jane Doe\njane.doe@example.com | careers@example.com | remote\nSUMMARY\nSome summary.\n";

        JsonNode parsed = parse(resume);

        assertThat(parsed.path("basics").path("location").asText()).isEqualTo("remote");
    }

    @Test
    void blank_text_uses_canned_stub() throws Exception {
        JsonNode parsed = parse("   ");

        assertThat(parsed.path("_stub").asBoolean()).isTrue();
        assertThat(parsed.path("basics").path("name").asText()).isEqualTo("Candidate Name");
    }

    private static JsonNode parse(String rawText) throws Exception {
        return MAPPER.readTree(RawTextResumeParser.parse(rawText, "master-resume.pdf"));
    }
}
