package com.atsdoctor.backend.application.analysis;

import com.atsdoctor.backend.api.PageResult;
import com.atsdoctor.backend.api.jobs.JobDto;
import com.atsdoctor.backend.api.resume.ResumeDto;
import com.atsdoctor.backend.domain.states.AnalysisState;
import com.atsdoctor.backend.domain.states.StateMachines;
import com.atsdoctor.backend.infrastructure.matching.EvidenceDoc;
import com.atsdoctor.backend.infrastructure.matching.MatchRequest;
import com.atsdoctor.backend.infrastructure.matching.RequirementData;
import com.atsdoctor.backend.infrastructure.persistence.Analysis;
import com.atsdoctor.backend.infrastructure.persistence.AnalysisRepository;
import com.atsdoctor.backend.infrastructure.persistence.Job;
import com.atsdoctor.backend.infrastructure.persistence.JobRepository;
import com.atsdoctor.backend.infrastructure.persistence.JobRequirement;
import com.atsdoctor.backend.infrastructure.persistence.JobRequirementRepository;
import com.atsdoctor.backend.infrastructure.persistence.ResumeEvidenceRepository;
import com.atsdoctor.backend.infrastructure.persistence.ResumeVersion;
import com.atsdoctor.backend.infrastructure.persistence.ResumeVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Analysis orchestration (FEAT-027, TASK-056/057/058). Creates analyses at
 * {@code QUEUED}, loads the job + resume into a {@link MatchRequest}, and
 * completes/fails them via the async {@link AnalysisPipeline}. Every state
 * change goes through {@link StateMachines#analysis()}.
 */
@Service
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class AnalysisService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AnalysisRepository analysisRepository;
    private final JobRepository jobRepository;
    private final JobRequirementRepository jobRequirementRepository;
    private final ResumeVersionRepository resumeVersionRepository;
    private final ResumeEvidenceRepository resumeEvidenceRepository;
    private final Validator validator;
    private final ApplicationEventPublisher publisher;

    public AnalysisService(AnalysisRepository analysisRepository,
                           JobRepository jobRepository,
                           JobRequirementRepository jobRequirementRepository,
                           ResumeVersionRepository resumeVersionRepository,
                           ResumeEvidenceRepository resumeEvidenceRepository,
                           Validator validator,
                           ApplicationEventPublisher publisher) {
        this.analysisRepository = analysisRepository;
        this.jobRepository = jobRepository;
        this.jobRequirementRepository = jobRequirementRepository;
        this.resumeVersionRepository = resumeVersionRepository;
        this.resumeEvidenceRepository = resumeEvidenceRepository;
        this.validator = validator;
        this.publisher = publisher;
    }

    /** Queue a new analysis (job + resume version must be READY). */
    @Transactional
    public Analysis queue(UUID jobId, UUID resumeVersionId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new AnalysisValidationException("No job found for id " + jobId));
        ResumeVersion resumeVersion = resumeVersionRepository.findById(resumeVersionId)
                .orElseThrow(() -> new AnalysisValidationException("No resume version found for id " + resumeVersionId));
        if (!"READY".equals(job.getState())) {
            throw new AnalysisValidationException("Job " + jobId + " is not READY (state=" + job.getState() + ")");
        }
        if (!"READY".equals(resumeVersion.getState())) {
            throw new AnalysisValidationException("Resume version " + resumeVersionId + " is not READY (state=" + resumeVersion.getState() + ")");
        }
        requireMasterSource(resumeVersion);
        Analysis analysis = new Analysis();
        analysis.setJob(job);
        analysis.setResumeVersion(resumeVersion);
        analysis.setState(AnalysisState.QUEUED.name());
        Analysis saved = analysisRepository.saveAndFlush(analysis);
        schedule(saved.getId());
        return saved;
    }

    /** Pipeline step: QUEUED → MATCHING → SCORING. */
    @Transactional
    public void mark(UUID analysisId, AnalysisState to) {
        Analysis analysis = require(analysisId);
        transition(analysis, to);
        analysisRepository.save(analysis);
    }

    /** Pipeline prerequisite data: the job + resume, parsed into a MatchRequest. */
    @Transactional(readOnly = true)
    public AnalysisInput load(UUID analysisId) {
        Analysis analysis = require(analysisId);
        return new AnalysisInput(analysis.getId(), buildMatchRequest(analysis));
    }

    /** Pipeline success: SCORING → READY with score/breakdown/matches/gaps. */
    @Transactional
    public void completeSuccess(UUID analysisId, String scoreBreakdownJson,
                                String matchesJson, String gapsJson, String generationJson) {
        Analysis analysis = require(analysisId);
        transition(analysis, AnalysisState.READY);
        analysis.setScore(scoreFromBreakdown(scoreBreakdownJson));
        analysis.setScoreBreakdown(scoreBreakdownJson);
        analysis.setMatches(matchesJson);
        analysis.setGaps(gapsJson);
        analysis.setGeneration(generationJson);
        analysis.setError(null);
        analysisRepository.save(analysis);
    }

    /** Pipeline failure: any active state → FAILED with error. */
    @Transactional
    public void completeFailure(UUID analysisId, Throwable error) {
        Analysis analysis = require(analysisId);
        transition(analysis, AnalysisState.FAILED);
        analysis.setError(messageOf(error));
        analysisRepository.save(analysis);
    }

    /** Re-run an analysis: READY/FAILED → QUEUED, result fields reset (TASK-058). */
    @Transactional
    public Analysis reanalyze(UUID analysisId) {
        Analysis analysis = require(analysisId);
        String state = analysis.getState();
        if (!"READY".equals(state) && !"FAILED".equals(state)) {
            throw new AnalysisNotReadyException("Analysis " + analysisId + " cannot be reanalyzed in state " + state);
        }
        transition(analysis, AnalysisState.QUEUED);
        analysis.setScore(null);
        analysis.setScoreBreakdown(null);
        analysis.setMatches(null);
        analysis.setGaps(null);
        analysis.setGeneration(null);
        analysis.setError(null);
        analysisRepository.saveAndFlush(analysis);
        schedule(analysisId);
        return analysis;
    }

    /** Paginated list, newest first; page/size are clamped in {@link PageResult}. */
    @Transactional(readOnly = true)
    public Page<Analysis> list(int page, int size) {
        return analysisRepository.findAll(PageRequest.of(
                        PageResult.clampPage(page), PageResult.clampSize(size),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Transactional(readOnly = true)
    public Analysis byId(UUID analysisId) {
        return require(analysisId);
    }

    // ------------------------------------------------------------------

    private MatchRequest buildMatchRequest(Analysis analysis) {
        Job job = analysis.getJob();
        if (job.getStructuredData() == null) {
            throw new AnalysisValidationException("Job " + job.getId() + " has no structured data");
        }
        JobDto jobDto = JobDto.parse(job.getStructuredData(), validator);

        ResumeVersion resumeVersion = analysis.getResumeVersion();
        if (resumeVersion.getStructuredData() == null) {
            throw new AnalysisValidationException("Resume version " + resumeVersion.getId() + " has no structured data");
        }
        ResumeDto resumeDto = ResumeDto.parse(resumeVersion.getStructuredData(), validator);

        List<EvidenceDoc> evidence = new ArrayList<>();
        int rowIndex = 0;
        for (var row : resumeEvidenceRepository.findByResumeVersionId(resumeVersion.getId())) {
            String id = row.getId() != null ? row.getId().toString()
                    : row.getSectionId() != null ? "evidence:" + row.getSectionId()
                    : "evidence:" + rowIndex;
            evidence.add(new EvidenceDoc(id, row.getText()));
            rowIndex++;
        }
        StringBuilder resumeText = new StringBuilder(optional(resumeDto.summary()));

        if (resumeDto.education() != null) {
            int i = 0;
            for (ResumeDto.Education edu : resumeDto.education()) {
                String label = String.join(" ",
                        optional(edu.institution()), optional(edu.degree()), optional(edu.field())).trim();
                if (!label.isBlank()) {
                    evidence.add(new EvidenceDoc("edu_" + (i++), label));
                    resumeText.append('\n').append(label);
                }
            }
        }
        for (EvidenceDoc doc : evidence) {
            resumeText.append('\n').append(doc.text());
        }

        Integer maxYears = null;
        if (resumeDto.skills() != null) {
            maxYears = resumeDto.skills().stream()
                    .map(ResumeDto.Skill::years)
                    .filter(y -> y != null)
                    .max(Integer::compareTo)
                    .orElse(null);
        }

        List<RequirementData> requirements = new ArrayList<>();
        // Authoritative requirement list = job_requirements rows (what
        // requirement_extraction produced and the product surfaces), not the
        // raw JD parse's requirements array.
        var rows = new ArrayList<>(jobRequirementRepository.findByJobId(job.getId()));
        rows.sort(java.util.Comparator
                .comparing((JobRequirement r) -> r.getCreatedAt() == null ? java.time.Instant.MIN : r.getCreatedAt())
                .thenComparing(r -> r.getId() == null ? UUID.randomUUID() : r.getId()));
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            requirements.add(new RequirementData(
                    "req_" + (i + 1), row.getText(), row.getType(), row.getImportance(),
                    row.getKeywords() == null ? List.of() : List.of(row.getKeywords())));
        }

        return new MatchRequest(
                requirements,
                jobDto.keywords() == null ? List.of() : jobDto.keywords(),
                jobDto.responsibilities() == null ? List.of() : jobDto.responsibilities(),
                jobDto.job() == null ? null : jobDto.job().seniority(),
                evidence,
                resumeText.toString(),
                maxYears);
    }

    /** Expose the stored score from the breakdown JSON top-level {@code total}. */
    private static Integer scoreFromBreakdown(String scoreBreakdownJson) {
        try {
            var node = MAPPER.readTree(scoreBreakdownJson);
            return node.path("total").isInt() ? node.path("total").asInt() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private void schedule(UUID analysisId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.publishEvent(new AnalysisQueuedEvent(analysisId));
            }
        });
    }

    private void transition(Analysis analysis, AnalysisState to) {
        AnalysisState next = StateMachines.analysis()
                .transition(AnalysisState.valueOf(analysis.getState()), to);
        analysis.setState(next.name());
    }

    private Analysis require(UUID analysisId) {
        return analysisRepository.findById(analysisId)
                .orElseThrow(() -> new AnalysisNotFoundException("No analysis found for id " + analysisId));
    }

    private static String messageOf(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static String optional(String value) {
        return value == null ? "" : value;
    }

    private void requireMasterSource(ResumeVersion resumeVersion) {
        String name = resumeVersion.getResume() == null ? null : resumeVersion.getResume().getName();
        if (!com.atsdoctor.backend.application.resume.ResumeService.MASTER_RESUME_NAME.equals(name)) {
            throw new AnalysisValidationException(
                    "Resume version " + resumeVersion.getId() + " does not belong to the master resume"
                            + " (source of truth) — analysis must reference the exact master version"
                            + " (PRD §5.9, FEAT-044/TASK-084).");
        }
    }

    /** Pipeline input: analysis id + the prepared match request. */
    public record AnalysisInput(UUID analysisId, MatchRequest matchRequest) {
    }
}
