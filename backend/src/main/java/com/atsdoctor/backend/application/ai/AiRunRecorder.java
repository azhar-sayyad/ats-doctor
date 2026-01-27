package com.atsdoctor.backend.application.ai;

/**
 * Persistence seam for ai_runs (FEAT-008). Implementations must never let a
 * recording failure break the caller (TASK-023).
 */
public interface AiRunRecorder {

    void record(AiRunRecord record);
}
