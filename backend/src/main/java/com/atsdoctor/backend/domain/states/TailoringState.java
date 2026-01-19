package com.atsdoctor.backend.domain.states;

public enum TailoringState {
    QUEUED,
    GENERATING,
    VALIDATING,
    READY,
    NEEDS_REVIEW,
    APPROVED
}
