package com.atsdoctor.backend.infrastructure.persistence;

import com.atsdoctor.backend.application.ai.AiRunRecord;
import com.atsdoctor.backend.application.ai.AiRunRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Recording is disabled when persistence is off (default dev boot without a DB).
 * Dropped records are logged at trace so callers are never affected.
 */
@Component
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "false", matchIfMissing = true)
public class NoopAiRunRecorder implements AiRunRecorder {

    private static final Logger log = LoggerFactory.getLogger(NoopAiRunRecorder.class);

    @Override
    public void record(AiRunRecord record) {
        log.trace("ai_runs recording disabled - skipped task={} status={}", record.task(), record.status());
    }
}
