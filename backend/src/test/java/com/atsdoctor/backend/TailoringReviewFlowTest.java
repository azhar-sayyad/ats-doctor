package com.atsdoctor.backend;

import com.atsdoctor.backend.infrastructure.persistence.TailoredChange;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChangeRepository;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResumeRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPRINT-06 review + export round trip against a real PostgreSQL (FEAT-037,
 * TASK-074..082): full pipeline → per-change accept/reject/edit/regenerate →
 * approve gate (409 while pending / on invalid reports) → APPROVED → PDF/DOCX/
 * JSON export with the gate (409 until every change is resolved). Skipped
 * automatically when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class TailoringReviewFlowTest {

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
        registry.add("ats.doctor.storage.resume-dir", () -> "target/test-review-resumes");
        registry.add("ats.doctor.storage.jobs-dir", () -> "target/test-review-jobs");
        registry.add("ats.doctor.storage.exports-dir", () -> "target/test-review-exports");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TailoredResumeRepository tailoredResumeRepository;

    @Autowired
    private TailoredChangeRepository tailoredChangeRepository;

    private final ObjectMapper mapper = new ObjectMapper();

@Test
    void review_regenerate_approve_and_export_round_trip() throws Exception {
        JsonNode ready = tailorReadyAnalysis();
        String tailoredId = ready.get("id").asText();

        List<TailoredChange> changes =
                tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(UUID.fromString(tailoredId));
        assertThat(changes).hasSizeGreaterThanOrEqualTo(2);

        // The stub rewrites insert JD-keyword phrases that the grounded
        // validator flags (unsupported_descriptor) — approve and export are
        // 409 until the reviewer reverts them to grounded texts. Rejecting
        // removes a rewrite from the document entirely (and from validity).
        mvc.perform(post("/api/v1/tailored/" + tailoredId + "/approve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Tailoring not ready"));
        mvc.perform(get("/api/v1/tailored/" + tailoredId + "/export/pdf"))
                .andExpect(status().isConflict());

        // Reject the first rewrite — REJECTED rows never ship.
        TailoredChange rejected = changes.get(0);
        mvc.perform(post("/api/v1/tailored/" + tailoredId + "/changes/" + rejected.getId())
                        .contentType("application/json")
                        .content("{\"action\":\"reject\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // Regenerate the second — the stub reruns deterministically (still
        // flagged), so the reviewer re-edits it to the grounded original:
        // REGENERATED rows stay editable until the reviewer is satisfied.
        TailoredChange regen = changes.get(1);
        mvc.perform(post("/api/v1/tailored/" + tailoredId + "/changes/" + regen.getId())
                        .contentType("application/json")
                        .content("{\"action\":\"regenerate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGENERATED"))
                .andExpect(jsonPath("$.prompt_version").value("tailor-bullet-v1"));
        String grounded = regen.getOriginalText();
        mvc.perform(post("/api/v1/tailored/" + tailoredId + "/changes/" + regen.getId())
                        .contentType("application/json")
                        .content("{\"action\":\"edit\",\"new_text\":\"" + grounded + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EDITED"))
                .andExpect(jsonPath("$.tailored_text").value(grounded));

        // Revert every remaining rewrite to its grounded original.
        for (int i = 2; i < changes.size(); i++) {
            mvc.perform(post("/api/v1/tailored/" + tailoredId + "/changes/" + changes.get(i).getId())
                            .contentType("application/json")
                            .content("{\"action\":\"edit\",\"new_text\":\""
                                    + changes.get(i).getOriginalText() + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("EDITED"));
        }

        // Revalidation after each edit persists a report in NEEDS_REVIEW state.
        JsonNode byId = getTailored(tailoredId);
        assertThat(byId.get("state").asText()).isEqualTo("NEEDS_REVIEW");
        String stored = tailoredResumeRepository.findById(UUID.fromString(tailoredId)).orElseThrow().getValidation();
        assertThat(mapper.readTree(stored).path("valid").asBoolean()).isTrue();

        // Approve now → APPROVED; idempotent second call still 200.
        mvc.perform(post("/api/v1/tailored/" + tailoredId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));
        assertThat(getTailored(tailoredId).get("state").asText()).isEqualTo("APPROVED");
        mvc.perform(post("/api/v1/tailored/" + tailoredId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));

        // PDF export: %PDF magic, attachment disposition, pdf_path persisted.
        MvcResult pdf = mvc.perform(get("/api/v1/tailored/" + tailoredId + "/export/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"tailored-resume-" + tailoredId + ".pdf\""))
                .andReturn();
        byte[] pdfBytes = pdf.getResponse().getContentAsByteArray();
        assertThat(new String(pdfBytes, 0, Math.min(8, pdfBytes.length)))
                .startsWith("%PDF");
        assertThat(tailoredResumeRepository.findById(UUID.fromString(tailoredId)).orElseThrow().getPdfPath())
                .contains("target/test-review-exports");

        // DOCX export: ZIP magic, docx_path persisted.
        MvcResult docx = mvc.perform(get("/api/v1/tailored/" + tailoredId + "/export/docx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"tailored-resume-" + tailoredId + ".docx\""))
                .andReturn();
        byte[] docxBytes = docx.getResponse().getContentAsByteArray();
        assertThat(docxBytes).startsWith((byte) 'P', (byte) 'K');
        assertThat(tailoredResumeRepository.findById(UUID.fromString(tailoredId)).orElseThrow().getDocxPath())
                .contains("target/test-review-exports");

        // JSON export: GET /tailored shape, state APPROVED.
        mvc.perform(get("/api/v1/tailored/" + tailoredId + "/export/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"))
                .andExpect(jsonPath("$.score_after").isNumber());
    }

    @Test
    void review_actions_and_export_are_guarded() throws Exception {
        JsonNode ready = tailorReadyAnalysis();
        String tailoredId = ready.get("id").asText();

        TailoredChange change = tailoredChangeRepository
                .findByTailoredResumeIdOrderByCreatedAtAsc(UUID.fromString(tailoredId)).get(0);

        // Blank edit → 400.
        mvc.perform(post("/api/v1/tailored/" + tailoredId + "/changes/" + change.getId())
                        .contentType("application/json")
                        .content("{\"action\":\"edit\",\"new_text\":\"   \"}"))
                .andExpect(status().isBadRequest());

        // Unknown action → 400.
        mvc.perform(post("/api/v1/tailored/" + tailoredId + "/changes/" + change.getId())
                        .contentType("application/json")
                        .content("{\"action\":\"nuke\"}"))
                .andExpect(status().isBadRequest());

        // Change not belonging to the tailored resume → 404.
        mvc.perform(post("/api/v1/tailored/" + tailoredId + "/changes/" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"action\":\"reject\"}"))
                .andExpect(status().isNotFound());

        // Export gate before any review → 409.
        mvc.perform(get("/api/v1/tailored/" + tailoredId + "/export/docx"))
                .andExpect(status().isConflict());

        // Reject every rewrite: REJECTED rows are excluded from validation →
        // a fresh report is valid and the resume can be approved and exported
        // with the original, evidence-derived texts.
        for (TailoredChange row : tailoredChangeRepository
                .findByTailoredResumeIdOrderByCreatedAtAsc(UUID.fromString(tailoredId))) {
            mvc.perform(post("/api/v1/tailored/" + tailoredId + "/changes/" + row.getId())
                            .contentType("application/json")
                            .content("{\"action\":\"reject\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"));
        }
        mvc.perform(post("/api/v1/tailored/" + tailoredId + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.checked_changes").value(0));
        mvc.perform(post("/api/v1/tailored/" + tailoredId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));
        mvc.perform(get("/api/v1/tailored/" + tailoredId + "/export/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));

        // APPROVED is terminal: review actions and edits are 409.
        for (TailoredChange row : tailoredChangeRepository
                .findByTailoredResumeIdOrderByCreatedAtAsc(UUID.fromString(tailoredId))) {
            mvc.perform(post("/api/v1/tailored/" + tailoredId + "/changes/" + row.getId())
                            .contentType("application/json")
                            .content("{\"action\":\"accept\"}"))
                    .andExpect(status().isConflict());
        }
    }

    // --- pipeline seeding (mirrors TailoringPipelineIntegrationTest) ---

    private JsonNode tailorReadyAnalysis() throws Exception {
        String resumeVersionId = uploadResumeAndAwaitReady();
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
        assertThat(ready.get("state").asText()).isEqualTo("READY");
        return ready;
    }

    private JsonNode getTailored(String tailoredId) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/tailored/" + tailoredId))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode awaitTailored(String tailoredId) throws Exception {
        return await(deadline(), () -> getTailored(tailoredId));
    }

    private JsonNode awaitAnalysis(String analysisId) throws Exception {
        return await(deadline(), () -> {
            MvcResult result = mvc.perform(get("/api/v1/analyses/" + analysisId))
                    .andExpect(status().isOk())
                    .andReturn();
            return mapper.readTree(result.getResponse().getContentAsString());
        });
    }

    private interface Poll {
        JsonNode poll() throws Exception;
    }

    private static JsonNode await(long deadline, Poll poll) throws Exception {
        while (System.currentTimeMillis() < deadline) {
            JsonNode node = poll.poll();
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

    private static long deadline() {
        return System.currentTimeMillis() + 20_000;
    }

    private String uploadResumeAndAwaitReady() throws Exception {
        String resumeText = "Jane Doe\nSenior Software Engineer\njane.doe@example.com | San Francisco, CA\n"
                + "Senior Software Engineer with 5+ years building distributed systems.\n"
                + "SKILLS\nLanguages: Python, FastAPI, PostgreSQL, Redis\n"
                + "EXPERIENCE\nTech Corp — Senior Backend Engineer (2020-01 to present)\n"
                + "- Built FastAPI services processing 2M events/day.\n"
                + "- Led a team of 4 engineers delivering the fraud detection platform.\n"
                + "PROJECTS\nDistributed Queue System — MSc in Computer Science capstone.\n";
        MockMultipartFile file = new MockMultipartFile("file", "master-resume.txt",
                "text/plain", resumeText.getBytes());

        MvcResult upload = mvc.perform(multipart("/api/v1/resumes/upload").file(file))
                .andExpect(status().isOk())
                .andReturn();
        String versionId = mapper.readTree(upload.getResponse().getContentAsString()).get("id").asText();
        await(deadline(), () -> {
            MvcResult result = mvc.perform(get("/api/v1/resumes/" + versionId))
                    .andExpect(status().isOk())
                    .andReturn();
            return mapper.readTree(result.getResponse().getContentAsString());
        });
        return versionId;
    }

    private String createJobAndAwaitReady() throws Exception {
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
                .andReturn();
        String jobId = mapper.readTree(create.getResponse().getContentAsString()).get("id").asText();
        await(deadline(), () -> {
            MvcResult result = mvc.perform(get("/api/v1/jobs/" + jobId))
                    .andExpect(status().isOk())
                    .andReturn();
            return mapper.readTree(result.getResponse().getContentAsString());
        });
        return jobId;
    }
}