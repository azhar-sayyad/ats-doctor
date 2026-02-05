package com.atsdoctor.backend;

import com.atsdoctor.backend.application.ai.AiRequest;
import com.atsdoctor.backend.application.ai.AiResult;
import com.atsdoctor.backend.application.ai.AiTask;
import com.atsdoctor.backend.application.ai.AIService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** TASK-025 — the app boots and the AI layer runs a stub generation. */
@SpringBootTest
class SmokeTest {

    @Autowired
    private AIService aiService;

    @Test
    void boots_and_runs_a_stub_generation() {
        AiResult result = aiService.generate(
                AiRequest.of(AiTask.RESUME_PARSER, "Jane Doe, Senior Engineer"));

        assertThat(result.provider()).isEqualTo("stub");
        assertThat(result.output()).contains("_stub");
        assertThat(result.task()).isEqualTo(AiTask.RESUME_PARSER);
    }
}
