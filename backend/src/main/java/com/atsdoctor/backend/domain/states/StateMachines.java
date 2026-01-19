package com.atsdoctor.backend.domain.states;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Processing state machines per PRD §5.8 (FEAT-007, TASK-020).
 *
 * <p>{@code transition(from, to)} returns the target state and rejects invalid
 * moves with an {@link IllegalArgumentException}. {@code FAILED} is terminal for
 * Resume/Job/Analysis. Tailoring has no FAILED state in §5.8 — failures surface
 * through error handling (TASK-089).
 */
public final class StateMachines {

    private StateMachines() {
    }

    /** Resume: UPLOADED → EXTRACTING → PARSING → READY / FAILED. */
    public static StateMachine<ResumeState> resume() {
        return new StateMachine<>(Map.of(
                ResumeState.UPLOADED, Set.of(ResumeState.EXTRACTING, ResumeState.FAILED),
                ResumeState.EXTRACTING, Set.of(ResumeState.PARSING, ResumeState.FAILED),
                ResumeState.PARSING, Set.of(ResumeState.READY, ResumeState.FAILED),
                ResumeState.READY, Set.of(ResumeState.FAILED)));
    }

    /** Job: CREATED → EXTRACTING → PARSING → READY / FAILED (incl. FAILED on Job). */
    public static StateMachine<JobState> job() {
        return new StateMachine<>(Map.of(
                JobState.CREATED, Set.of(JobState.EXTRACTING, JobState.FAILED),
                JobState.EXTRACTING, Set.of(JobState.PARSING, JobState.FAILED),
                JobState.PARSING, Set.of(JobState.READY, JobState.FAILED),
                JobState.READY, Set.of(JobState.FAILED)));
    }

    /** Analysis: QUEUED → MATCHING → SCORING → READY / FAILED. Reanalyze re-enters QUEUED from READY or FAILED (TASK-058). */
    public static StateMachine<AnalysisState> analysis() {
        return new StateMachine<>(Map.of(
                AnalysisState.QUEUED, Set.of(AnalysisState.MATCHING, AnalysisState.FAILED),
                AnalysisState.MATCHING, Set.of(AnalysisState.SCORING, AnalysisState.FAILED),
                AnalysisState.SCORING, Set.of(AnalysisState.READY, AnalysisState.FAILED),
                AnalysisState.READY, Set.of(AnalysisState.FAILED, AnalysisState.QUEUED),
                AnalysisState.FAILED, Set.of(AnalysisState.QUEUED)));
    }

    /**
     * Tailoring: QUEUED → GENERATING → VALIDATING → READY → NEEDS_REVIEW → APPROVED.
     * APPROVED is terminal.
     */
    public static StateMachine<TailoringState> tailoring() {
        return new StateMachine<>(Map.of(
                TailoringState.QUEUED, Set.of(TailoringState.GENERATING),
                TailoringState.GENERATING, Set.of(TailoringState.VALIDATING),
                TailoringState.VALIDATING, Set.of(TailoringState.READY),
                TailoringState.READY, Set.of(TailoringState.NEEDS_REVIEW),
                TailoringState.NEEDS_REVIEW, Set.of(TailoringState.APPROVED)));
    }

    /** A directed state machine over an enum. */
    public static final class StateMachine<S extends Enum<S>> {

        private final Map<S, Set<S>> allowed = new HashMap<>();

        private StateMachine(Map<S, Set<S>> transitions) {
            allowed.putAll(transitions);
        }

        public S transition(S from, S to) {
            Set<S> targets = allowed.get(from);
            if (targets == null || !targets.contains(to)) {
                throw new IllegalArgumentException(
                        "Invalid state transition: " + from + " → " + to);
            }
            return to;
        }

        public boolean canTransition(S from, S to) {
            Set<S> targets = allowed.get(from);
            return targets != null && targets.contains(to);
        }
    }
}
