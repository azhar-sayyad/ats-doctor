package com.atsdoctor.backend;

import com.atsdoctor.backend.api.jobs.JobController;
import com.atsdoctor.backend.api.jobs.JobResponse;
import com.atsdoctor.backend.application.job.JobNotFoundException;
import com.atsdoctor.backend.application.job.JobService;
import com.atsdoctor.backend.application.job.JobValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TASK-039/045 — POST file|text, GET/DELETE, validation and ProblemDetail mapping. */
@WebMvcTest(value = JobController.class, properties = "ats.doctor.persistence.enabled=true")
@Import(com.atsdoctor.backend.api.jobs.JobExceptionHandler.class)
class JobControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JobService jobService;

    @Test
    void create_accepts_a_pasted_text_jd() throws Exception {
        when(jobService.createFromText("Senior Backend Engineer\nPython"))
                .thenReturn(response("CREATED", "Senior Backend Engineer"));

        mvc.perform(post("/api/v1/jobs").param("text", "Senior Backend Engineer\nPython"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CREATED"))
                .andExpect(jsonPath("$.title").value("Senior Backend Engineer"));
    }

    @Test
    void create_accepts_a_file_jd() throws Exception {
        when(jobService.createFromFile(anyString(), any(byte[].class)))
                .thenReturn(response("CREATED", null));

        MockMultipartFile file = new MockMultipartFile("file", "jd.pdf",
                "application/pdf", "%PDF-1.4 fake".getBytes());

        mvc.perform(multipart("/api/v1/jobs").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CREATED"));
    }

    @Test
    void create_rejects_when_neither_file_nor_text_is_provided() throws Exception {
        mvc.perform(post("/api/v1/jobs"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("exactly one")));
    }

    @Test
    void create_rejects_when_both_file_and_text_are_provided() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "jd.pdf",
                "application/pdf", "%PDF-1.4 fake".getBytes());

        mvc.perform(multipart("/api/v1/jobs").file(file).param("text", "some JD"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_rejects_unsupported_file_types() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "jd.png",
                "image/png", "not a JD".getBytes());

        mvc.perform(multipart("/api/v1/jobs").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("PDF, DOCX or TXT")));
    }

    @Test
    void create_rejects_oversized_files() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "huge.pdf",
                "application/pdf", new byte[(int) JobController.MAX_FILE_BYTES + 1]);

        mvc.perform(multipart("/api/v1/jobs").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("10MB")));
    }

    @Test
    void create_rejects_oversized_pasted_text() throws Exception {
        String huge = "x".repeat((int) JobController.MAX_TEXT_BYTES + 1);

        mvc.perform(post("/api/v1/jobs").param("text", huge))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("1MB")));
    }

    @Test
    void list_returns_all_jobs() throws Exception {
        when(jobService.list()).thenReturn(List.of(response("READY", "Senior Backend Engineer")));

        mvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].state").value("READY"));
    }

    @Test
    void get_by_id_maps_not_found_to_problem_detail() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.byId(id)).thenThrow(new JobNotFoundException("No job found for id " + id));

        mvc.perform(get("/api/v1/jobs/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Job not found"))
                .andExpect(jsonPath("$.detail").value("No job found for id " + id));
    }

    @Test
    void delete_returns_deleted_status() throws Exception {
        mvc.perform(delete("/api/v1/jobs/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("deleted"));
    }

    private static JobResponse response(String state, String title) {
        UUID id = UUID.randomUUID();
        return new JobResponse(
                id, state, title, null, null, null, null, null, null,
                null, null, null, null, null,
                Map.of("total", 0, "high", 0L),
                Instant.now(), Instant.now());
    }
}