package com.atsdoctor.backend;

import com.atsdoctor.backend.infrastructure.persistence.AiRun;
import com.atsdoctor.backend.infrastructure.persistence.AiRunRepository;
import com.atsdoctor.backend.infrastructure.persistence.ResumeEvidenceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full SPRINT-01 pipeline against a real PostgreSQL: upload → V2 migration →
 * async extraction/parsing (stub AI) → evidence persistence → READY → edit.
 * Skipped automatically when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ResumePipelineIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("ats")
            .withUsername("ats")
            .withPassword("ats");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("ats.doctor.persistence.enabled", () -> "true");
        registry.add("ats.doctor.storage.resume-dir", () -> "target/test-resumes");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ResumeEvidenceRepository evidenceRepository;

    @Autowired
    private AiRunRepository aiRunRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void upload_processes_to_ready_and_edit_updates_in_place() throws Exception {
        String resumeText = """
                Jane Doe
                Senior Backend Engineer
                jane.doe@example.com | San Francisco, CA

                Senior Software Engineer with 5+ years building distributed systems.

                SKILLS
                Python (5y), FastAPI (3y), PostgreSQL (4y)

                EXPERIENCE
                Tech Corp — Senior Backend Engineer (2020-01 to present)
                - Built FastAPI services processing 2M events/day.
                - Led a team of 4 engineers.

                PROJECTS
                Distributed Queue System — reduced latency by 40%.
                """;
        MockMultipartFile file = new MockMultipartFile("file", "master-resume.txt",
                "text/plain", resumeText.getBytes());

        MvcResult uploadResult = mvc.perform(multipart("/api/v1/resumes/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("UPLOADED"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn();
        JsonNode upload = mapper.readTree(uploadResult.getResponse().getContentAsString());
        String versionId = upload.get("id").asText();

        JsonNode ready = awaitReady(versionId);

        assertThat(ready.get("state").asText()).isEqualTo("READY");
        assertThat(ready.get("structured_data").asText()).contains("\"name\": \"Jane Doe\"");
        assertThat(ready.get("model_used").asText()).isEqualTo("stub");
        assertThat(ready.get("evidence_summary").get("total").asInt()).isPositive();
        assertThat(ready.get("evidence_summary").get("a").asInt()).isPositive();

        long evidenceRows = evidenceRepository.findByResumeVersionId(java.util.UUID.fromString(versionId)).size();
        assertThat(evidenceRows).isEqualTo(ready.get("evidence_summary").get("total").asInt());

        List<AiRun> runs = aiRunRepository.findByTaskOrderByCreatedAtDesc("resume_parser");
        assertThat(runs).isNotEmpty();
        assertThat(runs.get(0).getStatus()).isEqualTo("success");
        assertThat(runs.get(0).getInputHash()).isNotBlank();

        String edited = """
                {"basics":{"name":"Jane Doe","email":"jane@example.com"},
                 "summary":"Edited summary.",
                 "skills":[{"name":"Python","category":"Language","years":5}],
                 "experience":[{"company":"Tech Corp","title":"Engineer"}],
                 "projects":[],"education":[]}
                """;
        mvc.perform(put("/api/v1/resumes/" + versionId + "/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(edited))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("READY"))
                .andExpect(jsonPath("$.structured_data").value(
                        org.hamcrest.Matchers.containsString("Edited summary.")));

        mvc.perform(get("/api/v1/resumes/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.structured_data").value(
                        org.hamcrest.Matchers.containsString("Edited summary.")));

        // FEAT-044/TASK-083: the edited source version stays immutable (v1) —
        // the edit created a NEW row (v2), current points at it.
        mvc.perform(get("/api/v1/resumes/" + versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.structured_data").value(
                        org.hamcrest.Matchers.containsString("Jane Doe")));

        mvc.perform(get("/api/v1/resumes/" + java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resume version not found"));
    }

    private JsonNode awaitReady(String versionId) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mvc.perform(get("/api/v1/resumes/" + versionId))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode node = mapper.readTree(result.getResponse().getContentAsString());
            String state = node.get("state").asText();
            if ("READY".equals(state)) {
                return node;
            }
            if ("FAILED".equals(state)) {
                throw new AssertionError("Pipeline FAILED: " + node.get("error").asText());
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Pipeline did not reach READY within 20s");
    }
}