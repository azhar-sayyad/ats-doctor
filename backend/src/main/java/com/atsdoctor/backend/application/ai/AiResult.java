package com.atsdoctor.backend.application.ai;

import java.util.Map;

public record AiResult(
        AiTask task,
        String output,
        String provider,
        String model,
        long latencyMs,
        Map<String, Object> metadata) {
}
