package com.atsdoctor.backend.api.jobs;

import com.atsdoctor.backend.application.job.JobService;
import com.atsdoctor.backend.infrastructure.parsing.TextExtractionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JD endpoints (07-api-contract §1, PRD §8.1). POST /jobs returns immediately
 * with the job at state CREATED; clients poll the job to follow
 * CREATED → EXTRACTING → PARSING → READY/FAILED (SPRINT-01 decision:
 * async + polling).
 */
@RestController
@RequestMapping("/api/v1/jobs")
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class JobController {

    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    public static final long MAX_TEXT_BYTES = 1024L * 1024;

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public Map<String, Object> create(@RequestParam(value = "file", required = false) MultipartFile file,
                                      @RequestParam(value = "text", required = false) String text) {
        boolean hasFile = file != null && !file.isEmpty();
        boolean hasText = text != null && !text.isBlank();
        if (hasFile == hasText) {
            throw badRequest("Provide exactly one of the 'file' or 'text' parts.");
        }
        if (hasFile) {
            String filename = file.getOriginalFilename();
            if (TextExtractionService.FileType.fromName(filename) == null) {
                throw badRequest("Unsupported file type '" + filename
                        + "' — only PDF, DOCX or TXT JDs are accepted.");
            }
            if (file.getSize() > MAX_FILE_BYTES) {
                throw badRequest("File exceeds the 10MB limit (" + file.getSize() + " bytes).");
            }
            try {
                return jobService.createFromFile(filename, file.getBytes()).toMap();
            } catch (IOException ex) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Could not read the uploaded file: " + ex.getMessage());
            }
        }
        if (text.length() > MAX_TEXT_BYTES) {
            throw badRequest("Pasted JD exceeds the 1MB limit (" + text.length() + " chars).");
        }
        return jobService.createFromText(text).toMap();
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return jobService.list().stream().map(JobResponse::toMap).toList();
    }

    @GetMapping("/{jobId}")
    public Map<String, Object> byId(@PathVariable("jobId") UUID jobId) {
        return jobService.byId(jobId).toMap();
    }

    @DeleteMapping("/{jobId}")
    public Map<String, Object> delete(@PathVariable("jobId") UUID jobId) {
        jobService.delete(jobId);
        return Map.of("status", "deleted");
    }

    private static ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}