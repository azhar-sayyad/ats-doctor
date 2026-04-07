package com.atsdoctor.backend;

import com.atsdoctor.backend.infrastructure.persistence.AnalysisRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full SPRINT-03 pipeline against a real PostgreSQL: resume upload + JD paste →
 * V4 migration → analysis QUEUED → async matching/scoring (stub AI, no ai_runs)
 * → READY with score/breakdown/matches/gaps, then reanalyze. The fixture (Jane
 * Doe resume vs canned Google JD) must match ≥80% of requirements. Skipped
 * automatically when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class AnalysisPipelineIntegrationTest extends PipelineIntegrationTestBase {

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
    private AnalysisRepository analysisRepository;

    @Test
    void analysis_pipeline_scores_the_job_against_the_resume() throws Exception {
        String resumeVersionId = uploadResumeAndAwaitReady();
        String jobId = createJobAndAwaitReady();

        MvcResult create = mvc.perform(post("/api/v1/analyses")
                        .contentType("application/json")
                        .content("{\"job_id\":\"" + jobId + "\",\"resume_version_id\":\"" + resumeVersionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("QUEUED"))
                .andReturn();
        String analysisId = mapper.readTree(create.getResponse().getContentAsString()).get("id").asText();

        JsonNode ready = awaitAnalysis(analysisId);

        assertThat(ready.get("state").asText()).isEqualTo("READY");
        int score = ready.get("score").asInt();
        assertThat(score).isBetween(0, 100);

        JsonNode breakdown = ready.get("score_breakdown");
        assertThat(breakdown).isNotNull();
        java.util.List<String> categories = new java.util.ArrayList<>();
        breakdown.fieldNames().forEachRemaining(categories::add);
        assertThat(categories)
                .containsExactlyInAnyOrder("skills", "keywords", "responsibilities", "experience", "seniority", "education", "total");
        assertThat(breakdown.get("total").asInt()).isBetween(0, 100);
        assertThat(breakdown.get("skills").get("score").asInt()).isBetween(0, 100);

        JsonNode matches = ready.get("matches");
        assertThat(matches.size()).isEqualTo(4);
        long matchedOrPartial = java.util.stream.StreamSupport.stream(matches.spliterator(), false)
                .filter(m -> !"unmatched".equals(m.get("status").asText()))
                .count();
        assertThat(matchedOrPartial * 100 / matches.size()).isGreaterThanOrEqualTo(80);

        assertThat(ready.get("gaps").size()).isPositive();
        assertThat(ready.get("generation").get("matching").get("mode").asText()).isEqualTo("exact");
        assertThat(ready.get("generation").get("scoring").get("formula").asText()).isEqualTo("hybrid-weights-v1");

        assertThat(analysisRepository.findById(UUID.fromString(analysisId))).isPresent();
        assertThat(analysisRepository.count()).isEqualTo(1);

        // Reanalyze: READY → QUEUED → READY again, same row.
        mvc.perform(post("/api/v1/analyses/" + analysisId + "/reanalyze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("QUEUED"));
        JsonNode rerun = awaitAnalysis(analysisId);
        assertThat(rerun.get("state").asText()).isEqualTo("READY");
        assertThat(rerun.get("score").asInt()).isBetween(0, 100);

        mvc.perform(get("/api/v1/analyses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(analysisId))
                .andExpect(jsonPath("$.items").isArray());

        mvc.perform(get("/api/v1/analyses/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Analysis not found"));

        assertThat(analysisRepository.count()).isEqualTo(1);
    }

    @Test
    void analysis_requires_ready_entities() throws Exception {
        UUID randomJob = UUID.randomUUID();
        UUID randomVersion = UUID.randomUUID();

        mvc.perform(post("/api/v1/analyses")
                        .contentType("application/json")
                        .content("{\"job_id\":\"" + randomJob + "\",\"resume_version_id\":\"" + randomVersion + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid analysis request"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("No job found")));
    }
}
