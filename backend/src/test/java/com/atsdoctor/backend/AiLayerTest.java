package com.atsdoctor.backend;

import com.atsdoctor.backend.application.ai.AiFacade;
import com.atsdoctor.backend.application.ai.AiRequest;
import com.atsdoctor.backend.application.ai.AiResult;
import com.atsdoctor.backend.application.ai.AiRunRecord;
import com.atsdoctor.backend.application.ai.AiRunRecorder;
import com.atsdoctor.backend.application.ai.AiTask;
import com.atsdoctor.backend.application.ai.AiUnavailableException;
import com.atsdoctor.backend.application.ai.ModelProfile;
import com.atsdoctor.backend.application.ai.ModelProfileResolver;
import com.atsdoctor.backend.application.ai.ProfileConfig;
import com.atsdoctor.backend.application.ai.PromptService;
import com.atsdoctor.backend.application.ai.TaskRouter;
import com.atsdoctor.backend.application.config.AtsDoctorProperties;
import com.atsdoctor.backend.infrastructure.ai.AiProvider;
import com.atsdoctor.backend.infrastructure.ai.AiProviderException;
import com.atsdoctor.backend.infrastructure.ai.ProviderRequest;
import com.atsdoctor.backend.infrastructure.ai.ProviderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-017 unit tests for application/ai: router, profiles, prompts, facade
 * retry/fallback/degradation, and the recorder hook (TASK-023).
 */
class AiLayerTest {

    // ── TaskRouter (TASK-013) ──────────────────────────────────────────────

    @Test
    void router_maps_tasks_to_profiles_from_ai_tasks_yml() {
        TaskRouter router = new TaskRouter(new ClassPathResource("ai-tasks.yml"));
        assertThat(router.profileFor(AiTask.RESUME_PARSER)).contains(ModelProfile.CHEAP);
        assertThat(router.profileFor(AiTask.JD_PARSER)).contains(ModelProfile.CHEAP);
        assertThat(router.profileFor(AiTask.REQUIREMENT_EXTRACTION)).contains(ModelProfile.CHEAP);
        assertThat(router.profileFor(AiTask.RESUME_TAILORING)).contains(ModelProfile.QUALITY);
        assertThat(router.profileFor(AiTask.FACT_VALIDATION)).contains(ModelProfile.QUALITY);
        assertThat(router.profileFor(AiTask.EMBEDDING)).isEmpty(); // unconfigured → default
    }

