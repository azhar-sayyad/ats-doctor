package com.atsdoctor.backend;

import com.atsdoctor.backend.application.ai.AiRunRecord;
import com.atsdoctor.backend.application.ai.AiTask;
import com.atsdoctor.backend.infrastructure.persistence.AiRun;
import com.atsdoctor.backend.infrastructure.persistence.AiRunRepository;
import com.atsdoctor.backend.infrastructure.persistence.JpaAiRunRecorder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TASK-023 — the recorder never breaks the caller, even when persistence fails. */
class AiRunsRecorderTest {

    @Test
    void recorder_swallows_persistence_failures() {
        AiRunRepository repository = mock(AiRunRepository.class);
        when(repository.save(any())).thenThrow(new RuntimeException("db down"));

        JpaAiRunRecorder recorder = new JpaAiRunRecorder(repository);

        assertThatCode(() -> recorder.record(record())).doesNotThrowAnyException();
    }

    @Test
    void recorder_maps_fields_and_saves() {
        AiRunRepository repository = mock(AiRunRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new JpaAiRunRecorder(repository).record(record());

        verify(repository).save(argThat((AiRun entity) ->
                "resume_parser".equals(entity.getTask())
                        && "stub".equals(entity.getProvider())
                        && "cheap".equals(entity.getProfile())
                        && "resume-parser-v1".equals(entity.getPromptVersion())
                        && "abc".equals(entity.getInputHash())
                        && "success".equals(entity.getStatus())
                        && entity.getLatencyMs() == 12
                        && entity.getCreatedAt() != null));
    }

    private static AiRunRecord record() {
        return new AiRunRecord(
                AiTask.RESUME_PARSER, "stub", "stub", null, "cheap",
                "resume-parser-v1", "abc", "{}", "success", null, 12L, null, null);
    }
}
