package com.atsdoctor.backend;

import com.atsdoctor.backend.application.ai.AiRequest;
import com.atsdoctor.backend.application.ai.AiRunRecorder;
import com.atsdoctor.backend.application.ai.AiTask;
import com.atsdoctor.backend.application.ai.AIService;
import com.atsdoctor.backend.infrastructure.persistence.AiRun;
import com.atsdoctor.backend.infrastructure.persistence.AiRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-022/023 — validates the Flyway migration, the JPA entity/repository, and
 * the recorder end-to-end against a real PostgreSQL. Skipped automatically when
 * Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class AiRunsRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg15")
            .withDatabaseName("ats")
            .withUsername("ats")
            .withPassword("ats");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("ats.doctor.persistence.enabled", () -> "true");
    }

    @Autowired
    private AiRunRepository repository;

    @Autowired
    private AiRunRecorder recorder;

    @Autowired
    private AIService aiService;

    @Test
    void flyway_applied_v1_and_repository_round_trips() {
        AiRun run = new AiRun();
        run.setTask("jd_parser");
        run.setProvider("omniroute");
        run.setModel("deepseek-v4-flash");
        run.setProfile("cheap");
        run.setPromptVersion("jd-parser-v1");
        run.setInputHash("deadbeef");
        run.setOutput("{\"job\": {\"title\": \"Engineer\"}}");
        run.setStatus("success");
        run.setLatencyMs(42);
        repository.save(run);

        List<AiRun> found = repository.findByTaskOrderByCreatedAtDesc("jd_parser");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getInputHash()).isEqualTo("deadbeef");
        assertThat(found.get(0).getId()).isNotNull();
    }

    @Test
    void recorder_persists_a_full_generation() {
        aiService.generate(AiRequest.of(AiTask.RESUME_PARSER, "Jane Doe, Senior Engineer"));

        List<AiRun> runs = repository.findByTaskOrderByCreatedAtDesc("resume_parser");
        assertThat(runs).isNotEmpty();
        AiRun last = runs.get(0);
        assertThat(last.getProvider()).isEqualTo("stub");
        assertThat(last.getStatus()).isEqualTo("success");
        assertThat(last.getPromptVersion()).isEqualTo("resume-parser-v1");
        assertThat(last.getOutput()).contains("_stub");
    }
}
