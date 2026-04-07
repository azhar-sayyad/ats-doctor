package com.atsdoctor.backend;

import com.atsdoctor.backend.infrastructure.persistence.AiRun;
import com.atsdoctor.backend.infrastructure.persistence.AiRunRepository;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChange;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChangeRepository;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResumeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full SPRINT-04 pipeline against a real PostgreSQL: resume upload + JD paste →
 * V4 migration → analysis READY → POST /analyses/{id}/tailor → V5 migration →
 * QUEUED → async decision/tailoring (stub AI: deterministic grounded rewrites,
 * one {@code ai_runs} row per {@code resume_tailoring} call) → READY with
 * content JSON, tailored_changes rows and score_after; GET /tailored reads it
 * back. Skipped automatically when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class TailoringPipelineIntegrationTest extends PipelineIntegrationTestBase {

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
        registry.add("ats.doctor.storage.resume-dir", () -> "target/test-tailored-resumes");
        registry.add("ats.doctor.storage.jobs-dir", () -> "target/test-tailored-jobs");
    }

    @Autowired
    private TailoredResumeRepository tailoredResumeRepository;

    @Autowired
    private TailoredChangeRepository tailoredChangeRepository;

    @Autowired
    private AiRunRepository aiRunRepository;

    @Test
    void tailoring_pipeline_rewrites_selected_bullets_and_scores_the_result() throws Exception {
        JsonNode ready = tailorReadyAnalysis();
        String tailoredId = ready.get("id").asText();

        int scoreBefore = ready.get("score_before").asInt();
        assertThat(ready.get("score_after").asInt()).isBetween(0, 100);
        assertThat(ready.get("score_after").asInt()).isGreaterThanOrEqualTo(scoreBefore);

        JsonNode content = ready.get("content");
        assertThat(content).isNotNull();
        assertThat(content.get("experience").isArray()).isTrue();
        boolean anyTailored = false;
        for (JsonNode entry : content.get("experience")) {
            for (JsonNode bullet : entry.get("bullets")) {
                assertThat(bullet.get("tailored_text").asText())
                        .isNotEqualTo(bullet.get("original_text").asText());
                anyTailored = true;
            }
        }
        assertThat(anyTailored).as("at least one bullet must be rewritten").isTrue();

        // Every change row: PENDING, prompt_version, non-blank texts.
        assertThat(tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(UUID.fromString(tailoredId)))
                .isNotEmpty()
                .allSatisfy(change -> {
                    assertThat(change.getStatus()).isEqualTo("PENDING");
                    assertThat(change.getPromptVersion()).isEqualTo("tailor-bullet-v1");
                    assertThat(change.getOriginalText()).isNotBlank();
                    assertThat(change.getTailoredText()).isNotBlank();
                    assertThat(change.getClaimCategory()).isIn("A", "B", "C");
                });

        // Each rewrite produced an ai_runs row with task resume_tailoring.
        assertThat(aiRunRepository.findByTaskOrderByCreatedAtDesc("resume_tailoring")).isNotEmpty();

        // GET /tailored/{id} surfaces the same result (snake_case, content parsed).
        mvc.perform(get("/api/v1/tailored/" + tailoredId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("READY"))
                .andExpect(jsonPath("$.content.experience[0].bullets[0].tailored_text").isNotEmpty());

        // Duplicate run while one is active is rejected (409) — after READY a
        // new run is allowed, so verify the guard via a second immediate call
        // only while the first is still QUEUED/GENERATING is racy; instead
        // assert the unknown-analysis mapping.
        mvc.perform(post("/api/v1/analyses/" + UUID.randomUUID() + "/tailor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid tailoring request"));
        mvc.perform(get("/api/v1/tailored/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Tailored resume not found"));

        assertThat(tailoredResumeRepository.findById(UUID.fromString(tailoredId))).isPresent();
    }

    @Test
    void tailoring_rewrites_land_in_content_for_id_less_resumes() throws Exception {
        // Production LLM parser output never carries bullet ids; strip every id
        // from the Jane Doe fixture (PUT /resumes/{id}/edit mints a new version)
        // and run the whole pipeline — rewrites must still land in content.
        String uploaded = uploadResumeAndAwaitReady();
        JsonNode parsed = mapper.readTree(
                getJson("/api/v1/resumes/" + uploaded).get("structured_data").asText());
        JsonNode idLess = stripIds(parsed);
        MvcResult edited = mvc.perform(put("/api/v1/resumes/" + uploaded + "/edit")
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(idLess)))
                .andExpect(status().isOk())
                .andReturn();
        String resumeVersionId = mapper.readTree(edited.getResponse().getContentAsString()).get("id").asText();
        assertThat(hasAnyId(getJson("/api/v1/resumes/" + resumeVersionId).get("structured_data"))).isFalse();

        String jobId = createJobAndAwaitReady();
        MvcResult create = mvc.perform(post("/api/v1/analyses")
                        .contentType("application/json")
                        .content("{\"job_id\":\"" + jobId + "\",\"resume_version_id\":\"" + resumeVersionId + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String analysisId = mapper.readTree(create.getResponse().getContentAsString()).get("id").asText();
        awaitAnalysis(analysisId);

        MvcResult tailor = mvc.perform(post("/api/v1/analyses/" + analysisId + "/tailor"))
                .andExpect(status().isOk())
                .andReturn();
        String tailoredId = mapper.readTree(tailor.getResponse().getContentAsString()).get("id").asText();
        JsonNode ready = awaitTailored(tailoredId);

        JsonNode content = ready.get("content");
        if (content.has("summary") && !content.get("summary").isNull()) {
            assertThat(content.get("summary").isObject()).isTrue();
        }
        assertThat(content.get("experience").isArray()).isTrue();
        assertThat(content.get("experience").size()).isPositive();

        List<TailoredChange> changes =
                tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(UUID.fromString(tailoredId));
        Set<String> bulletOriginals = changes.stream()
                .map(TailoredChange::getOriginalText).collect(Collectors.toSet());
        Set<String> masterTexts = new HashSet<>();
        idLess.path("experience").forEach(e ->
                e.path("bullets").forEach(b -> masterTexts.add(b.path("text").asText())));

        int contentBullets = 0;
        for (JsonNode entry : content.get("experience")) {
            for (JsonNode bullet : entry.get("bullets")) {
                contentBullets++;
                String original = bullet.get("original_text").asText();
                assertThat(bullet.get("tailored_text").asText()).isNotEqualTo(original);
                assertThat(masterTexts).as("content original_text must come from the master resume").contains(original);
            }
        }
        assertThat(contentBullets).as("every bullet change must surface in content.experience")
                .isEqualTo((int) bulletOriginals.stream().filter(masterTexts::contains).count());
    }

    @Test
    void validate_and_trace_round_trip_grounds_every_change() throws Exception {
        JsonNode ready = tailorReadyAnalysis();
        String tailoredId = ready.get("id").asText();

        List<TailoredChange> changes =
                tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(UUID.fromString(tailoredId));
        assertThat(changes).isNotEmpty();

        // POST /tailored/{id}/validate — the VALIDATING/READY gate ran for real:
        // every change was checked, the report is persisted and re-readable.
        MvcResult validation = mvc.perform(post("/api/v1/tailored/" + tailoredId + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").isBoolean())
                .andExpect(jsonPath("$.checked_changes").value(changes.size()))
                .andExpect(jsonPath("$.validated_at").isNotEmpty())
                .andReturn();
        JsonNode report = mapper.readTree(validation.getResponse().getContentAsString());
        assertThat(report.path("issues").isArray()).isTrue();
        for (JsonNode issue : report.path("issues")) {
            assertThat(issue.path("type").asText())
                    .isIn("unsupported_fact", "unsupported_descriptor", "unsupported_claim");
            assertThat(issue.path("code").asText()).isNotBlank();
            assertThat(issue.path("suggestion").asText()).isNotBlank();
            assertThat(issue.path("change_id").asText()).isNotBlank();
        }

        // GET /tailored/{id}/changes is the read-only listing of PENDING rows.
        mvc.perform(get("/api/v1/tailored/" + tailoredId + "/changes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].claim_category").isString())
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].original_text").isNotEmpty())
                .andExpect(jsonPath("$[0].tailored_text").isNotEmpty());

        // Every change resolves to its "why is this claim here?" trace; bullet
        // changes carry an evidence chain + section context, summary rewrites none.
        boolean anyGrounding = false;
        for (TailoredChange change : changes) {
            MvcResult trace = mvc.perform(get(
                            "/api/v1/tailored/" + tailoredId + "/changes/" + change.getId() + "/trace"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.change_id").value(change.getId().toString()))
                    .andExpect(jsonPath("$.original_text").value(change.getOriginalText()))
                    .andExpect(jsonPath("$.tailored_text").value(change.getTailoredText()))
                    .andExpect(jsonPath("$.claim_category").value(change.getClaimCategory()))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.evidence").isArray())
                    .andExpect(jsonPath("$.validation_issues").isArray())
                    .andReturn();
            JsonNode traceBody = mapper.readTree(trace.getResponse().getContentAsString());
            if (traceBody.path("evidence").size() > 0) {
                anyGrounding = true;
                assertThat(traceBody.path("section").isObject()).isTrue();
                assertThat(traceBody.path("section").path("company").asText()).isEqualTo("Tech Corp");
                assertThat(traceBody.path("bullet").path("tailored_text").asText())
                        .isEqualTo(change.getTailoredText());
            }
            for (JsonNode issue : traceBody.path("validation_issues")) {
                assertThat(issue.path("type").asText())
                        .isIn("unsupported_fact", "unsupported_descriptor", "unsupported_claim");
            }
        }
        assertThat(anyGrounding).as("bullet changes must resolve to evidence rows").isTrue();

        // A change that does not belong to the tailored resume is 404.
        mvc.perform(get("/api/v1/tailored/" + tailoredId + "/changes/" + UUID.randomUUID() + "/trace"))
                .andExpect(status().isNotFound());
    }

    /** Deep copy without any "id" fields — the shape the LLM resume parser emits. */
    private JsonNode stripIds(JsonNode node) {
        if (node.isObject()) {
            ObjectNode out = mapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> field = it.next();
                if (!"id".equals(field.getKey())) {
                    out.set(field.getKey(), stripIds(field.getValue()));
                }
            }
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = mapper.createArrayNode();
            node.forEach(child -> out.add(stripIds(child)));
            return out;
        }
        return node.deepCopy();
    }

    private boolean hasAnyId(JsonNode node) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> field = it.next();
                if ("id".equals(field.getKey()) && !field.getValue().isNull()) {
                    return true;
                }
                if (hasAnyId(field.getValue())) {
                    return true;
                }
            }
            return false;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (hasAnyId(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Upload resume + JD, create an analysis, tailor it and await READY. */
    private JsonNode tailorReadyAnalysis() throws Exception {
        String resumeVersionId = uploadResumeAndAwaitReady();
        String jobId = createJobAndAwaitReady();

        MvcResult create = mvc.perform(post("/api/v1/analyses")
                        .contentType("application/json")
                        .content("{\"job_id\":\"" + jobId + "\",\"resume_version_id\":\"" + resumeVersionId + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String analysisId = mapper.readTree(create.getResponse().getContentAsString()).get("id").asText();
        JsonNode analysis = awaitAnalysis(analysisId);
        assertThat(analysis.get("state").asText()).isEqualTo("READY");
        int scoreBefore = analysis.get("score").asInt();

        MvcResult tailor = mvc.perform(post("/api/v1/analyses/" + analysisId + "/tailor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("QUEUED"))
                .andExpect(jsonPath("$.score_before").value(scoreBefore))
                .andReturn();
        String tailoredId = mapper.readTree(tailor.getResponse().getContentAsString()).get("id").asText();

        JsonNode ready = awaitTailored(tailoredId);
        assertThat(ready.get("state").asText()).isEqualTo("READY");
        assertThat(ready.get("error").isNull()).isTrue();
        return ready;
    }
}