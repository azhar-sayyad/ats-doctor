package com.atsdoctor.backend.api.health;

import com.atsdoctor.backend.application.config.AtsDoctorProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final AtsDoctorProperties properties;

    public HealthController(AtsDoctorProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        String mode = properties.ai() == null || properties.ai().mode() == null ? "stub" : properties.ai().mode();
        boolean persistence = properties.persistence() != null && properties.persistence().enabled();
        return Map.of(
                "status", "ok",
                "ai_mode", mode,
                "db", persistence ? "enabled" : "disabled");
    }
}
