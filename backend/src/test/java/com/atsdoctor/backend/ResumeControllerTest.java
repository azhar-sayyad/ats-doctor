package com.atsdoctor.backend;

import com.atsdoctor.backend.api.resume.ResumeController;
import com.atsdoctor.backend.api.resume.ResumeVersionResponse;
import com.atsdoctor.backend.application.resume.ResumeNotFoundException;
import com.atsdoctor.backend.application.resume.ResumeNotReadyException;
import com.atsdoctor.backend.application.resume.ResumeService;
import com.atsdoctor.backend.application.resume.ResumeValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TASK-026/027/038 — upload validation, GET/PUT endpoints, ProblemDetail mapping. */
@WebMvcTest(value = ResumeController.class, properties = "ats.doctor.persistence.enabled=true")
@Import(com.atsdoctor.backend.api.resume.ResumeExceptionHandler.class)
class ResumeControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ResumeService resumeService;

    @Test
    void upload_accepts_a_valid_pdf_and_returns_the_version() throws Exception {
        ResumeVersionResponse response = response();
        when(resumeService.upload(anyString(), any(byte[].class))).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf",
                "application/pdf", "%PDF-1.4 fake".getBytes());

        mvc.perform(multipart("/api/v1/resumes/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.state").value("UPLOADED"))
                .andExpect(jsonPath("$.source_filename").value("resume.pdf"))
                .andExpect(jsonPath("$.evidence_summary.total").value(0));
    }

    @Test
    void upload_rejects_unsupported_file_types() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.png",
                "image/png", "not a resume".getBytes());

        mvc.perform(multipart("/api/v1/resumes/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("PDF, DOCX or TXT")));
    }

    @Test
    void upload_rejects_oversized_files() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "huge.pdf",
                "application/pdf", new byte[(int) ResumeController.MAX_FILE_BYTES + 1]);

        mvc.perform(multipart("/api/v1/resumes/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("10MB")));
    }

    @Test
    void upload_rejects_empty_files() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        mvc.perform(multipart("/api/v1/resumes/upload").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void current_returns_404_when_no_resume_exists() throws Exception {
        when(resumeService.current()).thenReturn(null);

        mvc.perform(get("/api/v1/resumes/current"))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_by_id_maps_not_found_to_problem_detail() throws Exception {
        UUID id = UUID.randomUUID();
        when(resumeService.byId(id))
                .thenThrow(new ResumeNotFoundException("No resume version found for id " + id));

        mvc.perform(get("/api/v1/resumes/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resume version not found"))
                .andExpect(jsonPath("$.detail").value("No resume version found for id " + id));
    }

    @Test
    void edit_maps_validation_failures_to_400() throws Exception {
        UUID id = UUID.randomUUID();
        when(resumeService.edit(any(), anyString()))
                .thenThrow(new ResumeValidationException("Structured resume failed validation: basics.name"));

        mvc.perform(put("/api/v1/resumes/" + id + "/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"basics\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid structured resume (§4.2)"));
    }

    @Test
    void edit_maps_not_ready_to_409() throws Exception {
        UUID id = UUID.randomUUID();
        when(resumeService.edit(any(), anyString()))
                .thenThrow(new ResumeNotReadyException("Resume version is EXTRACTING"));

        mvc.perform(put("/api/v1/resumes/" + id + "/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Resume not ready"));
    }

    private static ResumeVersionResponse response() {
        UUID id = UUID.randomUUID();
        return new ResumeVersionResponse(
                id, UUID.randomUUID(), 1, "UPLOADED", null, null,
                "resume.pdf", null, null, null, null, null,
                Map.of("a", 0, "b", 0, "c", 0, "total", 0),
                Instant.now(), Instant.now());
    }
}