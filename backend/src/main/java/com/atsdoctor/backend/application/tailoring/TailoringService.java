package com.atsdoctor.backend.application.tailoring;

import com.atsdoctor.backend.domain.states.StateMachines;
import com.atsdoctor.backend.domain.states.TailoringState;
import com.atsdoctor.backend.infrastructure.persistence.Analysis;
import com.atsdoctor.backend.infrastructure.persistence.AnalysisRepository;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChange;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChangeRepository;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResume;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResumeRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tailoring orchestration (FEAT-032, TASK-065/066): creates
 * {@code tailored_resumes} rows at {@code QUEUED} for a READY analysis, and
 * completes/fails them via the async {@link TailoringPipeline}. Every state
 * change goes through {@link StateMachines#tailoring()}.
 *
 * <p>Guards: an analysis must be READY (its score is {@code score_before}), and
 * no tailoring run may already be active for it (one tailored resume per
 * analysis; re-tailors after READY create a new run).
 */
@Service
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class TailoringService {

    private static final Set<String> ACTIVE_STATES = Set.of(
            TailoringState.QUEUED.name(), TailoringState.GENERATING.name(), TailoringState.VALIDATING.name());

    private final AnalysisRepository analysisRepository;
    private final TailoredResumeRepository tailoredResumeRepository;
    private final TailoredChangeRepository tailoredChangeRepository;
    private final ApplicationEventPublisher publisher;

    public TailoringService(AnalysisRepository analysisRepository,
                            TailoredResumeRepository tailoredResumeRepository,
                            TailoredChangeRepository tailoredChangeRepository,
                            ApplicationEventPublisher publisher) {
        this.analysisRepository = analysisRepository;
        this.tailoredResumeRepository = tailoredResumeRepository;
        this.tailoredChangeRepository = tailoredChangeRepository;
        this.publisher = publisher;
    }

    /** One persisted per-change record (PRD §7.8, TASK-062). */
    public record TailoringChangeData(
            UUID evidenceId,
            String originalText,
            String tailoredText,
            String reason,
            String claimCategory,
            String promptVersion) {
    }

    /** Queue a tailoring run for a READY analysis. */
    @Transactional
    public TailoredResume tailor(UUID analysisId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new TailoringValidationException("No analysis found for id " + analysisId));
        if (!"READY".equals(analysis.getState())) {
            throw new TailoringConflictException(
                    "Analysis " + analysisId + " is not READY (state=" + analysis.getState() + ")");
        }
        boolean running = tailoredResumeRepository.findByAnalysisIdOrderByCreatedAtDesc(analysisId).stream()
                .anyMatch(t -> ACTIVE_STATES.contains(t.getState()));
        if (running) {
            throw new TailoringConflictException("Tailoring already in progress for analysis " + analysisId);
        }

        TailoredResume tailored = new TailoredResume();
        tailored.setAnalysis(analysis);
        tailored.setResumeVersion(analysis.getResumeVersion());
        tailored.setState(TailoringState.QUEUED.name());
        tailored.setScoreBefore(analysis.getScore());
        TailoredResume saved = tailoredResumeRepository.saveAndFlush(tailored);
        schedule(saved.getId());
        return saved;
    }

    /** Pipeline step: QUEUED → GENERATING → VALIDATING. */
    @Transactional
    public void mark(UUID tailoredResumeId, TailoringState to) {
        TailoredResume tailored = require(tailoredResumeId);
        transition(tailored, to);
        tailoredResumeRepository.save(tailored);
    }

    /** Pipeline success: VALIDATING → READY with content, score and changes. */
    @Transactional
    public void completeSuccess(UUID tailoredResumeId, String contentJson, int scoreAfter,
                                List<TailoringChangeData> changes) {
        TailoredResume tailored = require(tailoredResumeId);
        transition(tailored, TailoringState.READY);
        tailored.setContent(contentJson);
        tailored.setScoreAfter(scoreAfter);
        tailored.setError(null);
        tailoredResumeRepository.save(tailored);
        for (TailoringChangeData change : changes) {
            TailoredChange row = new TailoredChange();
            row.setTailoredResume(tailored);
            row.setEvidenceId(change.evidenceId());
            row.setOriginalText(change.originalText());
            row.setTailoredText(change.tailoredText());
            row.setReason(change.reason());
            row.setClaimCategory(change.claimCategory());
            row.setPromptVersion(change.promptVersion());
            tailoredChangeRepository.save(row);
        }
    }

    /**
     * Pipeline failure: record the error on the row. TailoringState has no
     * FAILED per §5.8 — failures surface through {@code error} (TASK-089).
     */
    @Transactional
    public void completeFailure(UUID tailoredResumeId, Throwable error) {
        TailoredResume tailored = require(tailoredResumeId);
        tailored.setError(messageOf(error));
        tailoredResumeRepository.save(tailored);
    }

    @Transactional(readOnly = true)
    public TailoredResume byId(UUID tailoredResumeId) {
        return require(tailoredResumeId);
    }

    /**
     * Read-only change listing (TASK-073 support; the review lifecycle POST
     * actions ship with FEAT-037/TASK-074 in SPRINT-06). 404 when the tailored
     * resume is unknown.
     */
    @Transactional(readOnly = true)
    public List<TailoredResume> list() {
        return tailoredResumeRepository.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
    }

    @Transactional(readOnly = true)
    public List<TailoredChange> changes(UUID tailoredResumeId) {
        require(tailoredResumeId);
        return tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(tailoredResumeId);
    }

    private void schedule(UUID tailoredResumeId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.publishEvent(new TailoringQueuedEvent(tailoredResumeId));
            }
        });
    }

    private void transition(TailoredResume tailored, TailoringState to) {
        TailoringState next = StateMachines.tailoring()
                .transition(TailoringState.valueOf(tailored.getState()), to);
        tailored.setState(next.name());
    }

    private TailoredResume require(UUID tailoredResumeId) {
        return tailoredResumeRepository.findById(tailoredResumeId)
                .orElseThrow(() -> new TailoringNotFoundException("No tailored resume found for id " + tailoredResumeId));
    }

    private static String messageOf(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}