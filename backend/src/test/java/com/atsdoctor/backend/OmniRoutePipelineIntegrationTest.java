package com.atsdoctor.backend;

import com.atsdoctor.backend.application.ai.AiRequest;
import com.atsdoctor.backend.application.ai.AiTask;
import com.atsdoctor.backend.infrastructure.ai.ProviderRequest;
import com.atsdoctor.backend.infrastructure.ai.StubAiProvider;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChangeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end proof of the omniroute AI path (PRD §6.3, DEC-024): the app boots
 * with {@code ats.doctor.ai.mode=omniroute} and calls an OpenAI-compatible
 * gateway over real HTTP (an in-test stand-in that mirrors the stub provider's
 * deterministic outputs), then runs resume → job → analysis → tailor → approve
 * → export against a real PostgreSQL. Asserts the audit trail records
 * {@code provider=omniroute} (never stub) — i.e. the Spring AI → gateway
 * plumbing actually executes. Skipped automatically when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class OmniRoutePipelineIntegrationTest extends PipelineIntegrationTestBase {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg15")
            .withDatabaseName("ats")
            .withUsername("ats")
            .withPassword("ats");

    private static final int PORT;
    private static final StubAiProvider SHAPING = new StubAiProvider();

    static {
        PORT = startMockGateway();
    }

    @DynamicPropertySource
    static void config(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("ats.doctor.persistence.enabled", () -> "true");
        registry.add("ats.doctor.ai.mode", () -> "omniroute");
        registry.add("spring.ai.openai.base-url", () -> "http://localhost:" + PORT);
        registry.add("spring.ai.openai.api-key", () -> "dev-mock");
        registry.add("ats.doctor.storage.resume-dir", () -> "target/test-omniroute-resumes");
        registry.add("ats.doctor.storage.jobs-dir", () -> "target/test-omniroute-jobs");
        registry.add("ats.doctor.storage.exports-dir", () -> "target/test-omniroute-exports");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TailoredChangeRepository tailoredChangeRepository;

    @Test
    void full_pipeline_runs_through_the_omniroute_gateway() throws Exception {
        String resumeVersionId = uploadResumeAndAwaitReady();
        String jobId = createJobAndAwaitReady();

        MvcResult create = mvc.perform(post("/api/v1/analyses")
                        .contentType("application/json")
                        .content("{\"job_id\":\"" + jobId + "\",\"resume_version_id\":\"" + resumeVersionId + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String analysisId = mapper.readTree(create.getResponse().getContentAsString()).get("id").asText();
        JsonNode analysis = awaitAnalysis(analysisId);
        assertThat(analysis.get("state").asText()).isEqualTo("READY");
        assertThat(analysis.get("score").asInt()).isBetween(0, 100);

        MvcResult tailor = mvc.perform(post("/api/v1/analyses/" + analysisId + "/tailor"))
                .andExpect(status().isOk())
                .andReturn();
        String tailoredId = mapper.readTree(tailor.getResponse().getContentAsString()).get("id").asText();
        JsonNode ready = awaitTailored(tailoredId);
        assertThat(ready.get("state").asText()).isEqualTo("READY");

        // The audit trail must show every AI call went through the gateway.
        Integer omnirouteRuns = jdbc.queryForObject(
                "SELECT count(*) FROM ai_runs WHERE provider = 'omniroute'", Integer.class);
        Integer stubRuns = jdbc.queryForObject(
                "SELECT count(*) FROM ai_runs WHERE provider = 'stub'", Integer.class);
        assertThat(omnirouteRuns).isGreaterThanOrEqualTo(3); // resume + jd + requirements (+ tailors)
        assertThat(stubRuns).isZero();

        // Full review → approve → export proves the downstream pipeline holds.
        var changes = tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(UUID.fromString(tailoredId));
        assertThat(changes).isNotEmpty();
        mvc.perform(post("/api/v1/tailored/" + tailoredId + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
        mvc.perform(post("/api/v1/tailored/" + tailoredId + "/approve"))
                .andExpect(status().isConflict()); // unresolved rewrites → 409
        for (var row : tailoredChangeRepository
                .findByTailoredResumeIdOrderByCreatedAtAsc(UUID.fromString(tailoredId))) {
            mvc.perform(post("/api/v1/tailored/" + tailoredId + "/changes/" + row.getId())
                            .contentType("application/json")
                            .content("{\"action\":\"reject\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"));
        }
        mvc.perform(post("/api/v1/tailored/" + tailoredId + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
        mvc.perform(post("/api/v1/tailored/" + tailoredId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));
        mvc.perform(get("/api/v1/tailored/" + tailoredId + "/export/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    // --- in-test OpenAI-compatible gateway ---

    private static int startMockGateway() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/v1/chat/completions", OmniRoutePipelineIntegrationTest::handleChat);
            server.start();
            return server.getAddress().getPort();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start mock gateway", ex);
        }
    }

    private static void handleChat(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        JsonNode request = new ObjectMapper().readTree(body);
        StringBuilder prompt = new StringBuilder();
        for (JsonNode message : request.path("messages")) {
            prompt.append(message.path("content").asText()).append("\n");
        }

        String content;
        try {
            AiRequest aiRequest = toAiRequest(prompt.toString());
            content = SHAPING.complete(new ProviderRequest(aiRequest, "dev-mock-model")).output();
        } catch (Exception ex) {
            content = "{\"_mock_gateway\":true,\"task\":\"unknown\",\"note\":\"mock failed: "
                    + ex.getMessage() + "\"}";
        }

        String response = "{\"id\":\"chatcmpl-mock\",\"object\":\"chat.completion\",\"created\":0,"
                + "\"model\":\"" + request.path("model").asText("dev-mock-model") + "\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":"
                + json(content) + "},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":0,\"completion_tokens\":0,\"total_tokens\":0}}";

        byte[] out = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, out.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
        }
    }

    /**
     * Reconstruct an AiRequest from the rendered prompt (same template markers
     * as resources/prompts/*) so the mock can reuse StubAiProvider's
     * deterministic, schema-valid shaping logic.
     */
    private static AiRequest toAiRequest(String prompt) {
        if (prompt.contains("You are a resume parser")) {
            return AiRequest.of(AiTask.RESUME_PARSER, label(prompt, "Resume text:\n"));
        }
        if (prompt.contains("You are a job-description parser")) {
            return AiRequest.of(AiTask.JD_PARSER, label(prompt, "JD text:\n"));
        }
        if (prompt.contains("You extract discrete requirements")) {
            return AiRequest.of(AiTask.REQUIREMENT_EXTRACTION, label(prompt, "Structured JD:\n"));
        }
        if (prompt.contains("Rewrite a single resume bullet")) {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("original_bullet", label(prompt, "Original bullet:\n"));
            variables.put("jd_requirements", label(prompt, "Target requirements:\n"));
            variables.put("jd_keywords", label(prompt, "Keywords to align with (only where they are already supported by evidence):\n"));
            variables.put("resume_evidence", label(prompt, "Evidence (facts allowed to reference):\n"));
            return new AiRequest(AiTask.RESUME_TAILORING, prompt, variables);
        }
        if (prompt.contains("Validate that a tailored resume")) {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("original_resume", label(prompt, "Original resume:\n"));
            variables.put("tailored_resume", label(prompt, "Tailored resume:\n"));
            variables.put("resume_evidence", label(prompt, "Evidence (facts the tailored text may reference):\n"));
            variables.put("claim_category", label(prompt, "Claim category under validation:\n"));
            return new AiRequest(AiTask.FACT_VALIDATION, prompt, variables);
        }
        throw new IllegalArgumentException("Mock gateway could not route prompt");
    }

    private static final String[] NEXT_SECTIONS = {
            "Rules:",
            "Target requirements:",
            "Keywords to align with",
            "Evidence (facts allowed to reference):",
            "Evidence (facts the tailored text may reference):",
            "Claim category under validation:",
            "Tailored resume:",
            "Schema per row:"
    };

    private static String label(String prompt, String marker) {
        int start = prompt.indexOf(marker);
        if (start < 0) {
            return "";
        }
        String rest = prompt.substring(start + marker.length()).trim();
        int end = -1;
        for (String section : NEXT_SECTIONS) {
            int idx = rest.indexOf("\n\n" + section);
            if (idx >= 0 && (end < 0 || idx < end)) {
                end = idx;
            }
        }
        return end < 0 ? rest : rest.substring(0, end).trim();
    }

    private static String json(String value) {
        return new ObjectMapper().valueToTree(value).toString();
    }
}
