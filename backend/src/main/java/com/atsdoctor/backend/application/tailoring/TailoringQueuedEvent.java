package com.atsdoctor.backend.application.tailoring;

import java.util.UUID;

/** Published after the tailoring row is committed (QUEUED → async pipeline). */
public record TailoringQueuedEvent(UUID tailoredResumeId) {
}