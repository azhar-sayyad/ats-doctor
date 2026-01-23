package com.atsdoctor.backend.infrastructure.ai;

import com.atsdoctor.backend.application.ai.AiRequest;

/**
 * Provider-bound call. {@code model} is the resolved per-profile model override
 * (from deployment config), or {@code null} to use the provider's configured default.
 */
public record ProviderRequest(AiRequest request, String model) {}
