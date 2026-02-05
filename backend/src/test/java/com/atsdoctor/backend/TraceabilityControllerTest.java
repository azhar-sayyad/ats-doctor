package com.atsdoctor.backend;

import com.atsdoctor.backend.api.tailor.TailoringController;
import com.atsdoctor.backend.api.tailor.TailoringExceptionHandler;
import com.atsdoctor.backend.application.tailoring.TailoringNotFoundException;
import com.atsdoctor.backend.application.validation.TraceabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TASK-072 — GET /tailored/{id}/changes/{change_id}/trace + ProblemDetail mapping. */
@WebMvcTest(value = TailoringController.class, properties = "ats.doctor.persistence.enabled=true")
@Import(TailoringExceptionHandler.class)
class TraceabilityControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TraceabilityService traceabilityService;

    @MockitoBean
    private com.atsdoctor.backend.application.tailoring.TailoringService tailoringService;

    @MockitoBean
    private com.atsdoctor.backend.application.validation.ValidationService validationService;

    @MockitoBean
    private com.atsdoctor.backend.application.tailoring.ChangeReviewService changeReviewService;

    @Test
    void trace_returns_the_evidence_chain() throws Exception {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("change_id", "c1");
        trace.put("original_text", "Built Docker-based deployment pipelines.");
        trace.put("tailored_text", "Built Docker-based deployment pipelines with Kubernetes.");
        trace.put("evidence", java.util.List.of(Map.of("id", "e1", "text", "Docker-based deployment.")));
        trace.put("section", Map.of("id", "exp_002", "company", "Acme Corp", "title", "Backend Engineer"));
        trace.put("bullet", Map.of("original_id", "blt_2", "original_text", "…", "tailored_text", "…"));
        trace.put("validation_issues", java.util.List.of());
        when(traceabilityService.trace(any(), any())).thenReturn(trace);

        mvc.perform(get("/api/v1/tailored/" + UUID.randomUUID() + "/changes/" + UUID.randomUUID() + "/trace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.change_id").value("c1"))
                .andExpect(jsonPath("$.evidence[0].text").value("Docker-based deployment."))
                .andExpect(jsonPath("$.section.company").value("Acme Corp"))
                .andExpect(jsonPath("$.bullet.original_id").value("blt_2"));
    }

    @Test
    void trace_maps_unknown_tailored_to_404() throws Exception {
        when(traceabilityService.trace(any(), any()))
                .thenThrow(new TailoringNotFoundException("No tailored resume found for id x"));

        mvc.perform(get("/api/v1/tailored/" + UUID.randomUUID() + "/changes/" + UUID.randomUUID() + "/trace"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Tailored resume not found"));
    }

    @Test
    void trace_maps_unknown_change_to_404() throws Exception {
        when(traceabilityService.trace(any(), any()))
                .thenThrow(new TailoringNotFoundException("No tailored change x found for tailored resume y"));

        mvc.perform(get("/api/v1/tailored/" + UUID.randomUUID() + "/changes/" + UUID.randomUUID() + "/trace"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No tailored change x found for tailored resume y"));
    }
}