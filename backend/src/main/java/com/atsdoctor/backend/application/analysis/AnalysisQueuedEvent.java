package com.atsdoctor.backend.application.analysis;

import java.util.UUID;

/** Published after an analysis is queued; the async pipeline picks it up. */
public record AnalysisQueuedEvent(UUID analysisId) {
}
