package com.atsdoctor.backend;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** TASK-019 — OmniRoute config validation (deployment-only, PRD §6.3). */
class OmniRouteConfigTest {

    @Test
    void omniroute_config_parses_and_every_profile_has_a_model() throws Exception {
        Path configPath = Path.of("..", "omniroute", "config.yaml").toAbsolutePath().normalize();
        assertThat(configPath).exists();

        Map<String, Object> root = (Map<String, Object>) new Yaml().load(Files.readString(configPath));
        assertThat(root).containsKey("models");
        assertThat(root).containsKey("profiles");

        Map<String, Object> models = (Map<String, Object>) root.get("models");
        Map<String, Object> profiles = (Map<String, Object>) root.get("profiles");
        assertThat(profiles.keySet()).containsExactlyInAnyOrder("cheap", "quality", "local", "private");

        for (Map.Entry<String, Object> entry : profiles.entrySet()) {
            String profile = entry.getKey();
            Map<String, Object> mapping = (Map<String, Object>) entry.getValue();
            assertThat(mapping).as("profile %s", profile).containsKeys("provider", "model");

            String provider = String.valueOf(mapping.get("provider"));
            assertThat(models).as("provider %s for profile %s", provider, profile).containsKey(provider);
            assertThat(String.valueOf(mapping.get("model"))).isNotBlank();
        }

        // local must be a text/instruction model, never a vision model (PRD §6.3).
        String localModel = String.valueOf(((Map<String, Object>) profiles.get("local")).get("model")).toLowerCase();
        assertThat(localModel).doesNotContain("llava");

        // Secrets are env placeholders, never hard-coded.
        for (Map.Entry<String, Object> entry : models.entrySet()) {
            Map<String, Object> model = (Map<String, Object>) entry.getValue();
            Object apiKey = model.get("api_key");
            if (apiKey != null) {
                assertThat(String.valueOf(apiKey)).startsWith("${").endsWith("}");
            }
        }
    }
}
