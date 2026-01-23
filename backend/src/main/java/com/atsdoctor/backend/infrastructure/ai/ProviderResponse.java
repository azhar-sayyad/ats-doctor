package com.atsdoctor.backend.infrastructure.ai;

/**
 * A successful provider completion. {@code provider}/{@code model} are the labels
 * recorded in ai_runs and surfaced in results (PRD §6.7).
 */
public record ProviderResponse(String output, String provider, String model) {}
