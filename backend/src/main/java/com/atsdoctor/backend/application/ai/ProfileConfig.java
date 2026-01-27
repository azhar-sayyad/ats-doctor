package com.atsdoctor.backend.application.ai;

/**
 * Resolved per-profile configuration (TASK-012). Exact model names live ONLY in
 * configuration — environment overrides (PROFILE_&lt;NAME&gt;_PROVIDER/_MODEL/_FALLBACK)
 * or the deployment default — never in application code (PRD §6.2, DEC-005).
 *
 * @param provider gateway/provider name (default "omniroute"; "stub" in dev)
 * @param model    exact model to use for this profile, or {@code null} to let the
 *                 active provider use its configured default
 * @param fallback single fallback profile per PRD §6.4, or {@code null} if none
 */
public record ProfileConfig(String provider, String model, ModelProfile fallback) {

    public static ProfileConfig of(String provider, String model, ModelProfile fallback) {
        return new ProfileConfig(provider, model, fallback);
    }
}
