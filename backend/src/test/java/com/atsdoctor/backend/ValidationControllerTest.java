package com.atsdoctor.backend;

import com.atsdoctor.backend.api.tailor.TailoringController;
import com.atsdoctor.backend.api.tailor.TailoringExceptionHandler;
import com.atsdoctor.backend.application.tailoring.TailoringConflictException;
import com.atsdoctor.backend.application.tailoring.TailoringNotFoundException;
import com.atsdoctor.backend.application.validation.ValidationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TASK-071 — POST /tailored/{id}/validate: returns typed validation JSON; 404/409 mapping. */
@WebMvcTest(value = TailoringController.class, properties = "ats.doctor.persistence.enabled=true")
@Import(TailoringExceptionHandler.class)
class ValidationControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ValidationService validationService;

    @MockitoBean
    private com.atsdoctor.backend.application.tailoring.TailoringService tailoringService;

    @MockitoBean
    private com.atsdoctor.backend.application.validation.TraceabilityService traceabilityService;

    @MockitoBean
    private com.atsdoctor.backend.application.tailoring.ChangeReviewService changeReviewService;

    @Test
    void validate_returns_the_validation_report() throws Exception {
        JsonNode report = MAPPER.readTree("""
                {"valid": false, "checked_changes": 1, "validated_at": "2026-08-13T10:00:00Z", "issues": [
                  {"type": "unsupported_fact", "code": "unsupported_technology", "text": "kubernetes",
                   "suggestion": "drop", "context": "…", "change_id": "c1", "claim_category": "B"}
                ]}""");
        when(validationService.validate(any())).thenReturn(report);

        mvc.perform(post("/api/v1/tailored/" + UUID.randomUUID() + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.checked_changes").value(1))
                .andExpect(jsonPath("$.issues[0].code").value("unsupported_technology"))
                .andExpect(jsonPath("$.issues[0].claim_category").value("B"));
    }

    @Test
    void validate_maps_unknown_tailored_to_404() throws Exception {
        when(validationService.validate(any()))
                .thenThrow(new TailoringNotFoundException("No tailored resume found for id x"));

        mvc.perform(post("/api/v1/tailored/" + UUID.randomUUID() + "/validate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Tailored resume not found"));
    }

    @Test
    void validate_maps_non_ready_state_to_409() throws Exception {
        when(validationService.validate(any()))
                .thenThrow(new TailoringConflictException("Tailored resume x is not ready for validation (state=GENERATING)"));

        mvc.perform(post("/api/v1/tailored/" + UUID.randomUUID() + "/validate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Tailoring not ready"));
    }
}