    @Test
    void router_resolves_task_ids_and_rejects_deterministic_and_unknown_tasks() {
        TaskRouter router = new TaskRouter(new ClassPathResource("ai-tasks.yml"));
        assertThat(router.profileForTaskId("resume_parser")).isEqualTo(ModelProfile.CHEAP);
        assertThat(router.isRoutable("resume_parser")).isTrue();
        assertThat(router.isRoutable("embedding")).isTrue();

        for (String deterministic : List.of("exact_matching", "scoring", "extraction", "export")) {
            assertThat(router.isRoutable(deterministic)).as(deterministic).isFalse();
            assertThatThrownBy(() -> router.profileForTaskId(deterministic))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not AI-routable");
        }
        assertThatThrownBy(() -> router.profileForTaskId("bogus_task"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown AI task");
    }

    @Test
    void router_updateProfile_applies_runtime_changes() {
        TaskRouter router = new TaskRouter(new ClassPathResource("ai-tasks.yml"));
        router.updateProfile(AiTask.RESUME_PARSER, ModelProfile.QUALITY);
        assertThat(router.profileFor(AiTask.RESUME_PARSER)).contains(ModelProfile.QUALITY);
    }

    // ── ModelProfileResolver (TASK-012) ────────────────────────────────────

    @Test
    void profiles_resolve_defaults_and_env_overrides() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("PROFILE_QUALITY_MODEL", "q-model");
        env.setProperty("PROFILE_QUALITY_FALLBACK", "local");
        env.setProperty("PROFILE_LOCAL_PROVIDER", "ollama");
        ModelProfileResolver resolver = new ModelProfileResolver(env);

        ProfileConfig quality = resolver.resolve(ModelProfile.QUALITY);
        assertThat(quality.provider()).isEqualTo(ModelProfileResolver.DEFAULT_PROVIDER);
        assertThat(quality.model()).isEqualTo("q-model");
        assertThat(quality.fallback()).isEqualTo(ModelProfile.LOCAL);

        ProfileConfig local = resolver.resolve(ModelProfile.LOCAL);
        assertThat(local.provider()).isEqualTo("ollama");
        assertThat(local.fallback()).isNull();
    }

    @Test
    void profiles_reject_unknown_fallback_profile() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("PROFILE_CHEAP_FALLBACK", "bogus");
        assertThatThrownBy(() -> new ModelProfileResolver(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown fallback profile 'bogus'");
    }

    @Test
    void profiles_reject_self_fallback() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("PROFILE_CHEAP_FALLBACK", "cheap");
        assertThatThrownBy(() -> new ModelProfileResolver(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Self-fallback");
    }

    // ── PromptService (TASK-014) ───────────────────────────────────────────

    @Test
    void prompts_render_variables_and_surface_missing_ones() {
        PromptService prompts = new PromptService();
        String rendered = prompts.render(AiTask.RESUME_PARSER, Map.of("resume_text", "Jane Doe"));
        assertThat(rendered).contains("Jane Doe").doesNotContain("{{");

        assertThatThrownBy(() -> prompts.render(AiTask.RESUME_PARSER, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing variables")
                .hasMessageContaining("resume_text");
    }

    // ── AiFacade (TASK-016) ────────────────────────────────────────────────

    private final FakeRecorder recorder = new FakeRecorder();

    @Test
    void facade_success_path_returns_provider_result_and_records() {
        FakeProvider provider = new FakeProvider("fake");
        AiResult result = facade(true, provider).generate(
                AiRequest.of(AiTask.RESUME_PARSER, "Jane Doe"));

        assertThat(result.provider()).isEqualTo("fake");
        assertThat(result.output()).contains("ok-resume_parser");
        assertThat(result.metadata()).containsEntry("profile", "cheap");
        assertThat(result.metadata()).containsEntry("prompt_version", "resume-parser-v1");
        assertThat(result.metadata()).containsEntry("attempts", 1);
        assertThat(recorder.records()).hasSize(1);
        assertThat(recorder.records().get(0).status()).isEqualTo("success");
        assertThat(recorder.records().get(0).inputHash()).isNotBlank();
    }

    @Test
    void facade_retries_with_backoff_then_succeeds() {
        FakeProvider provider = new FakeProvider("fake").fail("boom");
        AiResult result = facade(true, provider).generate(
                AiRequest.of(AiTask.RESUME_PARSER, "x"));

        assertThat(provider.calls()).isEqualTo(2);
        assertThat(result.metadata()).containsEntry("attempts", 2);
        assertThat(recorder.records()).hasSize(2);
        assertThat(recorder.records().get(0).status()).isEqualTo("failed");
        assertThat(recorder.records().get(1).status()).isEqualTo("success");
    }

    @Test
    void facade_uses_single_configured_fallback_after_primary_failure() {
        // resume_tailoring → quality → fallback local (via PROFILE_QUALITY_FALLBACK).
        FakeProvider provider = new FakeProvider("fake").fail("boom").fail("boom").fail("boom");
        AiResult result = facade(true, provider).generate(
                AiRequest.of(AiTask.RESUME_TAILORING, "x"));

        assertThat(provider.calls()).isEqualTo(4);
        assertThat(result.metadata()).containsEntry("fallback_used", true);
        assertThat(result.metadata()).containsEntry("profile", "local");
        assertThat(recorder.records().stream().map(AiRunRecord::status))
                .containsExactly("failed", "failed", "failed", "failed", "fallback");
    }

    @Test
    void facade_degrades_to_labeled_stub_on_total_failure() {
        FakeProvider provider = new FakeProvider("fake").fail("boom").fail("boom").fail("boom");
        AiResult result = facade(true, provider).generate(
                AiRequest.of(AiTask.RESUME_PARSER, "x"));

        assertThat(result.provider()).isEqualTo("stub");
        assertThat(result.output()).contains("_stub");
        assertThat(result.metadata()).containsEntry("degraded", true);
        assertThat(recorder.records().get(recorder.records().size() - 1).status()).isEqualTo("fallback");
    }

    @Test
    void facade_throws_controlled_error_when_degradation_disabled() {
        FakeProvider provider = new FakeProvider("fake").fail("boom").fail("boom").fail("boom");
        assertThatThrownBy(() -> facade(false, provider).generate(
                AiRequest.of(AiTask.RESUME_PARSER, "x")))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("resume_parser");
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private AiFacade facade(boolean degrade, AiProvider... providers) {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("PROFILE_QUALITY_FALLBACK", "local");
        return new AiFacade(
                new TaskRouter(new ClassPathResource("ai-tasks.yml")),
                new PromptService(),
                new ModelProfileResolver(env),
                List.of(providers),
                recorder,
                properties(degrade));
    }

    private AtsDoctorProperties properties(boolean degrade) {
        return new AtsDoctorProperties(
                new AtsDoctorProperties.Ai("stub", 5L, 3, 1L, degrade),
                new AtsDoctorProperties.Persistence(false));
    }

    private static final class FakeProvider implements AiProvider {
        private final String name;
        private final Deque<RuntimeException> failures = new ArrayDeque<>();
        private final Deque<ProviderResponse> responses = new ArrayDeque<>();
        private int calls;

        FakeProvider(String name) {
            this.name = name;
        }

        FakeProvider fail(String message) {
            failures.add(new AiProviderException(message));
            return this;
        }

        @Override
        public String name() {
            return name;
        }

        int calls() {
            return calls;
        }

        @Override
        public ProviderResponse complete(ProviderRequest request) throws AiProviderException {
            calls++;
            if (!failures.isEmpty()) {
                throw failures.removeFirst();
            }
            if (!responses.isEmpty()) {
                return responses.removeFirst();
            }
            return new ProviderResponse("ok-" + request.request().task().id(), name, "m");
        }
    }

    private static final class FakeRecorder implements AiRunRecorder {
        private final List<AiRunRecord> records = new ArrayList<>();

        List<AiRunRecord> records() {
            return records;
        }

        @Override
        public void record(AiRunRecord record) {
            records.add(record);
        }
    }
}
