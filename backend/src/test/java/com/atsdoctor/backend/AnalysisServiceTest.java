package com.atsdoctor.backend;

import com.atsdoctor.backend.application.analysis.AnalysisNotReadyException;
import com.atsdoctor.backend.application.analysis.AnalysisNotFoundException;
import com.atsdoctor.backend.application.analysis.AnalysisService;
import com.atsdoctor.backend.application.analysis.AnalysisValidationException;
import com.atsdoctor.backend.domain.states.AnalysisState;
import com.atsdoctor.backend.infrastructure.matching.EvidenceDoc;
import com.atsdoctor.backend.infrastructure.matching.MatchRequest;
import com.atsdoctor.backend.infrastructure.persistence.Analysis;
import com.atsdoctor.backend.infrastructure.persistence.AnalysisRepository;
import com.atsdoctor.backend.infrastructure.persistence.Job;
import com.atsdoctor.backend.infrastructure.persistence.JobRepository;
import com.atsdoctor.backend.infrastructure.persistence.JobRequirement;
import com.atsdoctor.backend.infrastructure.persistence.JobRequirementRepository;
import com.atsdoctor.backend.infrastructure.persistence.ResumeEvidence;
import com.atsdoctor.backend.infrastructure.persistence.ResumeEvidenceRepository;
import com.atsdoctor.backend.infrastructure.persistence.ResumeVersion;
import com.atsdoctor.backend.infrastructure.persistence.ResumeVersionRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TASK-056/057/058 — analysis orchestration, state machine, match-request loading. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalysisServiceTest {

    @Mock
    private AnalysisRepository analysisRepository;
    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobRequirementRepository jobRequirementRepository;
    @Mock
    private ResumeVersionRepository resumeVersionRepository;
    @Mock
    private ResumeEvidenceRepository resumeEvidenceRepository;
    @Mock
    private ApplicationEventPublisher publisher;

    private AnalysisService service;

    @BeforeEach
    void setUp() {
        service = new AnalysisService(analysisRepository, jobRepository, jobRequirementRepository,
                resumeVersionRepository, resumeEvidenceRepository,
                Validation.buildDefaultValidatorFactory().getValidator(), publisher);
        when(analysisRepository.saveAndFlush(any(Analysis.class))).thenAnswer(inv -> inv.getArgument(0));
        when(analysisRepository.save(any(Analysis.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jobRequirementRepository.findByJobId(any())).thenReturn(List.of());
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void queue_requires_ready_job_and_resume() {
        Job job = readyJob();
        ResumeVersion version = readyVersion();

        Analysis analysis = service.queue(job.getId(), version.getId());

        assertThat(analysis.getState()).isEqualTo("QUEUED");
        assertThat(analysis.getScore()).isNull();
        verify(analysisRepository).saveAndFlush(any(Analysis.class));
    }

    @Test
    void queue_rejects_non_ready_job() {
        Job job = readyJob();
        job.setState("PARSING");
        ResumeVersion version = readyVersion();

        assertThatThrownBy(() -> service.queue(job.getId(), version.getId()))
                .isInstanceOf(AnalysisValidationException.class)
                .hasMessageContaining("not READY");
    }

    @Test
    void queue_rejects_missing_job_or_resume() {
        when(jobRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.queue(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(AnalysisValidationException.class)
                .hasMessageContaining("No job found");
    }

    @Test
    void queue_rejects_non_master_resume_versions_no_chaining() {
        Job job = readyJob();
        ResumeVersion derived = new ResumeVersion();
        derived.setState("READY");
        com.atsdoctor.backend.infrastructure.persistence.Resume resume =
                new com.atsdoctor.backend.infrastructure.persistence.Resume();
        resume.setName("TailoredVariant");
        derived.setResume(resume);
        when(resumeVersionRepository.findById(derived.getId())).thenReturn(Optional.of(derived));

        assertThatThrownBy(() -> service.queue(job.getId(), derived.getId()))
                .isInstanceOf(AnalysisValidationException.class)
                .hasMessageContaining("source of truth");
    }

    @Test
    void mark_enforces_analysis_state_machine() {
        Analysis analysis = analysis(AnalysisState.QUEUED);

        service.mark(analysis.getId(), AnalysisState.MATCHING);

        verify(analysisRepository).save(argState(AnalysisState.MATCHING));
    }

    @Test
    void mark_rejects_illegal_transitions() {
        Analysis analysis = analysis(AnalysisState.QUEUED);

        assertThatThrownBy(() -> service.mark(analysis.getId(), AnalysisState.READY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("QUEUED");
    }

    @Test
    void complete_success_extracts_score_and_persists_breakdown() {
        Analysis analysis = analysis(AnalysisState.SCORING);

        service.completeSuccess(analysis.getId(),
                "{\"total\": 69, \"skills\": {\"score\": 73, \"weight\": 0.3, \"matched\": [], \"missing\": []}}",
                "[]", "[]", "{\"matching\": {\"mode\": \"exact\"}}");

        assertThat(analysis.getState()).isEqualTo("READY");
        assertThat(analysis.getScore()).isEqualTo(69);
        assertThat(analysis.getMatches()).isEqualTo("[]");
        assertThat(analysis.getError()).isNull();
        verify(analysisRepository).save(analysis);
    }

    @Test
    void complete_failure_marks_failed_with_error() {
        Analysis analysis = analysis(AnalysisState.MATCHING);

        service.completeFailure(analysis.getId(), new IllegalStateException("boom"));

        assertThat(analysis.getState()).isEqualTo("FAILED");
        assertThat(analysis.getError()).isEqualTo("boom");
    }

    @Test
    void reanalyze_returns_ready_analysis_to_queued_and_resets_results() {
        Analysis analysis = analysis(AnalysisState.READY);
        analysis.setScore(69);
        analysis.setMatches("[]");

        Analysis requeued = service.reanalyze(analysis.getId());

        assertThat(requeued.getState()).isEqualTo("QUEUED");
        assertThat(requeued.getScore()).isNull();
        assertThat(requeued.getMatches()).isNull();
    }

    @Test
    void reanalyze_rejects_a_running_analysis() {
        Analysis analysis = analysis(AnalysisState.MATCHING);

        assertThatThrownBy(() -> service.reanalyze(analysis.getId()))
                .isInstanceOf(AnalysisNotReadyException.class)
                .hasMessageContaining("MATCHING");
    }

    @Test
    void load_builds_a_match_request_from_job_and_resume() {
        Job job = readyJob();
        ResumeVersion version = readyVersion();
        version.setStructuredData("""
                {"basics":{"name":"Jane Doe"},"summary":"Senior engineer with 5+ years.",
                 "skills":[{"name":"Python","years":5}],
                 "education":[{"institution":"Stanford","degree":"MSc","field":"CS"}]}
                """);
        ResumeEvidence evidence = new ResumeEvidence();
        evidence.setSection("experience");
        evidence.setSectionId("exp_001_bullet_001");
        evidence.setText("Built FastAPI services.");
        when(resumeEvidenceRepository.findByResumeVersionId(version.getId()))
                .thenReturn(List.of(evidence));
        JobRequirement requirement = new JobRequirement();
        requirement.setText("5+ years of backend experience");
        requirement.setType("experience");
        requirement.setImportance("high");
        requirement.setKeywords(new String[]{"backend"});
        when(jobRequirementRepository.findByJobId(job.getId())).thenReturn(List.of(requirement));

        Analysis analysis = analysis(AnalysisState.MATCHING);
        analysis.setJob(job);
        analysis.setResumeVersion(version);

        AnalysisService.AnalysisInput input = service.load(analysis.getId());

        MatchRequest request = input.matchRequest();
        assertThat(request.requirements()).hasSize(1);
        assertThat(request.requirements().get(0).id()).isEqualTo("req_1");
        assertThat(request.seniority()).isEqualTo("Senior");
        assertThat(request.maxResumeYears()).isEqualTo(5);
        assertThat(request.evidence()).extracting(EvidenceDoc::id)
                .contains("evidence:exp_001_bullet_001", "edu_0");
        assertThat(request.resumeText()).contains("FastAPI").contains("Stanford");
    }

    @Test
    void missing_analysis_yields_404_exception() {
        when(analysisRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.byId(UUID.randomUUID()))
                .isInstanceOf(AnalysisNotFoundException.class)
                .hasMessageContaining("No analysis found");
    }

    @Test
    void list_returns_newest_first() {
        Analysis a1 = analysis(AnalysisState.READY);
        when(analysisRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(a1));

        assertThat(service.list()).containsExactly(a1);
        verify(analysisRepository).findAllByOrderByCreatedAtDesc();
    }

    private Job readyJob() {
        Job job = new Job();
        job.setState("READY");
        job.setStructuredData("{\"job\":{\"title\":\"Senior Backend Engineer\",\"seniority\":\"Senior\"}}");
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        return job;
    }

    private ResumeVersion readyVersion() {
        ResumeVersion version = new ResumeVersion();
        version.setState("READY");
        com.atsdoctor.backend.infrastructure.persistence.Resume resume =
                new com.atsdoctor.backend.infrastructure.persistence.Resume();
        resume.setName(com.atsdoctor.backend.application.resume.ResumeService.MASTER_RESUME_NAME);
        version.setResume(resume);
        when(resumeVersionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        return version;
    }

    private Analysis analysis(AnalysisState state) {
        Analysis analysis = new Analysis();
        analysis.setState(state.name());
        when(analysisRepository.findById(analysis.getId())).thenReturn(Optional.of(analysis));
        return analysis;
    }

    private static Analysis argState(AnalysisState state) {
        return ArgumentMatchers.argThat((Analysis a) -> state.name().equals(a.getState()));
    }
}
