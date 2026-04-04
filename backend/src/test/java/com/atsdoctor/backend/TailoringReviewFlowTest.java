package com.atsdoctor.backend;

import com.atsdoctor.backend.infrastructure.persistence.TailoredChange;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChangeRepository;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResumeRepository;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class TailoringReviewFlowTest extends PipelineIntegrationTestBase {

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
        registry.add("ats.doctor.storage.resume-dir", () -> "target/test-review-resumes");
        registry.add("ats.doctor.storage.jobs-dir", () -> "target/test-review-jobs");
        registry.add("ats.doctor.storage.exports-dir", () -> "target/test-review-exports");
    }

    @Autowired
    private TailoredResumeRepository tailoredResumeRepository;

    @Autowired
    private TailoredChangeRepository tailoredChangeRepository;

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
        JsonNode byId = getJson("/api/v1/tailored/" + tailoredId);
        assertThat(byId.get("state").asText()).isEqualTo("NEEDS_REVIEW");
        String stored = tailoredResumeRepository.findById(UUID.fromString(tailoredId)).orElseThrow().getValidation();
        assertThat(mapper.readTree(stored).path("valid").asBoolean()).isTrue();

        // Approve now → APPROVED; idempotent second call still 200.
        mvc.perform(post("/api/v1/tailored/" + tailoredId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));
        assertThat(getJson("/api/v1/tailored/" + tailoredId).get("state").asText()).isEqualTo("APPROVED");
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

        // LaTeX export: TEXT-mode template, A4 article class, .tex attachment.
        MvcResult latex = mvc.perform(get("/api/v1/tailored/" + tailoredId + "/export/latex"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/x-tex"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"tailored-resume-" + tailoredId + ".tex\""))
                .andReturn();
        String tex = latex.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(tex).contains("\\documentclass[a4paper,10pt]{article}")
                .contains("\\begin{document}")
                .contains("\\end{document}");

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

    @Test
    void edit_document_is_authoritative_for_exports() throws Exception {
        JsonNode ready = tailorReadyAnalysis();
        String tailoredId = ready.get("id").asText();

        // Build the merged document from the master structured data, then edit
        // the name and first company so the rendered export can prove the saved
        // document (not the pre-edit content) was used.
        JsonNode version = mapper.readTree(mvc.perform(
                        get("/api/v1/resumes/" + ready.get("resume_version_id").asText()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        ObjectNode document = (ObjectNode) mapper.readTree(version.get("structured_data").asText());
        String name = document.path("basics").path("name").asText("Candidate");
        String company = document.path("experience").path(0).path("company").asText("Acme");
        ((ObjectNode) document.get("basics")).put("name", name + " (edited) & Co #1");
        ((ObjectNode) document.get("experience").get(0)).put("company", company + " (edited) 100% _uptime_");

        mvc.perform(put("/api/v1/tailored/" + tailoredId + "/edit")
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(document)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.document.basics.name").value(name + " (edited) & Co #1"));

        // A full-document edit resolves every non-REJECTED change to EDITED; the
        // reviewer still grounds each row so the approve/export gate passes.
        for (TailoredChange row : tailoredChangeRepository
                .findByTailoredResumeIdOrderByCreatedAtAsc(UUID.fromString(tailoredId))) {
            if (!"REJECTED".equals(row.getStatus())) {
                mvc.perform(post("/api/v1/tailored/" + tailoredId + "/changes/" + row.getId())
                                .contentType("application/json")
                                .content("{\"action\":\"edit\",\"new_text\":\"" + row.getOriginalText() + "\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("EDITED"));
            }
        }
        mvc.perform(post("/api/v1/tailored/" + tailoredId + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
        mvc.perform(post("/api/v1/tailored/" + tailoredId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));

        mvc.perform(get("/api/v1/tailored/" + tailoredId + "/export/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));

        // The rendered HTML carries the edited name and company — the saved
        // document, not the original content, drove the export.
        String html = tailoredResumeRepository.findById(UUID.fromString(tailoredId))
                .orElseThrow().getHtml();
        assertThat(html).contains(name + " (edited)");
        assertThat(html).contains(company + " (edited)");

        // LaTeX export: A4 article class and every LaTeX special in the edited
        // document escaped (the document is authoritative, so the edits ship).
        MvcResult latex = mvc.perform(get("/api/v1/tailored/" + tailoredId + "/export/latex"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/x-tex"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"tailored-resume-" + tailoredId + ".tex\""))
                .andReturn();
        String tex = latex.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(tex).contains("\\documentclass[a4paper,10pt]{article}")
                .contains("\\begin{document}")
                .contains("\\end{document}")
                .contains("Jane Doe (edited) \\& Co \\#1")
                .contains("100\\% \\_uptime\\_");

        // JSON export exposes the authoritative saved document.
        mvc.perform(get("/api/v1/tailored/" + tailoredId + "/export/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.basics.name").value(name + " (edited) & Co #1"));
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
        awaitAnalysis(analysisId);

        MvcResult tailor = mvc.perform(post("/api/v1/analyses/" + analysisId + "/tailor"))
                .andExpect(status().isOk())
                .andReturn();
        String tailoredId = mapper.readTree(tailor.getResponse().getContentAsString()).get("id").asText();
        JsonNode ready = awaitTailored(tailoredId);
        assertThat(ready.get("state").asText()).isEqualTo("READY");
        return ready;
    }
}