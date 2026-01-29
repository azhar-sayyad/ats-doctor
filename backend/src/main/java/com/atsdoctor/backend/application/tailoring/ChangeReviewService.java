package com.atsdoctor.backend.application.tailoring;

import com.atsdoctor.backend.api.jobs.JobDto;
import com.atsdoctor.backend.domain.states.StateMachines;
import com.atsdoctor.backend.domain.states.TailoringState;
import com.atsdoctor.backend.infrastructure.persistence.Analysis;
import com.atsdoctor.backend.infrastructure.persistence.JobRequirement;
import com.atsdoctor.backend.infrastructure.persistence.JobRequirementRepository;
import com.atsdoctor.backend.infrastructure.persistence.ResumeEvidence;
import com.atsdoctor.backend.infrastructure.persistence.ResumeEvidenceRepository;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChange;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChangeRepository;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResume;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResumeRepository;
import com.atsdoctor.backend.infrastructure.tailoring.BulletRewriter;
import com.atsdoctor.backend.application.validation.ValidationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tailored-change review lifecycle (FEAT-037, SPRINT-06, TASK-074/075/078):
 * per-change {@code POST /tailored/{id}/changes/{changeId}} actions
 * (ACCEPTED/REJECTED/EDITED/REGENERATED) and the approve gate
 * {@code POST /tailored/{id}/approve} (READY → NEEDS_REVIEW → APPROVED).
 *
 * <p>Rules (locked with the sprint plan): accept/reject/regenerate act only on
 * PENDING rows; the first review action moves a READY resume to NEEDS_REVIEW;
 * edit requires a non-blank replacement and is additionally allowed on
 * EDITED/REGENERATED rows (the reviewer re-fines a rewrite until it is
 * grounded); regenerate re-runs the {@link BulletRewriter} against the same
 * JD-gap context (deterministic re-run — one {@code ai_runs} row); both edit
 * and regenerate revalidate the resume in place via {@link ValidationService}
 * (guard admits NEEDS_REVIEW); approve requires no remaining PENDING changes
 * AND a persisted validation report with {@code valid=true} (409 otherwise);
 * approve applies the two existing edges idempotently — an already-APPROVED
 * resume approves without error.
 */
