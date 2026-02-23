package com.atsdoctor.backend.application.validation;

import com.atsdoctor.backend.application.tailoring.TailoringConflictException;
import com.atsdoctor.backend.application.tailoring.TailoringNotFoundException;
import com.atsdoctor.backend.domain.states.StateMachines;
import com.atsdoctor.backend.domain.states.TailoringState;
import com.atsdoctor.backend.infrastructure.persistence.ResumeEvidence;
import com.atsdoctor.backend.infrastructure.persistence.ResumeEvidenceRepository;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChange;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChangeRepository;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResume;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResumeRepository;
import com.atsdoctor.backend.infrastructure.validation.ValidationIssue;
import com.atsdoctor.backend.infrastructure.validation.ValidationRules;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Fact-grounding validation orchestration (FEAT-035, TASK-071): runs the
 * {@link ValidationRules} engine against every {@code tailored_changes} row of
 * a tailored resume, persists the merged report on {@code tailored_resumes}
 * ({@code validation} JSONB, PRD §7.8 precedent) and returns it as the
 * {@code POST /tailored/{id}/validate} payload.
 *
 * <p>State guard: only {@code VALIDATING}, {@code READY} and {@code NEEDS_REVIEW}
 * resumes validate — QUEUED/GENERATING are mid-run and APPROVED is past the
 * review gate (404 unknown; 409 otherwise). A resume stuck in {@code VALIDATING}
 * (e.g. crashed pipeline run) completes VALIDATING → READY on validation; an
 * already-READY resume is revalidated in place (state unchanged) — revalidation
 * allowed. NEEDS_REVIEW admits revalidation after an EDITED/REGENERATED change
 * (FEAT-037, TASK-075).
 *
 * <p>Every change is grounded against the FULL evidence set of the master
 * resume version (the same corpus the tailoring pipeline uses to pick its
 * keywords) — not just the change's linked {@code resume_evidence} row. This
 * keeps claims grounded in a skills row or another bullet from being flagged,
 * while still catching tokens the resume supports nowhere.
 */
@Service
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class ValidationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TailoredResumeRepository tailoredResumeRepository;
    private final TailoredChangeRepository tailoredChangeRepository;
    private final ResumeEvidenceRepository resumeEvidenceRepository;
    private final ValidationRules validationRules;

    public ValidationService(TailoredResumeRepository tailoredResumeRepository,
                             TailoredChangeRepository tailoredChangeRepository,
                             ResumeEvidenceRepository resumeEvidenceRepository,
                             ValidationRules validationRules) {
        this.tailoredResumeRepository = tailoredResumeRepository;
        this.tailoredChangeRepository = tailoredChangeRepository;
        this.resumeEvidenceRepository = resumeEvidenceRepository;
        this.validationRules = validationRules;
    }

    /** Run the rule engine over all changes; persist and return the report JSON. */
    @Transactional
    public JsonNode validate(UUID tailoredResumeId) {
        TailoredResume tailored = tailoredResumeRepository.findById(tailoredResumeId)
                .orElseThrow(() -> new TailoringNotFoundException("No tailored resume found for id " + tailoredResumeId));
        boolean acceptedState = "VALIDATING".equals(tailored.getState())
                || "READY".equals(tailored.getState())
                || "NEEDS_REVIEW".equals(tailored.getState());
        boolean stuckInValidating = "VALIDATING".equals(tailored.getState());
        if (!acceptedState) {
            throw new TailoringConflictException(
                    "Tailored resume " + tailoredResumeId + " is not ready for validation (state=" + tailored.getState() + ")");
        }

        List<String> evidenceTexts = evidenceTextsOf(tailored.getResumeVersion().getId());
        List<TailoredChange> changes =
                tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(tailoredResumeId);

        List<Map<String, Object>> issues = new ArrayList<>();
        boolean valid = true;
        int checked = 0;
        for (TailoredChange change : changes) {
            // REJECTED rewrites never ship — they are excluded from the
            // document and from validity (their original, evidence-derived
            // text stands instead). Editing (EDITED/REGENERATED re-edits) is
            // the reviewer's path for flagged rows (FEAT-037, SPRINT-06).
            if ("REJECTED".equals(change.getStatus())) {
                continue;
            }
            ValidationRules.Verdict verdict = validationRules.validate(
                    change.getClaimCategory(),
                    change.getOriginalText(),
                    change.getTailoredText(),
                    evidenceTexts);
            valid = valid && verdict.valid();
            checked++;
            for (ValidationIssue issue : verdict.issues()) {
                issues.add(issue(change, issue));
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("valid", valid);
        report.put("checked_changes", checked);
        report.put("validated_at", Instant.now().toString());
        report.put("issues", issues);
        String stored = write(report);

        tailored.setValidation(stored);
        if (stuckInValidating) {
            tailored.setState(StateMachines.tailoring()
                    .transition(TailoringState.valueOf(tailored.getState()), TailoringState.READY).name());
        }
        tailoredResumeRepository.save(tailored);
        return json(stored);
    }

    private static Map<String, Object> issue(TailoredChange change, ValidationIssue issue) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", issue.type().name().toLowerCase(Locale.ROOT));
        entry.put("code", issue.code());
        entry.put("text", issue.text());
        entry.put("suggestion", issue.suggestion());
        entry.put("context", issue.context());
        entry.put("change_id", change.getId());
        entry.put("claim_category", change.getClaimCategory());
        return entry;
    }

    /** Every evidence-row text of the tailored resume's master version — the grounding corpus for all changes. */
    private List<String> evidenceTextsOf(UUID resumeVersionId) {
        List<String> texts = new ArrayList<>();
        for (ResumeEvidence row : resumeEvidenceRepository.findByResumeVersionId(resumeVersionId)) {
            if (row.getText() != null && !row.getText().isBlank()) {
                texts.add(row.getText());
            }
        }
        return texts;
    }

    private static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize validation report", ex);
        }
    }

    private static JsonNode json(String stored) {
        try {
            return MAPPER.readTree(stored);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not re-read validation report", ex);
        }
    }
}