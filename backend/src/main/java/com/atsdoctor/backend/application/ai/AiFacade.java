package com.atsdoctor.backend.application.ai;

import com.atsdoctor.backend.application.config.AtsDoctorProperties;
import com.atsdoctor.backend.infrastructure.ai.AiProvider;
import com.atsdoctor.backend.infrastructure.ai.AiProviderException;
import com.atsdoctor.backend.infrastructure.ai.ProviderRequest;
import com.atsdoctor.backend.infrastructure.ai.ProviderResponse;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ONLY app-facing entry point for AI calls (PRD §6.1, DEC-002/003).
 *
 * <p>Implements FEAT-005/TASK-016: per-profile model resolution, retries with
 * exponential backoff bounded by {@code ats.doctor.ai.timeout-seconds}, a single
 * configured fallback per profile (PRD §6.4), dev/test degradation to a labeled
 * stub result, and an ai_runs recorder hook invoked for every execution.
 */
@Service
public class AiFacade implements AIService {

    private final TaskRouter taskRouter;
    private final PromptService promptService;
    private final ModelProfileResolver profileResolver;
    private final List<AiProvider> providers;
    private final AiRunRecorder recorder;
    private final AtsDoctorProperties properties;

    public AiFacade(TaskRouter taskRouter, PromptService promptService,
                    ModelProfileResolver profileResolver, List<AiProvider> providers,
                    AiRunRecorder recorder, AtsDoctorProperties properties) {
        this.taskRouter = taskRouter;
        this.promptService = promptService;
        this.profileResolver = profileResolver;
        this.providers = providers;
        this.recorder = recorder;
        this.properties = properties;
    }

    @Override
    public AiResult generate(AiRequest request) {
        ModelProfile profile = taskRouter.profileFor(request.task()).orElse(request.task().defaultProfile());
        return generateWithProfile(request, profile, true);
    }

    private AiResult generateWithProfile(AiRequest request, ModelProfile profile, boolean primary) {
        ProfileConfig config = profileResolver.resolve(profile);
        AiProvider provider = activeProvider();
        AiRequest effective = new AiRequest(
                request.task(), promptService.render(request.task(), request.variables()), request.variables());

        long deadline = System.nanoTime() + properties.ai().timeoutSeconds() * 1_000_000_000L;
        String lastError = null;
        int attempt = 0;

        while (attempt < properties.ai().maxRetries() && System.nanoTime() < deadline) {
            attempt++;
            long start = System.nanoTime();
            try {
                ProviderResponse response = provider.complete(new ProviderRequest(effective, config.model()));
                long latencyMs = (System.nanoTime() - start) / 1_000_000;
                recorder.record(toRecord(request, profile, response,
                        primary ? "success" : "fallback", null, latencyMs));
                return buildResult(request, profile, response, latencyMs, attempt, false, primary);
            } catch (AiProviderException ex) {
                long latencyMs = (System.nanoTime() - start) / 1_000_000;
                lastError = ex.getMessage();
                recorder.record(toRecord(request, profile, null, "failed", lastError, latencyMs));
                if (attempt < properties.ai().maxRetries() && System.nanoTime() < deadline) {
                    sleepBounded(backoffMs(attempt), deadline);
                }
            }
        }

        if (config.fallback() != null) {
            recorder.record(toRecord(request, profile, null, "failed",
                    lastError + " (falling back to profile " + config.fallback().profileName() + ")", 0));
            return generateWithProfile(request, config.fallback(), false);
        }

        if (properties.ai().degradeToStub()) {
            return degradeToStub(request, profile, lastError);
        }

        throw new AiUnavailableException(request.task(), profile.profileName(), lastError);
    }

    private AiResult buildResult(AiRequest request, ModelProfile profile, ProviderResponse response,
                                 long latencyMs, int attempt, boolean degraded, boolean primary) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("profile", profile.profileName());
        metadata.put("prompt_version", request.task().promptVersion());
        metadata.put("attempts", attempt);
        metadata.put("fallback_used", !primary);
        if (degraded) {
            metadata.put("degraded", true);
        }
        return new AiResult(request.task(), response.output(), response.provider(), response.model(),
                latencyMs, metadata);
    }

    private AiResult degradeToStub(AiRequest request, ModelProfile profile, String reason) {
        String output = """
                {"_stub": true, "task": "%s", "degraded": true, "note": "Degraded to stub - provider unavailable (%s)"}
                """.formatted(request.task().id(), reason).trim();
        recorder.record(new AiRunRecord(request.task(), "stub", "stub", null, profile.profileName(),
                request.task().promptVersion(), sha256(request.input()), output, "fallback", reason, 0, null, null));
        return new AiResult(request.task(), output, "stub", "stub", 0, Map.of(
                "profile", profile.profileName(),
                "degraded", true,
                "reason", String.valueOf(reason)));
    }

    private AiRunRecord toRecord(AiRequest request, ModelProfile profile, ProviderResponse response,
                                 String status, String error, long latencyMs) {
        return new AiRunRecord(
                request.task(),
                response != null ? response.provider() : activeProvider().name(),
                response != null ? response.model() : null,
                null,
                profile.profileName(),
                request.task().promptVersion(),
                sha256(request.input()),
                response != null ? response.output() : null,
                status,
                error,
                latencyMs,
                null,
                null);
    }

    private AiProvider activeProvider() {
        return providers.stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No AI provider configured"));
    }

    private long backoffMs(int attempt) {
        return properties.ai().backoffBaseMs() * (1L << (attempt - 1));
    }

    private void sleepBounded(long backoffMs, long deadline) {
        long remainingMs = (deadline - System.nanoTime()) / 1_000_000;
        long sleepMs = Math.min(backoffMs, Math.max(0, remainingMs));
        if (sleepMs > 0) {
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
