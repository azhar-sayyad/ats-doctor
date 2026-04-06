package com.atsdoctor.backend;

import com.atsdoctor.backend.api.tailor.TailoredResponse;
import com.atsdoctor.backend.api.tailor.TailoringController;
import com.atsdoctor.backend.api.tailor.TailoringExceptionHandler;
import com.atsdoctor.backend.application.tailoring.TailoringConflictException;
import com.atsdoctor.backend.application.tailoring.TailoringNotFoundException;
import com.atsdoctor.backend.application.tailoring.TailoringService;
import com.atsdoctor.backend.application.tailoring.TailoringValidationException;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResume;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TASK-066 — POST /analyses/{id}/tailor + GET /tailored/{id} and ProblemDetail mapping. */
@WebMvcTest(value = TailoringController.class, properties = "ats.doctor.persistence.enabled=true")
@Import(TailoringExceptionHandler.class)
class TailoringControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TailoringService tailoringService;

    @MockitoBean
    private com.atsdoctor.backend.application.validation.ValidationService validationService;

    @MockitoBean
    private com.atsdoctor.backend.application.validation.TraceabilityService traceabilityService;

    @MockitoBean
    private com.atsdoctor.backend.application.tailoring.ChangeReviewService changeReviewService;

    @Test
    void tailor_queues_a_run_for_an_analysis() throws Exception {
        when(tailoringService.tailor(any())).thenReturn(tailored("QUEUED", 68, null));

        mvc.perform(post("/api/v1/analyses/" + UUID.randomUUID() + "/tailor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("QUEUED"))
                .andExpect(jsonPath("$.score_before").value(68));
    }

    @Test
    void tailor_maps_conflict_to_409() throws Exception {
        when(tailoringService.tailor(any()))
                .thenThrow(new TailoringConflictException("Analysis x is not READY (state=MATCHING)"));

        mvc.perform(post("/api/v1/analyses/" + UUID.randomUUID() + "/tailor"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Tailoring not ready"));
    }

    @Test
    void tailor_maps_unknown_analysis_to_400() throws Exception {
        when(tailoringService.tailor(any()))
                .thenThrow(new TailoringValidationException("No analysis found for id x"));

        mvc.perform(post("/api/v1/analyses/" + UUID.randomUUID() + "/tailor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid tailoring request"));
    }

    @Test
    void by_id_returns_content_and_scores() throws Exception {
        when(tailoringService.byId(any()))
                .thenReturn(tailored("READY", 68, 71));

        mvc.perform(get("/api/v1/tailored/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("READY"))
                .andExpect(jsonPath("$.score_before").value(68))
                .andExpect(jsonPath("$.score_after").value(71))
                .andExpect(jsonPath("$.content").isNotEmpty());
    }

    @Test
    void by_id_maps_not_found_to_404() throws Exception {
        when(tailoringService.byId(any()))
                .thenThrow(new TailoringNotFoundException("No tailored resume found for id x"));

        mvc.perform(get("/api/v1/tailored/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Tailored resume not found"));
    }

    @Test
    void changes_returns_the_read_only_listing() throws Exception {
        com.atsdoctor.backend.infrastructure.persistence.TailoredChange change =
                new com.atsdoctor.backend.infrastructure.persistence.TailoredChange();
        org.springframework.test.util.ReflectionTestUtils.setField(change, "id", UUID.randomUUID());
        change.setOriginalText("Built Docker-based deployment pipelines.");
        change.setTailoredText("Built Docker-based deployment pipelines with Kubernetes.");
        change.setReason("Better alignment with JD keywords ('kubernetes').");
        change.setClaimCategory("B");
        change.setStatus("PENDING");
        when(tailoringService.changes(any())).thenReturn(java.util.List.of(change));

        mvc.perform(get("/api/v1/tailored/" + UUID.randomUUID() + "/changes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].claim_category").value("B"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].original_text").value("Built Docker-based deployment pipelines."));
    }

    @Test
    void review_applies_an_action_and_returns_the_updated_change() throws Exception {
        com.atsdoctor.backend.infrastructure.persistence.TailoredChange change =
                new com.atsdoctor.backend.infrastructure.persistence.TailoredChange();
        org.springframework.test.util.ReflectionTestUtils.setField(change, "id", UUID.randomUUID());
        change.setOriginalText("Built Docker-based deployment pipelines.");
        change.setTailoredText("Built Docker-based deployment pipelines with Kubernetes.");
        change.setReason("Better alignment with JD keywords ('kubernetes').");
        change.setClaimCategory("B");
        change.setStatus("ACCEPTED");
        when(changeReviewService.apply(any(), any(), any(), any())).thenReturn(change);

        mvc.perform(post("/api/v1/tailored/" + UUID.randomUUID() + "/changes/" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"action\":\"accept\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.claim_category").value("B"));
    }

    @Test
    void review_maps_unknown_action_to_400() throws Exception {
        mvc.perform(post("/api/v1/tailored/" + UUID.randomUUID() + "/changes/" + UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"action\":\"nuke\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid tailoring request"));
    }

    @Test
    void approve_returns_status_approved() throws Exception {
        mvc.perform(post("/api/v1/tailored/" + UUID.randomUUID() + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));
    }

    @Test
    void list_returns_paginated_envelope() throws Exception {
        when(tailoringService.list(0, 10))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        java.util.List.of(tailored("READY", 68, 74)),
                        org.springframework.data.domain.PageRequest.of(0, 10), 1));

        mvc.perform(get("/api/v1/tailored"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].state").value("READY"))
                .andExpect(jsonPath("$.items[0].score_before").value(68))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.has_more").value(false));
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
        if ("READY".equals(state)) {
            tailored.setContent("{\"summary\":null,\"experience\":[],\"order\":[\"exp_001\"]}");
        }
        return tailored;
    }
}