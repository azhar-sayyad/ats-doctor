package com.atsdoctor.backend;

import com.atsdoctor.backend.infrastructure.persistence.AiRun;
import com.atsdoctor.backend.infrastructure.persistence.AiRunRepository;
import com.atsdoctor.backend.infrastructure.persistence.JobRequirementRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full SPRINT-02 pipeline against a real PostgreSQL: pasted JD + file JD →
 * V3 migration → async extraction/parsing (stub AI) → requirement rows → READY,
 * then ai_runs recording and list/get/delete. Skipped automatically when Docker
 * is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class JobPipelineIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg15")
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
        registry.add("ats.doctor.storage.jobs-dir", () -> "target/test-jobs");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JobRequirementRepository requirementRepository;

    @Autowired
    private AiRunRepository aiRunRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void pasted_jd_processes_to_ready_with_requirements_and_ai_runs() throws Exception {
        String jdText = """
                Senior Backend Engineer (Google, Mountain View, CA)

                At least 5 years of backend development experience.
                Experience with Python and FastAPI required.
                Strong knowledge of PostgreSQL and Redis.
                BSc in Computer Science or related field preferred.

                Responsibilities: design and implement scalable REST APIs,
                optimize database performance, collaborate with frontend teams.
                """;

        MvcResult create = mvc.perform(post("/api/v1/jobs").param("text", jdText))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CREATED"))
                .andReturn();
        String jobId = mapper.readTree(create.getResponse().getContentAsString()).get("id").asText();

        JsonNode ready = awaitReady(jobId);

        assertThat(ready.get("state").asText()).isEqualTo("READY");
        assertThat(ready.get("title").asText()).isEqualTo("Senior Backend Engineer");
        assertThat(ready.get("company").asText()).isEqualTo("Google");
        assertThat(ready.get("model_used").asText()).isEqualTo("stub");
        assertThat(ready.get("structured_data").asText()).contains("\"title\": \"Senior Backend Engineer\"");
        assertThat(ready.get("raw_text").asText()).contains("Python and FastAPI");

        int requirementCount = ready.get("requirement_summary").get("total").asInt();
        assertThat(requirementCount).isPositive();
        long rows = requirementRepository.countByJobId(UUID.fromString(jobId));
        assertThat(rows).isEqualTo(requirementCount);

        List<AiRun> jdRuns = aiRunRepository.findByTaskOrderByCreatedAtDesc("jd_parser");
        assertThat(jdRuns).isNotEmpty();
        assertThat(jdRuns.get(0).getStatus()).isEqualTo("success");
        assertThat(jdRuns.get(0).getInputHash()).isNotBlank();

        List<AiRun> reqRuns = aiRunRepository.findByTaskOrderByCreatedAtDesc("requirement_extraction");
        assertThat(reqRuns).isNotEmpty();
        assertThat(reqRuns.get(0).getStatus()).isEqualTo("success");

        mvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(jobId));

        mvc.perform(delete("/api/v1/jobs/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("deleted"));

        mvc.perform(get("/api/v1/jobs/" + jobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Job not found"));
    }

    @Test
    void file_jd_is_extracted_from_the_stored_file() throws Exception {
        String jdText = "Backend Developer\nRequired: Python, PostgreSQL, Redis, Docker.";
        MockMultipartFile file = new MockMultipartFile("file", "job.txt",
                "text/plain", jdText.getBytes());

        MvcResult create = mvc.perform(multipart("/api/v1/jobs").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source_filename").value("job.txt"))
                .andReturn();
        String jobId = mapper.readTree(create.getResponse().getContentAsString()).get("id").asText();

        JsonNode ready = awaitReady(jobId);

        assertThat(ready.get("state").asText()).isEqualTo("READY");
        assertThat(ready.get("raw_text").asText()).contains("Backend Developer");
    }

    private JsonNode awaitReady(String jobId) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mvc.perform(get("/api/v1/jobs/" + jobId))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode node = mapper.readTree(result.getResponse().getContentAsString());
            String state = node.get("state").asText();
            if ("READY".equals(state)) {
                return node;
            }
            if ("FAILED".equals(state)) {
                throw new AssertionError("JD pipeline FAILED: " + node.get("error").asText());
            }
            Thread.sleep(200);
        }
        throw new AssertionError("JD pipeline did not reach READY within 20s");
    }
}