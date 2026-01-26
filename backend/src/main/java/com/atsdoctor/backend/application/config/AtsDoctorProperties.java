package com.atsdoctor.backend.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ats.doctor")
public record AtsDoctorProperties(Ai ai, Persistence persistence) {

    public record Ai(
            String mode,
            long timeoutSeconds,
            int maxRetries,
            long backoffBaseMs,
            boolean degradeToStub) {}

    public record Persistence(boolean enabled) {}
}
