package com.atsdoctor.backend.infrastructure.persistence;

import com.atsdoctor.backend.application.ai.AiRunRecord;
import com.atsdoctor.backend.application.ai.AiRunRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Persists every AI execution as an ai_runs row (TASK-023). Recording failures
 * never break the caller — exceptions are caught and logged.
 */
@Component
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class JpaAiRunRecorder implements AiRunRecorder {

    private static final Logger log = LoggerFactory.getLogger(JpaAiRunRecorder.class);

    private final AiRunRepository repository;

    public JpaAiRunRecorder(AiRunRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AiRunRecord record) {
        try {
            AiRun entity = new AiRun();
            entity.setTask(record.task() == null ? null : record.task().id());
            entity.setProvider(record.provider());
            entity.setModel(record.model());
            entity.setModelVersion(record.modelVersion());
            entity.setProfile(record.profile());
            entity.setPromptVersion(record.promptVersion());
            entity.setInputHash(record.inputHash());
            entity.setOutput(record.output());
            entity.setStatus(record.status());
            entity.setError(record.error());
            entity.setLatencyMs((int) record.latencyMs());
            entity.setInputTokens(record.inputTokens());
            entity.setOutputTokens(record.outputTokens());
            entity.setCreatedAt(Instant.now());
            repository.save(entity);
        } catch (RuntimeException ex) {
            log.error("Failed to record ai_runs entry (task={} status={}): {}",
                    record.task(), record.status(), ex.getMessage(), ex);
        }
    }
}
