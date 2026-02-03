package com.atsdoctor.backend;

import com.atsdoctor.backend.api.analyses.AnalysisController;
import com.atsdoctor.backend.api.analyses.AnalysisExceptionHandler;
import com.atsdoctor.backend.application.analysis.AnalysisNotReadyException;
import com.atsdoctor.backend.application.analysis.AnalysisNotFoundException;
import com.atsdoctor.backend.application.analysis.AnalysisService;
import com.atsdoctor.backend.application.analysis.AnalysisValidationException;
import com.atsdoctor.backend.infrastructure.persistence.Analysis;
import com.atsdoctor.backend.infrastructure.persistence.Job;
import com.atsdoctor.backend.infrastructure.persistence.ResumeVersion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TASK-056/058 — POST/GET/reanalyze endpoints and ProblemDetail mapping. */
@WebMvcTest(value = AnalysisController.class, properties = "ats.doctor.persistence.enabled=true")
@Import(AnalysisExceptionHandler.class)
class AnalysisControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AnalysisService analysisService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void create_queues_an_analysis() throws Exception {
        when(analysisService.queue(any(), any())).thenReturn(analysis("QUEUED", null));

        mvc.perform(post("/api/v1/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"job_id\":\"" + UUID.randomUUID() + "\",\"resume_version_id\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("QUEUED"));
    }

    @Test
    void create_rejects_a_missing_job_id() throws Exception {
        mvc.perform(post("/api/v1/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resume_version_id\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_maps_validation_to_problem_detail() throws Exception {
        when(analysisService.queue(any(), any()))
                .thenThrow(new AnalysisValidationException("Job x is not READY (state=PARSING)"));

        mvc.perform(post("/api/v1/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"job_id\":\"" + UUID.randomUUID() + "\",\"resume_version_id\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid analysis request"));
    }

    @Test
    void list_returns_analyses() throws Exception {
        when(analysisService.list()).thenReturn(List.of(analysis("READY", 69)));

        mvc.perform(get("/api/v1/analyses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].state").value("READY"))
                .andExpect(jsonPath("$[0].score").value(69));
    }

    @Test
    void by_id_returns_score_breakdown_and_matches() throws Exception {
        when(analysisService.byId(any())).thenReturn(analysis("READY", 69));

        mvc.perform(get("/api/v1/analyses/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score_breakdown.skills.score").value(73))
                .andExpect(jsonPath("$.matches[0].status").value("matched"));
    }

    @Test
    void by_id_maps_not_found_to_problem_detail() throws Exception {
        UUID id = UUID.randomUUID();
        when(analysisService.byId(id)).thenThrow(new AnalysisNotFoundException("No analysis found for id " + id));

        mvc.perform(get("/api/v1/analyses/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Analysis not found"));
    }

    @Test
    void reanalyze_requeues_a_ready_analysis() throws Exception {
        when(analysisService.reanalyze(any())).thenReturn(analysis("QUEUED", null));

        mvc.perform(post("/api/v1/analyses/" + UUID.randomUUID() + "/reanalyze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("QUEUED"));
    }

    @Test
    void reanalyze_maps_conflict_to_problem_detail() throws Exception {
        when(analysisService.reanalyze(any()))
                .thenThrow(new AnalysisNotReadyException("Analysis cannot be reanalyzed in state MATCHING"));

        mvc.perform(post("/api/v1/analyses/" + UUID.randomUUID() + "/reanalyze"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Analysis not ready"));
    }

    private static Analysis analysis(String state, Integer score) {
        Analysis analysis = new Analysis();
        Job job = new Job();
        ResumeVersion version = new ResumeVersion();
        analysis.setJob(job);
        analysis.setResumeVersion(version);
        analysis.setState(state);
        analysis.setScore(score);
        if ("READY".equals(state)) {
            analysis.setScoreBreakdown("{\"total\":69,\"skills\":{\"score\":73,\"weight\":0.3,\"matched\":[],\"missing\":[]}}");
            analysis.setMatches("[{\"status\":\"matched\",\"requirement_text\":\"Python\"}]");
            analysis.setGaps("[]");
            analysis.setGeneration("{\"matching\":{\"mode\":\"exact\"}}");
        }
        return analysis;
    }
}
