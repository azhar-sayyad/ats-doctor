package com.atsdoctor.backend;

import com.atsdoctor.backend.application.tailoring.TailoringConflictException;
import com.atsdoctor.backend.application.tailoring.TailoringNotFoundException;
import com.atsdoctor.backend.application.tailoring.TailoringService;
import com.atsdoctor.backend.application.tailoring.TailoringValidationException;
import com.atsdoctor.backend.domain.states.TailoringState;
import com.atsdoctor.backend.infrastructure.persistence.Analysis;
import com.atsdoctor.backend.infrastructure.persistence.AnalysisRepository;
import com.atsdoctor.backend.infrastructure.persistence.Job;
import com.atsdoctor.backend.infrastructure.persistence.ResumeVersion;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChange;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChangeRepository;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResume;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResumeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TASK-065/066 — tailoring orchestration: guards, state machine, change persistence. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TailoringServiceTest {

    @Mock
    private AnalysisRepository analysisRepository;
    @Mock
    private TailoredResumeRepository tailoredResumeRepository;
    @Mock
    private TailoredChangeRepository tailoredChangeRepository;
    @Mock
    private ApplicationEventPublisher publisher;

    private TailoringService service;

    @BeforeEach
    void setUp() {
        service = new TailoringService(analysisRepository, tailoredResumeRepository,
                tailoredChangeRepository, publisher);
        when(tailoredResumeRepository.saveAndFlush(any(TailoredResume.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tailoredResumeRepository.save(any(TailoredResume.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tailoredChangeRepository.save(any(TailoredChange.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tailoredResumeRepository.findByAnalysisIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void tailor_queues_for_a_ready_analysis_with_score_before() {
        when(analysisRepository.findById(any())).thenReturn(Optional.of(readyAnalysis(68)));

        TailoredResume tailored = service.tailor(UUID.randomUUID());

        assertThat(tailored.getState()).isEqualTo("QUEUED");
        assertThat(tailored.getScoreBefore()).isEqualTo(68);
        assertThat(tailored.getError()).isNull();
    }

    @Test
    void tailor_rejects_unknown_analysis() {
        when(analysisRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.tailor(UUID.randomUUID()))
                .isInstanceOf(TailoringValidationException.class)
                .hasMessageContaining("No analysis found");
    }

    @Test
    void tailor_conflicts_when_analysis_is_not_ready() {
        when(analysisRepository.findById(any())).thenReturn(Optional.of(analysis("MATCHING", null)));

        assertThatThrownBy(() -> service.tailor(UUID.randomUUID()))
                .isInstanceOf(TailoringConflictException.class)
                .hasMessageContaining("not READY");
    }

    @Test
    void tailor_conflicts_while_a_run_is_active() {
        when(analysisRepository.findById(any())).thenReturn(Optional.of(readyAnalysis(68)));
        when(tailoredResumeRepository.findByAnalysisIdOrderByCreatedAtDesc(any()))
                .thenReturn(List.of(tailored("GENERATING")));

        assertThatThrownBy(() -> service.tailor(UUID.randomUUID()))
                .isInstanceOf(TailoringConflictException.class)
                .hasMessageContaining("already in progress");
    }

    @Test
    void mark_follows_the_tailoring_state_machine() {
        TailoredResume tailored = tailored("QUEUED");
        when(tailoredResumeRepository.findById(any())).thenReturn(Optional.of(tailored));

        service.mark(tailored.getId(), TailoringState.GENERATING);

        assertThat(tailored.getState()).isEqualTo("GENERATING");
        assertThatThrownBy(() -> service.mark(tailored.getId(), TailoringState.READY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid state transition");
    }

    @Test
    void complete_success_persists_changes_with_prompt_version() {
        TailoredResume tailored = tailored("VALIDATING");
        when(tailoredResumeRepository.findById(any())).thenReturn(Optional.of(tailored));

        service.completeSuccess(tailored.getId(), "{\"summary\":null}", 71,
                List.of(new TailoringService.TailoringChangeData(
                        null, "Old summary", "New summary", "Alignment", "B", "tailor-bullet-v1")));

        assertThat(tailored.getState()).isEqualTo("READY");
        assertThat(tailored.getScoreAfter()).isEqualTo(71);
        assertThat(tailored.getError()).isNull();
        verify(tailoredChangeRepository).save(any(TailoredChange.class));
    }

    @Test
    void complete_failure_records_the_error_without_a_failed_state() {
        TailoredResume tailored = tailored("GENERATING");
        when(tailoredResumeRepository.findById(any())).thenReturn(Optional.of(tailored));

        service.completeFailure(tailored.getId(), new IllegalStateException("boom"));

        assertThat(tailored.getState()).isEqualTo("GENERATING");
        assertThat(tailored.getError()).isEqualTo("boom");
    }

    @Test
    void by_id_throws_not_found() {
        when(tailoredResumeRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.byId(UUID.randomUUID()))
                .isInstanceOf(TailoringNotFoundException.class);
    }

    @Test
    void changes_lists_the_tailored_changes_in_order() {
        UUID tailoredId = UUID.randomUUID();
        TailoredResume tailored = new TailoredResume();
        tailored.setAnalysis(new Analysis());
        tailored.setResumeVersion(new ResumeVersion());
        tailored.setState("READY");
        when(tailoredResumeRepository.findById(tailoredId)).thenReturn(Optional.of(tailored));
        TailoredChange first = new TailoredChange();
        first.setClaimCategory("A");
        first.setOriginalText("original A");
        first.setTailoredText("tailored A");
        TailoredChange second = new TailoredChange();
        second.setClaimCategory("B");
        second.setOriginalText("original B");
        second.setTailoredText("tailored B");
        when(tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(tailoredId))
                .thenReturn(List.of(first, second));

        assertThat(service.changes(tailoredId)).hasSize(2);
        assertThat(service.changes(tailoredId).get(0).getClaimCategory()).isEqualTo("A");
    }

    @Test
    void changes_throws_not_found_for_unknown_tailored() {
        when(tailoredResumeRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changes(UUID.randomUUID()))
                .isInstanceOf(TailoringNotFoundException.class);
    }

    private static Analysis readyAnalysis(int score) {
        return analysis("READY", score);
    }

    private static Analysis analysis(String state, Integer score) {
        Analysis analysis = new Analysis();
        analysis.setJob(new Job());
        analysis.setResumeVersion(new ResumeVersion());
        analysis.setState(state);
        analysis.setScore(score);
        return analysis;
    }

    private static TailoredResume tailored(String state) {
        TailoredResume tailored = new TailoredResume();
        tailored.setAnalysis(analysis("READY", 68));
        tailored.setResumeVersion(analysis("READY", 68).getResumeVersion());
        tailored.setState(state);
        return tailored;
    }
}