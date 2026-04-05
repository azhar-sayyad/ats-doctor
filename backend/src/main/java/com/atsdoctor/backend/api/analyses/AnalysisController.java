package com.atsdoctor.backend.api.analyses;

import com.atsdoctor.backend.api.PageResult;
import com.atsdoctor.backend.application.analysis.AnalysisService;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * analysis endpoints (07-api-contract §4, PRD §8.1). POST /analyses queues
 * immediately (state QUEUED); clients poll GET /analyses/{id} to follow
 * QUEUED → MATCHING → SCORING → READY/FAILED, then POST
 * /analyses/{id}/reanalyze to re-run a READY/FAILED analysis (TASK-058).
 */

@RestController
@RequestMapping("/api/v1/analyses")
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody @jakarta.validation.Valid CreateAnalysisRequest request) {
        return AnalysisResponse.of(
                analysisService.queue(request.jobId(), request.resumeVersionId())).toMap();
    }

    /** Paginated list, newest first (07-api-contract §4, FEAT-027). */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        org.springframework.data.domain.Page<com.atsdoctor.backend.infrastructure.persistence.Analysis> result =
                analysisService.list(page, size);
        return new PageResult<>(result.getContent().stream()
                        .map(a -> AnalysisResponse.of(a).toMap())
                        .toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.hasNext()).toMap();
    }

    @GetMapping("/{analysisId}")
    public Map<String, Object> byId(@PathVariable("analysisId") UUID analysisId) {
        return AnalysisResponse.of(analysisService.byId(analysisId)).toMap();
    }

    @PostMapping("/{analysisId}/reanalyze")
    public Map<String, Object> reanalyze(@PathVariable("analysisId") UUID analysisId) {
        return AnalysisResponse.of(analysisService.reanalyze(analysisId)).toMap();
    }

    /** POST /analyses body (07-api-contract §4). */
    public record CreateAnalysisRequest(
            @NotNull(message = "job_id is required") @JsonProperty("job_id") UUID jobId,
            @NotNull(message = "resume_version_id is required") @JsonProperty("resume_version_id") UUID resumeVersionId) {
    }
}
