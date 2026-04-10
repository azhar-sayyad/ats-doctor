package com.atsdoctor.backend.api.tailor;

import com.atsdoctor.backend.api.PageResult;
import com.atsdoctor.backend.application.tailoring.ChangeReviewService;
import com.atsdoctor.backend.application.tailoring.TailoringService;
import com.atsdoctor.backend.application.validation.TraceabilityService;
import com.atsdoctor.backend.application.validation.ValidationService;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResume;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * tailoring endpoints (07-api-contract §3/§4): POST /analyses/{id}/tailor
 * queues a tailoring run (state QUEUED; poll GET /tailored/{id} to follow
 * QUEUED → GENERATING → VALIDATING → READY), GET /tailored/{id} reads the
 * result with content/score_before/score_after (TASK-066), POST
 * /tailored/{id}/validate runs the defence-in-depth fact-grounding check over
 * the tailored changes and returns the typed validation JSON (FEAT-035,
 * TASK-071; 404 unknown / 409 state not VALIDATING or READY), and
 * GET /tailored/{id}/changes/{change_id}/trace resolves one change to its
 * evidence chain — the "why is this claim here?" API (FEAT-036, TASK-072) —
 * and GET /tailored/{id}/changes lists the changes read-only for the
 * traceability drawer (TASK-073). The review lifecycle (FEAT-037, SPRINT-06,
 * TASK-074/075/078): POST /tailored/{id}/changes/{change_id} with an
 * {@code action} body (accept|reject|edit|regenerate) transitions a PENDING
 * change and POST /tailored/{id}/approve approves once every change is
 * resolved and validation passed (§8.2).
 */

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class TailoringController {

    private final TailoringService tailoringService;
    private final ValidationService validationService;
    private final TraceabilityService traceabilityService;
    private final ChangeReviewService changeReviewService;

    public TailoringController(TailoringService tailoringService,
                               ValidationService validationService,
                               TraceabilityService traceabilityService,
                               ChangeReviewService changeReviewService) {
        this.tailoringService = tailoringService;
        this.validationService = validationService;
        this.traceabilityService = traceabilityService;
        this.changeReviewService = changeReviewService;
    }

    @PostMapping("/analyses/{analysisId}/tailor")
    public Map<String, Object> tailor(@PathVariable("analysisId") UUID analysisId) {
        return TailoredResponse.of(tailoringService.tailor(analysisId)).toMap();
    }

    /** Paginated list, newest first. */
    @GetMapping("/tailored")
    public Map<String, Object> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        org.springframework.data.domain.Page<TailoredResume> result = tailoringService.list(page, size);
        return new PageResult<>(result.getContent().stream()
                        .map(t -> TailoredResponse.of(t).toMap())
                        .toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.hasNext()).toMap();
    }

    @GetMapping("/tailored/{tailoredId}")
    public Map<String, Object> byId(@PathVariable("tailoredId") UUID tailoredId) {
        return TailoredResponse.of(tailoringService.byId(tailoredId)).toMap();
    }

    @PostMapping("/tailored/{tailoredId}/validate")
    public JsonNode validate(@PathVariable("tailoredId") UUID tailoredId) {
        return validationService.validate(tailoredId);
    }

    @GetMapping("/tailored/{tailoredId}/changes/{changeId}/trace")
    public Map<String, Object> trace(@PathVariable("tailoredId") UUID tailoredId,
                                     @PathVariable("changeId") UUID changeId) {
        return traceabilityService.trace(tailoredId, changeId);
    }

    @GetMapping("/tailored/{tailoredId}/changes")
    public List<Map<String, Object>> changes(@PathVariable("tailoredId") UUID tailoredId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var change : tailoringService.changes(tailoredId)) {
            out.add(ChangeItem.of(change).toMap());
        }
        return out;
    }

    /**
     * Full-document edit (tailored edit workspace): the body is the merged
     * tailored resume document JSON (basics, summary, skills, experience with
     * effective bullets, projects, education). Persists it on
     * {@code tailored_resumes.document}, moves READY → NEEDS_REVIEW, resolves
     * the per-change rows and re-runs fact-grounding validation so the
     * approve/export gate stays intact. 404 unknown; 409 not under review;
     * 400 blank or malformed JSON.
     */
    @PutMapping("/tailored/{tailoredId}/edit")
    public Map<String, Object> editDocument(@PathVariable("tailoredId") UUID tailoredId,
                                            @RequestBody String documentJson) {
        return TailoredResponse.of(changeReviewService.saveDocument(tailoredId, documentJson)).toMap();
    }

    /**
     * Persist the export template selection (CL-020): body {"template":
     * "ats_clean"|"modern_minimal"|...} — slug validated against the app-side
     * catalog (400 unknown), stored so exports default to it. 404 unknown
     * tailored resume.
     */
    @PutMapping("/tailored/{tailoredId}/template")
    public Map<String, Object> setTemplate(@PathVariable("tailoredId") UUID tailoredId,
                                           @RequestBody TemplateRequest body) {
        return TailoredResponse.of(tailoringService.setTemplate(tailoredId, body.template())).toMap();
    }

    /**
     * Review one change (§8.2): body {"action": "accept"|"reject"|"edit"|"regenerate",
     * "new_text"?: "..."} — only PENDING rows transition; edit requires a non-blank
     * replacement; edit/regenerate revalidate the resume in place. 404 when the
     * tailored resume or change is unknown, 400 on a malformed action, 409 when the
     * tailored resume is not under review or the change is already reviewed.
     */
    @PostMapping("/tailored/{tailoredId}/changes/{changeId}")
    public Map<String, Object> review(@PathVariable("tailoredId") UUID tailoredId,
                                      @PathVariable("changeId") UUID changeId,
                                      @RequestBody ReviewRequest body) {
        ChangeReviewService.ReviewAction action = ChangeReviewService.ReviewAction.of(body.action());
        return ChangeItem.of(changeReviewService.apply(tailoredId, changeId, action, body.newText())).toMap();
    }

    /**
     * Approve the tailored resume (§8.2): 200 {"status":"approved"} once every change
     * is resolved and the persisted validation report is valid; 409 otherwise;
     * 404 unknown. Idempotent for already-APPROVED rows.
     */
    @PostMapping("/tailored/{tailoredId}/approve")
    public Map<String, Object> approve(@PathVariable("tailoredId") UUID tailoredId) {
        changeReviewService.approve(tailoredId);
        return Map.of("status", "approved");
    }

    /** §8.2 review action body. */
    public record ReviewRequest(
            @com.fasterxml.jackson.annotation.JsonProperty("action") String action,
            @com.fasterxml.jackson.annotation.JsonProperty("new_text") String newText) {
    }

    /** CL-020 export-template selection body. */
    public record TemplateRequest(
            @com.fasterxml.jackson.annotation.JsonProperty("template") String template) {
    }
}