@Service
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class ChangeReviewService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TailoredResumeRepository tailoredResumeRepository;
    private final TailoredChangeRepository tailoredChangeRepository;
    private final JobRequirementRepository jobRequirementRepository;
    private final ResumeEvidenceRepository resumeEvidenceRepository;
    private final BulletRewriter bulletRewriter;
    private final ValidationService validationService;
    private final Validator validator;

    public ChangeReviewService(TailoredResumeRepository tailoredResumeRepository,
                               TailoredChangeRepository tailoredChangeRepository,
                               JobRequirementRepository jobRequirementRepository,
                               ResumeEvidenceRepository resumeEvidenceRepository,
                               BulletRewriter bulletRewriter,
                               ValidationService validationService,
                               Validator validator) {
        this.tailoredResumeRepository = tailoredResumeRepository;
        this.tailoredChangeRepository = tailoredChangeRepository;
        this.jobRequirementRepository = jobRequirementRepository;
        this.resumeEvidenceRepository = resumeEvidenceRepository;
        this.bulletRewriter = bulletRewriter;
        this.validationService = validationService;
        this.validator = validator;
    }

    /** Review action vocabulary (07-api-contract §8.2). */
    public enum ReviewAction {
        ACCEPT, REJECT, EDIT, REGENERATE;

        public static ReviewAction of(String value) {
            if (value == null || value.isBlank()) {
                throw new TailoringValidationException("Review action is required (accept|reject|edit|regenerate)");
            }
            try {
                return ReviewAction.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new TailoringValidationException(
                        "Unknown review action '" + value + "' (accept|reject|edit|regenerate)");
            }
        }
    }

    /** Apply one review action to a change. */
    @Transactional
    public TailoredChange apply(UUID tailoredResumeId, UUID changeId, ReviewAction action, String newText) {
        TailoredResume tailored = requireTailored(tailoredResumeId);
        TailoredChange change = requireChange(tailoredResumeId, changeId);
        if (action != ReviewAction.EDIT && !"PENDING".equals(change.getStatus())) {
            throw new TailoringConflictException(
                    "Change " + changeId + " is already reviewed (status=" + change.getStatus() + ")");
        }
        if (action == ReviewAction.EDIT && !isEditableStatus(change.getStatus())) {
            throw new TailoringConflictException(
                    "Change " + changeId + " is not editable (status=" + change.getStatus() + ")");
        }
        ensureReviewable(tailored);
        if ("READY".equals(tailored.getState())) {
            tailored.setState(StateMachines.tailoring()
                    .transition(TailoringState.valueOf(tailored.getState()), TailoringState.NEEDS_REVIEW).name());
        }

        switch (action) {
            case ACCEPT -> change.setStatus("ACCEPTED");
            case REJECT -> change.setStatus("REJECTED");
            case EDIT -> {
                if (newText == null || newText.isBlank()) {
                    throw new TailoringValidationException("new_text must not be blank for an edit action");
                }
                change.setTailoredText(newText.trim());
                change.setStatus("EDITED");
                revalidate(tailored);
            }
            case REGENERATE -> {
                BulletRewriter.RewrittenText rewritten = bulletRewriter.rewriteBullet(
                        change.getOriginalText(),
                        gapTextsOf(tailored),
                        evidenceTextOf(tailored),
                        keyWordsOf(tailored));
                change.setTailoredText(rewritten.text());
                change.setPromptVersion(rewritten.promptVersion());
                change.setStatus("REGENERATED");
                revalidate(tailored);
            }
        }
        return tailoredChangeRepository.save(change);
    }

    /**
     * Approve a fully reviewed, valid resume. Idempotent for APPROVED rows;
     * 409 until every change is resolved and the persisted validation report
     * is {@code valid=true}.
     */
    @Transactional
    public TailoredResume approve(UUID tailoredResumeId) {
        TailoredResume tailored = requireTailored(tailoredResumeId);
        if ("APPROVED".equals(tailored.getState())) {
            return tailored;
        }
        ensureReviewable(tailored);
        boolean pending = tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(tailoredResumeId)
                .stream().anyMatch(c -> "PENDING".equals(c.getStatus()));
        if (pending) {
            throw new TailoringConflictException("Cannot approve: changes are still pending review");
        }
        if (!validationValid(tailored)) {
            throw new TailoringConflictException("Cannot approve: fact-grounding validation did not pass");
        }

        if ("READY".equals(tailored.getState())) {
            tailored.setState(StateMachines.tailoring()
                    .transition(TailoringState.valueOf(tailored.getState()), TailoringState.NEEDS_REVIEW).name());
        }
        tailored.setState(StateMachines.tailoring()
                .transition(TailoringState.valueOf(tailored.getState()), TailoringState.APPROVED).name());
        return tailoredResumeRepository.save(tailored);
    }

    // ------------------------------------------------------------------

    /** A rewrite stays editable until the reviewer is satisfied: PENDING, EDITED or REGENERATED. */
    private static boolean isEditableStatus(String status) {
        return "PENDING".equals(status) || "EDITED".equals(status) || "REGENERATED".equals(status);
    }

    private void revalidate(TailoredResume tailored) {
        tailoredResumeRepository.save(tailored);
        validationService.validate(tailored.getId());
    }

    /** STATE must be READY or NEEDS_REVIEW — review actions and approve are 409 otherwise (incl. APPROVED). */
    private void ensureReviewable(TailoredResume tailored) {
        String state = tailored.getState();
        if (!"READY".equals(state) && !"NEEDS_REVIEW".equals(state)) {
            throw new TailoringConflictException(
                    "Tailored resume " + tailored.getId() + " is not under review (state=" + state + ")");
        }
    }

    /** Persisted report must exist and be valid ({@code valid=true}). */
    private boolean validationValid(TailoredResume tailored) {
        String stored = tailored.getValidation();
        if (stored == null || stored.isBlank()) {
            return false;
        }
        try {
            JsonNode node = MAPPER.readTree(stored);
            return node.path("valid").asBoolean(false);
        } catch (Exception ex) {
            return false;
        }
    }

    private TailoredResume requireTailored(UUID tailoredResumeId) {
        return tailoredResumeRepository.findById(tailoredResumeId)
                .orElseThrow(() -> new TailoringNotFoundException(
                        "No tailored resume found for id " + tailoredResumeId));
    }

    /** A change that does not belong to the tailored resume is 404. */
    private TailoredChange requireChange(UUID tailoredResumeId, UUID changeId) {
        TailoredChange change = tailoredChangeRepository.findById(changeId)
                .orElseThrow(() -> new TailoringNotFoundException("No change found for id " + changeId));
        if (change.getTailoredResume() == null
                || !tailoredResumeId.equals(change.getTailoredResume().getId())) {
            throw new TailoringNotFoundException(
                    "Change " + changeId + " does not belong to tailored resume " + tailoredResumeId);
        }
        return change;
    }

    /** JD-gap requirement texts for the resume's analysis (mirrors the pipeline context). */
    private List<String> gapTextsOf(TailoredResume tailored) {
        Map<String, List<String>> keywordsByText = new LinkedHashMap<>();
        for (JobRequirement row : requirementsOf(tailored)) {
            keywordsByText.put(row.getText(), row.getKeywords() == null
                    ? List.of() : List.of(row.getKeywords()));
        }
        List<String> gaps = new ArrayList<>();
        for (JsonNode match : matchesOf(tailored.getAnalysis())) {
            String text = match.path("requirement_text").asText("");
            if (!"matched".equals(match.path("status").asText("")) && !text.isBlank()) {
                gaps.add(text);
            }
        }
        return gaps;
    }

    /** Joined evidence text of the tailored resume's master version. */
    private String evidenceTextOf(TailoredResume tailored) {
        StringBuilder all = new StringBuilder();
        for (ResumeEvidence row :
                resumeEvidenceRepository.findByResumeVersionId(tailored.getResumeVersion().getId())) {
            all.append('\n').append(row.getText());
        }
        return all.toString();
    }

    private List<String> keyWordsOf(TailoredResume tailored) {
        JobDto jobDto = JobDto.parse(tailored.getAnalysis().getJob().getStructuredData(), validator);
        return jobDto.keywords() == null ? List.of() : jobDto.keywords();
    }

    private List<JobRequirement> requirementsOf(TailoredResume tailored) {
        List<JobRequirement> rows =
                new ArrayList<>(jobRequirementRepository.findByJobId(tailored.getAnalysis().getJob().getId()));
        rows.sort(Comparator
                .comparing((JobRequirement r) -> r.getCreatedAt() == null ? java.time.Instant.MIN : r.getCreatedAt())
                .thenComparing(r -> r.getId() == null ? UUID.randomUUID() : r.getId()));
        return rows;
    }

    private List<JsonNode> matchesOf(Analysis analysis) {
        if (analysis.getMatches() == null || analysis.getMatches().isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = MAPPER.readTree(analysis.getMatches());
            if (node.isArray()) {
                List<JsonNode> out = new ArrayList<>();
                node.forEach(out::add);
                return out;
            }
        } catch (Exception ignored) {
            // treat as no gaps
        }
        return List.of();
    }
}