package com.atsdoctor.backend.application.ai;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves {@link ModelProfile} → provider/model/fallback (TASK-012).
 *
 * <p>Every profile can be overridden via environment variables:
 * {@code PROFILE_<NAME>_PROVIDER}, {@code PROFILE_<NAME>_MODEL} and
 * {@code PROFILE_<NAME>_FALLBACK} (e.g. {@code PROFILE_QUALITY_FALLBACK=local}).
 * Defaults come from configuration (application.yml {@code ats.doctor.ai.fallback.*}).
 * Unknown profile names and self-fallbacks are rejected at startup (fail fast).
 */
@Component
public class ModelProfileResolver {

    public static final String DEFAULT_PROVIDER = "omniroute";

    private final Map<ModelProfile, ProfileConfig> resolved = new EnumMap<>(ModelProfile.class);

    public ModelProfileResolver(Environment environment) {
        for (ModelProfile profile : ModelProfile.values()) {
            String name = profile.profileName().toUpperCase(Locale.ROOT);
            String provider = firstNonBlank(
                    environment.getProperty("PROFILE_" + name + "_PROVIDER"),
                    DEFAULT_PROVIDER);
            String model = firstNonBlank(
                    environment.getProperty("PROFILE_" + name + "_MODEL"),
                    null);
            ModelProfile fallback = resolveFallback(environment, profile, name);
            resolved.put(profile, ProfileConfig.of(provider, model, fallback));
        }
    }

    public ProfileConfig resolve(ModelProfile profile) {
        ProfileConfig config = resolved.get(profile);
        if (config == null) {
            throw new IllegalStateException("No configuration resolved for profile: " + profile.profileName());
        }
        return config;
    }

    private ModelProfile resolveFallback(Environment environment, ModelProfile profile, String name) {
        String raw = firstNonBlank(
                environment.getProperty("PROFILE_" + name + "_FALLBACK"),
                environment.getProperty("ats.doctor.ai.fallback." + profile.profileName()),
                null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ModelProfile fallback = ModelProfile.fromName(raw.trim())
                .orElseThrow(() -> new IllegalStateException(
                        "Unknown fallback profile '" + raw + "' for " + profile.profileName()
                                + " (PROFILE_" + name + "_FALLBACK)"));
        if (fallback == profile) {
            throw new IllegalStateException(
                    "Self-fallback rejected for profile " + profile.profileName() + " (PROFILE_" + name + "_FALLBACK)");
        }
        return fallback;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
