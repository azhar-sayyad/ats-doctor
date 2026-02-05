package com.atsdoctor.backend;

import com.atsdoctor.backend.domain.states.AnalysisState;
import com.atsdoctor.backend.domain.states.JobState;
import com.atsdoctor.backend.domain.states.ResumeState;
import com.atsdoctor.backend.domain.states.StateMachines;
import com.atsdoctor.backend.domain.states.TailoringState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TASK-021 — state machine unit tests (PRD §5.8). */
class StateMachineTest {

    @Test
    void resume_valid_chain_and_failed_path() {
        var machine = StateMachines.resume();
        machine.transition(ResumeState.UPLOADED, ResumeState.EXTRACTING);
        machine.transition(ResumeState.EXTRACTING, ResumeState.PARSING);
        machine.transition(ResumeState.PARSING, ResumeState.READY);
        assertThat(machine.canTransition(ResumeState.READY, ResumeState.FAILED)).isTrue();
        machine.transition(ResumeState.UPLOADED, ResumeState.FAILED);
        assertThatThrownBy(() -> machine.transition(ResumeState.FAILED, ResumeState.READY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resume_rejects_invalid_transitions() {
        var machine = StateMachines.resume();
        assertThatThrownBy(() -> machine.transition(ResumeState.UPLOADED, ResumeState.PARSING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UPLOADED → PARSING");
        assertThatThrownBy(() -> machine.transition(ResumeState.PARSING, ResumeState.UPLOADED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void job_valid_chain_and_terminal_failed() {
        var machine = StateMachines.job();
        machine.transition(JobState.CREATED, JobState.EXTRACTING);
        machine.transition(JobState.EXTRACTING, JobState.PARSING);
        machine.transition(JobState.PARSING, JobState.READY);
        machine.transition(JobState.READY, JobState.FAILED);
        assertThatThrownBy(() -> machine.transition(JobState.FAILED, JobState.CREATED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void job_failed_is_reachable_from_each_processing_state() {
        var machine = StateMachines.job();
        assertThat(machine.canTransition(JobState.CREATED, JobState.FAILED)).isTrue();
        assertThat(machine.canTransition(JobState.EXTRACTING, JobState.FAILED)).isTrue();
        assertThat(machine.canTransition(JobState.PARSING, JobState.FAILED)).isTrue();
        assertThat(machine.canTransition(JobState.READY, JobState.FAILED)).isTrue();
    }

    @Test
    void analysis_valid_chain_and_terminal_failed() {
        var machine = StateMachines.analysis();
        machine.transition(AnalysisState.QUEUED, AnalysisState.MATCHING);
        machine.transition(AnalysisState.MATCHING, AnalysisState.SCORING);
        machine.transition(AnalysisState.SCORING, AnalysisState.READY);
        assertThatThrownBy(() -> machine.transition(AnalysisState.READY, AnalysisState.MATCHING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tailoring_valid_chain_to_approved() {
        var machine = StateMachines.tailoring();
        machine.transition(TailoringState.QUEUED, TailoringState.GENERATING);
        machine.transition(TailoringState.GENERATING, TailoringState.VALIDATING);
        machine.transition(TailoringState.VALIDATING, TailoringState.READY);
        machine.transition(TailoringState.READY, TailoringState.NEEDS_REVIEW);
        machine.transition(TailoringState.NEEDS_REVIEW, TailoringState.APPROVED);
        assertThat(machine.canTransition(TailoringState.APPROVED, TailoringState.READY)).isFalse();
        assertThatThrownBy(() -> machine.transition(TailoringState.READY, TailoringState.QUEUED))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
