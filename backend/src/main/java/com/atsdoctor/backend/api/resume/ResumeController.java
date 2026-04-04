package com.atsdoctor.backend.api.resume;

import com.atsdoctor.backend.application.resume.ResumeService;
import com.atsdoctor.backend.infrastructure.parsing.TextExtractionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Resume endpoints (07-api-contract §1, PRD §8.1). POST /resumes/upload returns
 * immediately with the version at state UPLOADED; clients poll the version to
 * follow UPLOADED → EXTRACTING → PARSING → READY/FAILED (SPRINT-01 decision:
 * async + polling).
 */

@RestController
@RequestMapping("/api/v1/resumes")
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class ResumeController {

    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("No file provided — attach the resume as the 'file' part.");
        }
        String filename = file.getOriginalFilename();
        if (TextExtractionService.FileType.fromName(filename) == null) {
            throw badRequest("Unsupported file type '" + filename
                    + "' — only PDF, DOCX or TXT resumes are accepted.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw badRequest("File exceeds the 10MB limit (" + file.getSize() + " bytes).");
        }
        try {
            return resumeService.upload(filename, file.getBytes()).toMap();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read the uploaded file: " + ex.getMessage());
        }
    }

    @GetMapping("/current")
    public Map<String, Object> current() {
        ResumeVersionResponse current = resumeService.current();
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No master resume uploaded yet — POST /resumes/upload first.");
        }
        return current.toMap();
    }

    @GetMapping("/{versionId}")
    public Map<String, Object> byId(@PathVariable("versionId") UUID versionId) {
        return resumeService.byId(versionId).toMap();
    }

    @PutMapping("/{versionId}/edit")
    public Map<String, Object> edit(@PathVariable("versionId") UUID versionId,
                                    @RequestBody String structuredDataJson) {
        return resumeService.edit(versionId, structuredDataJson).toMap();
    }

    private static ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}