package com.atsdoctor.backend;

import com.atsdoctor.backend.api.tailor.TailoringExceptionHandler;
import com.atsdoctor.backend.api.tailor.TailoringExportController;
import com.atsdoctor.backend.application.export.ExportService;
import com.atsdoctor.backend.application.tailoring.TailoringConflictException;
import com.atsdoctor.backend.application.tailoring.TailoringNotFoundException;
import com.atsdoctor.backend.application.tailoring.TailoringService;
import com.atsdoctor.backend.infrastructure.export.ResumeTemplateCatalog.UnknownResumeTemplateException;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResume;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TASK-082 — GET /tailored/{id}/export/{pdf|docx|json} routes, gates and ProblemDetail mapping. */
@WebMvcTest(value = TailoringExportController.class, properties = "ats.doctor.persistence.enabled=true")
@Import(TailoringExceptionHandler.class)
class TailoringExportControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ExportService exportService;

    @MockitoBean
    private TailoringService tailoringService;

    @Test
    void pdf_returns_the_rendered_artifact() throws Exception {
        when(exportService.pdf(any(), isNull())).thenReturn(
                new ExportService.ExportArtifact("application/pdf", "tailored-resume-x.pdf",
                        "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8)));

        mvc.perform(get("/api/v1/tailored/" + UUID.randomUUID() + "/export/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().string("%PDF-1.4 test"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"tailored-resume-x.pdf\""));
    }

    @Test
    void docx_returns_the_rendered_artifact() throws Exception {
        when(exportService.docx(any(), isNull())).thenReturn(
                new ExportService.ExportArtifact(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "tailored-resume-y.docx", new byte[]{(byte) 0x50, (byte) 0x4B, (byte) 0x03, (byte) 0x04}));

        mvc.perform(get("/api/v1/tailored/" + UUID.randomUUID() + "/export/docx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"tailored-resume-y.docx\""));
    }

    @Test
    void template_param_is_forwarded_to_the_service() throws Exception {
        when(exportService.pdf(any(), eq("modern_minimal"))).thenReturn(
                new ExportService.ExportArtifact("application/pdf", "tailored-resume-x.pdf",
                        "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8)));

        mvc.perform(get("/api/v1/tailored/" + UUID.randomUUID() + "/export/pdf")
                        .param("template", "modern_minimal"))
                .andExpect(status().isOk());
    }

    @Test
    void unknown_template_maps_to_400() throws Exception {
        doThrow(new UnknownResumeTemplateException("Unknown resume template 'nope'"))
                .when(exportService).pdf(any(), any());

        mvc.perform(get("/api/v1/tailored/" + UUID.randomUUID() + "/export/pdf")
                        .param("template", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Unknown resume template"));
    }

    @Test
    void json_reuses_the_tailored_response_shape() throws Exception {
        when(tailoringService.byId(any())).thenReturn(tailored("APPROVED", 68, 74));

        mvc.perform(get("/api/v1/tailored/" + UUID.randomUUID() + "/export/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"))
                .andExpect(jsonPath("$.score_before").value(68));
    }

    @Test
    void unknown_format_is_a_bad_request() throws Exception {
        mvc.perform(get("/api/v1/tailored/" + UUID.randomUUID() + "/export/rtf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid tailoring request"));
    }

    @Test
    void gate_conflicts_map_to_409() throws Exception {
        doThrow(new TailoringConflictException("has changes still pending review — cannot export"))
                .when(exportService).pdf(any(), any());

        mvc.perform(get("/api/v1/tailored/" + UUID.randomUUID() + "/export/pdf"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Tailoring not ready"));
    }

    @Test
    void unknown_tailored_maps_to_404() throws Exception {
        doThrow(new TailoringNotFoundException("No tailored resume found for id x"))
                .when(exportService).pdf(any(), any());

        mvc.perform(get("/api/v1/tailored/" + UUID.randomUUID() + "/export/pdf"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Tailored resume not found"));
    }

    private static TailoredResume tailored(String state, Integer scoreBefore, Integer scoreAfter) {
        TailoredResume tailored = new TailoredResume();
        com.atsdoctor.backend.infrastructure.persistence.Analysis analysis =
                new com.atsdoctor.backend.infrastructure.persistence.Analysis();
        analysis.setJob(new com.atsdoctor.backend.infrastructure.persistence.Job());
        analysis.setResumeVersion(new com.atsdoctor.backend.infrastructure.persistence.ResumeVersion());
        analysis.setState("READY");
        tailored.setAnalysis(analysis);
        tailored.setResumeVersion(analysis.getResumeVersion());
        tailored.setState(state);
        tailored.setScoreBefore(scoreBefore);
        tailored.setScoreAfter(scoreAfter);
        tailored.setContent("{\"summary\":null,\"experience\":[],\"order\":[\"exp_001\"]}");
        return tailored;
    }
}